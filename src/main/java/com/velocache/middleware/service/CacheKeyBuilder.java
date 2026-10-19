package com.velocache.middleware.service;

import org.springframework.stereotype.Component;

@Component
public class CacheKeyBuilder {
    public String build(String tenantId, String endpoint) {
        return "velocache:cache:" + tenantId + ":" + endpoint;
    }
}
