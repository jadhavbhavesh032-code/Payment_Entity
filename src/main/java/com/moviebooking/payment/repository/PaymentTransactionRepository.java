package com.moviebooking.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.moviebooking.payment.entity.PaymentTransaction;
import com.moviebooking.payment.enums.TransactionStatus;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction>
            findByPaymentPaymentIdOrderByAttemptNumberAsc(
                    Long paymentId
            );

    Optional<PaymentTransaction> findByGatewayTransactionId(
            String gatewayTransactionId
    );

    Optional<PaymentTransaction>
            findByPaymentPaymentIdAndIdempotencyKey(
                    Long paymentId,
                    String idempotencyKey
            );

    Optional<PaymentTransaction>
            findFirstByIdempotencyKeyOrderByTransactionIdAsc(
                    String idempotencyKey
            );

    Optional<PaymentTransaction>
            findByTransactionIdAndPaymentPaymentId(
                    Long transactionId,
                    Long paymentId
            );

    @Query("""
            SELECT MAX(pt.attemptNumber)
            FROM PaymentTransaction pt
            WHERE pt.payment.paymentId = :paymentId
            """)
    Integer findMaximumAttemptNumberByPaymentId(
            @Param("paymentId") Long paymentId
    );

    Optional<PaymentTransaction>
            findFirstByPaymentPaymentIdAndTransactionStatusOrderByAttemptNumberDesc(
                    Long paymentId,
                    TransactionStatus transactionStatus
            );
}
