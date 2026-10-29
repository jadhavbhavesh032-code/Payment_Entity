package com.moviebooking.payment.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.common.exception.BadRequestException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.PaymentTransactionResponse;
import com.moviebooking.payment.dto.ProcessPaymentRequest;
import com.moviebooking.payment.enums.TransactionStatus;
import com.moviebooking.payment.gateway.PaymentGateway;
import com.moviebooking.payment.gateway.PaymentGatewayResult;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.PaymentTransactionRepository;

@Service
public class PaymentTransactionService
        implements PaymentTransactionServiceOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentTransactionService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentTransactionWorkflow transactionWorkflow;
    private final PaymentGateway paymentGateway;

    public PaymentTransactionService(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentTransactionWorkflow transactionWorkflow,
            PaymentGateway paymentGateway) {

        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.transactionWorkflow = transactionWorkflow;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public IdempotentResult<PaymentTransactionResponse> processPayment(
            Long paymentId,
            String idempotencyKey,
            ProcessPaymentRequest request) {

        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (request == null || request.getPaymentMethod() == null) {
            throw new BadRequestException("Payment method is required");
        }

        PaymentTransactionWorkflow.Reservation reservation =
                transactionWorkflow.reserve(
                        paymentId,
                        normalizedKey,
                        request
                );

        if (!reservation.created()) {
            LOGGER.info("Payment replay paymentId={} transactionId={}",
                    paymentId, reservation.transaction().getTransactionId());
            return new IdempotentResult<>(
                    reservation.transaction(),
                    false
            );
        }

        PaymentTransactionResponse pending = reservation.transaction();
        LOGGER.info("Payment attempt reserved paymentId={} transactionId={}",
                paymentId, pending.getTransactionId());
        PaymentGatewayResult gatewayResult = paymentGateway.processPayment(
                pending.getTransactionAmount(),
                pending.getPaymentMethod()
        );
        validateGatewayResult(gatewayResult);

        PaymentTransactionResponse completed =
                transactionWorkflow.finalizeAttempt(
                        paymentId,
                        pending.getTransactionId(),
                        gatewayResult
                );

        if (completed.getTransactionStatus() == TransactionStatus.FAILED) {
            LOGGER.warn("Payment attempt completed paymentId={} transactionId={} status={}",
                    paymentId, completed.getTransactionId(), completed.getTransactionStatus());
        } else {
            LOGGER.info("Payment attempt completed paymentId={} transactionId={} status={}",
                    paymentId, completed.getTransactionId(), completed.getTransactionStatus());
        }
        return new IdempotentResult<>(completed, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getTransactionsByPaymentId(
            Long paymentId) {

        requirePayment(paymentId);
        return transactionRepository
                .findByPaymentPaymentIdOrderByAttemptNumberAsc(paymentId)
                .stream()
                .map(PaymentTransactionResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getTransactionById(
            Long paymentId,
            Long transactionId) {

        return transactionRepository
                .findByTransactionIdAndPaymentPaymentId(
                        transactionId,
                        paymentId
                )
                .map(PaymentTransactionResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with ID " + transactionId
                                + " for payment ID " + paymentId
                ));
    }

    private void validateGatewayResult(PaymentGatewayResult result) {
        if (result == null
                || result.gatewayTransactionId() == null
                || result.gatewayTransactionId().isBlank()
                || result.gatewayTransactionId().length() > 150
                || result.gatewayName() == null
                || result.gatewayName().isBlank()
                || result.gatewayName().length() > 50
                || (result.responseCode() != null
                    && result.responseCode().length() > 50)
                || result.completedAt() == null
                || (result.transactionStatus() != TransactionStatus.SUCCESS
                    && result.transactionStatus() != TransactionStatus.FAILED)) {
            throw new IllegalStateException(
                    "Payment gateway returned an invalid result"
            );
        }
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

    private void requirePayment(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new ResourceNotFoundException(
                    "Payment not found with ID " + paymentId
            );
        }
    }
}
