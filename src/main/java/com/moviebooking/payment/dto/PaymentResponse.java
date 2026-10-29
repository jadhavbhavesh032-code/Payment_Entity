package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.enums.PaymentStatus;

public class PaymentResponse {

    private final Long paymentId;
    private final Long bookingId;
    private final Long userId;
    private final Long couponId;

    private final BigDecimal bookingAmount;
    private final BigDecimal discountAmount;
    private final BigDecimal finalAmount;
    private final BigDecimal amountPaid;

    private final String currency;
    private final PaymentStatus paymentStatus;

    private final LocalDateTime paidAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private PaymentResponse(Payment payment) {
        this.paymentId = payment.getPaymentId();
        this.bookingId = payment.getBookingId();
        this.userId = payment.getUserId();
        this.couponId = payment.getCouponId();
        this.bookingAmount = payment.getBookingAmount();
        this.discountAmount = payment.getDiscountAmount();
        this.finalAmount = payment.getFinalAmount();
        this.amountPaid = payment.getAmountPaid();
        this.currency = payment.getCurrency();
        this.paymentStatus = payment.getPaymentStatus();
        this.paidAt = payment.getPaidAt();
        this.createdAt = payment.getCreatedAt();
        this.updatedAt = payment.getUpdatedAt();
    }

    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(payment);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public BigDecimal getBookingAmount() {
        return bookingAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
