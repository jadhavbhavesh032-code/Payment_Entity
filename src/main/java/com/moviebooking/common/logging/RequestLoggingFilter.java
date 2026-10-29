package com.moviebooking.common.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String previousRequestId = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                long durationMs = (System.nanoTime() - started) / 1_000_000;
                // Use the matched route template, never a raw URL or query string.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String method = request.getMethod().replaceAll("[^A-Z]", "_");
                String message = "HTTP request method={} route={} status={} durationMs={}";
                if (status >= 500) {
                    LOGGER.error(message, method, route == null ? "unmapped" : route, status, durationMs);
                } else if (status >= 400) {
                    LOGGER.warn(message, method, route == null ? "unmapped" : route, status, durationMs);
                } else {
                    LOGGER.info(message, method, route == null ? "unmapped" : route, status, durationMs);
                }
            } finally {
                if (previousRequestId == null) {
                    MDC.remove("requestId");
                } else {
                    MDC.put("requestId", previousRequestId);
                }
            }
        }
    }
}
