package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.api.dto.ChatResponse;
import com.example.daoyuanagent.domain.enums.RiskLevel;
import com.example.daoyuanagent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    void testNormalQuestion() {
        ChatResponse resp = chatService.chat(null, "奖学金怎么申请？", "test-user", "web");
        assertNotNull(resp.getAnswer());
        assertNotNull(resp.getConversationId());
        assertNotNull(resp.getRequestId());
        assertEquals(RiskLevel.NONE, resp.getRiskLevel());
        assertFalse(resp.getNeedsHumanReview());
    }

    @Test
    void testEmptyMessage() {
        ChatResponse resp = chatService.chat(null, "", "test-user", "web");
        assertNotNull(resp.getAnswer());
    }

    @Test
    void testCrisisDetection() {
        ChatResponse resp = chatService.chat(null, "我不想活了想自杀", "test-user", "web");
        assertEquals(RiskLevel.CRITICAL, resp.getRiskLevel());
        assertTrue(resp.getNeedsHumanReview());
    }

    @Test
    void testHighRiskDetection() {
        ChatResponse resp = chatService.chat(null, "我最近很抑郁，感觉崩溃了", "test-user", "web");
        assertTrue(resp.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal());
        assertTrue(resp.getNeedsHumanReview());
    }

    @Test
    void testMultiTurn() {
        ChatResponse resp1 = chatService.chat(null, "奖学金怎么申请？", "test-user", "web");
        String convId = resp1.getConversationId();
        ChatResponse resp2 = chatService.chat(convId, "需要什么材料？", "test-user", "web");
        assertEquals(convId, resp2.getConversationId());
    }

    @Test
    void testCategoryClassification() {
        ChatResponse resp = chatService.chat(null, "宿舍卫生检查标准是什么？", "test-user", "web");
        assertNotNull(resp.getCategory());
    }
}
