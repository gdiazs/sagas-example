package com.guillermods.orders.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String customer;

	@Enumerated(EnumType.STRING)
	private OrderStatus status;

	@Column(name = "created_at")
	private Instant createdAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<OrderItem> items = new ArrayList<>();

	@OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Invoice invoice;

	protected Order() {
	}

	public Order(String customer, OrderStatus status) {
		this.customer = customer;
		this.status = status;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getCustomer() {
		return customer;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public Invoice getInvoice() {
		return invoice;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public void setInvoice(Invoice invoice) {
		this.invoice = invoice;
	}
}
