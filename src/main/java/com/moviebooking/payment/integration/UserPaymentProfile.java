package com.moviebooking.payment.integration;

/**
 * Minimum User Profile projection needed for payment ownership checks.
 */
public record UserPaymentProfile(Long userId) {
}
