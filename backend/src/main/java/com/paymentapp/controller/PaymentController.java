package com.paymentapp.controller;

import com.paymentapp.dto.Auth;
import com.paymentapp.dto.PaymentDto;
import com.paymentapp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ─── GET /api/payment — list all ─────────────────────────────────────────

    @GetMapping("/payment")
    public ResponseEntity<List<PaymentDto.PaymentResponse>> listAll() {
        return ResponseEntity.ok(paymentService.findAll());
    }

    // ─── GET /api/{payment-id}/payment — get one ──────────────────────────────

    @GetMapping("/{paymentId}/payment")
    public ResponseEntity<PaymentDto.PaymentResponse> getOne(
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.findById(paymentId));
    }

    // NOTE: There is intentionally no direct "POST /api/payment" create endpoint.
    // Every payment MUST go through the two-step MFA flow below
    // (/payment/initiate → /payment/verify) so OTP verification cannot be skipped.

    // ─── POST /api/payment/initiate — MFA step 1: send OTP ───────────────────

    @PostMapping("/payment/initiate")
    public ResponseEntity<PaymentDto.OtpChallengeResponse> initiate(
            @Valid @RequestBody PaymentDto.CreateRequest req) {
        PaymentDto.OtpChallengeResponse response = paymentService.initiatePayment(req);
        return ResponseEntity.ok(response);
    }

    // ─── POST /api/payment/verify — MFA step 2: verify OTP & create ──────────

    @PostMapping("/payment/verify")
    public ResponseEntity<PaymentDto.PaymentResponse> verify(
            @Valid @RequestBody PaymentDto.VerifyOtpRequest req) {
        PaymentDto.PaymentResponse response = paymentService.verifyOtpAndCreate(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── PUT /api/payment — update ────────────────────────────────────────────

    @PutMapping("/payment")
    public ResponseEntity<PaymentDto.PaymentResponse> update(
            @Valid @RequestBody PaymentDto.UpdateRequest req) {
        return ResponseEntity.ok(paymentService.update(req));
    }

    // ─── DELETE /api/{payment-id}/payment — delete ────────────────────────────

    @DeleteMapping("/{paymentId}/payment")
    public ResponseEntity<Void> delete(@PathVariable Long paymentId) {
        paymentService.delete(paymentId);
        return ResponseEntity.noContent().build();
    }

    // ─── GET /api/accounts — active from-accounts dropdown ───────────────────

    @GetMapping("/accounts")
    public ResponseEntity<List<PaymentDto.AccountResponse>> listAccounts() {
        return ResponseEntity.ok(paymentService.findActiveAccounts());
    }

    // ─── GET /api/payees — payees dropdown ───────────────────────────────────

    @GetMapping("/payees")
    public ResponseEntity<List<PaymentDto.PayeeResponse>> listPayees() {
        return ResponseEntity.ok(paymentService.findAllPayees());
    }

    // ─── GET /api/payment/fee?amount=X — fee preview ─────────────────────────

    @GetMapping("/payment/fee")
    public ResponseEntity<PaymentDto.FeePreviewResponse> previewFee(
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(paymentService.previewFee(amount));
    }

    // ─── Exception handlers ───────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Auth.ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new Auth.ApiError(400, "Validation Failed", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Auth.ApiError> handleBadArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new Auth.ApiError(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Auth.ApiError> handleBadState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Auth.ApiError(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Auth.ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Auth.ApiError(403, "Forbidden", ex.getMessage()));
    }
}
