package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.entity.AuditLog;
import com.example.daoyuanagent.repository.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(String action, String operator, String targetType, String targetId, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setOperator(operator);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        auditLogMapper.insert(log);
    }

    public List<AuditLog> getRecent(int limit) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
