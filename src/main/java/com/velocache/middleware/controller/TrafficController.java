package com.velocache.middleware.controller;
import com.velocache.middleware.TrafficData;
import com.velocache.middleware.entity.TrafficLog;
import com.velocache.middleware.repository.TrafficLogRepository;
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
    public String trackTraffic(@RequestBody TrafficData data) {
        TrafficLog log = new TrafficLog();
        log.setEndpoint(data.getEndpoint());
        log.setRequestCount(data.getRequestCount());
        log.setRecordedAt(LocalDateTime.now());
        trafficLogRepository.save(log);
        return "Saved traffic log for " + log.getEndpoint();
    }

    @GetMapping("/api/stats/{endpoint}")
    public List<TrafficLog> getStats(@PathVariable String endpoint) {
        return trafficLogRepository.findByEndpoint(endpoint);
    }
}
