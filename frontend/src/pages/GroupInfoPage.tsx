import { useEffect, useState } from "react";
import type { GroupMemberVO } from "../types";
import { getGroupInfo, getGroupMembers, kickMember, setAdmins, getGroupPendingRequests, handleGroupRequest, type GroupVO, type GroupJoinRequestVO } from "../api/chatgroup";
import { getFriendList } from "../api/friend";
import type { FriendVO } from "../types";
import { useAuth } from "../auth/AuthContext";

interface GroupInfoPageProps {
  groupId: number;
  onBack: () => void;
}

export default function GroupInfoPage({ groupId, onBack }: GroupInfoPageProps) {
  const { user } = useAuth();
  const [groupInfo, setGroupInfo] = useState<GroupVO | null>(null);
  const [members, setMembers] = useState<GroupMemberVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [showMembers, setShowMembers] = useState(false);
  const [showInvite, setShowInvite] = useState(false);
  const [showRequests, setShowRequests] = useState(false);
  const [requests, setRequests] = useState<GroupJoinRequestVO[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [inviting, setInviting] = useState<number | null>(null);

  useEffect(() => {
    void loadData();
  }, [groupId]);

  useEffect(() => {
    if (showInvite && friends.length === 0) {
      void loadFriends();
    }
  }, [showInvite]);

  useEffect(() => {
    if ((isOwner() || isAdmin()) && members.length > 0) {
      void loadPendingCount();
    }
  }, [groupId, members]);

  async function loadPendingCount() {
    try {
      const list = await getGroupPendingRequests(groupId);
      setPendingCount(list.filter((r: GroupJoinRequestVO) => r.status === 0).length);
    } catch (err) {
      console.error("加载待处理数量失败", err);
    }
  }

  useEffect(() => {
    function handleMemberAdded(e: Event) {
      const detail = (e as CustomEvent).detail;
      if (detail.groupId === groupId) {
        void loadGroupMembers();
      }
    }
    window.addEventListener("group-member-added", handleMemberAdded);
    return () => window.removeEventListener("group-member-added", handleMemberAdded);
  }, [groupId]);

  async function loadGroupMembers() {
    try {
      const memberList = await getGroupMembers(groupId);
      setMembers(memberList);
    } catch (err) {
      console.error("加载群成员失败", err);
    }
  }

  async function loadData() {
    try {
      const [info, memberList] = await Promise.all([
        getGroupInfo(groupId),
        getGroupMembers(groupId),
      ]);
      setGroupInfo(info);
      setMembers(memberList);
    } catch (err) {
      console.error("加载群信息失败", err);
    } finally {
      setLoading(false);
    }
  }

  async function loadFriends() {
    try {
      const res = await getFriendList();
      setFriends(res.data || []);
    } catch (err) {
      console.error("加载好友列表失败", err);
    }
  }

  async function loadRequests() {
    try {
      const list = await getGroupPendingRequests(groupId);
      setRequests(list);
    } catch (err) {
      console.error("加载入群申请失败", err);
    }
  }

  async function handleRequest(requestId: number, accept: boolean) {
    try {
      await handleGroupRequest(requestId, accept);
      void loadRequests();
      void loadData();
      void loadPendingCount();
      alert(accept ? "已同意" : "已拒绝");
    } catch (err) {
      console.error("处理申请失败", err);
      alert("操作失败，请重试");
    }
  }

  async function handleInvite(friendId: number) {
    console.log("发送邀请: groupId=", groupId, "friendId=", friendId);
    if (!window.webSocket || window.webSocket.readyState !== WebSocket.OPEN) {
      alert("WebSocket未连接，请稍后重试");
      return;
    }
    setInviting(friendId);
    window.webSocket.send(JSON.stringify({ type: "group_invite", groupId, friendId }));
    const handleSuccess = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail.groupId === groupId) {
        window.removeEventListener("group-invite-success", handleSuccess);
        window.removeEventListener("group-invite-error", handleError);
        setInviting(null);
        void loadGroupMembers();
        alert("邀请已发送");
      }
    };
    const handleError = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      window.removeEventListener("group-invite-success", handleSuccess);
      window.removeEventListener("group-invite-error", handleError);
      setInviting(null);
      alert(detail.error || "邀请失败");
    };
    window.addEventListener("group-invite-success", handleSuccess);
    window.addEventListener("group-invite-error", handleError);
    setTimeout(() => {
      window.removeEventListener("group-invite-success", handleSuccess);
      window.removeEventListener("group-invite-error", handleError);
      if (inviting === friendId) {
        setInviting(null);
      }
    }, 5000);
  }

  function isOwner() {
    return groupInfo?.ownerId === user?.id;
  }

  function isAdmin() {
    const myMember = members.find(m => m.userId === user?.id);
    return myMember?.role === 1;
  }

  function canKick(member: GroupMemberVO): boolean {
    if (isOwner()) {
      return member.role !== 2;  // 群主可以踢管理员(role=1)和成员(role=0)，不能踢自己(role=2)
    }
    if (isAdmin()) {
      return member.role === 0;  // 管理员只能踢成员(role=0)
    }
    return false;
  }

  async function handleKick(targetUserId: number, targetName: string) {
    if (!window.confirm(`确定要将"${targetName}"移出群聊吗？`)) return;
    try {
      await kickMember(groupId, targetUserId);
      void loadData();
      alert("已移出群聊");
    } catch (err) {
      console.error("踢出成员失败", err);
      alert("操作失败，请重试");
    }
  }

  async function handleSetAdmin(targetUserId: number, isAdmin: boolean) {
    try {
      await setAdmins(groupId, [targetUserId], isAdmin);
      void loadData();
      alert(isAdmin ? "已设为管理员" : "已取消管理员");
    } catch (err) {
      console.error("设置管理员失败", err);
      alert("操作失败，请重试");
    }
  }

  function getRoleText(role: number) {
    switch (role) {
      case 2:
        return "群主";
      case 1:
        return "管理员";
      default:
        return "成员";
    }
  }

  if (loading) {
    return (
      <div className="group-info-page">
        <div className="page-header">
          <button className="back-btn" onClick={onBack}>返回</button>
          <h1>群聊信息</h1>
        </div>
        <div className="loading">加载中...</div>
      </div>
    );
  }

  if (!groupInfo) {
    return (
      <div className="group-info-page">
        <div className="page-header">
          <button className="back-btn" onClick={onBack}>返回</button>
          <h1>群聊信息</h1>
        </div>
        <div className="empty-state">加载失败</div>
      </div>
    );
  }

  return (
    <div className="group-info-page">
      <div className="page-header">
        <button className="back-btn" onClick={onBack}>返回</button>
        <h1>群聊信息</h1>
      </div>

      <div className="group-avatar-section">
        <div className="group-avatar large">
          {groupInfo.avatar ? (
            <img src={groupInfo.avatar} alt={groupInfo.name} />
          ) : (
            <span>{groupInfo.name[0]?.toUpperCase()}</span>
          )}
        </div>
        <div className="group-name">{groupInfo.name}</div>
        <div className="group-id">群号: {groupInfo.id}</div>
      </div>

      <div className="info-section">
        <div className="info-item">
          <span className="info-label">群公告</span>
          <span className="info-value">
            {groupInfo.announcement || "暂无公告"}
          </span>
        </div>
        <div className="info-item">
          <span className="info-label">群成员</span>
          <span className="info-value" onClick={() => setShowMembers(true)}>
            {groupInfo.memberCount} 人 →
          </span>
        </div>
        {groupInfo.isMuted === 1 && (
          <div className="info-item warning">
            <span className="info-label">⚠️ 全体禁言中</span>
          </div>
        )}
      </div>

      <div className="info-section">
        <div className="info-item" onClick={() => setShowMembers(true)}>
          <span className="info-label">查看全部群成员</span>
          <span className="info-value">→</span>
        </div>
      </div>

      {(isOwner() || isAdmin()) && (
        <div className="info-section">
          <div className="info-item">
            <span className="info-label">👑 {isOwner() ? "群主" : "管理员"}专属功能</span>
          </div>
          <div className="info-item" onClick={() => { setShowRequests(true); void loadRequests(); }}>
            <div className="info-label-row">
              <span className="info-label">入群申请</span>
              {pendingCount > 0 && <span className="pending-badge">{pendingCount}</span>}
            </div>
            <span className="info-value">→</span>
          </div>
          <div className="info-item">
            <span className="info-label">全体禁言</span>
            <div className="toggle-switch">
              <input
                type="checkbox"
                id="mute-toggle"
                checked={groupInfo.isMuted === 1}
                onChange={() => {
                  // TODO: 调用禁言 API
                }}
              />
              <label htmlFor="mute-toggle" />
            </div>
          </div>
        </div>
      )}

      <div className="info-section">
        <div className="info-item danger">
          <span className="info-value" onClick={() => {
            if (window.confirm(isOwner() ? "确定要解散群聊吗？" : "确定要退出群聊吗？")) {
              // TODO: 调用退出/解散群聊 API
              onBack();
            }
          }}>
            {isOwner() ? "解散群聊" : "退出群聊"}
          </span>
        </div>
      </div>

      {showMembers && (
        <div className="modal-overlay" onClick={() => setShowMembers(false)}>
          <div className="modal-content members-modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>群成员 ({members.length})</h2>
              <div className="modal-header-actions">
                <button className="btn-icon" onClick={() => { setShowMembers(false); setShowInvite(true); }} title="邀请好友">
                  +
                </button>
                <button className="close-btn" onClick={() => setShowMembers(false)}>×</button>
              </div>
            </div>
            <div className="members-list">
              {members.map(member => (
                <div key={member.id} className="member-item">
                  <div className="member-avatar">
                    {member.avatar ? (
                      <img src={member.avatar} alt={member.nickname || ""} />
                    ) : (
                      <span>{member.nickname?.[0]?.toUpperCase()}</span>
                    )}
                  </div>
                  <div className="member-info">
                    <div className="member-name">
                      {member.nickname || member.username}
                      {member.userId === user?.id && " (我)"}
                    </div>
                    <div className="member-role">{getRoleText(member.role)}</div>
                  </div>
                  {member.userId !== user?.id && (isOwner() || isAdmin()) && (
                    <div className="member-actions">
                      {isOwner() && member.role !== 2 && (
                        <button
                          className="btn-small"
                          onClick={() => handleSetAdmin(member.userId, member.role === 1 ? false : true)}
                        >
                          {member.role === 1 ? "取消管理" : "设为管理"}
                        </button>
                      )}
                      {canKick(member) && (
                        <button
                          className="btn-small btn-danger"
                          onClick={() => handleKick(member.userId, member.nickname || member.username)}
                        >
                          踢出
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {showInvite && (
        <div className="modal-overlay" onClick={() => setShowInvite(false)}>
          <div className="modal-content members-modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>邀请好友入群</h2>
              <button className="close-btn" onClick={() => setShowInvite(false)}>×</button>
            </div>
            <div className="invite-list">
              {(() => {
                const memberIds = new Set(members.map(m => m.userId));
                const availableFriends = friends.filter(f => !memberIds.has(f.userId));
                if (availableFriends.length === 0) {
                  return <div className="empty-state">暂无可邀请的好友</div>;
                }
                return availableFriends.map(friend => (
                  <div key={friend.userId} className="member-item">
                    <div className="member-avatar">
                      {friend.avatar ? (
                        <img src={friend.avatar} alt={friend.nickname || ""} />
                      ) : (
                        <span>{friend.nickname?.[0]?.toUpperCase() || friend.username[0]?.toUpperCase()}</span>
                      )}
                    </div>
                    <div className="member-info">
                      <div className="member-name">{friend.nickname || friend.username}</div>
                    </div>
                    <button
                      className="btn-invite"
                      disabled={inviting === friend.userId}
                      onClick={() => handleInvite(friend.userId)}
                    >
                      {inviting === friend.userId ? "邀请中..." : "邀请"}
                    </button>
                  </div>
                ));
              })()}
            </div>
          </div>
        </div>
      )}

      {showRequests && (
        <div className="modal-overlay" onClick={() => setShowRequests(false)}>
          <div className="modal-content members-modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>入群申请</h2>
              <button className="close-btn" onClick={() => setShowRequests(false)}>×</button>
            </div>
            <div className="invite-list">
              {requests.length === 0 ? (
                <div className="empty-state">暂无待处理的入群申请</div>
              ) : (
                requests.map(request => (
                  <div key={request.id} className="member-item">
                    <div className="member-avatar">
                      {request.applicantAvatar ? (
                        <img src={request.applicantAvatar} alt={request.applicantNickname || ""} />
                      ) : (
                        <span>{request.applicantNickname?.[0]?.toUpperCase()}</span>
                      )}
                    </div>
                    <div className="member-info">
                      <div className="member-name">{request.applicantNickname}</div>
                      <div className="member-role">
                        {request.type === 0 ? `邀请人：${request.inviterNickname}` : "主动申请"}
                      </div>
                    </div>
                    <div className="request-actions">
                      <button
                        className="btn-accept"
                        onClick={() => handleRequest(request.id, true)}
                      >
                        同意
                      </button>
                      <button
                        className="btn-reject"
                        onClick={() => handleRequest(request.id, false)}
                      >
                        拒绝
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}