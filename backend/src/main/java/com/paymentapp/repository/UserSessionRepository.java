package com.paymentapp.repository;

import com.paymentapp.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user.userId = :userId")
    void deactivateAllSessionsByUserId(Long userId);
}
