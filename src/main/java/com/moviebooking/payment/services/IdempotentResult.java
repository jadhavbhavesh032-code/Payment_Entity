package com.moviebooking.payment.services;

public record IdempotentResult<T>(T value, boolean created) {
}
