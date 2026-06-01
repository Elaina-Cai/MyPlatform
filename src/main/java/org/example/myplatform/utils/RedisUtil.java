package org.example.myplatform.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long activityTimeout;
    //常量
    private static final long DEFAULT_EXPIRE_HOURS = 1;
    private static final long RANDOM_BOUND_SECONDS = 300;

    public RedisUtil(StringRedisTemplate redisTemplate,
                     @Value("${auth.activity-timeout:1800000}") long activityTimeout) {
        this.redisTemplate = redisTemplate;
        this.activityTimeout = activityTimeout;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    // ========== 给其他实现类使用的基础操作相关操作 ==========
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // ========== JSON 缓存（防穿透） ==========
    //设置key->JSON值，过期时间为1小时+随机300秒
    public void setWithExpire(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long expireSeconds = DEFAULT_EXPIRE_HOURS * 3600 + (long) (Math.random() * RANDOM_BOUND_SECONDS);
            redisTemplate.opsForValue().set(key, json, expireSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }
    //获取key->value，找不到则返回null/NULL,找到则是返回value的clazz类型
    public <T> T get(String key, Class<T> clazz) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || isNullValue(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
    //设置key->NULL值，过期时间为1小时+随机300秒
    public void setNullValue(String key) {
        long expireSeconds = DEFAULT_EXPIRE_HOURS * 3600 + (long) (Math.random() * RANDOM_BOUND_SECONDS);
        redisTemplate.opsForValue().set(key, "NULL", expireSeconds, TimeUnit.SECONDS);
    }
    //判断value是否为NULL值
    public boolean isNullValue(String value) {
        return "NULL".equals(value);
    }

    // ========== 用户会话管理相关（登录状态控制） ==========
    //用户会话缓存键前缀
    private static final String USER_SESSION_PREFIX = "user:session:";
    private static final String FIELD_LAST_ACTIVITY = "lastActivity";
    private static final String FIELD_CURRENT_TOKEN = "currentToken";
    private static final String FIELD_STATUS = "status";
    
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_AWAY = "away";
    public static final String STATUS_OFFLINE = "offline";
    //离开状态的阈值：超过5分钟无活动转为离开
    private static final long AWAY_THRESHOLD_MILLIS = 5 * 60 * 1000;

    //保存用户会话数据
    public void saveUserSession(Long userId, String token, long timestamp) {
        String key = USER_SESSION_PREFIX + userId;
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put(FIELD_LAST_ACTIVITY, String.valueOf(timestamp));
        sessionData.put(FIELD_CURRENT_TOKEN, token);
        sessionData.put(FIELD_STATUS, STATUS_ONLINE);
        redisTemplate.opsForHash().putAll(key, sessionData);
        long ttlMillis = activityTimeout + 60000;
        redisTemplate.expire(key, ttlMillis, TimeUnit.MILLISECONDS);
    }

    //获取用户状态
    public String getUserStatus(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        Object status = redisTemplate.opsForHash().get(key, FIELD_STATUS);
        if (status == null) {
            return STATUS_OFFLINE;
        }
        return (String) status;
    }

    //设置用户状态
    public void setUserStatus(Long userId, String status) {
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.opsForHash().put(key, FIELD_STATUS, status);
        long ttlMillis = activityTimeout + 60000;
        redisTemplate.expire(key, ttlMillis, TimeUnit.MILLISECONDS);
    }

    //判断用户是否离开（超过30分钟无活动）
    public boolean isUserAway(Long userId) {
        Long lastActivity = getUserLastActivityTime(userId);
        if (lastActivity == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastActivity) > AWAY_THRESHOLD_MILLIS;
    }
    //更新用户会话数据
    public void updateUserActivity(Long userId, long timestamp) {
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.opsForHash().put(key, FIELD_LAST_ACTIVITY, String.valueOf(timestamp));
        redisTemplate.opsForHash().put(key, FIELD_STATUS, STATUS_ONLINE);
        long ttlMillis = activityTimeout + 60000;
        redisTemplate.expire(key, ttlMillis, TimeUnit.MILLISECONDS);
    }
    //更新用户最后活动时间（使用当前时间）
    public void updateUserLastActivity(Long userId) {
        updateUserActivity(userId, System.currentTimeMillis());
    }
    //获取用户最后活动时间
    public Long getUserLastActivityTime(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        Object value = redisTemplate.opsForHash().get(key, FIELD_LAST_ACTIVITY);
        return value == null ? null : Long.parseLong((String) value);
    }
    //获取当前用户token
    public String getCurrentUserToken(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        Object value = redisTemplate.opsForHash().get(key, FIELD_CURRENT_TOKEN);
        return value == null ? null : (String) value;
    }
    //删除用户会话数据
    public void deleteUserSession(Long userId) {
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.delete(key);
    }
    //更新用户token
    public void updateUserToken(Long userId, String token) {
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.opsForHash().put(key, FIELD_CURRENT_TOKEN, token);
        long ttlMillis = activityTimeout + 60000;
        redisTemplate.expire(key, ttlMillis, TimeUnit.MILLISECONDS);
    }
    //更新用户最后活动时间
    public void setUserLastActivityTime(Long userId, long timestamp) {
        updateUserActivity(userId, timestamp);
    }
    //更新当前用户token
    public void setCurrentUserToken(Long userId, String token) {
        updateUserToken(userId, token);
    }
    //删除用户会话数据
    public void deleteUserActivity(Long userId) {
        deleteUserSession(userId);
    }
    //删除当前用户token
       public void deleteCurrentUserToken(Long userId) {
        deleteUserSession(userId);
    }

    // ========== Token 黑名单 ==========
    //Token黑名单缓存键前缀
    private static final String TOKEN_BLACKLIST_PREFIX = "tokenBlacklist:";
    //添加Token到黑名单
    public void addTokenToBlacklist(String token, long ttlMillis) {
        String key = tokenBlacklistKey(token);
        redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }
    //判断Token是否在黑名单中
    public boolean isTokenBlacklisted(String token) {
        String key = tokenBlacklistKey(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    //生成Token黑名单缓存键
    private String tokenBlacklistKey(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return TOKEN_BLACKLIST_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ========== 用户信息缓存（用户资料缓存）Hash 缓存 ==========
    //用户信息缓存键前缀
    private static final String USER_PROFILE_PREFIX = "user:profile:";
    //设置用户资料（添加或更新用户资料都用这个就行了，因为用户资料是Hash缓存，putAll方法会自动更新）
    public void setUserProfile(Long userId, String nickName, String avatar) {
        String key = USER_PROFILE_PREFIX + userId;
        Map<String, String> profile = new HashMap<>();
        profile.put("nickname", nickName);
        profile.put("avatar", avatar);
        profile.put("_exists", "true");
        // TODO: 当前是抖动添加过期时间，雪崩时仍有大量请求打到DB；后续优化为逻辑过期：缓存永不过期，只存数据+过期时间，读取时判断过期则异步回源
        long randomSeconds = (long) (Math.random() * 6 * 3600);
        redisTemplate.opsForHash().putAll(key, profile);
        redisTemplate.expire(key, 7 * 24 * 3600 + randomSeconds, TimeUnit.SECONDS);
    }
    //获取用户资料
    public Map<String, String> getUserProfile(Long userId) {
        String key = USER_PROFILE_PREFIX + userId;
        Map<Object, Object> profile = redisTemplate.opsForHash().entries(key);
        if (profile.isEmpty() || "false".equals(profile.get("_exists"))) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        result.put("nickname", (String) profile.get("nickname"));
        result.put("avatar", (String) profile.get("avatar"));
        return result;
    }
    //设置用户资料为空（删除用户资料的时候用）
    public void setUserProfileNull(Long userId) {
        String key = USER_PROFILE_PREFIX + userId;
        Map<String, String> profile = new HashMap<>();
        profile.put("_exists", "false");
        redisTemplate.opsForHash().putAll(key, profile);
        // 防雪崩：7天 + 随机0~6小时抖动
        long randomSeconds = (long) (Math.random() * 6 * 3600);
        redisTemplate.expire(key, 7 * 24 * 3600 + randomSeconds, TimeUnit.SECONDS);
    }

    // ========== WebSocket 在线状态 ==========
    //WebSocket在线状态缓存键前缀
    private static final String WS_ONLINE_PREFIX = "ws:online:";
    //WebSocket会话缓存键前缀
    private static final String WS_SESSION_PREFIX = "ws:session:";
    //设置用户在线状态
       public void setUserOnline(Long userId, String sessionId) {
        redisTemplate.opsForValue().set(WS_ONLINE_PREFIX + userId, sessionId);
    }
    //获取用户会话ID
    public String getUserSessionId(Long userId) {
        return (String) redisTemplate.opsForValue().get(WS_ONLINE_PREFIX + userId);
    }
    //判断用户是否在线
    public boolean isUserOnline(Long userId) {
        return getUserSessionId(userId) != null;
    }
    //设置会话用户
    public void setSessionUser(String sessionId, Long userId) {
        redisTemplate.opsForValue().set(WS_SESSION_PREFIX + sessionId, String.valueOf(userId));
    }
    //根据会话ID获取用户ID
    public Long getUserIdBySessionId(String sessionId) {
        String value = (String) redisTemplate.opsForValue().get(WS_SESSION_PREFIX + sessionId);
        return value != null ? Long.parseLong(value) : null;
    }
    //删除用户在线状态
    public void removeUserOnline(Long userId) {
        String sessionId = getUserSessionId(userId);
        redisTemplate.delete(WS_ONLINE_PREFIX + userId);
        if (sessionId != null) {
            redisTemplate.delete(WS_SESSION_PREFIX + sessionId);
        }
    }

    // ========== Redis Pub/Sub 群聊消息推送 ==========
    //群聊消息推送缓存键前缀
    private static final String GROUP_CHANNEL_PREFIX = "channel:group:";
    //订阅群聊消息
    public void subscribeGroup(Long userId, Long groupId) {
        String channel = GROUP_CHANNEL_PREFIX + groupId;
        redisTemplate.convertAndSend(channel, "{\"type\":\"subscribe\",\"userId\":" + userId + ",\"groupId\":" + groupId + "}");
    }
    //发布群聊消息
    public void publishGroupMessage(Long groupId, String message) {
        String channel = GROUP_CHANNEL_PREFIX + groupId;
        redisTemplate.convertAndSend(channel, message);
    }
}