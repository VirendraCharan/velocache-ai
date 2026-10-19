package com.velocache.middleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ForecastData {
    @JsonProperty("tenant_id")
    private String tenantId;
    private String endpoint;
    @JsonProperty("predicted_spike_at")
    private String predictedSpikeAt;
    private String confidence;
    @JsonProperty("current_baseline")
    private String currentBaseline;
    @JsonProperty("generated_at")
    private String generatedAt;
    private String reason;
}
