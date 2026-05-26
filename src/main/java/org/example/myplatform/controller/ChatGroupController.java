package org.example.myplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.myplatform.dto.chatgroup.*;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.service.chatgroup.ChatGroupService;
import org.example.myplatform.vo.Result;
import org.example.myplatform.vo.chatgroup.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group")
public class ChatGroupController {
    //注入ChatGroupService
    private final ChatGroupService chatGroupService;
    public ChatGroupController(ChatGroupService chatGroupService) {
        this.chatGroupService = chatGroupService;
    }
    //获取当前用户ID
    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTR);
    }
    //创建群聊
    @PostMapping("/create")
    public Result<ChatGroupVO> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.createGroup(userId, request));
    }
    //获取群聊信息
    @GetMapping("/{groupId}")
    public Result<ChatGroupVO> getGroupInfo(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getGroupInfo(groupId, userId));
    }
    //获取我的群聊
    @GetMapping("/my")
    public Result<List<ChatGroupVO>> getMyGroups(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getMyGroups(userId));
    }
    //修改群聊信息
    @PutMapping("/{groupId}")
    public Result<ChatGroupVO> updateGroup(
            @PathVariable Long groupId,
            @RequestBody UpdateGroupRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.updateGroup(groupId, userId, request));
    }
    //解散群聊
    @DeleteMapping("/{groupId}")
    public Result<Object> deleteGroup(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.deleteGroup(groupId, userId);
        return Result.success(null);
    }
    //退出群聊
    @PostMapping("/{groupId}/quit")
    public Result<Object> quitGroup(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.quitGroup(groupId, userId);
        return Result.success(null);
    }
    //全体禁言
    @PostMapping("/{groupId}/mute")
    public Result<Object> setGroupMute(
            @PathVariable Long groupId,
            @RequestBody MuteRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.setGroupMute(groupId, userId, request.getIsMuted());
        return Result.success(null);
    }
    //搜索群聊
    @GetMapping("/{groupId}/search")
    public Result<ChatGroupVO> searchGroup(@PathVariable Long groupId) {
        return Result.success(chatGroupService.searchGroup(groupId));
    }
    //获取群聊成员
    @GetMapping("/{groupId}/members")
    public Result<List<ChatGroupMemberVO>> getMembers(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getMembers(groupId, userId));
    }
    //踢出群聊成员
    @PostMapping("/{groupId}/kick")
    public Result<Object> kickMember(
            @PathVariable Long groupId,
            @RequestBody KickMemberRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.kickMember(groupId, userId, request);
        return Result.success(null);
    }
    // 批量设置群聊管理员
    @PostMapping("/{groupId}/admins")
    public Result<Object> setAdmins(
            @PathVariable Long groupId,
            @RequestBody SetAdminsRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.setAdmins(groupId, userId, request);
        return Result.success(null);
    }
    //禁言群聊成员
    @PostMapping("/{groupId}/member/{targetUserId}/mute")
    public Result<Object> muteMember(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId,
            @RequestBody MuteRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.muteMember(groupId, userId, request);
        return Result.success(null);
    }
    //获取群聊消息
    @GetMapping("/{groupId}/message")
    public Result<List<ChatGroupMessageVO>> getMessages(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getMessages(groupId, userId, page, size));
    }
    //发送群聊消息
    @PostMapping("/{groupId}/message")
    public Result<ChatGroupMessageVO> sendMessage(
            @PathVariable Long groupId,
            @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        request.setGroupId(groupId);
        return Result.success(chatGroupService.saveGroupMessage(userId, request));
    }
    //标记群聊消息为已读
    @PostMapping("/{groupId}/message/{messageId}/read")
    public Result<Object> markAsRead(
            @PathVariable Long groupId,
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.markAsRead(groupId, userId, messageId);
        return Result.success(null);
    }
    //获取群聊未读消息数量
    @GetMapping("/{groupId}/unread")
    public Result<Long> getUnreadCount(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getUnreadCount(groupId, userId));
    }
    //标记群聊所有消息为已读
    @PostMapping("/{groupId}/read-all")
    public Result<Object> markAllAsRead(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.markAllAsRead(groupId, userId);
        return Result.success(null);
    }
    //邀请好友加入群聊
    @PostMapping("/invite")
    public Result<Object> inviteFriend(
            @RequestBody InviteRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.inviteFriend(userId, request);
        return Result.success(null);
    }
    //申请加入群聊
    @PostMapping("/apply")
    public Result<Object> applyJoin(
            @RequestBody ApplyJoinRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.applyJoin(userId, request);
        return Result.success(null);
    }
    //获取群聊待处理申请
    @GetMapping("/{groupId}/requests")
    public Result<List<ChatGroupJoinRequestVO>> getPendingRequests(
            @PathVariable Long groupId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getPendingRequests(groupId, userId));
    }
    //获取我的待处理群聊申请
    @GetMapping("/requests/my")
    public Result<List<ChatGroupJoinRequestVO>> getMyPendingRequests(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getMyPendingRequests(userId));
    }
    //获取我的待处理群聊邀请
    @GetMapping("/invitations")
    public Result<List<ChatGroupJoinRequestVO>> getMyInvitations(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(chatGroupService.getMyInvitations(userId));
    }
    //处理群聊申请
    @PostMapping("/request/handle")
    public Result<Object> handleRequest(
            @RequestBody HandleApplyJoinRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.handleRequest(userId, request);
        return Result.success(null);
    }
    //取消群聊申请
    @DeleteMapping("/request/{requestId}")
    public Result<Object> cancelRequest(
            @PathVariable Long requestId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        chatGroupService.cancelRequest(requestId, userId);
        return Result.success(null);
    }
}