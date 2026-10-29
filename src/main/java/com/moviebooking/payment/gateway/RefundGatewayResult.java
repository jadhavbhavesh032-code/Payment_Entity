package com.moviebooking.payment.gateway;

import java.time.LocalDateTime;

import com.moviebooking.payment.enums.RefundStatus;

public record RefundGatewayResult(
        String gatewayRefundId,
        RefundStatus refundStatus,
        String responseCode,
        LocalDateTime processedAt) {
}
