package com.example.daoyuanagent.api.dto;

import com.example.daoyuanagent.domain.enums.RiskLevel;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String answer;
    private String category;
    private List<String> relatedCategories;
    private RiskLevel riskLevel;
    private List<CitationDto> citations;
    private Boolean needsHumanReview;
    private Double confidence;
    private String conversationId;
    private String requestId;
    private String promptVersion;
    private Boolean fallback;
}
