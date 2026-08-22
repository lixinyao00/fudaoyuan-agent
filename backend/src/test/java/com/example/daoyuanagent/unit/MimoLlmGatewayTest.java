package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.provider.LlmGateway;
import com.example.daoyuanagent.provider.LlmResult;
import com.example.daoyuanagent.provider.MimoLlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MiMo API integration test.
 * Runs when AI_PROVIDER=mimo and MIMO_API_KEY is set.
 * Skips otherwise (mock mode is tested by MockLlmGatewayTest).
 */
@SpringBootTest
class MimoLlmGatewayTest {

    @Value("${ai.provider:mock}")
    private String aiProvider;

    @Autowired(required = false)
    private LlmGateway llmGateway;

    @Test
    void testMiMoProviderActivated() {
        if ("mimo".equals(aiProvider)) {
            assertNotNull(llmGateway, "MiMo gateway should be injected");
            assertEquals("mimo", llmGateway.getProviderName());
            assertTrue(llmGateway instanceof MimoLlmGateway,
                    "When ai.provider=mimo, gateway should be MimoLlmGateway");
        } else {
            // In mock mode, verify mock is used
            assertNotNull(llmGateway);
            assertEquals("mock", llmGateway.getProviderName());
        }
    }

    @Test
    void testMiMoChatWhenConfigured() {
        if (!"mimo".equals(aiProvider)) return; // Skip in mock mode

        LlmResult result = llmGateway.chat(
                "你是辅导员政策问答助手。请简短回答。",
                "你好");
        assertNotNull(result, "MiMo response should not be null");
        assertNotNull(result.getText(), "MiMo response text should not be null");
        assertFalse(result.getText().isBlank(), "MiMo response text should not be blank");
        assertEquals("mimo", result.getProvider());
        assertNotNull(result.getRequestId());
        assertTrue(result.getLatencyMs() > 0, "Should record latency");

        System.out.println("=== MiMo API 验证结果 ===");
        System.out.println("Provider: " + result.getProvider());
        System.out.println("Model: " + result.getModel());
        System.out.println("Latency: " + result.getLatencyMs() + "ms");
        System.out.println("Response: " + result.getText().substring(0, Math.min(200, result.getText().length())));
        System.out.println("Error: " + result.getErrorCode());
    }

    @Test
    void testMiMoPolicyAnswerWhenConfigured() {
        if (!"mimo".equals(aiProvider)) return;

        String systemPrompt = "你是辅导员政策问答助手。\n\n## 已审核证据\n"
                + "【证据片段1】\n页码=106\n制度=清华大学学生奖学金管理规定\n章节=第一章\n内容=奖学金每学年评选一次\n\n"
                + "## 回答要求\n依据证据回答，标注来源。";

        LlmResult result = llmGateway.chat(systemPrompt, "奖学金怎么申请？");
        assertNotNull(result);
        assertNotNull(result.getText());
        assertFalse(result.getText().isBlank());

        System.out.println("=== MiMo 政策回答测试 ===");
        System.out.println("Response length: " + result.getText().length());
        System.out.println("Response preview: " + result.getText().substring(0, Math.min(300, result.getText().length())));
    }
}
