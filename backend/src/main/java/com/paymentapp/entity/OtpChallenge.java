package com.paymentapp.entity;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A short-lived, single-use OTP challenge that gates a payment submission (MFA).
 * The plaintext OTP is never stored — only its hash. The validated payment
 * details are held here until the user verifies the OTP, at which point the
 * payment is actually created.
 */
@Entity
@Table(name = "otp_challenge")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OtpChallenge {

    @Id
    @Column(name = "challenge_id", length = 36)
    private String challengeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    // ─── Pending payment data (created only after OTP verification) ───────────

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "payee_id", nullable = false)
    private Long payeeId;

    @Column(name = "payment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "memo", length = 100)
    private String memo;

    // ─── OTP lifecycle ───────────────────────────────────────────────────────

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

