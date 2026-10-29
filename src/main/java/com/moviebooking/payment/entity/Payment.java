package com.moviebooking.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.moviebooking.common.exception.BusinessRuleConflictException;
import com.moviebooking.payment.domain.FinancialStateTransitions;
import com.moviebooking.payment.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment", schema = "payments")
public class Payment {

    private static final BigDecimal ZERO_MONEY =
            new BigDecimal("0.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(
        name = "booking_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal bookingAmount;

    @Column(
        name = "discount_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal discountAmount;

    @Column(
        name = "final_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal finalAmount;

    @Column(
        name = "amount_paid",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal amountPaid;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
        // Required by JPA
    }

    public Payment(
            Long bookingId,
            Long userId,
            Long couponId,
            BigDecimal bookingAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount) {

        if (bookingId == null || bookingId <= 0) {
            throw new IllegalArgumentException(
                    "Booking ID must be positive"
            );
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "User ID must be positive"
            );
        }
        if (couponId != null && couponId <= 0) {
            throw new IllegalArgumentException(
                    "Coupon ID must be positive"
            );
        }
        if (bookingAmount == null
                || bookingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Booking amount must be greater than zero"
            );
        }
        if (discountAmount == null
                || discountAmount.compareTo(BigDecimal.ZERO) < 0
                || discountAmount.compareTo(bookingAmount) > 0) {
            throw new IllegalArgumentException(
                    "Discount amount must be between zero and booking amount"
            );
        }
        BigDecimal expectedFinalAmount =
                bookingAmount.subtract(discountAmount);
        if (finalAmount == null
                || finalAmount.compareTo(BigDecimal.ZERO) < 0
                || finalAmount.compareTo(expectedFinalAmount) != 0) {
            throw new IllegalArgumentException(
                    "Final amount must equal booking amount minus discount"
            );
        }

        this.bookingId = bookingId;
        this.userId = userId;
        this.couponId = couponId;
        this.bookingAmount = bookingAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.amountPaid = ZERO_MONEY;
        this.paymentStatus = PaymentStatus.CREATED;

        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
        this.createdAt = currentTime;
        this.updatedAt = currentTime;
    }

    @PrePersist
    protected void beforeInsert() {
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);

        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }

        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }

        if (currency == null) {
            currency = "INR";
        }

        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.CREATED;
        }

        if (createdAt == null) {
            createdAt = currentTime;
        }

        updatedAt = currentTime;
    }

    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
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

    public void setCurrency(String currency) {
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "Currency must contain exactly 3 uppercase letters"
            );
        }
        this.currency = currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void transitionTo(PaymentStatus targetStatus) {
        FinancialStateTransitions.requirePaymentTransition(
                paymentStatus,
                targetStatus
        );
        paymentStatus = targetStatus;
    }

    public void markPending() {
        transitionTo(PaymentStatus.PENDING);
    }

    public void markPaid(LocalDateTime paidAt) {
        requireTimestamp(paidAt, "Paid timestamp is required");

        if (finalAmount == null
                || finalAmount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessRuleConflictException(
                    "A gateway-settled payment must have "
                            + "a positive final amount"
            );
        }

        FinancialStateTransitions.requirePaymentTransition(
                paymentStatus,
                PaymentStatus.PAID
        );

        paymentStatus = PaymentStatus.PAID;
        amountPaid = finalAmount;
        this.paidAt = paidAt;
    }

    public void markPaidWithoutGateway(LocalDateTime paidAt) {
        requireTimestamp(paidAt, "Paid timestamp is required");

        FinancialStateTransitions
                .requireZeroValuePaymentSettlement(
                        paymentStatus,
                        finalAmount
                );

        paymentStatus = PaymentStatus.PAID;
        amountPaid = ZERO_MONEY;
        this.paidAt = paidAt;
    }

    public void markFailed() {
        transitionTo(PaymentStatus.FAILED);
        amountPaid = ZERO_MONEY;
        paidAt = null;
    }

    public void cancel() {
        transitionTo(PaymentStatus.CANCELLED);
        amountPaid = ZERO_MONEY;
        paidAt = null;
    }

    public void markPartiallyRefunded() {
        transitionTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    public void markRefunded() {
        transitionTo(PaymentStatus.REFUNDED);
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

    private static void requireTimestamp(
            LocalDateTime timestamp,
            String message) {

        if (timestamp == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
