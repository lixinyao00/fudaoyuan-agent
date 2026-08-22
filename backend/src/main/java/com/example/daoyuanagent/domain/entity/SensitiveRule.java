package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sensitive_rule")
public class SensitiveRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private String riskType;
    private String riskLevel;
    private Boolean enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
