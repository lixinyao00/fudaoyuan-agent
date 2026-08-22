package com.example.daoyuanagent.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalItem {
    private String chunkId;
    private String content;
    private String chapterTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private String articleRef;
    private Double score;
    private String reviewStatus;
}
