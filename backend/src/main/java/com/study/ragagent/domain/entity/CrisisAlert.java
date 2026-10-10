package com.study.ragagent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危机预警实体
 */
@Data
@TableName("crisis_alert")
public class CrisisAlert {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chatLogId;

    private Long userId;

    private String question;

    private String matchedKeywords;

    private String level;

    private Boolean handled;

    private Long handledBy;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}