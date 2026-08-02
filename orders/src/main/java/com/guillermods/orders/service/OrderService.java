package com.guillermods.orders.service;

import com.guillermods.orders.client.EventClient;
import com.guillermods.orders.client.PaymentClient;
import com.guillermods.orders.dto.InvoicePayload;
import com.guillermods.orders.dto.OrderResponse;
import com.guillermods.orders.dto.PaymentResult;
import com.guillermods.orders.entity.Invoice;
import com.guillermods.orders.entity.InvoiceStatus;
import com.guillermods.orders.entity.Order;
import com.guillermods.orders.entity.OrderItem;
import com.guillermods.orders.entity.OrderStatus;
import com.guillermods.orders.entity.Product;
import com.guillermods.orders.exception.InsufficientStockException;
import com.guillermods.orders.exception.InvalidOrderStateException;
import com.guillermods.orders.exception.OrderNotFoundException;
import com.guillermods.orders.exception.ProductNotFoundException;
import com.guillermods.orders.repository.OrderRepository;
import com.guillermods.orders.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Saga orchestrator: creates the order, reserves stock, then drives the payment
 * step and compensates (restore stock, void invoice, FAILED) when it fails.
 */
@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final PaymentClient paymentClient;
	private final EventClient eventClient;

	public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
			PaymentClient paymentClient, EventClient eventClient) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.paymentClient = paymentClient;
		this.eventClient = eventClient;
	}

	@Transactional
	public OrderResponse create(String customerName) {
		Order order = new Order(customerName, OrderStatus.CREATED);
		order.setInvoice(new Invoice(order, 0.0, InvoiceStatus.PENDING));
		order = orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_CREATED", "customer=" + customerName);
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse addItem(Long orderId, Long productId, Integer quantity) {
		Order order = findOrder(orderId);
		if (order.getStatus() != OrderStatus.CREATED) {
			throw new InvalidOrderStateException("Only orders in CREATED state can receive items");
		}

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ProductNotFoundException(productId));
		if (product.getStock() < quantity) {
			throw new InsufficientStockException(productId, quantity);
		}

		int reserved = productRepository.reserve(productId, quantity);
		if (reserved != 1) {
			throw new InsufficientStockException(productId, quantity);
		}

		order.getItems().add(new OrderItem(order, productId, product.getName(), quantity, product.getPrice()));
		order.getInvoice().recomputeTotal();
		return OrderResponse.from(orderRepository.save(order));
	}

	@Transactional
	public OrderResponse submit(Long orderId) {
		Order order = findOrder(orderId);
		if (order.getStatus() != OrderStatus.CREATED) {
			throw new InvalidOrderStateException("Only orders in CREATED state can be submitted");
		}
		if (order.getItems().isEmpty()) {
			throw new InvalidOrderStateException("Cannot submit an order without items");
		}

		order.setStatus(OrderStatus.PROCESSING);
		order.getInvoice().recomputeTotal();
		order = orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_PROCESSING",
				"invoice stored, total=" + order.getInvoice().getTotal());

		PaymentResult result;
		try {
			result = paymentClient.charge(invoicePayload(order));
		}
		catch (Exception e) {
			log.warn("Payment call failed for order {}: {}", order.getId(), e.getMessage());
			result = new PaymentResult(PaymentResult.FAILED, null);
		}

		if (result.succeeded()) {
			order.setStatus(OrderStatus.COMPLETED);
			order.getInvoice().setStatus(InvoiceStatus.PAID);
			order = orderRepository.save(order);
			eventClient.post(order.getId(), "orders", "ORDER_COMPLETED",
					"paymentId=" + result.paymentId());
		}
		else {
			compensate(order);
		}
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse get(Long orderId) {
		return OrderResponse.from(findOrder(orderId));
	}

	private Order findOrder(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId));
	}

	private InvoicePayload invoicePayload(Order order) {
		List<InvoicePayload.Line> lines = order.getItems().stream()
				.map(item -> new InvoicePayload.Line(
						item.getProductId(), item.getProductName(), item.getQuantity(), item.getPrice()))
				.toList();
		return new InvoicePayload(order.getId(), order.getInvoice().getId(), order.getInvoice().getTotal(), lines);
	}

	/**
	 * Compensating transaction: reverses the local effects of a failed payment —
	 * restores the reserved stock, voids the invoice and marks the order FAILED.
	 */
	private void compensate(Order order) {
		for (OrderItem item : order.getItems()) {
			productRepository.restore(item.getProductId(), item.getQuantity());
		}
		order.getInvoice().setStatus(InvoiceStatus.VOID);
		order.setStatus(OrderStatus.FAILED);
		orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_FAILED", "payment failed, stock restored");
	}
}
