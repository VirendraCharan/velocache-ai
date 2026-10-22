package com.velocache.middleware.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantTrafficService {
    private static final long WINDOW_MS = 15 * 60_000; // 15 minutes
    @Autowired
    private MeterRegistry meterRegistry;

    public void recordCacheHit(String tenantId) {
        meterRegistry.counter("cache_hit_total", "tenant", tenantId).increment();
    }

    public void recordCacheMiss(String tenantId) {
        meterRegistry.counter("cache_miss_total", "tenant", tenantId).increment();
    }
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void recordRequest(String tenantId, String endpoint) {
        String key = "velocache:traffic:" + tenantId + ":" + endpoint;
        long now = System.currentTimeMillis();

        try {
            redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MS);
            System.out.println("Recorded request in Redis for key: " + key);
        } catch (Exception e) {
            System.err.println("Traffic tracking failed for key" + ": " + e.getMessage());
        }
    }

    public Set<ZSetOperations.TypedTuple<Object>> getRecentEntries(String tenantId, String endpoint) {
        String key = "velocache:traffic:" + tenantId + ":" + endpoint;
        return redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
    }

    public Map<Integer, Integer> getCountsByMinute(String tenantId, String endpoint) {
        Set<ZSetOperations.TypedTuple<Object>> entries = getRecentEntries(tenantId, endpoint);
        Map<Integer, Integer> countsByMinute = new LinkedHashMap<>();

        for (ZSetOperations.TypedTuple<Object> entry : entries) {
            long timestampMs = entry.getScore().longValue();
            LocalDateTime dt = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault());
            int minuteOfDay = dt.getHour() * 60 + dt.getMinute();

            countsByMinute.merge(minuteOfDay, 1, Integer::sum);
        }

        return countsByMinute;
    }
    public Set<String> getAllTrafficKeys() {
        return redisTemplate.keys("velocache:traffic:*");
    }
}
