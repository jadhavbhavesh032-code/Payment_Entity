package com.moviebooking.payment.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.common.exception.BusinessRuleConflictException;
import com.moviebooking.common.exception.IdempotencyConflictException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.RefundResponse;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentTransaction;
import com.moviebooking.payment.entity.Refund;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.enums.RefundStatus;
import com.moviebooking.payment.enums.TransactionStatus;
import com.moviebooking.payment.gateway.RefundGatewayResult;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.PaymentTransactionRepository;
import com.moviebooking.payment.repository.RefundRepository;

@Component
class RefundWorkflow {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final List<RefundStatus> RESERVED_STATUSES = List.of(
            RefundStatus.PROCESSING,
            RefundStatus.COMPLETED
    );

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final RefundRepository refundRepository;

    RefundWorkflow(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository,
            RefundRepository refundRepository) {

        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional
    public Reservation reserve(
            Long paymentId,
            String idempotencyKey,
            BigDecimal refundAmount,
            String refundReason) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));

        Refund existing = refundRepository
                .findByPaymentPaymentIdAndIdempotencyKey(
                        paymentId,
                        idempotencyKey
                )
                .orElse(null);
        if (existing != null) {
            validateReplay(existing, refundAmount, refundReason);
            return new Reservation(false, toResponse(existing), null);
        }

        refundRepository
                .findFirstByIdempotencyKeyOrderByRefundIdAsc(idempotencyKey)
                .ifPresent(other -> {
                    if (!other.getPayment().getPaymentId().equals(paymentId)) {
                        throw new IdempotencyConflictException(
                                "Idempotency-Key is already used by another payment"
                        );
                    }
                });

        validatePaymentCanBeRefunded(payment);

        BigDecimal reservedTotal = getReservedRefundTotal(paymentId);
        BigDecimal remaining = payment.getAmountPaid().subtract(reservedTotal);
        if (refundAmount.compareTo(remaining) > 0) {
            throw new BusinessRuleConflictException(
                    "Refund amount cannot be greater than remaining "
                            + "refundable amount " + remaining
            );
        }

        PaymentTransaction successfulTransaction = transactionRepository
                .findFirstByPaymentPaymentIdAndTransactionStatusOrderByAttemptNumberDesc(
                        paymentId,
                        TransactionStatus.SUCCESS
                )
                .orElseThrow(() -> new BusinessRuleConflictException(
                        "No successful transaction was found for payment ID "
                                + paymentId
                ));

        Refund refund = new Refund(
                payment,
                successfulTransaction,
                createPendingGatewayId(),
                refundAmount,
                refundReason,
                idempotencyKey
        );
        refund.markProcessing();
        Refund saved = refundRepository.saveAndFlush(refund);

        return new Reservation(
                true,
                toResponse(saved),
                successfulTransaction.getGatewayTransactionId()
        );
    }

    @Transactional
    public RefundResponse finalizeRefund(
            Long paymentId,
            Long refundId,
            RefundGatewayResult gatewayResult) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));
        Refund refund = refundRepository
                .findByRefundIdAndPaymentPaymentId(refundId, paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Refund not found with ID " + refundId
                                + " for payment ID " + paymentId
                ));

        if (refund.getRefundStatus() != RefundStatus.PROCESSING) {
            return toResponse(refund);
        }

        BigDecimal completedBefore = getCompletedRefundTotal(paymentId);
        refund.complete(
                gatewayResult.gatewayRefundId(),
                gatewayResult.refundStatus(),
                gatewayResult.processedAt()
        );
        Refund saved = refundRepository.saveAndFlush(refund);

        if (gatewayResult.refundStatus() == RefundStatus.COMPLETED) {
            BigDecimal newCompletedTotal =
                    completedBefore.add(refund.getRefundAmount());
            BigDecimal remaining = payment.getAmountPaid()
                    .subtract(newCompletedTotal);
            payment.transitionTo(
                    remaining.compareTo(ZERO_MONEY) == 0
                            ? PaymentStatus.REFUNDED
                            : PaymentStatus.PARTIALLY_REFUNDED
            );
            paymentRepository.saveAndFlush(payment);
        }

        return toResponse(saved);
    }

    private RefundResponse toResponse(Refund refund) {
        Long paymentId = refund.getPayment().getPaymentId();
        BigDecimal completed = getCompletedRefundTotal(paymentId);
        BigDecimal reserved = getReservedRefundTotal(paymentId);
        BigDecimal remaining = refund.getPayment().getAmountPaid()
                .subtract(reserved);

        return RefundResponse.fromEntity(
                refund,
                completed,
                remaining
        );
    }

    private void validateReplay(
            Refund existing,
            BigDecimal refundAmount,
            String refundReason) {

        boolean sameAmount = existing.getRefundAmount() != null
                && existing.getRefundAmount().compareTo(refundAmount) == 0;
        boolean sameReason = existing.getRefundReason().equals(refundReason);
        if (!sameAmount || !sameReason) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was reused with conflicting refund data"
            );
        }
    }

    private void validatePaymentCanBeRefunded(Payment payment) {
        PaymentStatus status = payment.getPaymentStatus();
        if (status != PaymentStatus.PAID
                && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleConflictException(
                    "Payment cannot be refunded when its status is " + status
            );
        }
        if (payment.getAmountPaid() == null
                || payment.getAmountPaid().compareTo(ZERO_MONEY) <= 0) {
            throw new BusinessRuleConflictException(
                    "Payment does not contain a refundable amount"
            );
        }
    }

    private BigDecimal getCompletedRefundTotal(Long paymentId) {
        BigDecimal total = refundRepository
                .sumRefundAmountByPaymentIdAndStatus(
                        paymentId,
                        RefundStatus.COMPLETED
                );
        return moneyOrZero(total);
    }

    private BigDecimal getReservedRefundTotal(Long paymentId) {
        BigDecimal total = refundRepository
                .sumRefundAmountByPaymentIdAndStatuses(
                        paymentId,
                        RESERVED_STATUSES
                );
        return moneyOrZero(total);
    }

    private BigDecimal moneyOrZero(BigDecimal amount) {
        return amount == null
                ? ZERO_MONEY
                : amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String createPendingGatewayId() {
        return "PENDING-REFUND-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    record Reservation(
            boolean created,
            RefundResponse refund,
            String originalGatewayTransactionId) {
    }
}
