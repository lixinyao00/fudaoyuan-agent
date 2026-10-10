package com.study.ragagent.controller.admin;

import com.study.ragagent.domain.entity.Document;
import com.study.ragagent.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 管理员 - 知识库文档管理
 */
@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    @Autowired
    private DocumentService documentService;

    /** 列表（含分块数） */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Document> docs = documentService.listAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document d : docs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("title", d.getTitle());
            map.put("fileName", d.getFileName());
            map.put("fileSize", d.getFileSize());
            map.put("status", d.getStatus());
            map.put("createdAt", d.getCreatedAt());
            map.put("chunkCount", documentService.countChunks(d.getId()));
            result.add(map);
        }
        return result;
    }

    /** 上传（MultipartFile） */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        if (file == null || file.isEmpty()) {
            response.put("success", false);
            response.put("message", "文件不能为空");
            return response;
        }
        try {
            Document doc = documentService.uploadAndParse(file);
            response.put("success", true);
            response.put("id", doc.getId());
            response.put("title", doc.getTitle());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
        }
        return response;
    }

    /** 删除文档（同步清理 H2 + Milvus） */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            documentService.deleteDocument(id);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}