package com.paymentapp.repository;

import com.paymentapp.entity.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface FeeRepository extends JpaRepository<Fee, Long> {

    /**
     * Find the fee tier that covers the given payment amount.
     * amount_max IS NULL means no upper bound (the "more" row).
     */
    @Query("SELECT f FROM Fee f WHERE f.amountMin <= :amount " +
           "AND (f.amountMax IS NULL OR f.amountMax >= :amount) " +
           "ORDER BY f.amountMin ASC")
    Optional<Fee> findFeeForAmount(BigDecimal amount);
}
