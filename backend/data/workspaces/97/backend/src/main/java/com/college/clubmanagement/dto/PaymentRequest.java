package com.college.clubmanagement.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private Long registrationId;
    private BigDecimal amount;
}
