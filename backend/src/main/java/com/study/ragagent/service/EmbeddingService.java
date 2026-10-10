package com.study.ragagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.ragagent.config.AiConfig;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 向量化服务
 * 调用千问 Embedding API；未配置 API Key 时抛异常（不生成假向量），
 * 由调用方决定降级为纯关键词检索
 */
@Service
public class EmbeddingService {

    @Autowired
    private AiConfig aiConfig;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isConfigured() {
        String apiKey = aiConfig.getEmbedding().getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    public float[] embed(String text) throws Exception {
        return embedBatch(List.of(text)).get(0);
    }

    public List<float[]> embedBatch(List<String> texts) throws Exception {
        String apiKey = aiConfig.getEmbedding().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 Embedding API Key，向量检索不可用");
        }

        String url = aiConfig.getEmbedding().getBaseUrl() + "/embeddings";
        String model = aiConfig.getEmbedding().getModel();
        String requestBody = objectMapper.writeValueAsString(new EmbeddingRequest(model, texts));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Embedding API 调用失败: " + response.code() + " " + body);
            }

            JsonNode data = objectMapper.readTree(body).get("data");
            List<float[]> vectors = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JsonNode embedding = data.get(i).get("embedding");
                float[] vector = new float[embedding.size()];
                for (int j = 0; j < embedding.size(); j++) {
                    vector[j] = (float) embedding.get(j).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        }
    }

    private record EmbeddingRequest(String model, List<String> input) {}
}
