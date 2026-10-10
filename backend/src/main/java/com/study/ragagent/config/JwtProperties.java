package com.study.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置 (app.jwt.*)
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 签名密钥（需 >= 32 字节） */
    private String secret;

    /** token 有效期（小时） */
    private long expirationHours = 24;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationHours() {
        return expirationHours;
    }

    public void setExpirationHours(long expirationHours) {
        this.expirationHours = expirationHours;
    }
}