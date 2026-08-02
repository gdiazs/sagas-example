package com.guillermods.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id")
	private Long orderId;

	@Column(name = "invoice_id")
	private Long invoiceId;

	private Double amount;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status;

	@Column(name = "fail_mode")
	private String failMode;

	@Column(name = "created_at")
	private Instant createdAt;

	protected Payment() {
	}

	public Payment(Long orderId, Long invoiceId, Double amount, PaymentStatus status, String failMode) {
		this.orderId = orderId;
		this.invoiceId = invoiceId;
		this.amount = amount;
		this.status = status;
		this.failMode = failMode;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOrderId() {
		return orderId;
	}

	public Long getInvoiceId() {
		return invoiceId;
	}

	public Double getAmount() {
		return amount;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getFailMode() {
		return failMode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}
}
