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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository      paymentRepository;
    @Mock private AccountService         accountService;
    @Mock private PayeeService           payeeService;
    @Mock private FeeService             feeService;
    @Mock private CurrentUserProvider    currentUserProvider;
    @Mock private OtpChallengeRepository otpChallengeRepository;
    @Mock private SmsSender              smsSender;
    @Mock private PasswordEncoder        passwordEncoder;

    @InjectMocks
    private PaymentService paymentService;

    private User     testUser;
    private Account  testAccount;
    private Payee    testPayee;
    private Fee      testFee;
    private Payment  testPayment;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setUsername("testuser");

        testAccount = new Account();
        testAccount.setAccountId(1L);
        testAccount.setUser(testUser);
        testAccount.setAccountNumber("ACC-001");
        testAccount.setAccountName("Savings");
        testAccount.setAccountBalance(new BigDecimal("50000"));
        testAccount.setAccountStatus("ACTIVE");
        testAccount.setMobileNumber("+919876543210");

        testPayee = new Payee();
        testPayee.setPayeeId(1L);
        testPayee.setPayeeNumber("PAY-001");
        testPayee.setPayeeName("Airtel Mobile");
        testPayee.setAmountDue(new BigDecimal("499"));

        testFee = new Fee();
        testFee.setFeeId(2L);
        testFee.setFeeAmount(new BigDecimal("25"));
        testFee.setAmountMin(new BigDecimal("100"));
        testFee.setAmountMax(new BigDecimal("999"));

        testPayment = new Payment();
        testPayment.setPaymentId(1L);
        testPayment.setAccount(testAccount);
        testPayment.setPayee(testPayee);
        testPayment.setFee(testFee);
        testPayment.setPaymentAmount(new BigDecimal("500"));
        testPayment.setPaymentDate(LocalDate.now().plusDays(1));
        testPayment.setMemo("Test payment");
        testPayment.setStatus("PENDING");
        testPayment.setUpdatedDatetime(LocalDateTime.now());

        // Most tests operate as the logged-in test user
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
        testUser.setPhoneNumber("+919876543210");

        // MFA config (mirrors application.properties defaults)
        ReflectionTestUtils.setField(paymentService, "otpExpirySeconds", 300L);
        ReflectionTestUtils.setField(paymentService, "otpMaxAttempts", 3);
    }

    // ─── previewFee ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("previewFee delegates to FeeService")
    void previewFee_delegatesToFeeService() {
        PaymentDto.FeePreviewResponse expected = new PaymentDto.FeePreviewResponse();
        expected.setPaymentAmount(new BigDecimal("500"));
        expected.setFeeAmount(new BigDecimal("25"));
        expected.setTotalAmount(new BigDecimal("525"));
        when(feeService.previewFee(new BigDecimal("500"))).thenReturn(expected);

        PaymentDto.FeePreviewResponse resp = paymentService.previewFee(new BigDecimal("500"));

        assertThat(resp.getTotalAmount()).isEqualByComparingTo("525");
        verify(feeService).previewFee(new BigDecimal("500"));
    }

    // ─── findAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns only the current user's payments")
    void findAll_returnsCurrentUserPayments() {
        when(paymentRepository.findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(1L))
                .thenReturn(List.of(testPayment));

        List<PaymentDto.PaymentResponse> result = paymentService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPayeeName()).isEqualTo("Airtel Mobile");
        assertThat(result.get(0).getAccountName()).isEqualTo("Savings");
        verify(paymentRepository).findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(1L);
    }

    @Test
    @DisplayName("findAll returns empty list when user has no payments")
    void findAll_noPayments_returnsEmptyList() {
        when(paymentRepository.findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(1L))
                .thenReturn(List.of());

        assertThat(paymentService.findAll()).isEmpty();
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns payment when owned by user")
    void findById_existingId_returnsPayment() {
        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L))
                .thenReturn(Optional.of(testPayment));

        PaymentDto.PaymentResponse resp = paymentService.findById(1L);

        assertThat(resp.getPaymentId()).isEqualTo(1L);
        assertThat(resp.getMemo()).isEqualTo("Test payment");
    }

    @Test
    @DisplayName("findById denied when payment is not owned by the user")
    void findById_notOwned_throwsAccessDenied() {
        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not belong to the current user");
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update re-balances accounts and saves for a PENDING payment")
    void update_pendingPayment_rebalancesAndSaves() {
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(1L);
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("800"));
        req.setPaymentDate(LocalDate.now().plusDays(2));
        req.setMemo("Updated memo");

        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L)).thenReturn(Optional.of(testPayment));
        when(accountService.getOwnedAccountOrThrow(1L)).thenReturn(testAccount);
        when(payeeService.getByIdOrThrow(1L)).thenReturn(testPayee);
        when(feeService.findFeeForAmount(any())).thenReturn(testFee);
        when(paymentRepository.save(any())).thenReturn(testPayment);

        PaymentDto.PaymentResponse resp = paymentService.update(req);

        assertThat(resp).isNotNull();
        // Original total (500 + 25) refunded, new total (800 + 25) debited
        verify(accountService).credit(testAccount, new BigDecimal("525"));
        verify(accountService).debit(testAccount, new BigDecimal("825"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("update denied when payment is not owned by the user")
    void update_notOwned_throwsAccessDenied() {
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(99L);
        req.setPaymentDate(LocalDate.now().plusDays(1));
        req.setPaymentAmount(new BigDecimal("500"));

        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.update(req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not belong to the current user");
        verify(paymentRepository, never()).save(any());
        verify(accountService, never()).debit(any(), any());
    }

    @Test
    @DisplayName("update throws for COMPLETED payment")
    void update_completedPayment_throwsException() {
        testPayment.setStatus("COMPLETED");
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(1L);
        req.setPaymentDate(LocalDate.now().plusDays(1));
        req.setPaymentAmount(new BigDecimal("500"));

        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L)).thenReturn(Optional.of(testPayment));

        assertThatThrownBy(() -> paymentService.update(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Completed payments cannot be modified");
    }

    @Test
    @DisplayName("update throws for past payment date")
    void update_pastDate_throwsException() {
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(1L);
        req.setPaymentDate(LocalDate.now().minusDays(1));
        req.setPaymentAmount(new BigDecimal("500"));

        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L)).thenReturn(Optional.of(testPayment));

        assertThatThrownBy(() -> paymentService.update(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be in the past");
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete refunds the reserved funds and removes a PENDING payment")
    void delete_pendingPayment_refundsAndDeletes() {
        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L)).thenReturn(Optional.of(testPayment));

        paymentService.delete(1L);

        // amount (500) + fee (25) credited back before deletion
        verify(accountService).credit(testAccount, new BigDecimal("525"));
        verify(paymentRepository).delete(testPayment);
    }

    @Test
    @DisplayName("delete throws for COMPLETED payment")
    void delete_completedPayment_throwsException() {
        testPayment.setStatus("COMPLETED");
        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(1L, 1L)).thenReturn(Optional.of(testPayment));

        assertThatThrownBy(() -> paymentService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Completed payments cannot be deleted");
        verify(paymentRepository, never()).delete(any());
        verify(accountService, never()).credit(any(), any());
    }

    @Test
    @DisplayName("delete denied when payment is not owned by the user")
    void delete_notOwned_throwsAccessDenied() {
        when(paymentRepository.findByPaymentIdAndAccount_User_UserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.delete(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not belong to the current user");
        verify(paymentRepository, never()).delete(any());
    }

    // ─── findActiveAccounts / findAllPayees ───────────────────────────────────

    @Test
    @DisplayName("findActiveAccounts delegates to AccountService")
    void findActiveAccounts_delegates() {
        PaymentDto.AccountResponse acc = new PaymentDto.AccountResponse();
        acc.setAccountName("Savings");
        when(accountService.findActiveAccounts()).thenReturn(List.of(acc));

        List<PaymentDto.AccountResponse> result = paymentService.findActiveAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountName()).isEqualTo("Savings");
        verify(accountService).findActiveAccounts();
    }

    @Test
    @DisplayName("findAllPayees delegates to PayeeService")
    void findAllPayees_delegates() {
        PaymentDto.PayeeResponse p = new PaymentDto.PayeeResponse();
        p.setPayeeName("Airtel Mobile");
        when(payeeService.findAll()).thenReturn(List.of(p));

        List<PaymentDto.PayeeResponse> result = paymentService.findAllPayees();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPayeeName()).isEqualTo("Airtel Mobile");
    }

    // ─── MFA: initiatePayment (send OTP) ──────────────────────────────────────

    @Test
    @DisplayName("initiatePayment validates balance, sends OTP, and stores a challenge")
    void initiatePayment_validRequest_sendsOtpAndStoresChallenge() {
        PaymentDto.CreateRequest req = new PaymentDto.CreateRequest();
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("500"));
        req.setPaymentDate(LocalDate.now().plusDays(1));
        req.setMemo("Test");

        when(accountService.getOwnedAccountOrThrow(1L)).thenReturn(testAccount);
        when(payeeService.getByIdOrThrow(1L)).thenReturn(testPayee);
        when(feeService.findFeeForAmount(any())).thenReturn(testFee);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedotp");

        PaymentDto.OtpChallengeResponse resp = paymentService.initiatePayment(req);

        assertThat(resp.getChallengeId()).isNotBlank();
        assertThat(resp.getMaskedMobile()).endsWith("3210");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(300L);

        // Balance must be checked before an OTP is dispatched (amount + fee = 525)
        verify(accountService).ensureSufficientBalance(testAccount, new BigDecimal("525"));
        verify(smsSender).sendOtp(eq("+919876543210"), anyString());
        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(otpChallengeRepository).save(captor.capture());
        assertThat(captor.getValue().getOtpHash()).isEqualTo("$2a$10$hashedotp");
        assertThat(captor.getValue().getPaymentAmount()).isEqualByComparingTo("500");
        // No payment is created during initiate, and nothing is debited yet
        verify(paymentRepository, never()).save(any());
        verify(accountService, never()).debit(any(), any());
    }

    @Test
    @DisplayName("initiatePayment denied when account is not owned by the user")
    void initiatePayment_accountNotOwned_throwsAccessDenied() {
        PaymentDto.CreateRequest req = new PaymentDto.CreateRequest();
        req.setAccountId(99L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("500"));
        req.setPaymentDate(LocalDate.now().plusDays(1));

        when(accountService.getOwnedAccountOrThrow(99L))
                .thenThrow(new AccessDeniedException("not owned"));

        assertThatThrownBy(() -> paymentService.initiatePayment(req))
                .isInstanceOf(AccessDeniedException.class);
        verify(smsSender, never()).sendOtp(anyString(), anyString());
        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("initiatePayment rejected when the account cannot cover amount + fee")
    void initiatePayment_insufficientBalance_throwsAndSendsNoOtp() {
        PaymentDto.CreateRequest req = new PaymentDto.CreateRequest();
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("500"));
        req.setPaymentDate(LocalDate.now().plusDays(1));

        when(accountService.getOwnedAccountOrThrow(1L)).thenReturn(testAccount);
        when(payeeService.getByIdOrThrow(1L)).thenReturn(testPayee);
        when(feeService.findFeeForAmount(any())).thenReturn(testFee);
        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(accountService).ensureSufficientBalance(eq(testAccount), any());

        assertThatThrownBy(() -> paymentService.initiatePayment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
        verify(smsSender, never()).sendOtp(anyString(), anyString());
        verify(otpChallengeRepository, never()).save(any());
    }

    // ─── MFA: verifyOtpAndCreate ──────────────────────────────────────────────

    @Test
    @DisplayName("verifyOtpAndCreate debits the account and creates the payment on a correct OTP")
    void verifyOtp_correctCode_debitsAndCreatesPayment() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("chal-1");
        req.setOtp("123456");

        OtpChallenge challenge = buildChallenge("chal-1", LocalDateTime.now().plusMinutes(5), 0, false);

        when(otpChallengeRepository.findByChallengeIdAndUserId("chal-1", 1L)).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "$2a$10$hashedotp")).thenReturn(true);
        when(accountService.getOwnedAccountOrThrow(1L)).thenReturn(testAccount);
        when(payeeService.getByIdOrThrow(1L)).thenReturn(testPayee);
        when(feeService.findFeeForAmount(any())).thenReturn(testFee);
        when(paymentRepository.save(any())).thenReturn(testPayment);

        PaymentDto.PaymentResponse resp = paymentService.verifyOtpAndCreate(req);

        assertThat(resp.getPaymentId()).isEqualTo(1L);
        assertThat(resp.getStatus()).isEqualTo("PENDING");
        // amount (500) + fee (25) debited at creation time
        verify(accountService).debit(testAccount, new BigDecimal("525"));
        verify(paymentRepository).save(any(Payment.class));
        verify(otpChallengeRepository).delete(challenge);   // one challenge → one payment
    }

    @Test
    @DisplayName("verifyOtpAndCreate does not create a payment when the debit fails (insufficient funds)")
    void verifyOtp_insufficientFunds_throwsAndCreatesNoPayment() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("chal-1");
        req.setOtp("123456");

        OtpChallenge challenge = buildChallenge("chal-1", LocalDateTime.now().plusMinutes(5), 0, false);

        when(otpChallengeRepository.findByChallengeIdAndUserId("chal-1", 1L)).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "$2a$10$hashedotp")).thenReturn(true);
        when(accountService.getOwnedAccountOrThrow(1L)).thenReturn(testAccount);
        when(payeeService.getByIdOrThrow(1L)).thenReturn(testPayee);
        when(feeService.findFeeForAmount(any())).thenReturn(testFee);
        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(accountService).debit(eq(testAccount), any());

        assertThatThrownBy(() -> paymentService.verifyOtpAndCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifyOtpAndCreate rejects an incorrect OTP and counts the attempt")
    void verifyOtp_wrongCode_throwsAndIncrementsAttempts() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("chal-1");
        req.setOtp("000000");

        OtpChallenge challenge = buildChallenge("chal-1", LocalDateTime.now().plusMinutes(5), 0, false);

        when(otpChallengeRepository.findByChallengeIdAndUserId("chal-1", 1L)).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("000000", "$2a$10$hashedotp")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.verifyOtpAndCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incorrect OTP");

        assertThat(challenge.getAttempts()).isEqualTo(1);
        verify(otpChallengeRepository).save(challenge);      // attempt persisted, not deleted
        verify(paymentRepository, never()).save(any());
        verify(accountService, never()).debit(any(), any());
    }

    @Test
    @DisplayName("verifyOtpAndCreate rejects an expired OTP and deletes the challenge")
    void verifyOtp_expired_throwsAndDeletesChallenge() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("chal-1");
        req.setOtp("123456");

        OtpChallenge challenge = buildChallenge("chal-1", LocalDateTime.now().minusSeconds(1), 0, false);

        when(otpChallengeRepository.findByChallengeIdAndUserId("chal-1", 1L)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> paymentService.verifyOtpAndCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");

        verify(otpChallengeRepository).delete(challenge);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifyOtpAndCreate rejects an unknown challenge id")
    void verifyOtp_unknownChallenge_throws() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("missing");
        req.setOtp("123456");

        when(otpChallengeRepository.findByChallengeIdAndUserId("missing", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verifyOtpAndCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or unknown OTP request");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifyOtpAndCreate rejects an already-used OTP")
    void verifyOtp_alreadyConsumed_throws() {
        PaymentDto.VerifyOtpRequest req = new PaymentDto.VerifyOtpRequest();
        req.setChallengeId("chal-1");
        req.setOtp("123456");

        OtpChallenge challenge = buildChallenge("chal-1", LocalDateTime.now().plusMinutes(5), 0, true);

        when(otpChallengeRepository.findByChallengeIdAndUserId("chal-1", 1L)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> paymentService.verifyOtpAndCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");
        verify(paymentRepository, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private OtpChallenge buildChallenge(String id, LocalDateTime expiresAt, int attempts, boolean consumed) {
        return OtpChallenge.builder()
                .challengeId(id)
                .userId(1L)
                .otpHash("$2a$10$hashedotp")
                .mobileNumber("+919876543210")
                .accountId(1L)
                .payeeId(1L)
                .paymentAmount(new BigDecimal("500"))
                .paymentDate(LocalDate.now().plusDays(1))
                .memo("Test")
                .expiresAt(expiresAt)
                .attempts(attempts)
                .consumed(consumed)
                .build();
    }
}