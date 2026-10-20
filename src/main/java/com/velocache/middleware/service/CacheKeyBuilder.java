package com.velocache.middleware.service;

import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class CacheKeyBuilder {
    public static final Duration SHELF_LIFE = Duration.ofMinutes(2);
    public String build(String tenantId, String endpoint) {
        return "velocache:cache:" + tenantId + ":" + endpoint;
    }
}
