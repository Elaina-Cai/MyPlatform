package org.example.myplatform.entity.chatgroup;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 群成员实体类，从数据库表里读取到的每一行数据，表示一个群成员
 * */
@Data
@TableName("chat_group_member")
public class ChatGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long userId;
    private Integer role;
    private Integer isMuted;
    private LocalDateTime muteExpireTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    // 未读消息数量
    private Long unreadCount;
    public void setUnreadCount(long l) {
        unreadCount = l;
    }
}