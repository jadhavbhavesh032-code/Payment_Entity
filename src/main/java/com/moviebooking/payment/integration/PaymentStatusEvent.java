package com.moviebooking.payment.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.enums.RefundStatus;

public record PaymentStatusEvent(
        Long paymentId,
        Long bookingId,
        Long userId,
        PaymentStatus paymentStatus,
        BigDecimal finalAmount,
        BigDecimal amountPaid,
        LocalDateTime paidAt,
        RefundStatus refundStatus,
        LocalDateTime eventTimestamp) {
}
