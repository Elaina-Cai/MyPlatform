package org.example.myplatform.xiaoxiaole.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordResponse {

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
}