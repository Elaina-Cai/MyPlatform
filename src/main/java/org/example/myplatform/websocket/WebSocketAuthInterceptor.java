package org.example.myplatform.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.server.HandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.utils.JwtUtil;
import org.example.myplatform.utils.RedisUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;
/**
 * WebSocket 认证拦截器
 * <p>功能：在 WebSocket 握手阶段进行身份认证</p>
 * <p>工作流程：
 * <ol>
 *   <li>从请求头获取 token</li>
 *   <li>验证 token 有效性</li>
 *   <li>检查 token 是否在黑名单</li>
 *   <li>认证通过后，将 userId 存入 session attributes</li>
 * </ol>
 * </p>
 * <p>如果认证失败，握手会被拒绝，客户端无法建立 WebSocket 连接。</p>
 * <p>前端连接方式：
 * <pre>
 *   const ws = new WebSocket('ws://localhost:8080/ws/chat', [], {
 *     headers: { token: '用户token' }
 *   });
 * </pre>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes  // 关键：存入 session attributes
    ) throws Exception {

        // 1. 优先从请求头获取 token
        String token = request.getHeaders().getFirst("token");
        
        // 2. 如果请求头没有，从 query 参数获取
        if (token == null || token.isEmpty()) {
            String uri = request.getURI().toString();
            int tokenIndex = uri.indexOf("token=");
            if (tokenIndex != -1) {
                token = uri.substring(tokenIndex + 6);
                int ampIndex = token.indexOf('&');
                if (ampIndex != -1) {
                    token = token.substring(0, ampIndex);
                }
            }
        }
        
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手失败：token 为空");
            return false;
        }
        // 2. 验证 token
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("WebSocket 握手失败：token 无效");
            return false;
        }

        // 3. 验证 token 是否在黑名单
        if (redisUtil.isTokenBlacklisted(token)) {
            log.warn("WebSocket 握手失败：token 已被拉黑");
            return false;
        }

        // 4. 存入 attributes，后续 Handler 可以通过 session 获取
        attributes.put("userId", userId);
        attributes.put("token", token);

        log.info("WebSocket 握手成功：userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // 握手后一般不需要处理，这里可以添加日志记录等操作
    }
}