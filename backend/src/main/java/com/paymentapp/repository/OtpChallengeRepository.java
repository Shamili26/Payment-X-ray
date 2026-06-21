package com.paymentapp.repository;

import com.paymentapp.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

    Optional<OtpChallenge> findByChallengeIdAndUserId(String challengeId, Long userId);

    // Housekeeping: remove expired / consumed challenges
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}

