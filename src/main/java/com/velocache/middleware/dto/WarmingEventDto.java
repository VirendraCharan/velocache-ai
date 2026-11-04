package com.velocache.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WarmingEventDto {
    private String time;
    private String minute;
    private String tenantId;
    private String endpoint;
    private String type;
}
