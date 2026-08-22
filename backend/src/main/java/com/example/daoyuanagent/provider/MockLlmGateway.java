package com.example.daoyuanagent.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmGateway implements LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(MockLlmGateway.class);

    @Override
    public LlmResult chat(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();
        log.info("[Mock LLM] 收到请求，系统提示词长度={}, 用户消息长度={}", systemPrompt.length(), userMessage.length());

        String response;
        // Check if system prompt indicates crisis mode
        if (systemPrompt.contains("紧急响应规则") || systemPrompt.contains("危机")) {
            response = buildCrisisResponse();
        } else if (containsPolicyEvidence(systemPrompt)) {
            response = buildStructuredPolicyAnswer(systemPrompt, userMessage);
        } else {
            response = buildNoEvidenceResponse();
        }

        long latency = System.currentTimeMillis() - start;
        return LlmResult.builder()
                .text(response)
                .provider("mock")
                .model("mock-model-v1")
                .requestId(UUID.randomUUID().toString())
                .latencyMs(latency)
                .build();
    }

    @Override
    public String getProviderName() { return "mock"; }

    private String buildCrisisResponse() {
        return "我理解你现在可能感到很难受。请记住，你不是一个人。\n\n"
                + "**你的安全是最重要的。**\n\n"
                + "如果你正处于危机中，请立即采取以下行动：\n"
                + "1. 拨打 **120**（急救）或 **110**（报警）\n"
                + "2. 联系你信任的人——家人、朋友、辅导员\n"
                + "3. 联系学校心理咨询中心（请向辅导员获取具体联系方式）\n\n"
                + "你不需要独自面对这些困难，寻求帮助是勇敢的行为。";
    }

    private String buildNoEvidenceResponse() {
        return "当前知识库未收录与您问题直接相关的明确依据。\n\n"
                + "**建议您：**\n"
                + "1. 联系辅导员获取准确解答\n"
                + "2. 查阅《2025年学生手册》相关章节\n"
                + "3. 咨询相关职能部门\n\n"
                + "为避免误导，我们在没有可靠依据时不会给出政策解答。";
    }

    private boolean containsPolicyEvidence(String systemPrompt) {
        return systemPrompt.contains("【证据片段");
    }

    private String buildStructuredPolicyAnswer(String systemPrompt, String userMessage) {
        List<EvidenceBlock> evidence = parseEvidence(systemPrompt);
        if (evidence.isEmpty()) {
            return buildNoEvidenceResponse();
        }

        StringBuilder answer = new StringBuilder();

        // 1. Conclusion section
        String mainRegulation = evidence.stream()
                .map(e -> e.regulation)
                .filter(r -> r != null)
                .findFirst().orElse(null);

        if (mainRegulation != null) {
            answer.append("**结论**：根据《").append(mainRegulation).append("》相关规定：\n\n");
        } else {
            answer.append("**结论**：根据《2025年学生手册》相关规定：\n\n");
        }

        // 2. Key content from evidence (conditions/process)
        for (int i = 0; i < evidence.size() && i < 3; i++) {
            EvidenceBlock eb = evidence.get(i);
            if (eb.content == null) continue;

            // Try to extract conditions/process from content
            String trimmed = eb.content.length() > 300
                    ? eb.content.substring(0, 300) + "..." : eb.content;

            if (eb.chapter != null && !eb.chapter.equals("待人工确认")) {
                answer.append("- **").append(eb.chapter).append("**");
            } else if (eb.regulation != null) {
                answer.append("- **").append(eb.regulation).append("**");
            } else {
                answer.append("- **相关规定**");
            }

            if (eb.page != null) {
                answer.append("（第").append(eb.page).append("页）");
            }
            answer.append("：").append(trimmed).append("\n\n");
        }

        // 3. Source citation
        answer.append("**制度依据**：");
        List<String> sources = new ArrayList<>();
        for (EvidenceBlock eb : evidence) {
            if (eb.regulation != null) {
                String src = "《" + eb.regulation + "》";
                if (eb.page != null) src += "第" + eb.page + "页";
                if (!sources.contains(src)) sources.add(src);
            }
        }
        if (sources.isEmpty()) {
            answer.append("《2025年学生手册》");
        } else {
            answer.append(String.join("；", sources));
        }
        answer.append("\n\n");

        // 4. Notes and human consultation advice
        answer.append("**注意事项**：\n");
        answer.append("- 以上信息仅基于《2025年学生手册》中的已审核条款\n");
        answer.append("- 如有疑问请联系辅导员或相关职能部门确认\n");
        answer.append("- 具体申请材料、截止日期等以实际通知为准\n");

        return answer.toString();
    }

    private List<EvidenceBlock> parseEvidence(String systemPrompt) {
        List<EvidenceBlock> result = new ArrayList<>();
        String[] blocks = systemPrompt.split("【证据片段");
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            EvidenceBlock eb = new EvidenceBlock();
            eb.page = extractField(block, "页码");
            eb.regulation = extractField(block, "制度");
            eb.chapter = extractField(block, "章节");
            eb.articleRef = extractField(block, "条款");
            eb.content = extractField(block, "内容");
            if (eb.content != null) result.add(eb);
        }
        return result;
    }

    private String extractField(String block, String fieldName) {
        int idx = block.indexOf(fieldName + "=");
        if (idx < 0) return null;
        int start = idx + fieldName.length() + 1;
        int end = block.indexOf("\n", start);
        if (end < 0) end = Math.min(start + 200, block.length());
        return block.substring(start, end).trim();
    }

    private static class EvidenceBlock {
        String page;
        String regulation;
        String chapter;
        String articleRef;
        String content;
    }
}
