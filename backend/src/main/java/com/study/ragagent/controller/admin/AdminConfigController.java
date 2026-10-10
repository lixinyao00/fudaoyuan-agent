package com.study.ragagent.controller.admin;

import com.study.ragagent.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员 - 系统配置管理
 */
@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    @Autowired
    private SystemConfigService configService;

    /** 读取全部配置 */
    @GetMapping
    public List<Map<String, Object>> list() {
        return configService.listAll();
    }

    /** 更新单个配置项 */
    @PutMapping("/{key}")
    public Map<String, Object> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String value = body.get("value");
        try {
            configService.updateConfig(key, value);
            configService.clearCache();
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}