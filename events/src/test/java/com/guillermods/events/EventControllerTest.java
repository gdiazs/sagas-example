package com.guillermods.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/events-test.db")
@Transactional
class EventControllerTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private RestDocumentationContextProvider restDocumentation;

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
				.apply(documentationConfiguration(this.restDocumentation))
				.build();
	}

	@Test
	void appendAndListByOrderId() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(APPLICATION_JSON)
						.content("""
								{"orderId": 1, "service": "orders", "type": "ORDER_CREATED", "payload": "order created"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderId").value(1))
				.andExpect(jsonPath("$.type").value("ORDER_CREATED"))
				.andDo(document("events-append",
						requestFields(
								fieldWithPath("orderId").description("Order the event belongs to"),
								fieldWithPath("service").description("Service that emitted the event (orders, payments)"),
								fieldWithPath("type").description("Event type (e.g. ORDER_CREATED, PAYMENT_FAILED)"),
								fieldWithPath("payload").description("Optional human-readable payload")),
						responseFields(
								fieldWithPath("id").description("Event id"),
								fieldWithPath("orderId").description("Order the event belongs to"),
								fieldWithPath("service").description("Emitting service"),
								fieldWithPath("type").description("Event type"),
								fieldWithPath("payload").description("Optional payload"),
								fieldWithPath("createdAt").description("Timestamp when the event was stored"))));

		mockMvc.perform(get("/events").param("orderId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].type").value("ORDER_CREATED"))
				.andDo(document("events-list",
						queryParameters(
								parameterWithName("orderId").description("Optional filter by order").optional()),
						responseFields(
								fieldWithPath("[].id").description("Event id"),
								fieldWithPath("[].orderId").description("Order the event belongs to"),
								fieldWithPath("[].service").description("Emitting service"),
								fieldWithPath("[].type").description("Event type"),
								fieldWithPath("[].payload").description("Optional payload"),
								fieldWithPath("[].createdAt").description("Timestamp when the event was stored"))));
	}
}
