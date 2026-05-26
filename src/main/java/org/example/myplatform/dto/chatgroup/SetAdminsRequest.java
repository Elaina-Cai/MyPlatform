package org.example.myplatform.dto.chatgroup;

import java.util.List;

import lombok.Data;

/**
 * 设置群聊管理员请求(多人)
 */
@Data
public class SetAdminsRequest {
    private List<Long> userIds;
    private Boolean isAdmin;
}
