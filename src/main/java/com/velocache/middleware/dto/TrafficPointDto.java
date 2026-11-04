package com.velocache.middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrafficPointDto {
    private String minute;
    private Integer actual;;
}
