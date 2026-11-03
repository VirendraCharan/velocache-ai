package com.velocache.middleware.controller;

import com.velocache.middleware.dto.CacheStatsDto;
import com.velocache.middleware.dto.TrafficPointDto;
import com.velocache.middleware.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/cache-stats")
    public CacheStatsDto getCacheStats(@RequestParam String tenantId ) {
        return dashboardService.getCacheStats(tenantId);
    }
    @GetMapping("/traffic-series")
    public List<TrafficPointDto> getTrafficSeries(
            @RequestParam String tenantId,
            @RequestParam String endpoint,
            @RequestParam(defaultValue = "15") int minutes) {
        return dashboardService.getTrafficSeries(tenantId, endpoint, minutes);
    }
}
