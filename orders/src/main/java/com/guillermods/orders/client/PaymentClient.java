package com.guillermods.orders.client;

import com.guillermods.orders.dto.InvoicePayload;
import com.guillermods.orders.dto.PaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

	private final RestClient restClient;

	public PaymentClient(@Value("${app.payments.url}") String paymentsUrl) {
		this.restClient = RestClient.builder().baseUrl(paymentsUrl).build();
	}

	public PaymentResult charge(InvoicePayload invoice) {
		return restClient.post()
				.uri("/payments/invoices")
				.contentType(MediaType.APPLICATION_JSON)
				.body(invoice)
				.retrieve()
				.body(PaymentResult.class);
	}
}
