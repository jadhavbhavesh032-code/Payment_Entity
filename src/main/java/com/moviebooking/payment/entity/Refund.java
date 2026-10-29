package com.moviebooking.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.moviebooking.payment.domain.FinancialStateTransitions;
import com.moviebooking.payment.enums.RefundStatus;
import com.moviebooking.payment.enums.TransactionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "refund",
    schema = "payments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_gateway_refund",
            columnNames = "gateway_refund_id"
        ),
        @UniqueConstraint(
            name = "uk_refund_payment_idempotency",
            columnNames = {"payment_id", "idempotency_key"}
        ),
        @UniqueConstraint(
            name = "uk_refund_idempotency_key",
            columnNames = "idempotency_key"
        )
    }
)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_refund_payment")
    )
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
        value = {
            @JoinColumn(
                name = "payment_id",
                referencedColumnName = "payment_id",
                nullable = false
            ),
            @JoinColumn(
                name = "transaction_id",
                referencedColumnName = "transaction_id",
                nullable = false
            )
        },
        foreignKey = @ForeignKey(
            name = "fk_refund_transaction_payment"
        )
    )
    private PaymentTransaction transaction;

    @Column(
        name = "gateway_refund_id",
        nullable = false,
        length = 150
    )
    private String gatewayRefundId;

    @Column(
        name = "idempotency_key",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String idempotencyKey;

    @Column(
        name = "refund_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", nullable = false, length = 255)
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private RefundStatus refundStatus;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected Refund() {
        // Required by JPA
    }

    public Refund(
            Payment payment,
            PaymentTransaction transaction,
            String gatewayRefundId,
            BigDecimal refundAmount,
            String refundReason,
            String idempotencyKey) {

        requireTransactionBelongsToPayment(payment, transaction);
        if (transaction.getTransactionStatus() != TransactionStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "Only a successful transaction can be refunded"
            );
        }
        if (refundAmount == null
                || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }
        if (refundReason == null
                || refundReason.isBlank()
                || refundReason.length() > 255) {
            throw new IllegalArgumentException(
                    "Refund reason must contain 1 to 255 characters"
            );
        }
        this.payment = payment;
        this.transaction = transaction;
        this.gatewayRefundId = gatewayRefundId;
        this.idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        this.refundAmount = refundAmount;
        this.refundReason = refundReason;
        this.refundStatus = RefundStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    protected void beforeInsert() {
        if (refundStatus == null) {
            refundStatus = RefundStatus.REQUESTED;
        }

        if (requestedAt == null) {
            requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getRefundId() {
        return refundId;
    }

    public Payment getPayment() {
        return payment;
    }

    public PaymentTransaction getTransaction() {
        return transaction;
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

    public void markProcessing() {
        FinancialStateTransitions.requireRefundTransition(
                refundStatus,
                RefundStatus.PROCESSING
        );
        refundStatus = RefundStatus.PROCESSING;
    }

    public void complete(
            String gatewayRefundId,
            RefundStatus terminalStatus,
            LocalDateTime processedAt) {

        FinancialStateTransitions.requireRefundTransition(
                refundStatus,
                terminalStatus
        );

        if (processedAt == null) {
            throw new IllegalArgumentException(
                    "Refund processing timestamp is required"
            );
        }

        if (gatewayRefundId == null || gatewayRefundId.isBlank()) {
            throw new IllegalArgumentException(
                    "Gateway refund ID is required"
            );
        }

        if (gatewayRefundId.length() > 150) {
            throw new IllegalArgumentException(
                    "Gateway refund ID cannot exceed 150 characters"
            );
        }

        refundStatus = terminalStatus;
        this.gatewayRefundId = gatewayRefundId;
        this.processedAt = processedAt;
    }

    private static void requireTransactionBelongsToPayment(
            Payment payment,
            PaymentTransaction transaction) {

        if (payment == null || transaction == null) {
            throw new IllegalArgumentException(
                    "Refund payment and transaction are required"
            );
        }

        Payment transactionPayment = transaction.getPayment();

        if (transactionPayment == null
                || !samePayment(payment, transactionPayment)) {

            throw new IllegalArgumentException(
                    "Refund transaction must belong to the refund payment"
            );
        }
    }

    private static boolean samePayment(
            Payment payment,
            Payment transactionPayment) {

        if (payment == transactionPayment) {
            return true;
        }

        return payment.getPaymentId() != null
                && payment.getPaymentId().equals(
                        transactionPayment.getPaymentId()
                );
    }

    private static String normalizeIdempotencyKey(
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        String normalizedKey = idempotencyKey.trim();

        if (normalizedKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key cannot exceed 100 characters"
            );
        }

        return normalizedKey;
    }
}
