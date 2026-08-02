package com.guillermods.orders.controller;

import com.guillermods.orders.dto.AddItemRequest;
import com.guillermods.orders.dto.CreateOrderRequest;
import com.guillermods.orders.dto.OrderResponse;
import com.guillermods.orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse create(@RequestBody CreateOrderRequest request) {
		return orderService.create(request.customerName());
	}

	@PostMapping("/orders/{id}/items")
	public OrderResponse addItem(@PathVariable Long id, @RequestBody AddItemRequest request) {
		return orderService.addItem(id, request.productId(), request.quantity());
	}

	@PostMapping("/orders/{id}/submit")
	public OrderResponse submit(@PathVariable Long id) {
		return orderService.submit(id);
	}

	@GetMapping("/orders")
	public List<OrderResponse> list() {
		return orderService.list();
	}

	@GetMapping("/orders/{id}")
	public OrderResponse get(@PathVariable Long id) {
		return orderService.get(id);
	}
}
