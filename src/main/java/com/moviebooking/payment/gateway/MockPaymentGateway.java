package com.moviebooking.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.moviebooking.payment.enums.PaymentMethod;
import com.moviebooking.payment.enums.TransactionStatus;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String GATEWAY_NAME = "MOCK_GATEWAY";

    @Override
    public PaymentGatewayResult processPayment(
            BigDecimal amount,
            PaymentMethod paymentMethod) {

        validateRequest(amount, paymentMethod);

        return new PaymentGatewayResult(
                createGatewayTransactionId(),
                GATEWAY_NAME,
                TransactionStatus.SUCCESS,
                "MOCK_APPROVED",
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    private static void validateRequest(
            BigDecimal amount,
            PaymentMethod paymentMethod) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }
    }

    private static String createGatewayTransactionId() {
        return "MOCK-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT);
    }
}
