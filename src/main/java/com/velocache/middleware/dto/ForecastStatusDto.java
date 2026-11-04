package com.velocache.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ForecastStatusDto {
    private String status;
    private String confidence;
    private String predictedMinute;
    private Double baseline;
}
