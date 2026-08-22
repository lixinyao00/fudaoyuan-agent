package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.daoyuanagent.domain.enums.FeedbackType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private FeedbackType feedbackType;
    private String reason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
