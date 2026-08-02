package com.guillermods.payments.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates an external payment provider. Configured via {@code app.payment.fail-mode}
 * ({@code never}, {@code always} or {@code random}) and {@code app.payment.fail-threshold}.
 * An invoice whose total is greater than the threshold always fails. Any failure is
 * signalled by throwing {@link PaymentException}.
 */
@Component
public class FakePaymentGateway {

	private final double failThreshold;

	private volatile String failMode;

	public FakePaymentGateway(@Value("${app.payment.fail-mode}") String failMode,
			@Value("${app.payment.fail-threshold}") double failThreshold) {
		this.failMode = failMode;
		this.failThreshold = failThreshold;
	}

	public void setFailMode(String failMode) {
		this.failMode = failMode;
	}

	public String getFailMode() {
		return failMode;
	}

	public void charge(double amount) {
		if ("always".equals(failMode)) {
			throw new PaymentException("Payment gateway declined (fail-mode = always)");
		}
		if (amount > failThreshold) {
			throw new PaymentException("Payment gateway declined: amount " + amount
					+ " exceeds threshold " + failThreshold);
		}
		if ("random".equals(failMode) && ThreadLocalRandom.current().nextBoolean()) {
			throw new PaymentException("Payment gateway declined (random failure)");
		}
	}
}
