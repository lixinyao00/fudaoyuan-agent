package com.study.ragagent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答日志实体
 */
@Data
@TableName("chat_log")
public class ChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String question;

    private String answer;

    /** 检索到的引用，JSON 字符串 */
    private String referencesJson;

    private String retrievalMode;

    private Integer latencyMs;

    private String feedback;

    private String feedbackNote;

    private LocalDateTime createdAt;
}