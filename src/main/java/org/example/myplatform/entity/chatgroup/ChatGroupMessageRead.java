package org.example.myplatform.entity.chatgroup;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 群消息已读实体类，从数据库表里读取到的每一行数据，表示一条群消息对一个用户的已读状态
 */
@Data
@TableName("chat_group_message_read")
public class ChatGroupMessageRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime readAt;
}