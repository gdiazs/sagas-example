package com.guillermods.events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saga_events")
public class SagaEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id")
	private Long orderId;

	private String service;

	private String type;

	@Column(length = 2000)
	private String payload;

	@Column(name = "created_at")
	private Instant createdAt;

	protected SagaEvent() {
	}

	public SagaEvent(Long orderId, String service, String type, String payload) {
		this.orderId = orderId;
		this.service = service;
		this.type = type;
		this.payload = payload;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public String getService() {
		return service;
	}

	public String getType() {
		return type;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
