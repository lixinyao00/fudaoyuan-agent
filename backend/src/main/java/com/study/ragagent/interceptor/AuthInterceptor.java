package com.study.ragagent.interceptor;

import com.study.ragagent.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 认证拦截器
 * 1. 校验 /api/** 下的 Bearer Token
 * 2. /api/admin/** 仅允许 ADMIN
 * 3. 解析后将 userId / role 写入 request attribute
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_ROLE = "role";
    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "缺少认证令牌");
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "令牌无效或已过期");
        }

        Long userId = jwtUtil.getUserId(claims);
        String role = jwtUtil.getRole(claims);
        if (userId == null || !StringUtils.hasText(role)) {
            return writeError(response, HttpStatus.UNAUTHORIZED, "令牌内容不完整");
        }

        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_ROLE, role);

        // /api/admin/** 仅 ADMIN
        if (uri.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            return writeError(response, HttpStatus.FORBIDDEN, "需要管理员权限");
        }

        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTH);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
        return false;
    }
}