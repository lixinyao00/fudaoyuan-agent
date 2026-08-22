package com.example.daoyuanagent.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmResult {
    private String text;
    private String provider;
    private String model;
    private String requestId;
    private long latencyMs;
    private String errorCode;
}
