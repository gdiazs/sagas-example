package com.guillermods.payments.dto;

public record PaymentResult(String status, Long paymentId) {

	public static final String SUCCESS = "SUCCESS";
	public static final String FAILED = "FAILED";

	public boolean succeeded() {
		return SUCCESS.equals(status);
	}
}
