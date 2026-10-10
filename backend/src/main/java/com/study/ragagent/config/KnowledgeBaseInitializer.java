package com.study.ragagent.config;

import com.study.ragagent.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 启动时自动导入内置知识库 PDF
 * 可通过 rag.kb.enabled=false 关闭
 */
@Component
public class KnowledgeBaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private final DocumentService documentService;
    private final ResourceLoader resourceLoader;
    private final KnowledgeBaseProperties properties;

    public KnowledgeBaseInitializer(DocumentService documentService,
                                    ResourceLoader resourceLoader,
                                    KnowledgeBaseProperties properties) {
        this.documentService = documentService;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("KB 自动导入已关闭 (rag.kb.enabled=false)");
            return;
        }

        String title = properties.getTitle();
        String resourcePath = properties.getResource();

        try {
            if (documentService.isKbReady(title)) {
                log.info("KB 已就绪，跳过初始化: {}", title);
                return;
            }

            log.info("KB 未入库，开始从 {} 自动导入...", resourcePath);
            long start = System.currentTimeMillis();

            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                log.warn("KB 资源文件不存在: {}，跳过", resourcePath);
                return;
            }
            byte[] content = resource.getInputStream().readAllBytes();
            documentService.ingest(title, content);

            log.info("KB 自动导入完成: {}，耗时 {} ms", title, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("KB 自动导入失败（不影响启动，问答时可手动上传）: {}", e.getMessage());
        }
    }
}
