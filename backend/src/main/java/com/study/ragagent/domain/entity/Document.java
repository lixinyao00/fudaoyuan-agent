package com.study.ragagent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.study.ragagent.domain.enums.DocumentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private DocumentStatus status;
}
