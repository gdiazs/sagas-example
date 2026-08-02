package com.guillermods.payments.service;

import com.guillermods.payments.client.EventClient;
import com.guillermods.payments.dto.InvoicePayload;
import com.guillermods.payments.dto.PaymentResult;
import com.guillermods.payments.entity.Payment;
import com.guillermods.payments.entity.PaymentStatus;
import com.guillermods.payments.gateway.FakePaymentGateway;
import com.guillermods.payments.gateway.PaymentException;
import com.guillermods.payments.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final FakePaymentGateway gateway;
	private final EventClient eventClient;

	public PaymentService(PaymentRepository paymentRepository, FakePaymentGateway gateway, EventClient eventClient) {
		this.paymentRepository = paymentRepository;
		this.gateway = gateway;
		this.eventClient = eventClient;
	}

	@Transactional
	public PaymentResult process(InvoicePayload invoice) {
		Payment payment = paymentRepository.save(
				new Payment(invoice.orderId(), invoice.invoiceId(), invoice.total(), PaymentStatus.PENDING,
						gateway.getFailMode()));

		try {
			gateway.charge(invoice.total());
			payment.setStatus(PaymentStatus.SUCCESS);
			paymentRepository.save(payment);
			eventClient.post(payment.getOrderId(), "payments", "PAYMENT_SUCCEEDED", "amount=" + invoice.total());
			return new PaymentResult(PaymentResult.SUCCESS, payment.getId());
		}
		catch (PaymentException e) {
			payment.setStatus(PaymentStatus.FAILED);
			paymentRepository.save(payment);
			eventClient.post(payment.getOrderId(), "payments", "PAYMENT_FAILED", e.getMessage());
			return new PaymentResult(PaymentResult.FAILED, payment.getId());
		}
	}

	public void setFailMode(String mode) {
		gateway.setFailMode(mode);
	}

	public String getFailMode() {
		return gateway.getFailMode();
	}

	public List<Payment> findByOrderId(Long orderId) {
		if (orderId != null) {
			return paymentRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);
		}
		return paymentRepository.findAll();
	}
}
