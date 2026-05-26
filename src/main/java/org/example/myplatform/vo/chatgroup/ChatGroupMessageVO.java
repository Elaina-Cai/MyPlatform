package org.example.myplatform.vo.chatgroup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupMessageVO {
    private Long id;
    private Long groupId;
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;
    private String content;
    private Integer messageType;
    private String fileUrl;
    private String fileType;
    private LocalDateTime createdAt;
    private Boolean isRead;
}