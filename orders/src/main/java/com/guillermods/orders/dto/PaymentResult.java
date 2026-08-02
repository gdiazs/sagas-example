package com.guillermods.orders.dto;

public record PaymentResult(String status, Long paymentId) {

	public static final String SUCCESS = "SUCCESS";
	public static final String FAILED = "FAILED";

	public boolean succeeded() {
		return SUCCESS.equals(status);
	}
}
