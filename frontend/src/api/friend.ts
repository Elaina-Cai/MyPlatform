import { apiRequest } from "./http";
import type { FriendVO, FriendApplyVO } from "../types";

/**
 * 获取我发起的申请列表
 */
export async function getMyApplyList() {
  return apiRequest<FriendApplyVO[]>("/api/friend/apply", {
    method: "GET",
  });
}

/**
 * 获取发给我的申请列表（待处理）
 */
export async function getRequestList() {
  return apiRequest<FriendApplyVO[]>("/api/friend/requests", {
    method: "GET",
  });
}

/**
 * 获取好友列表
 */
export async function getFriendList() {
  return apiRequest<FriendVO[]>("/api/friend/list", {
    method: "GET",
  });
}

/**
 * 删除好友
 * @param friendUserId - 要删除的好友的用户ID
 */
export async function deleteFriend(friendUserId: number) {
  return apiRequest<{ message: string }>(`/api/friend/${friendUserId}`, {
    method: "DELETE",
  });
}