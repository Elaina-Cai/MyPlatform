package org.example.myplatform.service.chatgroup;

import org.example.myplatform.dto.chatgroup.*;
import org.example.myplatform.entity.chatgroup.ChatGroup;
import org.example.myplatform.vo.chatgroup.*;
import java.util.List;

public interface ChatGroupService {
    // 创建群聊
    ChatGroupVO createGroup(Long userId, CreateGroupRequest request);
    // 获取群聊信息
    ChatGroupVO getGroupInfo(Long groupId, Long userId);
    // 获取用户加入的群聊
    List<ChatGroupVO> getMyGroups(Long userId);
    // 更新群聊信息
    ChatGroupVO updateGroup(Long groupId, Long userId, UpdateGroupRequest request);
    // 删除群聊
    void deleteGroup(Long groupId, Long userId);
    // 退出群聊
    void quitGroup(Long groupId, Long userId);
    // 设置群聊是否禁言
    void setGroupMute(Long groupId, Long userId, Boolean isMuted);
    // 搜索群聊
    ChatGroupVO searchGroup(Long groupId);
    // 获取群聊成员
    List<ChatGroupMemberVO> getMembers(Long groupId, Long userId);
    // 获取群成员ID列表（内部使用，不校验权限）
    List<Long> getMemberIds(Long groupId);
    // 踢出群聊成员
    void kickMember(Long groupId, Long userId, KickMemberRequest request);
    // 批量设置群聊管理员
    void setAdmins(Long groupId, Long userId, SetAdminsRequest request);
    // 禁言群聊成员
    void muteMember(Long groupId, Long userId, MuteRequest request);
    // 检查群聊成员是否禁言
    boolean isMuted(Long groupId, Long userId);
    // 发送群聊消息
    ChatGroupMessageVO saveGroupMessage(Long senderId, SendMessageRequest request);
    // 分页获取群聊消息
    List<ChatGroupMessageVO> getMessages(Long groupId, Long userId, int page, int size);
    // 标记群聊消息为已读
    void markAsRead(Long groupId, Long userId, Long messageId);
    // 获取群聊未读消息数量
    long getUnreadCount(Long groupId, Long userId);
    // 标记群聊所有消息为已读
    void markAllAsRead(Long groupId, Long userId);
    // 邀请好友入群
    void inviteFriend(Long inviterId, InviteRequest request);
    // 邀请好友入群（WebSocket调用，返回群信息用于推送）
    ChatGroup inviteFriend(Long inviterId, Long groupId, Long friendId);
    // 申请入群
    void applyJoin(Long applicantId, ApplyJoinRequest request);
    // 获取群聊待处理入群申请
    List<ChatGroupJoinRequestVO> getPendingRequests(Long groupId, Long userId);
    // 获取用户待处理入群申请
    List<ChatGroupJoinRequestVO> getMyPendingRequests(Long userId);
    // 获取用户待处理邀请
    List<ChatGroupJoinRequestVO> getMyInvitations(Long userId);
    // 处理入群申请
    void handleRequest(Long userId, HandleApplyJoinRequest request);
    // 取消入群申请
    void cancelRequest(Long requestId, Long userId);
}