package com.moviebooking.payment.integration;

import java.util.Optional;

/**
 * Future boundary for authoritative booking facts required by Payment.
 */
public interface BookingPaymentIntegration {

    Optional<BookingPaymentDetails> findByBookingId(Long bookingId);

    void notifyPaymentStatus(PaymentStatusEvent event);
}
