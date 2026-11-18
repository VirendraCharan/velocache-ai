package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;

@Service
public class RedisSlidingWindowService {
    private static final Logger logger = LoggerFactory.getLogger(RedisSlidingWindowService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_REQUESTS = 100;

    private static final String LUA_SCRIPT =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2])) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count >= tonumber(ARGV[3]) then " +
            "return 0 " +
            "end " +
            "redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[4]) " +
            "redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]) * 2) " +
            "return 1";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    public boolean isAllowed(String identity, String endpoint) {
        String key = "velocache:ratelimit:" + identity + ":" + endpoint;
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    Long.toString(now),
                    Long.toString(WINDOW_MS),
                    Integer.toString(MAX_REQUESTS),
                    UUID.randomUUID().toString()
            );
            return Long.valueOf(1).equals(result);
        } catch (Exception e) {
            logger.warn("Rate limiter Redis check failed ({})", e.getClass().getName());
            return true;
        }
    }
}