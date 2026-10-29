package com.moviebooking.payment.gateway;

import java.time.LocalDateTime;

import com.moviebooking.payment.enums.TransactionStatus;

public record PaymentGatewayResult(
        String gatewayTransactionId,
        String gatewayName,
        TransactionStatus transactionStatus,
        String responseCode,
        LocalDateTime completedAt) {
}
