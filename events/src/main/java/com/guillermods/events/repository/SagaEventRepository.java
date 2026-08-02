package com.guillermods.events.repository;

import com.guillermods.events.entity.SagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaEventRepository extends JpaRepository<SagaEvent, Long> {

	List<SagaEvent> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
