package org.example.myplatform.vo.chatgroup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupVO {
    private Long id;
    private String name;
    private String avatar;
    private String announcement;
    private Long ownerId;
    private String ownerNickname;
    private Integer joinType;
    private Integer invitePermission;
    private Integer allowMemberInvite;
    private Integer isMuted;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private Long unreadCount;
    private ChatGroupMessageVO lastMessage;
}