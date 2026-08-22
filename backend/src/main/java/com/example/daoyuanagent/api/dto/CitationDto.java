package com.example.daoyuanagent.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationDto {
    private String chunkId;
    private String documentName;
    private String chapterTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private String articleRef;
    private String contentSnippet;
    private Double score;
}
