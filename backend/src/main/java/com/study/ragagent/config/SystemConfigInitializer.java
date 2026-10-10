package com.study.ragagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.ragagent.service.RagService;
import com.study.ragagent.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统配置初始化：把代码内默认值 seed 进 system_config 表（仅缺失时写入），
 * 使后台「系统配置管理」页面可见、可编辑。
 */
@Slf4j
@Component
public class SystemConfigInitializer implements ApplicationRunner {

    private final SystemConfigService configService;
    private final RagService ragService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemConfigInitializer(SystemConfigService configService, RagService ragService) {
        this.configService = configService;
        this.ragService = ragService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            configService.ensureDefault("system.prompt",
                    RagService.defaultSystemPrompt(), "PROMPT", "RAG 系统提示词");
            configService.ensureDefault("keyword.mappings",
                    toJson(ragService.getDefaultKeywordMappings()), "JSON", "关键词映射 [[触发词,检索词],...]");
            configService.ensureDefault("crisis.keywords",
                    String.join(",", RagService.defaultCrisisKeywords()), "STRING", "危机关键词，逗号分隔");
            log.info("系统配置已初始化（缺失项写入默认值）");
        } catch (Exception e) {
            log.warn("系统配置初始化失败（不影响启动）: {}", e.getMessage());
        }
    }

    private String toJson(List<String[]> mappings) throws Exception {
        List<List<String>> list = new ArrayList<>();
        for (String[] m : mappings) {
            list.add(List.of(m[0], m[1]));
        }
        return objectMapper.writeValueAsString(list);
    }
}