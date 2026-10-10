package com.study.ragagent.service;

import com.study.ragagent.config.AdminProperties;
import com.study.ragagent.domain.entity.SysUser;
import com.study.ragagent.domain.enums.Role;
import com.study.ragagent.domain.enums.UserStatus;
import com.study.ragagent.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务
 * 仅依赖 spring-security-crypto 的 BCrypt，不启用 Spring Security Web
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminProperties adminProperties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录：校验用户名密码，成功返回 token
     */
    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        SysUser user = userService.getByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (UserStatus.INACTIVE.name().equals(user.getStatus())
                || UserStatus.LOCKED.name().equals(user.getStatus())) {
            throw new IllegalStateException("账号已被停用或锁定");
        }
        String token = jwtUtil.generateToken(user);
        return new LoginResult(token, user);
    }

    /**
     * 创建默认管理员（若不存在则创建）
     */
    public void createDefaultAdmin() {
        String username = adminProperties.getUsername();
        if (userService.existsByUsername(username)) {
            log.info("默认管理员已存在，跳过创建: {}", username);
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
        admin.setRole(Role.ADMIN.name());
        admin.setDisplayName("系统管理员");
        admin.setStatus(UserStatus.ACTIVE.name());
        admin.setCreatedAt(LocalDateTime.now());
        userService.save(admin);
        log.info("已创建默认管理员账号: {}", username);
    }

    /**
     * 注册普通用户（可选，供后续管理端使用）
     */
    public SysUser register(String username, String rawPassword, String displayName, String studentNo) {
        if (userService.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER.name());
        user.setDisplayName(displayName);
        user.setStudentNo(studentNo);
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCreatedAt(LocalDateTime.now());
        userService.save(user);
        return user;
    }

    /**
     * 登录结果
     */
    public static class LoginResult {
        private final String token;
        private final SysUser user;

        public LoginResult(String token, SysUser user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public SysUser getUser() {
            return user;
        }
    }
}