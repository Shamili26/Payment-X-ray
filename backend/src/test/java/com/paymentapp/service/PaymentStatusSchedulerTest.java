package com.paymentapp.service;

import com.paymentapp.entity.Payment;
import com.paymentapp.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentStatusScheduler Unit Tests")
class PaymentStatusSchedulerTest {

    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentStatusScheduler scheduler;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("completeDuePayments flips due PENDING payments to COMPLETED and saves them")
    void completeDuePayments_marksDueAsCompleted() {
        Payment due1 = new Payment();
        due1.setPaymentId(1L);
        due1.setStatus("PENDING");
        due1.setPaymentDate(LocalDate.now());
        Payment due2 = new Payment();
        due2.setPaymentId(2L);
        due2.setStatus("PENDING");
        due2.setPaymentDate(LocalDate.now().minusDays(1));

        when(paymentRepository.findByStatusAndPaymentDateLessThanEqual(eq("PENDING"), any(LocalDate.class)))
                .thenReturn(List.of(due1, due2));

        scheduler.completeDuePayments();

        assertThat(due1.getStatus()).isEqualTo("COMPLETED");
        assertThat(due2.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<List<Payment>> captor = ArgumentCaptor.forClass(List.class);
        verify(paymentRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(due1, due2);
    }

    @Test
    @DisplayName("completeDuePayments does nothing when there are no due payments")
    void completeDuePayments_noneDue_noSave() {
        when(paymentRepository.findByStatusAndPaymentDateLessThanEqual(eq("PENDING"), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.completeDuePayments();

        verify(paymentRepository, never()).saveAll(any());
    }
}