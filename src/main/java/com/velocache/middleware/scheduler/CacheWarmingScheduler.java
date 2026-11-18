package com.velocache.middleware.scheduler;

import com.velocache.middleware.dto.ForecastData;
import com.velocache.middleware.service.WarmingEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.velocache.middleware.service.CacheKeyBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.velocache.middleware.service.OriginService;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheWarmingScheduler {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;
    @Autowired
    private WarmingEventLog warmingEventLog;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingScheduler.class);
    private static final int WARM_LEAD_SECONDS = 60;
    private static final int STALE_MINUTES = 15;
    private static final String CACHEABLE_ENDPOINT = "/api/data";
    private final Map<String, LocalDateTime> warmedForecasts = new ConcurrentHashMap<>();
    @Value("${velocache.warming.enabled:true}")
    private volatile boolean warmingEnabled;

    public boolean isWarmingEnabled() {
        return warmingEnabled;
    }

    public void setWarmingEnabled(boolean enabled) {
        warmingEnabled = enabled;
        warmedForecasts.clear();
    }

    @Scheduled(fixedDelay = 30000)
    public void warmCache() {
        if (!warmingEnabled) {
            return;
        }
        LocalDateTime cleanupBefore = LocalDateTime.now().minusMinutes(2);
        warmedForecasts.entrySet().removeIf(entry -> entry.getValue().isBefore(cleanupBefore));
        logger.info("checking forecast...");

        File folder = new File("forecaster");
        File[] files = folder.listFiles((dir, name ) -> name.endsWith("-forecast.json"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String warmMapKey = null;
            try {
                ForecastData forecast = objectMapper.readValue(file, ForecastData.class);
                logger.info("Read forecast: tenant={} endpoint={} spike={} confidence={}",
                        forecast.getTenantId(), forecast.getEndpoint(),
                        forecast.getPredictedSpikeAt(), forecast.getConfidence());
                if (!"high".equals(forecast.getConfidence())) {
                    continue;
                }
                if (!CACHEABLE_ENDPOINT.equals(forecast.getEndpoint())) {
                    continue;
                }
                LocalDateTime generatedAt = LocalDateTime.parse(forecast.getGeneratedAt());
                if (generatedAt.isBefore(LocalDateTime.now().minusMinutes(STALE_MINUTES))) {
                    logger.warn("Stale forecast {} (generated {}), skipping", file.getName(), generatedAt);
                    continue;
                }
                if (forecast.getPredictedSpikeAt() == null) {
                    continue;
                }
                LocalDateTime spikeAt = LocalDateTime.parse(forecast.getPredictedSpikeAt());
                LocalDateTime now = LocalDateTime.now();
                if (spikeAt.isAfter(now) && !spikeAt.isAfter(now.plusSeconds(WARM_LEAD_SECONDS))) {
                    String mapKey = forecast.getTenantId() + "|" + forecast.getEndpoint() + "|" + forecast.getPredictedSpikeAt();
                    if (warmedForecasts.putIfAbsent(mapKey, spikeAt) != null) {
                        continue;
                    }
                    warmMapKey = mapKey;
                    logger.info("SPIKE SOON: tenant={} endpoint={} at {}",
                            forecast.getTenantId(), forecast.getEndpoint(), spikeAt);
                    warm(forecast.getTenantId(), forecast.getEndpoint());
                }
            } catch (Exception e) {
                if (warmMapKey != null) {
                    warmedForecasts.remove(warmMapKey);
                }
                logger.warn("Could not read {}: {}", file.getName(), e.getMessage());
            }
        }
    }
    private void warm(String tenantId, String endpoint) {
        String key = cacheKeyBuilder.build(tenantId, endpoint);
        String value = originService.fetch(tenantId, endpoint);
        stringRedisTemplate.opsForValue().set(key, value, CacheKeyBuilder.SHELF_LIFE);
        logger.info("WARMED key={}", key);
        warmingEventLog.record(tenantId, endpoint, "predicted");
    }
    @Autowired
    private OriginService originService;
}
