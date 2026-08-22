package com.example.daoyuanagent.api.dto;

import com.example.daoyuanagent.domain.enums.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotBlank
    private String messageId;
    @NotNull
    private FeedbackType feedbackType;
    private String reason;
}
