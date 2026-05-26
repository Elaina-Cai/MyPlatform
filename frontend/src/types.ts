/** 对齐后端 org.example.myplatform.vo.Result */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/** 对齐后端 AuthVO */
export interface AuthVO {
  token: string;
  userId: number;
  username: string;
  nickname: string | null;
  avatar: string | null;
}

/** 对齐后端 User 实体（密码字段后端会置 null） */
export interface User {
  id: number;
  username: string;
  password: string | null;
  phone: string | null;
  nickname: string | null;
  avatar: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== 聊天功能相关类型 ====================

/**
 * 聊天消息 VO
 */
export interface MessageVO {
  clientId?: string;  // 客户端生成的唯一ID，用于去重
  senderId: number;
  friendId: number;
  content: string;
  fileUrl?: string;   // 文件URL
  fileType?: string;  // 文件类型：image/video
  type?: number;      // 0-普通私聊，1-群邀请
  groupId?: number;    // 群邀请时关联的群ID
  groupName?: string;  // 群邀请时显示的群名称
  typeId?: number;     // 0-普通私聊，1-群邀请（WebSocket推送用）
  createdAt: string;
  isRead: number;
  senderNickname: string;
  senderAvatar: string;
}

/**
 * WebSocket 聊天消息（发送格式）
 */
export interface ChatMessage {
  type: "chat";
  receiverId: number;
  content: string;
}

/**
 * WebSocket 推送消息
 */
export interface PushMessage {
  type: "chat" | "friend_online" | "friend_offline";
  senderId?: number;
  senderNickname?: string;
  senderAvatar?: string;
  content?: string;
  friendId?: number;
  groupId?: number;    // 群邀请时关联的群ID
  groupName?: string;  // 群邀请时显示的群名称
  typeId?: number;      // 0-普通私聊，1-群邀请
}

/**
 * 聊天会话项（用于聊天列表展示）
 */
export interface ChatSession {
  friendId: number;
  friendUsername: string;
  friendNickname: string;
  friendAvatar: string;
  lastMessage: string;
  lastMessageTime: string;
  unreadCount: number;
}

// ==================== 好友功能相关类型 ====================

/**
 * 好友信息 VO（用于好友列表展示）
 * 对应后端 FriendVO
 */
export interface FriendVO {
  userId: number;
  username: string;
  nickname: string | null;
  avatar: string | null;
  friendAt: string;
  isOnline?: boolean;
  unreadCount?: number;
}

/**
 * 好友申请信息 VO（用于申请列表展示）
 * 对应后端 FriendApplyVO
 */
export interface FriendApplyVO {
  fromUserId: number;
  fromUsername: string;
  fromNickname: string | null;
  fromAvatar: string | null;
  message: string | null;
  createdAt: string;
}

/**
 * 发送好友申请请求
 * 对应后端 FriendRequest
 */
export interface FriendRequest {
  targetUserId: number;
  message?: string;
}

// ==================== 群聊功能相关类型 ====================

/**
 * 群聊消息 VO
 */
export interface GroupMessageVO {
  id: number;
  groupId: number;
  senderId: number;
  senderNickname: string;
  senderAvatar: string;
  content: string;
  messageType: number;
  fileUrl?: string;
  fileType?: string;
  createdAt: string;
  isRead: boolean;
}

// GroupSessionVO 已在 api/chatgroup.ts 中定义并导出

/**
 * 群成员信息 VO
 */
export interface GroupMemberVO {
  id: number;
  groupId: number;
  userId: number;
  username: string;
  nickname: string;
  avatar: string | null;
  role: number;
  isMuted: number;
  muteExpireTime: string | null;
  joinedAt: string;
}

/**
 * 群聊信息 VO
 */
export interface GroupInfoVO {
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