package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CacheAsideService {
    private static final Logger logger = LoggerFactory.getLogger(CacheAsideService.class);

    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private OriginService originService;
    @Autowired
    private TenantTrafficService trafficService;
    @Autowired
    private DemoOutageService demoOutageService;

    public String getOrLoad(String tenantId, String endpoint) {
        if (demoOutageService.isOutage(tenantId)) {
            trafficService.recordCacheMiss(tenantId);
            return originService.fetch(tenantId, endpoint);
        }

        String key = cacheKeyBuilder.build(tenantId, endpoint);

        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            logger.warn("Redis cache read failed: {}", exception.getClass().getName());
            cached = null;
        }

        if (cached != null) {
            trafficService.recordCacheHit(tenantId);
            return cached;
        }

        trafficService.recordCacheMiss(tenantId);
        String value = originService.fetch(tenantId, endpoint);
        if (cached == null) {
            try {
                stringRedisTemplate.opsForValue().set(key, value, CacheKeyBuilder.SHELF_LIFE);
            } catch (RuntimeException exception) {
                logger.warn("Redis cache write failed: {}", exception.getClass().getName());
            }
        }
        return value;
    }
}
