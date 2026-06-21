package com.paymentapp.dto;

import javax.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

public class Auth {

    // ─── Register ─────────────────────────────────────────────────────────────

    @Data
    public static class RegisterRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                 message = "Username may only contain letters, digits, _, ., -")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 100)
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
            message = "Password must contain uppercase, lowercase, digit, and special character"
        )
        private String password;

        @NotBlank(message = "First name is required")
        @Size(max = 50)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 50)
        private String lastName;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+91\\d{10}$",
                 message = "Must be a valid Indian phone number: +91 followed by 10 digits (e.g. +919876543210)")
        private String phoneNumber;

        @NotBlank(message = "Date of birth is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
                 message = "Date of birth must be in yyyy-MM-dd format")
        private String dateOfBirth;

        /**
         * 16-digit account number captured on the Create Account form as four
         * separate 4-digit inputs and concatenated by the client before submit.
         * Stored on BOTH the users row and a new accounts row.
         */
        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^\\d{16}$",
                 message = "Account number must be exactly 16 digits (numeric only)")
        private String accountNumber;
    }

    // ─── Update profile ────────────────────────────────────────────────────────

    /**
     * Editable profile fields. Username, account number and date of birth are
     * intentionally immutable here — only contact details and display name change.
     */
    @Data
    public static class UpdateProfileRequest {

        @NotBlank(message = "First name is required")
        @Size(max = 50)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 50)
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 100)
        private String email;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+91\\d{10}$",
                 message = "Must be a valid Indian phone number: +91 followed by 10 digits (e.g. +919876543210)")
        private String phoneNumber;
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Data
    public static class LoginRequest {

        @NotBlank(message = "Username or email is required")
        private String usernameOrEmail;

        @NotBlank(message = "Password is required")
        private String password;
    }

    // ─── Response ─────────────────────────────────────────────────────────────

    @Data
    public static class AuthResponse {
        private String accessToken;
        private final String tokenType = "Bearer";
        private long expiresIn;
        private UserInfo user;

        @Data
        public static class UserInfo {
            private Long userId;
            private String username;
            private String email;
            private String firstName;
            private String lastName;
            private String phoneNumber;
            private String role;
        }
    }

    // ─── API Error ───────────────────────────────────────────────────────────

    @Data
    public static class ApiError {
        private int status;
        private String error;
        private String message;
        private LocalDateTime timestamp;

        public ApiError(int status, String error, String message) {
            this.status    = status;
            this.error     = error;
            this.message   = message;
            this.timestamp = LocalDateTime.now();
        }
    }
}
