package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.daoyuanagent.domain.enums.RiskLevel;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String conversationId;
    private String role;
    private String content;
    private String category;
    private String relatedCategories;
    private RiskLevel riskLevel;
    private Boolean needsHumanReview;
    private Double confidence;
    private String requestId;
    private String promptVersion;
    private Boolean fallback;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
