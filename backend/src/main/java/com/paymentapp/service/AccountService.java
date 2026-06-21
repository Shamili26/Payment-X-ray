package com.paymentapp.service;

import com.paymentapp.dto.PaymentDto;
import com.paymentapp.entity.Account;
import com.paymentapp.repository.AccountRepository;
import com.paymentapp.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns account access and balance movements. Extracted from PaymentService so
 * the account concern is a distinct component (mirrors the AccountService box in
 * the architecture diagram) and so balance debits/credits live in one place.
 *
 * The debit/credit methods are intentionally NOT annotated with their own
 * transaction boundary — they join the caller's transaction (e.g. the payment
 * create/update/delete), so a failure anywhere rolls the balance change back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrentUserProvider currentUserProvider;

    // ─── Lookups ─────────────────────────────────────────────────────────────

    /** Loads an account only if it belongs to the logged-in user, else 403. */
    public Account getOwnedAccountOrThrow(Long accountId) {
        Long userId = currentUserProvider.getCurrentUser().getUserId();
        return accountRepository.findByAccountIdAndUser_UserId(accountId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Account does not belong to the current user or does not exist: " + accountId));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.AccountResponse> findActiveAccounts() {
        Long userId = currentUserProvider.getCurrentUser().getUserId();
        return accountRepository.findByUser_UserIdAndAccountStatus(userId, "ACTIVE")
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Balance movements ───────────────────────────────────────────────────

    /** Fails fast (no state change) when the account cannot cover {@code amount}. */
    public void ensureSufficientBalance(Account account, BigDecimal amount) {
        if (account.getAccountBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance: account holds " + account.getAccountBalance()
                            + " but the payment requires " + amount);
        }
    }

    /** Subtracts {@code amount} from the balance, rejecting an overdraft. */
    public void debit(Account account, BigDecimal amount) {
        ensureSufficientBalance(account, amount);
        account.setAccountBalance(account.getAccountBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Debited {} from account {} (new balance {})",
                amount, account.getAccountId(), account.getAccountBalance());
    }

    /** Adds {@code amount} back to the balance (e.g. a refunded/cancelled payment). */
    public void credit(Account account, BigDecimal amount) {
        account.setAccountBalance(account.getAccountBalance().add(amount));
        accountRepository.save(account);
        log.info("Credited {} to account {} (new balance {})",
                amount, account.getAccountId(), account.getAccountBalance());
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    public PaymentDto.AccountResponse toResponse(Account a) {
        PaymentDto.AccountResponse r = new PaymentDto.AccountResponse();
        r.setAccountId(a.getAccountId());
        r.setAccountNumber(a.getAccountNumber());
        r.setAccountName(a.getAccountName());
        r.setAccountBalance(a.getAccountBalance());
        r.setAccountStatus(a.getAccountStatus());
        return r;
    }
}