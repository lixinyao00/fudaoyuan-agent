package com.study.ragagent.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.ragagent.domain.entity.ChatLog;
import com.study.ragagent.service.ChatLogService;
import com.study.ragagent.service.CrisisAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理员 - 问答效果监控
 */
@RestController
@RequestMapping("/api/admin/chat-logs")
public class AdminChatLogController {

    @Autowired
    private ChatLogService chatLogService;

    @Autowired
    private CrisisAlertService crisisAlertService;

    /** 分页列表 */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String feedbackFilter) {
        Map<String, Object> result = new HashMap<>();
        List<ChatLog> rows = chatLogService.list(page, size, feedbackFilter);
        result.put("rows", rows);
        result.put("total", chatLogService.count(feedbackFilter));
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 详情（含引用解析） */
    @GetMapping("/{id}")
    public ChatLog detail(@PathVariable Long id) {
        return chatLogService.getById(id);
    }

    /** 统计卡 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new HashMap<>(chatLogService.getStats());
        stats.put("crisisCount", crisisAlertService.list(false).size());
        return stats;
    }
}