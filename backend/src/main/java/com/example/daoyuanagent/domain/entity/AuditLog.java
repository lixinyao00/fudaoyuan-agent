package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String action;
    private String operator;
    private String targetType;
    private String targetId;
    private String details;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
