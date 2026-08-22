package com.example.daoyuanagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DashScopeEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingService.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${dashscope.embedding-model:text-embedding-v4}")
    private String embeddingModel;

    public DashScopeEmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 将文本转换为向量
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            String url = baseUrl + "/embeddings";

            String requestBody = objectMapper.writeValueAsString(new EmbeddingRequest(
                    embeddingModel,
                    text
            ));

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("嵌入API调用失败: code={}, body={}", response.code(),
                            response.body() != null ? response.body().string() : "null");
                    return new float[0];
                }

                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);

                // 解析嵌入向量
                JsonNode data = jsonNode.get("data");
                if (data != null && data.isArray() && !data.isEmpty()) {
                    JsonNode embedding = data.get(0).get("embedding");
                    if (embedding != null && embedding.isArray()) {
                        float[] vector = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            vector[i] = (float) embedding.get(i).asDouble();
                        }
                        log.debug("嵌入成功: text='{}', dimension={}", 
                                text.substring(0, Math.min(50, text.length())), vector.length);
                        return vector;
                    }
                }

                log.error("嵌入响应格式异常: {}", responseBody);
                return new float[0];
            }
        } catch (IOException e) {
            log.error("嵌入API调用异常", e);
            return new float[0];
        }
    }

    /**
     * 批量将文本转换为向量
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            String url = baseUrl + "/embeddings";

            String requestBody = objectMapper.writeValueAsString(new BatchEmbeddingRequest(
                    embeddingModel,
                    texts
            ));

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("批量嵌入API调用失败: code={}", response.code());
                    return List.of();
                }

                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);

                JsonNode data = jsonNode.get("data");
                if (data != null && data.isArray()) {
                    float[][] vectors = new float[data.size()][];
                    for (int i = 0; i < data.size(); i++) {
                        JsonNode embedding = data.get(i).get("embedding");
                        if (embedding != null && embedding.isArray()) {
                            vectors[i] = new float[embedding.size()];
                            for (int j = 0; j < embedding.size(); j++) {
                                vectors[i][j] = (float) embedding.get(j).asDouble();
                            }
                        }
                    }
                    return List.of(vectors);
                }

                log.error("批量嵌入响应格式异常");
                return List.of();
            }
        } catch (IOException e) {
            log.error("批量嵌入API调用异常", e);
            return List.of();
        }
    }

    // 请求类
    private record EmbeddingRequest(String model, String input) {}
    private record BatchEmbeddingRequest(String model, List<String> input) {}
}