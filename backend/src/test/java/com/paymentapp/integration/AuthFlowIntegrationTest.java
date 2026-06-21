package com.paymentapp.integration;

import com.paymentapp.dto.Auth;
import com.paymentapp.dto.PaymentDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test that boots the <em>entire</em> Spring context on a
 * random port and drives it over real HTTP via {@link TestRestTemplate}. Unlike
 * the {@code @WebMvcTest} controller slices (which mock the service layer) and
 * the {@code @DataJpaTest} repository tests, this exercises the full stack —
 * the rate-limit filter, the JWT authentication filter, Spring Security, the
 * controllers, the services, and the JPA repositories against an H2 database.
 *
 * <p>It is the integration + security/authorization coverage the diagram's
 * test tier called for: it proves the httpOnly-cookie session actually
 * authenticates protected endpoints and that logout revokes the session
 * server-side (not just on the client).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Auth + session end-to-end integration (full context, H2)")
class AuthFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Auth.RegisterRequest registerRequest(String suffix) {
        Auth.RegisterRequest req = new Auth.RegisterRequest();
        req.setUsername("intuser" + suffix);
        req.setEmail("intuser" + suffix + "@example.com");
        req.setPassword("Str0ng@Pass1");
        req.setFirstName("Ingrid");
        req.setLastName("Tester");
        req.setPhoneNumber("+9198765" + suffix);          // 10 digits after +91
        req.setDateOfBirth("1990-01-01");
        req.setAccountNumber("12341234123" + suffix);       // 16 digits
        return req;
    }

    private static HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Object> withCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookie);
        return new HttpEntity<>(headers);
    }

    /** Extracts the {@code jwt=...} cookie pair from a login response's Set-Cookie header. */
    private static String jwtCookieFrom(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("Set-Cookie header present on login").isNotNull().isNotEmpty();
        String jwtCookie = setCookies.stream()
                .filter(c -> c.startsWith("jwt="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No jwt cookie in Set-Cookie: " + setCookies));
        // Keep only the name=value pair (drop the Path/Secure/SameSite attributes).
        return jwtCookie.split(";", 2)[0];
    }

    // ─── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register → login → /me → /accounts → logout → /me 401 (cookie session lifecycle)")
    void fullAuthAndSessionLifecycle() {
        final String suffix = "00001";   // unique within this run

        // 1. Register — creates the user AND a funded account, returns 201.
        ResponseEntity<Auth.AuthResponse> register = rest.postForEntity(
                "/api/auth/register", json(registerRequest(suffix)), Auth.AuthResponse.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).isNotNull();
        assertThat(register.getBody().getUser().getUsername()).isEqualTo("intuser" + suffix);
        // Registration must NOT issue a token/cookie — login is the only entry point.
        assertThat(register.getBody().getAccessToken()).isNull();

        // 2. Login — returns 200 with the JWT delivered ONLY as an httpOnly cookie.
        Auth.LoginRequest login = new Auth.LoginRequest();
        login.setUsernameOrEmail("intuser" + suffix);
        login.setPassword("Str0ng@Pass1");
        ResponseEntity<Auth.AuthResponse> loginResp = rest.postForEntity(
                "/api/auth/login", json(login), Auth.AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody()).isNotNull();
        assertThat(loginResp.getBody().getAccessToken())
                .as("token is cookie-only, never in the body").isNull();
        String cookie = jwtCookieFrom(loginResp);

        // 3. /auth/me with the cookie — authenticated, returns the current user.
        ResponseEntity<Auth.AuthResponse.UserInfo> me = rest.exchange(
                "/api/auth/me", HttpMethod.GET, withCookie(cookie), Auth.AuthResponse.UserInfo.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().getUsername()).isEqualTo("intuser" + suffix);
        assertThat(me.getBody().getEmail()).isEqualTo("intuser" + suffix + "@example.com");

        // 4. /api/accounts with the cookie — a protected endpoint, returns the
        //    funded account created during registration (per-user data isolation).
        ResponseEntity<PaymentDto.AccountResponse[]> accounts = rest.exchange(
                "/api/accounts", HttpMethod.GET, withCookie(cookie), PaymentDto.AccountResponse[].class);
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.getBody()).isNotNull().hasSize(1);
        assertThat(accounts.getBody()[0].getAccountNumber()).isEqualTo("12341234123" + suffix);
        assertThat(accounts.getBody()[0].getAccountStatus()).isEqualTo("ACTIVE");

        // 5. Logout — revokes the session server-side and clears the cookie.
        ResponseEntity<Void> logout = rest.exchange(
                "/api/auth/logout", HttpMethod.POST, withCookie(cookie), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 6. The SAME cookie is now rejected — proves logout is real revocation,
        //    not just a client-side cookie clear (the token has not yet expired).
        ResponseEntity<String> meAfterLogout = rest.exchange(
                "/api/auth/me", HttpMethod.GET, withCookie(cookie), String.class);
        assertThat(meAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("protected endpoint without a session cookie is rejected (403, default entry point)")
    void protectedEndpointRequiresAuthentication() {
        // A non-permitAll endpoint is blocked in the security filter chain before
        // it reaches any controller. SecurityConfig configures no AuthenticationEntryPoint,
        // so Spring Security's default Http403ForbiddenEntryPoint answers with 403.
        // (Contrast /api/auth/me, which is permitAll and reaches AuthController's own
        // AccessDeniedException handler → 401.)
        ResponseEntity<String> payments = rest.getForEntity("/api/payment", String.class);
        assertThat(payments.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("registration with an invalid payload is rejected with 400")
    void registrationValidationIsEnforced() {
        Auth.RegisterRequest bad = registerRequest("00009");
        bad.setEmail("not-an-email");            // fails @Email
        bad.setPhoneNumber("12345");             // fails the +91 pattern
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/auth/register", json(bad), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("login with wrong credentials returns 401 and sets no cookie")
    void loginWithWrongPasswordIsUnauthorized() {
        final String suffix = "00002";
        rest.postForEntity("/api/auth/register", json(registerRequest(suffix)), Auth.AuthResponse.class);

        Auth.LoginRequest login = new Auth.LoginRequest();
        login.setUsernameOrEmail("intuser" + suffix);
        login.setPassword("WrongPassword!9");
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/auth/login", json(login), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }
}