package com.moviebooking.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Entity API")
                .version("1.0")
                .description("Payment, transaction and refund workflows. The current gateways "
                        + "are local mocks: no money is moved. Create one payment per booking, "
                        + "then process it and request partial or full refunds. Transaction and "
                        + "refund POSTs require a unique Idempotency-Key (1-100 characters). "
                        + "An identical retry returns 200 and the existing attempt; conflicting "
                        + "reuse returns 409. New attempts return 201; inspect their financial status."));
    }

    @Bean
    OpenApiCustomizer paymentRequestExamples() {
        return api -> api.getPaths().forEach((path, item) -> {
            var post = item.getPost();
            if (post == null) {
                return;
            }
            if (post.getResponses() != null) {
                var success = post.getResponses().get("200");
                if (!path.endsWith("/cancel") && success != null) {
                    post.getResponses().addApiResponse("201",
                            new io.swagger.v3.oas.models.responses.ApiResponse()
                                    .description("New record created; inspect the financial outcome in data")
                                    .content(success.getContent()));
                    if (path.equals("/api/payments")) {
                        post.getResponses().remove("200");
                    } else {
                        success.setDescription("Existing attempt replayed; gateway is not invoked again");
                    }
                }
                post.getResponses().addApiResponse("400",
                        new io.swagger.v3.oas.models.responses.ApiResponse().description("Invalid body, path or required header"));
                post.getResponses().addApiResponse("409",
                        new io.swagger.v3.oas.models.responses.ApiResponse().description("Duplicate booking, conflicting key or invalid financial state"));
                if (!path.equals("/api/payments")) {
                    post.getResponses().addApiResponse("404",
                            new io.swagger.v3.oas.models.responses.ApiResponse().description("Payment not found"));
                }
            }
            Object example = null;
            if (path.equals("/api/payments")) {
                post.setSummary("Create a payment for a booking");
                example = java.util.Map.of("bookingId", 1001, "userId", 1,
                        "bookingAmount", 500.00, "discountAmount", 0.00, "currency", "INR");
            } else if (path.endsWith("/transactions")) {
                post.setSummary("Process payment (idempotent)");
                example = java.util.Map.of("paymentMethod", "UPI");
            } else if (path.endsWith("/refunds")) {
                post.setSummary("Request a partial or full refund (idempotent)");
                example = java.util.Map.of("refundAmount", 100.00,
                        "refundReason", "Customer requested cancellation");
            } else if (path.endsWith("/cancel")) {
                post.setSummary("Cancel an eligible unpaid payment");
            }
            if (example != null && post.getRequestBody() != null) {
                Object requestExample = example;
                post.getRequestBody().getContent().values()
                        .forEach(media -> media.setExample(requestExample));
            }
            if (post.getParameters() != null) {
                post.getParameters().stream()
                        .filter(parameter -> "Idempotency-Key".equals(parameter.getName()))
                        .forEach(parameter -> {
                            parameter.setRequired(true);
                            parameter.setExample(path.endsWith("/refunds")
                                    ? "refund-1001-01" : "payment-1001-01");
                            parameter.setDescription("Required, nonblank, at most 100 characters. "
                                    + "Reuse for identical retries only. Use a new key for each "
                                    + "new logical request; keys are globally unique within each attempt type.");
                        });
            }
        });
    }
}
