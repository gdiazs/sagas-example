package com.guillermods.payments;

import com.guillermods.payments.client.EventClient;
import com.guillermods.payments.gateway.FakePaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:sqlite:target/payments-test.db",
		"app.payment.fail-mode=never"
})
@Transactional
class PaymentControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private RestDocumentationContextProvider restDocumentation;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventClient eventClient;

	@Autowired
	private FakePaymentGateway gateway;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
				.apply(documentationConfiguration(this.restDocumentation))
				.build();
		gateway.setFailMode("never");
	}

	private static String invoice(double total) {
		return """
				{"orderId": 10, "invoiceId": 100, "total": %s, "items": [
				  {"productId": 1, "name": "Laptop", "quantity": 1, "price": %s}
				]}""".formatted(total, total);
	}

	@Test
	void processInvoiceSuccessfully() throws Exception {
		mockMvc.perform(post("/payments/invoices")
						.contentType(APPLICATION_JSON)
						.content(invoice(500.0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andDo(document("payments-process",
						requestFields(
								fieldWithPath("orderId").description("Id of the order being paid"),
								fieldWithPath("invoiceId").description("Id of the invoice being paid"),
								fieldWithPath("total").description("Invoice total to charge"),
								fieldWithPath("items").description("Invoice line items"),
								fieldWithPath("items[].productId").description("Product id"),
								fieldWithPath("items[].name").description("Product name"),
								fieldWithPath("items[].quantity").description("Quantity"),
								fieldWithPath("items[].price").description("Unit price")),
						responseFields(
								fieldWithPath("status").description("SUCCESS or FAILED"),
								fieldWithPath("paymentId").description("Persisted payment id"))));
		verify(eventClient).post(eq(10L), eq("payments"), eq("PAYMENT_SUCCEEDED"), anyString());
	}

	@Test
	void processInvoiceFailsWhenAlwaysFailMode() throws Exception {
		mockMvc.perform(post("/payments/fail-mode")
						.contentType(APPLICATION_JSON)
						.content("{\"mode\": \"always\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/payments/invoices")
						.contentType(APPLICATION_JSON)
						.content(invoice(500.0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andDo(document("payments-process-failed"));

		verify(eventClient).post(eq(10L), eq("payments"), eq("PAYMENT_FAILED"), anyString());
	}

	@Test
	void failsWhenAmountExceedsThreshold() throws Exception {
		mockMvc.perform(post("/payments/invoices")
						.contentType(APPLICATION_JSON)
						.content(invoice(2000.0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));

		verify(eventClient).post(eq(10L), eq("payments"), eq("PAYMENT_FAILED"), anyString());
	}

	@Test
	void setAndGetFailMode() throws Exception {
		mockMvc.perform(post("/payments/fail-mode")
						.contentType(APPLICATION_JSON)
						.content("{\"mode\": \"random\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("random"))
				.andDo(document("payments-fail-mode",
						requestFields(
								fieldWithPath("mode").description("never, always or random"))));

		mockMvc.perform(get("/payments/fail-mode"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("random"));
	}

	@Test
	void listPaymentsByOrder() throws Exception {
		mockMvc.perform(post("/payments/invoices")
						.contentType(APPLICATION_JSON)
						.content(invoice(500.0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCESS"));

		mockMvc.perform(get("/payments").param("orderId", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].orderId").value(10))
				.andExpect(jsonPath("$[0].status").value("SUCCESS"))
				.andDo(document("payments-list",
						queryParameters(
								parameterWithName("orderId").description("Optional filter by order").optional()),
						responseFields(
								fieldWithPath("[].id").description("Payment id"),
								fieldWithPath("[].orderId").description("Order id"),
								fieldWithPath("[].invoiceId").description("Invoice id"),
								fieldWithPath("[].amount").description("Charged amount"),
								fieldWithPath("[].status").description("PENDING, SUCCESS or FAILED"),
								fieldWithPath("[].failMode").description("Fail-mode in effect when processed"),
								fieldWithPath("[].createdAt").description("Timestamp"))));
	}
}
