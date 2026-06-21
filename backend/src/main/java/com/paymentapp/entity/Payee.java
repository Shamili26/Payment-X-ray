package com.paymentapp.entity;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payee")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payee_id")
    private Long payeeId;

    @Column(name = "payee_number", nullable = false, unique = true, length = 30)
    private String payeeNumber;

    @Column(name = "payee_name", nullable = false, length = 100)
    private String payeeName;

    @Column(name = "amount_due", precision = 15, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDatetime;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedDatetime = LocalDateTime.now(); }
}
