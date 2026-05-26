package org.example.myplatform.entity.chatgroup;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 群消息实体类，从数据库表里读取到的每一行数据，表示一条群消息
 */
@Data
@TableName("chat_group_message")
public class ChatGroupMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long senderId;
    private String content;
    private Integer messageType;
    private String fileUrl;
    private String fileType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}