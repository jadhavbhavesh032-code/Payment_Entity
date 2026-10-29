package com.moviebooking.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.moviebooking.payment.domain.FinancialStateTransitions;
import com.moviebooking.payment.enums.PaymentMethod;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "payment_transaction",
    schema = "payments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_gateway_transaction",
            columnNames = "gateway_transaction_id"
        ),
        @UniqueConstraint(
            name = "uk_payment_attempt",
            columnNames = {"payment_id", "attempt_number"}
        ),
        @UniqueConstraint(
            name = "uk_transaction_payment_idempotency",
            columnNames = {"payment_id", "idempotency_key"}
        ),
        @UniqueConstraint(
            name = "uk_transaction_idempotency_key",
            columnNames = "idempotency_key"
        ),
        @UniqueConstraint(
            name = "uk_transaction_payment_pair",
            columnNames = {"payment_id", "transaction_id"}
        )
    }
)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_transaction_payment")
    )
    private Payment payment;

    @Column(
        name = "gateway_transaction_id",
        nullable = false,
        length = 150
    )
    private String gatewayTransactionId;

    @Column(
        name = "idempotency_key",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String idempotencyKey;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_gateway", nullable = false, length = 50)
    private String paymentGateway;

    @Column(
        name = "transaction_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal transactionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 30)
    private TransactionStatus transactionStatus;

    @Column(name = "response_code", length = 50)
    private String responseCode;

    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PaymentTransaction() {
        // Required by JPA
    }

    public PaymentTransaction(
            Payment payment,
            String gatewayTransactionId,
            String idempotencyKey,
            Integer attemptNumber,
            PaymentMethod paymentMethod,
            String paymentGateway,
            BigDecimal transactionAmount) {

        this(
                payment,
                gatewayTransactionId,
                idempotencyKey,
                attemptNumber,
                paymentMethod,
                paymentGateway,
                transactionAmount,
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    public PaymentTransaction(
            Payment payment,
            String gatewayTransactionId,
            String idempotencyKey,
            Integer attemptNumber,
            PaymentMethod paymentMethod,
            String paymentGateway,
            BigDecimal transactionAmount,
            LocalDateTime initiatedAt) {

        if (initiatedAt == null) {
            throw new IllegalArgumentException(
                    "Transaction initiation timestamp is required"
            );
        }
        if (payment == null) {
            throw new IllegalArgumentException("Payment is required");
        }
        if (gatewayTransactionId == null
                || gatewayTransactionId.isBlank()
                || gatewayTransactionId.length() > 150) {
            throw new IllegalArgumentException(
                    "Gateway transaction ID must contain 1 to 150 characters"
            );
        }
        if (attemptNumber == null || attemptNumber <= 0) {
            throw new IllegalArgumentException(
                    "Attempt number must be positive"
            );
        }
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (paymentGateway == null
                || paymentGateway.isBlank()
                || paymentGateway.length() > 50) {
            throw new IllegalArgumentException(
                    "Payment gateway must contain 1 to 50 characters"
            );
        }
        if (transactionAmount == null
                || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be greater than zero"
            );
        }

        this.payment = payment;
        this.gatewayTransactionId = gatewayTransactionId;
        this.idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        this.attemptNumber = attemptNumber;
        this.paymentMethod = paymentMethod;
        this.paymentGateway = paymentGateway;
        this.transactionAmount = transactionAmount;
        this.transactionStatus = TransactionStatus.PENDING;
        this.initiatedAt = initiatedAt;
    }

    @PrePersist
    protected void beforeInsert() {
        if (transactionStatus == null) {
            transactionStatus = TransactionStatus.PENDING;
        }

        if (initiatedAt == null) {
            initiatedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void complete(
            TransactionStatus terminalStatus,
            String responseCode,
            LocalDateTime completedAt) {

        complete(
                gatewayTransactionId,
                paymentGateway,
                terminalStatus,
                responseCode,
                completedAt
        );
    }

    public void complete(
            String gatewayTransactionId,
            String paymentGateway,
            TransactionStatus terminalStatus,
            String responseCode,
            LocalDateTime completedAt) {

        FinancialStateTransitions.requireTransactionTransition(
                transactionStatus,
                terminalStatus
        );

        if (completedAt == null) {
            throw new IllegalArgumentException(
                    "Transaction completion timestamp is required"
            );
        }
        if (gatewayTransactionId == null
                || gatewayTransactionId.isBlank()
                || gatewayTransactionId.length() > 150) {
            throw new IllegalArgumentException(
                    "Gateway transaction ID must contain 1 to 150 characters"
            );
        }
        if (paymentGateway == null
                || paymentGateway.isBlank()
                || paymentGateway.length() > 50) {
            throw new IllegalArgumentException(
                    "Payment gateway must contain 1 to 50 characters"
            );
        }
        if (responseCode != null && responseCode.length() > 50) {
            throw new IllegalArgumentException(
                    "Gateway response code cannot exceed 50 characters"
            );
        }

        transactionStatus = terminalStatus;
        this.gatewayTransactionId = gatewayTransactionId;
        this.paymentGateway = paymentGateway;
        this.responseCode = responseCode;
        this.completedAt = completedAt;

        if (initiatedAt == null || initiatedAt.isAfter(completedAt)) {
            initiatedAt = completedAt;
        }
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
