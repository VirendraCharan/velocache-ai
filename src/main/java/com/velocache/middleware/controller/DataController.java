package com.velocache.middleware.controller;

import com.velocache.middleware.service.CacheAsideService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class DataController {
    @Autowired
    private CacheAsideService cacheAsideService;

    @Value("${velocache.ops-key:}")
    private String opsKey;

    @GetMapping("/api/data")
    public String getData(@RequestParam String endpoint, HttpServletRequest request) {
        String tenantId = (String) request.getAttribute("tenantId");
        return cacheAsideService.getOrLoad(
                tenantId,
                endpoint,
                isBenchmarkCacheBypass(request));
    }

    private boolean isBenchmarkCacheBypass(HttpServletRequest request) {
        String suppliedOpsKey = request.getHeader("X-Ops-Key");
        String mode = request.getHeader("X-Benchmark-Cache");
        return "bypass".equals(mode)
                && !opsKey.isEmpty()
                && suppliedOpsKey != null
                && MessageDigest.isEqual(
                opsKey.getBytes(StandardCharsets.UTF_8),
                suppliedOpsKey.getBytes(StandardCharsets.UTF_8));
    }
}
