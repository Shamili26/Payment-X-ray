package com.paymentapp.service;

import com.paymentapp.dto.Auth;
import com.paymentapp.entity.Account;
import com.paymentapp.entity.User;
import com.paymentapp.entity.UserSession;
import com.paymentapp.repository.AccountRepository;
import com.paymentapp.repository.UserRepository;
import com.paymentapp.repository.UserSessionRepository;
import com.paymentapp.security.CurrentUserProvider;
import com.paymentapp.security.JwtService;
import com.paymentapp.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final UserSessionRepository sessionRepository;
    private final AccountRepository     accountRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtService            jwtService;
    private final AuthenticationManager authManager;
    private final CurrentUserProvider   currentUserProvider;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // Brute-force lockout: after this many consecutive failed logins the account
    // is locked for the configured duration.
    @Value("${app.security.lockout.max-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout.duration-minutes:15}")
    private long lockoutDurationMinutes;

    // Default opening balance assigned to every newly created account.
    private static final BigDecimal DEFAULT_OPENING_BALANCE = new BigDecimal("1500000.00");

    // ─── Register ────────────────────────────────────────────────────────────

    @Transactional
    public Auth.AuthResponse register(Auth.RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username '" + req.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email '" + req.getEmail() + "' is already registered");
        }
        if (userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number '" + req.getPhoneNumber() + "' is already registered");
        }
        if (accountRepository.existsByAccountNumber(req.getAccountNumber())) {
            throw new IllegalArgumentException("Account number '" + req.getAccountNumber() + "' is already registered");
        }

        // Both inserts below run inside this single @Transactional method, so if
        // either the users row or the accounts row fails to save, the whole
        // registration is rolled back and no partial data is persisted.
        try {
            User user = User.builder()
                    .username(req.getUsername())
                    .email(req.getEmail())
                    .passwordHash(passwordEncoder.encode(req.getPassword()))
                    .firstName(req.getFirstName())
                    .lastName(req.getLastName())
                    .phoneNumber(req.getPhoneNumber())
                    .dateOfBirth(parseDateOfBirth(req.getDateOfBirth()))
                    .accountNumber(req.getAccountNumber())   // stored on the users table
                    .role("ROLE_USER")
                    .isEnabled(true)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .build();

            // saveAndFlush surfaces any DB constraint violation here (not later),
            // so we can handle it and roll back the transaction. Use the managed
            // entity it returns (it carries the generated id) for the rest of the flow.
            User savedUser = userRepository.saveAndFlush(user);
            log.info("New user registered: {}", savedUser.getUsername());

            // Simultaneously insert the same 16-digit account number into the
            // accounts table, owned by the new user (per-user data isolation).
            Account account = Account.builder()
                    .user(savedUser)
                    .accountNumber(req.getAccountNumber())
                    .accountName(req.getFirstName() + " " + req.getLastName())
                    .mobileNumber(req.getPhoneNumber())
                    .accountBalance(DEFAULT_OPENING_BALANCE)   // default opening balance: 15,00,000.00
                    .accountStatus("ACTIVE")
                    .build();
            accountRepository.saveAndFlush(account);
            log.info("Account {} created for user {}", account.getAccountNumber(), savedUser.getUsername());

            // Registration does NOT issue a session/token. The user must log in,
            // which is the only place an authenticated session (and JWT cookie)
            // is created.
            Auth.AuthResponse response = new Auth.AuthResponse();
            response.setUser(buildUserInfo(savedUser));
            return response;
        } catch (DataIntegrityViolationException ex) {
            // Marks the transaction for rollback; neither table keeps partial data.
            log.error("Registration failed while saving user/account data", ex);
            throw new IllegalArgumentException(
                    "Could not complete registration. The account number or another unique field is already in use.");
        }
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    @Transactional
    public Auth.AuthResponse login(Auth.LoginRequest req, String ipAddress, String userAgent) {
        // Resolve whether the identifier is username or email
        User user = userRepository.findByUsername(req.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(req.getUsernameOrEmail()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        // Delegates to DaoAuthenticationProvider → BCrypt check. A locked account
        // throws LockedException here (before the password check) and is handled
        // by AuthController as a 403; a wrong password throws BadCredentials,
        // which we use to drive the brute-force lockout counter.
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), req.getPassword())
            );
        } catch (BadCredentialsException ex) {
            registerFailedLogin(user);
            throw ex;
        }

        // Successful login clears any prior failed-attempt / lockout state.
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // Update last login timestamp
        userRepository.updateLastLogin(user.getUserId(), LocalDateTime.now());

        log.info("User logged in: {} from {}", user.getUsername(), ipAddress);
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    /**
     * Records a failed login. After {@code maxFailedAttempts} consecutive
     * failures the account is locked for {@code lockoutDurationMinutes}; once
     * that window passes the next failure starts a fresh count.
     */
    private void registerFailedLogin(User user) {
        final LocalDateTime now = LocalDateTime.now();
        // A previously-expired lock window starts the count over.
        int prior = (user.getLockedUntil() != null && user.getLockedUntil().isBefore(now))
                ? 0 : user.getFailedLoginAttempts();
        int attempts = prior + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(now.plusMinutes(lockoutDurationMinutes));
            log.warn("Account '{}' locked for {} min after {} failed login attempts",
                    user.getUsername(), lockoutDurationMinutes, attempts);
        } else {
            user.setLockedUntil(null);
        }
        userRepository.save(user);
    }

    // ─── Logout ──────────────────────────────────────────────────────────────

    /**
     * Revokes the session backing the given JWT so the token can no longer be
     * used, even before it expires. Safe to call with a null/unknown token.
     */
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionRepository.findByTokenHash(TokenHasher.sha256Hex(token))
                .ifPresent(session -> {
                    session.setActive(false);
                    sessionRepository.save(session);
                    log.info("Session revoked for user {}", session.getUser().getUsername());
                });
    }

    // ─── Current user ─────────────────────────────────────────────────────────

    /** Returns the profile of the currently authenticated user (for GET /me). */
    @Transactional(readOnly = true)
    public Auth.AuthResponse.UserInfo currentUserInfo() {
        return buildUserInfo(currentUserProvider.getCurrentUser());
    }

    // ─── Update profile ────────────────────────────────────────────────────────

    /**
     * Updates the current user's editable profile fields (name, email, phone).
     * Email and phone must remain unique across other users. The managed entity
     * is loaded fresh so JPA dirty-checking persists the change.
     */
    @Transactional
    public Auth.AuthResponse.UserInfo updateProfile(Auth.UpdateProfileRequest req) {
        User current = currentUserProvider.getCurrentUser();
        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("User no longer exists"));

        if (userRepository.existsByEmailAndUserIdNot(req.getEmail(), user.getUserId())) {
            throw new IllegalArgumentException("Email '" + req.getEmail() + "' is already registered");
        }
        if (userRepository.existsByPhoneNumberAndUserIdNot(req.getPhoneNumber(), user.getUserId())) {
            throw new IllegalArgumentException("Phone number '" + req.getPhoneNumber() + "' is already registered");
        }

        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setEmail(req.getEmail());
        user.setPhoneNumber(req.getPhoneNumber());

        User saved = userRepository.save(user);
        log.info("Profile updated for user {}", saved.getUsername());
        return buildUserInfo(saved);
    }

    // ─── Shared ──────────────────────────────────────────────────────────────

    private Auth.AuthResponse buildAuthResponse(User user, String ipAddress, String userAgent) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole());
        extraClaims.put("email", user.getEmail());

        String token = jwtService.generateToken(extraClaims, user);
        long expiresInSeconds = jwtExpirationMs / 1000;

        // Persist session record. The token is stored as a SHA-256 hash (never
        // in clear text, never MD5) so the JWT filter can look it up to confirm
        // the session is still active on every request.
        UserSession session = UserSession.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(token))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds))
                .lastActivityAt(LocalDateTime.now())
                .isActive(true)
                .build();
        sessionRepository.save(session);

        Auth.AuthResponse response = new Auth.AuthResponse();
        response.setAccessToken(token);
        response.setExpiresIn(expiresInSeconds);
        response.setUser(buildUserInfo(user));
        return response;
    }

    private Auth.AuthResponse.UserInfo buildUserInfo(User user) {
        Auth.AuthResponse.UserInfo userInfo = new Auth.AuthResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setFirstName(user.getFirstName());
        userInfo.setLastName(user.getLastName());
        userInfo.setPhoneNumber(user.getPhoneNumber());
        userInfo.setRole(user.getRole());
        return userInfo;
    }

    private LocalDate parseDateOfBirth(String dateOfBirth) {
        LocalDate dob;
        try {
            dob = LocalDate.parse(dateOfBirth); // expects ISO yyyy-MM-dd
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Date of birth must be a valid date in yyyy-MM-dd format");
        }
        if (dob.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
        return dob;
    }
}
