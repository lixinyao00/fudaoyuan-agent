package com.example.daoyuanagent.provider;

public interface LlmGateway {
    LlmResult chat(String systemPrompt, String userMessage);
    String getProviderName();
}
