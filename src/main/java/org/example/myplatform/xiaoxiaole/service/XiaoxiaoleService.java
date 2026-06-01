package org.example.myplatform.xiaoxiaole.service;

import org.example.myplatform.xiaoxiaole.dto.request.SaveRecordRequest;
import org.example.myplatform.xiaoxiaole.dto.response.RecordResponse;
import org.example.myplatform.xiaoxiaole.dto.response.RankResponse;
import org.example.myplatform.xiaoxiaole.vo.GameStatsVO;

import java.util.List;

public interface XiaoxiaoleService {

    /**
     * 保存游戏记录
     */
    RecordResponse saveRecord(Long userId, SaveRecordRequest request);

    /**
     * 获取用户游戏记录
     */
    RecordResponse getRecord(Long userId);

    /**
     * 获取排行榜
     */
    List<RankResponse> getRank();

    /**
     * 获取用户排名
     */
    Integer getUserRank(Long userId);

    /**
     * 获取游戏统计信息（包含排名和进度）
     */
    GameStatsVO getGameStats(Long userId);

    /**
     * 重置游戏进度
     */
    void resetRecord(Long userId);
}