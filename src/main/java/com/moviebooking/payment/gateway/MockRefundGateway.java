package com.moviebooking.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.moviebooking.payment.enums.RefundStatus;

@Component
public class MockRefundGateway implements RefundGateway {

    @Override
    public RefundGatewayResult processRefund(
            String originalGatewayTransactionId,
            BigDecimal refundAmount) {

        validateRequest(originalGatewayTransactionId, refundAmount);

        String gatewayRefundId =
                "MOCK-REFUND-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT);

        return new RefundGatewayResult(
                gatewayRefundId,
                RefundStatus.COMPLETED,
                null,
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    private static void validateRequest(
            String originalGatewayTransactionId,
            BigDecimal refundAmount) {

        if (originalGatewayTransactionId == null
                || originalGatewayTransactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Original gateway transaction ID is required"
            );
        }

        if (refundAmount == null
                || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }
    }
}
