package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 入群申请DTO
 * 用于用户申请加入群聊
 */
@Data
public class ApplyJoinRequest {
    private Long groupId;
}