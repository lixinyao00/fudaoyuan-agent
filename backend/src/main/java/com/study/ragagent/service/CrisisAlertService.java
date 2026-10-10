package com.study.ragagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.ragagent.domain.entity.CrisisAlert;
import com.study.ragagent.mapper.CrisisAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 危机告警服务
 * 记录用户提问中出现的危机关键词事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrisisAlertService {

    private final CrisisAlertMapper mapper;

    /** 创建一条未处理告警 */
    public CrisisAlert createAlert(Long chatLogId, Long userId, String question, List<String> matchedKeywords) {
        CrisisAlert alert = new CrisisAlert();
        alert.setChatLogId(chatLogId);
        alert.setUserId(userId);
        alert.setQuestion(question);
        alert.setMatchedKeywords(String.join(",", matchedKeywords));
        alert.setLevel("HIGH");
        alert.setHandled(false);
        alert.setCreatedAt(LocalDateTime.now());
        mapper.insert(alert);
        log.warn("[危机告警] chatLogId={}, userId={}, keywords={}", chatLogId, userId, matchedKeywords);
        return alert;
    }

    /** 标记已处置 */
    public void markHandled(Long alertId, Long handledBy) {
        CrisisAlert record = mapper.selectById(alertId);
        if (record != null) {
            record.setHandled(true);
            record.setHandledBy(handledBy);
            record.setHandledAt(LocalDateTime.now());
            mapper.updateById(record);
        }
    }

    /** 管理员查看列表 */
    public List<CrisisAlert> list(Boolean onlyUnHandled) {
        LambdaQueryWrapper<CrisisAlert> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(onlyUnHandled)) {
            wrapper.eq(CrisisAlert::getHandled, false);
        }
        wrapper.orderByDesc(CrisisAlert::getCreatedAt);
        return mapper.selectList(wrapper);
    }
}