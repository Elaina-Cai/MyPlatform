package org.example.myplatform.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.myplatform.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate:limit:";
    private static final int MAX_REQUESTS_PER_MINUTE = 200;
    private static final int LOGIN_MAX_REQUESTS_PER_MINUTE = 10;
    private static final int WINDOW_SECONDS = 60;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);
        String path = request.getRequestURI();
        
        int maxRequests = isLoginOrRegister(path) ? LOGIN_MAX_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_MINUTE;
        
        if (!isAllowed(ip, maxRequests)) {
            throw new TooManyRequestsException("请求过于频繁，请稍后再试");
        }
        
        return true;
    }

    private boolean isAllowed(String ip, int maxRequests) {
        String key = RATE_LIMIT_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return false;
        if (count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        return count <= maxRequests;
    }

    private boolean isLoginOrRegister(String path) {
        return path.contains("/api/auth/login") || path.contains("/api/auth/register");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}