package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisSlidingWindowService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_REQUESTS = 100;

    public boolean isAllowed(String ip, String endpoint) {
        String key = "Velocache:ratelimit:" + ip + ":" + endpoint;
        long now = System.currentTimeMillis();

        try {
            redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MS);
            Long count = redisTemplate.opsForZSet().zCard(key);
            redisTemplate.expire(key, Duration.ofSeconds(120));

            return count != null && count <= MAX_REQUESTS;
        } catch (Exception e) {
            System.err.println("Rate limiter Redis check failed for key " + key + ": " + e.getMessage());
            return true; // Allow the request if Redis fails
        }
    }
}