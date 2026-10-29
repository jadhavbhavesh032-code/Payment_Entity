package com.moviebooking.payment.integration;

import java.math.BigDecimal;

public record BookingPaymentDetails(
        Long bookingId,
        Long userId,
        BigDecimal bookingAmount,
        String bookingStatus,
        BookingTargetType targetType) {
}
