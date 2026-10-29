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
import com.moviebooking.payment.dto.PaymentTransactionResponse;
import com.moviebooking.payment.dto.ProcessPaymentRequest;
import com.moviebooking.payment.services.IdempotentResult;
import com.moviebooking.payment.services.PaymentTransactionServiceOperations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/payments/{paymentId}/transactions")
@Validated
public class PaymentTransactionController {

    private final PaymentTransactionServiceOperations
            paymentTransactionService;

    public PaymentTransactionController(
            PaymentTransactionServiceOperations
                    paymentTransactionService) {

        this.paymentTransactionService =
                paymentTransactionService;
    }

    @PostMapping
    public ResponseEntity<
            ApiResponse<PaymentTransactionResponse>>
            processPayment(
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
                    ProcessPaymentRequest request) {

        IdempotentResult<PaymentTransactionResponse> result =
                paymentTransactionService.processPayment(
                        paymentId,
                        idempotencyKey,
                        request
                );

        PaymentTransactionResponse transaction = result.value();
        HttpStatus responseStatus = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        String action = result.created() ? "recorded" : "replayed";

        return ResponseEntity
                .status(responseStatus)
                .body(ApiResponse.success(
                        "Payment transaction " + action
                                + " with outcome "
                                + transaction.getTransactionStatus(),
                        transaction
                ));
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<PaymentTransactionResponse>>>
            getPaymentTransactions(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId) {

        List<PaymentTransactionResponse> transactions =
                paymentTransactionService
                        .getTransactionsByPaymentId(
                                paymentId
                        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Payment transactions retrieved successfully",
                        transactions
                )
        );
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>>
            getPaymentTransaction(
                    @PathVariable
                    @Positive(message = "Payment ID must be positive")
                    Long paymentId,
                    @PathVariable
                    @Positive(message = "Transaction ID must be positive")
                    Long transactionId) {

        PaymentTransactionResponse transaction =
                paymentTransactionService.getTransactionById(
                        paymentId,
                        transactionId
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Payment transaction retrieved successfully",
                transaction
        ));
    }
}
