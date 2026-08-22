package com.example.daoyuanagent.service;

import io.milvus.client.MilvusClient;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {
    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final MilvusClient milvusClient;
    private final DashScopeEmbeddingService embeddingService;

    @Value("${milvus.collection.name:student_handbook}")
    private String collectionName;

    @Value("${rag.vector.top-k:10}")
    private int vectorTopK;

    public VectorSearchService(@Autowired(required = false) MilvusClient milvusClient, 
                               DashScopeEmbeddingService embeddingService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    /**
     * 向量检索
     */
    public List<VectorSearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Milvus不可用时返回空结果
        if (milvusClient == null) {
            log.debug("Milvus不可用，跳过向量检索");
            return List.of();
        }

        try {
            // 1. 文本向量化
            float[] queryVector = embeddingService.embed(query);
            if (queryVector.length == 0) {
                log.warn("向量化失败，跳过向量检索: query='{}'", query);
                return List.of();
            }

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("vector")
                    .withTopK(Math.min(topK, vectorTopK))
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withOutFields(Arrays.asList("content", "page_number", "source"))
                    .withParams("{\"ef\":64}")
                    .build();

            // 3. 执行搜索
            R<io.milvus.grpc.SearchResults> response = milvusClient.search(searchParam);

            // 4. 解析结果
            List<VectorSearchResult> searchResults = new ArrayList<>();
            if (response.getData() != null) {
                SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

                List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
                for (SearchResultsWrapper.IDScore score : scores) {
                    try {
                        String content = (String) score.get("content");
                        Long pageNumber = (Long) score.get("page_number");
                        String source = (String) score.get("source");
                        float relevance = score.getScore();

                        if (content != null && !content.isEmpty()) {
                            searchResults.add(new VectorSearchResult(
                                    content,
                                    pageNumber != null ? pageNumber.intValue() : 0,
                                    source != null ? source : "",
                                    relevance
                            ));
                        }
                    } catch (Exception e) {
                        log.debug("解析搜索结果失败: {}", e.getMessage());
                    }
                }
            }

            log.info("向量检索完成: query='{}', results={}", query, searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("向量检索失败: query='{}'", query, e);
            return List.of();
        }
    }

    /**
     * RRF融合排序
     */
    public List<HybridSearchResult> rrfFusion(
            List<VectorSearchResult> vectorResults,
            List<KeywordSearchResult> keywordResults,
            int k) {

        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, HybridSearchResult> resultMap = new HashMap<>();

        // 计算向量检索的RRF分数
        for (int i = 0; i < vectorResults.size(); i++) {
            VectorSearchResult r = vectorResults.get(i);
            String key = generateKey(r.content());
            double rrfScore = 1.0 / (k + i + 1);
            scoreMap.merge(key, rrfScore, Double::sum);
            resultMap.put(key, new HybridSearchResult(
                    r.content(), r.pageNumber(), r.source(),
                    r.score(), 0.0, "vector"
            ));
        }

        // 计算关键词检索的RRF分数
        for (int i = 0; i < keywordResults.size(); i++) {
            KeywordSearchResult r = keywordResults.get(i);
            String key = generateKey(r.content());
            double rrfScore = 1.0 / (k + i + 1);
            scoreMap.merge(key, rrfScore, Double::sum);
            resultMap.putIfAbsent(key, new HybridSearchResult(
                    r.content(), r.pageNumber(), r.source(),
                    0.0, r.score(), "keyword"
            ));
        }

        // 按RRF分数排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(entry -> resultMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 生成内容的唯一键（用于去重）
     */
    private String generateKey(String content) {
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100);
    }

    // 结果类
    public record VectorSearchResult(
            String content,
            Integer pageNumber,
            String source,
            float score
    ) {}

    public record KeywordSearchResult(
            String content,
            Integer pageNumber,
            String source,
            double score
    ) {}

    public record HybridSearchResult(
            String content,
            Integer pageNumber,
            String source,
            double vectorScore,
            double keywordScore,
            String sourceType
    ) {}
}