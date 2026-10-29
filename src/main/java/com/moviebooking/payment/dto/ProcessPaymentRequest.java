package com.moviebooking.payment.dto;

import com.moviebooking.payment.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;

public class ProcessPaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    public ProcessPaymentRequest() {
        // Required by JSON deserializers before properties are populated.
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

}
