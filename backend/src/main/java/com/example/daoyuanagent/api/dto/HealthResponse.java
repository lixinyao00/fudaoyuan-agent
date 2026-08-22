package com.example.daoyuanagent.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthResponse {
    private String status;
    private String database;
    private String knowledgeBase;
    private String aiProvider;
}
