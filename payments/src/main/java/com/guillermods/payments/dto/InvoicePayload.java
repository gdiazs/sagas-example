package com.guillermods.payments.dto;

import java.util.List;

public record InvoicePayload(Long orderId, Long invoiceId, Double total, List<InvoiceLine> items) {

	public record InvoiceLine(Long productId, String name, Integer quantity, Double price) {
	}
}
