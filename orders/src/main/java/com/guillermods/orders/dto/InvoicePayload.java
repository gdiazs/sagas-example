package com.guillermods.orders.dto;

import java.util.List;

public record InvoicePayload(Long orderId, Long invoiceId, Double total, List<Line> items) {

	public record Line(Long productId, String name, Integer quantity, Double price) {
	}
}
