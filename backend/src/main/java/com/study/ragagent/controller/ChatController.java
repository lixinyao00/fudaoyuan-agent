package com.study.ragagent.controller;

import com.study.ragagent.service.ChatLogService;
import com.study.ragagent.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 问答接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private RagService ragService;

    @Autowired
    private ChatLogService chatLogService;

    /** 从鉴权拦截器写入的 request attribute 取当前用户ID */
    private Long currentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        return attr instanceof Long l ? l : null;
    }

    /**
     * 问答接口（普通）
     */
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> request, HttpServletRequest http) {
        String question = request.get("question");
        return ragService.chat(question, currentUserId(http));
    }

    /**
     * 问答接口（流式 SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request, HttpServletRequest http) {
        String question = request.get("question");
        Long userId = currentUserId(http);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 异步执行，不阻塞主线程
        emitter.onCompletion(() -> {});
        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(e -> emitter.completeWithError(e));

        // 在新线程中执行流式调用
        new Thread(() -> {
            try {
                ragService.chatStream(question, emitter, userId);
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        }).start();

        return emitter;
    }

    /**
     * 学生反馈（普通用户）
     */
    @PostMapping("/feedback")
    public Map<String, Object> feedback(@RequestBody Map<String, Object> request, HttpServletRequest http) {
        Map<String, Object> response = new HashMap<>();
        Long userId = currentUserId(http);
        Object logIdRaw = request.get("logId");
        if (userId == null || logIdRaw == null) {
            response.put("success", false);
            response.put("message", "缺少必要信息");
            return response;
        }
        try {
            Long logId = Long.valueOf(logIdRaw.toString());
            String feedback = request.get("feedback") != null ? request.get("feedback").toString() : null;
            String note = request.get("note") != null ? request.get("note").toString() : null;
            chatLogService.updateFeedback(logId, feedback, note);
            response.put("success", true);
        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "日志ID不合法");
        }
        return response;
    }
}