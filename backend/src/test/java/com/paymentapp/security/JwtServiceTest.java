package com.paymentapp.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    // Base64 of a 32-byte key
    private static final String SECRET =
            "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW5z";

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86400000L);

        userDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("generateToken creates a non-blank token")
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("extractUsername returns correct subject from token")
    void extractUsername_returnsCorrectUsername() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
    }

    @Test
    @DisplayName("isTokenValid returns true for fresh valid token")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false for different user")
    void isTokenValid_differentUser_returnsFalse() {
        String token = jwtService.generateToken(userDetails);

        UserDetails otherUser = User.builder()
                .username("otheruser").password("pw").authorities(Collections.emptyList()).build();

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired returns false for fresh token")
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtService.generateToken(userDetails);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("extractUsername throws ExpiredJwtException for expired token")
    void extractUsername_expiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1000L);
        String expiredToken = jwtService.generateToken(userDetails);

        assertThatThrownBy(() -> jwtService.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("generateToken with extra claims embeds them in the token")
    void generateToken_withExtraClaims_embedsClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ROLE_USER");
        claims.put("email", "test@example.com");

        String token = jwtService.generateToken(claims, userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("extractExpiration returns a future date for fresh token")
    void extractExpiration_freshToken_returnsFutureDate() {
        String token = jwtService.generateToken(userDetails);
        Date expiration = jwtService.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
    }
}
