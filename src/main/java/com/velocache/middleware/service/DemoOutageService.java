package com.velocache.middleware.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulates an outage only for demo tenants and never touches Redis.
 * The caller must only pass tenant ids from the demo allow-list.
 */
@Service
public class DemoOutageService {
    public static final long OUTAGE_MILLIS = 30000;
    public static final long COOLDOWN_MILLIS = 60000;

    private final ConcurrentHashMap<String, Long> lastStart = new ConcurrentHashMap<>();

    public enum Result {
        STARTED, COOLING_DOWN
    }

    public Result start(String tenantId) {
        long now = System.currentTimeMillis();
        AtomicReference<Result> result = new AtomicReference<>(Result.STARTED);
        lastStart.compute(tenantId, (key, previous) -> {
            if (previous != null && now - previous < COOLDOWN_MILLIS) {
                result.set(Result.COOLING_DOWN);
                return previous;
            }
            return now;
        });
        return result.get();
    }

    public boolean isOutage(String tenantId) {
        Long start = lastStart.get(tenantId);
        return start != null && System.currentTimeMillis() - start < OUTAGE_MILLIS;
    }

    public long secondsRemaining(String tenantId) {
        Long start = lastStart.get(tenantId);
        if (start == null) {
            return 0;
        }
        long remaining = OUTAGE_MILLIS - (System.currentTimeMillis() - start);
        return remaining <= 0 ? 0 : (long) Math.ceil(remaining / 1000.0);
    }
}
