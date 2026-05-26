package org.example.myplatform.interceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.utils.JwtUtil;
import org.example.myplatform.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
/**
 * JWT 拦截器
 *
 * 功能：
 * - 验证请求中的 token 是否有效
 * - 实现心跳机制（30分钟无操作则需重新登录）
 * - 将已验证的用户ID传递给后续处理
 *
 * 执行顺序（每一步失败都会抛出 UnauthorizedException）：
 * 1. 从请求头获取 token
 * 2. 验证 token 签名（JWT 不含 exp，会话由 Redis 活跃时间控制）
 * 3. 检查 token 是否在黑名单
 * 4. 检查用户是否超过30分钟无操作
 * 5. 通过所有检查，更新活跃时间，放行
 *
 * 异常由 GlobalExceptionHandler 统一处理，返回 JSON
 *
 * 拦截路径：在 WebConfig 中配置
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    /**
     * 从 JWT 中提取的用户ID属性名
     */
    public static final String USER_ID_ATTR = "userId";
    /**
     * 从请求头中获取 token 的 key
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * token 前缀（Bearer xxx）
     */
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 存储在 request 中的 userId 属性名
     * 后续 Controller 可通过 request.getAttribute("userId") 获取
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    /**
     * 用户无操作超时时间（毫秒）
     * 30分钟 = 30 * 60 * 1000 = 1800000
     */
    private final long activityTimeout;
    /**
     * 构造器注入
     */
    public JwtInterceptor(
            JwtUtil jwtUtil,
            RedisUtil redisUtil,
            @Value("${auth.activity-timeout}") long activityTimeout) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.activityTimeout = activityTimeout;
    }
    /**
     * 拦截器主方法
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ========== 验证逻辑（失败则抛异常） ==========
        //对 OPTIONS 方法直接放行，不做 token 校验
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        // 步骤1：从请求头获取 token
        String token = extractToken(request);
        if (token == null) {
            throw new UnauthorizedException("未携带 token");
        }
        // 步骤2：验证 token 签名（无 JWT 日历过期，由 Redis 活跃时间约束会话）
        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("token 无效或已损坏");
        }
        // 步骤3：检查 token 是否在黑名单
        if (redisUtil.isTokenBlacklisted(token)) {
            throw new UnauthorizedException("token 已失效，请重新登录");
        }
        // 步骤4：从 token 解析出 userId
        Long userId = jwtUtil.getUserIdFromToken(token);
        // 步骤5：检查用户是否超过30分钟无操作
        Long lastActivityTime = redisUtil.getUserLastActivityTime(userId);
        if (lastActivityTime == null) {
            throw new UnauthorizedException("会话已过期，请重新登录");
        }
        long now = System.currentTimeMillis();
        if (lastActivityTime + activityTimeout < now) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        // 步骤6：校验 token 是否为该用户当前有效的 token（防顶号的人能继续访问）
        String currentToken = redisUtil.getCurrentUserToken(userId);
        if (currentToken == null || !currentToken.equals(token)) {
            throw new UnauthorizedException("您的账号已在其他设备登录，请重新登录");
        }
        // 步骤7：通过所有检查，更新活跃时间（心跳续命）
        redisUtil.setUserLastActivityTime(userId, now);
        // 将 userId 存入 request 属性，供后续 Controller 使用
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return true;
    }
    /**
     * 从请求头中提取 token
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}