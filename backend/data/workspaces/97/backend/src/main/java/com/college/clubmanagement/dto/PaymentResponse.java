package com.college.clubmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String razorpayKey;

    private java.math.BigDecimal getAmount() {
        return amount;
    }
}
