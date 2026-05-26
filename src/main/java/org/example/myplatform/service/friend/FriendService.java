package org.example.myplatform.service.friend;

import org.example.myplatform.dto.friend.FriendRequest;
import org.example.myplatform.vo.FriendApplyVO;
import org.example.myplatform.vo.FriendVO;

import java.util.List;

/**
 * 好友服务接口
 * 定义好友功能的业务逻辑
 */
public interface FriendService {
    /**
     * 发送好友申请
     * 应用场景：
     * - 用户在搜索页面找到目标用户，点击"添加好友"
     * - 或在用户主页点击"添加好友"按钮
     * @param userId 当前登录用户ID
     * @param request 包含目标用户ID和申请留言
     */
    void sendApply(Long userId, FriendRequest request);
    /**
     * 获取我发起的申请列表
     * 应用场景：
     * - 用户想查看"我发出了哪些申请"
     * - 页面路径：个人中心 → 好友申请 → 我发出的
     * @param userId 当前登录用户ID
     * @return 我发起的所有申请（含已同意、已拒绝、待处理的）
     */
    List<FriendApplyVO> getMyApplyList(Long userId);
    /**
     * 获取发给我的申请列表
     * 应用场景：
     * - 用户想查看"谁想加我为好友"
     * - 页面路径：个人中心 → 好友申请 → 待处理
     * @param userId 当前登录用户ID
     * @return 发给我且状态为"待处理"的申请列表
     */
    List<FriendApplyVO> getRequestList(Long userId);
    /**
     * 同意好友申请
     * 应用场景：
     * - 用户查看来自分配的申请，点击"同意"按钮
     * - 申请状态从 0(待处理) 变为 1(已同意)
     * @param userId 当前登录用户ID（被申请人）
     * @param fromUserId 发起申请的用户用户ID
     */
    void acceptApply(Long userId, Long fromUserId);
    /**
     * 拒绝好友申请
     * 应用场景：
     * - 用户不想添加某人为好友，点击"拒绝"按钮
     * - 申请状态从 0(待处理) 变为 2(已拒绝)
     * @param userId 当前登录用户ID（被申请人）
     * @param fromUserId 发起申请的用户用户ID
     */
    void rejectApply(Long userId, Long fromUserId);
    /**
     * 获取好友列表
     * 应用场景：
     * - 用户打开聊天页面，需要展示好友列表
     * - 页面路径：首页 → 好友列表
     * @param userId 当前登录用户ID
     * @return 已同意的好友列表
     */
    List<FriendVO> getFriendList(Long userId);
    /**
     * 删除好友
     * 应用场景：
     * - 用户想与某人解除好友关系
     * - 使用软删除，保留聊天记录
     * @param userId 当前登录用户ID
     * @param friendUserId 要删除的好友的用户ID
     */
    void deleteFriend(Long userId, Long friendUserId);
    /**
     * 获取用户的所有好友ID列表
     */
    List<Long> getFriendIds(Long userId);
}