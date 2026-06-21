package com.paymentapp.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client request throttle implemented with Bucket4j. Mirrors the API Gateway
 * tier in the architecture diagram (≈100 req/s, HTTP 429 on breach) so the limit
 * is still enforced when traffic reaches the app directly.
 *
 * A token bucket is kept per client IP. The bucket holds {@code capacity} tokens
 * and refills greedily over {@code refill-period}, so the steady-state rate is
 * {@code capacity / refill-period}. Defaults: 100 tokens per 1 second.
 *
 * Registered ahead of the Spring Security chain (see {@code RateLimitConfig}) so
 * abusive traffic is shed before any authentication work is done.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${app.ratelimit.capacity:100}")
    private long capacity;

    @Value("${app.ratelimit.refill-period-seconds:1}")
    private long refillPeriodSeconds;

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(capacity, Duration.ofSeconds(refillPeriodSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(clientKey(request), k -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            tooManyRequests(request, response);
        }
    }

    /** Identifies the caller by originating IP (honouring the proxy header). */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void tooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("Rate limit exceeded for {} on {}", clientKey(request), request.getRequestURI());
        response.setStatus(429); // 429 Too Many Requests (no constant in Servlet 3.x)
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(refillPeriodSeconds));
        response.getWriter().write(String.format(
                "{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Rate limit exceeded. Please retry shortly.\","
                        + "\"timestamp\":\"%s\"}",
                LocalDateTime.now()));
    }
}