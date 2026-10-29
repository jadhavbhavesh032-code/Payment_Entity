package com.moviebooking.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.moviebooking.common.api.ApiResponse;
import com.moviebooking.common.api.PageResponse;
import com.moviebooking.payment.dto.CreatePaymentRequest;
import com.moviebooking.payment.dto.PaymentResponse;
import com.moviebooking.payment.enums.PaymentStatus;
import com.moviebooking.payment.services.PaymentServiceOperations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private final PaymentServiceOperations paymentService;

    public PaymentController(PaymentServiceOperations paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>>
            createPayment(
                    @Valid
                    @RequestBody
                    CreatePaymentRequest request) {

        PaymentResponse payment =
                paymentService.createPayment(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Payment created with outcome "
                                + payment.getPaymentStatus(),
                        payment
                ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>>
            listPayments(
                    @RequestParam(required = false)
                    @Positive(message = "User ID must be positive")
                    Long userId,
                    @RequestParam(required = false)
                    PaymentStatus paymentStatus,
                    @RequestParam(defaultValue = "0")
                    @Min(value = 0, message = "Page cannot be negative")
                    int page,
                    @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "Page size must be positive")
                    @Max(value = 100, message = "Page size cannot exceed 100")
                    int size,
                    @RequestParam(defaultValue = "createdAt,desc")
                    String sort) {

        PageResponse<PaymentResponse> payments =
                paymentService.listPayments(
                        userId,
                        paymentStatus,
                        page,
                        size,
                        sort
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Payments retrieved successfully",
                payments
        ));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>>
            getPaymentById(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId) {

        PaymentResponse payment =
                paymentService.getPaymentById(paymentId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment retrieved successfully",
                        payment
                )
        );
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<PaymentResponse>>
            getPaymentByBookingId(
                    @PathVariable
                    @Positive(message = "Booking ID must be positive")
                    Long bookingId) {

        PaymentResponse payment =
                paymentService.getPaymentByBookingId(bookingId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment retrieved successfully",
                        payment
                )
        );
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @PathVariable
            @Positive(message = "Payment ID must be positive")
            Long paymentId) {

        PaymentResponse payment = paymentService.cancelPayment(paymentId);

        return ResponseEntity.ok(ApiResponse.success(
                "Payment cancelled with outcome "
                        + payment.getPaymentStatus(),
                payment
        ));
    }
}
