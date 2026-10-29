package com.moviebooking.common.logging;

import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.jupiter.api.Assertions.*;

class RequestLoggingFilterTests {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Map<String, String> previousContext;
    private Level previousLevel;

    @BeforeEach
    void attach() {
        previousContext = MDC.getCopyOfContextMap();
        MDC.clear();
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
        MDC.clear();
        if (previousContext != null) MDC.setContextMap(previousContext);
    }

    @Test
    void correlatesRequestWithoutLoggingSensitiveInputAndRestoresContext() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/payments/private-value");
        request.setQueryString("token=secret-query");
        request.addHeader("Authorization", "secret-authorization");
        request.addHeader("Idempotency-Key", "secret-key");
        request.addHeader("X-Request-ID", "untrusted-id");
        request.setContent("secret-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");

        filter.doFilter(request, response, (req, res) -> {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/api/payments/{paymentId}");
            assertEquals(response.getHeader("X-Request-ID"), MDC.get("requestId"));
            response.setStatus(409);
        });

        String id = response.getHeader("X-Request-ID");
        assertNotNull(UUID.fromString(id));
        assertEquals("outer-context", MDC.get("requestId"));
        assertEquals(1, appender.list.size());
        var event = appender.list.getFirst();
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(id, event.getMDCPropertyMap().get("requestId"));
        String message = event.getFormattedMessage();
        assertTrue(message.contains("route=/api/payments/{paymentId} status=409 durationMs="));
        for (String secret : new String[]{"private-value", "secret-query", "secret-authorization",
                "secret-key", "secret-body", "untrusted-id"}) {
            assertFalse(message.contains(secret));
        }
    }

    @Test
    void logsEscapingFailureAs500AndClearsContext() {
        var request = new MockHttpServletRequest("GET", "/private-path");
        var response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (req, res) -> { throw new ServletException("failure"); }));
        assertNull(MDC.get("requestId"));
        assertEquals(Level.ERROR, appender.list.getFirst().getLevel());
        assertTrue(appender.list.getFirst().getFormattedMessage()
                .contains("route=unmapped status=500"));
    }

    @Test
    void logsSuccessfulRequestAtInfo() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/payments"), response,
                (req, res) -> response.setStatus(200));
        assertEquals(Level.INFO, appender.list.getFirst().getLevel());
        assertNull(MDC.get("requestId"));
    }
}
