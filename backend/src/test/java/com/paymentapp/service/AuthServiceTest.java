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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository        userRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private AccountRepository     accountRepository;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private JwtService            jwtService;
    @Mock private AuthenticationManager authManager;
    @Mock private CurrentUserProvider   currentUserProvider;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 86400000L);

        testUser = new User();
        testUser.setUserId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("$2a$10$hashedpassword");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setRole("ROLE_USER");
        testUser.setEnabled(true);
        testUser.setAccountNonExpired(true);
        testUser.setAccountNonLocked(true);
        testUser.setCredentialsNonExpired(true);
    }

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register succeeds and creates an account capturing the mobile number")
    void register_newUser_returnsAuthResponse() {
        Auth.RegisterRequest req = buildRegisterRequest("testuser", "test@example.com");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.saveAndFlush(any())).thenReturn(testUser);

        Auth.AuthResponse resp = authService.register(req);

        // Registration returns the user profile but issues NO token/session —
        // the user must log in to obtain a session cookie.
        assertThat(resp.getAccessToken()).isNull();
        assertThat(resp.getUser().getUsername()).isEqualTo("testuser");
        assertThat(resp.getUser().getRole()).isEqualTo("ROLE_USER");

        // The 16-digit account number must be persisted on the users row …
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountNumber()).isEqualTo("1234567812345678");

        verify(sessionRepository, never()).save(any());
        verify(jwtService, never()).generateToken(anyMap(), any());

        // … and simultaneously on a new accounts row (with the mobile number).
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getMobileNumber()).isEqualTo("+911234567890");
        assertThat(savedAccount.getUser()).isEqualTo(testUser);
        assertThat(savedAccount.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(savedAccount.getAccountNumber()).isEqualTo("1234567812345678");
    }

    @Test
    @DisplayName("register throws when username already taken")
    void register_duplicateUsername_throwsException() {
        Auth.RegisterRequest req = buildRegisterRequest("testuser", "new@example.com");
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("register throws when email already registered")
    void register_duplicateEmail_throwsException() {
        Auth.RegisterRequest req = buildRegisterRequest("newuser", "test@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("register throws when account number already registered")
    void register_duplicateAccountNumber_throwsException() {
        Auth.RegisterRequest req = buildRegisterRequest("newuser", "new@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(accountRepository.existsByAccountNumber("1234567812345678")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(userRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login succeeds with valid username and password")
    void login_validUsername_returnsAuthResponse() {
        Auth.LoginRequest req = new Auth.LoginRequest();
        req.setUsernameOrEmail("testuser");
        req.setPassword("Test@1234");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(anyMap(), any())).thenReturn("mock.jwt.token");
        when(sessionRepository.save(any())).thenReturn(new UserSession());

        Auth.AuthResponse resp = authService.login(req, "127.0.0.1", "TestAgent");

        assertThat(resp.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(resp.getUser().getEmail()).isEqualTo("test@example.com");
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).updateLastLogin(eq(1L), any());
    }

    @Test
    @DisplayName("login succeeds with email as identifier")
    void login_validEmail_returnsAuthResponse() {
        Auth.LoginRequest req = new Auth.LoginRequest();
        req.setUsernameOrEmail("test@example.com");
        req.setPassword("Test@1234");

        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(anyMap(), any())).thenReturn("mock.jwt.token");
        when(sessionRepository.save(any())).thenReturn(new UserSession());

        Auth.AuthResponse resp = authService.login(req, "127.0.0.1", "Mozilla");

        assertThat(resp.getAccessToken()).isNotNull();
        verify(authManager).authenticate(any());
    }

    @Test
    @DisplayName("login throws when user not found")
    void login_userNotFound_throwsException() {
        Auth.LoginRequest req = new Auth.LoginRequest();
        req.setUsernameOrEmail("nobody");
        req.setPassword("pass");

        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1", "agent"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("login throws when password is wrong")
    void login_wrongPassword_throwsBadCredentials() {
        Auth.LoginRequest req = new Auth.LoginRequest();
        req.setUsernameOrEmail("testuser");
        req.setPassword("WrongPass");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1", "agent"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ─── updateProfile ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile updates name, email and phone and returns the new info")
    void updateProfile_validRequest_updatesUser() {
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmailAndUserIdNot("new@example.com", 1L)).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndUserIdNot("+919999988888", 1L)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Auth.UpdateProfileRequest req = new Auth.UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("new@example.com");
        req.setPhoneNumber("+919999988888");

        Auth.AuthResponse.UserInfo info = authService.updateProfile(req);

        assertThat(info.getFirstName()).isEqualTo("Jane");
        assertThat(info.getLastName()).isEqualTo("Doe");
        assertThat(info.getEmail()).isEqualTo("new@example.com");
        assertThat(info.getPhoneNumber()).isEqualTo("+919999988888");
        assertThat(testUser.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateProfile rejects an email already used by another user")
    void updateProfile_duplicateEmail_throws() {
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmailAndUserIdNot("taken@example.com", 1L)).thenReturn(true);

        Auth.UpdateProfileRequest req = new Auth.UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("taken@example.com");
        req.setPhoneNumber("+919999988888");

        assertThatThrownBy(() -> authService.updateProfile(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile rejects a phone already used by another user")
    void updateProfile_duplicatePhone_throws() {
        when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmailAndUserIdNot(anyString(), eq(1L))).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndUserIdNot("+919999988888", 1L)).thenReturn(true);

        Auth.UpdateProfileRequest req = new Auth.UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("new@example.com");
        req.setPhoneNumber("+919999988888");

        assertThatThrownBy(() -> authService.updateProfile(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(userRepository, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Auth.RegisterRequest buildRegisterRequest(String username, String email) {
        Auth.RegisterRequest req = new Auth.RegisterRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("Test@1234");
        req.setFirstName("Test");
        req.setLastName("User");
        req.setPhoneNumber("+911234567890");
        req.setDateOfBirth("1990-01-01");
        req.setAccountNumber("1234567812345678");
        return req;
    }
}
