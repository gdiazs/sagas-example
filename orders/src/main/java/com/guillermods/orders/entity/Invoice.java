package com.guillermods.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "order_id")
	private Order order;

	private Double total;

	@Enumerated(EnumType.STRING)
	private InvoiceStatus status;

	@Column(name = "created_at")
	private Instant createdAt;

	protected Invoice() {
	}

	public Invoice(Order order, Double total, InvoiceStatus status) {
		this.order = order;
		this.total = total;
		this.status = status;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Order getOrder() {
		return order;
	}

	public Double getTotal() {
		return total;
	}

	public InvoiceStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setStatus(InvoiceStatus status) {
		this.status = status;
	}

	public void recomputeTotal() {
		this.total = order.getItems().stream()
				.mapToDouble(OrderItem::getSubtotal)
				.sum();
	}
}
