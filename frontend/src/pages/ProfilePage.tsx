import { useState, useRef, useEffect } from "react";
import { useAuth } from "../auth/AuthContext";
import { getStoredToken } from "../api/http";
import { updateNickname } from "../api/auth";

export function ProfilePage() {
  const { user, signOut, refreshUser } = useAuth();
  const [nickname, setNickname] = useState("");
  const [saving, setSaving] = useState(false);
  const [nicknameMessage, setNicknameMessage] = useState("");
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [saveMessage, setSaveMessage] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (user) {
      setNickname(user.nickname || "");
    }
  }, [user]);

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    console.log('=== handleAvatarUpload called');
    const file = e.target.files?.[0];
    console.log('=== selected file:', file);
    if (!file) return;

    setUploading(true);
    setUploadError("");

    try {
      const formData = new FormData();
      formData.append("file", file);

      const token = getStoredToken();
      console.log('=== token:', token ? 'exists' : 'not found');
      console.log('=== sending request to /api/user/avatar/upload');

      const response = await fetch("/api/user/avatar/upload", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      });

      console.log('=== response status:', response.status);

      if (!response.ok) {
        throw new Error("上传失败");
      }

      await refreshUser();
      setSaveMessage("头像更新成功！");
      setTimeout(() => setSaveMessage(""), 3000);
    } catch (err) {
      setUploadError("头像上传失败，请重试");
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleSaveNickname = async () => {
    if (!nickname || nickname.length < 2 || nickname.length > 20) {
      setNicknameMessage("昵称长度需在2-20个字符之间");
      return;
    }
    setSaving(true);
    setNicknameMessage("");
    try {
      await updateNickname(nickname);
      await refreshUser();
      setNicknameMessage("昵称修改成功！");
      setTimeout(() => setNicknameMessage(""), 3000);
    } catch (err) {
      setNicknameMessage("修改失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  if (!user) {
    return (
      <div className="shell">
        <div className="card">
          <p className="subtitle">加载中…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="header-left">
          <button className="back-btn" onClick={() => window.history.back()}>
            <svg viewBox="0 0 24 24">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
            <span>返回</span>
          </button>
          <div className="logo">个人资料</div>
        </div>
        <div className="header-right">
          <button className="logout-btn" onClick={() => void signOut()}>
            退出登录
          </button>
        </div>
      </header>

      <main className="profile-content">
        <div className="avatar-section">
          <div className="avatar-preview">
            <div className="avatar-wrapper" onClick={() => {
              console.log('=== avatar clicked, fileInputRef:', fileInputRef.current);
              if (fileInputRef.current) {
                fileInputRef.current.click();
                console.log('=== file input clicked');
              } else {
                console.log('=== fileInputRef is null!');
              }
            }}>
              <div className="user-avatar large">
                {user.avatar ? (
                  <img src={user.avatar} alt="avatar" />
                ) : (
                  <span>{user.nickname?.charAt(0) || "?"}</span>
                )}
              </div>
              <div className="avatar-edit">
                <svg viewBox="0 0 24 24">
                  <path d="M12 20h9l-4-4H3L8 20z" />
                </svg>
              </div>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg"
              className="avatar-input"
              onChange={handleAvatarUpload}
              disabled={uploading}
            />
            <p className="avatar-hint">点击头像上传新头像</p>
            {uploadError && <p className="error-text">{uploadError}</p>}
          </div>
        </div>

        <div className="profile-form">
          <form>
            <h2>基本信息</h2>
            
            <div className="form-group">
              <label>用户ID</label>
              <input
                type="text"
                value={user.id}
                disabled
                className="disabled-input"
              />
            </div>

            <div className="form-group">
              <label>用户名</label>
              <input
                type="text"
                value={user.username}
                disabled
                className="disabled-input"
              />
            </div>

            <div className="form-group">
              <label>昵称 *</label>
              <div className="nickname-edit">
                <input
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="请输入昵称（2-20字符）"
                />
                <button 
                  type="button" 
                  className="btn-save-nickname"
                  onClick={() => void handleSaveNickname()}
                  disabled={saving}
                >
                  {saving ? "保存中…" : "保存"}
                </button>
              </div>
              {nicknameMessage && (
                <span className={nicknameMessage.includes("成功") ? "success-text" : "error-text"}>
                  {nicknameMessage}
                </span>
              )}
            </div>

            <div className="form-group">
              <label>创建时间</label>
              <input
                type="text"
                value={user.createdAt ? new Date(user.createdAt).toLocaleString("zh-CN") : "—"}
                disabled
                className="disabled-input"
              />
            </div>

            <div className="form-actions">
              {saveMessage && (
                <span className={saveMessage.includes("成功") ? "success-text" : "error-text"}>
                  {saveMessage}
                </span>
              )}
            </div>
          </form>
        </div>

        <div className="password-section">
          <h2>修改密码</h2>
          <div className="form-group">
            <label>当前密码</label>
            <input type="password" placeholder="请输入当前密码" />
          </div>
          <div className="form-group">
            <label>新密码</label>
            <input type="password" placeholder="请输入新密码" />
          </div>
          <div className="form-group">
            <label>确认新密码</label>
            <input type="password" placeholder="请再次输入新密码" />
          </div>
          <button className="primary password-btn">修改密码</button>
        </div>
      </main>
    </div>
  );
}