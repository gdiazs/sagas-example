package com.guillermods.payments.gateway;

public class PaymentException extends RuntimeException {

	public PaymentException(String message) {
		super(message);
	}
}
