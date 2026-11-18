package com.velocache.middleware.scheduler;

import com.velocache.middleware.service.DemoSimulationService;
import com.velocache.middleware.service.TenantTrafficService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// This sends simulated background traffic for one demo tenant so the real forecaster and cache warmer have a repeating rhythm to act on; it is off unless velocache.demo-rhythm.enabled is true.
@Component
public class DemoRhythmScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DemoRhythmScheduler.class);

    @Autowired
    private DemoSimulationService demoSimulationService;

    @Autowired
    private TenantTrafficService trafficService;

    @Value("${velocache.demo-rhythm.enabled:false}")
    private boolean enabled;

    @Value("${velocache.demo-rhythm.tenant:demo-rhythm}")
    private String tenant;

    @Scheduled(cron = "0 */5 * * * *")
    public void burst() {
        if (!enabled) {
            return;
        }
        DemoSimulationService.Result result = demoSimulationService.start(tenant);
        if (result != DemoSimulationService.Result.STARTED) {
            logger.warn("Scheduled demo burst not started: {}", result.name());
        }
    }

    @Scheduled(cron = "30 * * * * *")
    public void trickle() {
        if (!enabled) {
            return;
        }
        trafficService.recordRequest(tenant, DemoSimulationService.DEMO_ENDPOINT);
    }
}
