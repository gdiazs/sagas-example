package com.guillermods.payments.controller;

import com.guillermods.payments.dto.FailModeRequest;
import com.guillermods.payments.dto.InvoicePayload;
import com.guillermods.payments.dto.PaymentResult;
import com.guillermods.payments.entity.Payment;
import com.guillermods.payments.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/payments/invoices")
	public PaymentResult process(@RequestBody InvoicePayload invoice) {
		return paymentService.process(invoice);
	}

	@PostMapping("/payments/fail-mode")
	public Map<String, String> setFailMode(@RequestBody FailModeRequest request) {
		paymentService.setFailMode(request.mode());
		return Map.of("mode", request.mode());
	}

	@GetMapping("/payments/fail-mode")
	public Map<String, String> getFailMode() {
		return Map.of("mode", paymentService.getFailMode());
	}

	@GetMapping("/payments")
	public List<Payment> list(@RequestParam(required = false) Long orderId) {
		return paymentService.findByOrderId(orderId);
	}
}
