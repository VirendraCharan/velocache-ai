package com.velocache.middleware.service;

import com.velocache.middleware.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private TenantTrafficService tenantTrafficService;
    @Autowired
    private ObjectMapper objectMapper;

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

    private ForecastData readForecast(String tenantId, String endpoint) {
        String fileName = tenantId + "__" + endpoint.replace("/", "~") + "-forecast.json";
        File file = new File("forecaster", fileName);
        if (!file.exists()) {
            return null;
        }
        try {
            return objectMapper.readValue(file, ForecastData.class);
        } catch (Exception e) {
            return null;
        }
    }

    public ForecastStatusDto getForecastStatus(String tenantId, String endpoint) {
        ForecastData forecast = readForecast(tenantId, endpoint);
        if (forecast == null) {
            return new ForecastStatusDto("missing", null, null, null);
        }
        try {
            LocalDateTime generatedAt = LocalDateTime.parse(forecast.getGeneratedAt());
            String status = generatedAt.isBefore(LocalDateTime.now().minusMinutes(15)) ? "stale" : "ok";
            String minute = null;
            if ("ok".equals(status) && forecast.getPredictedSpikeAt() != null) {
                try {
                    minute = LocalDateTime.parse(forecast.getPredictedSpikeAt())
                            .format(DateTimeFormatter.ofPattern("HH:mm"));
                } catch (Exception e) {
                    minute = null;
                }
            }
            return new ForecastStatusDto(status, forecast.getConfidence(), minute, forecast.getCurrentBaseline());
        } catch (Exception e) {
            return new ForecastStatusDto("missing", null, null, null);
        }
    }

    public ForecastOverlayDto getOverlay(String tenantId, String endpoint, int minutes) {
        List<TrafficPointDto> series = getTrafficSeries(tenantId, endpoint, minutes);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 10; i++) {
            LocalDateTime t = now.plusMinutes(i);
            series.add(new TrafficPointDto(String.format("%02d:%02d", t.getHour(), t.getMinute()), null));
        }
        return new ForecastOverlayDto(series, getForecastStatus(tenantId, endpoint));
    }
}
