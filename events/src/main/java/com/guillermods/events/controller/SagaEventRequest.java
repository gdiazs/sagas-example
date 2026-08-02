package com.guillermods.events.controller;

public record SagaEventRequest(Long orderId, String service, String type, String payload) {
}
