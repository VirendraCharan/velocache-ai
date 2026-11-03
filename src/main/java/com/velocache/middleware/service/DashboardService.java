package com.velocache.middleware.service;

import com.velocache.middleware.dto.CacheStatsDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    @Autowired
    private MeterRegistry meterRegistry;

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
}
