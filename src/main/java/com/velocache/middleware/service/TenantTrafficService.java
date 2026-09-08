package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantTrafficService {
    private static final long WINDOW_MS = 60_000; // 60 seconds
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
}

