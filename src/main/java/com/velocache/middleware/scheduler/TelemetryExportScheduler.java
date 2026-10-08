package com.velocache.middleware.scheduler;

import com.velocache.middleware.service.TelemetryExportService;
import com.velocache.middleware.service.TenantTrafficService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TelemetryExportScheduler {
    @Autowired
    private TenantTrafficService trafficService;
    @Autowired
    private TelemetryExportService exportService;
    private static final Logger logger = LoggerFactory.getLogger(TelemetryExportScheduler.class);

    @Scheduled(fixedDelay = 30000)
    public void checkIn() {
        Set<String> keys = trafficService.getAllTrafficKeys();

        for (String key : keys) {
            String[] parts = key.split(":", 4);
            String tenantId = parts[2];
            String endpoint = parts[3];

            String safeEndpoint = endpoint.replace("/", "~");
            String fileName = tenantId + "__" + safeEndpoint + "-export.csv";
            exportService.exportToCsv(tenantId, endpoint, "forecaster/" + fileName);
        }

        logger.info("Exported telemetry for {} tenant/endpoint pairs", keys.size());
    }
}
