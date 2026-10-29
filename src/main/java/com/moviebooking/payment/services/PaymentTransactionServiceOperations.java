package com.moviebooking.payment.services;

import java.util.List;

import com.moviebooking.payment.dto.PaymentTransactionResponse;
import com.moviebooking.payment.dto.ProcessPaymentRequest;

public interface PaymentTransactionServiceOperations {

    IdempotentResult<PaymentTransactionResponse> processPayment(
            Long paymentId,
            String idempotencyKey,
            ProcessPaymentRequest request);

    List<PaymentTransactionResponse> getTransactionsByPaymentId(
            Long paymentId);

    PaymentTransactionResponse getTransactionById(
            Long paymentId,
            Long transactionId);
}
