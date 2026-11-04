package com.velocache.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ForecastOverlayDto {
    private List<TrafficPointDto> series;
    private ForecastStatusDto forecast;
}
