package com.study.ragagent.service;

import com.study.ragagent.domain.entity.KnowledgeChunk;
import com.study.ragagent.mapper.KnowledgeChunkMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.ragagent.config.AiConfig;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 问答服务，负责检索知识库并调用大模型生成回答
 */
@Service
public class RagService {

    // 配置项存在 system_config 表里，读不到就用下面的默认值
    private static final String CFG_SYSTEM_PROMPT = "system.prompt";
    private static final String CFG_KEYWORD_MAP = "keyword.mappings";
    private static final String CFG_CRISIS_KEYWORDS = "crisis.keywords";

    /** 危机关键词列表，可以在后台配置里改 */
    private static final List<String> DEFAULT_CRISIS_KEYWORDS = List.of(
            "自杀", "跳楼", "自伤", "轻生", "想死", "不想活", "结束生命", "割腕", "上吊", "一了百了", "活不下去");

    private static final String SYSTEM_PROMPT = """
            你是「辅导员问答助手」，专门依据《清华大学学生手册》等校内正式文件，
            帮助学生解答学籍、奖助、请假、宿舍、考试纪律、学位授予等事务性问题。

            【角色定位】
            - 语气：亲切、专业、简洁，像一位认真负责的辅导员
            - 立场：以学校正式规定为准，不编造、不猜测、不补充手册外政策

            【回答规则】
            1. 只根据下方「参考文档」回答；文档未提及的内容必须明确说「学生手册相关条款中未找到明确依据」
            2. 禁止编造条款编号、日期、金额、比例、审批人等具体信息
            3. 若文档有相关信息，按以下结构作答：
               - 先用1-2句直接回答结论
               - 再分点说明条件、流程、注意事项
               - 最后注明依据来源（如「依据：学生奖学金管理规定」或文档片段编号）
            4. 涉及处分、退学、违纪等高风险事项时，提醒学生以院系/教务处最新通知为准，必要时联系辅导员当面确认
            5. 心理危机、人身安全等紧急情况：明确建议立即联系辅导员、校医院或紧急求助电话，不要仅做政策解读
            6. 回答使用中文，条理清晰，避免空洞套话；不要输出与问题无关的长篇复述
            7. 若检索结果明显不相关，直接说明无法从现有文档回答，并建议咨询辅导员或相关部门
            8. 多个相关子问题：逐一回答每个子问题，不要遗漏；对每个子问题分别说明是否有依据
            9. 诱导性或包含错误信息的问题：先纠正错误认知，再依据文档给出正确信息
            10. 模糊代词问题（如"那个规定"）：请学生明确具体指哪项规定
            11. 紧急危机（自杀、自伤倾向）：立即提供危机干预资源，优先保障安全，不做长篇政策解读

            【一致性要求】
            - 相似问题应给出一致的回答，不要前后矛盾
            - 关于处分对奖学金的影响：处分期间取消奖学金资格是常见规定，如有相关条款应明确指出
            - 关于宿舍违规电器：如有明确规定应详细列出，不要说"未找到"
            """;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private ChatLogService chatLogService;

    @Autowired
    private CrisisAlertService crisisAlertService;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 问答接口 - 混合检索：关键词 + 向量，去重合并
     * @param question 问题
     * @param userId 用户ID，null 表示游客（不记录日志）
     */
    public Map<String, Object> chat(String question, Long userId) {
        final long start = System.currentTimeMillis();
        String retrievalMode = detectionRetrievalMode(question);
        List<String> crisisKeywords = detectCrisisKeywords(question);
        final Long logId = (userId != null) ? chatLogService.createLog(userId, question, retrievalMode).getId() : null;
        if (!crisisKeywords.isEmpty() && userId != null) {
            crisisAlertService.createAlert(logId, userId, question, crisisKeywords);
        }

        try {
            List<KnowledgeChunk> chunks = retrieveChunks(question);
            String context = buildContext(chunks);
            String answer = callLLM(question, context);
            List<Map<String, Object>> references = buildReferences(chunks);
            int latency = (int) (System.currentTimeMillis() - start);

            if (logId != null) {
                chatLogService.updateAnswer(logId, answer, references, latency);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("answer", answer);
            result.put("references", references);
            result.put("logId", logId);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("问答处理失败: " + e.getMessage());
        }
    }

    /** 判断检索模式：vector|keyword|hybrid|none */
    private String detectionRetrievalMode(String question) {
        boolean hasEmbedding = embeddingService != null && embeddingService.isConfigured();
        boolean hasMilvus = vectorStoreService != null && vectorStoreService.isReady();
        if (hasEmbedding && hasMilvus) return "hybrid";
        if (hasEmbedding || hasMilvus) return hasEmbedding ? "vector" : "keyword";
        return "none";
    }

    /**
     * 问答接口 - 流式响应 (SSE)
     * @param question 问题
     * @param emitter SSE 发射器
     * @param userId 用户ID，null 表示游客
     */
    public void chatStream(String question, SseEmitter emitter, Long userId) {
        final long start = System.currentTimeMillis();
        String retrievalMode = detectionRetrievalMode(question);
        final Long logId;
        if (userId != null) {
            logId = chatLogService.createLog(userId, question, retrievalMode).getId();
        } else {
            logId = null;
        }

        // 危机检测（先于回答入库，保证不漏报）
        List<String> crisisKeywords = detectCrisisKeywords(question);
        if (!crisisKeywords.isEmpty() && userId != null) {
            crisisAlertService.createAlert(logId, userId, question, crisisKeywords);
        }

        try {
            // 1. 检索文档
            List<KnowledgeChunk> chunks = retrieveChunks(question);
            String context = buildContext(chunks);
            final List<Map<String, Object>> references = buildReferences(chunks);

            // 2. 发送引用信息
            Map<String, Object> refsEvent = new HashMap<>();
            refsEvent.put("type", "references");
            refsEvent.put("data", references);
            emitter.send(SseEmitter.event().name("references").data(objectMapper.writeValueAsString(refsEvent)));

            // 3. 流式调用LLM（完成时在监听器内回填日志）
            callLLMStream(question, context, emitter, logId, references, start);

        } catch (Exception e) {
            try {
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("type", "error");
                errorEvent.put("data", "问答处理失败: " + e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(errorEvent)));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        }
    }

    /**
     * 检索文档 chunks
     */
    private List<KnowledgeChunk> retrieveChunks(String question) {
        // 1. 关键词检索
        List<String> keywords = extractKeywords(question);
        List<KnowledgeChunk> keywordChunks = new ArrayList<>();
        if (!keywords.isEmpty()) {
            keywordChunks = chunkMapper.searchByAllKeywords(keywords, 10);
            if (keywordChunks.size() < 3) {
                List<KnowledgeChunk> orChunks = chunkMapper.searchByAnyKeyword(keywords, 10);
                for (KnowledgeChunk c : orChunks) {
                    if (keywordChunks.stream().noneMatch(k -> k.getId().equals(c.getId()))) {
                        keywordChunks.add(c);
                    }
                }
            }
        }

        // 2. 向量检索（Embedding 与 Milvus 均就绪时才尝试，不配置 key 则静默跳过）
        List<KnowledgeChunk> vectorChunks = new ArrayList<>();
        if (embeddingService.isConfigured() && vectorStoreService.isReady()) {
            try {
                float[] questionVector = embeddingService.embed(question);
                List<Long> chunkIds = vectorStoreService.search(questionVector, 8);
                for (Long chunkId : chunkIds) {
                    KnowledgeChunk chunk = chunkMapper.selectById(chunkId);
                    if (chunk != null && chunk.getContent() != null && !chunk.getContent().isBlank()) {
                        vectorChunks.add(chunk);
                    }
                }
            } catch (Exception e) {
                System.err.println("向量检索失败，仅使用关键词结果: " + e.getMessage());
            }
        }

        // 3. 合并去重
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();
        for (KnowledgeChunk c : keywordChunks) {
            merged.put(c.getId(), c);
        }
        for (KnowledgeChunk c : vectorChunks) {
            merged.putIfAbsent(c.getId(), c);
        }
        List<KnowledgeChunk> chunks = new ArrayList<>(merged.values());
        if (chunks.size() > 8) {
            chunks = chunks.subList(0, 8);
        }
        return chunks;
    }

    private List<Map<String, Object>> buildReferences(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .map(c -> {
                    Map<String, Object> ref = new HashMap<>();
                    ref.put("content", c.getContent());
                    ref.put("chunkIndex", c.getChunkIndex() != null ? c.getChunkIndex() : 0);
                    return ref;
                })
                .toList();
    }

    /**
     * 从问题中提取关键词，用于 SQL LIKE 检索
     */
    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        List<String[]> mappings = getKeywordMappings();
        for (String[] pair : mappings) {
            if (question.contains(pair[0])) {
                keywords.add(pair[1]);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add(question.replaceAll("[？?！!。，,\\s]+", "").trim());
        }
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    private String buildContext(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "（未检索到相关文档片段）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            sb.append("【文档片段").append(i + 1).append("】");
            if (chunk.getChunkIndex() != null) {
                sb.append("（索引 ").append(chunk.getChunkIndex()).append("）");
            }
            sb.append("\n").append(chunk.getContent().trim()).append("\n\n");
        }
        return sb.toString();
    }

    private String callLLM(String question, String context) throws Exception {
        String url = getLlmUrl();
        String apiKey = getLlmApiKey();
        String model = getLlmModel();
        int maxTokens = getLlmMaxTokens();
        double temperature = getLlmTemperature();

        String userPrompt = buildUserPrompt(question, context);
        String jsonBody = buildRequestBody(model, userPrompt, maxTokens, temperature, false);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("LLM API 调用失败: " + response.code() + " " + err);
            }
            String body = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(body);
            return jsonNode.get("choices").get(0).get("message").get("content").asText();
        }
    }

    /**
     * 流式调用 LLM
     * @param question 问题
     * @param context 上下文
     * @param emitter SSE 发射器
     * @param logId 日志ID（完成后回填）
     * @param references 检索到的引用
     * @param startTime 开始时间戳
     */
    private void callLLMStream(String question, String context, SseEmitter emitter,
                               Long logId, List<Map<String, Object>> references, long startTime) throws Exception {
        String url = getLlmUrl();
        String apiKey = getLlmApiKey();
        String model = getLlmModel();
        int maxTokens = getLlmMaxTokens();
        double temperature = getLlmTemperature();

        String userPrompt = buildUserPrompt(question, context);
        String jsonBody = buildRequestBody(model, userPrompt, maxTokens, temperature, true);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        EventSource.Factory factory = EventSources.createFactory(client);
        StringBuilder fullAnswer = new StringBuilder();
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);

        factory.newEventSource(request, new EventSourceListener() {

            private boolean safeSend(String eventName, Object payload) {
                if (finished.get()) return false;
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(objectMapper.writeValueAsString(payload)));
                    return true;
                } catch (Exception e) {
                    // 客户端断开或 emitter 已完成，停止后续发送
                    finished.set(true);
                    return false;
                }
            }

            private void finish() {
                if (finished.compareAndSet(false, true)) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                    // 回填日志（延迟 500ms 给调用方同步线程一点时间发送 [DONE]）
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        if (logId != null) {
                            try {
                                int latency = (int) (System.currentTimeMillis() - startTime);
                                chatLogService.updateAnswer(logId, fullAnswer.toString(), references, latency);
                            } catch (Exception e) {
                                System.err.println("日志回填失败: " + e.getMessage());
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (finished.get()) {
                    eventSource.cancel();
                    return;
                }
                if ("[DONE]".equals(data)) {
                    Map<String, Object> doneEvent = new HashMap<>();
                    doneEvent.put("type", "done");
                    doneEvent.put("data", fullAnswer.toString());
                    safeSend("done", doneEvent);
                    finish();
                    return;
                }

                try {
                    JsonNode jsonNode = objectMapper.readTree(data);
                    JsonNode choices = jsonNode.get("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta != null && delta.has("content")) {
                            JsonNode contentNode = delta.get("content");
                            // 过滤 null 值（JSON null / 字面量 "null"）和空字符串
                            if (contentNode != null && !contentNode.isNull()) {
                                String content = contentNode.asText();
                                if (content != null && !content.isEmpty() && !"null".equals(content)) {
                                    fullAnswer.append(content);
                                    Map<String, Object> chunkEvent = new HashMap<>();
                                    chunkEvent.put("type", "chunk");
                                    chunkEvent.put("data", content);
                                    safeSend("chunk", chunkEvent);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("SSE解析错误: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                if (!finished.get()) {
                    Map<String, Object> errorEvent = new HashMap<>();
                    errorEvent.put("type", "error");
                    errorEvent.put("data", "LLM API 调用失败: " + (t != null ? t.getMessage() : "unknown"));
                    safeSend("error", errorEvent);
                }
                finish();
            }

            @Override
            public void onClosed(EventSource eventSource) {
                finish();
            }
        });
    }

    private String getLlmUrl() {
        if ("mimo".equals(aiConfig.getProvider())) {
            return aiConfig.getMimo().getBaseUrl() + "/chat/completions";
        }
        return aiConfig.getDeepseek().getBaseUrl() + "/chat/completions";
    }

    private String getLlmApiKey() {
        if ("mimo".equals(aiConfig.getProvider())) {
            return aiConfig.getMimo().getApiKey();
        }
        return aiConfig.getDeepseek().getApiKey();
    }

    private String getLlmModel() {
        if ("mimo".equals(aiConfig.getProvider())) {
            return aiConfig.getMimo().getModel();
        }
        return aiConfig.getDeepseek().getModel();
    }

    private int getLlmMaxTokens() {
        if ("mimo".equals(aiConfig.getProvider())) {
            return aiConfig.getMimo().getMaxTokens();
        }
        return aiConfig.getDeepseek().getMaxTokens();
    }

    private double getLlmTemperature() {
        if ("mimo".equals(aiConfig.getProvider())) {
            return Math.min(aiConfig.getMimo().getTemperature(), 0.3);
        }
        return Math.min(aiConfig.getDeepseek().getTemperature(), 0.3);
    }

    /**
     * MiMo 推理强度：优先读后台配置 llm.reasoning-effort，回退 yml。
     * 仅 provider=mimo 时生效，其它 provider 返回 null（不注入该参数）。
     */
    private String getLlmReasoningEffort() {
        if (!"mimo".equals(aiConfig.getProvider())) {
            return null;
        }
        String yml = aiConfig.getMimo().getReasoningEffort();
        String fallback = (yml != null && !yml.isBlank()) ? yml.trim() : "low";
        try {
            String db = systemConfigService.getConfig("llm.reasoning-effort", fallback);
            return (db != null && !db.isBlank()) ? db.trim() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String buildUserPrompt(String question, String context) {
        return """
                请根据以下参考文档回答学生问题。

                ===== 参考文档 =====
                %s
                ===== 参考文档结束 =====

                学生问题：%s

                请严格按系统规则作答；若文档不足，明确说明无法依据现有条款回答。
                """.formatted(context, question);
    }

    // ===== 配置迁移辅助 =====

    /** 默认危机关键词（公开给启动 seed 器复用） */
    public static List<String> defaultCrisisKeywords() {
        return DEFAULT_CRISIS_KEYWORDS;
    }

    /** 默认系统提示词（公开给启动 seed 器复用） */
    public static String defaultSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** 默认关键词映射（公开给启动 seed 器复用） */
    public List<String[]> getDefaultKeywordMappings() {
        return defaultMappings();
    }

    /** 从 DB 读取系统提示词，库空/异常用默认 */
    private String getSystemPrompt() {
        try {
            String val = systemConfigService.getConfig(CFG_SYSTEM_PROMPT, SYSTEM_PROMPT);
            return val != null && !val.isBlank() ? val : SYSTEM_PROMPT;
        } catch (Exception e) {
            return SYSTEM_PROMPT;
        }
    }

    /** 从 DB 读取关键词映射（JSON 格式），缺失用代码默认数组 */
    private List<String[]> getKeywordMappings() {
        try {
            String raw = systemConfigService.getConfig(CFG_KEYWORD_MAP, null);
            if (raw != null && !raw.isBlank()) {
                // 期望格式：[["关键词","检索词"],...]
                JsonNode arr = objectMapper.readTree(raw);
                List<String[]> result = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode pair = arr.get(i);
                    if (pair.isArray() && pair.size() >= 2) {
                        result.add(new String[]{pair.get(0).asText(), pair.get(1).asText()});
                    }
                }
                return result.isEmpty() ? defaultMappings() : result;
            }
        } catch (Exception e) {
            System.err.println("配置 keyword.mappings 解析失败，使用默认映射: " + e.getMessage());
        }
        return defaultMappings();
    }

    /** 默认关键词映射（与原硬编码一致） */
    private List<String[]> defaultMappings() {
        return List.of(
                new String[]{"奖学金", "奖学金"}, new String[]{"助学金", "助学金"},
                new String[]{"勤工助学", "勤工助学"}, new String[]{"请假", "请假"},
                new String[]{"宿舍", "公寓"}, new String[]{"住宿", "公寓"},
                new String[]{"考试", "考场纪律"}, new String[]{"违纪", "纪律处分"},
                new String[]{"处分", "纪律处分"}, new String[]{"作弊", "违纪"},
                new String[]{"学位", "学位授予"}, new String[]{"毕业", "学籍管理"},
                new String[]{"休学", "休学"}, new String[]{"复学", "复学"},
                new String[]{"退学", "退学"}, new String[]{"转专业", "转专业"},
                new String[]{"学分", "学分"}, new String[]{"缓考", "缓考"},
                new String[]{"补考", "补考"}, new String[]{"重修", "重修"},
                new String[]{"心理", "心理"}, new String[]{"抑郁", "心理"},
                new String[]{"焦虑", "心理"}, new String[]{"贷款", "助学贷款"},
                new String[]{"困难", "困难"}, new String[]{"贫困", "经济困难"},
                new String[]{"图书馆", "图书馆"}, new String[]{"社团", "社团"},
                new String[]{"退宿", "退宿"}, new String[]{"换宿", "调宿"},
                new String[]{"处分撤销", "服务消过"}, new String[]{"撤销", "服务消过"},
                new String[]{"消过", "服务消过"}, new String[]{"四六级", "外语"},
                new String[]{"辅修", "辅修"}, new String[]{"双学位", "双学士"},
                new String[]{"申诉", "申诉"}, new String[]{"违规电器", "违章电器"},
                new String[]{"电器", "违章电器"}, new String[]{"晚归", "门禁"},
                new String[]{"门禁", "门禁"}, new String[]{"评优", "奖学金"},
                new String[]{"三好", "奖学金"}, new String[]{"荣誉称号", "奖学金"},
                new String[]{"处分期", "纪律处分"}, new String[]{"连带", "纪律处分"},
                new String[]{"举报", "纪律处分"}, new String[]{"处分影响", "纪律处分"},
                new String[]{"入党", "纪律处分"}, new String[]{"档案", "纪律处分"},
                new String[]{"记录", "纪律处分"}
        );
    }

    /** 危机关键词检测（来自配置或默认值） */
    private List<String> getCrisisKeywords() {
        try {
            String raw = systemConfigService.getConfig(CFG_CRISIS_KEYWORDS, null);
            if (raw != null && !raw.isBlank()) {
                List<String> list = new ArrayList<>();
                for (String s : raw.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) list.add(s);
                }
                return list.isEmpty() ? DEFAULT_CRISIS_KEYWORDS : list;
            }
        } catch (Exception e) {
            System.err.println("危机关键词配置读取失败: " + e.getMessage());
        }
        return DEFAULT_CRISIS_KEYWORDS;
    }

    /** 检测问题是否包含危机关键词，返回匹配列表 */
    private List<String> detectCrisisKeywords(String question) {
        if (question == null || question.isBlank()) return List.of();
        List<String> matched = new ArrayList<>();
        for (String kw : getCrisisKeywords()) {
            if (question.contains(kw)) matched.add(kw);
        }
        return matched.stream().distinct().toList();
    }

    /** 调用 LLM 时使用动态提示词 */
    private String getSystemPromptFromConfig() {
        return getSystemPrompt();
    }

    private String buildRequestBody(String model, String userPrompt, int maxTokens, double temperature, boolean stream) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", getSystemPromptFromConfig()),
                Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("stream", stream);
        // MiMo 推理强度控制
        String reasoningEffort = getLlmReasoningEffort();
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            requestBody.put("reasoning_effort", reasoningEffort);
        }
        return objectMapper.writeValueAsString(requestBody);
    }
}
