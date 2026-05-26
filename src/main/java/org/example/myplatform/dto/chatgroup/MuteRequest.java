package org.example.myplatform.dto.chatgroup;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 禁言用户DTO
 * 用于群主/管理员禁言用户
 */
@Data
public class MuteRequest {
    private Long groupId;
    private Long userId;
    private Boolean isMuted;
    private LocalDateTime muteExpireTime;
}