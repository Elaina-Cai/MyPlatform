package org.example.myplatform.service.friend.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.example.myplatform.dto.friend.FriendRequest;
import org.example.myplatform.entity.Friend;
import org.example.myplatform.entity.User;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.mapper.FriendMapper;
import org.example.myplatform.mapper.UserMapper;
import org.example.myplatform.service.friend.FriendService;
import org.example.myplatform.service.friend.MessageService;
import org.example.myplatform.utils.RedisUtil;
import org.example.myplatform.vo.FriendApplyVO;
import org.example.myplatform.vo.FriendVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 好友服务实现类
 *
 * 核心优化：永远只保留一条好友关系记录
 *
 * 智能合并逻辑：
 * - A 添加 B 时，如果 B 已经添加了 A，则直接同意反向申请
 * - 这样避免产生 A→B 和 B→A 两条重复的好友关系
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {
    private final FriendMapper friendMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final MessageService messageService;
    /**
     * 申请状态常量
     */
    private static final int STATUS_PENDING = 0;   // 待处理
    private static final int STATUS_ACCEPTED = 1; // 已同意
    private static final int STATUS_REJECTED = 2;  // 已拒绝
    private static final int STATUS_UN_DELETED = 0; // 未删除
    private static final int STATUS_DELETED = 1;   // 已删除

    /**
     * 发送好友申请
     * 核心优化：智能合并逻辑
     * 1. 检查不能添加自己
     * 2. 检查目标用户是否存在
     * 3. 检查是否已经是好友（直接拒绝/已删除的恢复）
     * 4. 检查是否已经发送过申请（防止重复发送）
     * 5. 【关键优化】检查是否收到过来自对方的申请
     *    - 如果有，则直接同意对方的申请（合并为一条记录）
     *    - 如果没有，则创建新申请
     */
    @Override
    @Transactional
    public void sendApply(Long userId, FriendRequest request) {
        Long targetUserId = request.getTargetUserId();
        // 场景：用户在输入框输入自己的ID试图添加自己
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("不能添加自己为好友");
        }
        // 场景：用户输入一个不存在的用户ID
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BadRequestException("目标用户不存在");
        }
        // ========== 核心优化：查找反向申请 ==========
        // 查询是否收到过来自目标用户的申请（反向申请）
        LambdaQueryWrapper<Friend> reverseWrapper = new LambdaQueryWrapper<>();
        reverseWrapper.eq(Friend::getFromUserId, targetUserId)
                .eq(Friend::getToUserId, userId)
                .eq(Friend::getDeleted, 0);
        Friend reverseApply = friendMapper.selectOne(reverseWrapper);
        // ========== 情况1：对方已经发来过申请 ==========
        if (reverseApply != null) {
            // 情况1a：对方申请正在待处理 → 直接同意
            if (reverseApply.getStatus() == STATUS_PENDING) {
                reverseApply.setStatus(STATUS_ACCEPTED);
                reverseApply.setMessage(request.getMessage()); // 更新留言
                friendMapper.updateById(reverseApply);
                return; // 完成，无需创建新记录
            }
            // 情况1b：已经是好友（status=1）→ 抛出已存在提示
            if (reverseApply.getStatus() == STATUS_ACCEPTED) {
                throw new BadRequestException("你们已经是好友了");
            }
            // 情况1c：对方申请被拒绝（status=2）→ 恢复并变为好友
            if (reverseApply.getStatus() == STATUS_REJECTED) {
                reverseApply.setStatus(STATUS_ACCEPTED);
                reverseApply.setMessage(request.getMessage());
                friendMapper.updateById(reverseApply);
                return;
            }
        }
        // ========== 情况2：没有反向申请，检查自己的申请 ==========
        LambdaQueryWrapper<Friend> myWrapper = new LambdaQueryWrapper<>();
        myWrapper.eq(Friend::getFromUserId, userId)
                .eq(Friend::getToUserId, targetUserId)
                .eq(Friend::getDeleted, 0);
        Friend myApply = friendMapper.selectOne(myWrapper);
        if (myApply != null) {
            // 情况2a：已经是好友
            if (myApply.getStatus() == STATUS_ACCEPTED) {
                throw new BadRequestException("你们已经是好友了");
            }
            // 情况2b：申请正在待处理
            if (myApply.getStatus() == STATUS_PENDING) {
                throw new BadRequestException("已发送过申请，请等待对方处理");
            }
            // 情况2c：之前被拒绝 → 重新发起
            if (myApply.getStatus() == STATUS_REJECTED) {
                myApply.setStatus(STATUS_PENDING);
                myApply.setMessage(request.getMessage());
                friendMapper.updateById(myApply);
                return;
            }
        }
        // ========== 情况3：全新申请，创建记录 ==========
        Friend friend = new Friend();
        friend.setFromUserId(userId);
        friend.setToUserId(targetUserId);
        friend.setStatus(STATUS_PENDING);
        friend.setMessage(request.getMessage());
        friendMapper.insert(friend);
    }
    /**
     * 获取我发起的申请列表
     * 业务逻辑：
     * 查询 fromUserId = 当前用户ID 的所有申请记录（排除已删除的）
     * 注意：只返回待处理和已拒绝的申请，已同意的申请不再显示（已成为好友）
     */
    @Override
    public List<FriendApplyVO> getMyApplyList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getFromUserId, userId)  // 我发起的
                .eq(Friend::getDeleted, 0)          // 未删除
                .ne(Friend::getStatus, STATUS_ACCEPTED)  // 排除已同意的（已成为好友）
                .orderByDesc(Friend::getCreatedAt); // 按时间倒序

        List<Friend> applies = friendMapper.selectList(wrapper);
        List<FriendApplyVO> result = new ArrayList<>();

        for (Friend apply : applies) {
            // 获取申请对象的用户信息
            User user = userMapper.selectById(apply.getToUserId());
            if (user != null) {
                result.add(convertToApplyVO(apply, user));
            }
        }
        return result;
    }
    /**
     * 获取发给我的申请列表
     * 业务逻辑：
     * 查询 toUserId = 当前用户ID 且 status = 待处理 的申请记录
     */
    @Override
    public List<FriendApplyVO> getRequestList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getToUserId, userId)      // 发给我的
                .eq(Friend::getStatus, STATUS_PENDING) // 待处理的
                .eq(Friend::getDeleted, 0)            // 未删除
                .orderByDesc(Friend::getCreatedAt);

        List<Friend> applies = friendMapper.selectList(wrapper);
        List<FriendApplyVO> result = new ArrayList<>();

        for (Friend apply : applies) {
            User user = userMapper.selectById(apply.getFromUserId());
            if (user != null) {
                result.add(convertToApplyVO(apply, user));
            }
        }
        return result;
    }
    /**
     * 同意好友申请
     * 业务逻辑：
     * 1. 验证申请存在且属于当前用户
     * 2. 验证申请状态为"待处理"
     * 3. 更新状态为"已同意"
     */
    @Override
    @Transactional
    public void acceptApply(Long userId, Long fromUserId) {
        // 通过 fromUserId 查找待处理的申请记录
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getFromUserId, fromUserId)
                .eq(Friend::getToUserId, userId)
                .eq(Friend::getStatus, STATUS_PENDING)
                .eq(Friend::getDeleted, 0);

        Friend apply = friendMapper.selectOne(wrapper);

        if (apply == null) {
            throw new BadRequestException("申请不存在或已处理");
        }

        apply.setStatus(STATUS_ACCEPTED);
        friendMapper.updateById(apply);
    }
    /**
     * 拒绝好友申请
     * 业务逻辑：同上
     */
    @Override
    @Transactional
    public void rejectApply(Long userId, Long fromUserId) {
        // 通过 fromUserId 查找待处理的申请记录
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getFromUserId, fromUserId)
                .eq(Friend::getToUserId, userId)
                .eq(Friend::getStatus, STATUS_PENDING)
                .eq(Friend::getDeleted, 0);

        Friend apply = friendMapper.selectOne(wrapper);

        if (apply == null) {
            throw new BadRequestException("申请不存在或已处理");
        }

        apply.setStatus(STATUS_REJECTED);
        friendMapper.updateById(apply);
    }
    /**
     * 获取好友列表
     * - 只查询 status = 已同意 且 deleted = 0 的记录
     * 业务逻辑：
     * 查询 (fromUserId 或 toUserId) = 当前用户ID 且 status = 已同意 的记录
     */
    @Override
    public List<FriendVO> getFriendList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                        .eq(Friend::getFromUserId, userId)  // 我发起的
                        .or()
                        .eq(Friend::getToUserId, userId))    // 发给我的
                .eq(Friend::getStatus, STATUS_ACCEPTED)      // 已同意
                .orderByDesc(Friend::getUpdatedAt);  // 按最近更新时间排序

        List<Friend> friends = friendMapper.selectList(wrapper);
        List<FriendVO> result = new ArrayList<>();

        for (Friend friend : friends) {
            // 确定好友的用户ID（如果 from 是我，那 to 就是好友；反之亦然）
            Long friendUserId = friend.getFromUserId().equals(userId)
                    ? friend.getToUserId()
                    : friend.getFromUserId();

            User user = userMapper.selectById(friendUserId);
            if (user != null) {
                FriendVO vo = new FriendVO();
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setFriendAt(friend.getUpdatedAt() != null ?
                        LocalDateTime.parse(friend.getUpdatedAt().toString()) :
                        LocalDateTime.parse(friend.getCreatedAt().toString()));
                vo.setIsOnline(redisUtil.isUserOnline(friendUserId));
                result.add(vo);
            }
        }

        java.util.Map<Long, Long> unreadMap = messageService.getUnreadCountMap(userId);
        for (FriendVO vo : result) {
            vo.setUnreadCount(unreadMap.getOrDefault(vo.getUserId(), 0L));
        }
        return result;
    }
    /**
     * 删除好友
     * 优化后：
     * - 只需删除一条记录（因为智能合并，永远不会产生重复好友关系）
     * 业务逻辑：
     * 1. 查询双方的好友关系记录
     * 2. 执行软删除（deleted = 1）
     * 3. 保留聊天记录（因为聊天记录是通过 userId 关联的，不依赖 friend.id）
     */
    @Override
    @Transactional
    public void deleteFriend(Long userId, Long friendUserId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                        // 场景1：我发起的，好友是 toUserId
                        .eq(Friend::getFromUserId, userId)
                        .eq(Friend::getToUserId, friendUserId)
                        .or()
                        // 场景2：好友发起的，我是 toUserId
                        .eq(Friend::getFromUserId, friendUserId)
                        .eq(Friend::getToUserId, userId))
                .eq(Friend::getStatus, STATUS_ACCEPTED) // 已是好友
                .eq(Friend::getDeleted, STATUS_UN_DELETED);     // 未删除

        Friend friend = friendMapper.selectOne(wrapper);
        if (friend == null) {
            throw new BadRequestException("好友关系不存在");
        }

        friend.setDeleted(STATUS_DELETED);  // 软删除
        // 使用 UpdateWrapper 绕过 @TableLogic 的自动过滤，否则 deleted 字段无法更新
        UpdateWrapper<Friend> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", friend.getId()).set("deleted", STATUS_DELETED);
        friendMapper.update(null, updateWrapper);
    }
    /**
     * 将 Friend 实体转换为 FriendApplyVO
     * 应用场景：
     * - 在申请列表中需要展示申请人的用户信息
     */
    private FriendApplyVO convertToApplyVO(Friend apply, User user) {
        FriendApplyVO vo = new FriendApplyVO();
        vo.setFromUserId(user.getId());
        vo.setFromUsername(user.getUsername());
        vo.setFromNickname(user.getNickname());
        vo.setFromAvatar(user.getAvatar());
        vo.setMessage(apply.getMessage());
        vo.setCreatedAt(apply.getCreatedAt() != null ? LocalDateTime.parse(apply.getCreatedAt().toString()) : null);
        return vo;
    }
    @Override
    public List<Long> getFriendIds(Long userId) {
        return friendMapper.selectFriendIdsByUserId(userId);
    }
}