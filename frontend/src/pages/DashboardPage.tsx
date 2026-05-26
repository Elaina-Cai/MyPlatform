import { useState, useEffect, useCallback, useRef } from "react";
import { useAuth } from "../auth/AuthContext";
import { useNavigate } from "react-router-dom";
import { getRequestList, getFriendList } from "../api/friend";
import { getMyGroups, getMyInvitations, type GroupSessionVO } from "../api/chatgroup";
import type { FriendVO } from "../types";
import ChatPage, { ChatPageRef } from "./ChatPage";
import GroupChatPage, { GroupChatPageRef } from "./GroupChatPage";
import GroupInfoPage from "./GroupInfoPage";
import CreateGroupModal from "./CreateGroupModal";
import GamesPage from "./GamesPage";
import { FriendPage } from "./FriendPage";

type NavItem = {
  id: string;
  label: string;
  icon: string;
  path?: string;
};

const navItems: NavItem[] = [
  { id: "home", label: "首页", icon: "⌂" },
  { id: "social", label: "社交", icon: "👥" },
  { id: "games", label: "游戏库", icon: "🎮" },
  { id: "settings", label: "设置", icon: "⚙" },
];

export function DashboardPage() {
  const { user, signOut } = useAuth();
  const [activeNav, setActiveNav] = useState("home");
  const [showUserMenu, setShowUserMenu] = useState(false);
  const navigate = useNavigate();
  const [applyCount, setApplyCount] = useState(0);
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [friendSearch, setFriendSearch] = useState("");
  const [onlineFriends, setOnlineFriends] = useState<Set<number>>(new Set());
  const [selectedChat, setSelectedChat] = useState<FriendVO | null>(null);
  const [chatPanelOpen, setChatPanelOpen] = useState(false);
  const [groups, setGroups] = useState<GroupSessionVO[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<GroupSessionVO | null>(null);
  const [showGroupInfo, setShowGroupInfo] = useState(false);
  const [showCreateGroup, setShowCreateGroup] = useState(false);
  const [groupInviteCount, setGroupInviteCount] = useState(0);

  const currentChatIdRef = useRef<number | null>(null);
  const currentGroupIdRef = useRef<number | null>(null);
  const isViewingChatRef = useRef<number | null>(null);
  const chatRef = useRef<ChatPageRef>(null);
  const groupChatRef = useRef<GroupChatPageRef>(null);
  const isViewingGroupRef = useRef<number | null>(null);

  useEffect(() => {
    function handleSwitchNav(e: Event) {
      const detail = (e as CustomEvent).detail;
      setActiveNav(detail.nav);
    }
    window.addEventListener("switch-nav", handleSwitchNav);
    return () => window.removeEventListener("switch-nav", handleSwitchNav);
  }, []);

  useEffect(() => {
    currentChatIdRef.current = selectedChat?.userId ?? null;
    isViewingChatRef.current = selectedChat?.userId ?? null;
  }, [selectedChat]);

  useEffect(() => {
    currentGroupIdRef.current = selectedGroup?.id ?? null;
    isViewingGroupRef.current = selectedGroup?.id ?? null;
  }, [selectedGroup]);

  function resetFriendUnread(userId: number) {
    setFriends(prev => prev.map(f =>
      f.userId === userId ? { ...f, unreadCount: 0 } : f
    ));
  }

  function resetGroupUnread(groupId: number) {
    setGroups(prev => prev.map(g =>
      g.id === groupId ? { ...g, unreadCount: 0 } : g
    ));
  }

  const loadApplyCount = useCallback(async () => {
    try {
      const res = await getRequestList();
      setApplyCount(res.data?.length || 0);
    } catch (error) {
      console.error("加载申请数量失败", error);
    }
  }, []);

  const loadGroupInviteCount = useCallback(async () => {
    try {
      const invitations = await getMyInvitations();
      setGroupInviteCount(invitations.length);
    } catch (error) {
      console.error("加载群邀请数量失败", error);
    }
  }, []);

  const loadFriends = useCallback(async (viewedId?: number | null) => {
    try {
      const res = await getFriendList();
      const friendList = res.data || [];
      const effectiveViewedId = viewedId !== undefined ? viewedId : isViewingChatRef.current;
      const updatedList = friendList.map((f: FriendVO) => {
        if (effectiveViewedId !== null && f.userId === effectiveViewedId) {
          return { ...f, unreadCount: 0 };
        }
        return f;
      });
      setFriends(updatedList);
      const onlineSet = new Set<number>();
      friendList.forEach((f: FriendVO) => {
        if (f.isOnline) {
          onlineSet.add(f.userId);
        }
      });
      setOnlineFriends(onlineSet);
    } catch (err) {
      console.error("加载好友列表失败", err);
    }
  }, []);

  const loadGroups = useCallback(async (viewedId?: number | null) => {
    try {
      const groupList = await getMyGroups();
      const effectiveViewedId = viewedId !== undefined ? viewedId : isViewingGroupRef.current;
      const updatedList = groupList.map((g) => {
        if (effectiveViewedId !== null && g.id === effectiveViewedId) {
          return { ...g, unreadCount: 0 };
        }
        return g;
      });
      setGroups(updatedList);
    } catch (err) {
      console.error("加载群聊列表失败", err);
    }
  }, []);

  useEffect(() => {
    void loadApplyCount();
    void loadGroupInviteCount();
    void loadFriends();
    void loadGroups();

    const timer = setInterval(() => {
      void loadApplyCount();
      void loadGroupInviteCount();
    }, 30000);

    return () => {
      clearInterval(timer);
    };
  }, [loadApplyCount, loadGroupInviteCount, loadFriends, loadGroups]);

  useEffect(() => {
    document.title = applyCount > 0 ? `(${applyCount}) MyPlatform` : "MyPlatform";
  }, [applyCount]);

  useEffect(() => {
    function handleFriendOnline(e: Event) {
      const detail = (e as CustomEvent).detail;
      setOnlineFriends(prev => new Set([...prev, detail.friendId]));
    }

    function handleFriendOffline(e: Event) {
      const detail = (e as CustomEvent).detail;
      setOnlineFriends(prev => {
        const next = new Set(prev);
        next.delete(detail.friendId);
        return next;
      });
    }

    function handleChatMessage(e: Event) {
      const detail = e as CustomEvent<{ senderId: number; content?: string; groupId?: number; typeId?: number }>;
      const message = detail.detail;
      if (message.senderId && message.senderId !== user?.id && currentChatIdRef.current !== message.senderId) {
        setFriends(prev => prev.map(f =>
          f.userId === message.senderId
            ? { ...f, unreadCount: (f.unreadCount || 0) + 1 }
            : f
        ));
      }
    }

    function handleGroupMessage(e: Event) {
      const detail = (e as CustomEvent).detail;
      if (detail.groupId && detail.senderId !== user?.id && currentGroupIdRef.current !== detail.groupId) {
        setGroups(prev => prev.map(g =>
          g.id === detail.groupId
            ? { ...g, unreadCount: (g.unreadCount || 0) + 1 }
            : g
        ));
      }
    }

    function handleFriendApply() {
      void loadApplyCount();
    }

    function handleFriendAccepted() {
      void loadApplyCount();
      void loadFriends();
    }

    function handleFriendRejected() {
      void loadApplyCount();
    }

    function handleGroupInvite() {
      void loadGroupInviteCount();
    }

    window.addEventListener("friend-online", handleFriendOnline);
    window.addEventListener("friend-offline", handleFriendOffline);
    window.addEventListener("chat-message", handleChatMessage);
    window.addEventListener("group-message", handleGroupMessage);
    window.addEventListener("friend-apply", handleFriendApply);
    window.addEventListener("friend-accepted", handleFriendAccepted);
    window.addEventListener("friend-rejected", handleFriendRejected);
    window.addEventListener("group-invite", handleGroupInvite);

    return () => {
      window.removeEventListener("friend-online", handleFriendOnline);
      window.removeEventListener("friend-offline", handleFriendOffline);
      window.removeEventListener("chat-message", handleChatMessage);
      window.removeEventListener("group-message", handleGroupMessage);
      window.removeEventListener("friend-apply", handleFriendApply);
      window.removeEventListener("friend-accepted", handleFriendAccepted);
      window.removeEventListener("friend-rejected", handleFriendRejected);
      window.removeEventListener("group-invite", handleGroupInvite);
    };
  }, [loadApplyCount, loadFriends]);

  const filteredFriends = friends.filter(f => {
    const keyword = friendSearch.toLowerCase();
    return (
      f.nickname?.toLowerCase().includes(keyword) ||
      f.username.toLowerCase().includes(keyword)
    );
  });

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="header-left">
          <div className="logo">MyPlatform</div>
        </div>
        <div className="header-right">
          <div className="user-info" onClick={() => setShowUserMenu(!showUserMenu)}>
            <div className="avatar-wrapper" onClick={(e) => {
                e.stopPropagation();
                navigate("/profile");
              }}>
              <div className="user-avatar">
                {user?.avatar ? (
                  <img src={user.avatar} alt="avatar" />
                ) : (
                  <span>{user?.nickname?.charAt(0) || "?"}</span>
                )}
              </div>
              <div className="avatar-edit">
                <svg viewBox="0 0 24 24">
                  <path d="M12 20h9l-4-4H3L8 20z" />
                </svg>
              </div>
            </div>

            <div className="user-details">
              <span className="user-nickname">{user?.nickname || "玩家"}</span>
              <span className="user-username">@{user?.username || "loading"}</span>
            </div>
            <span className="dropdown-arrow">{showUserMenu ? "▲" : "▼"}</span>
          </div>
          {showUserMenu && (
            <div className="user-dropdown">
              <div className="dropdown-item" onClick={() => {
                setShowUserMenu(false);
                navigate("/profile");
              }}>
                修改个人信息
              </div>
              <div className="dropdown-divider" />
              <div className="dropdown-item danger" onClick={() => void signOut()}>
                退出登录
              </div>
            </div>
          )}
        </div>
      </header>

      <div className="dashboard-body">
        <nav className="sidebar">
          <ul className="nav-menu">
            {navItems.map((item) => (
              <li
                key={item.id}
                className={`nav-item ${activeNav === item.id ? "active" : ""}`}
                onClick={() => setActiveNav(item.id)}
              >
                <span className="nav-icon">
                  {item.icon}
                  {item.id === "social" && applyCount > 0 && (
                    <span className="nav-badge">{applyCount > 99 ? "99+" : applyCount}</span>
                  )}
                </span>
                <span className="nav-label">{item.label}</span>
              </li>
            ))}
          </ul>
        </nav>

        <main className="main-content">
          {activeNav === "home" && (
            <div className="friend-list-page">
              <div className="friend-list-header">
                <h2>聊天</h2>
                <button className="btn-icon" onClick={() => setShowCreateGroup(true)}>+</button>
              </div>
              <div className="friend-search-wrapper">
                <input
                  type="text"
                  className="friend-search-input"
                  placeholder="搜索聊天..."
                  value={friendSearch}
                  onChange={e => setFriendSearch(e.target.value)}
                />
              </div>
              <div className="friend-online-list">
                {/* 好友列表 */}
                {filteredFriends.filter(f =>
                  (f.nickname?.toLowerCase().includes(friendSearch.toLowerCase()) ||
                   f.username.toLowerCase().includes(friendSearch.toLowerCase()))
                ).map(friend => (
                  <div
                    key={`friend-${friend.userId}`}
                    className={`friend-online-item ${onlineFriends.has(friend.userId) ? "online" : ""}`}
                    onClick={() => {
                      setSelectedChat(friend);
                      setSelectedGroup(null);
                      setChatPanelOpen(false);
                      setTimeout(() => setChatPanelOpen(true), 16);
                    }}
                  >
                    <div className="friend-online-avatar">
                      {friend.avatar ? (
                        <img src={friend.avatar} alt={friend.nickname || friend.username} />
                      ) : (
                        <span>{(friend.nickname || friend.username)[0]?.toUpperCase()}</span>
                      )}
                      <span className={`online-dot ${onlineFriends.has(friend.userId) ? "online" : ""}`} />
                    </div>
                    <div className="friend-online-info">
                      <div className="friend-online-name">
                        {friend.nickname || friend.username}
                        {typeof friend.unreadCount === "number" && friend.unreadCount > 0 && friend.userId !== isViewingChatRef.current && (
                          <span className="unread-badge">{friend.unreadCount > 99 ? "99+" : friend.unreadCount}</span>
                        )}
                      </div>
                      <div className="friend-online-status">
                        {onlineFriends.has(friend.userId) ? "在线" : "离线"}
                      </div>
                    </div>
                  </div>
                ))}
                {/* 群聊列表 */}
                {groups.filter(g => g.name.toLowerCase().includes(friendSearch.toLowerCase())).map(group => (
                  <div
                    key={`group-${group.id}`}
                    className="friend-online-item"
                    onClick={() => {
                      setSelectedGroup(group);
                      setSelectedChat(null);
                      setChatPanelOpen(false);
                    }}
                  >
                    <div className="friend-online-avatar group">
                      {group.avatar ? (
                        <img src={group.avatar} alt={group.name} />
                      ) : (
                        <span>{group.name[0]?.toUpperCase()}</span>
                      )}
                    </div>
                    <div className="friend-online-info">
                      <div className="friend-online-name">
                        {group.name}
                        {group.ownerId === user?.id && <span className="badge">群主</span>}
                        {group.unreadCount > 0 && group.id !== isViewingGroupRef.current && (
                          <span className="unread-badge">{group.unreadCount > 99 ? "99+" : group.unreadCount}</span>
                        )}
                      </div>
                      <div className="friend-online-status">
                        {group.memberCount} 人
                      </div>
                    </div>
                  </div>
                ))}
                {filteredFriends.length === 0 && groups.length === 0 && (
                  <div className="friend-empty">暂无聊天</div>
                )}
              </div>
              {applyCount > 0 && (
                <div className="apply-notice" onClick={() => navigate("/friends")}>
                  {applyCount} 个好友申请待处理
                </div>
              )}
              {groupInviteCount > 0 && (
                <div className="apply-notice group-invite-notice" onClick={() => navigate("/friends?tab=invitations")}>
                  {groupInviteCount} 个群邀请待处理
                </div>
              )}
            </div>
          )}
          {activeNav === "games" && <GamesPage />}
          {activeNav === "social" && (
            <FriendPage
              applyCount={applyCount}
              onApplyRead={() => setApplyCount(0)}
              onInviteRead={() => setGroupInviteCount(0)}
            />
          )}
        </main>
      </div>

      {selectedChat && (
        <div className={`side-panel ${chatPanelOpen ? "open" : ""}`} style={{ zIndex: 110 }}>
          <ChatPage
            key={selectedChat?.userId}
            ref={chatRef}
            friendId={selectedChat.userId}
            friendNickname={selectedChat.nickname || selectedChat.username}
            friendAvatar={selectedChat.avatar || ""}
            onBack={() => {
              chatRef.current?.cleanup();
              setChatPanelOpen(false);
            }}
            onRead={() => resetFriendUnread(selectedChat.userId)}
            isSidePanel={true}
          />
        </div>
      )}

      {/* 群聊侧边面板 */}
      <div className={`side-panel ${selectedGroup && !selectedChat ? "open" : ""}`}>
        {selectedGroup && (
          <>
            <GroupChatPage
              key={selectedGroup.id}
              ref={groupChatRef}
              groupId={selectedGroup.id}
              groupName={selectedGroup.name}
              groupAvatar={selectedGroup.avatar}
              onBack={async () => {
                await groupChatRef.current?.cleanup();
                const viewedId = isViewingGroupRef.current;
                isViewingGroupRef.current = null;
                setSelectedGroup(null);
                await loadGroups(viewedId);
              }}
              onShowGroupInfo={() => setShowGroupInfo(true)}
              onRead={() => resetGroupUnread(selectedGroup.id)}
            />
            {showGroupInfo && (
              <GroupInfoPage
                groupId={selectedGroup.id}
                onBack={() => setShowGroupInfo(false)}
              />
            )}
          </>
        )}
      </div>

      {showCreateGroup && (
        <CreateGroupModal
          onClose={() => setShowCreateGroup(false)}
          onSuccess={(groupId, groupName) => {
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
          }}
        />
      )}
    </div>
  );
}