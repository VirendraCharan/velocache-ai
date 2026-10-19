package com.velocache.middleware.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheWarmingScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingScheduler.class);

    @Scheduled(fixedDelay = 30000)
    public void warmCache() {
        logger.info("checking forecast...");
    }
}
