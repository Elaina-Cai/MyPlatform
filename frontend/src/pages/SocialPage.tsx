import { useState, useEffect } from "react";
import {
  getFriendList,
  getRequestList,
  getMyApplyList,
} from "../api/friend";
import { getUserById } from "../api/auth";
import type { FriendVO, FriendApplyVO } from "../types";
import ChatPage from "./ChatPage";

declare global {
  interface Window {
    webSocket?: WebSocket;
  }
}

export default function SocialPage() {
  const [activeTab, setActiveTab] = useState<"friends" | "requests">("friends");
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<FriendApplyVO[]>([]);
  const [sentRequests, setSentRequests] = useState<FriendApplyVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [addUserId, setAddUserId] = useState("");
  const [addMessage, setAddMessage] = useState("");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState("");
  const [selectedChat, setSelectedChat] = useState<FriendVO | null>(null);

  useEffect(() => {
    void loadAllData();
  }, []);

  async function loadAllData() {
    setLoading(true);
    try {
      const [friendRes, receivedRes, sentRes] = await Promise.all([
        getFriendList(),
        getRequestList(),
        getMyApplyList(),
      ]);
      setFriends(friendRes.data || []);
      setReceivedRequests(receivedRes.data || []);
      setSentRequests(sentRes.data || []);
    } catch (error) {
      console.error("加载数据失败", error);
    } finally {
      setLoading(false);
    }
  }

  if (selectedChat) {
    return (
      <ChatPage
        friendId={selectedChat.userId}
        friendNickname={selectedChat.nickname || selectedChat.username}
        friendAvatar={selectedChat.avatar || ""}
        onBack={() => setSelectedChat(null)}
      />
    );
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
    <div className="social-page">
      <div className="page-header">
        <h1>社交</h1>
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
          className={`tab-item ${activeTab === "requests" ? "active" : ""}`}
          onClick={() => setActiveTab("requests")}
        >
          申请列表 ({receivedRequests.length + sentRequests.length})
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
                    <div key={friend.userId} className="friend-card" onClick={() => setSelectedChat(friend)}>
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
                      </div>
                      <div className="friend-actions">
                        <button
                          className="btn-chat"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedChat(friend);
                          }}
                        >
                          聊天
                        </button>
                        <button
                          className="btn-delete"
                          disabled={actionLoading === friend.userId}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteFriend(friend.userId, friend.nickname || friend.username);
                          }}
                        >
                          {actionLoading === friend.userId ? "删除中…" : "删除"}
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {activeTab === "requests" && (
              <div className="request-section">
                {receivedRequests.length > 0 && (
                  <div className="request-group">
                    <h3 className="request-group-title">收到的申请 ({receivedRequests.length})</h3>
                    {receivedRequests.map((request) => (
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
                    ))}
                  </div>
                )}

                {sentRequests.length > 0 && (
                  <div className="request-group">
                    <h3 className="request-group-title">发出的申请 ({sentRequests.length})</h3>
                    {sentRequests.map((request) => (
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
                        </div>
                        <div className="request-status">等待对方确认</div>
                      </div>
                    ))}
                  </div>
                )}

                {receivedRequests.length === 0 && sentRequests.length === 0 && (
                  <div className="empty-state">暂无好友申请</div>
                )}
              </div>
            )}
          </>
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