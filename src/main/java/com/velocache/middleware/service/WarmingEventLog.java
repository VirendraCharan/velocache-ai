package com.velocache.middleware.service;

import com.velocache.middleware.dto.WarmingEventDto;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
public class WarmingEventLog {
    private static final int MAX_EVENTS = 100;
    private final LinkedList<WarmingEventDto> events = new LinkedList<>();

    public synchronized void record(String tenantId, String endpoint, String type) {
        LocalDateTime now = LocalDateTime.now();
        events.addFirst(new WarmingEventDto(
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                tenantId, endpoint, type));
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    public synchronized List<WarmingEventDto> recent(String tenantId, int limit) {
        List<WarmingEventDto> result = new ArrayList<>();
        for (WarmingEventDto e : events) {
            if (e.getTenantId().equals(tenantId)) {
                result.add(e);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }
}
