package com.moviebooking.payment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.moviebooking.common.api.ApiResponse;
import com.moviebooking.payment.dto.CreateRefundRequest;
import com.moviebooking.payment.dto.RefundResponse;
import com.moviebooking.payment.services.IdempotentResult;
import com.moviebooking.payment.services.RefundServiceOperations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/payments/{paymentId}/refunds")
@Validated
public class RefundController {

    private final RefundServiceOperations refundService;

    public RefundController(
            RefundServiceOperations refundService) {

        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RefundResponse>>
            createRefund(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId,
                    @RequestHeader("Idempotency-Key")
                    @NotBlank(message = "Idempotency-Key cannot be blank")
                    @Size(
                        max = 100,
                        message = "Idempotency-Key cannot exceed 100 characters"
                    )
                    String idempotencyKey,
                    @Valid
                    @RequestBody
                    CreateRefundRequest request) {

        IdempotentResult<RefundResponse> result =
                refundService.createRefund(
                        paymentId,
                        idempotencyKey,
                        request
                );

        RefundResponse refund = result.value();
        HttpStatus responseStatus = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        String action = result.created() ? "recorded" : "replayed";

        return ResponseEntity
                .status(responseStatus)
                .body(ApiResponse.success(
                        "Refund attempt " + action
                                + " with outcome "
                                + refund.getRefundStatus(),
                        refund
                ));
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<RefundResponse>>>
            getPaymentRefunds(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId) {

        List<RefundResponse> refunds =
                refundService
                        .getRefundsByPaymentId(
                                paymentId
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Refund history retrieved successfully",
                        refunds
                )
        );
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>>
            getRefundById(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId,
                    @PathVariable
                    @Positive(message = "Refund ID must be positive")
                    Long refundId) {

        RefundResponse refund =
                refundService.getRefundById(
                        paymentId,
                        refundId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Refund retrieved successfully",
                        refund
                )
        );
    }
}
