package com.paymentapp.entity;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    // Owning user — enforces per-user data isolation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    // Mobile number captured at registration
    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "account_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal accountBalance;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;   // ACTIVE, INACTIVE, SUSPENDED

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDatetime;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedDatetime = LocalDateTime.now(); }
}
