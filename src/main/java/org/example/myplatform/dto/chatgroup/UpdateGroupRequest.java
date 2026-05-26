package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 更新群聊DTO
 * 用于更新群聊信息
 */
@Data
public class UpdateGroupRequest {
    private String name;
    private String avatar;
    private String announcement;
    private Integer joinType;
    private Integer invitePermission;
    private Integer allowMemberInvite;
}