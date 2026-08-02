package com.guillermods.payments.repository;

import com.guillermods.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
