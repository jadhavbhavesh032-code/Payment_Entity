package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.util.Locale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class CreatePaymentRequest {

    @NotNull(message = "Booking ID is required")
    @Positive(message = "Booking ID must be positive")
    private Long bookingId;

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @Positive(message = "Coupon ID must be positive")
    private Long couponId;

    @NotNull(message = "Booking amount is required")
    @DecimalMin(
        value = "0.01",
        inclusive = true,
        message = "Booking amount must be greater than zero"
    )
    @Digits(
        integer = 10,
        fraction = 2,
        message = "Booking amount can contain up to 2 decimal places"
    )
    private BigDecimal bookingAmount;

    @DecimalMin(
        value = "0.00",
        inclusive = true,
        message = "Discount amount cannot be negative"
    )
    @Digits(
        integer = 10,
        fraction = 2,
        message = "Discount amount can contain up to 2 decimal places"
    )
    private BigDecimal discountAmount;

    @Pattern(
        regexp = "^[A-Z]{3}$",
        message = "Currency must contain exactly 3 uppercase letters"
    )
    private String currency;

    public CreatePaymentRequest() {
        // Required by Jackson for JSON deserialization.
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public BigDecimal getBookingAmount() {
        return bookingAmount;
    }

    public void setBookingAmount(BigDecimal bookingAmount) {
        this.bookingAmount = bookingAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency == null
                ? null
                : currency.trim().toUpperCase(Locale.ROOT);
    }
}
