package com.guillermods.orders;

import com.guillermods.orders.client.EventClient;
import com.guillermods.orders.client.PaymentClient;
import com.guillermods.orders.dto.InvoicePayload;
import com.guillermods.orders.dto.PaymentResult;
import com.guillermods.orders.entity.Product;
import com.guillermods.orders.repository.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/orders-test.db")
@Transactional
class OrderControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private RestDocumentationContextProvider restDocumentation;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@MockitoBean
	private PaymentClient paymentClient;

	@MockitoBean
	private EventClient eventClient;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
				.apply(documentationConfiguration(this.restDocumentation))
				.build();
	}

	private Long createOrder(String customer) throws Exception {
		String body = mockMvc.perform(post("/orders")
						.contentType(APPLICATION_JSON)
						.content("{\"customerName\":\"" + customer + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void addItem(Long orderId, Long productId, Integer quantity) throws Exception {
		mockMvc.perform(post("/orders/{id}/items", orderId)
						.contentType(APPLICATION_JSON)
						.content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
				.andExpect(status().isOk());
	}

	private Integer stockOf(Long productId) {
		entityManager.clear();
		return productRepository.findById(productId).map(Product::getStock).orElseThrow();
	}

	@Test
	void listProducts() throws Exception {
		mockMvc.perform(get("/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Laptop"))
				.andExpect(jsonPath("$[0].stock").value(10))
				.andDo(document("products-list",
						responseFields(
								fieldWithPath("[].id").description("Product id"),
								fieldWithPath("[].name").description("Product name"),
								fieldWithPath("[].price").description("Unit price"),
								fieldWithPath("[].stock").description("Available stock"))));
	}

	@Test
	void createOrder() throws Exception {
		mockMvc.perform(post("/orders")
						.contentType(APPLICATION_JSON)
						.content("{\"customerName\":\"Alice\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customer").value("Alice"))
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.invoice.status").value("PENDING"))
				.andExpect(jsonPath("$.invoice.total").value(0.0))
				.andDo(document("orders-create",
						requestFields(
								fieldWithPath("customerName").description("Customer name")),
						responseFields(orderResponseFields())));
	}

	@Test
	void addItemReservesStockAndUpdatesInvoice() throws Exception {
		Long orderId = createOrder("Bob");

		mockMvc.perform(post("/orders/{id}/items", orderId)
						.contentType(APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":2}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].productId").value(1))
				.andExpect(jsonPath("$.items[0].quantity").value(2))
				.andExpect(jsonPath("$.items[0].subtotal").value(2400.0))
				.andExpect(jsonPath("$.invoice.total").value(2400.0))
				.andDo(document("orders-add-item",
						pathParameters(
								parameterWithName("id").description("Order id")),
						requestFields(
								fieldWithPath("productId").description("Product to add"),
								fieldWithPath("quantity").description("Quantity to add")),
						responseFields(orderResponseFields())));

		assertEquals(8, stockOf(1L));
	}

	@Test
	void addItemFailsWhenStockInsufficient() throws Exception {
		Long orderId = createOrder("Carol");

		mockMvc.perform(post("/orders/{id}/items", orderId)
						.contentType(APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":99999}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").isNotEmpty());
	}

	@Test
	void submitCompletesOrderWhenPaymentSucceeds() throws Exception {
		when(paymentClient.charge(any(InvoicePayload.class)))
				.thenReturn(new PaymentResult(PaymentResult.SUCCESS, 7L));

		Long orderId = createOrder("Dave");
		addItem(orderId, 1L, 2);

		mockMvc.perform(post("/orders/{id}/submit", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.invoice.status").value("PAID"))
				.andDo(document("orders-submit-success",
						pathParameters(
								parameterWithName("id").description("Order id")),
						responseFields(orderResponseFields())));

		verify(eventClient).post(eq(orderId), eq("orders"), eq("ORDER_PROCESSING"), anyString());
		verify(eventClient).post(eq(orderId), eq("orders"), eq("ORDER_COMPLETED"), anyString());
		assertEquals(8, stockOf(1L));
	}

	@Test
	void submitFailsAndCompensatesWhenPaymentIsDeclined() throws Exception {
		when(paymentClient.charge(any(InvoicePayload.class)))
				.thenReturn(new PaymentResult(PaymentResult.FAILED, null));

		Long orderId = createOrder("Eve");
		addItem(orderId, 1L, 2);

		mockMvc.perform(post("/orders/{id}/submit", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.invoice.status").value("VOID"))
				.andDo(document("orders-submit-failed",
						pathParameters(
								parameterWithName("id").description("Order id")),
						responseFields(orderResponseFields())));

		verify(eventClient).post(eq(orderId), eq("orders"), eq("ORDER_FAILED"), anyString());
		assertEquals(10, stockOf(1L));
	}

	@Test
	void submitFailsAndCompensatesWhenPaymentServiceIsDown() throws Exception {
		when(paymentClient.charge(any(InvoicePayload.class)))
				.thenThrow(new RuntimeException("connection refused"));

		Long orderId = createOrder("Frank");
		addItem(orderId, 1L, 2);

		mockMvc.perform(post("/orders/{id}/submit", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));

		assertEquals(10, stockOf(1L));
	}

	@Test
	void listOrdersReturnsPersistedOrders() throws Exception {
		Long first = createOrder("Alice");
		Long second = createOrder("Bob");

		mockMvc.perform(get("/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[1].id").value(second));
	}

	@Test
	void getOrder() throws Exception {
		Long orderId = createOrder("Grace");
		addItem(orderId, 1L, 1);

		mockMvc.perform(get("/orders/{id}", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andDo(document("orders-get",
						pathParameters(
								parameterWithName("id").description("Order id")),
						responseFields(orderResponseFields())));
	}

	private static FieldDescriptor[] orderResponseFields() {
		return new FieldDescriptor[] {
				fieldWithPath("id").description("Order id"),
				fieldWithPath("customer").description("Customer name"),
				fieldWithPath("status").description("CREATED, PROCESSING, COMPLETED or FAILED"),
				fieldWithPath("createdAt").description("Creation timestamp"),
				fieldWithPath("items").description("Order line items"),
				fieldWithPath("items[].id").description("Item id").type(JsonFieldType.NUMBER).optional(),
				fieldWithPath("items[].productId").description("Product id").type(JsonFieldType.NUMBER).optional(),
				fieldWithPath("items[].productName").description("Product name").type(JsonFieldType.STRING).optional(),
				fieldWithPath("items[].quantity").description("Quantity").type(JsonFieldType.NUMBER).optional(),
				fieldWithPath("items[].price").description("Unit price at order time").type(JsonFieldType.NUMBER).optional(),
				fieldWithPath("items[].subtotal").description("Line subtotal").type(JsonFieldType.NUMBER).optional(),
				fieldWithPath("invoice").description("Invoice"),
				fieldWithPath("invoice.id").description("Invoice id"),
				fieldWithPath("invoice.total").description("Invoice total"),
				fieldWithPath("invoice.status").description("PENDING, PAID or VOID")
		};
	}
}
