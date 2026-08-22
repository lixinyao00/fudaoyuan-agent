package com.example.daoyuanagent.unit;

import com.example.daoyuanagent.service.PdfIngestionService;
import com.example.daoyuanagent.domain.entity.Document;
import com.example.daoyuanagent.repository.DocumentMapper;
import com.example.daoyuanagent.repository.KnowledgeChunkMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PdfIngestionServiceTest {

    @Autowired
    private PdfIngestionService pdfIngestionService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Test
    @Order(1)
    void testIngestPdfWithRealFile() throws Exception {
        File pdf = new File("学生手册(1)-1.pdf");
        if (!pdf.exists()) {
            return; // Skip if PDF not available
        }

        // Clean existing data for a fresh test
        chunkMapper.delete(null);
        documentMapper.delete(null);

        Map<String, Object> result = pdfIngestionService.ingestPdf(pdf.getAbsolutePath());
        assertEquals("success", result.get("status"));
        assertNotNull(result.get("documentId"));
        assertNotNull(result.get("pageCount"));
        assertNotNull(result.get("chunkCount"));
        assertTrue(((Number) result.get("chunkCount")).intValue() > 0, "应生成至少一个片段");
        assertTrue(((Number) result.get("pageCount")).intValue() > 100, "PDF应有100+页");

        Long documentId = ((Number) result.get("documentId")).longValue();
        Document document = documentMapper.selectById(documentId);
        assertNotNull(document);
        assertEquals("学生手册(1)-1.pdf", document.getFileName(), "知识库引用必须保留原始文件名");

        // Verify regulation count
        assertNotNull(result.get("regulationCount"));
        assertTrue(((Number) result.get("regulationCount")).intValue() > 5, "应识别至少5个制度");

        // Verify unconfirmed count is tracked
        assertNotNull(result.get("unconfirmedChapterCount"));
    }

    @Test
    @Order(2)
    void testDuplicateIngestSkipped() throws Exception {
        File pdf = new File("学生手册(1)-1.pdf");
        if (!pdf.exists()) return;

        // First ingest already done by testIngestPdfWithRealFile
        Map<String, Object> result = pdfIngestionService.ingestPdf(pdf.getAbsolutePath());
        assertEquals("skipped", result.get("status"));
    }

    @Test
    @Order(3)
    void testNonexistentPdfThrows() {
        assertThrows(Exception.class, () -> {
            pdfIngestionService.ingestPdf("/nonexistent/path/file.pdf");
        });
    }
}
