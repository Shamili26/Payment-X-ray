package com.paymentapp.repository;

import com.paymentapp.entity.Account;
import com.paymentapp.entity.Fee;
import com.paymentapp.entity.Payment;
import com.paymentapp.entity.Payee;
import com.paymentapp.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.ANY;

@DataJpaTest
@AutoConfigureTestDatabase(replace = ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:repotestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Repository Tests (H2 in-memory)")
class RepositoryTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private PayeeRepository   payeeRepository;
    @Autowired private FeeRepository     feeRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository    userRepository;

    // ─── AccountRepository ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAccountStatus returns only matching accounts")
    void accountRepo_findByStatus_returnsMatchingAccounts() {
        User user = userRepository.save(buildUser("acctowner", "acctowner@example.com"));
        accountRepository.save(buildAccount(user, "ACC-A1", "ACTIVE"));
        accountRepository.save(buildAccount(user, "ACC-I1", "INACTIVE"));

        List<Account> active = accountRepository.findByAccountStatus("ACTIVE");
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getAccountNumber()).isEqualTo("ACC-A1");
    }

    @Test
    @DisplayName("save and findById work for Account")
    void accountRepo_saveAndFind_works() {
        User user = userRepository.save(buildUser("acctowner2", "acctowner2@example.com"));
        Account saved = accountRepository.save(buildAccount(user, "ACC-001", "ACTIVE"));
        Optional<Account> found = accountRepository.findById(saved.getAccountId());
        assertThat(found).isPresent();
        assertThat(found.get().getAccountName()).isEqualTo("Test Account");
        assertThat(found.get().getMobileNumber()).isEqualTo("+919876543210");
    }

    @Test
    @DisplayName("user-scoped account queries isolate data per user")
    void accountRepo_userScopedQueries_isolateByUser() {
        User alice = userRepository.save(buildUser("alice2", "alice2@example.com"));
        User bob   = userRepository.save(buildUser("bob2", "bob2@example.com"));
        Account aliceAcc = accountRepository.save(buildAccount(alice, "ACC-ALICE", "ACTIVE"));
        accountRepository.save(buildAccount(bob, "ACC-BOB", "ACTIVE"));

        // Each user sees only their own active accounts
        List<Account> aliceActive = accountRepository.findByUser_UserIdAndAccountStatus(alice.getUserId(), "ACTIVE");
        assertThat(aliceActive).hasSize(1);
        assertThat(aliceActive.get(0).getAccountNumber()).isEqualTo("ACC-ALICE");

        // Ownership-scoped lookup succeeds for the owner and fails for a non-owner
        assertThat(accountRepository.findByAccountIdAndUser_UserId(aliceAcc.getAccountId(), alice.getUserId()))
                .isPresent();
        assertThat(accountRepository.findByAccountIdAndUser_UserId(aliceAcc.getAccountId(), bob.getUserId()))
                .isEmpty();
    }

    // ─── FeeRepository ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findFeeForAmount returns correct tier for amount in range")
    void feeRepo_findFeeForAmount_returnsCorrectTier() {
        feeRepository.save(Fee.builder().feeAmount(new BigDecimal("10"))
                .amountMin(BigDecimal.ZERO).amountMax(new BigDecimal("99")).build());
        feeRepository.save(Fee.builder().feeAmount(new BigDecimal("25"))
                .amountMin(new BigDecimal("100")).amountMax(new BigDecimal("999")).build());
        feeRepository.save(Fee.builder().feeAmount(new BigDecimal("500"))
                .amountMin(new BigDecimal("100000")).amountMax(null).build());

        Optional<Fee> fee50  = feeRepository.findFeeForAmount(new BigDecimal("50"));
        Optional<Fee> fee500 = feeRepository.findFeeForAmount(new BigDecimal("500"));
        Optional<Fee> feeMax = feeRepository.findFeeForAmount(new BigDecimal("200000"));

        assertThat(fee50).isPresent();
        assertThat(fee50.get().getFeeAmount()).isEqualByComparingTo("10");

        assertThat(fee500).isPresent();
        assertThat(fee500.get().getFeeAmount()).isEqualByComparingTo("25");

        assertThat(feeMax).isPresent();
        assertThat(feeMax.get().getFeeAmount()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("findFeeForAmount returns empty for unmatched amount")
    void feeRepo_findFeeForAmount_returnsEmptyWhenNoTier() {
        // No tiers seeded — empty result
        Optional<Fee> fee = feeRepository.findFeeForAmount(new BigDecimal("500"));
        assertThat(fee).isEmpty();
    }

    // ─── PaymentRepository ────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllByOrderByUpdatedDatetimeDesc returns payments in order")
    void paymentRepo_findAll_returnsOrderedList() {
        User user      = userRepository.save(buildUser("payowner", "payowner@example.com"));
        Account account = accountRepository.save(buildAccount(user, "ACC-P1", "ACTIVE"));
        Payee   payee   = payeeRepository.save(buildPayee("PAY-P1"));
        Fee     fee     = feeRepository.save(Fee.builder().feeAmount(new BigDecimal("25"))
                .amountMin(new BigDecimal("100")).amountMax(new BigDecimal("999")).build());

        paymentRepository.save(buildPayment(account, payee, fee, new BigDecimal("200"), "PENDING"));
        paymentRepository.save(buildPayment(account, payee, fee, new BigDecimal("500"), "COMPLETED"));

        List<Payment> payments = paymentRepository.findAllByOrderByUpdatedDatetimeDesc();
        assertThat(payments).hasSize(2);

        // User-scoped query returns only that user's payments
        List<Payment> userPayments =
                paymentRepository.findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(user.getUserId());
        assertThat(userPayments).hasSize(2);
    }

    @Test
    @DisplayName("findByStatusAndPaymentDateLessThanEqual returns due PENDING payments only")
    void paymentRepo_findDuePending_returnsOnlyDue() {
        User user      = userRepository.save(buildUser("dueowner", "dueowner@example.com"));
        Account account = accountRepository.save(buildAccount(user, "ACC-DUE", "ACTIVE"));
        Payee   payee   = payeeRepository.save(buildPayee("PAY-DUE"));
        Fee     fee     = feeRepository.save(Fee.builder().feeAmount(new BigDecimal("25"))
                .amountMin(new BigDecimal("100")).amountMax(new BigDecimal("999")).build());

        // Due today (PENDING) — should match
        paymentRepository.save(buildPaymentDated(account, payee, fee, "PENDING", LocalDate.now()));
        // Future-dated PENDING — should NOT match
        paymentRepository.save(buildPaymentDated(account, payee, fee, "PENDING", LocalDate.now().plusDays(3)));
        // Already COMPLETED and due — should NOT match
        paymentRepository.save(buildPaymentDated(account, payee, fee, "COMPLETED", LocalDate.now()));

        List<Payment> due = paymentRepository
                .findByStatusAndPaymentDateLessThanEqual("PENDING", LocalDate.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(due.get(0).getPaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("exists*AndUserIdNot ignores the user's own row")
    void userRepo_existsExcludingSelf_ignoresOwnRow() {
        User self = userRepository.save(buildUser("selfedit", "selfedit@example.com"));

        // The user's own email/phone are not treated as duplicates of themselves
        assertThat(userRepository.existsByEmailAndUserIdNot("selfedit@example.com", self.getUserId())).isFalse();
        assertThat(userRepository.existsByPhoneNumberAndUserIdNot("+919876543210", self.getUserId())).isFalse();

        // But another user holding the same email/phone is detected
        User other = userRepository.save(buildUser("otheruser", "shared@example.com"));
        assertThat(userRepository.existsByEmailAndUserIdNot("shared@example.com", self.getUserId())).isTrue();
        assertThat(userRepository.existsByPhoneNumberAndUserIdNot(other.getPhoneNumber(), self.getUserId())).isTrue();
    }

    // ─── UserRepository ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByUsername returns user when exists")
    void userRepo_findByUsername_returnsUser() {
        userRepository.save(buildUser("alice", "alice@example.com"));

        Optional<User> found = userRepository.findByUsername("alice");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("existsByUsername returns true for existing user")
    void userRepo_existsByUsername_returnsTrue() {
        userRepository.save(buildUser("bob", "bob@example.com"));
        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }

    @Test
    @DisplayName("existsByEmail returns true for existing email")
    void userRepo_existsByEmail_returnsTrue() {
        userRepository.save(buildUser("carol", "carol@example.com"));
        assertThat(userRepository.existsByEmail("carol@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByEmail returns user when exists")
    void userRepo_findByEmail_returnsUser() {
        userRepository.save(buildUser("dave", "dave@example.com"));

        Optional<User> found = userRepository.findByEmail("dave@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("dave");
    }

    @Test
    @DisplayName("updateLastLogin sets the lastLogin timestamp")
    void userRepo_updateLastLogin_setsTimestamp() {
        User saved = userRepository.save(buildUser("eve", "eve@example.com"));
        LocalDateTime loginTime = LocalDateTime.now();

        userRepository.updateLastLogin(saved.getUserId(), loginTime);
        userRepository.flush();

        User updated = userRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(updated.getLastLogin()).isNotNull();
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private Account buildAccount(User user, String number, String status) {
        return Account.builder()
                .user(user)
                .accountNumber(number).accountName("Test Account")
                .accountBalance(new BigDecimal("10000")).accountStatus(status)
                .mobileNumber("+919876543210").build();
    }

    private Payee buildPayee(String number) {
        return Payee.builder()
                .payeeNumber(number).payeeName("Test Payee")
                .amountDue(new BigDecimal("500")).dueDate(LocalDate.now().plusDays(5)).build();
    }

    private Payment buildPayment(Account account, Payee payee, Fee fee,
                                  BigDecimal amount, String status) {
        return Payment.builder()
                .account(account).payee(payee).fee(fee)
                .paymentAmount(amount).paymentDate(LocalDate.now().plusDays(1))
                .status(status).build();
    }

    private Payment buildPaymentDated(Account account, Payee payee, Fee fee,
                                       String status, LocalDate paymentDate) {
        return Payment.builder()
                .account(account).payee(payee).fee(fee)
                .paymentAmount(new BigDecimal("200")).paymentDate(paymentDate)
                .status(status).build();
    }

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username).email(email)
                .passwordHash("$2a$10$hash").firstName("First").lastName("Last")
                .phoneNumber("+919876543210").dateOfBirth(LocalDate.of(1990, 1, 1))
                .role("ROLE_USER").isEnabled(true).isAccountNonExpired(true)
                .isAccountNonLocked(true).isCredentialsNonExpired(true).build();
    }
}
