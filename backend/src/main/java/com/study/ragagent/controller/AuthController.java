package com.study.ragagent.controller;

import com.study.ragagent.domain.entity.SysUser;
import com.study.ragagent.service.AuthService;
import com.study.ragagent.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        try {
            AuthService.LoginResult result = authService.login(username, password);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", result.getToken());
            response.put("user", toSafeUser(result.getUser()));
            return response;
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return response;
        }
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        // 由 AuthInterceptor 写入 request attribute
        Object attr = request.getAttribute("userId");
        if (attr == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return response;
        }
        Long userId = (Long) attr;
        SysUser user = userService.getById(userId);
        response.put("success", true);
        response.put("user", toSafeUser(user));
        return response;
    }

    private Map<String, Object> toSafeUser(SysUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (user == null) {
            return map;
        }
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole());
        map.put("displayName", user.getDisplayName());
        map.put("studentNo", user.getStudentNo());
        map.put("status", user.getStatus());
        return map;
    }
}