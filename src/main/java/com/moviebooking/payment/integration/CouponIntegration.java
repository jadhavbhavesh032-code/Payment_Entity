package com.moviebooking.payment.integration;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Future boundary for authoritative coupon quoting and consumption.
 */
public interface CouponIntegration {

    Optional<CouponDiscountQuote> quoteDiscount(
            Long couponId,
            Long bookingId,
            Long userId,
            BigDecimal bookingAmount);

    void confirmConsumption(Long couponId, Long paymentId);
}
