package org.example.myplatform.service.friend.impl;

import lombok.RequiredArgsConstructor;
import org.example.myplatform.entity.Message;
import org.example.myplatform.entity.User;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.mapper.FriendMapper;
import org.example.myplatform.mapper.MessageMapper;
import org.example.myplatform.mapper.UserMapper;
import org.example.myplatform.service.friend.MessageService;
import org.example.myplatform.vo.MessageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.util.*;

/**
 * 好友聊天消息服务实现类
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final org.example.myplatform.utils.RedisUtil redisUtil;

    @Override
    @Transactional
    public void saveMessage(Long senderId, Long receiverId, String content) {
        saveMessage(senderId, receiverId, content, null, null);
    }

    @Override
    @Transactional
    public void saveMessage(Long senderId, Long receiverId, String content, String fileUrl, String fileType) {
        // 1. 检查好友关系
        int friendshipCount = friendMapper.checkFriendship(senderId, receiverId);
        if (friendshipCount == 0) {
            throw new BadRequestException("你们还不是好友，无法发送消息");
        }

        // 2. 写扩散：消息存入双方收件箱，各存一份
        List<Message> messages = new ArrayList<>();

        // 3. 生成消息记录
        LocalDateTime now = LocalDateTime.now(); //当前时间
        // A 的收件箱（显示 A 发出的消息）
        Message msgToSender = new Message();
        msgToSender.setSenderId(senderId);
        msgToSender.setUserId(senderId);      // 收件人是发送者自己
        msgToSender.setFriendId(receiverId);  // 对方是接收者
        msgToSender.setContent(content);
        msgToSender.setFileUrl(fileUrl);
        msgToSender.setFileType(fileType);
        msgToSender.setIsRead(1);  // 自己发的，已读
        msgToSender.setCreatedAt(now); // 创建时间是当前时间
        messages.add(msgToSender);

        // B 的收件箱（显示 B 收到的消息）
        Message msgToReceiver = new Message();
        msgToReceiver.setSenderId(senderId);
        msgToReceiver.setUserId(receiverId);   // 收件人是接收者
        msgToReceiver.setFriendId(senderId);   // 对方是发送者
        msgToReceiver.setContent(content);
        msgToReceiver.setFileUrl(fileUrl);
        msgToReceiver.setFileType(fileType);
        msgToReceiver.setIsRead(0);  // 对方还没看，未读
        msgToReceiver.setCreatedAt(now); // 创建时间是当前时间
        messages.add(msgToReceiver);

        messageMapper.insertBatch(messages);
    }

    @Override
    public List<MessageVO> getChatHistory(Long userId, Long friendId) {
        // 1. 查询消息列表
        List<Message> messages = messageMapper.selectChatHistory(userId, friendId);

        // 2. 收集所有发送者 ID
        Set<Long> senderIds = new HashSet<>();
        for (Message msg : messages) {
            senderIds.add(msg.getSenderId());
        }

        // 3. 批量从 Redis 获取用户信息
        Map<Long, Map<String, String>> profiles = new HashMap<>();
        for (Long senderId : senderIds) {
            // TODO: 高并发下可能短暂不一致
            Map<String, String> profile = redisUtil.getUserProfile(senderId);
            if (profile != null) {
                profiles.put(senderId, profile);
            } else {
                // Redis 未命中，查数据库并回填
                User user = userMapper.selectById(senderId);
                if (user != null) {
                    // TODO: 缓存回填时若并发更新，可能导致旧值覆盖新值
                    redisUtil.setUserProfile(senderId, user.getNickname(), user.getAvatar());
                    profile = new HashMap<>();
                    profile.put("nickname", user.getNickname());
                    profile.put("avatar", user.getAvatar());
                    profiles.put(senderId, profile);
                } else {
                    // 防穿透：缓存不存在标记
                    redisUtil.setUserProfileNull(senderId);
                }
            }
        }

        // 4. 组装 VO
        List<MessageVO> result = new ArrayList<>();
        for (Message msg : messages) {
            MessageVO vo = new MessageVO();
            vo.setSenderId(msg.getSenderId());
            vo.setFriendId(msg.getFriendId());
            vo.setContent(msg.getContent());
            vo.setFileUrl(msg.getFileUrl());
            vo.setFileType(msg.getFileType());
            vo.setCreatedAt(msg.getCreatedAt());
            vo.setIsRead(msg.getIsRead());
            vo.setType(msg.getType());
            vo.setGroupId(msg.getGroupId());
            vo.setGroupName(msg.getGroupName());

            // 设置发送者信息
            Map<String, String> profile = profiles.get(msg.getSenderId());
            if (profile != null) {
                vo.setSenderNickname(profile.get("nickname"));
                vo.setSenderAvatar(profile.get("avatar"));
            }

            result.add(vo);
        }

        return result;
    }

    // 标记为已读
    @Override
    public void markAsRead(Long userId, Long friendId) {
        messageMapper.markAsRead(userId, friendId);
    }

    // 获取未读消息数量
    @Override
    public long getUnreadCount(Long userId) {
        return messageMapper.selectUnreadCount(userId);
    }

    // 获取好友未读消息数量映射
    @Override
    public Map<Long, Long> getUnreadCountMap(Long userId) {
        List<Map<String, Object>> list = messageMapper.selectUnreadCountByFriends(userId);
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : list) {
            long senderId = ((Number) row.get("sender_id")).longValue();
            long unreadCount = ((Number) row.get("unread_count")).longValue();
            map.put(senderId, unreadCount);
        }
        return map;
    }
}