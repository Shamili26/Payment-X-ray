package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Payee;
import com.paymentapp.repository.PayeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayeeService Unit Tests")
class PayeeServiceTest {

    @Mock private PayeeRepository payeeRepository;

    @InjectMocks
    private PayeeService payeeService;

    private Payee payee;

    @BeforeEach
    void setUp() {
        payee = new Payee();
        payee.setPayeeId(1L);
        payee.setPayeeNumber("PAY-001");
        payee.setPayeeName("Airtel Mobile");
        payee.setAmountDue(new BigDecimal("499"));
    }

    @Test
    @DisplayName("getByIdOrThrow returns the payee when present")
    void getById_present_returnsPayee() {
        when(payeeRepository.findById(1L)).thenReturn(Optional.of(payee));

        assertThat(payeeService.getByIdOrThrow(1L)).isSameAs(payee);
    }

    @Test
    @DisplayName("getByIdOrThrow throws when the payee is missing")
    void getById_missing_throws() {
        when(payeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payeeService.getByIdOrThrow(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payee not found");
    }

    @Test
    @DisplayName("findAll maps all payees to responses")
    void findAll_mapsResponses() {
        when(payeeRepository.findAll()).thenReturn(List.of(payee));

        List<PaymentDto.PayeeResponse> result = payeeService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPayeeName()).isEqualTo("Airtel Mobile");
        assertThat(result.get(0).getPayeeNumber()).isEqualTo("PAY-001");
    }
}