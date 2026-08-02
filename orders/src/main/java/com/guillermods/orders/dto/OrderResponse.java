package com.guillermods.orders.dto;

import com.guillermods.orders.entity.Invoice;
import com.guillermods.orders.entity.Order;
import com.guillermods.orders.entity.OrderItem;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
		Long id,
		String customer,
		String status,
		Instant createdAt,
		List<ItemResponse> items,
		InvoiceResponse invoice) {

	public record ItemResponse(
			Long id,
			Long productId,
			String productName,
			Integer quantity,
			Double price,
			Double subtotal) {
	}

	public record InvoiceResponse(Long id, Double total, String status) {
	}

	public static OrderResponse from(Order order) {
		List<ItemResponse> items = order.getItems().stream()
				.map(OrderResponse::toItem)
				.toList();
		Invoice invoice = order.getInvoice();
		InvoiceResponse invoiceResponse = new InvoiceResponse(
				invoice.getId(), invoice.getTotal(), invoice.getStatus().name());
		return new OrderResponse(
				order.getId(), order.getCustomer(), order.getStatus().name(), order.getCreatedAt(),
				items, invoiceResponse);
	}

	private static ItemResponse toItem(OrderItem item) {
		return new ItemResponse(
				item.getId(), item.getProductId(), item.getProductName(),
				item.getQuantity(), item.getPrice(), item.getSubtotal());
	}
}
