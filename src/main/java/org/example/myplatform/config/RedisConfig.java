package org.example.myplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration//这个注解可以将 RedisConfig 类标记为一个 Spring 配置类，Spring 会自动扫描并加载这个类
/**
 * Redis 配置类
 * 目前负责配置 Redis的消息监听容器（因为Redis Pub/Sub 需要一个监听容器来接收 Redis 发来的消息）
 * 消息监听容器用于监听 Redis 中的群聊消息频道，将消息分发给对应的 WebSocket会话
 */
//Redis Pub/Sub 监听原理是：
// 消费端订阅一个频道，当有消息发布到这个频道时，消费端就会收到这个消息，然后根据消息的内容，将消息分发给对应的 WebSocket会话
// 这里配置的 RedisMessageListenerContainer 就是消费端，订阅的频道就是群聊消息频道
// 消息监听容器会自动监听 Redis 中的群聊消息频道，当有消息发布到这个频道时，消费端就会收到这个消息，然后根据消息的内容，将消息分发给对应的 WebSocket会话
public class RedisConfig {
    /**
     * 这个方法做的事：配置 Redis 消息监听容器
     * @param connectionFactory Redis连接工厂
     * @return Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

}