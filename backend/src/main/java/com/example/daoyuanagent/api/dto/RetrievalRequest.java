package com.example.daoyuanagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RetrievalRequest {
    @NotBlank(message = "查询不能为空")
    private String query;
    private String category;
    private Integer topK = 8;
    private String documentVersion;
}
