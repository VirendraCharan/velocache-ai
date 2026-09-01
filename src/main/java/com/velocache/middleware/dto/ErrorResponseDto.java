package com.velocache.middleware.dto;

public class ErrorResponseDto {
    private String error;
    private long retryAfterSeconds;

    public ErrorResponseDto(String error, long retryAfterSeconds) {
        this.error = error;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public void setRetryAfterSeconds(long retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
}
