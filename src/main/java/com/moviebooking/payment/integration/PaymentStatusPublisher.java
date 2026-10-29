package com.moviebooking.payment.integration;

/**
 * Future outbound boundary; this milestone deliberately provides no publisher.
 */
public interface PaymentStatusPublisher {

    void publish(PaymentStatusEvent event);
}
