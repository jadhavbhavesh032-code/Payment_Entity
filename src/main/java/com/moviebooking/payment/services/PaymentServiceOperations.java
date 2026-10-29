package com.moviebooking.payment.services;

import com.moviebooking.common.api.PageResponse;
import com.moviebooking.payment.dto.CreatePaymentRequest;
import com.moviebooking.payment.dto.PaymentResponse;
import com.moviebooking.payment.enums.PaymentStatus;

public interface PaymentServiceOperations {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse getPaymentById(Long paymentId);

    PaymentResponse getPaymentByBookingId(Long bookingId);

    PageResponse<PaymentResponse> listPayments(
            Long userId,
            PaymentStatus paymentStatus,
            int page,
            int size,
            String sort);

    PaymentResponse cancelPayment(Long paymentId);
}
