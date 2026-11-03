package com.velocache.middleware.service;

import com.velocache.middleware.dto.CacheStatsDto;
import com.velocache.middleware.dto.TrafficPointDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private TenantTrafficService tenantTrafficService;
    private long readCounter(String name, String tenant) {
        Counter counter = meterRegistry.find(name).tag("tenant", tenant).counter();
        return counter == null ? 0 : (long) counter.count();
    }

    public CacheStatsDto getCacheStats(String tenantId) {
        long hits = readCounter("cache_hit_total", tenantId);
        long misses = readCounter("cache_miss_total", tenantId);
        long total = hits + misses;
        double hitRate = total == 0 ? 0 : (double) hits / total;
        return new CacheStatsDto(tenantId, hits, misses, hitRate);
    }

    public List<TrafficPointDto> getTrafficSeries(String tenantId, String endpoint, int minutes) {
        Map<Integer, Integer> counts = tenantTrafficService.getCountsByMinute(tenantId, endpoint);
        LocalDateTime now = LocalDateTime.now();
        List<TrafficPointDto> series = new ArrayList<>();
        for (int i = minutes - 1; i >= 0; i--) {
            LocalDateTime t = now.minusMinutes(i);
            int minuteOfDay = t.getHour() * 60 + t.getMinute();
            String label = String.format("%02d:%02d", t.getHour(), t.getMinute());
            series.add(new TrafficPointDto(label, counts.getOrDefault(minuteOfDay, 0)));
        }
        return series;
    }
}
