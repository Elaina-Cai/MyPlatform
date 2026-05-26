package org.example.myplatform.dto.friend;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友响应（包含好友用户信息）
 * 包含好友用户的 ID、用户名、昵称、头像和成为好友的时间。
 */
@Data
public class FriendResponse {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private LocalDateTime createdAt;  // 好友关系创建时间
}
