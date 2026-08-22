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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

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

    public ChatService(RetrievalService retrievalService, QuestionClassifierService classifierService,
                       RiskDetectionService riskService, PromptService promptService,
                       LlmGateway llmGateway, ConversationMapper conversationMapper,
                       ChatMessageMapper messageMapper, ChatCitationMapper citationMapper,
                       RiskEventMapper riskEventMapper, DocumentMapper documentMapper) {
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

    public ChatResponse chat(String conversationId, String message, String userId, String channel) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // 1. Sanitize input
        String sanitized = SanitizeUtil.sanitizeInput(message);
        if (sanitized.isBlank()) {
            return errorResponse("消息不能为空", requestId, conversationId);
        }

        // 2. Risk detection
        RiskDetectionService.RiskAssessment risk = riskService.assess(sanitized);
        log.info("风险评估: level={}, type={}, keyword={}", risk.level(), risk.riskType(), risk.matchedKeyword());

        // 3. Classify question
        QuestionClassifierService.ClassificationResult classification = classifierService.classify(sanitized);
        log.info("问题分类: primary={}, related={}", classification.primary(), classification.related());

        // 4. Handle conversation
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            Conversation conv = new Conversation();
            conv.setConversationId(conversationId);
            conv.setUserId(userId);
            conv.setChannel(channel);
            conv.setLastMessageAt(java.time.LocalDateTime.now());
            conversationMapper.insert(conv);
        }

        // 5. Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setMessageId(UUID.randomUUID().toString());
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(SanitizeUtil.maskSensitive(sanitized));
        userMsg.setRiskLevel(risk.level());
        messageMapper.insert(userMsg);

        // 6. Retrieve evidence
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve(
                sanitized, classification.primary().name(), 8);

        // 7. Build evidence snippets
        List<String> evidenceSnippets = results.stream()
                .map(r -> {
                    String reg = r.chunk().getRegulationName();
                    return String.format("页码=%d-%d\n制度=%s\n章节=%s\n条款=%s\n内容=%s",
                            r.chunk().getPageStart(), r.chunk().getPageEnd(),
                            reg != null ? reg : "未知",
                            r.chunk().getChapterTitle(),
                            r.chunk().getArticleRef() != null ? r.chunk().getArticleRef() : "无",
                            r.chunk().getContent());
                })
                .collect(Collectors.toList());

        // 8. Generate response
        String systemPrompt;
        if (risk.level().ordinal() >= RiskLevel.HIGH.ordinal()) {
            systemPrompt = promptService.buildCrisisSystemPrompt();
        } else {
            systemPrompt = promptService.buildSystemPrompt(evidenceSnippets);
        }

        LlmResult llmResult = llmGateway.chat(systemPrompt, sanitized);

        // 9. Build citations with real document info
        Map<Long, String> docNameCache = new HashMap<>();
        List<CitationDto> citations = results.stream()
                .map(r -> {
                    String docName = docNameCache.computeIfAbsent(r.chunk().getDocumentId(), id -> {
                        Document d = documentMapper.selectById(id);
                        return d != null ? d.getFileName() : "未知文档";
                    });
                    String regName = r.chunk().getRegulationName();
                    String chapter = r.chunk().getChapterTitle();
                    String displayChapter = regName != null
                            ? regName + (chapter != null && !chapter.equals("待人工确认") ? " - " + chapter : "")
                            : (chapter != null ? chapter : "未知章节");
                    return CitationDto.builder()
                            .chunkId(r.chunk().getChunkId())
                            .documentName(docName)
                            .chapterTitle(displayChapter)
                            .pageStart(r.chunk().getPageStart())
                            .pageEnd(r.chunk().getPageEnd())
                            .articleRef(r.chunk().getArticleRef())
                            .contentSnippet(r.chunk().getContent().length() > 150
                                    ? r.chunk().getContent().substring(0, 150) + "..."
                                    : r.chunk().getContent())
                            .score(r.score())
                            .build();
                })
                .collect(Collectors.toList());

        boolean needsHumanReview = risk.level().ordinal() >= RiskLevel.HIGH.ordinal()
                || risk.level() == RiskLevel.CRITICAL;

        // 10. Save assistant message
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setMessageId(UUID.randomUUID().toString());
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(llmResult.getText());
        assistantMsg.setCategory(classification.primary().name());
        assistantMsg.setRelatedCategories(classification.related().stream()
                .map(Category::name).collect(Collectors.joining(",")));
        assistantMsg.setRiskLevel(risk.level());
        assistantMsg.setNeedsHumanReview(needsHumanReview);
        assistantMsg.setConfidence(results.isEmpty() ? 0.0 : results.get(0).score());
        assistantMsg.setRequestId(requestId);
        assistantMsg.setPromptVersion(PromptService.PROMPT_VERSION);
        assistantMsg.setFallback(results.isEmpty());
        messageMapper.insert(assistantMsg);

        // 11. Save citations
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

        // 12. Save risk event if needed
        if (risk.level().ordinal() >= RiskLevel.MEDIUM.ordinal()) {
            RiskEvent riskEvent = new RiskEvent();
            riskEvent.setMessageId(assistantMsg.getMessageId());
            riskEvent.setConversationId(conversationId);
            riskEvent.setRiskLevel(risk.level());
            riskEvent.setRiskType(risk.riskType());
            // Sanitize description - don't log full crisis text
            String safeDesc = SanitizeUtil.maskSensitive(risk.matchedKeyword());
            riskEvent.setDescription("检测到关键词: " + safeDesc);
            riskEvent.setStatus("OPEN");
            riskEvent.setResolved(false);
            riskEventMapper.insert(riskEvent);
        }

        // 13. Update conversation
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));
        if (conv != null) {
            conv.setLastMessageAt(java.time.LocalDateTime.now());
            conversationMapper.updateById(conv);
        }

        return ChatResponse.builder()
                .answer(llmResult.getText())
                .category(classification.primary().name())
                .relatedCategories(classification.related().stream().map(Category::name).toList())
                .riskLevel(risk.level())
                .citations(citations)
                .needsHumanReview(needsHumanReview)
                .confidence(results.isEmpty() ? 0.0 : results.get(0).score())
                .conversationId(conversationId)
                .requestId(requestId)
                .promptVersion(PromptService.PROMPT_VERSION)
                .fallback(results.isEmpty())
                .build();
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
}
