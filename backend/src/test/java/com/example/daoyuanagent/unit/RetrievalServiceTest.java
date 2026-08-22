package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RetrievalServiceTest {

    @Autowired
    private RetrievalService retrievalService;

    @Test
    void testEmptyQuery() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("", null, 8);
        assertNotNull(results);
    }

    @Test
    void testSearchWithCategory() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("奖学金", "SCHOLARSHIP", 8);
        assertNotNull(results);
    }

    @Test
    void testSearchNoResults() {
        List<RetrievalService.RetrievalResult> results = retrievalService.retrieve("完全不存在的内容xyz123", null, 8);
        assertNotNull(results);
        assertEquals(0, results.size());
    }
}
