package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.domain.enums.Category;
import com.example.daoyuanagent.service.QuestionClassifierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QuestionClassifierTest {

    @Autowired
    private QuestionClassifierService classifierService;

    @Test
    void testScholarship() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("奖学金怎么申请");
        assertEquals(Category.SCHOLARSHIP, result.primary());
    }

    @Test
    void testAttendance() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("请假需要什么手续");
        assertEquals(Category.ATTENDANCE, result.primary());
    }

    @Test
    void testDormitory() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("宿舍管理规定");
        assertEquals(Category.DORMITORY, result.primary());
    }

    @Test
    void testAcademic() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("选课和学分要求");
        assertEquals(Category.ACADEMIC, result.primary());
    }

    @Test
    void testMentalHealth() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("最近心理压力很大");
        assertEquals(Category.MENTAL_HEALTH, result.primary());
    }

    @Test
    void testSafety() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("考试违纪处分");
        assertEquals(Category.SAFETY, result.primary());
    }

    @Test
    void testEmptyInput() {
        QuestionClassifierService.ClassificationResult result = classifierService.classify("");
        assertEquals(Category.DAILY, result.primary());
    }
}
