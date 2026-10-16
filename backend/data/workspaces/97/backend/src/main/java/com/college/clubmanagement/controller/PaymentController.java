package com.college.clubmanagement.controller;

import com.college.clubmanagement.dto.PaymentRequest;
import com.college.clubmanagement.dto.PaymentResponse;
import com.college.clubmanagement.dto.PaymentVerificationRequest;
import com.college.clubmanagement.entity.Payment;
import com.college.clubmanagement.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPaymentOrder(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        Payment payment = paymentService.createPaymentOrder(
                request.getRegistrationId(),
                authentication.getName());

        PaymentResponse response = new PaymentResponse(
                payment.getRazorpayOrderId(),
                payment.getAmount(),
                "INR",
                paymentService.getRazorpayKeyId() // Use actual key from service
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @Valid @RequestBody PaymentVerificationRequest request) {

        paymentService.verifyPayment(
                request.getRazorpayPaymentId(),
                request.getRazorpayOrderId(),
                request.getRazorpaySignature());

        return ResponseEntity.ok(new java.util.HashMap<String, String>() {
            {
                put("message", "Payment verified successfully");
                put("status", "success");
            }
        });
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Payment> getPaymentByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }
}
