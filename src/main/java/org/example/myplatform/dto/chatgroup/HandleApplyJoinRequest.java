package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 处理入群申请DTO
 * 用于群主/管理员审批入群申请
 */
@Data
public class HandleApplyJoinRequest {
    private Long requestId;
    private Boolean approve;
}