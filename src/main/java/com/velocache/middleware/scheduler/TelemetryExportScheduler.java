package com.velocache.middleware.scheduler;

import com.velocache.middleware.service.TelemetryExportService;
import com.velocache.middleware.service.TenantTrafficService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelemetryExportScheduler {
    @Autowired
    private TenantTrafficService trafficService;
    @Autowired
    private TelemetryExportService exportService;
    private static final Logger logger = LoggerFactory.getLogger(TelemetryExportScheduler.class);

    @Scheduled(fixedDelay = 30000)
    public void checkIn() {
      exportService.exportToCsv("tenant-a", "/api/track", "tenant-a-export.csv");
      logger.info("Export written for tenant-a");
    }
}
