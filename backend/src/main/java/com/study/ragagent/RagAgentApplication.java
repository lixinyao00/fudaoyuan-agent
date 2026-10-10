package com.study.ragagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG Agent 应用启动类
 * 
 * @author Your Name
 */
@SpringBootApplication
public class RagAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAgentApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("🚀 RAG Agent 启动成功！");
        System.out.println("📝 访问地址: http://localhost:8080");
        System.out.println("❤️ 健康检查: http://localhost:8080/api/health");
        System.out.println("========================================\n");
    }
}
