package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.enums.RiskLevel;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RiskDetectionService {

    private static final List<RiskKeyword> CRITICAL_KEYWORDS = List.of(
            new RiskKeyword("自杀", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("想死", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("不想活", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("自残", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("割腕", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("跳楼", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("服毒", RiskLevel.CRITICAL, "SELF_HARM"),
            new RiskKeyword("杀人", RiskLevel.CRITICAL, "VIOLENCE"),
            new RiskKeyword("砍人", RiskLevel.CRITICAL, "VIOLENCE"),
            new RiskKeyword("炸弹", RiskLevel.CRITICAL, "VIOLENCE"),
            new RiskKeyword("放火", RiskLevel.CRITICAL, "VIOLENCE"),
            new RiskKeyword("绑架", RiskLevel.CRITICAL, "VIOLENCE")
    );

    private static final List<RiskKeyword> HIGH_KEYWORDS = List.of(
            new RiskKeyword("自伤", RiskLevel.HIGH, "SELF_HARM"),
            new RiskKeyword("抑郁", RiskLevel.HIGH, "MENTAL_HEALTH"),
            new RiskKeyword("崩溃", RiskLevel.HIGH, "MENTAL_HEALTH"),
            new RiskKeyword("活不下去", RiskLevel.HIGH, "SELF_HARM"),
            new RiskKeyword("伤害自己", RiskLevel.HIGH, "SELF_HARM"),
            new RiskKeyword("持刀", RiskLevel.HIGH, "VIOLENCE"),
            new RiskKeyword("威胁", RiskLevel.MEDIUM, "THREAT"),
            new RiskKeyword("打架", RiskLevel.MEDIUM, "VIOLENCE"),
            new RiskKeyword("火灾", RiskLevel.HIGH, "SAFETY"),
            new RiskKeyword("着火", RiskLevel.HIGH, "SAFETY"),
            new RiskKeyword("地震", RiskLevel.HIGH, "SAFETY"),
            new RiskKeyword("失联", RiskLevel.HIGH, "SAFETY"),
            new RiskKeyword("失踪", RiskLevel.HIGH, "SAFETY")
    );

    public RiskAssessment assess(String input) {
        if (input == null || input.isBlank()) return new RiskAssessment(RiskLevel.NONE, "", "");

        String text = input.toLowerCase();
        for (RiskKeyword kw : CRITICAL_KEYWORDS) {
            if (text.contains(kw.keyword())) {
                return new RiskAssessment(RiskLevel.CRITICAL, kw.riskType(), kw.keyword());
            }
        }
        for (RiskKeyword kw : HIGH_KEYWORDS) {
            if (text.contains(kw.keyword())) {
                return new RiskAssessment(RiskLevel.HIGH, kw.riskType(), kw.keyword());
            }
        }
        return new RiskAssessment(RiskLevel.NONE, "", "");
    }

    public record RiskAssessment(RiskLevel level, String riskType, String matchedKeyword) {}
    private record RiskKeyword(String keyword, RiskLevel level, String riskType) {}
}
