package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.provider.LlmGateway;
import com.example.daoyuanagent.provider.LlmResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MockLlmGatewayTest {

    @Autowired
    private LlmGateway llmGateway;

    @Test
    void testProviderName() {
        assertEquals("mock", llmGateway.getProviderName());
    }

    @Test
    void testNormalChatNoEvidence() {
        LlmResult result = llmGateway.chat("你是助手", "你好");
        assertNotNull(result.getText());
        assertEquals("mock", result.getProvider());
        assertNotNull(result.getRequestId());
        // Without evidence, should indicate no data
        assertTrue(result.getText().contains("未收录") || result.getText().contains("未找到"),
                "无证据时应拒答");
    }

    @Test
    void testCrisisResponse() {
        String crisisPrompt = "你是辅导员政策问答助手。用户可能正处于心理危机中。\n## 紧急响应规则\n1. 首先表达理解和关心\n2. 立即提供求助资源";
        LlmResult result = llmGateway.chat(crisisPrompt, "我想自杀");
        assertNotNull(result.getText());
        assertTrue(result.getText().contains("120"), "危机响应应包含120");
        assertTrue(result.getText().contains("安全"), "危机响应应提及安全");
        // Must NOT contain fabricated phone numbers
        assertFalse(result.getText().contains("400-161-9995"), "不应硬编码心理热线号码");
    }

    @Test
    void testPolicyAnswerWithEvidence() {
        String promptWithEvidence = "你是辅导员政策问答助手。\n## 已审核证据\n"
                + "【证据片段1】\n页码=106-107\n制度=清华大学学生奖学金管理规定\n章节=第一章 总则\n条款=第十二条\n内容=申请奖学金应当满足以下基本条件：（一）具有中国国籍……\n\n"
                + "【证据片段2】\n页码=107\n制度=清华大学学生奖学金管理规定\n章节=第三章 评选程序\n条款=第十三条\n内容=奖学金每学年评选一次，原则上于秋季学期开学初组织评选。\n\n"
                + "## 回答要求\n- 依据证据回答";

        LlmResult result = llmGateway.chat(promptWithEvidence, "奖学金怎么申请？");
        String text = result.getText();

        // Must follow structured format
        assertTrue(text.contains("结论"), "应包含结论部分");
        assertTrue(text.contains("制度依据") || text.contains("手册"), "应包含制度依据");
        assertTrue(text.contains("注意事项"), "应包含注意事项");
        // Must contain real evidence
        assertTrue(text.contains("奖学金管理规定"), "应引用奖学金管理规定");
        assertTrue(text.contains("106") || text.contains("107"), "应包含页码");
    }

    @Test
    void testNoFabrication() {
        LlmResult result = llmGateway.chat("你是助手", "申请奖学金需要什么材料？");
        String text = result.getText();
        // Without evidence, must not fabricate
        assertFalse(text.contains("截止日期"), "不应编造截止日期");
        assertFalse(text.contains("申请链接"), "不应编造申请链接");
        assertFalse(text.contains("审批权限"), "不应编造审批权限");
    }
}
