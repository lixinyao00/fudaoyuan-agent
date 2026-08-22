package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String userId;
    private String channel;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
