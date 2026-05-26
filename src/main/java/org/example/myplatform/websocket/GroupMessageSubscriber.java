package org.example.myplatform.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.utils.RedisUtil;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component //扫描到这个注解，就会自动创建一个Bean，用于管理这个类，再将这个类的实例注入到其他类中，自动启动这个类
@RequiredArgsConstructor
/**
 * 群聊消息订阅器，被自动创建并启动
 * 负责监听Redis中的所有群聊消息频道，将消息分发给对应的WebSocket会话
 * 每个群聊频道对应一个WebSocket会话映射表，（channel -> userId -> WebSocketSession），用于存储该频道的所有在线用户的会话
 * 当Redis 发送群聊消息时，根据消息的频道和用户ID，将消息分发给对应的WebSocket会话
 */
public class GroupMessageSubscriber {

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisUtil redisUtil;

    // 本地存储：channel -> userId -> WebSocketSession
    private final Map<String, Map<Long, org.springframework.web.socket.WebSocketSession>> groupSessions = new ConcurrentHashMap<>();

    @PostConstruct // ← Bean 创建后自动执行
    public void init() {
        // 监听所有群聊频道
        listenerContainer.addMessageListener(
                new GroupMessageListener(),
                new PatternTopic("channel:group:*")
        );
        log.info("群聊消息订阅器初始化完成");
    }
    // 群聊消息监听器，负责监听Redis中的群聊消息频道，将消息分发给对应的WebSocket会话
    class GroupMessageListener implements MessageListener {
        /**
         * 处理群聊消息
         * @param message 收到的消息
         * @param pattern 匹配的频道模式
         */
        //在这里是将消息分发给对应的WebSocket会话
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            // 解析 groupId
            String groupIdStr = channel.replace("channel:group:", "");
            Long groupId = Long.parseLong(groupIdStr);

            // 获取该频道的所有在线用户
            Map<Long, org.springframework.web.socket.WebSocketSession> sessions = groupSessions.get(channel);
            // 遍历该频道的所有在线用户会话，将消息发送给每个会话
            if (sessions != null && !sessions.isEmpty()) {
                for (org.springframework.web.socket.WebSocketSession session : sessions.values()) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new org.springframework.web.socket.TextMessage(body));
                        } catch (Exception e) {
                            log.error("推送消息失败: {}", e.getMessage());
                        }
                    }
                }
            }
        }
    }

    // 添加用户到群聊频道
    public void addUserToGroup(Long userId, Long groupId, WebSocketSession session) {
        String channel = "channel:group:" + groupId;
        groupSessions.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
                     .put(userId, session);
        log.info("用户 {} 已加入群聊频道: groupId={}", userId, groupId);
    }

    // 移除用户从群聊频道
    public void removeUserFromGroup(Long userId, Long groupId) {
        String channel = "channel:group:" + groupId;
        Map<Long, org.springframework.web.socket.WebSocketSession> sessions = groupSessions.get(channel);
        if (sessions != null) {
            sessions.remove(userId);
            log.info("用户 {} 已离开群聊频道: groupId={}", userId, groupId);
        }
    }

    // 获取群聊频道的在线用户数
    public int getGroupOnlineCount(Long groupId) {
        String channel = "channel:group:" + groupId;
        Map<Long, org.springframework.web.socket.WebSocketSession> sessions = groupSessions.get(channel);
        return sessions != null ? sessions.size() : 0;
    }
}