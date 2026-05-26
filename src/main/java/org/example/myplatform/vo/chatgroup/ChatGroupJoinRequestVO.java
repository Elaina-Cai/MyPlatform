package org.example.myplatform.vo.chatgroup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupJoinRequestVO {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long applicantId;
    private String applicantNickname;
    private String applicantAvatar;
    private Long inviterId;
    private String inviterNickname;
    private Integer status;
    private Integer type;
    private LocalDateTime createdAt;
}