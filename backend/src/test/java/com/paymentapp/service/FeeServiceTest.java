package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Fee;
import com.paymentapp.repository.FeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeeService Unit Tests")
class FeeServiceTest {

    @Mock private FeeRepository feeRepository;

    @InjectMocks
    private FeeService feeService;

    private Fee fee;

    @BeforeEach
    void setUp() {
        fee = new Fee();
        fee.setFeeId(1L);
        fee.setFeeAmount(new BigDecimal("25"));
        fee.setAmountMin(new BigDecimal("100"));
        fee.setAmountMax(new BigDecimal("999"));
    }

    @Test
    @DisplayName("findFeeForAmount returns the matching tier")
    void findFeeForAmount_match_returnsFee() {
        when(feeRepository.findFeeForAmount(new BigDecimal("500"))).thenReturn(Optional.of(fee));

        assertThat(feeService.findFeeForAmount(new BigDecimal("500"))).isSameAs(fee);
    }

    @Test
    @DisplayName("findFeeForAmount throws when no tier matches")
    void findFeeForAmount_noMatch_throws() {
        when(feeRepository.findFeeForAmount(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feeService.findFeeForAmount(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No fee tier configured");
    }

    @Test
    @DisplayName("previewFee returns amount, fee and total")
    void previewFee_returnsBreakdown() {
        when(feeRepository.findFeeForAmount(new BigDecimal("500"))).thenReturn(Optional.of(fee));

        PaymentDto.FeePreviewResponse resp = feeService.previewFee(new BigDecimal("500"));

        assertThat(resp.getPaymentAmount()).isEqualByComparingTo("500");
        assertThat(resp.getFeeAmount()).isEqualByComparingTo("25");
        assertThat(resp.getTotalAmount()).isEqualByComparingTo("525");
    }
}