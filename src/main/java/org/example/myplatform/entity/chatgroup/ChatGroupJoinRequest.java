package org.example.myplatform.entity.chatgroup;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 群加入请求实体类，从数据库表里读取到的每一行数据，表示一个加入某个群聊的请求
 */
@Data
@TableName("chat_group_join_request")
public class ChatGroupJoinRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long applicantId;
    private Long inviterId;
    private Integer status;
    private Integer type;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private Long handledBy;
}