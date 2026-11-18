package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheAsideService {
    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private OriginService originService;
    @Autowired
    private TenantTrafficService trafficService;

    public String getOrLoad(String tenantId, String endpoint) {
        String key = cacheKeyBuilder.build(tenantId, endpoint);

        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            trafficService.recordCacheHit(tenantId);
            return cached;
        }

        trafficService.recordCacheMiss(tenantId);
        String value = originService.fetch(tenantId, endpoint);
        stringRedisTemplate.opsForValue().set(key, value, CacheKeyBuilder.SHELF_LIFE);
        return value;
    }
}
