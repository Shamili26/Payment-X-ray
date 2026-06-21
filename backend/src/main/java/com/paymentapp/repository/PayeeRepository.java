package com.paymentapp.repository;

import com.paymentapp.entity.Payee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayeeRepository extends JpaRepository<Payee, Long> {}
