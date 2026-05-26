package org.example.myplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.service.friend.FriendService;
import org.example.myplatform.vo.FriendApplyVO;
import org.example.myplatform.vo.FriendVO;
import org.example.myplatform.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friend")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService){
        this.friendService = friendService;
    }
    /**
     * 获取我发起的申请列表
     * API: GET /api/friend/apply
     * 应用场景：
     * - 用户想查看自己发出了哪些好友申请
     * - 页面路径：个人中心 → 好友申请 → 我发出的
     */
    @GetMapping("/apply")
    public Result<List<FriendApplyVO>> getMyApplyList(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(friendService.getMyApplyList(userId));
    }
    /**
     * 获取发给我的申请列表
     * API: GET /api/friend/requests
     * 应用场景：
     * - 用户想查看谁想添加自己为好友
     * - 页面路径：个人中心 → 好友申请 → 待处理
     */
    @GetMapping("/requests")
    public Result<List<FriendApplyVO>> getRequestList(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(friendService.getRequestList(userId));
    }
    /**
     * 获取好友列表
     * API: GET /api/friend/list
     * 应用场景：
     * - 用户打开聊天页面，需要展示好友列表
     * - 前端遍历列表显示好友卡片
     */
    @GetMapping("/list")
    public Result<List<FriendVO>> getFriendList(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return Result.success(friendService.getFriendList(userId));
    }
    /**
     * 删除好友
     * API: DELETE /api/friend/{friendUserId}
     * 应用场景：
     * - 用户在好友列表中左滑或长按某个好友
     * - 点击"删除好友"按钮
     * - 注意：这是软删除，聊天记录会保留
     */
    @DeleteMapping("/{friendUserId}")
    public Result<Map<String, Object>> deleteFriend(
            @PathVariable Long friendUserId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        friendService.deleteFriend(userId, friendUserId);
        return Result.success(Map.of("message", "已删除好友"));
    }
    /**
     * 从请求中获取当前登录用户的ID
     * 通用逻辑：
     * JwtInterceptor 在验证 token 后会将 userId 存入 request 属性
     * Controller 通过此方法获取当前用户ID
     */
    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }
}