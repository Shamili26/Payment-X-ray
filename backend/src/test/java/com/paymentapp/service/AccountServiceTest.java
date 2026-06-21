package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Account;
import com.paymentapp.entity.User;
import com.paymentapp.repository.AccountRepository;
import com.paymentapp.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository   accountRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AccountService accountService;

    private User    testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);

        testAccount = new Account();
        testAccount.setAccountId(1L);
        testAccount.setUser(testUser);
        testAccount.setAccountNumber("ACC-001");
        testAccount.setAccountName("Savings");
        testAccount.setAccountBalance(new BigDecimal("1000.00"));
        testAccount.setAccountStatus("ACTIVE");

        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    @DisplayName("getOwnedAccountOrThrow returns the account when owned")
    void getOwnedAccount_owned_returnsAccount() {
        when(accountRepository.findByAccountIdAndUser_UserId(1L, 1L)).thenReturn(Optional.of(testAccount));

        assertThat(accountService.getOwnedAccountOrThrow(1L)).isSameAs(testAccount);
    }

    @Test
    @DisplayName("getOwnedAccountOrThrow throws 403 when not owned")
    void getOwnedAccount_notOwned_throws() {
        when(accountRepository.findByAccountIdAndUser_UserId(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getOwnedAccountOrThrow(2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("findActiveAccounts maps the current user's ACTIVE accounts")
    void findActiveAccounts_mapsResponses() {
        when(accountRepository.findByUser_UserIdAndAccountStatus(1L, "ACTIVE")).thenReturn(List.of(testAccount));

        List<PaymentDto.AccountResponse> result = accountService.findActiveAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountName()).isEqualTo("Savings");
        assertThat(result.get(0).getAccountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ensureSufficientBalance passes when balance covers the amount")
    void ensureSufficientBalance_enough_noThrow() {
        assertThatCode(() -> accountService.ensureSufficientBalance(testAccount, new BigDecimal("1000.00")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ensureSufficientBalance throws when balance is too low")
    void ensureSufficientBalance_tooLow_throws() {
        assertThatThrownBy(() -> accountService.ensureSufficientBalance(testAccount, new BigDecimal("1000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("debit subtracts from the balance and saves")
    void debit_reducesBalanceAndSaves() {
        accountService.debit(testAccount, new BigDecimal("250.00"));

        assertThat(testAccount.getAccountBalance()).isEqualByComparingTo("750.00");
        verify(accountRepository).save(testAccount);
    }

    @Test
    @DisplayName("debit rejects an overdraft and does not save")
    void debit_overdraft_throws() {
        assertThatThrownBy(() -> accountService.debit(testAccount, new BigDecimal("5000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(testAccount.getAccountBalance()).isEqualByComparingTo("1000.00");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit adds to the balance and saves")
    void credit_increasesBalanceAndSaves() {
        accountService.credit(testAccount, new BigDecimal("500.00"));

        assertThat(testAccount.getAccountBalance()).isEqualByComparingTo("1500.00");
        verify(accountRepository).save(testAccount);
    }
}