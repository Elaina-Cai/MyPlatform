import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  getFriendList,
  getRequestList,
  getMyApplyList,
} from "../api/friend";
import { getUserById } from "../api/auth";
import { getMyInvitations, applyJoinGroup } from "../api/chatgroup";
import type { ChatGroupJoinRequestVO } from "../api/chatgroup";
import type { FriendVO, FriendApplyVO } from "../types";

declare global {
  interface Window {
    webSocket?: WebSocket;
  }
}

interface FriendPageProps {
  applyCount?: number;
  onApplyRead?: () => void;
  onInviteRead?: () => void;
}

export function FriendPage({ applyCount = 0, onApplyRead, onInviteRead }: FriendPageProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialTab = searchParams.get("tab") === "invitations" ? "invitations" : "friends";
  const [activeTab, setActiveTab] = useState<"friends" | "received" | "sent" | "invitations">(initialTab as any);
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<FriendApplyVO[]>([]);
  const [sentRequests, setSentRequests] = useState<FriendApplyVO[]>([]);
  const [invitations, setInvitations] = useState<ChatGroupJoinRequestVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [addUserId, setAddUserId] = useState("");
  const [addMessage, setAddMessage] = useState("");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState("");

  useEffect(() => {
    void loadAllData();
  }, []);

  useEffect(() => {
    function handleGroupInvite() {
      void loadAllData();
    }
    window.addEventListener("group-invite", handleGroupInvite);
    return () => window.removeEventListener("group-invite", handleGroupInvite);
  }, []);

  async function loadAllData() {
    setLoading(true);
    try {
      const [friendRes, receivedRes, sentRes, inviteRes] = await Promise.all([
        getFriendList(),
        getRequestList(),
        getMyApplyList(),
        getMyInvitations(),
      ]);
      setFriends(friendRes.data || []);
      setReceivedRequests(receivedRes.data || []);
      setSentRequests(sentRes.data || []);
      setInvitations(inviteRes);
    } catch (error) {
      console.error("加载数据失败", error);
    } finally {
      setLoading(false);
    }
  }

  async function handleAccept(fromUserId: number) {
    setActionLoading(fromUserId);
    if (!window.webSocket || window.webSocket.readyState !== WebSocket.OPEN) {
      alert("WebSocket未连接，请稍后重试");
      setActionLoading(null);
      return;
    }
    window.webSocket.send(JSON.stringify({ type: "accept_friend", fromUserId }));
    setTimeout(() => {
      void loadAllData();
      setActionLoading(null);
    }, 500);
  }

  async function handleReject(fromUserId: number) {
    if (!confirm("确定要拒绝此申请吗？")) return;
    setActionLoading(fromUserId);
    if (!window.webSocket || window.webSocket.readyState !== WebSocket.OPEN) {
      alert("WebSocket未连接，请稍后重试");
      setActionLoading(null);
      return;
    }
    window.webSocket.send(JSON.stringify({ type: "reject_friend", fromUserId }));
    setTimeout(() => {
      void loadAllData();
      setActionLoading(null);
    }, 500);
  }

  async function handleDeleteFriend(friendUserId: number, friendName: string) {
    if (!confirm(`确定要删除好友"${friendName}"吗？`)) return;
    setActionLoading(friendUserId);
    try {
      const { deleteFriend } = await import("../api/friend");
      await deleteFriend(friendUserId);
      await loadAllData();
    } catch (error) {
      console.error("删除好友失败", error);
      alert("操作失败，请重试");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleInvitation(invitationId: number) {
    setActionLoading(invitationId);
    try {
      const invite = invitations.find(i => i.id === invitationId);
      if (!invite) return;
      await applyJoinGroup(invite.groupId);
      await loadAllData();
      if (onInviteRead) onInviteRead();
      alert("已提交入群申请，请等待群主审批");
    } catch (error) {
      console.error("提交申请失败", error);
      alert("操作失败，请重试");
    } finally {
      setActionLoading(null);
    }
  }

  function formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return "—";
    const d = new Date(dateStr);
    if (Number.isNaN(d.getTime())) return dateStr;
    return d.toLocaleString("zh-CN", { hour12: false });
  }

  function getAvatarText(name: string | null | undefined, fallback: string): string {
    if (name && name.length > 0) {
      return name.charAt(0).toUpperCase();
    }
    return fallback.toUpperCase();
  }

  async function handleAddFriend() {
    const targetUserId = parseInt(addUserId.trim(), 10);
    if (isNaN(targetUserId) || targetUserId <= 0) {
      setAddError("请输入有效的用户ID");
      return;
    }
    setAddLoading(true);
    setAddError("");
    try {
      await getUserById(targetUserId);
    } catch {
      setAddError("用户不存在");
      setAddLoading(false);
      return;
    }
    if (!window.webSocket || window.webSocket.readyState !== WebSocket.OPEN) {
      setAddError("WebSocket未连接，请稍后重试");
      setAddLoading(false);
      return;
    }
    window.webSocket.send(JSON.stringify({
      type: "friend_apply",
      targetUserId,
      message: addMessage.trim() || undefined,
    }));
    const handleSuccess = () => {
      window.removeEventListener("friend-apply-success", handleSuccess);
      setShowAddModal(false);
      setAddUserId("");
      setAddMessage("");
      void loadAllData();
      setAddLoading(false);
    };
    const handleError = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      window.removeEventListener("friend-apply-error", handleError);
      setAddError(detail?.error || "添加失败，请重试");
      setAddLoading(false);
    };
    window.addEventListener("friend-apply-success", handleSuccess);
    window.addEventListener("friend-apply-error", handleError);
    setTimeout(() => {
      window.removeEventListener("friend-apply-success", handleSuccess);
      window.removeEventListener("friend-apply-error", handleError);
      if (addLoading) {
        setAddError("发送超时，请稍后重试");
        setAddLoading(false);
      }
    }, 5000);
  }

  return (
    <div className="friend-page">
      <div className="page-header">
        <button className="back-btn" onClick={() => {
          window.dispatchEvent(new CustomEvent("switch-nav", { detail: { nav: "home" } }));
          navigate("/dashboard");
        }}>
          ← 返回
        </button>
        <h1>好友管理</h1>
        <button className="btn-add-friend" onClick={() => setShowAddModal(true)}>
          + 添加好友
        </button>
      </div>

      <div className="tab-bar">
        <button
          className={`tab-item ${activeTab === "friends" ? "active" : ""}`}
          onClick={() => setActiveTab("friends")}
        >
          好友列表 ({friends.length})
        </button>
        <button
          className={`tab-item ${activeTab === "received" ? "active" : ""}`}
           onClick={() => {
            setActiveTab("received");
            onApplyRead?.();
          }}
        >
          收到的申请 ({receivedRequests.length})
          {applyCount > 0 && activeTab !== "received" && (
            <span className="tab-badge">{applyCount > 99 ? "99+" : applyCount}</span>
          )}
        </button>
        <button
          className={`tab-item ${activeTab === "sent" ? "active" : ""}`}
          onClick={() => setActiveTab("sent")}
        >
          发出的申请 ({sentRequests.length})
        </button>
        <button
          className={`tab-item ${activeTab === "invitations" ? "active" : ""}`}
          onClick={() => setActiveTab("invitations")}
        >
          群邀请 ({invitations.length})
        </button>
      </div>

      <div className="tab-content">
        {loading ? (
          <div className="loading">加载中…</div>
        ) : (
          <>
            {activeTab === "friends" && (
              <div className="friend-list">
                {friends.length === 0 ? (
                  <div className="empty-state">暂无好友，快去添加吧！</div>
                ) : (
                  friends.map((friend) => (
                    <div key={friend.userId} className="friend-card">
                      <div className="friend-avatar">
                        {friend.avatar ? (
                          <img src={friend.avatar} alt="avatar" />
                        ) : (
                          <span>{getAvatarText(friend.nickname, friend.username)}</span>
                        )}
                      </div>
                      <div className="friend-info">
                        <div className="friend-name">
                          {friend.nickname || friend.username}
                        </div>
                        <div className="friend-username">@{friend.username}</div>
                        <div className="friend-time">
                          成为好友于 {formatDate(friend.friendAt)}
                        </div>
                      </div>
                      <div className="friend-actions">
                        <button
                          className="btn-chat"
                          onClick={() => navigate(`/chat/${friend.userId}`)}
                        >
                          聊天
                        </button>
                        <button
                          className="btn-delete"
                          disabled={actionLoading === friend.userId}
                          onClick={() => handleDeleteFriend(friend.userId, friend.nickname || friend.username)}
                        >
                          {actionLoading === friend.userId ? "删除中…" : "删除"}
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {activeTab === "received" && (
              <div className="request-list">
                {receivedRequests.length === 0 ? (
                  <div className="empty-state">暂无好友申请</div>
                ) : (
                  receivedRequests.map((request) => (
                    <div key={request.fromUserId} className="request-card">
                      <div className="request-avatar">
                        {request.fromAvatar ? (
                          <img src={request.fromAvatar} alt="avatar" />
                        ) : (
                          <span>{getAvatarText(request.fromNickname, request.fromUsername)}</span>
                        )}
                      </div>
                      <div className="request-info">
                        <div className="request-name">
                          {request.fromNickname || request.fromUsername}
                        </div>
                        <div className="request-username">@{request.fromUsername}</div>
                        {request.message && (
                          <div className="request-message">留言：{request.message}</div>
                        )}
                        <div className="request-time">
                          申请时间 {formatDate(request.createdAt)}
                        </div>
                      </div>
                      <div className="request-actions">
                        <button
                          className="btn-accept"
                          disabled={actionLoading === request.fromUserId}
                          onClick={() => handleAccept(request.fromUserId)}
                        >
                          {actionLoading === request.fromUserId ? "处理中…" : "同意"}
                        </button>
                        <button
                          className="btn-reject"
                          disabled={actionLoading === request.fromUserId}
                          onClick={() => handleReject(request.fromUserId)}
                        >
                          拒绝
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {activeTab === "sent" && (
              <div className="request-list">
                {sentRequests.length === 0 ? (
                  <div className="empty-state">暂无发出的申请</div>
                ) : (
                  sentRequests.map((request) => (
                    <div key={request.fromUserId} className="request-card sent">
                      <div className="request-avatar">
                        {request.fromAvatar ? (
                          <img src={request.fromAvatar} alt="avatar" />
                        ) : (
                          <span>{getAvatarText(request.fromNickname, request.fromUsername)}</span>
                        )}
                      </div>
                      <div className="request-info">
                        <div className="request-name">
                          {request.fromNickname || request.fromUsername}
                        </div>
                        <div className="request-username">@{request.fromUsername}</div>
                        <div className="request-time">
                          申请时间 {formatDate(request.createdAt)}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}
          </>
        )}

        {activeTab === "invitations" && (
          <div className="request-list">
            {invitations.length === 0 ? (
              <div className="empty-state">暂无群邀请</div>
            ) : (
              invitations.map((invite) => (
                <div key={invite.id} className="request-card">
                  <div className="request-info">
                    <div className="request-name">{invite.groupName}</div>
                    <div className="request-message">邀请人：{invite.inviterNickname || "群主"}</div>
                    <div className="request-time">{formatDate(invite.createdAt)}</div>
                  </div>
                  <div className="request-actions">
                    <button
                      className="btn-accept"
                      disabled={actionLoading === invite.id}
                      onClick={() => handleInvitation(invite.id)}
                    >
                      {actionLoading === invite.id ? "处理中…" : "提交入群申请"}
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {showAddModal && (
        <div className="modal-overlay" onClick={() => setShowAddModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>添加好友</h3>
            <div className="modal-field">
              <label>用户ID</label>
              <input
                type="number"
                placeholder="请输入对方用户ID"
                value={addUserId}
                onChange={(e) => setAddUserId(e.target.value)}
                disabled={addLoading}
              />
            </div>
            <div className="modal-field">
              <label>留言（选填）</label>
              <input
                type="text"
                placeholder="我是…"
                value={addMessage}
                onChange={(e) => setAddMessage(e.target.value)}
                disabled={addLoading}
              />
            </div>
            {addError && <div className="modal-error">{addError}</div>}
            <div className="modal-actions">
              <button className="btn-cancel" onClick={() => setShowAddModal(false)} disabled={addLoading}>
                取消
              </button>
              <button className="btn-confirm" onClick={() => void handleAddFriend()} disabled={addLoading}>
                {addLoading ? "发送中…" : "发送申请"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}