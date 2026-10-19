package com.velocache.middleware.controller;

import com.velocache.middleware.service.CacheKeyBuilder;
import com.velocache.middleware.service.OriginService;
import com.velocache.middleware.service.TenantTrafficService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class DataController {
    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private OriginService originService;
    @Autowired
    private TenantTrafficService trafficService;

    @GetMapping("/api/data")
    public String getData(@RequestParam String endpoint, HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        String key = cacheKeyBuilder.build(tenantId, endpoint);

        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            trafficService.recordCacheHit();
            return cached;
        }

        trafficService.recordCacheMiss();
        String value = originService.fetch(tenantId, endpoint);
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10));
        return value;
    }
}
