package com.moviebooking.payment.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.common.exception.BadRequestException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.CreateRefundRequest;
import com.moviebooking.payment.dto.RefundResponse;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.Refund;
import com.moviebooking.payment.enums.RefundStatus;
import com.moviebooking.payment.gateway.RefundGateway;
import com.moviebooking.payment.gateway.RefundGatewayResult;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.RefundRepository;

@Service
public class RefundService implements RefundServiceOperations {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final List<RefundStatus> RESERVED_STATUSES = List.of(
            RefundStatus.PROCESSING,
            RefundStatus.COMPLETED
    );

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundWorkflow refundWorkflow;
    private final RefundGateway refundGateway;

    public RefundService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundWorkflow refundWorkflow,
            RefundGateway refundGateway) {

        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundWorkflow = refundWorkflow;
        this.refundGateway = refundGateway;
    }

    @Override
    public IdempotentResult<RefundResponse> createRefund(
            Long paymentId,
            String idempotencyKey,
            CreateRefundRequest request) {

        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (request == null) {
            throw new BadRequestException("Refund request is required");
        }
        BigDecimal refundAmount = normalizeMoney(request.getRefundAmount());
        if (refundAmount.compareTo(ZERO_MONEY) <= 0) {
            throw new BadRequestException(
                    "Refund amount must be greater than zero"
            );
        }
        String refundReason = normalizeReason(request.getRefundReason());

        RefundWorkflow.Reservation reservation = refundWorkflow.reserve(
                paymentId,
                normalizedKey,
                refundAmount,
                refundReason
        );
        if (!reservation.created()) {
            return new IdempotentResult<>(reservation.refund(), false);
        }

        RefundResponse processingRefund = reservation.refund();
        RefundGatewayResult gatewayResult = refundGateway.processRefund(
                reservation.originalGatewayTransactionId(),
                processingRefund.getRefundAmount()
        );
        validateGatewayResult(gatewayResult);

        RefundResponse completedRefund = refundWorkflow.finalizeRefund(
                paymentId,
                processingRefund.getRefundId(),
                gatewayResult
        );
        return new IdempotentResult<>(completedRefund, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));

        BigDecimal completed = getCompletedRefundTotal(paymentId);
        BigDecimal reserved = getReservedRefundTotal(paymentId);
        BigDecimal remaining = payment.getAmountPaid().subtract(reserved);

        return refundRepository
                .findByPaymentPaymentIdOrderByRequestedAtAsc(paymentId)
                .stream()
                .map(refund -> RefundResponse.fromEntity(
                        refund,
                        completed,
                        remaining
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse getRefundById(Long paymentId, Long refundId) {
        Refund refund = refundRepository
                .findByRefundIdAndPaymentPaymentId(refundId, paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Refund not found with ID " + refundId
                                + " for payment ID " + paymentId
                ));

        return toResponse(refund);
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

    private void validateGatewayResult(RefundGatewayResult result) {
        if (result == null
                || result.gatewayRefundId() == null
                || result.gatewayRefundId().isBlank()
                || result.gatewayRefundId().length() > 150
                || (result.responseCode() != null
                    && result.responseCode().length() > 50)
                || result.processedAt() == null
                || (result.refundStatus() != RefundStatus.COMPLETED
                    && result.refundStatus() != RefundStatus.FAILED)) {
            throw new IllegalStateException(
                    "Refund gateway returned an invalid result"
            );
        }
    }

    private BigDecimal getCompletedRefundTotal(Long paymentId) {
        return moneyOrZero(refundRepository
                .sumRefundAmountByPaymentIdAndStatus(
                        paymentId,
                        RefundStatus.COMPLETED
                ));
    }

    private BigDecimal getReservedRefundTotal(Long paymentId) {
        return moneyOrZero(refundRepository
                .sumRefundAmountByPaymentIdAndStatuses(
                        paymentId,
                        RESERVED_STATUSES
                ));
    }

    private BigDecimal moneyOrZero(BigDecimal amount) {
        return amount == null
                ? ZERO_MONEY
                : amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BadRequestException(
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }
        return normalized;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Refund amount is required");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(
                    "Refund amount can contain up to 2 decimal places"
            );
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Refund reason is required");
        }
        String normalized = reason.trim();
        if (normalized.length() > 255) {
            throw new BadRequestException(
                    "Refund reason cannot exceed 255 characters"
            );
        }
        return normalized;
    }
}
