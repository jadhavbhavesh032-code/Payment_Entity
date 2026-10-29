package com.moviebooking.common.exception;

public class BusinessRuleConflictException extends RuntimeException {

    public BusinessRuleConflictException(String message) {
        super(message);
    }
}
