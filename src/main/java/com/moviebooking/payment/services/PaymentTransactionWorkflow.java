package com.moviebooking.payment.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.common.exception.BusinessRuleConflictException;
import com.moviebooking.common.exception.IdempotencyConflictException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.PaymentTransactionResponse;
import com.moviebooking.payment.dto.ProcessPaymentRequest;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentTransaction;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.enums.TransactionStatus;
import com.moviebooking.payment.gateway.PaymentGatewayResult;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.PaymentTransactionRepository;

@Component
class PaymentTransactionWorkflow {

    private static final String PENDING_GATEWAY = "PENDING";

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;

    PaymentTransactionWorkflow(
            PaymentRepository paymentRepository,
            PaymentTransactionRepository transactionRepository) {

        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Reservation reserve(
            Long paymentId,
            String idempotencyKey,
            ProcessPaymentRequest request) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));

        PaymentTransaction existing = transactionRepository
                .findByPaymentPaymentIdAndIdempotencyKey(
                        paymentId,
                        idempotencyKey
                )
                .orElse(null);
        if (existing != null) {
            validateReplay(existing, payment, request);
            return new Reservation(
                    false,
                    PaymentTransactionResponse.fromEntity(existing)
            );
        }

        transactionRepository
                .findFirstByIdempotencyKeyOrderByTransactionIdAsc(
                        idempotencyKey
                )
                .ifPresent(other -> {
                    if (!other.getPayment().getPaymentId().equals(paymentId)) {
                        throw new IdempotencyConflictException(
                                "Idempotency-Key is already used by another payment"
                        );
                    }
                });

        validateNewAttempt(payment);

        Integer maximumAttempt = transactionRepository
                .findMaximumAttemptNumberByPaymentId(paymentId);
        int nextAttemptNumber =
                (maximumAttempt == null ? 0 : maximumAttempt) + 1;

        payment.markPending();
        PaymentTransaction transaction = new PaymentTransaction(
                payment,
                createPendingGatewayId(),
                idempotencyKey,
                nextAttemptNumber,
                request.getPaymentMethod(),
                PENDING_GATEWAY,
                payment.getFinalAmount(),
                LocalDateTime.now(ZoneOffset.UTC)
        );

        paymentRepository.save(payment);
        PaymentTransaction saved = transactionRepository
                .saveAndFlush(transaction);

        return new Reservation(
                true,
                PaymentTransactionResponse.fromEntity(saved)
        );
    }

    @Transactional
    public PaymentTransactionResponse finalizeAttempt(
            Long paymentId,
            Long transactionId,
            PaymentGatewayResult gatewayResult) {

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));
        PaymentTransaction transaction = transactionRepository
                .findByTransactionIdAndPaymentPaymentId(
                        transactionId,
                        paymentId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with ID " + transactionId
                                + " for payment ID " + paymentId
                ));

        if (transaction.getTransactionStatus() != TransactionStatus.PENDING) {
            return PaymentTransactionResponse.fromEntity(transaction);
        }

        transaction.complete(
                gatewayResult.gatewayTransactionId(),
                gatewayResult.gatewayName(),
                gatewayResult.transactionStatus(),
                gatewayResult.responseCode(),
                gatewayResult.completedAt()
        );

        if (gatewayResult.transactionStatus() == TransactionStatus.SUCCESS) {
            payment.markPaid(gatewayResult.completedAt());
        } else {
            payment.markFailed();
        }

        PaymentTransaction saved = transactionRepository
                .saveAndFlush(transaction);
        paymentRepository.saveAndFlush(payment);
        return PaymentTransactionResponse.fromEntity(saved);
    }

    private void validateReplay(
            PaymentTransaction existing,
            Payment payment,
            ProcessPaymentRequest request) {

        boolean sameMethod = existing.getPaymentMethod()
                == request.getPaymentMethod();
        boolean sameAmount = existing.getTransactionAmount() != null
                && payment.getFinalAmount() != null
                && existing.getTransactionAmount().compareTo(
                        payment.getFinalAmount()
                ) == 0;

        if (!sameMethod || !sameAmount) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was reused with conflicting payment data"
            );
        }
    }

    private void validateNewAttempt(Payment payment) {
        if (payment.getPaymentStatus() != PaymentStatus.CREATED
                && payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new BusinessRuleConflictException(
                    "Payment cannot be processed when its status is "
                            + payment.getPaymentStatus()
            );
        }
        if (payment.getFinalAmount() == null
                || payment.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleConflictException(
                    "Payment does not require a gateway transaction"
            );
        }
    }

    private String createPendingGatewayId() {
        return "PENDING-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    record Reservation(
            boolean created,
            PaymentTransactionResponse transaction) {
    }
}
