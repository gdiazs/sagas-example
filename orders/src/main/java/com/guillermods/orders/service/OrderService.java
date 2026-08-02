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
 * ============================================================
 *  SAGA ORCHESTRATOR
 * ============================================================
 *  Orchestrated saga: ONE service (this one) acts as the central
 *  coordinator. It decides the order of steps, calls each
 *  participant explicitly, and — crucially — knows the
 *  compensating action for every step so it can undo work when
 *  a later step fails.
 *
 *  Saga state machine (see OrderStatus):
 *      CREATED ──submit──▶ PROCESSING ──payment ok──▶ COMPLETED
 *         │                    │
 *         └──(pre-submit)──┐   └──payment fail──▶ FAILED (compensated)
 *
 *  Steps in this saga (each one commits in its own service's DB):
 *    1. create order            (this service, local DB)
 *    2. add items / reserve stock (this service, local DB)
 *    3. submit → PROCESSING     (this service, local DB)
 *    4. charge payment          (payments service, remote HTTP)
 *    5a. COMPLETED if payment ok
 *    5b. COMPENSATE (restore stock, void invoice, FAILED) if not
 *
 *  IMPORTANT (study note): a REAL saga persists each step to a
 *  durable saga table and delivers steps via a message broker /
 *  outbox, so a crash mid-saga can be resumed or compensated.
 *  This example runs the whole flow inline in one method to make
 *  the concept easy to read — see the compensation method below.
 * ============================================================
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

	/**
	 * Saga step 1 — CREATE THE ORDER.
	 * "Forward transaction #1": persists the order (CREATED) + an empty
	 * PENDING invoice in THIS service's own database, then records an
	 * audit event. Nothing can fail here that needs compensation because
	 * nothing else has happened yet.
	 *
	 * @Transactional → local ACID: either the order row is committed or
	 * nothing happens. Note the event post is an HTTP call made INSIDE the
	 * transaction (a known simplification of a real saga).
	 */
	@Transactional
	public OrderResponse create(String customerName) {
		log.info("SAGA step 1/5 | CREATE: start order for customer={}", customerName);
		Order order = new Order(customerName, OrderStatus.CREATED);
		order.setInvoice(new Invoice(order, 0.0, InvoiceStatus.PENDING));
		order = orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_CREATED", "customer=" + customerName);
		log.info("SAGA step 1/5 | CREATE: COMMITTED orderId={} status=CREATED", order.getId());
		return OrderResponse.from(order);
	}

	/**
	 * Saga step 2 — ADD ITEMS + RESERVE STOCK (part of "forward transaction #2").
	 * This step must be REVERSIBLE: reserving stock changes the catalog, so the
	 * saga must remember it as the compensating action for a later failure.
	 *
	 * - Only orders still in CREATED can be edited (guard on the state machine).
	 * - reserve() is an atomic SQL UPDATE guarded by `stock >= qty`
	 *   (ProductRepository.reserve) — no lost updates under concurrency.
	 * - Once this commits, the catalog row is permanently changed, so the
	 *   ONLY way back is the compensation path (restore) — never a rollback.
	 */
	@Transactional
	public OrderResponse addItem(Long orderId, Long productId, Integer quantity) {
		log.info("SAGA step 2/5 | RESERVE: start orderId={} productId={} qty={}", orderId, productId, quantity);
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
		log.info("SAGA step 2/5 | RESERVE: stock reserved ({} left before commit), WILL RESTORE IF SAGA FAILS", 
				product.getStock() - quantity);

		order.getItems().add(new OrderItem(order, productId, product.getName(), quantity, product.getPrice()));
		order.getInvoice().recomputeTotal();
		OrderResponse response = OrderResponse.from(orderRepository.save(order));
		log.info("SAGA step 2/5 | RESERVE: COMMITTED orderId={} item={}x{} newTotal={}", 
				orderId, productId, quantity, order.getInvoice().getTotal());
		return response;
	}

	/**
	 * Saga step 3 → 5 — RUN THE SAGA (the heart of the orchestrator).
	 *
	 * 3. Move order CREATED → PROCESSING and commit locally. From now on this
	 *    order is "in flight": the stock is reserved and cannot be edited.
	 * 4. Forward transaction #3 = charge the payment (PAYMENTS SERVICE, own DB).
	 *    This is where the saga crosses a service boundary — the payment will
	 *    commit in the payments DB, NOT here.
	 * 5. Branch:
	 *      SUCCESS → mark COMPLETED / invoice PAID (forward, done).
	 *      FAILURE → COMPENSATE: undo step 2 (restore stock) + step 3's invoice
	 *                (VOID) and mark the order FAILED. This is why we keep each
	 *                step's compensating action around.
	 *
	 * Study notes:
	 * - A payment failure is returned as a RESULT, not thrown — so the local
	 *   transaction does NOT roll back; instead we run forward compensation.
	 * - A network exception (payment service down) is also treated as failure
	 *   and routed to the compensation path — the saga must not hang.
	 * - @Transactional spans the whole method INCLUDING the remote HTTP call.
	 *   In a real saga each step would be its own short transaction persisted
	 *   by a saga execution engine (see the class-level comment).
	 */
	@Transactional
	public OrderResponse submit(Long orderId) {
		log.info("SAGA step 3/5 | SUBMIT: start orderId={}", orderId);

		// --- State machine guard: only a fresh CREATED order may be submitted.
		Order order = findOrder(orderId);
		if (order.getStatus() != OrderStatus.CREATED) {
			throw new InvalidOrderStateException("Only orders in CREATED state can be submitted");
		}
		if (order.getItems().isEmpty()) {
			throw new InvalidOrderStateException("Cannot submit an order without items");
		}

		// --- Step 3: mark the order as PROCESSING and commit the transition.
		order.setStatus(OrderStatus.PROCESSING);
		order.getInvoice().recomputeTotal();
		order = orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_PROCESSING",
				"invoice stored, total=" + order.getInvoice().getTotal());
		log.info("SAGA step 3/5 | SUBMIT: COMMITTED orderId={} status=PROCESSING total={}", 
				order.getId(), order.getInvoice().getTotal());

		// --- Step 4: call the payments participant (a separate service/DB).
		// The payments service commits the charge on ITS side. If it declines,
		// it returns FAILED; if we can't even reach it, catch and treat as FAILED.
		log.info("SAGA step 4/5 | PAY: calling payments service for orderId={} amount={}", 
				order.getId(), order.getInvoice().getTotal());
		PaymentResult result;
		try {
			result = paymentClient.charge(invoicePayload(order));
		}
		catch (Exception e) {
			log.warn("SAGA step 4/5 | PAY: call failed for order {}: {}", order.getId(), e.getMessage());
			result = new PaymentResult(PaymentResult.FAILED, null);
		}
		log.info("SAGA step 4/5 | PAY: result={} paymentId={} for orderId={}", 
				result.status(), result.paymentId(), order.getId());

		// --- Step 5: forward path (all steps done) or compensation path.
		if (result.succeeded()) {
			// Forward: every step succeeded → order is COMPLETED, invoice PAID.
			order.setStatus(OrderStatus.COMPLETED);
			order.getInvoice().setStatus(InvoiceStatus.PAID);
			order = orderRepository.save(order);
			eventClient.post(order.getId(), "orders", "ORDER_COMPLETED",
					"paymentId=" + result.paymentId());
			log.info("SAGA step 5/5 | COMPLETE: COMMITTED orderId={} status=COMPLETED invoice=PAID", order.getId());
		}
		else {
			// Compensation: payment failed → undo what we can locally
			// (restore stock, void invoice) and terminate the saga as FAILED.
			log.info("SAGA step 5/5 | COMPENSATE: starting for orderId={}", order.getId());
			compensate(order);
			log.info("SAGA step 5/5 | COMPENSATE: COMMITTED orderId={} status=FAILED invoice=VOID stock restored",
					order.getId());
		}
		return OrderResponse.from(order);
	}

	/**
	 * Read-only query — not part of the saga, just an API for inspecting state.
	 */
	@Transactional
	public OrderResponse get(Long orderId) {
		return OrderResponse.from(findOrder(orderId));
	}

	/**
	 * Read-only query — lists all persisted orders so a client can resume
	 * an existing order after a page reload / restart.
	 */
	@Transactional(readOnly = true)
	public List<OrderResponse> list() {
		return orderRepository.findAllByOrderByIdAsc().stream()
				.map(OrderResponse::from)
				.toList();
	}

	private Order findOrder(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId));
	}

	/**
	 * Builds the payload sent to the payments participant: everything the
	 * payments service needs to charge an invoice without knowing our schema.
	 */
	private InvoicePayload invoicePayload(Order order) {
		List<InvoicePayload.Line> lines = order.getItems().stream()
				.map(item -> new InvoicePayload.Line(
						item.getProductId(), item.getProductName(), item.getQuantity(), item.getPrice()))
				.toList();
		return new InvoicePayload(order.getId(), order.getInvoice().getId(), order.getInvoice().getTotal(), lines);
	}

	/**
	 * COMPENSATING TRANSACTION(S) — the key idea of the saga pattern.
	 * Because earlier steps already COMMITTED in their own databases, we cannot
	 * roll them back. Instead we run the logical inverse of each step:
	 *
	 *   forward action          compensating action
	 *   ----------------        -------------------
	 *   reserve stock (step 2)  → restore stock  (undo the catalog change)
	 *   invoice PENDING (step 3)→ invoice VOID    (neutralize the invoice)
	 *   order PROCESSING        → order FAILED    (terminate the saga cleanly)
	 *
	 * Note: this only compensates the LOCAL steps. In a real saga the
	 * orchestrator would also tell every other participant to compensate
	 * (e.g. ask payments to reverse a partial charge).
	 */
	private void compensate(Order order) {
		// Undo step 2: give the reserved units back to the catalog.
		for (OrderItem item : order.getItems()) {
			productRepository.restore(item.getProductId(), item.getQuantity());
			log.info("SAGA COMPENSATE | RESTORE stock: +{} units for productId={} (orderId={})",
					item.getQuantity(), item.getProductId(), order.getId());
		}
		// Undo step 3: the invoice is now meaningless → VOID, order → FAILED.
		order.getInvoice().setStatus(InvoiceStatus.VOID);
		order.setStatus(OrderStatus.FAILED);
		orderRepository.save(order);
		eventClient.post(order.getId(), "orders", "ORDER_FAILED", "payment failed, stock restored");
	}
}
