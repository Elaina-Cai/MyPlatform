package org.example.myplatform.xiaoxiaole.dto.request;

import lombok.Data;

@Data
public class SaveRecordRequest {

    /**
     * 当前关卡
     */
    private Integer currentLevel;

    /**
     * 本次得分
     */
    private Long score;

    /**
     * 操作次数
     */
    private Integer moves;

    /**
     * 游戏时长（秒）
     */
    private Integer timeSpent;
}