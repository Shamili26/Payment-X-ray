package com.paymentapp.repository;

import com.paymentapp.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByAccountStatus(String status);

    // Uniqueness check for the 16-digit account number captured at registration
    boolean existsByAccountNumber(String accountNumber);

    // User-scoped lookups for per-user data isolation
    List<Account> findByUser_UserIdAndAccountStatus(Long userId, String status);

    List<Account> findByUser_UserId(Long userId);

    Optional<Account> findByAccountIdAndUser_UserId(Long accountId, Long userId);
}
