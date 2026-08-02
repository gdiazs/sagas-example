package com.guillermods.orders.repository;

import com.guillermods.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findAllByOrderByIdAsc();
}
