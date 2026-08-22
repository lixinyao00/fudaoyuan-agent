package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.entity.KnowledgeChunk;
import com.example.daoyuanagent.domain.enums.ReviewStatus;
import com.example.daoyuanagent.repository.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final KnowledgeChunkMapper chunkMapper;

    @Value("${rag.retrieval.top-k:8}")
    private int topK;

    @Value("${rag.evidence.top-n:5}")
    private int topN;

    @Value("${rag.min-score:0.15}")
    private double minScore;

    // Chinese stopwords for policy queries
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "什么", "怎么", "如何", "怎样",
            "吗", "呢", "吧", "啊", "哦", "嗯", "哪", "谁", "几", "多少", "为什么",
            "可以", "能", "应该", "需要", "请问", "想问", "咨询", "了解", "一下",
            "同学", "老师", "您好", "请问一下", "想", "问", "想知道", "告诉"
    );

    // Important words to always retain
    private static final Set<String> IMPORTANT_WORDS = Set.of(
            "不", "没有", "未", "禁止", "必须", "应当", "可以", "不得", "取消",
            "处分", "警告", "记过", "留校察看", "开除", "学籍"
    );

    // Policy keyword synonyms for normalization
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("申请", "申请"),
            Map.entry("报名", "申请"),
            Map.entry("申领", "申请"),
            Map.entry("办", "办理"),
            Map.entry("办理", "办理"),
            Map.entry("流程", "流程"),
            Map.entry("手续", "流程"),
            Map.entry("步骤", "流程"),
            Map.entry("条件", "条件"),
            Map.entry("资格", "条件"),
            Map.entry("要求", "条件"),
            Map.entry("材料", "材料"),
            Map.entry("文件", "材料"),
            Map.entry("资料", "材料"),
            Map.entry("时间", "时间"),
            Map.entry("日期", "时间"),
            Map.entry("期限", "时间"),
            Map.entry("截止", "时间")
    );

    // Number/date/condition patterns to preserve
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+[年月日号届期次]?");
    private static final Pattern NEGATION_PATTERN = Pattern.compile("(不|未|禁止|必须|不得|取消|无)");

    public RetrievalService(KnowledgeChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    public List<RetrievalResult> retrieve(String query, String category, int limit) {
        if (query == null || query.isBlank()) return List.of();

        // Step 1: Extract meaningful keywords
        List<Keyword> keywords = extractKeywords(query);
        if (keywords.isEmpty()) return List.of();

        String[] keywordTexts = keywords.stream().map(Keyword::text).toArray(String[]::new);

        // Step 2: Search candidates by keywords
        Map<Long, KnowledgeChunk> candidates = new LinkedHashMap<>();
        for (Keyword kw : keywords) {
            if (kw.text().length() < 2 && !IMPORTANT_WORDS.contains(kw.text())) continue;

            LambdaQueryWrapper<KnowledgeChunk> qw = new LambdaQueryWrapper<>();
            qw.eq(KnowledgeChunk::getReviewStatus, ReviewStatus.APPROVED);
            qw.like(KnowledgeChunk::getContent, kw.text());
            qw.last("LIMIT " + limit);
            for (KnowledgeChunk c : chunkMapper.selectList(qw)) {
                candidates.put(c.getId(), c);
            }
        }

        // Step 3: Score and rank
        List<RetrievalResult> results = candidates.values().stream()
                .map(c -> new RetrievalResult(c, computeScore(c, keywords, category)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .collect(Collectors.toList());

        // Step 4: Apply minimum score threshold
        List<RetrievalResult> filtered = results.stream()
                .filter(r -> r.score() >= minScore)
                .limit(topN)
                .collect(Collectors.toList());

        log.info("检索完成: query='{}', keywords={}, candidates={}, above_threshold={}, returned={}",
                query, keywordTexts.length, candidates.size(), results.stream()
                        .filter(r -> r.score() >= minScore).count(), filtered.size());
        return filtered;
    }

    private double computeScore(KnowledgeChunk chunk, List<Keyword> keywords, String queryCategory) {
        double score = 0;
        String content = chunk.getContent();
        String chapter = chunk.getChapterTitle() != null ? chunk.getChapterTitle() : "";
        String regulation = chunk.getRegulationName() != null ? chunk.getRegulationName() : "";
        String categories = chunk.getCategories() != null ? chunk.getCategories() : "";
        String articleRef = chunk.getArticleRef() != null ? chunk.getArticleRef() : "";

        for (Keyword kw : keywords) {
            String text = kw.text();
            double weight = kw.weight();

            // Content match
            int contentIdx = content.indexOf(text);
            if (contentIdx >= 0) {
                score += 0.25 * weight;
                // Boost for match near beginning
                if (contentIdx < 100) score += 0.05 * weight;

                // Count frequency (more mentions = more relevant)
                int count = countOccurrences(content, text);
                if (count > 1) score += Math.min(count * 0.03, 0.1) * weight;
            }

            // Regulation name match (strong signal)
            if (!regulation.isEmpty() && regulation.contains(text)) {
                score += 0.35 * weight;
            }

            // Chapter title match
            if (!chapter.isEmpty() && chapter.contains(text)) {
                score += 0.2 * weight;
            }

            // Article ref match
            if (!articleRef.isEmpty() && articleRef.contains(text)) {
                score += 0.15 * weight;
            }
        }

        // Category match boost
        if (queryCategory != null && !queryCategory.isBlank()) {
            if (categories.contains(queryCategory)) {
                score += 0.15;
            }
        }

        // Penalty for content that's clearly about a different topic
        // e.g., scholarship query shouldn't match disciplinary content
        if (queryCategory != null && !queryCategory.isBlank()) {
            if (queryCategory.equals("SCHOLARSHIP") && categories.contains("SAFETY")
                    && !content.contains("奖学金") && !content.contains("助学金")) {
                score *= 0.3; // Heavy penalty
            }
        }

        return Math.min(score, 1.0);
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private List<Keyword> extractKeywords(String query) {
        // Remove punctuation and clean
        String cleaned = query.replaceAll("[\\p{Punct}\\s，。？！、；：（）【】\u201c\u201d\u2018\u2019《》？！]+", " ").trim();
        if (cleaned.isEmpty()) return List.of();

        // If query contains random alphanumeric nonsense, treat as whole unit only
        boolean hasNonsense = cleaned.matches(".*[a-zA-Z]{2,}.*\\d+.*") || cleaned.matches(".*\\d+.*[a-zA-Z]{2,}.*");
        if (hasNonsense) {
            String full = cleaned.replaceAll("\\s+", "");
            if (full.length() >= 3) return List.of(new Keyword(full, 1.0));
            return List.of();
        }

        List<Keyword> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Split by spaces and extract meaningful segments
        for (String part : cleaned.split("\\s+")) {
            if (part.isEmpty()) continue;

            // Remove question words from the beginning
            String normalized = normalizeQuery(part);

            if (normalized.length() >= 2) {
                addKeyword(keywords, seen, normalized, 1.0);
            }
        }

        // 2. Extract policy-related phrases (compound terms)
        extractPolicyPhrases(query, keywords, seen);

        // 3. Extract important single characters (negations, numbers)
        Matcher numMatcher = NUMBER_PATTERN.matcher(query);
        while (numMatcher.find()) {
            addKeyword(keywords, seen, numMatcher.group(), 0.8);
        }

        Matcher negMatcher = NEGATION_PATTERN.matcher(query);
        while (negMatcher.find()) {
            addKeyword(keywords, seen, negMatcher.group(), 1.2);
        }

        // 4. Only use bigram fallback if we have zero meaningful keywords
        if (keywords.isEmpty() && cleaned.length() >= 4) {
            for (int i = 0; i <= cleaned.length() - 2; i++) {
                String gram = cleaned.substring(i, Math.min(i + 2, cleaned.length()));
                if (!STOPWORDS.contains(gram) && gram.matches("[\\u4e00-\\u9fff]{2}")) {
                    addKeyword(keywords, seen, gram, 0.5);
                }
            }
        }

        // 5. Add full query if meaningful (not too long)
        String fullQuery = cleaned.replaceAll("\\s+", "");
        if (fullQuery.length() >= 3 && fullQuery.length() <= 15) {
            addKeyword(keywords, seen, fullQuery, 1.5);
        }

        return keywords;
    }

    private void extractPolicyPhrases(String query, List<Keyword> keywords, Set<String> seen) {
        // Common policy query patterns
        String[][] patterns = {
                {"奖学金", "申请"}, {"奖学金", "条件"}, {"奖学金", "材料"},
                {"奖学金", "时间"}, {"奖学金", "流程"}, {"奖学金", "评审"},
                {"助学金", "申请"}, {"助学金", "条件"}, {"助学金", "材料"},
                {"困难认定", "条件"}, {"困难认定", "材料"}, {"困难认定", "流程"},
                {"勤工助学", "岗位"}, {"勤工助学", "申请"},
                {"处分", "取消"}, {"处分", "申诉"}, {"处分", "解除"},
                {"请假", "流程"}, {"请假", "手续"}, {"请假", "条件"},
                {"宿舍", "规定"}, {"宿舍", "卫生"}, {"宿舍", "熄灯"},
                {"选课", "时间"}, {"退课", "截止"}, {"重修", "报名"},
                {"毕业", "条件"}, {"学位", "授予"}, {"学位", "条件"},
                {"心理咨询", "预约"}, {"心理中心", "地址"},
        };

        for (String[] pattern : patterns) {
            boolean allMatch = true;
            for (String p : pattern) {
                if (!query.contains(p)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                for (String p : pattern) {
                    addKeyword(keywords, seen, p, 1.2);
                }
                // Add compound phrase
                String compound = String.join("", pattern);
                addKeyword(keywords, seen, compound, 1.5);
            }
        }
    }

    private String normalizeQuery(String text) {
        // Remove common question prefixes
        String result = text;
        String[] prefixes = {"怎么", "如何", "怎样", "请问", "想问", "咨询", "了解", "想知道"};
        for (String prefix : prefixes) {
            if (result.startsWith(prefix) && result.length() > prefix.length()) {
                result = result.substring(prefix.length());
            }
        }
        // Remove trailing question particles
        String[] suffixes = {"吗", "呢", "吧", "啊", "哦", "么"};
        for (String suffix : suffixes) {
            if (result.endsWith(suffix) && result.length() > suffix.length()) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }
        // Apply synonym normalization
        String normalized = SYNONYMS.get(result);
        return normalized != null ? normalized : result;
    }

    private void addKeyword(List<Keyword> keywords, Set<String> seen, String text, double weight) {
        if (text == null || text.isBlank()) return;
        text = text.trim();
        if (STOPWORDS.contains(text) && !IMPORTANT_WORDS.contains(text)) return;
        if (seen.contains(text)) return;
        seen.add(text);
        keywords.add(new Keyword(text, weight));
    }

    public record RetrievalResult(KnowledgeChunk chunk, double score) {}
    private record Keyword(String text, double weight) {}
}
