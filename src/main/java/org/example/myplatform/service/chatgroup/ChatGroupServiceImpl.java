package org.example.myplatform.service.chatgroup;

import lombok.extern.slf4j.Slf4j;
import org.example.myplatform.dto.chatgroup.*;
import org.example.myplatform.entity.Message;
import org.example.myplatform.entity.User;
import org.example.myplatform.entity.chatgroup.*;
import org.example.myplatform.event.GroupMemberAddedEvent;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.mapper.MessageMapper;
import org.example.myplatform.mapper.UserMapper;
import org.example.myplatform.mapper.chatgroup.*;
import org.example.myplatform.utils.RedisUtil;
import org.example.myplatform.vo.chatgroup.*;
import org.example.myplatform.websocket.MessageWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
public class ChatGroupServiceImpl implements ChatGroupService {
    private final ChatGroupMapper chatGroupMapper;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final ChatGroupMessageMapper chatGroupMessageMapper;
    private final ChatGroupMessageReadMapper chatGroupMessageReadMapper;
    private final ChatGroupJoinRequestMapper chatGroupJoinRequestMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final RedisUtil redisUtil;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ChatGroupServiceImpl(ChatGroupMapper chatGroupMapper,
                                ChatGroupMemberMapper chatGroupMemberMapper,
                                ChatGroupMessageMapper chatGroupMessageMapper,
                                ChatGroupMessageReadMapper chatGroupMessageReadMapper,
                                ChatGroupJoinRequestMapper chatGroupJoinRequestMapper,
                                UserMapper userMapper,
                                MessageMapper messageMapper,
                                RedisUtil redisUtil,
                                org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.chatGroupMapper = chatGroupMapper;
        this.chatGroupMemberMapper = chatGroupMemberMapper;
        this.chatGroupMessageMapper = chatGroupMessageMapper;
        this.chatGroupMessageReadMapper = chatGroupMessageReadMapper;
        this.chatGroupJoinRequestMapper = chatGroupJoinRequestMapper;
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
        this.redisUtil = redisUtil;
        this.eventPublisher = eventPublisher;
    }
    //实现创建群聊逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatGroupVO createGroup(Long userId, CreateGroupRequest request) {
        // 1. 参数校验
        if (!request.getMemberIds().contains(userId)) {
            throw new BadRequestException("创建者必须在成员列表中");
        }

        // 2. 创建群聊记录
        ChatGroup chatGroup = new ChatGroup();
        chatGroup.setName(request.getName().trim());
        chatGroup.setAvatar(request.getAvatar() != null ? request.getAvatar() : "");
        chatGroup.setAnnouncement(request.getAnnouncement() != null ? request.getAnnouncement() : "");
        chatGroup.setOwnerId(userId);
        chatGroup.setJoinType(request.getJoinType() != null ? request.getJoinType() : 0);
        chatGroup.setInvitePermission(request.getInvitePermission() != null ? request.getInvitePermission() : 1);
        chatGroup.setAllowMemberInvite(request.getAllowMemberInvite() != null ? request.getAllowMemberInvite() : 0);
        chatGroup.setIsMuted(0);
        chatGroupMapper.insert(chatGroup);

        // 3. 批量插入所有成员（一次性插入）
        List<ChatGroupMember> members = new ArrayList<>();

        // 群主
        ChatGroupMember owner = new ChatGroupMember();
        owner.setGroupId(chatGroup.getId());
        owner.setUserId(userId);
        owner.setRole(2); // 群主 role=2
        owner.setIsMuted(0);
        owner.setJoinedAt(LocalDateTime.now());
        members.add(owner);

        // 其他成员
        for (Long memberId : request.getMemberIds()) {
            if (memberId.equals(userId)) continue;
            ChatGroupMember member = new ChatGroupMember();
            member.setGroupId(chatGroup.getId());
            member.setUserId(memberId);
            member.setRole(0); // 普通成员 role=0
            member.setIsMuted(0);
            member.setJoinedAt(LocalDateTime.now());
            members.add(member);
        }
        // 批量插入群人员记录
        chatGroupMemberMapper.insertBatch(members);

        // 4. 返回群聊信息
        return getGroupInfo(chatGroup.getId(), userId);
    }
    //实现获取群聊信息逻辑
    @Override
    public ChatGroupVO getGroupInfo(Long groupId, Long userId) {
        String cacheKey = "group:info:" + groupId;
        // TODO: 高并发下可能短暂不一致，可考虑加分布式锁
        ChatGroupVO cached = redisUtil.get(cacheKey, ChatGroupVO.class);
        if (cached != null) {
            return cached;
        }

        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            // 防穿透：缓存不存在标记
            redisUtil.setNullValue(cacheKey);
            throw new BadRequestException("群聊不存在");
        }

        User owner = userMapper.selectById(chatGroup.getOwnerId());

        ChatGroupVO vo = new ChatGroupVO();
        vo.setId(chatGroup.getId());
        vo.setName(chatGroup.getName());
        vo.setAvatar(chatGroup.getAvatar());
        vo.setAnnouncement(chatGroup.getAnnouncement());
        vo.setOwnerId(chatGroup.getOwnerId());
        vo.setOwnerNickname(owner != null ? owner.getNickname() : "");
        vo.setJoinType(chatGroup.getJoinType());
        vo.setInvitePermission(chatGroup.getInvitePermission());
        vo.setAllowMemberInvite(chatGroup.getAllowMemberInvite());
        vo.setIsMuted(chatGroup.getIsMuted());
        vo.setCreatedAt(chatGroup.getCreatedAt());
        vo.setMemberCount(chatGroupMemberMapper.countByGroupId(groupId));

        // TODO: 缓存回填时若并发更新，可能导致旧值覆盖新值
        redisUtil.setWithExpire(cacheKey, vo);
        return vo;
    }
    //实现获取我的群列表逻辑
    @Override
    public List<ChatGroupVO> getMyGroups(Long userId) {
        // 1. 一次 SQL 查询所有群聊及成员数量
        List<Map<String, Object>> result = chatGroupMapper.selectByUserIdWithMemberCount(userId);

        // 2. 转换为 VO 列表
        return result.stream().map(map -> {
            ChatGroupVO vo = new ChatGroupVO();
            vo.setId(((Number) map.get("id")).longValue());
            vo.setName((String) map.get("name"));
            vo.setAvatar((String) map.get("avatar"));
            vo.setAnnouncement((String) map.get("announcement"));
            vo.setOwnerId(((Number) map.get("owner_id")).longValue());
            vo.setJoinType((Integer) map.get("join_type"));
            vo.setInvitePermission((Integer) map.get("invite_permission"));
            vo.setAllowMemberInvite((Integer) map.get("allow_member_invite"));
            vo.setIsMuted((Integer) map.get("is_muted"));
            vo.setCreatedAt((java.time.LocalDateTime) map.get("created_at"));
            vo.setMemberCount(((Number) map.get("member_count")).intValue());
            return vo;
        }).toList();
    }
    //TODO：高并发下的缓存更新问题待优化（多人同时修改群信息时，缓存会不一致）
    //实现修改群信息逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatGroupVO updateGroup(Long groupId, Long userId, UpdateGroupRequest request) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（只有群主可以修改）
        if (!chatGroup.getOwnerId().equals(userId)) {
            throw new BadRequestException("只有群主可以修改群信息");
        }

        // 3. 更新群聊信息
        if (request.getName() != null) {
            chatGroup.setName(request.getName().trim());
        }
        if (request.getAvatar() != null) {
            chatGroup.setAvatar(request.getAvatar());
        }
        if (request.getAnnouncement() != null) {
            chatGroup.setAnnouncement(request.getAnnouncement());
        }
        if (request.getJoinType() != null) {
            chatGroup.setJoinType(request.getJoinType());
        }
        if (request.getInvitePermission() != null) {
            chatGroup.setInvitePermission(request.getInvitePermission());
        }
        if (request.getAllowMemberInvite() != null) {
            chatGroup.setAllowMemberInvite(request.getAllowMemberInvite());
        }
        chatGroupMapper.updateById(chatGroup);

        // 直接更新缓存
        // TODO: 高并发下缓存更新和DB更新之间可能有请求读到旧值
        String cacheKey = "group:info:" + groupId;
        User owner = userMapper.selectById(chatGroup.getOwnerId());
        ChatGroupVO vo = new ChatGroupVO();
        vo.setId(chatGroup.getId());
        vo.setName(chatGroup.getName());
        vo.setAvatar(chatGroup.getAvatar());
        vo.setAnnouncement(chatGroup.getAnnouncement());
        vo.setOwnerId(chatGroup.getOwnerId());
        vo.setOwnerNickname(owner != null ? owner.getNickname() : "");
        vo.setJoinType(chatGroup.getJoinType());
        vo.setInvitePermission(chatGroup.getInvitePermission());
        vo.setAllowMemberInvite(chatGroup.getAllowMemberInvite());
        vo.setIsMuted(chatGroup.getIsMuted());
        vo.setCreatedAt(chatGroup.getCreatedAt());
        vo.setMemberCount(chatGroupMemberMapper.countByGroupId(groupId));
        redisUtil.setWithExpire(cacheKey, vo);

        return vo;
    }
    //实现解散群聊逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId, Long userId) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（只有群主可以解散）
        if (!chatGroup.getOwnerId().equals(userId)) {
            throw new BadRequestException("只有群主可以解散群聊");
        }

        // 3. 删除关联数据（按顺序）
        // 删除消息已读记录
        chatGroupMessageReadMapper.deleteByGroupId(groupId);
        // 删除群消息
        chatGroupMessageMapper.deleteByGroupId(groupId);
        // 删除入群申请
        chatGroupJoinRequestMapper.deleteByGroupId(groupId);
        // 删除群成员
        chatGroupMemberMapper.deleteByGroupId(groupId);
        // 删除群聊
        chatGroupMapper.deleteByGroupId(groupId);
        // TODO: 删除缓存失败会导致数据不一致，可考虑删除失败时记录日志并重试
        redisUtil.delete("group:info:" + groupId);
    }
    //实现退出群聊逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void quitGroup(Long groupId, Long userId) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 查询成员信息
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 群主不能退出，只能解散群聊
        if (member.getRole() == 2) {
            throw new BadRequestException("群主不能退出群聊，请先转让群主或解散群聊");
        }

        // 4. 删除成员记录
        chatGroupMemberMapper.deleteById(member.getId());
    }
    //实现全体禁言逻辑
    @Override
    public void setGroupMute(Long groupId, Long userId, Boolean isMuted) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（群主和管理员可以设置全体禁言）
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }
        if (member.getRole() != 1 && member.getRole() != 2) {
            throw new BadRequestException("只有群主和管理员可以设置全体禁言");
        }

        // 3. 更新群聊的禁言状态
        chatGroup.setIsMuted(isMuted ? 1 : 0);
        chatGroupMapper.updateById(chatGroup);
    }
    //实现搜索群聊逻辑
    @Override
    public ChatGroupVO searchGroup(Long groupId) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 查询群主信息
        User owner = userMapper.selectById(chatGroup.getOwnerId());

        // 3. 构建 VO
        ChatGroupVO vo = new ChatGroupVO();
        vo.setId(chatGroup.getId());
        vo.setName(chatGroup.getName());
        vo.setAvatar(chatGroup.getAvatar());
        vo.setAnnouncement(chatGroup.getAnnouncement());
        vo.setOwnerId(chatGroup.getOwnerId());
        vo.setOwnerNickname(owner != null ? owner.getNickname() : "");
        vo.setJoinType(chatGroup.getJoinType());
        vo.setMemberCount(chatGroupMemberMapper.countByGroupId(groupId));
        vo.setCreatedAt(chatGroup.getCreatedAt());

        return vo;
    }
    //实现获取群成员列表逻辑
    @Override
    public List<ChatGroupMemberVO> getMembers(Long groupId, Long userId) {
        // 1. 查询群聊是否存在
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（必须是成员才能查看成员列表）
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 查询所有成员
        List<ChatGroupMember> members = chatGroupMemberMapper.selectByGroupId(groupId);

        // 4. 批量查询用户信息
        List<Long> userIds = members.stream().map(ChatGroupMember::getUserId).toList();
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);

        // 5. 构建用户 ID 到信息的 Map
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 6. 转换为 VO 列表
        return members.stream().map(m -> {
            ChatGroupMemberVO vo = new ChatGroupMemberVO();
            vo.setId(m.getId());
            vo.setGroupId(m.getGroupId());
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setIsMuted(m.getIsMuted());
            vo.setMuteExpireTime(m.getMuteExpireTime());
            vo.setJoinedAt(m.getJoinedAt());
            User user = userMap.get(m.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).toList();
    }
    // 获取群成员ID列表（内部使用，不校验权限）
    @Override
    public List<Long> getMemberIds(Long groupId) {
        List<ChatGroupMember> members = chatGroupMemberMapper.selectByGroupId(groupId);
        return members.stream().map(ChatGroupMember::getUserId).toList();
    }
    //实现踢出成员逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long groupId, Long userId, KickMemberRequest request) {
        Long targetUserId = request.getTargetUserId();

        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 查询操作者权限
        ChatGroupMember operator = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (operator == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 查询目标成员
        ChatGroupMember target = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (target == null) {
            throw new BadRequestException("目标用户不是群成员");
        }

        // 4. 权限校验
        // 群主可以踢任何人（除自己外）
        if (operator.getRole() == 2) {
            if (targetUserId.equals(userId)) {
                throw new BadRequestException("群主不能踢出自己");
            }
        }
        // 管理员只能踢普通成员
        else if (operator.getRole() == 1) {
            if (target.getRole() != 0) {
                throw new BadRequestException("管理员不能踢出管理员或群主");
            }
        }
        // 普通成员不能踢人
        else {
            throw new BadRequestException("普通成员没有踢人权限");
        }

        // 5. 删除目标成员
        chatGroupMemberMapper.deleteById(target.getId());
    }
    // 实现批量设置群聊管理员逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAdmins(Long groupId, Long userId, SetAdminsRequest request) {
        List<Long> userIds = request.getUserIds();
        Boolean isAdmin = request.getIsAdmin();

        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（只有群主可以设置管理员）
        ChatGroupMember operator = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (operator == null) {
            throw new BadRequestException("你不是群成员");
        }
        if (operator.getRole() != 2) {
            throw new BadRequestException("只有群主可以设置管理员");
        }

        // 3. 校验目标用户都是群成员（批量查询）
        if (userIds.contains(userId)) {
            throw new BadRequestException("不能把自己设为管理员");
        }
        List<ChatGroupMember> targets = chatGroupMemberMapper.selectByGroupId(groupId);
        java.util.Map<Long, ChatGroupMember> targetMap = targets.stream()
                .collect(java.util.stream.Collectors.toMap(ChatGroupMember::getUserId, m -> m));
        for (Long targetUserId : userIds) {
            if (!targetMap.containsKey(targetUserId)) {
                throw new BadRequestException("用户" + targetUserId + "不是群成员");
            }
        }

        // 4. 批量更新群成员角色（一次 SQL）
        int newRole = Boolean.TRUE.equals(isAdmin) ? 1 : 0;
        chatGroupMemberMapper.updateRoleBatch(groupId, userIds, newRole);
    }
    // 实现个体禁言逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMember(Long groupId, Long userId, MuteRequest request) {
        Long targetUserId = request.getUserId();

        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验操作者权限（群主和管理员可以禁言）
        ChatGroupMember operator = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (operator == null) {
            throw new BadRequestException("你不是群成员");
        }
        if (operator.getRole() != 1 && operator.getRole() != 2) {
            throw new BadRequestException("只有群主和管理员可以禁言用户");
        }

        // 3. 查询目标成员
        ChatGroupMember target = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (target == null) {
            throw new BadRequestException("目标用户不是群成员");
        }

        // 4. 不能禁言自己
        if (targetUserId.equals(userId)) {
            throw new BadRequestException("不能禁言自己");
        }

        // 5. 不能禁言群主
        if (target.getRole() == 2) {
            throw new BadRequestException("不能禁言群主");
        }

        // 6. 更新禁言状态
        Integer isMuted = Boolean.TRUE.equals(request.getIsMuted()) ? 1 : 0;
        LocalDateTime muteExpireTime = request.getMuteExpireTime();
        chatGroupMemberMapper.updateMuteStatus(groupId, targetUserId, isMuted, muteExpireTime);
    }
    // 实现检查是否被禁言逻辑
    @Override
    public boolean isMuted(Long groupId, Long userId) {
        // 1. 检查全体禁言
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            return false;
        }
        if (chatGroup.getIsMuted() != null && chatGroup.getIsMuted() == 1) {
            return true;
        }

        // 2. 检查个体禁言
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            return false;
        }
        if (member.getIsMuted() != null && member.getIsMuted() == 1) {
            // 检查禁言是否过期
            if (member.getMuteExpireTime() != null && member.getMuteExpireTime().isBefore(java.time.LocalDateTime.now())) {
                return false;
            }
            return true;
        }

        return false;
    }
    // 实现保存群消息逻辑
    @Override
    public ChatGroupMessageVO saveGroupMessage(Long senderId, SendMessageRequest request) {
        Long groupId = request.getGroupId();

        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 查询发送者成员信息
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, senderId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 检查是否被禁言
        if (isMuted(groupId, senderId)) {
            throw new BadRequestException("你已被禁言，无法发送消息");
        }

        // 4. 创建消息
        ChatGroupMessage message = new ChatGroupMessage();
        message.setGroupId(groupId);
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setMessageType(request.getMessageType() != null ? request.getMessageType() : 1);
        message.setFileUrl(request.getFileUrl());
        message.setFileType(request.getFileType());
        message.setCreatedAt(LocalDateTime.now());
        // 5. 插入消息
        chatGroupMessageMapper.insert(message);

        // 5. 查询发送者信息
        User sender = userMapper.selectById(senderId);

        // 6. 构建返回 VO
        ChatGroupMessageVO vo = new ChatGroupMessageVO();
        vo.setId(message.getId());
        vo.setGroupId(message.getGroupId());
        vo.setSenderId(message.getSenderId());
        vo.setSenderNickname(sender != null ? sender.getNickname() : "");
        vo.setSenderAvatar(sender != null ? sender.getAvatar() : "");
        vo.setContent(message.getContent());
        vo.setMessageType(message.getMessageType());
        vo.setFileUrl(message.getFileUrl());
        vo.setFileType(message.getFileType());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setIsRead(false);

        return vo;
    }
    // 实现获取群消息历史逻辑
    @Override
    public List<ChatGroupMessageVO> getMessages(Long groupId, Long userId, int page, int size) {
        // 1. 查询群聊
        ChatGroup chatGroup = chatGroupMapper.selectByGroupId(groupId);
        if (chatGroup == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 校验权限（必须是成员）
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 查询消息列表（分页查询）
        int offset = (page - 1) * size;
        List<ChatGroupMessage> messages = chatGroupMessageMapper.selectByGroupId(groupId, offset, size);

        // 4. 批量查询已读状态
        List<Long> messageIds = messages.stream().map(ChatGroupMessage::getId).toList();
        List<Long> readMessageIds = messageIds.isEmpty() ? List.of()
                : chatGroupMessageReadMapper.selectReadMessageIds(userId, groupId);

        // 5. 批量查询发送者信息
        List<Long> senderIds = messages.stream().map(ChatGroupMessage::getSenderId).toList();
        List<User> senders = senderIds.isEmpty() ? List.of() : userMapper.selectBatchIds(senderIds);
        java.util.Map<Long, User> senderMap = senders.stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        // 6. 构建 VO 列表
        java.util.Set<Long> readSet = new java.util.HashSet<>(readMessageIds);
        return messages.stream().map(msg -> {
            ChatGroupMessageVO vo = new ChatGroupMessageVO();
            vo.setId(msg.getId());
            vo.setGroupId(msg.getGroupId());
            vo.setSenderId(msg.getSenderId());
            vo.setContent(msg.getContent());
            vo.setMessageType(msg.getMessageType());
            vo.setFileUrl(msg.getFileUrl());
            vo.setFileType(msg.getFileType());
            vo.setCreatedAt(msg.getCreatedAt());
            vo.setIsRead(readSet.contains(msg.getId()));
            User sender = senderMap.get(msg.getSenderId());
            if (sender != null) {
                vo.setSenderNickname(sender.getNickname());
                vo.setSenderAvatar(sender.getAvatar());
            }
            return vo;
        }).toList();
    }
    // 实现标记单条消息已读逻辑
    @Override
    public void markAsRead(Long groupId, Long userId, Long messageId) {
        // 1. 查询消息是否存在
        ChatGroupMessage message = chatGroupMessageMapper.selectById(messageId);
        if (message == null || !message.getGroupId().equals(groupId)) {
            throw new BadRequestException("消息不存在");
        }

        // 2. 校验权限（必须是成员）
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BadRequestException("你不是群成员");
        }

        // 3. 检查是否已读，未读则插入记录
        Integer existing = chatGroupMessageReadMapper.checkMessageRead(messageId, userId);
        if (existing == null || existing == 0) {
            chatGroupMessageReadMapper.insertReadRecord(messageId, userId);
        }
    }
    // 实现获取未读消息数逻辑
    @Override
    public long getUnreadCount(Long groupId, Long userId) {
        return chatGroupMessageReadMapper.selectUnreadCount(groupId, userId);
    }
    // 实现标记全部消息已读逻辑
    @Override
    public void markAllAsRead(Long groupId, Long userId) {
        chatGroupMessageReadMapper.markAllAsRead(groupId, userId);
    }
    //实现邀请好友入群逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteFriend(Long inviterId, InviteRequest request) {
        log.info("邀请入群: inviterId={}, groupId={}, friendId={}", inviterId, request.getGroupId(), request.getFriendId());
        Long groupId = request.getGroupId();
        Long friendId = request.getFriendId();

        // 1. 检查群是否存在
        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 检查邀请人是否是群主或管理员
        ChatGroupMember inviterMember = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, inviterId);

        if (inviterMember == null || (inviterMember.getRole() != 1 && inviterMember.getRole() != 2)) {
            throw new BadRequestException("只有群主和管理员可以邀请好友");
        }

        // 3. 检查好友是否已在群里
        ChatGroupMember existingMember = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, friendId);
        if (existingMember != null) {
            throw new BadRequestException("该好友已在群聊中");
        }

        // 4. 写入消息记录（2条：发送人+收件人）
        LocalDateTime now = LocalDateTime.now();
        String groupName = group.getName();
        String inviteContent = "邀请你加入群聊：" + groupName;
        // 给发送人的消息记录
        Message senderMsg = new Message();
        senderMsg.setSenderId(inviterId);
        senderMsg.setUserId(inviterId);
        senderMsg.setFriendId(friendId);
        senderMsg.setContent(inviteContent);
        senderMsg.setType(1);  // 群邀请
        senderMsg.setGroupId(groupId);
        senderMsg.setGroupName(groupName);
        senderMsg.setIsRead(1);  // 发送人已读
        senderMsg.setCreatedAt(now);
        messageMapper.insert(senderMsg);

        // 给被邀请人的消息记录
        Message receiverMsg = new Message();
        receiverMsg.setSenderId(inviterId);
        receiverMsg.setUserId(friendId);
        receiverMsg.setFriendId(inviterId);
        receiverMsg.setContent(inviteContent);
        receiverMsg.setType(1);  // 群邀请
        receiverMsg.setGroupId(groupId);
        receiverMsg.setGroupName(groupName);
        receiverMsg.setIsRead(0);  // 被邀请人未读
        receiverMsg.setCreatedAt(now);
        messageMapper.insert(receiverMsg);
    }

    @Override
    public ChatGroup inviteFriend(Long inviterId, Long groupId, Long friendId) {
        InviteRequest request = new InviteRequest();
        request.setGroupId(groupId);
        request.setFriendId(friendId);
        inviteFriend(inviterId, request);
        return chatGroupMapper.selectById(groupId);
    }

    // 实现申请入群逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyJoin(Long applicantId, ApplyJoinRequest request) {
        Long groupId = request.getGroupId();

        // 1. 检查群是否存在
        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) {
            throw new BadRequestException("群聊不存在");
        }

        // 2. 检查申请人是否已在群里
        ChatGroupMember existingMember = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, applicantId);
        if (existingMember != null) {
            throw new BadRequestException("您已在群聊中");
        }

        // 3. 检查是否有待处理的邀请（type=0），如果有则转换为申请
        List<ChatGroupJoinRequest> pendingInvitations = chatGroupJoinRequestMapper.selectPendingInvitations(applicantId);
        ChatGroupJoinRequest existingInvite = pendingInvitations.stream()
                .filter(r -> r.getGroupId().equals(groupId))
                .findFirst()
                .orElse(null);
        
        if (existingInvite != null) {
            existingInvite.setType(1);  // 转换为申请
            chatGroupJoinRequestMapper.updateById(existingInvite);
            return;
        }

        // 4. 检查是否有待处理的申请（type=1）
        ChatGroupJoinRequest pendingRequest = chatGroupJoinRequestMapper.selectPendingRequest(groupId, applicantId);
        if (pendingRequest != null) {
            throw new BadRequestException("已存在待处理的申请，请等待处理");
        }

        // 5. 创建申请记录
        ChatGroupJoinRequest apply = new ChatGroupJoinRequest();
        apply.setGroupId(groupId);
        apply.setApplicantId(applicantId);
        apply.setInviterId(null);  // 申请入群，没有邀请人
        apply.setType(1);  // 1-申请
        apply.setStatus(0);  // 0-待处理
        chatGroupJoinRequestMapper.insert(apply);
    }
    // 实现获取群待审批列表逻辑
    @Override
    public List<ChatGroupJoinRequestVO> getPendingRequests(Long groupId, Long userId) {
        // 1. 检查用户是否是群主或管理员
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null || (member.getRole() != 1 && member.getRole() != 2)) {
            throw new BadRequestException("只有群主和管理员可以查看待审批列表");
        }

        // 2. 查询待处理申请列表
        List<ChatGroupJoinRequest> requests = chatGroupJoinRequestMapper.selectPendingByGroupId(groupId);

        // 3. 收集所有用户 ID
        List<Long> userIds = new ArrayList<>();
        for (ChatGroupJoinRequest req : requests) {
            if (req.getApplicantId() != null) userIds.add(req.getApplicantId());
            if (req.getInviterId() != null) userIds.add(req.getInviterId());
        }

        // 4. 批量获取用户信息
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 5. 获取群信息
        ChatGroup group = chatGroupMapper.selectById(groupId);

        // 6. 转换成 VO
        List<ChatGroupJoinRequestVO> voList = new ArrayList<>();
        for (ChatGroupJoinRequest req : requests) {
            ChatGroupJoinRequestVO vo = new ChatGroupJoinRequestVO();
            vo.setId(req.getId());
            vo.setGroupId(groupId);
            vo.setGroupName(group != null ? group.getName() : "");
            vo.setApplicantId(req.getApplicantId());
            vo.setInviterId(req.getInviterId());
            vo.setStatus(req.getStatus());
            vo.setType(req.getType());
            vo.setCreatedAt(req.getCreatedAt());

            // 申请人信息
            User applicant = userMap.get(req.getApplicantId());
            if (applicant != null) {
                vo.setApplicantNickname(applicant.getNickname());
                vo.setApplicantAvatar(applicant.getAvatar());
            }

            // 邀请人信息
            if (req.getInviterId() != null) {
                User inviter = userMap.get(req.getInviterId());
                if (inviter != null) {
                    vo.setInviterNickname(inviter.getNickname());
                }
            }

            voList.add(vo);
        }

        return voList;
    }
    // 实现获取我发起的申请列表逻辑
    @Override
    public List<ChatGroupJoinRequestVO> getMyPendingRequests(Long userId) {
        // 查询我发起的申请列表（type=1 的申请）
        List<ChatGroupJoinRequest> requests = chatGroupJoinRequestMapper.selectPendingByApplicantId(userId)
                .stream()
                .filter(r -> r.getType() != null && r.getType() == 1)  // 只取 type=1 的申请
                .collect(Collectors.toList());

        return buildRequestVOList(requests);
    }
    // 实现获取我收到的邀请列表逻辑
    @Override
    public List<ChatGroupJoinRequestVO> getMyInvitations(Long userId) {
        // 查询我收到的邀请列表（type=0 的邀请）
        List<ChatGroupJoinRequest> requests = chatGroupJoinRequestMapper.selectPendingInvitations(userId);

        return buildRequestVOList(requests);
    }
    // 公共方法：构建请求 VO 列表
    private List<ChatGroupJoinRequestVO> buildRequestVOList(List<ChatGroupJoinRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集用户 ID（申请人和邀请人）
        List<Long> userIds = new ArrayList<>();
        for (ChatGroupJoinRequest req : requests) {
            if (req.getApplicantId() != null) userIds.add(req.getApplicantId());
            if (req.getInviterId() != null) userIds.add(req.getInviterId());
        }

        // 批量获取用户信息
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 收集群 ID
        List<Long> groupIds = requests.stream()
                .map(ChatGroupJoinRequest::getGroupId)
                .collect(Collectors.toList());

        // 批量获取群信息
        Map<Long, ChatGroup> groupMap = groupIds.isEmpty() ? Map.of() :
                chatGroupMapper.selectBatchIds(groupIds).stream()
                        .collect(Collectors.toMap(ChatGroup::getId, g -> g));

        // 组装 VO
        List<ChatGroupJoinRequestVO> voList = new ArrayList<>();
        for (ChatGroupJoinRequest req : requests) {
            ChatGroupJoinRequestVO vo = new ChatGroupJoinRequestVO();
            vo.setId(req.getId());
            vo.setGroupId(req.getGroupId());
            ChatGroup g = groupMap.get(req.getGroupId());
            vo.setGroupName(g != null ? g.getName() : "");
            vo.setApplicantId(req.getApplicantId());
            vo.setInviterId(req.getInviterId());
            vo.setStatus(req.getStatus());
            vo.setType(req.getType());
            vo.setCreatedAt(req.getCreatedAt());

            // 申请人信息
            User applicant = userMap.get(req.getApplicantId());
            if (applicant != null) {
                vo.setApplicantNickname(applicant.getNickname());
                vo.setApplicantAvatar(applicant.getAvatar());
            }

            // 邀请人信息
            if (req.getInviterId() != null) {
                User inviter = userMap.get(req.getInviterId());
                if (inviter != null) {
                    vo.setInviterNickname(inviter.getNickname());
                }
            }

            voList.add(vo);
        }

        return voList;
    }
    // 实现处理入群申请逻辑
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRequest(Long userId, HandleApplyJoinRequest request) {
        Long requestId = request.getRequestId();
        boolean approve = Boolean.TRUE.equals(request.getApprove());

        // 1. 查询申请记录
        ChatGroupJoinRequest joinRequest = chatGroupJoinRequestMapper.selectById(requestId);
        if (joinRequest == null) {
            throw new BadRequestException("申请记录不存在");
        }

        // 2. 检查状态
        if (joinRequest.getStatus() != 0) {
            throw new BadRequestException("该申请已被处理");
        }

        Long groupId = joinRequest.getGroupId();
        Long applicantId = joinRequest.getApplicantId();

        // 3. 根据 type 检查权限
        if (joinRequest.getType() == 1) {
            // 申请入群：需要群主/管理员审批
            ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
            if (member == null || (member.getRole() != 1 && member.getRole() != 2)) {
                throw new BadRequestException("只有群主和管理员可以审批入群申请");
            }
        } else {
            // 邀请入群：被邀请人自己确认
            if (!applicantId.equals(userId)) {
                throw new BadRequestException("只有被邀请人可以确认邀请");
            }
        }

        // 4. 更新申请状态
        int newStatus = approve ? 1 : 2;  // 1-已同意，2-已拒绝
        chatGroupJoinRequestMapper.updateStatus(requestId, newStatus, userId);

        // 5. 同意则添加成员并推送通知
        if (approve) {
            ChatGroupMember newMember = new ChatGroupMember();
            newMember.setGroupId(groupId);
            newMember.setUserId(applicantId);
            newMember.setRole(0);  // 普通成员 role=0
            newMember.setIsMuted(0);
            newMember.setJoinedAt(LocalDateTime.now());
            chatGroupMemberMapper.insert(newMember);

            // 获取新成员信息
            User newUser = userMapper.selectById(applicantId);
            String nickname = newUser != null ? newUser.getNickname() : "未知用户";
            String avatar = newUser != null ? newUser.getAvatar() : "";
            /**
             * 成员加入群聊事件 的事件发布处
             */
            // 发布成员加入的事件，利用的是sping的事件机制来通知其他组件一件事：有新的群成员加入群聊
            eventPublisher.publishEvent(new GroupMemberAddedEvent(
                this, groupId, applicantId, nickname, avatar
            ));
        }
    }
    // 实现取消入群申请逻辑
    @Override
    public void cancelRequest(Long requestId, Long userId) {
        // 1. 查询申请记录
        ChatGroupJoinRequest request = chatGroupJoinRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BadRequestException("申请记录不存在");
        }

        // 2. 检查状态
        if (request.getStatus() != 0) {
            throw new BadRequestException("该申请已被处理，无法取消");
        }

        // 3. 检查权限：申请人/邀请人/群主可以取消
        Long groupId = request.getGroupId();
        ChatGroupMember member = chatGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        boolean isCreator = request.getApplicantId().equals(userId);  // 我是申请人
        boolean isInviter = request.getInviterId() != null && request.getInviterId().equals(userId);  // 我是邀请人
        boolean isAdmin = member != null && (member.getRole() == 0 || member.getRole() == 1);  // 我是群主/管理员

        if (!isCreator && !isInviter && !isAdmin) {
            throw new BadRequestException("您没有权限取消该申请");
        }

        // 4. 删除申请记录
        chatGroupJoinRequestMapper.deleteById(requestId);
    }
}