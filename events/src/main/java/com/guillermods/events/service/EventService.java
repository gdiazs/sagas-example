package com.guillermods.events.service;

import com.guillermods.events.entity.SagaEvent;
import com.guillermods.events.repository.SagaEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

	private final SagaEventRepository repository;

	public EventService(SagaEventRepository repository) {
		this.repository = repository;
	}

	public SagaEvent append(Long orderId, String service, String type, String payload) {
		return repository.save(new SagaEvent(orderId, service, type, payload));
	}

	public List<SagaEvent> findByOrderId(Long orderId) {
		return repository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
	}

	public List<SagaEvent> findAll() {
		return repository.findAll();
	}
}
