package org.example.myplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * 消息实体类
 * 写扩散模式：每条消息存入双方收件箱
 * - senderId: 发送者
 * - userId: 收件人（自己的收件箱）
 */
@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;
    private Long userId;      // 收件人（inbox 模式）
    private Long friendId;    // 聊天对象（用于会话归类）
    private String content;
    private String fileUrl;   // 文件URL（图片/视频）
    private String fileType;  // 文件类型：image/video
    private Integer type;    // 0-普通私聊，1-群邀请
    private Long groupId;    // 群邀请时关联的群ID
    private String groupName; // 群邀请时显示的群名称
    private LocalDateTime createdAt;
    private Integer isRead;  // 0-未读，1-已读
}