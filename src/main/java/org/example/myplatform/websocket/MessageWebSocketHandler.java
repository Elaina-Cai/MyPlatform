package org.example.myplatform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.dto.chatgroup.SendMessageRequest;
import org.example.myplatform.dto.friend.FriendRequest;
import org.example.myplatform.entity.User;
import org.example.myplatform.entity.chatgroup.ChatGroup;
import org.example.myplatform.entity.chatgroup.ChatGroupMember;
import org.example.myplatform.event.GroupMemberAddedEvent;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.mapper.UserMapper;
import org.example.myplatform.mapper.chatgroup.ChatGroupMapper;
import org.example.myplatform.mapper.chatgroup.ChatGroupMemberMapper;
import org.example.myplatform.service.friend.FriendService;
import org.example.myplatform.service.friend.MessageService;
import org.example.myplatform.service.chatgroup.ChatGroupService;
import org.example.myplatform.utils.RedisUtil;
import org.example.myplatform.utils.XssUtil;
import org.example.myplatform.vo.chatgroup.ChatGroupMessageVO;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final RedisUtil redisUtil;
    private final XssUtil xssUtil;
    private final FriendService friendService;
    private final MessageService messageService;
    private final ChatGroupService chatGroupService;
    private final UserMapper userMapper;
    private final ChatGroupMapper chatGroupMapper;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final GroupMessageSubscriber groupMessageSubscriber;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用Map存储 sessionId → WebSocketSession 的映射
    private final Map<String, WebSocketSession> onlineSessions = new ConcurrentHashMap<>();
    // 用Map存储 userId → 所在群聊列表（用于断线清理）
    private final Map<Long, List<Long>> userGroups = new ConcurrentHashMap<>();
    // 用Map存储 userId → 当前状态（用于避免重复推送）
    private final Map<Long, String> userStatusCache = new ConcurrentHashMap<>();
    //上次状态检测时间
    private long lastCheckTime = 0;

    public MessageWebSocketHandler(RedisUtil redisUtil, XssUtil xssUtil, FriendService friendService,
                                   MessageService messageService, ChatGroupService chatGroupService,
                                   UserMapper userMapper, ChatGroupMapper chatGroupMapper,
                                   ChatGroupMemberMapper chatGroupMemberMapper,
                                   GroupMessageSubscriber groupMessageSubscriber) {
        this.redisUtil = redisUtil;
        this.xssUtil = xssUtil;
        this.friendService = friendService;
        this.messageService = messageService;
        this.chatGroupService = chatGroupService;
        this.userMapper = userMapper;
        this.chatGroupMapper = chatGroupMapper;
        this.chatGroupMemberMapper = chatGroupMemberMapper;
        this.groupMessageSubscriber = groupMessageSubscriber;
    }
    // 成员加入事件 的事件监听器
    // 监听群成员加入事件
    // 当群成员加入群聊时，会触发该方法
    // 这个方法只监听 GroupMemberAddedEvent 类型的事件。
    // 如果发布其他事件（如 UserRegisteredEvent），这个方法不会被调用。
    @EventListener
    public void handleGroupMemberAdded(GroupMemberAddedEvent event) {
        String pushMsg = String.format(
            "{\"type\":\"group_member_added\",\"groupId\":%d,\"userId\":%d,\"nickname\":\"%s\",\"avatar\":\"%s\"}",
            //从事件中获取各种数据：群聊ID、用户ID、昵称、头像
            event.getGroupId(), event.getUserId(),
            event.getNickname().replace("\"", "\\\""),
            event.getAvatar() != null ? event.getAvatar().replace("\"", "\\\"") : ""
        );
        List<ChatGroupMember> members = chatGroupMemberMapper.selectByGroupId(event.getGroupId());
        for (ChatGroupMember member : members) {
            sendToUser(member.getUserId(), pushMsg);
        }
    }
    //websocket连接建立时
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 从 session 属性获取 userId（拦截器已设置）
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close();
            return;
        }
        // 2. 存 Redis：userId → sessionId
        redisUtil.setUserOnline(userId, session.getId());
        log.info("WebSocket 连接: userId={}, sessionId={}", userId, session.getId());
        // 3. 立即存入本地 Map（必须在推送消息之前）
        onlineSessions.put(session.getId(), session);
        log.info("当前在线会话数量: {}", onlineSessions.size());
        // 4. 存 Redis：sessionId → userId（反向索引）
        redisUtil.setSessionUser(session.getId(), userId);
        log.info("连接建立后，userId={} 的 lastActivity={}, isAway={}, status={}",
            userId, redisUtil.getUserLastActivityTime(userId),
            redisUtil.isUserAway(userId), redisUtil.getUserStatus(userId));
        // 4. 确保用户信息已缓存到 Redis
        // TODO: 高并发下可能短暂不一致
        if (redisUtil.getUserProfile(userId) == null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                // TODO: 缓存回填时若并发更新，可能导致旧值覆盖新值
                redisUtil.setUserProfile(userId, user.getNickname(), user.getAvatar());
            } else {
                // 防穿透：缓存不存在标记
                redisUtil.setUserProfileNull(userId);
            }
        }
        // 4. 查询好友列表
        List<Long> friendIds = friendService.getFriendIds(userId);

        // 5. 推送给每个在线好友，告知自己的状态
        // 注意：连接时强制推送 online，不读取 Redis 中的旧状态（可能是 away）
        String myNotifyType = "friend_online";
        String myStatusNotify = "{\"type\":\"" + myNotifyType + "\",\"friendId\":" + userId + "}";
        log.info("用户 {} 推送自己状态给好友: {}, 在线好友数: {}", userId, myNotifyType, friendIds.size());
        for (Long friendId : friendIds) {
            String friendSessionId = redisUtil.getUserSessionId(friendId);
            if (friendSessionId != null) {
                sendToUser(friendId, myStatusNotify);
            }
        }

        // 6. 向用户本人推送所有在线好友的状态
        for (Long friendId : friendIds) {
            String friendSessionId = redisUtil.getUserSessionId(friendId);
            if (friendSessionId != null) {
                // 获取好友状态：优先从缓存获取，缓存没有则从 Redis 获取
                String friendCachedStatus = userStatusCache.get(friendId);
                String friendStatus = friendCachedStatus != null ? friendCachedStatus : redisUtil.getUserStatus(friendId);
                // 如果缓存和 Redis 都没有状态，默认设为在线
                if (friendStatus == null || RedisUtil.STATUS_OFFLINE.equals(friendStatus)) {
                    friendStatus = RedisUtil.STATUS_ONLINE;
                }
                String friendNotifyType = RedisUtil.STATUS_AWAY.equals(friendStatus) ? "friend_away" : "friend_online";
                String friendStatusNotify = "{\"type\":\"" + friendNotifyType + "\",\"friendId\":" + friendId + "}";
                sendToUser(userId, friendStatusNotify);
            }
        }
        // 7. 推送初始状态加载完成标志
        sendToUser(userId, "{\"type\":\"friends_status_ready\"}");

        // 8. 订阅用户所在的所有群聊频道
        List<Long> groupIds = chatGroupService.getMyGroups(userId).stream()
                .map(g -> g.getId())
                .toList();
        userGroups.put(userId, groupIds);
        if (groupMessageSubscriber != null) {
            for (Long groupId : groupIds) {
                groupMessageSubscriber.addUserToGroup(userId, groupId, session);
            }
            log.info("用户 {} 已订阅 {} 个群聊频道", userId, groupIds.size());
        }
    }
    //=====收到消息时=====
    @Override
    //handleTextMessage是TextWebSocketHandler里的方法，这里重写
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 更新用户活跃时间
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            redisUtil.updateUserLastActivity(userId);
        }

        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();
        if ("chat".equals(type)) {
            handleChatMessage(json, session);
        } else if ("group_chat".equals(type)) {
            handleGroupChatMessage(json, session);
        } else if ("subscribe_group".equals(type)) {
            handleSubscribeGroup(json, session);
        } else if ("unsubscribe_group".equals(type)) {
            handleUnsubscribeGroup(json, session);
        } else if ("friend_apply".equals(type)) {
            handleFriendApply(json, session);
        } else if ("accept_friend".equals(type)) {
            handleAcceptFriend(json, session);
        } else if ("reject_friend".equals(type)) {
            handleRejectFriend(json, session);
        } else if ("group_invite".equals(type)) {
            handleGroupInvite(json, session);
        }
    }
    private void handleChatMessage(JsonNode json, WebSocketSession session) throws Exception {
        // 1. 从 session 获取发送者 ID
        Long senderId = (Long) session.getAttributes().get("userId");

        // 2. 获取参数
        Long receiverId = json.get("receiverId").asLong();
        String content = json.has("content") && !json.get("content").isNull() ? json.get("content").asText() : "";
        String fileUrl = json.has("fileUrl") && !json.get("fileUrl").isNull() ? json.get("fileUrl").asText() : null;
        String fileType = json.has("fileType") && !json.get("fileType").isNull() ? json.get("fileType").asText() : null;

        log.info("收到私聊消息: senderId={}, receiverId={}, content={}", senderId, receiverId, content);

        // 3. 保存消息到数据库
        messageService.saveMessage(senderId, receiverId, content, fileUrl, fileType);

        // 4. 获取发送者信息（从 Redis）
        // TODO: 高并发下可能短暂不一致
        Map<String, String> profile = redisUtil.getUserProfile(senderId);
        String senderNickname;
        String senderAvatar;
        if (profile != null) {
            senderNickname = xssUtil.escapeHtml(profile.get("nickname"));
            senderAvatar = profile.get("avatar");
        } else {
            // Redis 未命中，查数据库并回填
            User sender = userMapper.selectById(senderId);
            if (sender != null) {
                senderNickname = xssUtil.escapeHtml(sender.getNickname());
                senderAvatar = sender.getAvatar();
                // TODO: 缓存回填时若并发更新，可能导致旧值覆盖新值
                redisUtil.setUserProfile(senderId, senderNickname, senderAvatar);
            } else {
                senderNickname = "未知用户";
                senderAvatar = "";
                // 防穿透：缓存不存在标记
                redisUtil.setUserProfileNull(senderId);
            }
        }

        // 5. 判断接收者是否在线
        String receiverSessionId = redisUtil.getUserSessionId(receiverId);
        if (receiverSessionId != null) {
            // 6. 在线 → 推送消息给接收者（包含完整信息）
            String pushMessage = "{\"type\":\"chat\",\"senderId\":" + senderId +
                    ",\"senderNickname\":\"" + xssUtil.escapeHtml(senderNickname) + "\"" +
                    ",\"senderAvatar\":\"" + senderAvatar + "\"" +
                    ",\"content\":\"" + xssUtil.escapeHtml(content) + "\"" +
                    (fileUrl != null ? ",\"fileUrl\":\"" + fileUrl + "\"" : "") +
                    (fileType != null ? ",\"fileType\":\"" + fileType + "\"" : "") +
                    "}";
            sendToUser(receiverId, pushMessage);
        }
    }

    //=====群聊消息处理=====作用：处理用户发送的群聊消息，调用方法来保存到数据库 和 发布该消息到群聊频道
    private void handleGroupChatMessage(JsonNode json, WebSocketSession session) throws Exception {
        // 1. 从 session 获取发送者 ID
        Long senderId = (Long) session.getAttributes().get("userId");
        // 更新用户活跃时间
        redisUtil.updateUserLastActivity(senderId);
        
        Long groupId = json.get("groupId").asLong();
        String content = json.get("content").asText();
        int messageType = json.has("messageType") ? json.get("messageType").asInt() : 1;
        String fileUrl = json.has("fileUrl") && !json.get("fileUrl").isNull() ? json.get("fileUrl").asText() : null;
        String fileType = json.has("fileType") && !json.get("fileType").isNull() ? json.get("fileType").asText() : null;

        log.info("收到群聊消息: senderId={}, groupId={}, content={}", senderId, groupId, content);

        // 2. 这里是在调用 ChatGroupService的 saveGroupMessage方法保存消息
        //先声明一个ChatGroupMessageVO对象，用于存储保存的消息信息，下面会赋值
        ChatGroupMessageVO msgVO;
        try {
            SendMessageRequest request = new SendMessageRequest();
            request.setGroupId(groupId);
            request.setContent(content);
            request.setMessageType(messageType);
            request.setFileUrl(fileUrl);
            request.setFileType(fileType);
            //调用 ChatGroupService的 saveGroupMessage方法保存消息
            msgVO = chatGroupService.saveGroupMessage(senderId, request);
        } catch (org.example.myplatform.exception.BadRequestException e) {
            // 被禁言了等错误，推送错误提示给发送者
            String errorMsg = "{\"type\":\"group_error\",\"groupId\":" + groupId +
                    ",\"error\":\"" + xssUtil.escapeHtml(e.getMessage()) + "\"}";
            session.sendMessage(new TextMessage(errorMsg));
            return;
        }

        // 3. 通过 Redis Pub/Sub 发布消息到群聊频道
        String pushMessage = "{\"type\":\"group_message\",\"groupId\":" + groupId +
                ",\"messageId\":" + msgVO.getId() +
                ",\"senderId\":" + senderId +
                ",\"senderNickname\":\"" + xssUtil.escapeHtml(msgVO.getSenderNickname()) + "\"" +
                ",\"senderAvatar\":\"" + (msgVO.getSenderAvatar() != null ? msgVO.getSenderAvatar() : "") + "\"" +
                ",\"content\":\"" + xssUtil.escapeHtml(content) + "\"" +
                ",\"messageType\":" + messageType +
                ",\"fileUrl\":\"" + (msgVO.getFileUrl() != null ? msgVO.getFileUrl() : "") + "\"" +
                ",\"fileType\":\"" + (msgVO.getFileType() != null ? msgVO.getFileType() : "") + "\"" +
                ",\"createdAt\":\"" + msgVO.getCreatedAt() + "\"}";

        redisUtil.publishGroupMessage(groupId, pushMessage);
        log.info("群聊消息已发布到频道: groupId={}, messageId={}", groupId, msgVO.getId());
    }

    private void handleSubscribeGroup(JsonNode json, WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Long groupId = json.get("groupId").asLong();
        if (groupMessageSubscriber != null) {
            groupMessageSubscriber.addUserToGroup(userId, groupId, session);
            log.info("用户 {} 订阅群聊频道: groupId={}", userId, groupId);
        }
    }

    private void handleUnsubscribeGroup(JsonNode json, WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Long groupId = json.get("groupId").asLong();
        if (groupMessageSubscriber != null) {
            groupMessageSubscriber.removeUserFromGroup(userId, groupId);
        }
    }
    
    /**
     * 发送消息给指定用户
     */
    public void sendToUser(Long userId, String message) {
        String sessionId = redisUtil.getUserSessionId(userId);

        if (sessionId == null) {
            return;
        }

        WebSocketSession targetSession = onlineSessions.get(sessionId);
        if (targetSession == null) {
            return;
        }

        if (targetSession.isOpen()) {
            try {
                targetSession.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.warn("消息发送失败: userId={}, sessionId={}", userId, sessionId, e);
            }
        }
    }

    // 服务器通过socket连接 收到 添加XXX为好友的申请 之后，调用 FriendService的 sendApply方法推送好友申请通知
    private void handleFriendApply(JsonNode json, WebSocketSession session) throws Exception {
        Long senderId = (Long) session.getAttributes().get("userId");
        Long targetUserId = json.get("targetUserId").asLong();
        String message = json.has("message") ? json.get("message").asText() : "";

        FriendRequest request = new FriendRequest();
        request.setTargetUserId(targetUserId);
        request.setMessage(message);

        try {
            friendService.sendApply(senderId, request);
            // 推送成功通知给申请者
            String successMsg = "{\"type\":\"friend_apply_success\"}";
            session.sendMessage(new TextMessage(successMsg));
            // 推送好友申请通知给目标用户
            String pushMsg = "{\"type\":\"friend_apply\",\"fromUserId\":" + senderId + "}";
            sendToUser(targetUserId, pushMsg);
        } catch (BadRequestException e) {
            // 推送错误给申请者
            String errorMsg = "{\"type\":\"friend_apply_error\",\"error\":\"" + xssUtil.escapeHtml(e.getMessage()) + "\"}";
            session.sendMessage(new TextMessage(errorMsg));
        }
    }

    // 处理同意好友申请
    private void handleAcceptFriend(JsonNode json, WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Long fromUserId = json.get("fromUserId").asLong();

        try {
            friendService.acceptApply(userId, fromUserId);
            // 通知双方
            String notify = "{\"type\":\"friend_accepted\",\"userId\":" + userId + "}";
            sendToUser(fromUserId, notify);
            sendToUser(userId, "{\"type\":\"friend_accepted\",\"userId\":" + fromUserId + "}");
        } catch (BadRequestException e) {
            String errorMsg = "{\"type\":\"friend_error\",\"error\":\"" + xssUtil.escapeHtml(e.getMessage()) + "\"}";
            session.sendMessage(new TextMessage(errorMsg));
        }
    }

    // 处理拒绝好友申请
    private void handleRejectFriend(JsonNode json, WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        Long fromUserId = json.get("fromUserId").asLong();

        try {
            friendService.rejectApply(userId, fromUserId);
            // 通知被拒绝的人
            String notify = "{\"type\":\"friend_rejected\",\"userId\":" + userId + "}";
            sendToUser(fromUserId, notify);
        } catch (BadRequestException e) {
            String errorMsg = "{\"type\":\"friend_error\",\"error\":\"" + xssUtil.escapeHtml(e.getMessage()) + "\"}";
            session.sendMessage(new TextMessage(errorMsg));
        }
    }

    // 处理群聊邀请
    private void handleGroupInvite(JsonNode json, WebSocketSession session) throws Exception {
        Long inviterId = (Long) session.getAttributes().get("userId");
        Long groupId = json.get("groupId").asLong();
        Long friendId = json.get("friendId").asLong();

        try {
            chatGroupService.inviteFriend(inviterId, groupId, friendId);
            
            // 获取邀请人信息用于推送
            User inviter = userMapper.selectById(inviterId);
            String inviterNickname = inviter != null && inviter.getNickname() != null 
                ? inviter.getNickname() : (inviter != null ? inviter.getUsername() : "未知用户");
            String inviterAvatar = inviter != null ? inviter.getAvatar() : "";
            
            // 获取群信息
            ChatGroup group = chatGroupMapper.selectById(groupId);
            String groupName = group != null ? group.getName() : "群聊";
            
            // 推送邀请消息给被邀请人（格式与普通私聊消息一致）
            String pushMsg = String.format(
                "{\"type\":\"chat\",\"senderId\":%d,\"senderNickname\":\"%s\",\"senderAvatar\":\"%s\",\"content\":\"邀请你加入群聊：%s\",\"groupId\":%d,\"groupName\":\"%s\",\"typeId\":1}",
                inviterId, inviterNickname.replace("\"", "\\\""), inviterAvatar != null ? inviterAvatar.replace("\"", "\\\"") : "", 
                groupName.replace("\"", "\\\""), groupId, groupName.replace("\"", "\\\"")
            );
            sendToUser(friendId, pushMsg);

            // 推送成功通知给邀请人
            String successMsg = "{\"type\":\"group_invite_success\",\"groupId\":" + groupId + "}";
            session.sendMessage(new TextMessage(successMsg));
        } catch (BadRequestException e) {
            String errorMsg = "{\"type\":\"group_invite_error\",\"error\":\"" + xssUtil.escapeHtml(e.getMessage()) + "\"}";
            session.sendMessage(new TextMessage(errorMsg));
        }
    }

    //断开连接时
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 连接断开: sessionId={}, status={}", session.getId(), status);
        // 1. 获取 userId
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        // 2. 从本地 Map 移除（必须在心跳检测之前，避免竞态条件）
        onlineSessions.remove(session.getId());
        userStatusCache.remove(userId);
        // 3. 取消订阅群聊频道
        List<Long> groupIds = userGroups.remove(userId);
        if (groupIds != null && groupMessageSubscriber != null) {
            for (Long groupId : groupIds) {
                groupMessageSubscriber.removeUserFromGroup(userId, groupId);
            }
        }
        // 4. 从 Redis 清理在线状态
        redisUtil.removeUserOnline(userId);
        // 5. 查询好友列表
        List<Long> friendIds = friendService.getFriendIds(userId);
        // 6. 推送给好友
        String offlineNotify = "{\"type\":\"friend_offline\",\"friendId\":" + userId + "}";
        for (Long friendId : friendIds) {
            if (redisUtil.isUserOnline(friendId)) {
                sendToUser(friendId, offlineNotify);
            }
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
        }
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        PingMessage ping = new PingMessage(ByteBuffer.wrap(new byte[]{1}));
        int sentCount = 0;
        for (WebSocketSession session : onlineSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(ping);
                    sentCount++;
                } catch (IOException e) {
                    log.warn("心跳发送失败: sessionId={}", session.getId());
                }
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < 10000) return;
        lastCheckTime = now;

        log.info("心跳检测开始");
        checkUserStatus();
    }

    private void checkUserStatus() {
        for (Map.Entry<String, WebSocketSession> entry : onlineSessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            // 跳过已关闭的 session
            if (!session.isOpen()) continue;

            Long userId = redisUtil.getUserIdBySessionId(sessionId);
            if (userId == null) continue;

            Long lastActivity = redisUtil.getUserLastActivityTime(userId);
            boolean isAway = redisUtil.isUserAway(userId);
            String newStatus = isAway ? RedisUtil.STATUS_AWAY : RedisUtil.STATUS_ONLINE;
            String cachedStatus = userStatusCache.get(userId);

            if (cachedStatus == null) {
                // 首次检测：设置缓存和 Redis 状态
                userStatusCache.put(userId, newStatus);
                redisUtil.setUserStatus(userId, newStatus);
                notifyFriendsStatusChange(userId, newStatus);
                continue;
            }

            // 状态变化时同步到 Redis 并通知好友
            // 注意：不去更新 lastActivity，避免覆盖消息处理中的更新时间
            if (!newStatus.equals(cachedStatus)) {
                userStatusCache.put(userId, newStatus);
                redisUtil.setUserStatus(userId, newStatus);
                notifyFriendsStatusChange(userId, newStatus);
            }
        }
    }
    
    private void notifyFriendsStatusChange(Long userId, String newStatus) {
        List<Long> friendIds = friendService.getFriendIds(userId);
        String notifyType = RedisUtil.STATUS_AWAY.equals(newStatus) ? "friend_away" : "friend_online";
        String notifyMsg = "{\"type\":\"" + notifyType + "\",\"friendId\":" + userId + "}";
        for (Long friendId : friendIds) {
            if (redisUtil.isUserOnline(friendId)) {
                sendToUser(friendId, notifyMsg);
            }
        }
    }

}