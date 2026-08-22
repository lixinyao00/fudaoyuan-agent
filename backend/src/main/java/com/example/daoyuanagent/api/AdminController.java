package com.example.daoyuanagent.api;

import com.example.daoyuanagent.api.dto.ErrorResponse;
import com.example.daoyuanagent.domain.entity.*;
import com.example.daoyuanagent.domain.enums.ReviewStatus;
import com.example.daoyuanagent.repository.*;
import com.example.daoyuanagent.service.*;
import com.example.daoyuanagent.config.SecurityConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SecurityConfig securityConfig;
    private final PdfIngestionService pdfIngestionService;
    private final KnowledgeChunkMapper chunkMapper;
    private final FeedbackService feedbackService;
    private final RiskEventMapper riskEventMapper;
    private final AuditService auditService;
    private final DocumentMapper documentMapper;

    public AdminController(SecurityConfig securityConfig, PdfIngestionService pdfIngestionService,
                           KnowledgeChunkMapper chunkMapper, FeedbackService feedbackService,
                           RiskEventMapper riskEventMapper, AuditService auditService,
                           DocumentMapper documentMapper) {
        this.securityConfig = securityConfig;
        this.pdfIngestionService = pdfIngestionService;
        this.chunkMapper = chunkMapper;
        this.feedbackService = feedbackService;
        this.riskEventMapper = riskEventMapper;
        this.auditService = auditService;
        this.documentMapper = documentMapper;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");
        if (!securityConfig.validateAdmin(username, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.builder().code("AUTH_FAILED").message("用户名或密码错误").build());
        }
        String token = securityConfig.createSession(username);
        Cookie cookie = new Cookie("admin_token", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(3600);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("token", token, "username", username));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractToken(request);
        if (token != null) securityConfig.invalidateSession(token);
        Cookie cookie = new Cookie("admin_token", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("message", "已退出"));
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestParam("file") MultipartFile file,
                                    HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();

        // Validate file
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INVALID_FILE").message("仅支持PDF文件").build());
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("FILE_TOO_LARGE").message("文件大小不能超过50MB").build());
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("EMPTY_FILE").message("文件不能为空").build());
        }

        // Sanitize filename to prevent path traversal
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff()（）]", "_");

        try {
            File temp = File.createTempFile("ingest-", ".pdf");
            file.transferTo(temp);
            Map<String, Object> result = pdfIngestionService.ingestPdf(
                    temp.getAbsolutePath(), file.getOriginalFilename());
            auditService.log("INGEST", "admin", "document", safeName, result.toString());
            temp.delete(); // Clean up temp file
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("PDF导入失败", e);
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .code("INGEST_ERROR").message("导入失败，请检查文件格式").build());
        }
    }

    @PostMapping("/chunks/{id}/review")
    public ResponseEntity<?> reviewChunk(@PathVariable Long id, @RequestBody Map<String, String> body,
                                         HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("error", "status required"));
        KnowledgeChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) return ResponseEntity.notFound().build();
        chunk.setReviewStatus(ReviewStatus.valueOf(status));
        chunkMapper.updateById(chunk);
        auditService.log("REVIEW", "admin", "chunk", id.toString(),
                "status=" + status + ", notes=" + body.get("notes"));
        return ResponseEntity.ok(Map.of("id", id, "status", status));
    }

    @GetMapping("/chunks")
    public ResponseEntity<?> listChunks(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String status,
                                        HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        LambdaQueryWrapper<KnowledgeChunk> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(KnowledgeChunk::getReviewStatus, ReviewStatus.valueOf(status));
        qw.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return ResponseEntity.ok(chunkMapper.selectList(qw));
    }

    @GetMapping("/chunks/{id}")
    public ResponseEntity<?> getChunk(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        return ResponseEntity.ok(chunkMapper.selectById(id));
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> listFeedback(HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        return ResponseEntity.ok(feedbackService.getAll());
    }

    @GetMapping("/risks")
    public ResponseEntity<?> listRisks(@RequestParam(required = false) String status,
                                       HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        LambdaQueryWrapper<RiskEvent> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(RiskEvent::getStatus, status);
        qw.orderByDesc(RiskEvent::getCreatedAt);
        return ResponseEntity.ok(riskEventMapper.selectList(qw));
    }

    @GetMapping("/risks/{id}")
    public ResponseEntity<?> getRisk(@PathVariable Long id, HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(event);
    }

    @PostMapping("/risks/{id}/claim")
    public ResponseEntity<?> claimRisk(@PathVariable Long id, @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) return ResponseEntity.notFound().build();
        String operator = getOperator(request);
        event.setStatus("IN_PROGRESS");
        event.setHandler(operator);
        event.setUpdatedAt(java.time.LocalDateTime.now());
        riskEventMapper.updateById(event);
        auditService.log("RISK_CLAIM", operator, "risk_event", id.toString(),
                "level=" + event.getRiskLevel() + ", type=" + event.getRiskType());
        return ResponseEntity.ok(Map.of("id", id, "status", "IN_PROGRESS", "handler", operator));
    }

    @PostMapping("/risks/{id}/note")
    public ResponseEntity<?> addNote(@PathVariable Long id, @RequestBody Map<String, String> body,
                                      HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) return ResponseEntity.notFound().build();
        String note = body.get("note");
        if (note == null || note.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "note required"));
        }
        String operator = getOperator(request);
        String existing = event.getHandlerNotes() != null ? event.getHandlerNotes() : "";
        event.setHandlerNotes(existing + "\n[" + operator + "] " + note);
        event.setUpdatedAt(java.time.LocalDateTime.now());
        riskEventMapper.updateById(event);
        auditService.log("RISK_NOTE", operator, "risk_event", id.toString(), "added note");
        return ResponseEntity.ok(Map.of("id", id, "handler_notes", event.getHandlerNotes()));
    }

    @PostMapping("/risks/{id}/resolve")
    public ResponseEntity<?> resolveRisk(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                          HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) return ResponseEntity.notFound().build();
        String operator = getOperator(request);
        event.setStatus("RESOLVED");
        event.setResolved(true);
        event.setResolvedAt(java.time.LocalDateTime.now());
        event.setUpdatedAt(java.time.LocalDateTime.now());
        if (body != null && body.get("note") != null) {
            String existing = event.getHandlerNotes() != null ? event.getHandlerNotes() : "";
            event.setHandlerNotes(existing + "\n[RESOLVED by " + operator + "] " + body.get("note"));
        }
        riskEventMapper.updateById(event);
        auditService.log("RISK_RESOLVE", operator, "risk_event", id.toString(),
                "level=" + event.getRiskLevel());
        return ResponseEntity.ok(Map.of("id", id, "status", "RESOLVED"));
    }

    @PostMapping("/risks/{id}/close")
    public ResponseEntity<?> closeRisk(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                        HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        RiskEvent event = riskEventMapper.selectById(id);
        if (event == null) return ResponseEntity.notFound().build();
        String operator = getOperator(request);
        event.setStatus("CLOSED");
        event.setResolved(true);
        event.setResolvedAt(java.time.LocalDateTime.now());
        event.setUpdatedAt(java.time.LocalDateTime.now());
        if (body != null && body.get("note") != null) {
            String existing = event.getHandlerNotes() != null ? event.getHandlerNotes() : "";
            event.setHandlerNotes(existing + "\n[CLOSED by " + operator + "] " + body.get("note"));
        }
        riskEventMapper.updateById(event);
        auditService.log("RISK_CLOSE", operator, "risk_event", id.toString(),
                "level=" + event.getRiskLevel());
        return ResponseEntity.ok(Map.of("id", id, "status", "CLOSED"));
    }

    @GetMapping("/audit")
    public ResponseEntity<?> listAudit(HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        return ResponseEntity.ok(auditService.getRecent(100));
    }

    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(HttpServletRequest request) {
        if (!checkAuth(request)) return unauthorized();
        return ResponseEntity.ok(documentMapper.selectList(new LambdaQueryWrapper<>()));
    }

    private String getOperator(HttpServletRequest request) {
        String token = extractToken(request);
        return securityConfig.getUsername(token);
    }

    private boolean checkAuth(HttpServletRequest request) {
        String token = extractToken(request);
        return securityConfig.isValidSession(token);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("admin_token".equals(c.getName())) return c.getValue();
            }
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder().code("UNAUTHORIZED").message("请先登录").build());
    }
}
