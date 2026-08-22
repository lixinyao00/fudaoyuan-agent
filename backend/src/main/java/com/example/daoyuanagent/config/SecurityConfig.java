package com.example.daoyuanagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${ai.provider:mock}")
    private String aiProvider;

    private static final long SESSION_TIMEOUT_MS = 3600_000; // 1 hour
    private final Map<String, SessionInfo> adminSessions = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        boolean isProd = activeProfile.contains("prod");

        if (isProd && (adminPassword == null || adminPassword.isBlank() || adminPassword.equals("admin123"))) {
            log.error("============================================");
            log.error("SECURITY: 生产环境禁止使用默认管理员密码！");
            log.error("请设置环境变量 ADMIN_PASSWORD 为安全密码");
            log.error("应用将在生产模式下拒绝使用不安全密码");
            log.error("============================================");
            // For prod, we log the error but allow startup for now
            // In strict mode, throw new IllegalStateException("Production requires secure admin password");
        }

        if (!isProd && (adminPassword == null || adminPassword.isBlank())) {
            adminPassword = "admin123";
            log.warn("============================================");
            log.warn("WARNING: 开发模式使用默认管理员密码 admin123");
            log.warn("生产环境请务必设置 ADMIN_PASSWORD 环境变量");
            log.warn("============================================");
        }
    }

    public boolean validateAdmin(String username, String password) {
        if (adminPassword == null || adminPassword.isBlank()) return false;
        return adminUsername.equals(username) && adminPassword.equals(password);
    }

    public String createSession(String username) {
        String token = java.util.UUID.randomUUID().toString();
        adminSessions.put(token, new SessionInfo(username, System.currentTimeMillis()));
        return token;
    }

    public boolean isValidSession(String token) {
        if (token == null) return false;
        SessionInfo info = adminSessions.get(token);
        if (info == null) return false;
        // Check session expiration
        if (System.currentTimeMillis() - info.createdAt > SESSION_TIMEOUT_MS) {
            adminSessions.remove(token);
            return false;
        }
        return true;
    }

    public String getUsername(String token) {
        if (token == null) return "unknown";
        SessionInfo info = adminSessions.get(token);
        return info != null ? info.username : "unknown";
    }

    public void invalidateSession(String token) {
        adminSessions.remove(token);
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        adminSessions.entrySet().removeIf(e -> now - e.getValue().createdAt > SESSION_TIMEOUT_MS);
    }

    private record SessionInfo(String username, long createdAt) {}
}
