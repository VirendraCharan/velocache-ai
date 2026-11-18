package com.velocache.middleware.controller;

import com.velocache.middleware.dto.CacheStatsDto;
import com.velocache.middleware.dto.ForecastOverlayDto;
import com.velocache.middleware.dto.WarmingEventDto;
import com.velocache.middleware.service.DashboardService;
import com.velocache.middleware.service.WarmingEventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

// Everything under /api/public is readable without a key, so it must never expose tenant-specific data except through an explicit allow-list of demo tenants.
@RestController
@RequestMapping("/api/public")
public class PublicDemoController {
    private static final String DEMO_ENDPOINT = "/api/data";

    @Value("${velocache.demo-tenants:demo-a,demo-b}")
    private String demoTenantsCsv;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WarmingEventLog warmingEventLog;

    private boolean isDemoTenant(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return false;
        }
        for (String tenant : demoTenantsCsv == null ? new String[0] : demoTenantsCsv.split(",")) {
            String trimmedTenant = tenant.trim();
            if (!trimmedTenant.isEmpty() && tenantId.equals(trimmedTenant)) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/tenants")
    public List<String> getTenants() {
        List<String> tenants = new ArrayList<>();
        if (demoTenantsCsv == null) {
            return tenants;
        }
        for (String tenant : demoTenantsCsv.split(",")) {
            String trimmedTenant = tenant.trim();
            if (!trimmedTenant.isEmpty()) {
                tenants.add(trimmedTenant);
            }
        }
        return tenants;
    }

    @GetMapping("/cache-stats")
    public ResponseEntity<CacheStatsDto> getCacheStats(@RequestParam String tenantId) {
        if (!isDemoTenant(tenantId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dashboardService.getCacheStats(tenantId));
    }

    @GetMapping("/forecast-overlay")
    public ResponseEntity<ForecastOverlayDto> getForecastOverlay(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "15") int minutes) {
        if (!isDemoTenant(tenantId)) {
            return ResponseEntity.notFound().build();
        }
        int clampedMinutes = Math.max(1, Math.min(60, minutes));
        return ResponseEntity.ok(dashboardService.getOverlay(tenantId, DEMO_ENDPOINT, clampedMinutes));
    }

    @GetMapping("/warming-events")
    public ResponseEntity<List<WarmingEventDto>> getWarmingEvents(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "20") int limit) {
        if (!isDemoTenant(tenantId)) {
            return ResponseEntity.notFound().build();
        }
        int clampedLimit = Math.max(1, Math.min(50, limit));
        return ResponseEntity.ok(warmingEventLog.recent(tenantId, clampedLimit));
    }

    @GetMapping("/benchmark")
    public ResponseEntity<JsonNode> getBenchmark() {
        JsonNode benchmark = dashboardService.getBenchmark();
        return benchmark == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(benchmark);
    }
}
