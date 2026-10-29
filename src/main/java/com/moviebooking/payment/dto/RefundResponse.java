package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.moviebooking.payment.entity.Refund;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.enums.RefundStatus;

public class RefundResponse {

    private final Long refundId;
    private final Long paymentId;
    private final Long transactionId;
    private final String gatewayRefundId;
    private final String idempotencyKey;

    private final BigDecimal refundAmount;
    private final String refundReason;
    private final RefundStatus refundStatus;

    private final LocalDateTime requestedAt;
    private final LocalDateTime processedAt;

    private final PaymentStatus paymentStatus;
    private final BigDecimal totalRefundedAmount;
    private final BigDecimal remainingRefundableAmount;

    private RefundResponse(
            Refund refund,
            BigDecimal totalRefundedAmount,
            BigDecimal remainingRefundableAmount) {

        this.refundId = refund.getRefundId();
        this.paymentId = refund.getPayment().getPaymentId();
        this.transactionId =
                refund.getTransaction().getTransactionId();
        this.gatewayRefundId = refund.getGatewayRefundId();
        this.idempotencyKey = refund.getIdempotencyKey();
        this.refundAmount = refund.getRefundAmount();
        this.refundReason = refund.getRefundReason();
        this.refundStatus = refund.getRefundStatus();
        this.requestedAt = refund.getRequestedAt();
        this.processedAt = refund.getProcessedAt();
        this.paymentStatus = refund.getPayment().getPaymentStatus();
        this.totalRefundedAmount = totalRefundedAmount;
        this.remainingRefundableAmount =
                remainingRefundableAmount;
    }

    public static RefundResponse fromEntity(
            Refund refund,
            BigDecimal totalRefundedAmount,
            BigDecimal remainingRefundableAmount) {

        return new RefundResponse(
                refund,
                totalRefundedAmount,
                remainingRefundableAmount
        );
    }

    public Long getRefundId() {
        return refundId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getTotalRefundedAmount() {
        return totalRefundedAmount;
    }

    public BigDecimal getRemainingRefundableAmount() {
        return remainingRefundableAmount;
    }
}
