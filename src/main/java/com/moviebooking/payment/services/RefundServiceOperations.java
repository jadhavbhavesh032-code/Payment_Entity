package com.moviebooking.payment.services;

import java.util.List;

import com.moviebooking.payment.dto.CreateRefundRequest;
import com.moviebooking.payment.dto.RefundResponse;

public interface RefundServiceOperations {

    IdempotentResult<RefundResponse> createRefund(
            Long paymentId,
            String idempotencyKey,
            CreateRefundRequest request);

    List<RefundResponse> getRefundsByPaymentId(Long paymentId);

    RefundResponse getRefundById(
            Long paymentId,
            Long refundId);
}
