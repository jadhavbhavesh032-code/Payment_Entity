package com.moviebooking.payment.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.enums.PaymentStatus;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    Page<Payment> findByUserId(
            Long userId,
            Pageable pageable
    );

    Page<Payment> findByPaymentStatus(
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    Page<Payment> findByUserIdAndPaymentStatus(
            Long userId,
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment
            FROM Payment payment
            WHERE payment.paymentId = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") Long paymentId
    );
}
