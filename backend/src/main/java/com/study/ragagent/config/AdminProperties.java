package com.study.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 默认管理员配置 (app.default-admin.*)
 */
@Component
@ConfigurationProperties(prefix = "app.default-admin")
public class AdminProperties {

    /** 默认管理员用户名 */
    private String username = "admin";

    /** 默认管理员初始密码（BCrypt 加密后入库） */
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}