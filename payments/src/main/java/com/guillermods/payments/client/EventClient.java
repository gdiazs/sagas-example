package com.guillermods.payments.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class EventClient {

	private final RestClient restClient;

	public EventClient(@Value("${app.events.url}") String eventsUrl) {
		this.restClient = RestClient.builder().baseUrl(eventsUrl).build();
	}

	public void post(Long orderId, String service, String type, String payload) {
		restClient.post()
				.uri("/events")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"orderId", orderId,
						"service", service,
						"type", type,
						"payload", payload))
				.retrieve()
				.toBodilessEntity();
	}
}
