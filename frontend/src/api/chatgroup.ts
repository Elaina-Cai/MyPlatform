import { apiRequest } from "./http";
import type { GroupMessageVO, GroupMemberVO } from "../types";

interface GroupVO {
  id: number;
  name: string;
  avatar: string | null;
  announcement: string;
  ownerId: number;
  ownerNickname: string;
  joinType: number;
  invitePermission: number;
  allowMemberInvite: number;
  isMuted: number;
  memberCount: number;
  createdAt: string;
  unreadCount: number;
  lastMessage: GroupMessageVO | null;
}

interface GroupSessionVO {
  id: number;
  name: string;
  avatar: string | null;
  memberCount: number;
  unreadCount: number;
  role: number;
  ownerId: number;
  lastMessage: GroupMessageVO | null;
  lastMessageTime: string;
}

export type { GroupVO, GroupSessionVO };

/**
 * 获取我的群聊列表
 */
export async function getMyGroups(): Promise<GroupSessionVO[]> {
  const res = await apiRequest<GroupVO[]>("/api/group/my");
  return (res.data || []).map(g => ({
    id: g.id,
    name: g.name,
    avatar: g.avatar,
    memberCount: g.memberCount,
    unreadCount: g.unreadCount,
    role: 0, // role 字段暂未使用，通过 ownerId 判断群主
    ownerId: g.ownerId,
    lastMessage: g.lastMessage,
    lastMessageTime: g.lastMessage?.createdAt || "",
  }));
}

/**
 * 获取群聊信息
 */
export async function getGroupInfo(groupId: number): Promise<GroupVO> {
  const res = await apiRequest<GroupVO>(`/api/group/${groupId}`);
  return res.data!;
}

/**
 * 获取群聊消息历史
 */
export async function getGroupMessages(groupId: number, page: number = 1, size: number = 20): Promise<GroupMessageVO[]> {
  const res = await apiRequest<GroupMessageVO[]>(`/api/group/${groupId}/message?page=${page}&size=${size}`);
  return res.data || [];
}

export interface UploadResult {
  fileUrl: string;
  fileType: string;
}

export async function uploadFile(
  file: File,
  onProgress?: (percent: number) => void
): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append("file", file);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const result = JSON.parse(xhr.responseText);
        if (result.code === 200) {
          resolve(result.data);
        } else {
          reject(new Error(result.message || "上传失败"));
        }
      } else {
        reject(new Error("上传失败: " + xhr.status));
      }
    };

    xhr.onerror = () => reject(new Error("网络错误"));

    const token = localStorage.getItem("token");
    xhr.open("POST", "/api/upload/file");
    xhr.setRequestHeader("Authorization", token ? `Bearer ${token}` : "");

    xhr.send(formData);
  });
}

export async function deleteFile(fileUrl: string): Promise<void> {
  await apiRequest("/api/upload/file?fileUrl=" + encodeURIComponent(fileUrl), {
    method: "DELETE",
  });
}

/**
 * 获取群成员列表
 */
export async function getGroupMembers(groupId: number): Promise<GroupMemberVO[]> {
  const res = await apiRequest<GroupMemberVO[]>(`/api/group/${groupId}/members`);
  return res.data || [];
}

/**
 * 创建群聊
 */
export async function createGroup(name: string, memberIds: number[]): Promise<number> {
  const res = await apiRequest<{ id: number }>("/api/group/create", {
    method: "POST",
    body: { name, memberIds },
  });
  return res.data!.id;
}

/**
 * 申请加入群聊
 */
export async function applyJoinGroup(groupId: number): Promise<void> {
  await apiRequest("/api/group/apply", {
    method: "POST",
    body: { groupId },
  });
}

/**
 * 获取群聊未读消息数
 */
export async function getGroupUnreadCount(groupId: number): Promise<number> {
  const res = await apiRequest<{ unreadCount: number }>(`/api/group/${groupId}/unread`);
  return res.data?.unreadCount || 0;
}

/**
 * 标记群聊消息已读
 */
export async function markGroupMessageRead(groupId: number, messageId: number): Promise<void> {
  await apiRequest(`/api/group/${groupId}/message/${messageId}/read`, {
    method: "POST",
  });
}

/**
 * 邀请好友入群
 */
export async function inviteToGroup(groupId: number, friendId: number): Promise<void> {
  await apiRequest("/api/group/invite", {
    method: "POST",
    body: { groupId, friendId },
  });
}

/**
 * 踢出群成员
 */
export async function kickMember(groupId: number, targetUserId: number): Promise<void> {
  await apiRequest(`/api/group/${groupId}/kick`, {
    method: "POST",
    body: { groupId, targetUserId },
  });
}

/**
 * 设置/取消管理员
 */
export async function setAdmins(groupId: number, userIds: number[], isAdmin: boolean): Promise<void> {
  await apiRequest(`/api/group/${groupId}/admins`, {
    method: "POST",
    body: { userIds, isAdmin },
  });
}

/**
 * 获取我的群聊邀请列表
 */
export interface ChatGroupJoinRequestVO {
  id: number;
  groupId: number;
  groupName: string;
  inviterId: number;
  inviterNickname: string;
  type: number;
  status: number;
  createdAt: string;
}

export async function getMyInvitations(): Promise<ChatGroupJoinRequestVO[]> {
  const res = await apiRequest<ChatGroupJoinRequestVO[]>("/api/group/invitations");
  return res.data || [];
}

/**
 * 同意入群邀请
 */
export async function acceptInvitation(invitationId: number): Promise<void> {
  await apiRequest(`/api/group/invitations/${invitationId}`, {
    method: "POST",
    body: { accept: true },
  });
}

/**
 * 拒绝入群邀请
 */
export async function rejectInvitation(invitationId: number): Promise<void> {
  await apiRequest(`/api/group/invitations/${invitationId}`, {
    method: "POST",
    body: { accept: false },
  });
}

/**
 * 获取群聊待处理入群申请
 */
export async function getGroupPendingRequests(groupId: number): Promise<GroupJoinRequestVO[]> {
  const res = await apiRequest<GroupJoinRequestVO[]>(`/api/group/${groupId}/requests`);
  return res.data || [];
}

/**
 * 处理入群申请（同意/拒绝）
 */
export async function handleGroupRequest(requestId: number, accept: boolean): Promise<void> {
  await apiRequest("/api/group/request/handle", {
    method: "POST",
    body: { requestId, approve: accept },
  });
}

interface GroupJoinRequestVO {
  id: number;
  groupId: number;
  groupName: string;
  applicantId: number;
  applicantNickname: string;
  applicantAvatar: string;
  inviterId: number | null;
  inviterNickname: string | null;
  type: number;
  status: number;
  createdAt: string;
}

export type { GroupJoinRequestVO };