package com.guillermods.events.controller;

import com.guillermods.events.entity.SagaEvent;
import com.guillermods.events.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/events")
	@ResponseStatus(HttpStatus.CREATED)
	public SagaEvent append(@RequestBody SagaEventRequest request) {
		return eventService.append(request.orderId(), request.service(), request.type(), request.payload());
	}

	@GetMapping("/events")
	public List<SagaEvent> list(@RequestParam(required = false) Long orderId) {
		if (orderId != null) {
			return eventService.findByOrderId(orderId);
		}
		return eventService.findAll();
	}
}
