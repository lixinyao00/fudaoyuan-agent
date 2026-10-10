package com.study.ragagent.util;

import com.study.ragagent.config.JwtProperties;
import com.study.ragagent.domain.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具类（HS256）
 * 密钥/过期时间从 app.jwt.* 读取
 */
@Component
public class JwtUtil {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为用户生成 token
     */
    public String generateToken(SysUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationHours() * 3600L);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，返回 Claims；无效或过期返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Claims 取用户 ID
     */
    public Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Claims 取角色
     */
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * 从 Claims 取用户名
     */
    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }
}