package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 邀请入群DTO
 * 用于群主/管理员邀请好友加入群聊
 */
@Data
public class InviteRequest {
    private Long groupId;
    private Long friendId;
}