package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.entity.Feedback;
import com.example.daoyuanagent.domain.enums.FeedbackType;
import com.example.daoyuanagent.repository.FeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public void submitFeedback(String messageId, FeedbackType type, String reason) {
        Feedback fb = new Feedback();
        fb.setMessageId(messageId);
        fb.setFeedbackType(type);
        fb.setReason(reason);
        feedbackMapper.insert(fb);
    }

    public List<Feedback> getByMessageId(String messageId) {
        return feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>().eq(Feedback::getMessageId, messageId));
    }

    public List<Feedback> getAll() {
        return feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                .orderByDesc(Feedback::getCreatedAt));
    }
}
