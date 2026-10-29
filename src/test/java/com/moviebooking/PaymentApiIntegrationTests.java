package com.moviebooking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiIntegrationTests {
    @Value("${local.server.port}")
    int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();

    private HttpResponse<String> request(String method, String path, String body, String key)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertNotNull(UUID.fromString(requestId));
        return response;
    }

    private JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    @Test
    void processingReplayConflictsAndRefundAccounting() throws Exception {
        long booking = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1;
        String body = "{\"bookingId\":" + booking
                + ",\"userId\":1,\"bookingAmount\":500,\"currency\":\"INR\"}";
        var payment = expect(201, request("POST", "/api/payments", body, null)).get("data");
        String path = "/api/payments/" + payment.get("paymentId").asLong();
        expect(409, request("POST", "/api/payments", body, null));
        expect(400, request("POST", path + "/transactions", "{}", null));
        String key = UUID.randomUUID().toString();
        String method = "{\"paymentMethod\":\"UPI\"}";
        var transaction = expect(201, request("POST", path + "/transactions", method, key));
        assertEquals("SUCCESS", transaction.at("/data/transactionStatus").asString());
        var replay = expect(200, request("POST", path + "/transactions", method, key));
        assertEquals(transaction.at("/data/transactionId"), replay.at("/data/transactionId"));
        expect(409, request("POST", path + "/transactions", "{\"paymentMethod\":\"CARD\"}", key));
        expect(409, request("POST", path + "/transactions", method, UUID.randomUUID().toString()));
        String refundKey = UUID.randomUUID().toString();
        String refundBody = "{\"refundAmount\":100,\"refundReason\":\"Test cancellation\"}";
        var refund = expect(201, request("POST", path + "/refunds", refundBody, refundKey));
        assertEquals("COMPLETED", refund.at("/data/refundStatus").asString());
        var refundReplay = expect(200, request("POST", path + "/refunds", refundBody, refundKey));
        assertEquals(refund.at("/data/refundId"), refundReplay.at("/data/refundId"));
        expect(409, request("POST", path + "/refunds",
                "{\"refundAmount\":101,\"refundReason\":\"Test cancellation\"}", refundKey));
        expect(409, request("POST", path + "/refunds",
                "{\"refundAmount\":401,\"refundReason\":\"Too much\"}", UUID.randomUUID().toString()));
        assertEquals("PARTIALLY_REFUNDED", expect(200, request("GET", path, null, null))
                .at("/data/paymentStatus").asString());
        expect(201, request("POST", path + "/refunds",
                "{\"refundAmount\":400,\"refundReason\":\"Remaining balance\"}", UUID.randomUUID().toString()));
        assertEquals("REFUNDED", expect(200, request("GET", path, null, null))
                .at("/data/paymentStatus").asString());
        assertEquals(1, expect(200, request("GET", path + "/transactions", null, null)).get("data").size());
        assertEquals(2, expect(200, request("GET", path + "/refunds", null, null)).get("data").size());
    }

    @Test
    void rejectsInvalidRequestsAndDocumentsRequiredHeaders() throws Exception {
        expect(400, request("POST", "/api/payments", "{\"bookingId\":-1,\"userId\":0,\"bookingAmount\":-5}", null));
        expect(400, request("GET", "/api/payments?size=101", null, null));
        expect(400, request("GET", "/api/payments?sort=unknown,desc", null, null));
        expect(400, request("POST", "/api/payments", "{\"unexpected\":true}", null));
        var docs = expect(200, request("GET", "/v3/api-docs", null, null));
        for (String endpoint : new String[]{"transactions", "refunds"}) {
            var operation = docs.get("paths").get("/api/payments/{paymentId}/" + endpoint).get("post");
            boolean requiredKey = false;
            for (var parameter : operation.get("parameters")) {
                if ("Idempotency-Key".equals(parameter.get("name").asString())) {
                    requiredKey = parameter.get("required").asBoolean();
                    assertNotNull(parameter.get("example"));
                }
            }
            assertTrue(requiredKey, endpoint);
            assertNotNull(operation.at("/requestBody/content/application~1json/example"));
        }
        assertEquals(200, request("GET", "/swagger-ui/index.html", null, null).statusCode());
    }
}
