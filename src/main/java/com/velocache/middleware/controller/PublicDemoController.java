package com.velocache.middleware.controller;

import com.velocache.middleware.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

// Everything under /api/public is readable without a key, so it must never expose tenant-specific data except through an explicit allow-list of demo tenants.
@RestController
@RequestMapping("/api/public")
public class PublicDemoController {
    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/benchmark")
    public ResponseEntity<JsonNode> getBenchmark() {
        JsonNode benchmark = dashboardService.getBenchmark();
        return benchmark == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(benchmark);
    }
}
