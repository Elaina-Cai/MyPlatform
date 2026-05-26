package org.example.myplatform.vo.chatgroup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupMemberVO {
    private Long id;
    private Long groupId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private Integer role;
    private Integer isMuted;
    private LocalDateTime muteExpireTime;
    private LocalDateTime joinedAt;
}