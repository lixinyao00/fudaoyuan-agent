package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_citation")
public class ChatCitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String chunkId;
    private String documentName;
    private String chapterTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private String articleRef;
    private String contentSnippet;
    private Double score;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
