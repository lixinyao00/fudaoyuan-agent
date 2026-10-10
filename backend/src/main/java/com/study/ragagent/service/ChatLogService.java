package com.study.ragagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.ragagent.domain.entity.ChatLog;
import com.study.ragagent.mapper.ChatLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ChatLogService {

    private final ChatLogMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatLogService(ChatLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 创建一条问答日志（answer 暂为空，流式完成后再回填） */
    public ChatLog createLog(Long userId, String question, String retrievalMode) {
        ChatLog log = new ChatLog();
        log.setUserId(userId);
        log.setQuestion(question);
        log.setRetrievalMode(retrievalMode);
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
        return log;
    }

    /** 回填回答内容、引用和耗时 */
    public void updateAnswer(Long logId, String answer, List<Map<String, Object>> references, int latencyMs) {
        ChatLog record = mapper.selectById(logId);
        if (record == null) return;
        record.setAnswer(answer);
        try {
            record.setReferencesJson(objectMapper.writeValueAsString(references != null ? references : List.of()));
        } catch (Exception e) {
            record.setReferencesJson("[]");
        }
        record.setLatencyMs(latencyMs);
        mapper.updateById(record);
    }

    /** 学生反馈 */
    public void updateFeedback(Long logId, String feedback, String note) {
        ChatLog record = mapper.selectById(logId);
        if (record == null) return;
        record.setFeedback(feedback);
        record.setFeedbackNote(note);
        mapper.updateById(record);
    }

    public ChatLog getById(Long logId) {
        return mapper.selectById(logId);
    }

    /** 管理员分页查询 */
    public List<ChatLog> list(int page, int size, String feedbackFilter) {
        LambdaQueryWrapper<ChatLog> w = new LambdaQueryWrapper<>();
        if (feedbackFilter != null && !feedbackFilter.isBlank()) {
            if ("null".equals(feedbackFilter)) {
                w.isNull(ChatLog::getFeedback);
            } else {
                w.eq(ChatLog::getFeedback, feedbackFilter);
            }
        }
        w.orderByDesc(ChatLog::getCreatedAt);
        w.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return mapper.selectList(w);
    }

    public long count(String feedbackFilter) {
        LambdaQueryWrapper<ChatLog> w = new LambdaQueryWrapper<>();
        if (feedbackFilter != null && !feedbackFilter.isBlank()) {
            if ("null".equals(feedbackFilter)) {
                w.isNull(ChatLog::getFeedback);
            } else {
                w.eq(ChatLog::getFeedback, feedbackFilter);
            }
        }
        return mapper.selectCount(w);
    }

    /** 统计：总量、平均耗时、好评率、危机数 */
    public Map<String, Object> getStats() {
        List<ChatLog> all = mapper.selectList(null);
        long total = all.size();
        double avgLatency = all.stream()
                .filter(c -> c.getLatencyMs() != null)
                .mapToInt(ChatLog::getLatencyMs)
                .average().orElse(0);
        long withFeedback = all.stream().filter(c -> c.getFeedback() != null).count();
        long goodFeedback = all.stream().filter(c -> {
            try {
                return c.getFeedback() != null && Integer.parseInt(c.getFeedback()) >= 4;
            } catch (NumberFormatException e) {
                return false;
            }
        }).count();
        return Map.of(
                "total", total,
                "avgLatencyMs", Math.round(avgLatency),
                "feedbackCount", withFeedback,
                "goodFeedbackRate", withFeedback > 0 ? Math.round(goodFeedback * 1000.0 / withFeedback) / 10.0 : 0
        );
    }
}