package com.moviebooking.payment.integration;

import java.math.BigDecimal;

public record CouponDiscountQuote(
        Long couponId,
        String couponStatus,
        boolean applicable,
        BigDecimal discountAmount) {
}
