package org.example.myplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系实体类
 * 表结构：friend 表
 * 应用场景：
 * - 存储用户之间的好友申请和好友关系
 * - status 字段区分申请的不同状态
 * - deleted 字段实现软删除，删除好友时保留历史关系（用于聊天记录关联）
 */
@Data
@TableName("friend")
public class Friend {
    /**
     * 主键ID，自增
     * 应用场景：唯一标识每一条好友申请/关系记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 申请发起者用户ID
     * 应用场景：
     * - 用户A给用户B发申请时，fromUserId = A的ID
     * - 用于查询"我发出的申请列表"
     */
    private Long fromUserId;
    /**
     * 申请接收者用户ID
     * 应用场景：
     * - 用户A给用户B发申请时，toUserId = B的ID
     * - 用于查询"发给我的申请列表"
     */
    private Long toUserId;
    /**
     * 好友申请状态
     * 应用场景：
     * - 0：待处理（申请中）
     * - 1：已同意（已成为好友）
     * - 2：已拒绝
     * 应用场景：
     * - 前端根据 status 显示不同的按钮（待处理时显示同意/拒绝，已是好友时显示删除）
     */
    private Integer status;
    /**
     * 申请留言（可选）
     * 应用场景：
     * - 用户在申请好友时可以填写一句话，如"我是XXX同学"或"好久不见"
     */
    private String message;
    /**
     * 创建时间（申请发起时间）
     * 应用场景：
     * - 按时间排序显示申请列表
     * - 记录申请发起的具体时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 更新时间（状态变更时间）
     * 应用场景：
     * - 好友关系建立时记录成为好友的时间
     * - 用于好友列表按最近联系时间排序
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /**
     * 逻辑删除标记
     * 应用场景：
     * - 删除好友时设置为1（软删除），保留记录
     * - 原因：聊天记录需要通过 (userA, userB) 关联查询，删除好友关系不影响历史聊天
     * - 查询好友列表时自动过滤 deleted = 1 的记录
     */
    @TableLogic
    private Integer deleted;
}