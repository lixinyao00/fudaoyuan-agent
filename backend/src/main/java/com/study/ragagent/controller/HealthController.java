package com.study.ragagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查 Controller
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "RAG Agent 运行正常");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("version", "1.0.0");
        return response;
    }

    /**
     * 欢迎页面
     */
    @GetMapping("/")
    public Map<String, Object> welcome() {
        Map<String, Object> response = new HashMap<>();
        response.put("project", "My RAG Agent");
        response.put("description", "从零手写的 RAG 智能问答系统");
        response.put("author", "Your Name");
        response.put("tips", "访问 /api/health 查看健康状态");
        return response;
    }
}
