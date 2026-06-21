package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Payee;
import com.paymentapp.repository.PayeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns payee lookups. Extracted from PaymentService so the payee concern is a
 * distinct component (mirrors the PayeeService box in the architecture diagram).
 */
@Service
@RequiredArgsConstructor
public class PayeeService {

    private final PayeeRepository payeeRepository;

    /** Loads a payee by id, or throws if it does not exist. */
    public Payee getByIdOrThrow(Long payeeId) {
        return payeeRepository.findById(payeeId)
                .orElseThrow(() -> new IllegalArgumentException("Payee not found: " + payeeId));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.PayeeResponse> findAll() {
        return payeeRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PaymentDto.PayeeResponse toResponse(Payee p) {
        PaymentDto.PayeeResponse r = new PaymentDto.PayeeResponse();
        r.setPayeeId(p.getPayeeId());
        r.setPayeeNumber(p.getPayeeNumber());
        r.setPayeeName(p.getPayeeName());
        r.setAmountDue(p.getAmountDue());
        r.setDueDate(p.getDueDate());
        return r;
    }
}