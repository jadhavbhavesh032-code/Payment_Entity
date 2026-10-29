package com.moviebooking.payment.integration;

import java.util.Optional;

/**
 * Future boundary for validating a user without coupling to a User entity.
 */
public interface UserProfileIntegration {

    boolean userExists(Long userId);

    Optional<UserPaymentProfile> findByUserId(Long userId);
}
