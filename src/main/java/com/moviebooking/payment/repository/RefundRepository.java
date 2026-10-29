package com.moviebooking.payment.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.moviebooking.payment.entity.Refund;
import com.moviebooking.payment.enums.RefundStatus;

public interface RefundRepository
        extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentPaymentIdOrderByRequestedAtAsc(
            Long paymentId
    );

    Optional<Refund> findByRefundIdAndPaymentPaymentId(
            Long refundId,
            Long paymentId
    );

    Optional<Refund> findByPaymentPaymentIdAndIdempotencyKey(
            Long paymentId,
            String idempotencyKey
    );

    Optional<Refund> findFirstByIdempotencyKeyOrderByRefundIdAsc(
            String idempotencyKey
    );

    @Query("""
            SELECT SUM(refund.refundAmount)
            FROM Refund refund
            WHERE refund.payment.paymentId = :paymentId
              AND refund.refundStatus = :refundStatus
            """)
    BigDecimal sumRefundAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("refundStatus") RefundStatus refundStatus
    );

    @Query("""
            SELECT SUM(refund.refundAmount)
            FROM Refund refund
            WHERE refund.payment.paymentId = :paymentId
              AND refund.refundStatus IN :refundStatuses
            """)
    BigDecimal sumRefundAmountByPaymentIdAndStatuses(
            @Param("paymentId") Long paymentId,
            @Param("refundStatuses")
            Collection<RefundStatus> refundStatuses
    );
}
