package com.example.daoyuanagent.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.daoyuanagent.domain.enums.Category;
import com.example.daoyuanagent.domain.enums.ReviewStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chunkId;
    private Long documentId;
    private Integer pageStart;
    private Integer pageEnd;
    private String chapterTitle;
    private String regulationName;
    private String articleRef;
    private String categories;
    private String content;
    private String sourceHash;
    private String embeddingJson;
    private ReviewStatus reviewStatus;
    private Boolean canonicalFlag;
    private LocalDateTime effectiveDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
