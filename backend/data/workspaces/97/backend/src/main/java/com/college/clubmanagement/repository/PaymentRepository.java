package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Payment;
import com.college.clubmanagement.entity.PaymentStatus;
import com.college.clubmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    List<Payment> findByUser(User user);
    
    List<Payment> findByStatus(PaymentStatus status);
    
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
}
