package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.domain.enums.RiskLevel;
import com.example.daoyuanagent.service.RiskDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RiskDetectionTest {

    @Autowired
    private RiskDetectionService riskService;

    @Test
    void testNoRisk() {
        RiskDetectionService.RiskAssessment result = riskService.assess("奖学金怎么申请？");
        assertEquals(RiskLevel.NONE, result.level());
    }

    @Test
    void testCriticalRisk() {
        RiskDetectionService.RiskAssessment result = riskService.assess("我想自杀");
        assertEquals(RiskLevel.CRITICAL, result.level());
        assertEquals("SELF_HARM", result.riskType());
    }

    @Test
    void testHighRisk() {
        RiskDetectionService.RiskAssessment result = riskService.assess("我最近非常抑郁");
        assertEquals(RiskLevel.HIGH, result.level());
    }

    @Test
    void testViolenceRisk() {
        RiskDetectionService.RiskAssessment result = riskService.assess("我要杀人");
        assertEquals(RiskLevel.CRITICAL, result.level());
        assertEquals("VIOLENCE", result.riskType());
    }

    @Test
    void testNullInput() {
        RiskDetectionService.RiskAssessment result = riskService.assess(null);
        assertEquals(RiskLevel.NONE, result.level());
    }

    @Test
    void testEmptyInput() {
        RiskDetectionService.RiskAssessment result = riskService.assess("");
        assertEquals(RiskLevel.NONE, result.level());
    }
}
