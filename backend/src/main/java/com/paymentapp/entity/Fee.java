package com.paymentapp.entity;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fee_id")
    private Long feeId;

    @Column(name = "fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "amount_min", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountMin;

    @Column(name = "amount_max", precision = 15, scale = 2)   // NULL = no upper limit
    private BigDecimal amountMax;

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDatetime;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedDatetime = LocalDateTime.now(); }
}
