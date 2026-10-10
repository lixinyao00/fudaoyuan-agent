package com.study.ragagent.controller.admin;

import com.study.ragagent.domain.entity.SysUser;
import com.study.ragagent.service.AuthService;
import com.study.ragagent.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理员 - 用户（学生账号）管理
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    /** 用户列表（不含密码） */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : userService.listAll()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("role", u.getRole());
            map.put("displayName", u.getDisplayName());
            map.put("studentNo", u.getStudentNo());
            map.put("status", u.getStatus());
            map.put("createdAt", u.getCreatedAt());
            result.add(map);
        }
        return result;
    }

    /** 创建学生账号 */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            response.put("success", false);
            response.put("message", "用户名和密码不能为空");
            return response;
        }
        try {
            SysUser user = authService.register(
                    username, password,
                    body.get("displayName"), body.get("studentNo"));
            response.put("success", true);
            response.put("id", user.getId());
            response.put("username", user.getUsername());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}