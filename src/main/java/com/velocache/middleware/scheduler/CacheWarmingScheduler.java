package com.velocache.middleware.scheduler;

import com.velocache.middleware.dto.ForecastData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.velocache.middleware.service.CacheKeyBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import com.velocache.middleware.service.OriginService;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.time.LocalDateTime;

@Component
public class CacheWarmingScheduler {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CacheKeyBuilder cacheKeyBuilder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingScheduler.class);
    private static final int HORIZON_MINUTES = 10;
    private static final int STALE_MINUTES = 15;
    @Value("${velocache.warming.enabled:true}")
    private boolean warmingEnabled;
    @Scheduled(fixedDelay = 30000)
    public void warmCache() {
        if (!warmingEnabled) {
            return;
        }
        logger.info("checking forecast...");

        File folder = new File("forecaster");
        File[] files = folder.listFiles((dir, name ) -> name.endsWith("-forecast.json"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                ForecastData forecast = objectMapper.readValue(file, ForecastData.class);
                logger.info("Read forecast: tenant={} endpoint={} spike={} confidence={}",
                        forecast.getTenantId(), forecast.getEndpoint(),
                        forecast.getPredictedSpikeAt(), forecast.getConfidence());
                if (!"high".equals(forecast.getConfidence())) {
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
                if (spikeAt.isAfter(now) && spikeAt.isBefore(now.plusMinutes(HORIZON_MINUTES))) {
                    logger.info("SPIKE SOON: tenant={} endpoint={} at {}",
                            forecast.getTenantId(), forecast.getEndpoint(), spikeAt);
                    warm(forecast.getTenantId(), forecast.getEndpoint());
                }
            } catch (Exception e) {
                logger.warn("Could not read {}: {}", file.getName(), e.getMessage());
            }
        }
    }
    private void warm(String tenantId, String endpoint) {
        String key = cacheKeyBuilder.build(tenantId, endpoint);
        String value = originService.fetch(tenantId, endpoint);
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10));
        logger.info("WARMED key={}", key);
    }
    @Autowired
    private OriginService originService;
}
