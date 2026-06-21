package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Fee;
import com.paymentapp.repository.FeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Owns fee-tier resolution and the fee preview shown before a payment.
 * Extracted from PaymentService so the fee concern is a distinct component
 * (mirrors the FeeService box in the architecture diagram).
 */
@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeRepository feeRepository;

    /** Resolves the fee tier covering {@code amount}, or throws if none is configured. */
    public Fee findFeeForAmount(BigDecimal amount) {
        return feeRepository.findFeeForAmount(amount)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No fee tier configured for amount: " + amount));
    }

    /** Builds the payment + fee + total preview for a given amount. */
    public PaymentDto.FeePreviewResponse previewFee(BigDecimal amount) {
        Fee fee = findFeeForAmount(amount);
        PaymentDto.FeePreviewResponse resp = new PaymentDto.FeePreviewResponse();
        resp.setPaymentAmount(amount);
        resp.setFeeAmount(fee.getFeeAmount());
        resp.setTotalAmount(amount.add(fee.getFeeAmount()));
        return resp;
    }
}