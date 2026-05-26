package org.example.myplatform.entity.chatgroup;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊实体类，从数据库表里读取到的每一行数据，表示一个群聊
 */
@Data
@TableName("chat_group")
public class ChatGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String avatar;
    private String announcement;
    private Long ownerId;
    private Integer joinType;
    private Integer invitePermission;
    private Integer allowMemberInvite;
    private Integer isMuted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}