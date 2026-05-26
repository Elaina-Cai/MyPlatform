package org.example.myplatform.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>当前策略：JWT <b>不包含 exp</b>，会话是否有效完全由 Redis 最后活跃时间 +
 * {@code auth.activity-timeout}（拦截器）决定。JWT 仅作带签名的 userId 载体。</p>
 *
 * <p>{@code jwt.blacklist-retention-ms}：登出或顶号时，被废弃的 token 写入黑名单后在 Redis 中的 TTL。</p>
 */
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 环境变量未设置，请配置后再启动");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 长度至少 32 字符，请使用 openssl rand -base64 64 生成");
        }
    }

    /**
     * 被登出/顶号替换的旧 token 在黑名单中的保留时间（毫秒）。与 JWT 是否过期无关。
     */
    @Value("${jwt.blacklist-retention-ms}")
    private long blacklistRetentionMs;

    /**
     * 生成 JWT（无 exp；会话由 Redis 活跃时间控制）
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * 校验签名是否有效（不校验 exp，因签发时不写入 exp）
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 登出 / 顶号时写入黑名单的 TTL（毫秒） */
    public long getBlacklistRetentionMillis() {
        return blacklistRetentionMs;
    }
}