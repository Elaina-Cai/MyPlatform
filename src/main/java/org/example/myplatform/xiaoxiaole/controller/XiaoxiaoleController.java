package org.example.myplatform.xiaoxiaole.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.vo.Result;
import org.example.myplatform.xiaoxiaole.dto.request.SaveRecordRequest;
import org.example.myplatform.xiaoxiaole.dto.response.RecordResponse;
import org.example.myplatform.xiaoxiaole.dto.response.RankResponse;
import org.example.myplatform.xiaoxiaole.service.XiaoxiaoleService;
import org.example.myplatform.xiaoxiaole.vo.GameStatsVO;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/xiaoxiaole")
public class XiaoxiaoleController {

    private final XiaoxiaoleService xiaoxiaoleService;

    public XiaoxiaoleController(XiaoxiaoleService xiaoxiaoleService) {
        this.xiaoxiaoleService = xiaoxiaoleService;
    }

    /**
     * 保存游戏记录
     */
    @PostMapping("/record")
    public Result<RecordResponse> saveRecord(
            @RequestBody SaveRecordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        RecordResponse response = xiaoxiaoleService.saveRecord(userId, request);
        return Result.success(response);
    }

    /**
     * 获取用户游戏记录
     */
    @GetMapping("/record")
    public Result<RecordResponse> getRecord(HttpServletRequest request) {
        Long userId = getUserId(request);
        RecordResponse response = xiaoxiaoleService.getRecord(userId);
        return Result.success(response);
    }

    /**
     * 获取游戏统计信息（包含排名和进度）
     */
    @GetMapping("/stats")
    public Result<GameStatsVO> getGameStats(HttpServletRequest request) {
        Long userId = getUserId(request);
        GameStatsVO stats = xiaoxiaoleService.getGameStats(userId);
        return Result.success(stats);
    }

    /**
     * 获取排行榜
     */
    @GetMapping("/rank")
    public Result<List<RankResponse>> getRank() {
        List<RankResponse> ranks = xiaoxiaoleService.getRank();
        return Result.success(ranks);
    }

    /**
     * 获取用户排名
     */
    @GetMapping("/rank/my")
    public Result<Map<String, Integer>> getMyRank(HttpServletRequest request) {
        Long userId = getUserId(request);
        Integer rank = xiaoxiaoleService.getUserRank(userId);
        Map<String, Integer> result = new HashMap<>();
        result.put("rank", rank);
        return Result.success(result);
    }

    /**
     * 重置游戏进度
     */
    @PostMapping("/record/reset")
    public Result<Map<String, String>> resetRecord(HttpServletRequest request) {
        Long userId = getUserId(request);
        xiaoxiaoleService.resetRecord(userId);
        Map<String, String> result = new HashMap<>();
        result.put("message", "游戏进度已重置");
        return Result.success(result);
    }

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }
}