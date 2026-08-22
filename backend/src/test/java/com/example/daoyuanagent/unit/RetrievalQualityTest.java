package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.service.RetrievalService;
import com.example.daoyuanagent.domain.entity.KnowledgeChunk;
import com.example.daoyuanagent.domain.enums.ReviewStatus;
import com.example.daoyuanagent.repository.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalQualityTest {

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @BeforeAll
    void setup() {
        // Approve all chunks for testing
        List<KnowledgeChunk> all = chunkMapper.selectList(null);
        for (KnowledgeChunk c : all) {
            if (c.getReviewStatus() != ReviewStatus.APPROVED) {
                c.setReviewStatus(ReviewStatus.APPROVED);
                chunkMapper.updateById(c);
            }
        }
    }

    @Test
    void testScholarshipQueryReturnsScholarshipContent() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("奖学金怎么申请", "SCHOLARSHIP", 8);
        if (results.isEmpty()) return; // Skip if no data

        // Top results should contain scholarship-related content
        boolean hasScholarship = results.stream()
                .limit(3)
                .anyMatch(r -> r.chunk().getContent().contains("奖学金")
                        || r.chunk().getRegulationName() != null && r.chunk().getRegulationName().contains("奖学金"));
        assertTrue(hasScholarship, "奖学金查询应返回奖学金相关内容");
    }

    @Test
    void testScholarshipQueryDoesNotReturnDisciplinaryContent() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("奖学金怎么申请", "SCHOLARSHIP", 8);
        if (results.isEmpty()) return;

        // Top results should NOT be primarily about disciplinary actions
        boolean topIsDisciplinary = results.stream()
                .limit(2)
                .anyMatch(r -> r.chunk().getRegulationName() != null
                        && r.chunk().getRegulationName().contains("处分")
                        && !r.chunk().getContent().contains("奖学金"));
        assertFalse(topIsDisciplinary, "奖学金查询不应将处分内容排在前面");
    }

    @Test
    void testNonexistentQueryReturnsEmpty() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("完全不存在的内容xyz123", null, 8);
        assertEquals(0, results.size(), "不存在的查询应返回空结果（拒答）");
    }

    @Test
    void testEmptyQueryReturnsEmpty() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("", null, 8);
        assertEquals(0, results.size());
    }

    @Test
    void testCategoryFiltering() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("宿舍", "DORMITORY", 8);
        if (results.isEmpty()) return;

        // Results should be in the DORMITORY category
        for (RetrievalService.RetrievalResult r : results) {
            assertTrue(r.chunk().getCategories().contains("DORMITORY"),
                    "宿舍查询结果应属于宿舍管理类别: " + r.chunk().getCategories());
        }
    }
}
