package org.example.myplatform.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息返回对象
 * - senderId: 发送者
 * - friendId: 聊天对象（对方）
 * - senderNickname/senderAvatar: 发送者信息（从 Redis 获取）
 *
 * 前端判断：
 * if (message.senderId === currentUserId) {
 *   // 我发的，显示"我"的头像和昵称
 * } else {
 *   // 对方发的，显示 MessageVO 里的头像和昵称
 * }
 */
@Data
public class MessageVO {
    private Long senderId;
    private Long friendId;  // 聊天对象
    private String content;
    private String fileUrl;   // 文件URL
    private String fileType;  // 文件类型：image/video
    private Integer type;      // 0-普通私聊，1-群邀请
    private Long groupId;     // 群邀请时关联的群ID
    private String groupName; // 群邀请时显示的群名称
    private LocalDateTime createdAt;
    private Integer isRead;  // 0-未读，1-已读
    private String senderNickname;  // 发送者昵称从redis中获取
    private String senderAvatar;  // 发送者头像从redis中获取
}