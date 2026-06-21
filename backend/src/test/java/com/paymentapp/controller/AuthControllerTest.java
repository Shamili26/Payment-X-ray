package com.paymentapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentapp.config.TestSecurityConfig;
import com.paymentapp.dto.Auth;
import com.paymentapp.repository.UserSessionRepository;
import com.paymentapp.security.JwtService;
import com.paymentapp.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;
    @MockBean  private AuthService   authService;

    // Collaborators of the JwtAuthenticationFilter, which @WebMvcTest pulls into
    // the slice as a servlet Filter. Mocked so the context can load; not exercised
    // here (requests carry no JWT cookie).
    @MockBean  private JwtService            jwtService;
    @MockBean  private UserDetailsService    userDetailsService;
    @MockBean  private UserSessionRepository userSessionRepository;

    private Auth.AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        Auth.AuthResponse.UserInfo userInfo = new Auth.AuthResponse.UserInfo();
        userInfo.setUserId(1L);
        userInfo.setUsername("testuser");
        userInfo.setEmail("test@example.com");
        userInfo.setFirstName("Test");
        userInfo.setLastName("User");
        userInfo.setRole("ROLE_USER");

        sampleAuthResponse = new Auth.AuthResponse();
        sampleAuthResponse.setAccessToken("mock.jwt.token");
        sampleAuthResponse.setExpiresIn(86400L);
        sampleAuthResponse.setUser(userInfo);
    }

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register returns 201 with token")
    void register_validRequest_returns201() throws Exception {
        when(authService.register(any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for blank username")
    void register_blankUsername_returns400() throws Exception {
        String body = "{\"username\":\"\",\"email\":\"test@example.com\",\"password\":\"Test@1234\","
                + "\"firstName\":\"Test\",\"lastName\":\"User\",\"phoneNumber\":\"+911234567890\","
                + "\"dateOfBirth\":\"1990-01-01\",\"accountNumber\":\"1234567812345678\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for invalid email")
    void register_invalidEmail_returns400() throws Exception {
        String body = "{\"username\":\"testuser\",\"email\":\"not-an-email\",\"password\":\"Test@1234\","
                + "\"firstName\":\"Test\",\"lastName\":\"User\",\"phoneNumber\":\"+911234567890\","
                + "\"dateOfBirth\":\"1990-01-01\",\"accountNumber\":\"1234567812345678\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 when username taken")
    void register_duplicateUsername_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Username 'testuser' is already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username 'testuser' is already taken"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for weak password")
    void register_weakPassword_returns400() throws Exception {
        String body = "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"weak\","
                + "\"firstName\":\"Test\",\"lastName\":\"User\",\"phoneNumber\":\"+911234567890\","
                + "\"dateOfBirth\":\"1990-01-01\",\"accountNumber\":\"1234567812345678\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for invalid account number")
    void register_invalidAccountNumber_returns400() throws Exception {
        // contains non-numeric characters => must fail the 16-digit numeric rule
        String body = "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"Test@1234\","
                + "\"firstName\":\"Test\",\"lastName\":\"User\",\"phoneNumber\":\"+911234567890\","
                + "\"dateOfBirth\":\"1990-01-01\",\"accountNumber\":\"12345678abcd5678\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login returns 200, sets httpOnly JWT cookie, omits token from body")
    void login_validCredentials_returns200() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"testuser\",\"password\":\"Test@1234\"}"))
                .andExpect(status().isOk())
                // JWT delivered as an httpOnly cookie, never readable by JavaScript
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().value("jwt", "mock.jwt.token"))
                // and stripped from the response body
                .andExpect(jsonPath("$.accessToken").value(nullValue()))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for bad credentials")
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"testuser\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for user not found")
    void login_userNotFound_returns401() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new UsernameNotFoundException("Not found"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"nobody\",\"password\":\"Test@1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 403 for disabled account")
    void login_disabledAccount_returns403() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new DisabledException("Disabled"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"testuser\",\"password\":\"Test@1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 403 for locked account")
    void login_lockedAccount_returns403() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new LockedException("Locked"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"testuser\",\"password\":\"Test@1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is locked"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 for missing fields")
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /api/auth/profile ────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("PUT /api/auth/profile returns 200 with updated info")
    void updateProfile_validRequest_returns200() throws Exception {
        Auth.AuthResponse.UserInfo updated = new Auth.AuthResponse.UserInfo();
        updated.setUserId(1L);
        updated.setUsername("testuser");
        updated.setEmail("new@example.com");
        updated.setFirstName("Jane");
        updated.setLastName("Doe");
        updated.setPhoneNumber("+919999988888");
        updated.setRole("ROLE_USER");
        when(authService.updateProfile(any())).thenReturn(updated);

        String body = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                + "\"email\":\"new@example.com\",\"phoneNumber\":\"+919999988888\"}";

        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+919999988888"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/auth/profile returns 400 for an invalid phone number")
    void updateProfile_invalidPhone_returns400() throws Exception {
        String body = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                + "\"email\":\"new@example.com\",\"phoneNumber\":\"12345\"}";

        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String validRegisterJson() {
        return "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"Test@1234\","
                + "\"firstName\":\"Test\",\"lastName\":\"User\",\"phoneNumber\":\"+911234567890\","
                + "\"dateOfBirth\":\"1990-01-01\",\"accountNumber\":\"1234567812345678\"}";
    }
}
