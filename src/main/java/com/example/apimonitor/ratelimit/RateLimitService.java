package com.example.apimonitor.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    private static final long CAPACITY = 60;

    private final ConcurrentMap<String, Bucket> buckets =
            new ConcurrentHashMap<>();

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
                CAPACITY,
                Refill.greedy(CAPACITY, Duration.ofMinutes(1))
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public Bucket getBucket(String clientIp) {
        return buckets.computeIfAbsent(
                clientIp,
                ip -> createBucket()
        );
    }

    public long getLimit() {
        return CAPACITY;
    }
}