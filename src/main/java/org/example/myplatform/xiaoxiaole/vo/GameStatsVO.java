package org.example.myplatform.xiaoxiaole.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStatsVO {

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
     * 用户排名
     */
    private Integer rank;

    /**
     * 关卡进度百分比
     */
    private Double progressPercent;

    /**
     * 下一关目标分数
     */
    private Integer nextTargetScore;
}