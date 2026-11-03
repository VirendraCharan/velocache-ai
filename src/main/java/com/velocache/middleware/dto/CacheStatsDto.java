package com.velocache.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheStatsDto {
    private String tenantId;
    private long hits;
    private long misses;
    private double hitRate;
}