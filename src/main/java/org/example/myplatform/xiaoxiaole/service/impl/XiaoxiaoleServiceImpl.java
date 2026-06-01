package org.example.myplatform.xiaoxiaole.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.myplatform.entity.User;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.service.authanduser.UserService;
import org.example.myplatform.xiaoxiaole.dto.request.SaveRecordRequest;
import org.example.myplatform.xiaoxiaole.dto.response.RecordResponse;
import org.example.myplatform.xiaoxiaole.dto.response.RankResponse;
import org.example.myplatform.xiaoxiaole.entity.XiaoxiaoleRecord;
import org.example.myplatform.xiaoxiaole.mapper.XiaoxiaoleRecordMapper;
import org.example.myplatform.xiaoxiaole.service.XiaoxiaoleService;
import org.example.myplatform.xiaoxiaole.vo.GameStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class XiaoxiaoleServiceImpl implements XiaoxiaoleService {

    private final XiaoxiaoleRecordMapper xiaoxiaoleRecordMapper;
    private final UserService userService;

    @Override
    @Transactional
    public RecordResponse saveRecord(Long userId, SaveRecordRequest request) {
        // 1. 验证关卡范围（1-10关）
        if (request.getCurrentLevel() == null || request.getCurrentLevel() < 1 || request.getCurrentLevel() > 10) {
            throw new BadRequestException("无效的关卡编号");
        }

        // 2. 验证分数范围（0-30000分）
        if (request.getScore() == null || request.getScore() < 0 || request.getScore() > 30000) {
            throw new BadRequestException("无效的分数");
        }

        // 5. 查询用户当前记录
        XiaoxiaoleRecord record = xiaoxiaoleRecordMapper.selectByUserId(userId);

        // 7. 关卡进度验证（不能跳关）
        if (record != null && request.getCurrentLevel() > record.getHighestLevel() + 1) {
            throw new BadRequestException("请先完成前面的关卡");
        }

        // 8. 首次游戏验证（必须从第1关开始）
        if (record == null && request.getCurrentLevel() != 1) {
            throw new BadRequestException("请从第1关开始游戏");
        }

        // 9. 正常保存逻辑
        if (record == null) {
            record = new XiaoxiaoleRecord();
            record.setUserId(userId);
            record.setCurrentLevel(request.getCurrentLevel() + 1);  // 通关后进入下一关
            record.setHighestLevel(request.getCurrentLevel());
            record.setTotalScore(request.getScore());
            record.setBestScore(request.getScore());
            record.setPlayCount(1);
            xiaoxiaoleRecordMapper.insert(record);
        } else {
            record.setCurrentLevel(request.getCurrentLevel() + 1);  // 通关后进入下一关
            record.setHighestLevel(Math.max(record.getHighestLevel(), request.getCurrentLevel()));
            record.setTotalScore(record.getTotalScore() + request.getScore());
            record.setBestScore(Math.max(record.getBestScore(), request.getScore()));
            record.setPlayCount(record.getPlayCount() + 1);
            xiaoxiaoleRecordMapper.updateById(record);
        }

        return convertToResponse(record);
    }

    @Override
    public RecordResponse getRecord(Long userId) {
        XiaoxiaoleRecord record = xiaoxiaoleRecordMapper.selectByUserId(userId);

        if (record == null) {
            // 返回默认记录（第1关，分数为0）
            return RecordResponse.builder()
                    .currentLevel(1)
                    .highestLevel(1)
                    .totalScore(0L)
                    .bestScore(0L)
                    .playCount(0)
                    .build();
        }

        return convertToResponse(record);
    }

    @Override
    public List<RankResponse> getRank() {
        List<XiaoxiaoleRecord> records = xiaoxiaoleRecordMapper.selectRankList();

        return records.stream()
                .map(record -> {
                    User user = userService.getUserById(record.getUserId());
                    return RankResponse.builder()
                            .userId(record.getUserId())
                            .nickname(user != null ? user.getNickname() : "未知用户")
                            .avatar(user != null ? user.getAvatar() : null)
                            .highestLevel(record.getHighestLevel())
                            .bestScore(record.getBestScore())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public Integer getUserRank(Long userId) {
        Integer rank = xiaoxiaoleRecordMapper.selectUserRank(userId);
        return rank != null ? rank : 0;
    }

    @Override
    public GameStatsVO getGameStats(Long userId) {
        XiaoxiaoleRecord record = xiaoxiaoleRecordMapper.selectByUserId(userId);
        Integer rank = getUserRank(userId);

        if (record == null) {
            return GameStatsVO.builder()
                    .currentLevel(1)
                    .highestLevel(1)
                    .totalScore(0L)
                    .bestScore(0L)
                    .playCount(0)
                    .rank(rank)
                    .progressPercent(0.0)
                    .nextTargetScore(1000)
                    .build();
        }

        // 计算关卡进度百分比（假设共10关）
        double progressPercent = (record.getHighestLevel().doubleValue() / 10.0) * 100;

        // 下一关目标分数（假设每关目标分数递增）
        int nextTargetScore = (record.getHighestLevel() + 1) * 1000;

        return GameStatsVO.builder()
                .currentLevel(record.getCurrentLevel())
                .highestLevel(record.getHighestLevel())
                .totalScore(record.getTotalScore())
                .bestScore(record.getBestScore())
                .playCount(record.getPlayCount())
                .rank(rank)
                .progressPercent(Math.min(progressPercent, 100.0))
                .nextTargetScore(nextTargetScore)
                .build();
    }

    @Override
    @Transactional
    public void resetRecord(Long userId) {
        XiaoxiaoleRecord record = xiaoxiaoleRecordMapper.selectByUserId(userId);

        if (record != null) {
            record.setCurrentLevel(1);
            record.setHighestLevel(1);
            record.setTotalScore(0L);
            record.setBestScore(0L);
            record.setPlayCount(0);
            xiaoxiaoleRecordMapper.updateById(record);
        }
    }

    private RecordResponse convertToResponse(XiaoxiaoleRecord record) {
        return RecordResponse.builder()
                .currentLevel(record.getCurrentLevel())
                .highestLevel(record.getHighestLevel())
                .totalScore(record.getTotalScore())
                .bestScore(record.getBestScore())
                .playCount(record.getPlayCount())
                .build();
    }
}