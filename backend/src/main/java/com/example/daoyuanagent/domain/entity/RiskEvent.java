package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.daoyuanagent.domain.enums.RiskLevel;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("risk_event")
public class RiskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String conversationId;
    private RiskLevel riskLevel;
    private String riskType;
    private String description;
    private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    private String handler;
    private String handlerNotes;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
