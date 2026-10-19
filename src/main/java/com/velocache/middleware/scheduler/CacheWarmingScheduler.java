package com.velocache.middleware.scheduler;

import com.velocache.middleware.dto.ForecastData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.LocalDateTime;

@Component
public class CacheWarmingScheduler {
    @Autowired
    private ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingScheduler.class);
    private static final int HORIZON_MINUTES = 10;
    @Scheduled(fixedDelay = 30000)
    public void warmCache() {
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
                if (forecast.getPredictedSpikeAt() == null) {
                    continue;
                }
                LocalDateTime spikeAt = LocalDateTime.parse(forecast.getPredictedSpikeAt());
                LocalDateTime now = LocalDateTime.now();
                if (spikeAt.isAfter(now) && spikeAt.isBefore(now.plusMinutes(HORIZON_MINUTES))) {
                    logger.info("SPIKE SOON: tenant={} endpoint={} at {}",
                            forecast.getTenantId(), forecast.getEndpoint(), spikeAt);
                }
            } catch (Exception e) {
                logger.warn("Could not read {}: {}", file.getName(), e.getMessage());
            }
        }
    }
}
