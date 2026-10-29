package com.moviebooking.payment.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.common.api.PageResponse;
import com.moviebooking.common.exception.BadRequestException;
import com.moviebooking.common.exception.DuplicateResourceException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.CreatePaymentRequest;
import com.moviebooking.payment.dto.PaymentResponse;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.repository.PaymentRepository;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class PaymentService implements PaymentServiceOperations {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "paymentId",
            "bookingId",
            "userId",
            "bookingAmount",
            "discountAmount",
            "finalAmount",
            "amountPaid",
            "paymentStatus",
            "paidAt",
            "createdAt",
            "updatedAt"
    );

    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    public PaymentService(
            PaymentRepository paymentRepository,
            EntityManager entityManager) {

        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        if (request == null) {
            throw new BadRequestException("Payment request is required");
        }
        if (request.getBookingId() == null || request.getBookingId() <= 0) {
            throw new BadRequestException("Booking ID must be positive");
        }
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new BadRequestException("User ID must be positive");
        }
        if (request.getCouponId() != null && request.getCouponId() <= 0) {
            throw new BadRequestException("Coupon ID must be positive");
        }

        if (paymentRepository.existsByBookingId(request.getBookingId())) {
            throw new DuplicateResourceException(
                    "A payment already exists for booking ID "
                            + request.getBookingId()
            );
        }

        BigDecimal bookingAmount = normalizeMoney(
                request.getBookingAmount(),
                "Booking amount"
        );
        if (bookingAmount.compareTo(ZERO_MONEY) <= 0) {
            throw new BadRequestException(
                    "Booking amount must be greater than zero"
            );
        }

        BigDecimal discountAmount = request.getDiscountAmount() == null
                ? ZERO_MONEY
                : normalizeMoney(
                        request.getDiscountAmount(),
                        "Discount amount"
                );

        if (discountAmount.compareTo(ZERO_MONEY) < 0) {
            throw new BadRequestException(
                    "Discount amount cannot be negative"
            );
        }
        if (discountAmount.compareTo(bookingAmount) > 0) {
            throw new BadRequestException(
                    "Discount amount cannot be greater than booking amount"
            );
        }

        BigDecimal finalAmount = bookingAmount.subtract(discountAmount);
        if (finalAmount.compareTo(ZERO_MONEY) < 0) {
            throw new BadRequestException(
                    "Final amount cannot be negative"
            );
        }

        String currency = request.getCurrency() == null
                ? "INR"
                : request.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new BadRequestException(
                    "Currency must contain exactly 3 uppercase letters"
            );
        }

        Payment payment = new Payment(
                request.getBookingId(),
                request.getUserId(),
                request.getCouponId(),
                bookingAmount,
                discountAmount,
                finalAmount
        );
        payment.setCurrency(currency);

        if (finalAmount.compareTo(ZERO_MONEY) == 0) {
            payment.markPaidWithoutGateway(
                    LocalDateTime.now(ZoneOffset.UTC)
            );
        }

        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        entityManager.refresh(savedPayment);
        return PaymentResponse.fromEntity(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        return PaymentResponse.fromEntity(findPayment(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for booking ID " + bookingId
                ));

        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listPayments(
            Long userId,
            PaymentStatus paymentStatus,
            int page,
            int size,
            String sort) {

        if (userId != null && userId <= 0) {
            throw new BadRequestException("User ID must be positive");
        }
        if (page < 0) {
            throw new BadRequestException("Page cannot be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        Pageable pageable = PageRequest.of(page, size, parseSort(sort));

        Page<Payment> payments;
        if (userId != null && paymentStatus != null) {
            payments = paymentRepository.findByUserIdAndPaymentStatus(
                    userId,
                    paymentStatus,
                    pageable
            );
        } else if (userId != null) {
            payments = paymentRepository.findByUserId(userId, pageable);
        } else if (paymentStatus != null) {
            payments = paymentRepository.findByPaymentStatus(
                    paymentStatus,
                    pageable
            );
        } else {
            payments = paymentRepository.findAll(pageable);
        }

        return PageResponse.from(payments, PaymentResponse::fromEntity);
    }

    @Override
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));

        payment.cancel();
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        entityManager.refresh(savedPayment);
        return PaymentResponse.fromEntity(savedPayment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with ID " + paymentId
                ));
    }

    private Sort parseSort(String sortValue) {
        String value = sortValue == null || sortValue.isBlank()
                ? "createdAt,desc"
                : sortValue.trim();
        String[] parts = value.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new BadRequestException(
                    "Sort must use the format property,direction"
            );
        }

        String property = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            throw new BadRequestException(
                    "Unsupported payment sort field: " + property
            );
        }

        String directionValue = parts.length == 1
                ? "asc"
                : parts[1].trim();
        try {
            return Sort.by(
                    Sort.Direction.fromString(directionValue),
                    property
            );
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Sort direction must be 'asc' or 'desc'"
            );
        }
    }

    private BigDecimal normalizeMoney(
            BigDecimal amount,
            String fieldName) {

        if (amount == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(
                    fieldName + " can contain up to 2 decimal places"
            );
        }
    }
}
