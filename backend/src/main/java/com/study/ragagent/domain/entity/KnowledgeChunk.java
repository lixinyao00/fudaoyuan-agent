package com.study.ragagent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;     // 关联文档ID
    private String content;      // 文本内容
    private Integer chunkIndex;  // 第几块
    private Integer tokenCount;  // token数量
    private String vectorId;     // Milvus向量ID
    private LocalDateTime createdAt;
}
