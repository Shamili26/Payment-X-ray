package com.paymentapp.service;

import com.paymentapp.entity.Payment;
import com.paymentapp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Moves PENDING payments to COMPLETED once their payment date has arrived.
 *
 * The funds were already debited at creation time (see PaymentService), so this
 * job only advances the lifecycle state — it performs no balance movement. It
 * runs on a fixed schedule (configurable via {@code app.scheduler.*}); the cron
 * defaults to once a minute so a payment dated "today" settles promptly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusScheduler {

    private final PaymentRepository paymentRepository;

    @Scheduled(cron = "${app.scheduler.payment-completion.cron:0 * * * * *}")
    @Transactional
    public void completeDuePayments() {
        List<Payment> due = paymentRepository
                .findByStatusAndPaymentDateLessThanEqual("PENDING", LocalDate.now());
        if (due.isEmpty()) {
            return;
        }
        for (Payment p : due) {
            p.setStatus("COMPLETED");
        }
        paymentRepository.saveAll(due);
        log.info("Payment scheduler completed {} due payment(s)", due.size());
    }
}