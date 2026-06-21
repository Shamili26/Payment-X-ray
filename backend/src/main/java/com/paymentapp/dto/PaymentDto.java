package com.paymentapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PaymentDto {

    // ─── Request ──────────────────────────────────────────────────────────────

    @Data
    public static class CreateRequest {

        @NotNull(message = "From account is required")
        private Long accountId;

        @NotNull(message = "Payee is required")
        private Long payeeId;

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
        private BigDecimal paymentAmount;

        @NotNull(message = "Payment date is required")
        @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
        private LocalDate paymentDate;

        @Size(max = 100, message = "Memo must not exceed 100 characters")
        private String memo;
    }

    @Data
    public static class UpdateRequest {

        @NotNull(message = "Payment ID is required")
        private Long paymentId;

        @NotNull(message = "From account is required")
        private Long accountId;

        @NotNull(message = "Payee is required")
        private Long payeeId;

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
        private BigDecimal paymentAmount;

        @NotNull(message = "Payment date is required")
        @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
        private LocalDate paymentDate;

        @Size(max = 100, message = "Memo must not exceed 100 characters")
        private String memo;
    }

    // ─── MFA / OTP ────────────────────────────────────────────────────────────

    /** Sent by the client to verify the OTP and finalize the payment. */
    @Data
    public static class VerifyOtpRequest {

        @NotBlank(message = "Challenge ID is required")
        private String challengeId;

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        private String otp;
    }

    /** Returned when a payment is initiated and an OTP has been dispatched. */
    @Data
    public static class OtpChallengeResponse {
        private String challengeId;
        private String maskedMobile;   // e.g. +91●●●●●●1234
        private long   expiresInSeconds;
        private String message;
    }

    // ─── Responses ────────────────────────────────────────────────────────────

    @Data
    public static class PaymentResponse {
        private Long paymentId;
        private Long accountId;
        private String accountName;
        private String accountNumber;
        private Long payeeId;
        private String payeeName;
        private String payeeNumber;
        private BigDecimal paymentAmount;
        private BigDecimal feeAmount;

        @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
        private LocalDate paymentDate;

        private String memo;
        private String status;
        private LocalDateTime updatedDatetime;
    }

    @Data
    public static class FeePreviewResponse {
        private BigDecimal paymentAmount;
        private BigDecimal feeAmount;
        private BigDecimal totalAmount;
    }

    @Data
    public static class AccountResponse {
        private Long accountId;
        private String accountNumber;
        private String accountName;
        private BigDecimal accountBalance;
        private String accountStatus;
    }

    @Data
    public static class PayeeResponse {
        private Long payeeId;
        private String payeeNumber;
        private String payeeName;
        private BigDecimal amountDue;

        @JsonFormat(pattern = "dd/MM/yyyy")
        private LocalDate dueDate;
    }
}
