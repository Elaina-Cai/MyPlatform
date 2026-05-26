import { useEffect, useState } from "react";
import { FriendVO } from "../types";
import { getFriendList } from "../api/friend";
import { getMyGroups, type GroupSessionVO } from "../api/chatgroup";
import { useAuth } from "../auth/AuthContext";
import ChatPage from "./ChatPage";
import GroupChatPage from "./GroupChatPage";
import GroupInfoPage from "./GroupInfoPage";
import CreateGroupModal from "./CreateGroupModal";

export default function ChatListPage() {
  const { user } = useAuth();
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [groups, setGroups] = useState<GroupSessionVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedChat, setSelectedChat] = useState<FriendVO | null>(null);
  const [selectedGroup, setSelectedGroup] = useState<GroupSessionVO | null>(null);
  const [showGroupInfo, setShowGroupInfo] = useState(false);
  const [showCreateGroup, setShowCreateGroup] = useState(false);
  const [activeTab, setActiveTab] = useState<"all" | "group">("all");

  useEffect(() => {
    void loadData();
  }, []);

  async function loadData() {
    try {
      const [friendRes, groupRes] = await Promise.all([
        getFriendList(),
        getMyGroups(),
      ]);
      setFriends(friendRes.data || []);
      setGroups(groupRes);
    } catch (err) {
      console.error("加载数据失败", err);
    } finally {
      setLoading(false);
    }
  }

  function handleSelectGroup(group: GroupSessionVO) {
    setSelectedGroup(group);
  }

  function handleGroupCreated(groupId: number, groupName: string) {
    setShowCreateGroup(false);
    setSelectedGroup({
      id: groupId,
      name: groupName,
      avatar: null,
      memberCount: 1,
      unreadCount: 0,
      role: 0,
      ownerId: user?.id || 0,
      lastMessage: null,
      lastMessageTime: new Date().toISOString(),
    });
  }

  function handleBackFromGroupChat() {
    setSelectedGroup(null);
    void loadData();
  }

  function handleBackFromGroupInfo() {
    setShowGroupInfo(false);
  }

  if (selectedGroup && showGroupInfo) {
    return (
      <GroupInfoPage
        groupId={selectedGroup.id}
        onBack={handleBackFromGroupInfo}
      />
    );
  }

  if (selectedGroup) {
    return (
      <GroupChatPage
        groupId={selectedGroup.id}
        groupName={selectedGroup.name}
        groupAvatar={selectedGroup.avatar}
        onBack={handleBackFromGroupChat}
        onShowGroupInfo={() => setShowGroupInfo(true)}
      />
    );
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

  return (
    <div className="chat-list-page">
      <div className="page-header">
        <h1>聊天</h1>
        <button className="btn-icon" onClick={() => setShowCreateGroup(true)}>
          +
        </button>
      </div>

      <div className="chat-tabs">
        <button
          className={`tab ${activeTab === "all" ? "active" : ""}`}
          onClick={() => setActiveTab("all")}
        >
          全部
        </button>
        <button
          className={`tab ${activeTab === "group" ? "active" : ""}`}
          onClick={() => setActiveTab("group")}
        >
          群聊
        </button>
      </div>

      {loading ? (
        <div className="loading">加载中...</div>
      ) : (
        <>
          {(activeTab === "all") && friends.length === 0 && groups.length === 0 && (
            <div className="empty-state">
              <p>暂无聊天</p>
              <button className="btn-link" onClick={() => setShowCreateGroup(true)}>
                创建群聊
              </button>
            </div>
          )}

          {activeTab === "group" && groups.length === 0 && (
            <div className="empty-state">
              <p>暂无群聊</p>
              <button className="btn-link" onClick={() => setShowCreateGroup(true)}>
                创建群聊
              </button>
            </div>
          )}

          <div className="chat-session-list">
            {activeTab === "all" && friends.map(friend => (
              <div
                key={`friend-${friend.userId}`}
                className="chat-session-item"
                onClick={() => setSelectedChat(friend)}
              >
                <div className="session-avatar">
                  {friend.avatar ? (
                    <img src={friend.avatar} alt={friend.nickname || friend.username} />
                  ) : (
                    <span>{(friend.nickname || friend.username)[0]?.toUpperCase()}</span>
                  )}
                </div>
                <div className="session-info">
                  <div className="session-name">{friend.nickname || friend.username}</div>
                  <div className="session-preview">点击开始聊天</div>
                </div>
              </div>
            ))}

            {activeTab === "all" && groups.map(group => (
              <div
                key={`group-${group.id}`}
                className="chat-session-item"
                onClick={() => handleSelectGroup(group)}
              >
                <div className="session-avatar group">
                  {group.avatar ? (
                    <img src={group.avatar} alt={group.name} />
                  ) : (
                    <span>{group.name[0]?.toUpperCase()}</span>
                  )}
                </div>
                <div className="session-info">
                  <div className="session-name">
                    {group.name}
                    {group.role === 0 && <span className="badge">群主</span>}
                    {group.role === 1 && <span className="badge admin">管理</span>}
                  </div>
                  <div className="session-preview">
                      {typeof group.lastMessage === "string"
                        ? group.lastMessage
                        : (group.lastMessage as { content?: string } | null)?.content || "暂无消息"}
                    </div>
                </div>
                {group.unreadCount > 0 && (
                  <div className="unread-badge">{group.unreadCount > 99 ? "99+" : group.unreadCount}</div>
                )}
              </div>
            ))}

            {activeTab === "group" && groups.map(group => (
              <div
                key={`group-${group.id}`}
                className="chat-session-item"
                onClick={() => handleSelectGroup(group)}
              >
                <div className="session-avatar group">
                  {group.avatar ? (
                    <img src={group.avatar} alt={group.name} />
                  ) : (
                    <span>{group.name[0]?.toUpperCase()}</span>
                  )}
                </div>
                <div className="session-info">
                  <div className="session-name">
                    {group.name}
                    {group.role === 0 && <span className="badge">群主</span>}
                    {group.role === 1 && <span className="badge admin">管理</span>}
                  </div>
                  <div className="session-preview">
                      {typeof group.lastMessage === "string"
                        ? group.lastMessage
                        : (group.lastMessage as { content?: string } | null)?.content || "暂无消息"}
                    </div>
                </div>
                {group.unreadCount > 0 && (
                  <div className="unread-badge">{group.unreadCount > 99 ? "99+" : group.unreadCount}</div>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {showCreateGroup && (
        <CreateGroupModal
          onClose={() => setShowCreateGroup(false)}
          onSuccess={handleGroupCreated}
        />
      )}
    </div>
  );
}