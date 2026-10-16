package com.college.clubmanagement.service;

import com.college.clubmanagement.entity.*;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.exception.ResourceNotFoundException;
import com.college.clubmanagement.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EventRegistrationRepository eventRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${razorpay.key.id:rzp_test_dummy}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:dummy_secret}")
    private String razorpayKeySecret;

    @Transactional
    public Payment createPaymentOrder(Long registrationId, String username) {
        if (registrationId == null)
            throw new BadRequestException("Registration ID cannot be null");
        EventRegistration registration = eventRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!registration.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("You can only pay for your own registrations");
        }

        if (registration.getPaymentCompleted()) {
            throw new BadRequestException("Payment already completed for this registration");
        }

        // Create payment record
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setRegistration(registration);
        payment.setAmount(registration.getEvent().getEntryFee());
        payment.setStatus(PaymentStatus.PENDING);

        // In production, integrate with Razorpay SDK to create order
        // For now, generate a dummy order ID
        String orderId = "order_" + System.currentTimeMillis();
        payment.setRazorpayOrderId(orderId);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment verifyPayment(String razorpayPaymentId, String razorpayOrderId, String razorpaySignature) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // In production, verify signature using Razorpay SDK
        // For now, mark as success
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCompletedAt(LocalDateTime.now());

        // Update registration
        EventRegistration registration = payment.getRegistration();
        registration.setPaymentCompleted(true);
        eventRegistrationRepository.save(registration);

        return paymentRepository.save(payment);
    }

    public Payment getPaymentById(Long id) {
        if (id == null)
            throw new BadRequestException("Payment ID cannot be null");
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }
}
