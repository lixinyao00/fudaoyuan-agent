package com.study.ragagent.controller.admin;

import com.study.ragagent.domain.entity.CrisisAlert;
import com.study.ragagent.service.CrisisAlertService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员 - 危机告警管理
 */
@RestController
@RequestMapping("/api/admin/crisis-alerts")
public class AdminCrisisController {

    @Autowired
    private CrisisAlertService crisisAlertService;

    /** 告警列表（可只看未处理） */
    @GetMapping
    public List<CrisisAlert> list(@RequestParam(defaultValue = "false") boolean onlyUnhandled) {
        return crisisAlertService.list(onlyUnhandled);
    }

    /** 标记已处置 */
    @PostMapping("/{id}/handle")
    public Map<String, Object> handle(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Object uid = request.getAttribute("userId");
            Long handledBy = uid instanceof Long l ? l : null;
            crisisAlertService.markHandled(id, handledBy);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}