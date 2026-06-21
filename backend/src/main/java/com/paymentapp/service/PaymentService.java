package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Account;
import com.paymentapp.entity.Fee;
import com.paymentapp.entity.OtpChallenge;
import com.paymentapp.entity.Payment;
import com.paymentapp.entity.Payee;
import com.paymentapp.entity.User;
import com.paymentapp.repository.OtpChallengeRepository;
import com.paymentapp.repository.PaymentRepository;
import com.paymentapp.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountService accountService;
    private final PayeeService payeeService;
    private final FeeService feeService;
    private final CurrentUserProvider currentUserProvider;
    private final OtpChallengeRepository otpChallengeRepository;
    private final SmsSender smsSender;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.mfa.otp.expiry-seconds:300}")
    private long otpExpirySeconds;

    @Value("${app.mfa.otp.max-attempts:3}")
    private int otpMaxAttempts;

    // ─── Fee preview ─────────────────────────────────────────────────────────

    public PaymentDto.FeePreviewResponse previewFee(BigDecimal amount) {
        return feeService.previewFee(amount);
    }

    // NOTE: Payments are created ONLY after OTP verification, inside
    // verifyOtpAndCreate(). There is deliberately no MFA-free create() path —
    // removing it closes the bypass where a payment could be made without an OTP.

    // ─── MFA: initiate payment (send OTP) ─────────────────────────────────────

    /**
     * Step 1 of an MFA-gated payment. Validates the request exactly as a real
     * create would (including that the account can cover the amount + fee), then
     * dispatches a one-time OTP to the user's registered mobile number and stores
     * a short-lived, hashed challenge. The payment is NOT created here — only
     * after the OTP is verified.
     */
    @Transactional
    public PaymentDto.OtpChallengeResponse initiatePayment(PaymentDto.CreateRequest req) {
        User user = currentUserProvider.getCurrentUser();

        // Validate up front so the user isn't asked for an OTP on a bad payment
        validatePaymentDate(req.getPaymentDate());
        Account account = accountService.getOwnedAccountOrThrow(req.getAccountId());
        payeeService.getByIdOrThrow(req.getPayeeId());
        Fee fee = feeService.findFeeForAmount(req.getPaymentAmount());

        // Reject early if the account cannot cover amount + fee, so we don't
        // dispatch an OTP for a payment that can never complete.
        accountService.ensureSufficientBalance(account, req.getPaymentAmount().add(fee.getFeeAmount()));

        // Resolve the destination mobile: prefer the account's mobile, else the user's
        String mobile = account.getMobileNumber() != null && !account.getMobileNumber().isBlank()
                ? account.getMobileNumber()
                : user.getPhoneNumber();
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalStateException("No registered mobile number found to send the OTP");
        }

        String otp = generateOtp();
        OtpChallenge challenge = OtpChallenge.builder()
                .challengeId(UUID.randomUUID().toString())
                .userId(user.getUserId())
                .otpHash(passwordEncoder.encode(otp))
                .mobileNumber(mobile)
                .accountId(req.getAccountId())
                .payeeId(req.getPayeeId())
                .paymentAmount(req.getPaymentAmount())
                .paymentDate(req.getPaymentDate())
                .memo(req.getMemo())
                .expiresAt(LocalDateTime.now().plusSeconds(otpExpirySeconds))
                .attempts(0)
                .consumed(false)
                .build();
        otpChallengeRepository.save(challenge);

        // Deliver the OTP (dev: logged; prod: real SMS gateway)
        smsSender.sendOtp(mobile, otp);
        log.info("OTP challenge {} created for user {} (payment of {})",
                challenge.getChallengeId(), user.getUsername(), req.getPaymentAmount());

        PaymentDto.OtpChallengeResponse resp = new PaymentDto.OtpChallengeResponse();
        resp.setChallengeId(challenge.getChallengeId());
        resp.setMaskedMobile(maskMobile(mobile));
        resp.setExpiresInSeconds(otpExpirySeconds);
        resp.setMessage("An OTP has been sent to your registered mobile number " + maskMobile(mobile));
        return resp;
    }

    // ─── MFA: verify OTP and create the payment ───────────────────────────────

    /**
     * Step 2 of an MFA-gated payment. Verifies the OTP against the stored,
     * hashed challenge. The payment is created only when verification succeeds.
     * Handles expiry, too many attempts, already-used and wrong-code cases.
     */
    @Transactional
    public PaymentDto.PaymentResponse verifyOtpAndCreate(PaymentDto.VerifyOtpRequest req) {
        User user = currentUserProvider.getCurrentUser();

        OtpChallenge challenge = otpChallengeRepository
                .findByChallengeIdAndUserId(req.getChallengeId(), user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unknown OTP request. Please restart the payment."));

        if (challenge.isConsumed()) {
            throw new IllegalArgumentException("This OTP has already been used. Please restart the payment.");
        }
        if (LocalDateTime.now().isAfter(challenge.getExpiresAt())) {
            otpChallengeRepository.delete(challenge);
            throw new IllegalArgumentException("OTP has expired. Please restart the payment to receive a new code.");
        }
        if (challenge.getAttempts() >= otpMaxAttempts) {
            otpChallengeRepository.delete(challenge);
            throw new IllegalArgumentException("Too many incorrect attempts. Please restart the payment.");
        }

        // Wrong code: count the attempt and reject (deleting once the cap is hit)
        if (!passwordEncoder.matches(req.getOtp(), challenge.getOtpHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            int remaining = otpMaxAttempts - challenge.getAttempts();
            if (remaining <= 0) {
                otpChallengeRepository.delete(challenge);
                throw new IllegalArgumentException("Incorrect OTP. Too many incorrect attempts — please restart the payment.");
            }
            otpChallengeRepository.save(challenge);
            throw new IllegalArgumentException("Incorrect OTP. You have " + remaining + " attempt(s) remaining.");
        }

        // Success — mark consumed so the same code can never be reused
        challenge.setConsumed(true);
        otpChallengeRepository.save(challenge);

        // Re-validate and create the payment using the stored, server-side data
        validatePaymentDate(challenge.getPaymentDate());
        Account account = accountService.getOwnedAccountOrThrow(challenge.getAccountId());
        Payee payee = payeeService.getByIdOrThrow(challenge.getPayeeId());
        Fee fee = feeService.findFeeForAmount(challenge.getPaymentAmount());

        // Debit the account for amount + fee. This reserves the funds at creation
        // time and rejects the payment (rolling back the whole transaction) if the
        // balance can't cover it.
        BigDecimal total = challenge.getPaymentAmount().add(fee.getFeeAmount());
        accountService.debit(account, total);

        Payment payment = Payment.builder()
                .account(account)
                .payee(payee)
                .fee(fee)
                .paymentAmount(challenge.getPaymentAmount())
                .paymentDate(challenge.getPaymentDate())
                .memo(challenge.getMemo())
                .status("PENDING")
                .build();

        Payment saved = paymentRepository.save(payment);
        otpChallengeRepository.delete(challenge);   // one challenge → one payment
        log.info("Payment created after OTP verification: id={} by user={} (debited {})",
                saved.getPaymentId(), user.getUsername(), total);
        return toResponse(saved);
    }

    // ─── Read all ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentDto.PaymentResponse> findAll() {
        Long userId = currentUserProvider.getCurrentUser().getUserId();
        return paymentRepository.findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Read one ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentDto.PaymentResponse findById(Long id) {
        return toResponse(getOwnedPaymentOrThrow(id));
    }

    // ─── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public PaymentDto.PaymentResponse update(PaymentDto.UpdateRequest req) {
        // Only the owner can load (and therefore modify) the payment
        Payment payment = getOwnedPaymentOrThrow(req.getPaymentId());

        if ("COMPLETED".equals(payment.getStatus())) {
            throw new IllegalStateException("Completed payments cannot be modified");
        }

        validatePaymentDate(req.getPaymentDate());

        Account newAccount = accountService.getOwnedAccountOrThrow(req.getAccountId());
        Payee payee = payeeService.getByIdOrThrow(req.getPayeeId());
        Fee newFee = feeService.findFeeForAmount(req.getPaymentAmount());

        // Re-balance: refund the original debit (amount + original fee) to the
        // original account, then debit the new account for the new total. If the
        // new debit can't be covered, the whole transaction rolls back — including
        // the refund — so balances stay consistent.
        BigDecimal oldTotal = payment.getPaymentAmount().add(payment.getFee().getFeeAmount());
        BigDecimal newTotal = req.getPaymentAmount().add(newFee.getFeeAmount());
        accountService.credit(payment.getAccount(), oldTotal);
        accountService.debit(newAccount, newTotal);

        payment.setAccount(newAccount);
        payment.setPayee(payee);
        payment.setFee(newFee);
        payment.setPaymentAmount(req.getPaymentAmount());
        payment.setPaymentDate(req.getPaymentDate());
        payment.setMemo(req.getMemo());

        Payment saved = paymentRepository.save(payment);
        log.info("Payment updated: id={}", saved.getPaymentId());
        return toResponse(saved);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        // Only the owner can load (and therefore delete) the payment
        Payment payment = getOwnedPaymentOrThrow(id);
        if ("COMPLETED".equals(payment.getStatus())) {
            throw new IllegalStateException("Completed payments cannot be deleted");
        }
        // Refund the reserved funds (amount + fee) before removing the payment.
        BigDecimal total = payment.getPaymentAmount().add(payment.getFee().getFeeAmount());
        accountService.credit(payment.getAccount(), total);
        paymentRepository.delete(payment);
        log.info("Payment deleted: id={} (refunded {})", id, total);
    }

    // ─── Account & Payee lookups ──────────────────────────────────────────────

    public List<PaymentDto.AccountResponse> findActiveAccounts() {
        return accountService.findActiveAccounts();
    }

    public List<PaymentDto.PayeeResponse> findAllPayees() {
        return payeeService.findAll();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Loads a payment only if it belongs to the logged-in user, else 403. */
    private Payment getOwnedPaymentOrThrow(Long paymentId) {
        Long userId = currentUserProvider.getCurrentUser().getUserId();
        return paymentRepository.findByPaymentIdAndAccount_User_UserId(paymentId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Payment does not belong to the current user or does not exist: " + paymentId));
    }

    private void validatePaymentDate(LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Payment date cannot be in the past");
        }
    }

    /** Cryptographically-strong 6-digit OTP (000000–999999). */
    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Masks a mobile number, keeping the country code and last 4 digits. */
    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        String last4 = mobile.substring(mobile.length() - 4);
        String prefix = mobile.startsWith("+91") ? "+91" : "";
        return prefix + "●●●●●●" + last4;
    }

    private PaymentDto.PaymentResponse toResponse(Payment p) {
        PaymentDto.PaymentResponse r = new PaymentDto.PaymentResponse();
        r.setPaymentId(p.getPaymentId());
        r.setAccountId(p.getAccount().getAccountId());
        r.setAccountName(p.getAccount().getAccountName());
        r.setAccountNumber(p.getAccount().getAccountNumber());
        r.setPayeeId(p.getPayee().getPayeeId());
        r.setPayeeName(p.getPayee().getPayeeName());
        r.setPayeeNumber(p.getPayee().getPayeeNumber());
        r.setPaymentAmount(p.getPaymentAmount());
        r.setFeeAmount(p.getFee().getFeeAmount());
        r.setPaymentDate(p.getPaymentDate());
        r.setMemo(p.getMemo());
        r.setStatus(p.getStatus());
        r.setUpdatedDatetime(p.getUpdatedDatetime());
        return r;
    }
}