package com.paymentapp.repository;

import com.paymentapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    // Uniqueness checks that exclude the user themselves (used when editing a profile)
    boolean existsByEmailAndUserIdNot(String email, Long userId);

    boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, Long userId);

    // clearAutomatically/flushAutomatically keep the persistence context in sync
    // with this bulk update, so a subsequent read sees the new lastLogin value
    // instead of a stale cached entity.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.lastLogin = :lastLogin WHERE u.userId = :userId")
    void updateLastLogin(Long userId, LocalDateTime lastLogin);
}
