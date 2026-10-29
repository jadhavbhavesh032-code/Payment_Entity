package com.moviebooking.payment.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import com.moviebooking.common.exception.BusinessRuleConflictException;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.enums.RefundStatus;
import com.moviebooking.payment.enums.TransactionStatus;

/**
 * Central definition of the state transitions permitted for financial records.
 */
public final class FinancialStateTransitions {

    private static final Map<PaymentStatus, Set<PaymentStatus>>
            PAYMENT_TRANSITIONS = Map.of(
                    PaymentStatus.CREATED,
                    Set.of(
                            PaymentStatus.PENDING,
                            PaymentStatus.CANCELLED
                    ),
                    PaymentStatus.PENDING,
                    Set.of(
                            PaymentStatus.PAID,
                            PaymentStatus.FAILED
                    ),
                    PaymentStatus.FAILED,
                    Set.of(
                            PaymentStatus.PENDING,
                            PaymentStatus.CANCELLED
                    ),
                    PaymentStatus.PAID,
                    Set.of(
                            PaymentStatus.PARTIALLY_REFUNDED,
                            PaymentStatus.REFUNDED
                    ),
                    PaymentStatus.PARTIALLY_REFUNDED,
                    Set.of(
                            PaymentStatus.PARTIALLY_REFUNDED,
                            PaymentStatus.REFUNDED
                    ),
                    PaymentStatus.CANCELLED,
                    Set.of(),
                    PaymentStatus.REFUNDED,
                    Set.of()
            );

    private static final Map<TransactionStatus, Set<TransactionStatus>>
            TRANSACTION_TRANSITIONS = Map.of(
                    TransactionStatus.INITIATED,
                    Set.of(),
                    TransactionStatus.PENDING,
                    Set.of(
                            TransactionStatus.SUCCESS,
                            TransactionStatus.FAILED
                    ),
                    TransactionStatus.SUCCESS,
                    Set.of(),
                    TransactionStatus.FAILED,
                    Set.of()
            );

    private static final Map<RefundStatus, Set<RefundStatus>>
            REFUND_TRANSITIONS = Map.of(
                    RefundStatus.REQUESTED,
                    Set.of(RefundStatus.PROCESSING),
                    RefundStatus.PROCESSING,
                    Set.of(
                            RefundStatus.COMPLETED,
                            RefundStatus.FAILED
                    ),
                    RefundStatus.COMPLETED,
                    Set.of(),
                    RefundStatus.FAILED,
                    Set.of(),
                    RefundStatus.REJECTED,
                    Set.of()
            );

    private FinancialStateTransitions() {
    }

    public static void requirePaymentTransition(
            PaymentStatus currentStatus,
            PaymentStatus targetStatus) {

        requireTransition(
                "Payment",
                currentStatus,
                targetStatus,
                PAYMENT_TRANSITIONS
        );
    }

    public static void requireZeroValuePaymentSettlement(
            PaymentStatus currentStatus,
            BigDecimal finalAmount) {

        if (currentStatus != PaymentStatus.CREATED) {
            throw conflict(
                    "Payment",
                    currentStatus,
                    PaymentStatus.PAID
            );
        }

        if (finalAmount == null
                || finalAmount.compareTo(BigDecimal.ZERO) != 0) {

            throw new BusinessRuleConflictException(
                    "Only a zero-value payment can be settled "
                            + "without a gateway transaction"
            );
        }
    }

    public static void requireTransactionTransition(
            TransactionStatus currentStatus,
            TransactionStatus targetStatus) {

        requireTransition(
                "Payment transaction",
                currentStatus,
                targetStatus,
                TRANSACTION_TRANSITIONS
        );
    }

    public static void requireRefundTransition(
            RefundStatus currentStatus,
            RefundStatus targetStatus) {

        requireTransition(
                "Refund",
                currentStatus,
                targetStatus,
                REFUND_TRANSITIONS
        );
    }

    private static <S extends Enum<S>> void requireTransition(
            String recordType,
            S currentStatus,
            S targetStatus,
            Map<S, Set<S>> allowedTransitions) {

        if (currentStatus == null
                || targetStatus == null
                || !allowedTransitions
                        .getOrDefault(currentStatus, Set.of())
                        .contains(targetStatus)) {

            throw conflict(recordType, currentStatus, targetStatus);
        }
    }

    private static BusinessRuleConflictException conflict(
            String recordType,
            Enum<?> currentStatus,
            Enum<?> targetStatus) {

        return new BusinessRuleConflictException(
                recordType
                        + " cannot transition from "
                        + statusName(currentStatus)
                        + " to "
                        + statusName(targetStatus)
        );
    }

    private static String statusName(Enum<?> status) {
        return status == null ? "an undefined status" : status.name();
    }
}
