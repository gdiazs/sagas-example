package com.guillermods.orders.exception;

public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(Long productId, Integer quantity) {
		super("Insufficient stock for product " + productId + " (requested " + quantity + ")");
	}
}
