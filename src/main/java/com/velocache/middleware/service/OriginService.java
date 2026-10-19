package com.velocache.middleware.service;

import org.springframework.stereotype.Service;

@Service
public class OriginService {
    public String fetch(String tenantId, String endpoint) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "origin-data-for-" + tenantId + endpoint;
    }
}
