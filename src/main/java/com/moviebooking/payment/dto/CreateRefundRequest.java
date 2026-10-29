package com.moviebooking.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateRefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(
        value = "0.01",
        inclusive = true,
        message = "Refund amount must be greater than zero"
    )
    @Digits(
        integer = 10,
        fraction = 2,
        message = "Refund amount can contain up to 2 decimal places"
    )
    private BigDecimal refundAmount;

    @NotBlank(message = "Refund reason is required")
    @Size(
        max = 255,
        message = "Refund reason cannot exceed 255 characters"
    )
    private String refundReason;

    public CreateRefundRequest() {
        // Required by JSON deserializers before properties are populated.
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

}
