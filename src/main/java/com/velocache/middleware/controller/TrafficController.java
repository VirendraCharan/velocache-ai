package com.velocache.middleware.controller;
import com.velocache.middleware.TrafficData;
import com.velocache.middleware.entity.TrafficLog;
import com.velocache.middleware.repository.TrafficLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class TrafficController {

    @GetMapping("/api/health")
    public String healthCheck() {
        return "VeloCache AI is running";
    }
    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @PostMapping("/api/track")
    public String trackTraffic(@RequestBody TrafficData data, HttpServletRequest request) {
        TrafficLog log = new TrafficLog();
        String tenant = (String) request.getAttribute("tenantId");
        log.setTenantId(tenant);
        log.setEndpoint(data.getEndpoint());
        log.setRequestCount(data.getRequestCount());
        log.setRecordedAt(LocalDateTime.now());
        trafficLogRepository.save(log);
        return "Saved traffic log for " + log.getEndpoint();
    }

    @GetMapping("/api/stats/{endpoint}")
    public List<TrafficLog> getStats(@PathVariable String endpoint, HttpServletRequest request) {
        String tenant = (String) request.getAttribute("tenantId");
        return trafficLogRepository.findByTenantIdAndEndpoint(tenant, endpoint);
    }
}
