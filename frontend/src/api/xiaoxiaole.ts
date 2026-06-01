import { apiRequest } from "./http";

export interface SaveRecordRequest {
  currentLevel: number;
  score: number;
  moves: number;
  timeSpent: number;
}

export interface RecordResponse {
  currentLevel: number;
  highestLevel: number;
  totalScore: number;
  bestScore: number;
  playCount: number;
}

export interface RankResponse {
  rank: number;
  userId: number;
  nickname: string;
  avatar: string;
  highestLevel: number;
  bestScore: number;
}

export interface GameStatsResponse {
  currentLevel: number;
  highestLevel: number;
  totalScore: number;
  bestScore: number;
  playCount: number;
  rank: number;
  progressPercent: number;
  nextTargetScore: number;
}

export const xiaoxiaoleApi = {
  /**
   * 保存游戏记录
   */
  saveRecord: async (data: SaveRecordRequest) => {
    return await apiRequest<RecordResponse>("/api/xiaoxiaole/record", {
      method: "POST",
      body: data,
      auth: true,
    });
  },

  /**
   * 获取用户游戏记录
   */
  getRecord: async () => {
    return await apiRequest<RecordResponse>("/api/xiaoxiaole/record", {
      method: "GET",
      auth: true,
    });
  },

  /**
   * 获取游戏统计信息（包含排名和进度）
   */
  getGameStats: async () => {
    return await apiRequest<GameStatsResponse>("/api/xiaoxiaole/stats", {
      method: "GET",
      auth: true,
    });
  },

  /**
   * 获取排行榜
   */
  getRank: async () => {
    return await apiRequest<RankResponse[]>("/api/xiaoxiaole/rank", {
      method: "GET",
      auth: true,
    });
  },

  /**
   * 获取用户排名
   */
  getMyRank: async () => {
    return await apiRequest<{ rank: number }>("/api/xiaoxiaole/rank/my", {
      method: "GET",
      auth: true,
    });
  },

  /**
   * 重置游戏进度
   */
  resetRecord: async () => {
    return await apiRequest<{ message: string }>("/api/xiaoxiaole/record/reset", {
      method: "POST",
      auth: true,
    });
  },
};