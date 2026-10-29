package com.moviebooking.payment.gateway;

import java.math.BigDecimal;

public interface RefundGateway {

    RefundGatewayResult processRefund(
            String originalGatewayTransactionId,
            BigDecimal refundAmount);
}
