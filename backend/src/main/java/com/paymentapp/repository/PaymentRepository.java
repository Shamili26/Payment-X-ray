package com.paymentapp.repository;

import com.paymentapp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderByUpdatedDatetimeDesc();

    // User-scoped lookups for per-user data isolation
    List<Payment> findByAccount_User_UserIdOrderByUpdatedDatetimeDesc(Long userId);

    Optional<Payment> findByPaymentIdAndAccount_User_UserId(Long paymentId, Long userId);

    // Scheduler: due payments still awaiting completion
    List<Payment> findByStatusAndPaymentDateLessThanEqual(String status, LocalDate date);
}
