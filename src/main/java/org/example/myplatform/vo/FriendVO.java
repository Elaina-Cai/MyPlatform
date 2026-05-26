package org.example.myplatform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友信息 VO（用于好友列表展示）
 * 包含好友用户的 ID、用户名、昵称、头像和成为好友的时间。
 * 应用场景：
 * - 用户打开"好友列表"页面时，后端返回此格式的数据
 * - 前端遍历此列表展示好友卡片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendVO {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private LocalDateTime friendAt;
    private Boolean isOnline;
    private Long unreadCount;
}