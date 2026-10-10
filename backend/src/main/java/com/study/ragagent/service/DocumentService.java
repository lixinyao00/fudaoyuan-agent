package com.study.ragagent.service;

import com.study.ragagent.domain.entity.Document;
import com.study.ragagent.domain.entity.KnowledgeChunk;
import com.study.ragagent.domain.enums.DocumentStatus;
import com.study.ragagent.mapper.DocumentMapper;
import com.study.ragagent.mapper.KnowledgeChunkMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档服务
 * 负责文档上传、解析、切分、向量化
 */
@Service
public class DocumentService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 保存文档记录
     */
    public Document save(Document document) {
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        document.setStatus(DocumentStatus.UPLOADED);
        documentMapper.insert(document);
        return document;
    }

    /**
     * 根据ID查询文档
     */
    public Document getById(Long id) {
        return documentMapper.selectById(id);
    }

    /**
     * 查询所有文档
     */
    public List<Document> listAll() {
        return documentMapper.selectList(null);
    }

    /**
     * 上传并解析文档
     * 保存文件到 uploads/ 后转调 ingest
     */
    public Document uploadAndParse(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String filePath = uploadDir + System.currentTimeMillis() + "_" + fileName;
            File dest = new File(filePath);
            file.transferTo(dest);
            return ingest(fileName, Files.readAllBytes(Paths.get(filePath)));
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 导入一份文档内容
     * 完整流程：建记录 -> 解析 -> 切分 -> 向量化 -> 存储
     */
    public Document ingest(String title, byte[] content) {
        try {
            // 1. 创建文档记录
            Document document = new Document();
            document.setTitle(title);
            document.setFileName(title);
            document.setFileSize((long) content.length);
            document = save(document);

            // 2. 解析文档并切分
            List<String> chunks = parseDocument(title, content);

            // 3. 保存分块到数据库
            List<KnowledgeChunk> savedChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(document.getId());
                chunk.setContent(chunks.get(i));
                chunk.setChunkIndex(i);
                chunk.setTokenCount(chunks.get(i).length());
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);
                savedChunks.add(chunk);
            }

            // 4. 向量化并存储（只有 Embedding 和 Milvus 都就绪时执行）
            if (embeddingService.isConfigured() && vectorStoreService.isReady()) {
                for (KnowledgeChunk chunk : savedChunks) {
                    try {
                        float[] vector = embeddingService.embed(chunk.getContent());
                        vectorStoreService.insert(chunk.getId(), vector);
                    } catch (Exception e) {
                        System.err.println("向量化失败: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("Embedding 或 Milvus 未就绪，跳过向量化，仅使用关键词检索");
            }

            // 5. 更新文档状态
            document.setStatus(DocumentStatus.COMPLETED);
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);

            return document;
        } catch (Exception e) {
            throw new RuntimeException("文档导入失败: " + e.getMessage());
        }
    }

    /**
     * 删除文档：删除数据库分块与文档记录，并清理 Milvus 中的向量
     */
    public void deleteDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("文档不存在: " + id);
        }
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                        .eq("document_id", id));
        for (KnowledgeChunk chunk : chunks) {
            try {
                vectorStoreService.deleteByChunkId(chunk.getId());
            } catch (Exception e) {
                System.err.println("删除向量失败 chunkId=" + chunk.getId() + ": " + e.getMessage());
            }
        }
        chunkMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                        .eq("document_id", id));
        documentMapper.deleteById(id);
    }

    /**
     * 查询某个文档下的分块数
     */
    public long countChunks(Long documentId) {
        Long count = chunkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                        .eq("document_id", documentId));
        return count == null ? 0 : count;
    }

    /**
     * 判断知识库是否已就绪：存在同名 COMPLETED 文档且有分块
     */
    public boolean isKbReady(String title) {
        Document doc = documentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Document>()
                        .eq("title", title)
                        .eq("status", DocumentStatus.COMPLETED.name()));
        if (doc == null) {
            return false;
        }
        Long count = chunkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                        .eq("document_id", doc.getId()));
        return count != null && count > 0;
    }

    /**
     * 解析文档内容
     * 支持 PDF 和普通文本文件
     */
    private List<String> parseDocument(String title, byte[] content) throws Exception {
        String text;
        if (title != null && title.toLowerCase().endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(content)) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            }
        } else {
            text = new String(content);
        }
        return chunkText(text);
    }

    /**
     * 按固定窗口切分，保留重叠，避免政策条文被截断
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        // 先按空行聚合成段落，再按长度合并/切分
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder buffer = new StringBuilder();
        final int maxLen = 600;
        final int overlap = 80;

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.length() < 8) {
                continue;
            }
            if (buffer.length() == 0) {
                buffer.append(p);
                continue;
            }
            if (buffer.length() + p.length() + 1 <= maxLen) {
                buffer.append("\n").append(p);
            } else {
                chunks.add(buffer.toString());
                String prev = buffer.toString();
                String tail = prev.length() > overlap ? prev.substring(prev.length() - overlap) : prev;
                buffer.setLength(0);
                buffer.append(tail).append("\n").append(p);
                if (buffer.length() > maxLen * 2) {
                    // 超长段落硬切
                    String longText = buffer.toString();
                    for (int i = 0; i < longText.length(); i += maxLen - overlap) {
                        int end = Math.min(i + maxLen, longText.length());
                        chunks.add(longText.substring(i, end));
                        if (end >= longText.length()) break;
                    }
                    buffer.setLength(0);
                }
            }
        }
        if (buffer.length() >= 8) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }
}
