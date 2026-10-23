package com.example.apimonitor.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        Bucket bucket = rateLimitService.getBucket(clientIp);

        ConsumptionProbe probe =
                bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader(
                "X-RateLimit-Limit",
                String.valueOf(rateLimitService.getLimit())
        );

        if (probe.isConsumed()) {
            response.setHeader(
                    "X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens())
            );

            filterChain.doFilter(request, response);
            return;
        }

        long waitNanos = probe.getNanosToWaitForRefill();
        long retryAfter = Math.max(
                1,
                (long) Math.ceil(
                        waitNanos / 1_000_000_000.0
                )
        );

        response.setStatus(429);
        response.setHeader(
                "Retry-After",
                String.valueOf(retryAfter)
        );
        response.setHeader(
                "X-RateLimit-Remaining",
                "0"
        );
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"status\":429,"
                        + "\"error\":\"Too Many Requests\","
                        + "\"message\":\"Rate limit exceeded. Try again later.\"}"
        );
    }
}