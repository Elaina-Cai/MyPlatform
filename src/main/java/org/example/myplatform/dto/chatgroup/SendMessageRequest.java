package org.example.myplatform.dto.chatgroup;

import lombok.Data;

/**
 * 发送消息DTO
 * 用于发送消息到群聊
 */
@Data
public class SendMessageRequest {
    private Long groupId;
    private String content;
    private Integer messageType;
    private String fileUrl;
    private String fileType;
}