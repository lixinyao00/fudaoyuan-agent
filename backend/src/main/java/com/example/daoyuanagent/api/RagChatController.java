package com.example.daoyuanagent.api;

import com.example.daoyuanagent.api.dto.ChatRequest;
import com.example.daoyuanagent.api.dto.ChatResponse;
import com.example.daoyuanagent.service.RagChatService;
import com.example.daoyuanagent.service.VectorSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/rag")
public class RagChatController {
    private static final Logger log = LoggerFactory.getLogger(RagChatController.class);

    private final RagChatService ragChatService;
    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    @Value("${rag.sse.timeout:30000}")
    private long sseTimeout;

    @Value("${rag.sse.async.thread.pool.size:10}")
    private int threadPoolSize;

    private ExecutorService executorService;

    public RagChatController(RagChatService ragChatService,
                             VectorSearchService vectorSearchService,
                             ObjectMapper objectMapper) {
        this.ragChatService = ragChatService;
        this.vectorSearchService = vectorSearchService;
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize > 0 ? threadPoolSize : 10);
    }

    /**
     * RAG问答（同步）
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> ragChat(@Valid @RequestBody ChatRequest request,
                                                HttpServletRequest httpRequest) {
        String requestId = UUID.randomUUID().toString();
        httpRequest.setAttribute("requestId", requestId);

        ChatResponse response = ragChatService.ragChat(
                request.getConversationId(),
                request.getMessage(),
                request.getUserId(),
                request.getChannel());

        return ResponseEntity.ok(response);
    }

    /**
     * RAG问答（SSE流式输出）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String question,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String channel) {

        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 异步处理
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 发送开始事件
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("{\"status\":\"processing\"}"));

                // 2. 检索文档
                List<VectorSearchService.VectorSearchResult> searchResults =
                        vectorSearchService.search(question, 5);

                // 3. 发送检索结果事件
                String retrievalData = objectMapper.writeValueAsString(
                        searchResults.stream()
                                .map(r -> new RetrievalEvent(
                                        r.content(),
                                        r.pageNumber(),
                                        r.source(),
                                        r.score()
                                ))
                                .toList()
                );
                emitter.send(SseEmitter.event()
                        .name("retrieval")
                        .data(retrievalData));

                // 4. 执行RAG问答
                ChatResponse response = ragChatService.ragChat(
                        conversationId, question, userId, channel);

                // 5. 流式发送答案
                String answer = response.getAnswer();
                for (int i = 0; i < answer.length(); i++) {
                    char c = answer.charAt(i);
                    emitter.send(SseEmitter.event()
                            .name("answer")
                            .data(String.valueOf(c)));
                    Thread.sleep(30); // 模拟流式效果
                }

                // 6. 发送引用事件
                String citationData = objectMapper.writeValueAsString(response.getCitations());
                emitter.send(SseEmitter.event()
                        .name("citations")
                        .data(citationData));

                // 7. 发送完成事件
                String completeData = objectMapper.writeValueAsString(new CompleteEvent(
                        response.getConversationId(),
                        response.getRequestId(),
                        response.getCategory(),
                        response.getRiskLevel().name(),
                        response.getConfidence(),
                        Boolean.TRUE.equals(response.getNeedsHumanReview()),
                        Boolean.TRUE.equals(response.getFallback())
                ));
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(completeData));

                emitter.complete();

            } catch (IOException e) {
                log.error("SSE发送失败", e);
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("SSE流式处理被中断", e);
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("RAG流式处理失败", e);
                emitter.completeWithError(e);
            }
        }, executorService);

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            log.warn("SSE超时");
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            log.info("SSE完成");
        });

        return emitter;
    }

    /**
     * 向量检索（同步）
     */
    @PostMapping("/search")
    public ResponseEntity<List<VectorSearchResultDto>> search(@Valid @RequestBody VectorSearchRequest request) {
        List<VectorSearchService.VectorSearchResult> results = vectorSearchService.search(
                request.query(), request.topK());

        List<VectorSearchResultDto> items = results.stream()
                .map(r -> new VectorSearchResultDto(
                        r.content(),
                        r.pageNumber(),
                        r.source(),
                        r.score()
                ))
                .toList();

        return ResponseEntity.ok(items);
    }

    // DTO类
    public record VectorSearchRequest(
            String query,
            int topK
    ) {}

    public record VectorSearchResultDto(
            String content,
            Integer pageNumber,
            String source,
            float score
    ) {}

    public record RetrievalEvent(
            String content,
            Integer pageNumber,
            String source,
            float score
    ) {}

    public record CompleteEvent(
            String conversationId,
            String requestId,
            String category,
            String riskLevel,
            double confidence,
            boolean needsHumanReview,
            boolean fallback
    ) {}
}