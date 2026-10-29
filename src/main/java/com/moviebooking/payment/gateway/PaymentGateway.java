package com.moviebooking.payment.gateway;

import java.math.BigDecimal;

import com.moviebooking.payment.enums.PaymentMethod;

public interface PaymentGateway {

    PaymentGatewayResult processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod);
}
