package com.example.daoyuanagent.api;

import com.example.daoyuanagent.api.dto.*;
import com.example.daoyuanagent.service.ChatService;
import com.example.daoyuanagent.service.FeedbackService;
import com.example.daoyuanagent.service.RetrievalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;
    private final RetrievalService retrievalService;
    private final FeedbackService feedbackService;

    public ChatController(ChatService chatService, RetrievalService retrievalService,
                          FeedbackService feedbackService) {
        this.chatService = chatService;
        this.retrievalService = retrievalService;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                             HttpServletRequest httpRequest) {
        String requestId = UUID.randomUUID().toString();
        httpRequest.setAttribute("requestId", requestId);
        ChatResponse response = chatService.chat(
                request.getConversationId(),
                request.getMessage(),
                request.getUserId(),
                request.getChannel());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/retrieval/search")
    public ResponseEntity<List<RetrievalItem>> search(@Valid @RequestBody RetrievalRequest request) {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve(
                request.getQuery(), request.getCategory(), request.getTopK());
        List<RetrievalItem> items = results.stream()
                .map(r -> RetrievalItem.builder()
                        .chunkId(r.chunk().getChunkId())
                        .content(r.chunk().getContent())
                        .chapterTitle(r.chunk().getChapterTitle())
                        .pageStart(r.chunk().getPageStart())
                        .pageEnd(r.chunk().getPageEnd())
                        .articleRef(r.chunk().getArticleRef())
                        .score(r.score())
                        .reviewStatus(r.chunk().getReviewStatus().name())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.submitFeedback(request.getMessageId(), request.getFeedbackType(), request.getReason());
        return ResponseEntity.ok().build();
    }

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${ai.provider:mock}")
    private String aiProvider;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        String dbType = datasourceUrl.contains("mysql") ? "MySQL" : "H2-embedded";
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .database(dbType)
                .knowledgeBase("active")
                .aiProvider(aiProvider)
                .build());
    }
}
