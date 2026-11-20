package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class CacheKeyBuilder {
    @Value("${velocache.cache.shelf-life-seconds:120}")
    private long shelfLifeSeconds = 120;

    public Duration shelfLife() {
        return Duration.ofSeconds(shelfLifeSeconds > 0 ? shelfLifeSeconds : 120);
    }

    public String build(String tenantId, String endpoint) {
        return "velocache:cache:" + tenantId + ":" + endpoint;
    }
}
