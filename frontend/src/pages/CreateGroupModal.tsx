import { useEffect, useState } from "react";
import { FriendVO } from "../types";
import { getFriendList } from "../api/friend";
import { createGroup } from "../api/chatgroup";
import { useAuth } from "../auth/AuthContext";

interface CreateGroupModalProps {
  onClose: () => void;
  onSuccess: (groupId: number, groupName: string) => void;
}

export default function CreateGroupModal({ onClose, onSuccess }: CreateGroupModalProps) {
  const { user } = useAuth();
  const [groupName, setGroupName] = useState("");
  const [selectedFriends, setSelectedFriends] = useState<number[]>([]);
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    void loadFriends();
  }, []);

  async function loadFriends() {
    try {
      const res = await getFriendList();
      setFriends(res.data || []);
    } catch (err) {
      console.error("加载好友列表失败", err);
    }
  }

  function toggleFriend(friendId: number) {
    setSelectedFriends(prev =>
      prev.includes(friendId)
        ? prev.filter(id => id !== friendId)
        : [...prev, friendId]
    );
  }

  async function handleCreate() {
    if (!groupName.trim()) {
      setError("请输入群名称");
      return;
    }
    if (selectedFriends.length < 1) {
      setError("请至少选择一位好友");
      return;
    }

    setLoading(true);
    setError("");

    try {
      // 后端要求创建者必须在成员列表中
      const memberIds = user?.id ? [user.id, ...selectedFriends] : selectedFriends;
      const groupId = await createGroup(groupName.trim(), memberIds);
      onSuccess(groupId, groupName.trim());
    } catch (err: unknown) {
      console.error("创建群聊失败", err);
      setError("创建失败，请重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content create-group-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>创建群聊</h2>
          <button className="close-btn" onClick={onClose}>×</button>
        </div>

        <div className="modal-body">
          <div className="form-group">
            <label>群名称</label>
            <input
              type="text"
              value={groupName}
              onChange={e => setGroupName(e.target.value)}
              placeholder="输入群名称"
              maxLength={20}
            />
          </div>

          <div className="form-group">
            <label>选择好友 ({selectedFriends.length})</label>
            <div className="friend-select-list">
              {friends.map(friend => (
                <div
                  key={friend.userId}
                  className={`friend-select-item ${selectedFriends.includes(friend.userId) ? "selected" : ""}`}
                  onClick={() => toggleFriend(friend.userId)}
                >
                  <div className="friend-avatar small">
                    {friend.avatar ? (
                      <img src={friend.avatar} alt={friend.nickname || friend.username} />
                    ) : (
                      <span>{(friend.nickname || friend.username)[0]?.toUpperCase()}</span>
                    )}
                  </div>
                  <span className="friend-name">{friend.nickname || friend.username}</span>
                  {selectedFriends.includes(friend.userId) && (
                    <span className="check-mark">✓</span>
                  )}
                </div>
              ))}
            </div>
          </div>

          {error && <div className="error-text">{error}</div>}
        </div>

        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose}>取消</button>
          <button
            className="btn-primary"
            onClick={() => void handleCreate()}
            disabled={loading}
          >
            {loading ? "创建中..." : "创建"}
          </button>
        </div>
      </div>
    </div>
  );
}