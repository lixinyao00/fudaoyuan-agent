package com.study.ragagent.config;

import com.study.ragagent.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化默认管理员账号（admin）
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final AuthService authService;

    public AdminUserInitializer(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            authService.createDefaultAdmin();
        } catch (Exception e) {
            log.warn("默认管理员初始化失败（不影响启动）: {}", e.getMessage());
        }
    }
}