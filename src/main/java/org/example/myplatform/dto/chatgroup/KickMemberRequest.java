package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 踢出群成员DTO
 * 用于群主/管理员踢出群成员
 */
@Data
public class KickMemberRequest {
    private Long groupId;
    private Long targetUserId;
}