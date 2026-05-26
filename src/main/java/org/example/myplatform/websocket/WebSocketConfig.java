package org.example.myplatform.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * <p>功能：添加 WebSocket 处理器和拦截器</p>
 * <p>工作流程：
 * <ol>
 *   <li>注册 MessageWebSocketHandler 处理器</li>
 *   <li>添加 WebSocketAuthInterceptor 拦截器</li>
 *   <li>允许所有来源的 WebSocket 连接</li>
 * </ol>
 * </p>
 * <p>注：WebSocketAuthInterceptor 用于验证 WebSocket 连接的 token，确保只有认证通过的用户才能连接。</p>
 * <p>前端连接方式：
 * <pre>
 *   const ws = new WebSocket('ws://localhost:8080/ws/chat', [], {
 *     headers: { token: '用户token' }
 *   });
 * </pre>
 * </p>
 * <p>注：WebSocket 连接路径为 /ws/chat</p>
 * <p>注：WebSocket 连接时需要在 headers 中包含 token，否则会拒绝连接。</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketHandler, "/ws/chat")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}