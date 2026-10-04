package com.velocache.middleware.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

@Service
public class TelemetryExportService {

    @Autowired
    private TenantTrafficService trafficService;

    public void exportToCsv(String tenantId, String endpoint, String filePath) {
        Map<Integer, Integer> counts = trafficService.getCountsByMinute(tenantId, endpoint);

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("minute,request_count\n");
            for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            System.err.println("Failed to write telemetry export: " + e.getMessage());
        }
    }
}
