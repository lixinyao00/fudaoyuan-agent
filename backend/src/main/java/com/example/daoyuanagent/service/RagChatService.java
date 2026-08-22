package com.example.daoyuanagent.service;

import com.example.daoyuanagent.api.dto.ChatResponse;
import com.example.daoyuanagent.api.dto.CitationDto;
import com.example.daoyuanagent.domain.entity.*;
import com.example.daoyuanagent.domain.enums.Category;
import com.example.daoyuanagent.domain.enums.RiskLevel;
import com.example.daoyuanagent.provider.LlmGateway;
import com.example.daoyuanagent.provider.LlmResult;
import com.example.daoyuanagent.repository.*;
import com.example.daoyuanagent.util.SanitizeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagChatService {
    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private final VectorSearchService vectorSearchService;
    private final RetrievalService retrievalService;
    private final QuestionClassifierService classifierService;
    private final RiskDetectionService riskService;
    private final PromptService promptService;
    private final LlmGateway llmGateway;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatCitationMapper citationMapper;
    private final RiskEventMapper riskEventMapper;
    private final DocumentMapper documentMapper;

    @Value("${rag.chat.use.vector.search:true}")
    private boolean useVectorSearch;

    @Value("${rag.chat.use.hybrid.search:true}")
    private boolean useHybridSearch;

    public RagChatService(VectorSearchService vectorSearchService,
                          RetrievalService retrievalService,
                          QuestionClassifierService classifierService,
                          RiskDetectionService riskService,
                          PromptService promptService,
                          LlmGateway llmGateway,
                          ConversationMapper conversationMapper,
                          ChatMessageMapper messageMapper,
                          ChatCitationMapper citationMapper,
                          RiskEventMapper riskEventMapper,
                          DocumentMapper documentMapper) {
        this.vectorSearchService = vectorSearchService;
        this.retrievalService = retrievalService;
        this.classifierService = classifierService;
        this.riskService = riskService;
        this.promptService = promptService;
        this.llmGateway = llmGateway;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;
        this.riskEventMapper = riskEventMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * RAG问答（支持向量检索和混合检索）
     */
    public ChatResponse ragChat(String conversationId, String message, String userId, String channel) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // 1. 输入清理
        String sanitized = SanitizeUtil.sanitizeInput(message);
        if (sanitized.isBlank()) {
            return errorResponse("消息不能为空", requestId, conversationId);
        }

        // 2. 风险检测
        RiskDetectionService.RiskAssessment risk = riskService.assess(sanitized);
        log.info("风险评估: level={}, type={}, keyword={}", risk.level(), risk.riskType(), risk.matchedKeyword());

        // 3. 问题分类
        QuestionClassifierService.ClassificationResult classification = classifierService.classify(sanitized);
        log.info("问题分类: primary={}, related={}", classification.primary(), classification.related());

        // 4. 处理会话
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            Conversation conv = new Conversation();
            conv.setConversationId(conversationId);
            conv.setUserId(userId);
            conv.setChannel(channel);
            conv.setLastMessageAt(java.time.LocalDateTime.now());
            conversationMapper.insert(conv);
        }

        // 5. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setMessageId(UUID.randomUUID().toString());
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(SanitizeUtil.maskSensitive(sanitized));
        userMsg.setRiskLevel(risk.level());
        messageMapper.insert(userMsg);

        // 6. 检索相关文档（支持向量检索和混合检索）
        List<SearchResult> searchResults = retrieveDocuments(sanitized, classification.primary().name());

        // 7. 构建上下文
        String context = buildContext(searchResults);

        // 8. 生成回答
        String systemPrompt;
        if (risk.level().ordinal() >= RiskLevel.HIGH.ordinal()) {
            systemPrompt = promptService.buildCrisisSystemPrompt();
        } else {
            systemPrompt = buildRagSystemPrompt(context);
        }

        LlmResult llmResult = llmGateway.chat(systemPrompt, sanitized);
        String answer = llmResult.getText();

        // 9. 构建引用
        List<CitationDto> citations = buildCitations(searchResults);

        boolean needsHumanReview = risk.level().ordinal() >= RiskLevel.HIGH.ordinal()
                || risk.level() == RiskLevel.CRITICAL;

        // 10. 保存助手消息
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setMessageId(UUID.randomUUID().toString());
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setCategory(classification.primary().name());
        assistantMsg.setRelatedCategories(classification.related().stream()
                .map(Category::name).collect(Collectors.joining(",")));
        assistantMsg.setRiskLevel(risk.level());
        assistantMsg.setNeedsHumanReview(needsHumanReview);
        assistantMsg.setConfidence(searchResults.isEmpty() ? 0.0 : searchResults.get(0).score());
        assistantMsg.setRequestId(requestId);
        assistantMsg.setPromptVersion(PromptService.PROMPT_VERSION);
        assistantMsg.setFallback(searchResults.isEmpty());
        messageMapper.insert(assistantMsg);

        // 11. 保存引用
        for (CitationDto cit : citations) {
            ChatCitation citation = new ChatCitation();
            citation.setMessageId(assistantMsg.getMessageId());
            citation.setChunkId(cit.getChunkId());
            citation.setDocumentName(cit.getDocumentName());
            citation.setChapterTitle(cit.getChapterTitle());
            citation.setPageStart(cit.getPageStart());
            citation.setPageEnd(cit.getPageEnd());
            citation.setArticleRef(cit.getArticleRef());
            citation.setContentSnippet(cit.getContentSnippet());
            citation.setScore(cit.getScore());
            citationMapper.insert(citation);
        }

        // 12. 保存风险事件（如果需要）
        if (risk.level().ordinal() >= RiskLevel.MEDIUM.ordinal()) {
            RiskEvent riskEvent = new RiskEvent();
            riskEvent.setMessageId(assistantMsg.getMessageId());
            riskEvent.setConversationId(conversationId);
            riskEvent.setRiskLevel(risk.level());
            riskEvent.setRiskType(risk.riskType());
            String safeDesc = SanitizeUtil.maskSensitive(risk.matchedKeyword());
            riskEvent.setDescription("检测到关键词: " + safeDesc);
            riskEvent.setStatus("OPEN");
            riskEvent.setResolved(false);
            riskEventMapper.insert(riskEvent);
        }

        // 13. 更新会话
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));
        if (conv != null) {
            conv.setLastMessageAt(java.time.LocalDateTime.now());
            conversationMapper.updateById(conv);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("RAG问答完成: requestId={}, duration={}ms, results={}", requestId, duration, searchResults.size());

        return ChatResponse.builder()
                .answer(answer)
                .category(classification.primary().name())
                .relatedCategories(classification.related().stream().map(Category::name).toList())
                .riskLevel(risk.level())
                .citations(citations)
                .needsHumanReview(needsHumanReview)
                .confidence(searchResults.isEmpty() ? 0.0 : searchResults.get(0).score())
                .conversationId(conversationId)
                .requestId(requestId)
                .promptVersion(PromptService.PROMPT_VERSION)
                .fallback(searchResults.isEmpty())
                .build();
    }

    /**
     * 检索文档（支持向量检索和混合检索）
     */
    private List<SearchResult> retrieveDocuments(String query, String category) {
        if (useVectorSearch) {
            // 使用向量检索
            List<VectorSearchService.VectorSearchResult> vectorResults = vectorSearchService.search(query, 10);

            if (useHybridSearch) {
                // 使用混合检索：向量 + 关键词
                List<RetrievalService.RetrievalResult> keywordRetrievalResults = retrievalService.retrieve(query, category, 10);

                // 转换为统一格式
                List<VectorSearchService.KeywordSearchResult> keywordResults = keywordRetrievalResults.stream()
                        .map(r -> new VectorSearchService.KeywordSearchResult(
                                r.chunk().getContent(),
                                r.chunk().getPageStart(),
                                r.chunk().getRegulationName(),
                                r.score()
                        ))
                        .collect(Collectors.toList());

                // RRF融合
                List<VectorSearchService.HybridSearchResult> hybridResults = vectorSearchService.rrfFusion(
                        vectorResults, keywordResults, 60);

                // 转换为SearchResult
                return hybridResults.stream()
                        .map(r -> new SearchResult(
                                r.content(),
                                r.pageNumber(),
                                r.source(),
                                r.vectorScore() + r.keywordScore(),
                                r.sourceType()
                        ))
                        .collect(Collectors.toList());
            } else {
                // 仅使用向量检索
                return vectorResults.stream()
                        .map(r -> new SearchResult(
                                r.content(),
                                r.pageNumber(),
                                r.source(),
                                r.score(),
                                "vector"
                        ))
                        .collect(Collectors.toList());
            }
        } else {
            // 使用传统关键词检索
            List<RetrievalService.RetrievalResult> keywordResults = retrievalService.retrieve(query, category, 10);
            return keywordResults.stream()
                    .map(r -> new SearchResult(
                            r.chunk().getContent(),
                            r.chunk().getPageStart(),
                            r.chunk().getRegulationName(),
                            r.score(),
                            "keyword"
                    ))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "没有找到相关文档内容。";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是相关的学生手册内容：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append(String.format("【文档%d】页码: %d, 来源: %s, 相关度: %.2f\n",
                    i + 1, result.pageNumber(), result.source(), result.score()));
            context.append(result.content());
            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * 构建RAG系统提示词
     */
    private String buildRagSystemPrompt(String context) {
        return "你是辅导员专属AI助手，基于《学生手册》回答学生问题。\n\n" +
                "回答要求：\n" +
                "1. 准确引用手册内容，注明页码\n" +
                "2. 如果涉及风险事件（心理危机、违纪等），提醒联系辅导员\n" +
                "3. 回答要简洁明了，适合学生理解\n" +
                "4. 如果不确定，明确说明\"根据手册内容，我无法确定...\"\n\n" +
                "相关手册内容：\n" + context;
    }

    /**
     * 构建引用
     */
    private List<CitationDto> buildCitations(List<SearchResult> results) {
        List<CitationDto> citations = new ArrayList<>();
        for (SearchResult r : results) {
            CitationDto dto = CitationDto.builder()
                    .chunkId("0")
                    .documentName(r.source())
                    .chapterTitle("学生手册")
                    .pageStart(r.pageNumber())
                    .pageEnd(r.pageNumber())
                    .articleRef("")
                    .contentSnippet(r.content().length() > 150
                            ? r.content().substring(0, 150) + "..."
                            : r.content())
                    .score(r.score())
                    .build();
            citations.add(dto);
        }
        return citations;
    }

    private ChatResponse errorResponse(String message, String requestId, String conversationId) {
        return ChatResponse.builder()
                .answer(message)
                .category("UNKNOWN")
                .riskLevel(RiskLevel.NONE)
                .citations(List.of())
                .needsHumanReview(false)
                .confidence(0.0)
                .conversationId(conversationId)
                .requestId(requestId)
                .promptVersion(PromptService.PROMPT_VERSION)
                .fallback(true)
                .build();
    }

    // 搜索结果类
    public record SearchResult(
            String content,
            Integer pageNumber,
            String source,
            double score,
            String sourceType
    ) {}
}