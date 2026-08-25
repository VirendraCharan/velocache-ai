package com.velocache.middleware.service;

import com.velocache.middleware.entity.TrafficLog;
import com.velocache.middleware.repository.TrafficLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class TrafficService {

    @Autowired
    private TrafficLogRepository trafficLogRepository;
    public TrafficLog saveTraffic(String endpoint, int requestCount) {
        TrafficLog log = new TrafficLog();
        log.setEndpoint(endpoint);
        log.setRequestCount(requestCount);
        log.setRecordedAt(LocalDateTime.now());

        return trafficLogRepository.save(log);
    }

    public List<TrafficLog> getLogsByEndpoint(String endpoint) {
        return trafficLogRepository.findByEndpoint(endpoint);
    }
}
