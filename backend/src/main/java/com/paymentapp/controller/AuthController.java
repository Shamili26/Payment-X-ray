package com.paymentapp.controller;

import com.paymentapp.dto.Auth;
import com.paymentapp.security.JwtAuthenticationFilter;
import com.paymentapp.service.AuthService;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Cookies are marked Secure by default; can be disabled for local HTTP dev
    // via app.security.cookie.secure=false.
    @Value("${app.security.cookie.secure:true}")
    private boolean cookieSecure;

    // SameSite policy for the JWT cookie. Defaults to "None" so the cookie is
    // sent on cross-site requests — the frontend and API are served from
    // different hosts (separate CloudFront distributions). "None" REQUIRES the
    // Secure flag, so for local HTTP dev set this to "Lax" alongside
    // app.security.cookie.secure=false.
    @Value("${app.security.cookie.same-site:None}")
    private String cookieSameSite;

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<Auth.AuthResponse> register(
            @Valid @RequestBody Auth.RegisterRequest request
    ) {
        Auth.AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Auth.AuthResponse> login(
            @Valid @RequestBody Auth.LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress  = getClientIp(httpRequest);
        String userAgent  = httpRequest.getHeader("User-Agent");

        Auth.AuthResponse response = authService.login(request, ipAddress, userAgent);

        // Deliver the JWT in an httpOnly, Secure, SameSite=None cookie so it is
        // unreachable from JavaScript (XSS cannot read it) yet still sent on the
        // cross-site requests from the frontend host to the API host. The token
        // is removed from the response body for the same reason.
        ResponseCookie cookie = buildJwtCookie(response.getAccessToken(), response.getExpiresIn());
        response.setAccessToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    // ─── POST /api/auth/logout ────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(extractToken(httpRequest));

        // Clear the cookie on the client.
        ResponseCookie cleared = buildJwtCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    // ─── GET /api/auth/me ─────────────────────────────────────────────────────

    /** Returns the current user if the session cookie is valid; else 401. */
    @GetMapping("/me")
    public ResponseEntity<Auth.AuthResponse.UserInfo> me() {
        return ResponseEntity.ok(authService.currentUserInfo());
    }

    // ─── PUT /api/auth/profile ────────────────────────────────────────────────

    /** Updates the current user's editable profile fields and returns the result. */
    @PutMapping("/profile")
    public ResponseEntity<Auth.AuthResponse.UserInfo> updateProfile(
            @Valid @RequestBody Auth.UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    // ─── Exception handlers ───────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Auth.ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .badRequest()
                .body(new Auth.ApiError(400, "Validation Failed", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Auth.ApiError> handleConflict(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new Auth.ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Auth.ApiError> handleBadCredentials() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new Auth.ApiError(401, "Unauthorized", "Invalid username or password"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Auth.ApiError> handleDisabled() {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new Auth.ApiError(403, "Forbidden", "Account is disabled"));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Auth.ApiError> handleLocked() {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new Auth.ApiError(403, "Forbidden", "Account is locked"));
    }

    // GET /me with no valid session lands here.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Auth.ApiError> handleNotAuthenticated() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new Auth.ApiError(401, "Unauthorized", "Not authenticated"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseCookie buildJwtCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.JWT_COOKIE, value)
                .httpOnly(true)            // not readable by JavaScript → XSS-safe
                .secure(cookieSecure)      // only sent over HTTPS (required for SameSite=None)
                .sameSite(cookieSameSite)  // None → sent cross-site (frontend host → API host)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JwtAuthenticationFilter.JWT_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
