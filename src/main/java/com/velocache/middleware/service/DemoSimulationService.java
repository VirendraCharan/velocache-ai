package com.velocache.middleware.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This only ever runs for demo tenants chosen by the caller, with a fixed endpoint,
 * fixed size and a cooldown, so a visitor cannot use it as a free load generator.
 */
@Service
public class DemoSimulationService {
    private static final Logger logger = LoggerFactory.getLogger(DemoSimulationService.class);

    public static final String DEMO_ENDPOINT = "/api/data";
    public static final int BURST_CALLS = 40;
    public static final long PAUSE_MILLIS = 150;
    public static final long COOLDOWN_MILLIS = 60000;

    @Autowired
    private CacheAsideService cacheAsideService;
    @Autowired
    private TenantTrafficService trafficService;

    private final ConcurrentHashMap<String, Long> lastStart = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            runnable -> {
                Thread thread = new Thread(runnable, "demo-simulation-worker");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public enum Result {
        STARTED, COOLING_DOWN, BUSY
    }

    public Result start(String tenantId) {
        long now = System.currentTimeMillis();
        AtomicReference<Result> result = new AtomicReference<>(Result.STARTED);
        AtomicReference<Long> previousStart = new AtomicReference<>();
        lastStart.compute(tenantId, (key, previous) -> {
            if (previous != null && now - previous < COOLDOWN_MILLIS) {
                result.set(Result.COOLING_DOWN);
                return previous;
            }
            previousStart.set(previous);
            return now;
        });

        if (result.get() == Result.COOLING_DOWN) {
            return Result.COOLING_DOWN;
        }

        try {
            worker.execute(() -> runBurst(tenantId));
            return Result.STARTED;
        } catch (RejectedExecutionException exception) {
            Long previous = previousStart.get();
            if (previous == null) {
                lastStart.remove(tenantId, now);
            } else {
                lastStart.replace(tenantId, now, previous);
            }
            return Result.BUSY;
        }
    }

    private void runBurst(String tenantId) {
        try {
            for (int i = 0; i < BURST_CALLS; i++) {
                trafficService.recordRequest(tenantId, DEMO_ENDPOINT);
                cacheAsideService.getOrLoad(tenantId, DEMO_ENDPOINT);
                Thread.sleep(PAUSE_MILLIS);
            }
        } catch (Throwable exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Demo burst stopped: {}", exception.getClass().getName());
        }
    }
}
