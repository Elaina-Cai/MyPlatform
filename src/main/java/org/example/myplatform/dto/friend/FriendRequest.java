package org.example.myplatform.dto.friend;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 好友申请DTO（用于前端提交申请申请）
 * 应用场景：
 * - 前端用户在"好友列表"页面点击"申请好友"按钮时，提交此DTO
 * - 后端根据此DTO创建好友申请记录
 */
@Data
public class FriendRequest {
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    private String message;
}
