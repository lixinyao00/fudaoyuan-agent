package com.study.ragagent.controller;

import com.study.ragagent.domain.entity.Document;
import com.study.ragagent.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    //保存文档
    @PostMapping
    public Document save(@RequestBody Document document) {
        return documentService.save(document);
    }

    //根据id查询
    @GetMapping("/{id}")
    public Document getById(@PathVariable Long id) {
        return documentService.getById(id);
    }

    //查询所有
    @GetMapping
    public List<Document> listAll() {
        return documentService.listAll();
    }

    //文件上传
    @PostMapping("/upload")
    public Document uploadFile(@RequestParam("file") MultipartFile file) {
        return documentService.uploadAndParse(file);
    }
}
