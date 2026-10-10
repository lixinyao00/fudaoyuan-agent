package com.study.ragagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {
    private String provider;
    private DeepSeekConfig deepseek = new DeepSeekConfig();
    private MimoConfig mimo = new MimoConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Data
    public static class DeepSeekConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int maxTokens = 2000;
        private double temperature = 0.7;
    }

    @Data
    public static class MimoConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int maxTokens = 2000;
        private double temperature = 0.7;
        /** MiMo 推理强度：low / medium / high；不填则使用默认（high） */
        private String reasoningEffort;
    }

    @Data
    public static class EmbeddingConfig {
        private String baseUrl;
        private String apiKey;
        private String model;
        private int dimension = 1024;
    }
}
