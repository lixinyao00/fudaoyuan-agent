package com.study.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置知识库配置 (rag.kb.*)
 */
@Component
@ConfigurationProperties(prefix = "rag.kb")
public class KnowledgeBaseProperties {

    /** 是否启用启动自动导入 */
    private boolean enabled = true;

    /** 入库后文档标题 */
    private String title = "学生手册.pdf";

    /** PDF 资源位置，支持 classpath:/file: 前缀 */
    private String resource = "classpath:kb/student-handbook.pdf";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
}
