package org.example.myplatform.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系视图对象（FriendRelationVO）
 * 用于表示好友关系的详细信息，包含当前用户ID、好友用户ID、好友关系状态和创建时间。
 */
@Data
public class FriendRelationVO {
    private Long fromUserId;
    private Long toUserId;
    private Integer status;
    private LocalDateTime createdAt;
}