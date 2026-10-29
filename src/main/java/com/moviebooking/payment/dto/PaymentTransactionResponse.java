package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.moviebooking.payment.entity.PaymentTransaction;
import com.moviebooking.payment.enums.PaymentMethod;
import com.moviebooking.payment.enums.TransactionStatus;

public class PaymentTransactionResponse {

    private final Long transactionId;
    private final Long paymentId;
    private final String gatewayTransactionId;
    private final String idempotencyKey;
    private final Integer attemptNumber;
    private final PaymentMethod paymentMethod;
    private final String paymentGateway;
    private final BigDecimal transactionAmount;
    private final TransactionStatus transactionStatus;
    private final String responseCode;
    private final LocalDateTime initiatedAt;
    private final LocalDateTime completedAt;

    private PaymentTransactionResponse(
            PaymentTransaction transaction) {

        this.transactionId = transaction.getTransactionId();
        this.paymentId = transaction.getPayment().getPaymentId();
        this.gatewayTransactionId =
                transaction.getGatewayTransactionId();
        this.idempotencyKey = transaction.getIdempotencyKey();
        this.attemptNumber = transaction.getAttemptNumber();
        this.paymentMethod = transaction.getPaymentMethod();
        this.paymentGateway = transaction.getPaymentGateway();
        this.transactionAmount = transaction.getTransactionAmount();
        this.transactionStatus = transaction.getTransactionStatus();
        this.responseCode = transaction.getResponseCode();
        this.initiatedAt = transaction.getInitiatedAt();
        this.completedAt = transaction.getCompletedAt();
    }

    public static PaymentTransactionResponse fromEntity(
            PaymentTransaction transaction) {

        return new PaymentTransactionResponse(transaction);
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
