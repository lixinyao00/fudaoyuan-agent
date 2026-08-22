package com.example.daoyuanagent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mimo")
public class MimoLlmGateway implements LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(MimoLlmGateway.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mimo.api-base-url}")
    private String apiBaseUrl;

    @Value("${mimo.api-key}")
    private String apiKey;

    @Value("${mimo.model:mimo-v2.5}")
    private String model;

    @Value("${mimo.timeout:30}")
    private int timeoutSeconds;

    @Value("${mimo.max-tokens:1200}")
    private int maxTokens;

    @Override
    public LlmResult chat(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MiMo API Key 未配置，返回降级提示");
            return LlmResult.builder()
                    .text("AI服务暂时不可用，请联系管理员配置API密钥。您也可以联系辅导员获取帮助。")
                    .provider("mimo")
                    .model(model)
                    .requestId(requestId)
                    .latencyMs(System.currentTimeMillis() - start)
                    .errorCode("API_KEY_MISSING")
                    .build();
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.1,
                    "max_tokens", maxTokens
            );

            String jsonBody = objectMapper.writeValueAsString(body);
            String url = apiBaseUrl.endsWith("/") ? apiBaseUrl + "chat/completions" : apiBaseUrl + "/chat/completions";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("[MiMo] 发送请求: model={}, requestId={}", model, requestId);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                log.error("[MiMo] API错误: status={}, body={}", response.statusCode(), response.body());
                return LlmResult.builder()
                        .text("AI服务响应异常（HTTP " + response.statusCode() + "），请稍后重试或联系辅导员。")
                        .provider("mimo")
                        .model(model)
                        .requestId(requestId)
                        .latencyMs(latency)
                        .errorCode("HTTP_" + response.statusCode())
                        .build();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messageNode = root.path("choices").path(0).path("message");
            String text = messageNode.path("content").asText("");
            // MiMo-V2.5 thinking model: reasoning_content holds the thought process,
            // content holds the final answer. Only expose content to users.
            if (text.isBlank() && messageNode.has("reasoning_content")) {
                text = messageNode.path("reasoning_content").asText("");
                log.info("[MiMo] content为空，使用reasoning_content作为回答");
            }
            String modelUsed = root.path("model").asText(model);

            log.info("[MiMo] 响应成功: model={}, latency={}ms, textLen={}", modelUsed, latency, text.length());

            return LlmResult.builder()
                    .text(text)
                    .provider("mimo")
                    .model(modelUsed)
                    .requestId(requestId)
                    .latencyMs(latency)
                    .build();

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[MiMo] 请求超时: timeout={}s", timeoutSeconds);
            return LlmResult.builder()
                    .text("AI服务响应超时，请稍后重试或联系辅导员。")
                    .provider("mimo")
                    .model(model)
                    .requestId(requestId)
                    .latencyMs(System.currentTimeMillis() - start)
                    .errorCode("TIMEOUT")
                    .build();
        } catch (Exception e) {
            log.error("[MiMo] 请求异常: {}", e.getMessage(), e);
            return LlmResult.builder()
                    .text("AI服务出现异常，请联系管理员。您可以联系辅导员获取帮助。")
                    .provider("mimo")
                    .model(model)
                    .requestId(requestId)
                    .latencyMs(System.currentTimeMillis() - start)
                    .errorCode("EXCEPTION")
                    .build();
        }
    }

    @Override
    public String getProviderName() { return "mimo"; }
}
