package org.example.myplatform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友申请信息 VO（用于申请列表展示）
 *
 * 应用场景：
 * - 用户打开"好友申请"页面时，后端返回此格式的数据
 * - 区分两种情况：我发起的申请、发给“我”的申请
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendApplyVO {
    private Long fromUserId;
    private String fromUsername;
    private String fromNickname;
    private String fromAvatar;
    private String message;  // 申请留言
    private LocalDateTime createdAt;
}
