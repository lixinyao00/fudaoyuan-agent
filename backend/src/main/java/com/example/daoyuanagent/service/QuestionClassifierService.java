package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.enums.Category;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionClassifierService {

    public ClassificationResult classify(String question) {
        if (question == null || question.isBlank()) {
            return new ClassificationResult(Category.DAILY, List.of());
        }

        String q = question.toLowerCase();
        Category primary = Category.DAILY;
        List<Category> related = new ArrayList<>();

        // Score each category
        int maxScore = 0;
        for (Category cat : Category.values()) {
            int score = scoreCategory(q, cat);
            if (score > maxScore) {
                maxScore = score;
                primary = cat;
            }
        }

        // Find related categories
        for (Category cat : Category.values()) {
            if (cat == primary) continue;
            if (scoreCategory(q, cat) > 0) related.add(cat);
        }

        return new ClassificationResult(primary, related);
    }

    private int scoreCategory(String text, Category cat) {
        return switch (cat) {
            case SCHOLARSHIP -> count(text, "奖学金", "助学金", "困难", "补助", "贷款", "勤工", "评优", "奖励", "资助");
            case ATTENDANCE -> count(text, "考勤", "请假", "销假", "缺勤", "旷课", "迟到", "出勤", "假条", "军训");
            case DORMITORY -> count(text, "宿舍", "公寓", "住宿", "寝室", "床位", "调宿", "卫生", "查寝");
            case ACADEMIC -> count(text, "学分", "选课", "成绩", "学位", "毕业", "重修", "缓考", "退课", "考试", "教务", "学籍", "辅修", "转专业");
            case MENTAL_HEALTH -> count(text, "心理", "情绪", "压力", "抑郁", "焦虑", "咨询", "失眠", "难过", "不开心");
            case EMPLOYMENT -> count(text, "就业", "求职", "实习", "派遣", "简历", "工作", "offer");
            case SAFETY -> count(text, "处分", "违纪", "申诉", "安全", "消防", "暴力", "保卫", "应急");
            case DAILY -> count(text, "学生证", "校徽", "图书馆", "社团", "教室", "网络", "体育", "校园卡", "食堂");
        };
    }

    private int count(String text, String... keywords) {
        int c = 0;
        for (String kw : keywords) if (text.contains(kw)) c++;
        return c;
    }

    public record ClassificationResult(Category primary, List<Category> related) {}
}
