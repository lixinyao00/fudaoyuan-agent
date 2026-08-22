package com.example.daoyuanagent.service;

import com.example.daoyuanagent.domain.entity.Document;
import com.example.daoyuanagent.domain.entity.KnowledgeChunk;
import com.example.daoyuanagent.domain.enums.Category;
import com.example.daoyuanagent.domain.enums.ReviewStatus;
import com.example.daoyuanagent.repository.DocumentMapper;
import com.example.daoyuanagent.repository.KnowledgeChunkMapper;
import com.example.daoyuanagent.util.HashUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PdfIngestionService {
    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);
    private static final int MIN_CHUNK_SIZE = 300;
    private static final int MAX_CHUNK_SIZE = 800;
    private static final int OVERLAP_SIZE = 80;

    // Pattern for regulation titles in TOC: "二十五、清华大学学生奖学金管理规定……102"
    private static final Pattern TOC_ENTRY_PATTERN = Pattern.compile(
            "^[一二三四五六七八九十]+[、,]\\s*(.+?)[.…]+\\s*(\\d+)$");

    // Pattern for regulation titles in body text (standalone, no chapter marker)
    private static final Pattern REGULATION_TITLE_PATTERN = Pattern.compile(
            "^([\\u4e00-\\u9fff][\\u4e00-\\u9fff\\w()（）]+(?:管理[规办细]定|实施[办细]法|管理[办细]法|实施办法|有关[规管]定|课程修读管理办法|教学管理办法|设置[与与]管理办法|认定管理办法|卫生管理细则|住宿管理办法|建设管理办法|知识产权管理[规办]定|借用[规管]定|使用[规管]定))$");

    // Pattern for chapter headers: "第一章　总则", "第二章  附 则"
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百]+[篇章节]\\s*[\\u4e00-\\u9fff\\s]+");

    // Pattern for article references
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十百]+[条款项])");

    // Noise patterns to filter
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\d{1,3}$");
    private static final Pattern PAGE_HEADER_PATTERN = Pattern.compile(
            "^\\d{1,3}\\s*$|^20\\d{2}[\\s\\-－]?[年\\-－]?\\s*[\\s\\-－]?\\d*\\s*[\\s\\-－]?学生手册$|^20\\d{2}\\s*年\\s*学生手册$");
    private static final Pattern SCHOOL_NAME_PATTERN = Pattern.compile(
            "^清华(大学)?\\s*$|^Tsinghua\\s+University$|^自强不息$|^厚德载物$|^清华大学校训$");

    // Patterns for table of contents pages
    private static final Pattern TOC_DOTS_PATTERN = Pattern.compile("[.…]{3,}");
    private static final Pattern TOC_HEADING = Pattern.compile("^目录$");

    private final DocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public PdfIngestionService(DocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Transactional
    public Map<String, Object> ingestPdf(String pdfPath) throws IOException {
        return ingestPdf(pdfPath, null);
    }

    /**
     * Ingest a PDF while preserving the user-facing filename. The admin upload
     * flow stores uploads in a temporary file, so using File#getName() alone
     * would leak names such as ingest-12345.pdf into citations.
     */
    @Transactional
    public Map<String, Object> ingestPdf(String pdfPath, String displayFileName) throws IOException {
        File file = new File(pdfPath);
        if (!file.exists()) throw new IOException("PDF文件不存在: " + pdfPath);

        String sha256 = computeFileHash(file);
        LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<>();
        qw.eq(Document::getSha256, sha256);
        if (documentMapper.selectCount(qw) > 0) {
            log.info("PDF已导入（相同SHA-256），跳过: {}", pdfPath);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "skipped");
            result.put("reason", "相同文件已导入");
            return result;
        }

        PDDocument pdDoc = Loader.loadPDF(file);
        int pageCount = pdDoc.getNumberOfPages();
        PDFTextStripper stripper = new PDFTextStripper();

        List<PageContent> pages = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String text = stripper.getText(pdDoc);
            pages.add(new PageContent(i, text));
        }
        pdDoc.close();

        // Step 1: Parse TOC to build regulation → start page mapping
        Map<String, Integer> tocRegulations = parseTocRegulations(pages);
        log.info("目录解析: 发现 {} 个制度条目", tocRegulations.size());

        // Step 2: Build inverted map: page → regulation name
        Map<Integer, String> pageToRegulation = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tocRegulations.entrySet()) {
            pageToRegulation.put(entry.getValue(), entry.getKey());
        }

        Document doc = new Document();
        String safeDisplayName = displayFileName == null || displayFileName.isBlank()
                ? file.getName() : new File(displayFileName).getName();
        doc.setFileName(safeDisplayName);
        doc.setVersion("2025");
        doc.setFilePath(pdfPath);
        doc.setSha256(sha256);
        doc.setPageCount(pageCount);
        doc.setStatus("ACTIVE");
        doc.setEffectiveDate(LocalDate.of(2025, 1, 1));
        documentMapper.insert(doc);

        List<KnowledgeChunk> chunks = buildChunks(pages, doc, tocRegulations, pageToRegulation);
        int seq = 0;
        for (KnowledgeChunk chunk : chunks) {
            chunk.setChunkId(HashUtil.chunkId(doc.getVersion(), chunk.getPageStart(), seq++));
            chunk.setDocumentId(doc.getId());
            chunk.setReviewStatus(ReviewStatus.PENDING);
            chunk.setCanonicalFlag(true);
            chunkMapper.insert(chunk);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("status", "success");
        stats.put("documentId", doc.getId());
        stats.put("pageCount", pageCount);
        stats.put("chunkCount", chunks.size());
        Map<String, Long> catCounts = chunks.stream()
                .flatMap(c -> Arrays.stream(c.getCategories().split(",")))
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        stats.put("categoryCounts", catCounts);

        // Chapter statistics
        long chapterCount = chunks.stream()
                .filter(c -> c.getChapterTitle() != null && !c.getChapterTitle().equals("待人工确认"))
                .map(KnowledgeChunk::getChapterTitle).distinct().count();
        long unconfirmedCount = chunks.stream()
                .filter(c -> "待人工确认".equals(c.getChapterTitle())).count();
        long regCount = chunks.stream()
                .filter(c -> c.getRegulationName() != null).map(KnowledgeChunk::getRegulationName).distinct().count();
        stats.put("chapterCount", chapterCount);
        stats.put("unconfirmedChapterCount", unconfirmedCount);
        stats.put("regulationCount", regCount);

        log.info("PDF导入完成: {} 页, {} 片段, {} 章节, {} 未确认, {} 制度",
                pageCount, chunks.size(), chapterCount, unconfirmedCount, regCount);
        return stats;
    }

    private Map<String, Integer> parseTocRegulations(List<PageContent> pages) {
        Map<String, Integer> result = new LinkedHashMap<>();
        boolean inToc = false;
        int tocEndPage = -1;

        for (PageContent page : pages) {
            String text = page.text().trim();
            if (text.isEmpty()) continue;

            // Detect TOC start
            if (text.contains("目录") && page.page() <= 5) {
                inToc = true;
                continue;
            }

            if (!inToc) continue;

            // TOC ends when we see actual content (first regulation title or chapter)
            if (text.matches("(?s).*第[一二三四五六七八九十]+章.*") && !TOC_DOTS_PATTERN.matcher(text).find()) {
                tocEndPage = page.page();
                break;
            }

            // Parse each line in TOC page
            String[] lines = text.split("\n");
            for (String line : lines) {
                String t = line.trim();
                Matcher m = TOC_ENTRY_PATTERN.matcher(t);
                if (m.find()) {
                    String title = m.group(1).trim();
                    int pageNum = Integer.parseInt(m.group(2));
                    // Adjust for PDF page offset (TOC page numbers start from the content, not PDF page)
                    // The TOC says "01" for "清华大学章程" which starts at PDF page 5
                    // So we need to find the offset
                    if (result.isEmpty()) {
                        // First entry helps calibrate offset
                    }
                    result.put(title, pageNum);
                }
            }

            // Stop TOC parsing after enough pages
            if (page.page() > 6) break;
        }

        // Now map TOC page numbers to actual PDF page numbers
        // We know "清华大学章程" is at TOC page 01 and PDF page 5
        // So offset = PDF_page - TOC_page = 5 - 1 = 4
        if (!result.isEmpty()) {
            int offset = 0;
            // Find the first entry to calibrate
            for (PageContent page : pages) {
                if (page.page() >= 5) {
                    String text = page.text();
                    for (Map.Entry<String, Integer> entry : result.entrySet()) {
                        if (text.contains(entry.getKey()) && text.contains("序言")) {
                            offset = page.page() - entry.getValue();
                            break;
                        }
                    }
                    if (offset != 0) break;
                }
            }
            // If we couldn't calibrate, use a reasonable default
            if (offset == 0) offset = 4;

            Map<String, Integer> calibrated = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : result.entrySet()) {
                calibrated.put(entry.getKey(), entry.getValue() + offset);
            }
            return calibrated;
        }
        return result;
    }

    private List<KnowledgeChunk> buildChunks(List<PageContent> pages, Document doc,
                                              Map<String, Integer> tocRegulations,
                                              Map<Integer, String> pageToRegulation) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int bufferStartPage = 1;

        // Track current context - never default to "前言" when we have real context
        String currentRegulation = null;
        String currentChapter = null;
        boolean isTocPage = false;
        boolean passedToc = false;

        for (PageContent page : pages) {
            String rawText = page.text();
            String[] lines = rawText.split("\n");

            // Detect TOC pages
            if (!passedToc && isTocPage(lines)) {
                isTocPage = true;
                continue;
            }
            if (isTocPage && !isTocPage(lines)) {
                isTocPage = false;
                passedToc = true;
            }

            // Clean the page text: remove noise lines
            StringBuilder cleaned = new StringBuilder();
            for (String line : lines) {
                String t = line.trim();
                if (isNoiseLine(t)) continue;
                cleaned.append(t).append("\n");
            }
            String text = cleaned.toString().trim();
            if (text.isEmpty()) continue;

            // Check if this page starts a new regulation (from TOC mapping)
            String regFromToc = pageToRegulation.get(page.page());
            if (regFromToc != null) {
                // Flush buffer before starting new regulation
                if (buffer.length() > 0) {
                    flushBuffer(chunks, buffer, bufferStartPage, page.page() - 1,
                            currentChapter, currentRegulation, doc);
                    buffer.setLength(0);
                }
                currentRegulation = regFromToc;
                currentChapter = null; // Reset chapter for new regulation
            }

            // Detect regulation title in body text
            String detectedReg = detectRegulationTitle(text);
            if (detectedReg != null && !detectedReg.equals(currentRegulation)) {
                if (buffer.length() > 0) {
                    flushBuffer(chunks, buffer, bufferStartPage, page.page() - 1,
                            currentChapter, currentRegulation, doc);
                    buffer.setLength(0);
                }
                currentRegulation = detectedReg;
                currentChapter = null;
            }

            // Detect chapter marker
            String detectedChapter = detectChapterTitle(text);
            if (detectedChapter != null) {
                // Flush buffer before new chapter
                if (buffer.length() > 0) {
                    flushBuffer(chunks, buffer, bufferStartPage, page.page() - 1,
                            currentChapter, currentRegulation, doc);
                    buffer.setLength(0);
                }
                currentChapter = detectedChapter;
            }

            // Buffer management
            if (buffer.length() > 0 && buffer.length() + text.length() > MAX_CHUNK_SIZE) {
                flushBuffer(chunks, buffer, bufferStartPage, page.page() - 1,
                        currentChapter, currentRegulation, doc);
                buffer.setLength(0);
            }

            if (buffer.isEmpty()) bufferStartPage = page.page();
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(text);

            if (buffer.length() >= MIN_CHUNK_SIZE) {
                String overlap = buffer.length() > OVERLAP_SIZE
                        ? buffer.substring(buffer.length() - OVERLAP_SIZE) : "";
                flushBuffer(chunks, buffer, bufferStartPage, page.page(),
                        currentChapter, currentRegulation, doc);
                buffer.setLength(0);
                buffer.append(overlap);
                bufferStartPage = page.page();
            }
        }
        if (buffer.length() > 0) {
            flushBuffer(chunks, buffer, bufferStartPage,
                    pages.get(pages.size() - 1).page(), currentChapter, currentRegulation, doc);
        }
        return chunks;
    }

    private boolean isTocPage(String[] lines) {
        int tocLineCount = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.equals("目录")) return true;
            if (TOC_DOTS_PATTERN.matcher(t).find() && t.matches("^[一二三四五六七八九十].*")) {
                tocLineCount++;
            }
        }
        return tocLineCount >= 3;
    }

    private boolean isNoiseLine(String text) {
        if (text.isEmpty()) return true;
        if (PAGE_NUMBER_PATTERN.matcher(text).matches()) return true;
        if (PAGE_HEADER_PATTERN.matcher(text).matches()) return true;
        if (SCHOOL_NAME_PATTERN.matcher(text).matches()) return true;
        // Filter "2025 年 学生手册" variations
        if (text.matches("^20\\d{2}[\\s\\-－]?[年\\-－]?\\s*[学生手册]*$")) return true;
        // Filter page number with dashes: "01", "02", etc. when standalone
        if (text.matches("^0[1-9]$|^1[0-7]\\d$")) return true;
        return false;
    }

    private void flushBuffer(List<KnowledgeChunk> chunks, StringBuilder buffer,
                             int startPage, int endPage, String chapter,
                             String regulation, Document doc) {
        String content = buffer.toString().trim();
        if (content.length() < 50) return;

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setPageStart(startPage);
        chunk.setPageEnd(endPage);

        // Set chapter: use detected chapter, or mark as unconfirmed
        if (chapter != null && !chapter.isBlank()) {
            chunk.setChapterTitle(chapter);
        } else if (regulation != null) {
            // When we know the regulation but not the specific chapter
            chunk.setChapterTitle("待人工确认");
        } else {
            chunk.setChapterTitle("待人工确认");
        }

        chunk.setRegulationName(regulation);
        chunk.setArticleRef(detectArticleRef(content));
        chunk.setCategories(classifyContent(content));
        chunk.setContent(content);
        chunk.setSourceHash(HashUtil.sha256(content));
        chunks.add(chunk);
    }

    private String classifyContent(String text) {
        Set<String> cats = new HashSet<>();
        String t = text.toLowerCase();
        if (containsAny(t, "奖学金", "助学金", "困难认定", "勤工助学", "助学贷款", "补助", "评优", "奖励"))
            cats.add(Category.SCHOLARSHIP.name());
        if (containsAny(t, "考勤", "请假", "销假", "缺勤", "旷课", "迟到", "纪律", "军训"))
            cats.add(Category.ATTENDANCE.name());
        if (containsAny(t, "宿舍", "公寓", "住宿", "寝室", "卫生", "调宿"))
            cats.add(Category.DORMITORY.name());
        if (containsAny(t, "学籍", "学分", "选课", "成绩", "学位", "毕业", "重修", "缓考", "退课", "考试", "考场", "辅修"))
            cats.add(Category.ACADEMIC.name());
        if (containsAny(t, "心理", "情绪", "压力", "抑郁", "焦虑", "咨询", "危机", "自伤", "自杀"))
            cats.add(Category.MENTAL_HEALTH.name());
        if (containsAny(t, "就业", "毕业", "证书", "修业", "派遣", "实习"))
            cats.add(Category.EMPLOYMENT.name());
        if (containsAny(t, "处分", "违纪", "申诉", "安全", "消防", "暴力", "应急", "保卫"))
            cats.add(Category.SAFETY.name());
        if (containsAny(t, "学生证", "校徽", "图书馆", "社团", "网络", "体育", "教室", "校园卡"))
            cats.add(Category.DAILY.name());
        if (cats.isEmpty()) cats.add(Category.DAILY.name());
        return String.join(",", cats);
    }

    /**
     * Detect regulation title in body text.
     * Matches standalone titles like "清华大学学生奖学金管理规定" on their own line.
     */
    private String detectRegulationTitle(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.length() < 5) continue;

            // Skip lines that are clearly not titles
            if (t.startsWith("第") && t.contains("条")) continue;
            if (t.matches("^第[一二三四五六七八九十百]+[篇章节].*")) continue;

            // Match regulation title patterns
            Matcher m = REGULATION_TITLE_PATTERN.matcher(t);
            if (m.find()) return m.group(1);

            // Also match specific known patterns
            if (t.matches("^清华[大学]?[\\u4e00-\\u9fff]+(?:规定|办法|细则|守则|准则|公约|条例)$") && t.length() >= 6) {
                return t;
            }
        }
        return null;
    }

    private String detectChapterTitle(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String t = line.trim();
            Matcher m = CHAPTER_PATTERN.matcher(t);
            if (m.find()) {
                return m.group().trim();
            }
        }
        return null;
    }

    private String detectArticleRef(String text) {
        Matcher m = ARTICLE_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String computeFileHash(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return HashUtil.sha256(new String(bytes, 0, Math.min(bytes.length, 10000)));
    }

    private record PageContent(int page, String text) {}
}
