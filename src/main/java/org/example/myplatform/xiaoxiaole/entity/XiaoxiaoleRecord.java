package org.example.myplatform.xiaoxiaole.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xiaoxiaole_record")
public class XiaoxiaoleRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 当前关卡
     */
    private Integer currentLevel;

    /**
     * 历史最高关卡
     */
    private Integer highestLevel;

    /**
     * 总分数
     */
    private Long totalScore;

    /**
     * 最高分
     */
    private Long bestScore;

    /**
     * 游玩次数
     */
    private Integer playCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}