import { useEffect, useState, useRef, forwardRef, useImperativeHandle } from "react";
import { MessageVO } from "../types";
import { getChatHistory, uploadFile, UploadResult } from "../api/chat";
import { applyJoinGroup } from "../api/chatgroup";
import { useAuth } from "../auth/AuthContext";

interface PendingFile {
  id: string;
  file: File;
  preview: string;
  fileType: string;
  progress: number;
  uploading: boolean;
  error: string | null;
  result: UploadResult | null;
  retries: number;
}

interface ChatPageProps {
  friendId: number;
  friendNickname: string;
  friendAvatar: string;
  onBack: () => void;
  onRead?: () => void;
  isSidePanel?: boolean;
}

export interface ChatPageRef {
  cleanup: () => Promise<void>;
}

const ChatPage = forwardRef<ChatPageRef, ChatPageProps>(({
  friendId,
  friendNickname,
  friendAvatar,
  onBack,
  onRead,
  isSidePanel = false
}, ref) => {
  const { user, sendWsMessage, maxFileSize } = useAuth();
  const [messages, setMessages] = useState<MessageVO[]>([]);
  const recentMsgIds = useRef<Set<string>>(new Set());
  const isSending = useRef(false);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [pendingFiles, setPendingFiles] = useState<PendingFile[]>([]);
  const pendingFilesRef = useRef<PendingFile[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUserId = user?.id;
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewType, setPreviewType] = useState<"image" | "video" | null>(null);
  const [previewPos, setPreviewPos] = useState({ x: 0, y: 0 });
  const previewRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const isDragging = useRef(false);
  const dragOffset = useRef({ x: 0, y: 0 });

  useImperativeHandle(ref, () => ({
    cleanup: cleanupPendingFiles,
  }));

  async function cleanupPendingFiles() {
    for (const pf of pendingFilesRef.current) {
      if (pf.preview) {
        URL.revokeObjectURL(pf.preview);
      }
    }
    pendingFilesRef.current = [];
    setPendingFiles([]);
  }

  useEffect(() => {
    void loadHistory();
    setPendingFiles([]);
    pendingFilesRef.current = [];
  }, [friendId]);

  useEffect(() => {
    scrollToBottom();
  }, [messages, pendingFiles]);

  useEffect(() => {
    pendingFilesRef.current = pendingFiles;
  }, [pendingFiles]);

  useEffect(() => {
    function handleNewMessage(e: Event) {
      const msg = (e as CustomEvent<MessageVO>);
      if (msg.detail.senderId === friendId) {
        const msgKey = (msg.detail as any).clientId || msg.detail.createdAt + "-" + msg.detail.senderId + "-" + (msg.detail.content || msg.detail.fileUrl || "");
        if (!recentMsgIds.current.has(msgKey)) {
          recentMsgIds.current.add(msgKey);
          setMessages(prev => [...prev, msg.detail]);
        }
      }
    }
    window.addEventListener("chat-message", handleNewMessage);
    return () => window.removeEventListener("chat-message", handleNewMessage);
  }, [friendId]);

  async function loadHistory() {
    try {
      const history = await getChatHistory(friendId);
      setMessages(history);
      recentMsgIds.current.clear();
      onRead?.();
    } catch (err) {
      console.error("加载聊天记录失败", err);
    } finally {
      setLoading(false);
    }
  }

  function scrollToBottom() {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }

  function selectFiles() {
    fileInputRef.current?.click();
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    for (const file of files) {
      if (file.size > maxFileSize) {
        alert(`文件 "${file.name}" 超出大小限制`);
        continue;
      }

      const preview = URL.createObjectURL(file);
      const isImage = file.type.startsWith("image/");
      const pendingFile: PendingFile = {
        id: Math.random().toString(36).slice(2),
        file,
        preview,
        fileType: isImage ? "image" : "video",
        progress: 0,
        uploading: false,
        error: null,
        result: null,
        retries: 0
      };

      setPendingFiles(prev => [...prev, pendingFile]);
    }

    e.target.value = "";
  }

  async function uploadAndSend(pf: PendingFile): Promise<UploadResult | null> {
    try {
      const result = await uploadFile(pf.file, (percent) => {
        setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, progress: percent } : p));
      });
      return result;
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "上传失败";
      if (pf.retries < 3) {
        setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, retries: p.retries + 1 } : p));
        await new Promise(resolve => setTimeout(resolve, 1000 * (pf.retries + 1)));
        return uploadAndSend({ ...pf, retries: pf.retries + 1 });
      } else {
        setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, uploading: false, error: errorMsg } : p));
        return null;
      }
    }
  }

  async function handleCancelFile(id: string) {
    const file = pendingFiles.find(p => p.id === id);
    if (file?.preview) {
      URL.revokeObjectURL(file.preview);
    }
    setPendingFiles(prev => prev.filter(p => p.id !== id));
  }

  async function handleSend() {
    if (isSending.current) return;
    isSending.current = true;

    const content = input.trim();
    const readyFiles = pendingFiles.filter(p => !p.uploading && !p.error);
    if (!content && readyFiles.length === 0) {
      isSending.current = false;
      return;
    }
    if (!currentUserId) {
      isSending.current = false;
      return;
    }

    for (const pf of readyFiles) {
      setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, uploading: true } : p));
    }

    const clientId = Date.now().toString(36) + Math.random().toString(36).slice(2);

    for (const pf of readyFiles) {
      const result = await uploadAndSend(pf);

      if (result) {
        if (pf.preview) {
          URL.revokeObjectURL(pf.preview);
        }

        const message: MessageVO = {
          senderId: currentUserId,
          friendId: friendId,
          content: "",
          fileUrl: result.fileUrl,
          fileType: result.fileType,
          createdAt: new Date().toISOString(),
          isRead: 1,
          senderNickname: user?.nickname || "",
          senderAvatar: user?.avatar || "",
          clientId
        };

        recentMsgIds.current.add(clientId);
        setMessages(prev => [...prev, message]);
        sendWsMessage(friendId, content, result.fileUrl, result.fileType);
      }
    }

    if (content) {
      const message: MessageVO = {
        senderId: currentUserId,
        friendId: friendId,
        content,
        createdAt: new Date().toISOString(),
        isRead: 1,
        senderNickname: user?.nickname || "",
        senderAvatar: user?.avatar || "",
        clientId
      };

      recentMsgIds.current.add(clientId);
      setMessages(prev => [...prev, message]);
      sendWsMessage(friendId, content);
    }

    setPendingFiles(prev => prev.filter(p => !readyFiles.some(r => r.id === p.id)));
    setInput("");
    isSending.current = false;
  }

  async function handleAcceptInvite(groupId: number) {
    try {
      await applyJoinGroup(groupId);
      alert("已提交入群申请，请等待群主审批");
    } catch (err) {
      console.error("接受邀请失败", err);
      alert("操作失败，请重试");
    }
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    }
  }

  function formatTime(time: string) {
    const d = new Date(time);
    return `${d.getHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}`;
  }

  function formatDate(time: string) {
    const d = new Date(time);
    const today = new Date();
    if (d.toDateString() === today.toDateString()) {
      return "今天";
    }
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) {
      return "昨天";
    }
    return `${d.getMonth() + 1}/${d.getDate()}`;
  }

  function shouldShowDateDivider(index: number): boolean {
    if (index === 0) return true;
    const current = new Date(messages[index].createdAt).toDateString();
    const prev = new Date(messages[index - 1].createdAt).toDateString();
    return current !== prev;
  }

  const canSend = input.trim() || pendingFiles.filter(p => !p.uploading && !p.error).length > 0;
  const hasUploading = pendingFiles.some(p => p.uploading);

  function renderFileMessage(msg: MessageVO) {
    if (msg.fileType === "image" && msg.fileUrl) {
      return (
        <div className="message-image">
          <img src={msg.fileUrl} alt="图片" onClick={() => window.open(msg.fileUrl)} />
        </div>
      );
    }
    if (msg.fileType === "video" && msg.fileUrl) {
      return (
        <div className="message-video">
          <video src={msg.fileUrl} controls preload="metadata" />
        </div>
      );
    }
    return null;
  }

  if (isSidePanel) {
    return (
      <div className="chat-page">
        <div className="chat-header">
          <div className="chat-title">
            <div className="chat-title-name">{friendNickname}</div>
          </div>
          <div className="chat-header-actions">
            <button className="icon-btn close-btn" onClick={onBack} title="关闭">×</button>
          </div>
        </div>

        <div className="chat-messages">
          {loading ? (
            <div className="loading">加载中...</div>
          ) : messages.length === 0 && pendingFiles.length === 0 ? (
            <div className="empty-state">暂无消息</div>
          ) : (
            <>
              {messages.map((msg, index) => (
                <div key={index}>
                  {shouldShowDateDivider(index) && (
                    <div className="date-divider">{formatDate(msg.createdAt)}</div>
                  )}
                  {msg.type === 1 ? (
                    <div className="message group-invite-message">
                      <div className="group-invite-card">
                        <div className="group-invite-icon">群</div>
                        <div className="group-invite-info">
                          <div className="group-invite-title">{msg.groupName || "群聊邀请"}</div>
                          <div className="group-invite-content">{msg.content}</div>
                        </div>
                      </div>
                      {msg.senderId !== currentUserId && (
                        <div className="group-invite-actions">
                          <button className="btn-accept" onClick={() => void handleAcceptInvite(msg.groupId!)}>
                            发送入群申请
                          </button>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className={`message ${msg.senderId === currentUserId ? "own" : ""}`}>
                      <div className="message-left">
                        <div className="message-sender">
                          {msg.senderId === currentUserId ? user?.nickname : msg.senderNickname}
                        </div>
                        <div className="message-avatar">
                          {msg.senderId === currentUserId
                            ? (user?.avatar ? <img src={user.avatar} alt="" /> : <span>{user?.nickname?.[0]?.toUpperCase()}</span>)
                            : (msg.senderAvatar ? <img src={msg.senderAvatar} alt="" /> : <span>{msg.senderNickname?.[0]?.toUpperCase()}</span>)
                          }
                        </div>
                      </div>
                      <div className="message-bubble-wrap">
                        {renderFileMessage(msg)}
                        {msg.content && <div className="message-bubble">{msg.content}</div>}
                        <div className="message-time">{formatTime(msg.createdAt)}</div>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </>
          )}
          <div ref={messagesEndRef} />
        </div>

        {pendingFiles.length > 0 && (
          <div className="upload-preview-area">
            {pendingFiles.map(pf => (
              <div key={pf.id} className={`upload-preview-item ${pf.error ? "error" : ""}`}>
                <div onClick={() => { setPreviewUrl(pf.preview); setPreviewType(pf.fileType as "image" | "video"); }}>
                  {pf.fileType === "video" ? (
                    <video src={pf.preview} className="preview-thumb" muted playsInline />
                  ) : (
                    <img src={pf.preview} alt="预览" className="preview-thumb" />
                  )}
                </div>
                {pf.uploading && (
                  <>
                    <div className="upload-progress-bar">
                      <div className="upload-progress-fill" style={{ width: `${pf.progress}%` }} />
                    </div>
                    <span className="upload-progress-text">{pf.progress}%</span>
                  </>
                )}
                {pf.error && <span className="upload-error-text">上传失败</span>}
                {!pf.uploading && !pf.error && <span className="upload-ready-text">待发送</span>}
                {!pf.uploading && (
                  <button className="upload-cancel-btn" onClick={() => void handleCancelFile(pf.id)}>×</button>
                )}
              </div>
            ))}
          </div>
        )}

        {previewUrl && (
          <div
            className="preview-modal"
            onMouseDown={(e) => {
              isDragging.current = true;
              dragOffset.current = { x: e.clientX, y: e.clientY };
            }}
            onMouseMove={(e) => {
              if (isDragging.current) {
                const dx = e.clientX - dragOffset.current.x;
                const dy = e.clientY - dragOffset.current.y;
                previewRef.current = {
                  x: previewRef.current.x + dx,
                  y: previewRef.current.y + dy
                };
                dragOffset.current = { x: e.clientX, y: e.clientY };
                setPreviewPos({ ...previewRef.current });
              }
            }}
            onMouseUp={() => { isDragging.current = false; }}
            onClick={() => setPreviewUrl(null)}
          >
            <div
              className="preview-modal-content"
              style={{ transform: `translate(${previewPos.x}px, ${previewPos.y}px)` }}
              onClick={e => e.stopPropagation()}
            >
              {previewType === "video" ? (
                <video src={previewUrl} controls autoPlay className="preview-full" />
              ) : (
                <img src={previewUrl} alt="预览" className="preview-full" />
              )}
          <button className="preview-modal-close" onClick={() => !hasUploading && setPreviewUrl(null)} disabled={hasUploading}>×</button>
            </div>
          </div>
        )}

        <div className="chat-input-area">
          <input type="file" ref={fileInputRef} accept="image/*,video/*" multiple onChange={handleFileChange} style={{ display: "none" }} />
          <button className="attach-btn" onClick={selectFiles} title="选择图片或视频">📎</button>
          <input
            type="text"
            className="chat-input"
            placeholder="输入消息..."
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button
            className="send-btn"
            onClick={() => void handleSend()}
            disabled={!canSend || hasUploading}
          >
            {hasUploading ? "上传中..." : "发送"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="chat-page">
      <div className="chat-header">
        <button className="back-btn" onClick={onBack}>← 返回</button>
        <div className="chat-friend-info">
          <div className="friend-avatar small">
            {friendAvatar ? <img src={friendAvatar} alt={friendNickname} /> : <span>{friendNickname[0]?.toUpperCase()}</span>}
          </div>
          <div className="friend-details">
            <div className="friend-name">{friendNickname}</div>
          </div>
        </div>
      </div>

      <div className="chat-messages">
        {loading ? (
          <div className="loading">加载中...</div>
        ) : messages.length === 0 ? (
          <div className="empty-state">暂无消息记录，开始聊天吧</div>
        ) : (
          messages.map((msg, index) => {
            const isMe = msg.senderId === currentUserId;
            return (
              <div key={index} className={`message-row ${isMe ? "me" : "friend"}`}>
                {!isMe && (
                  <div className="message-avatar">
                    {msg.senderAvatar ? <img src={msg.senderAvatar} alt="" /> : <span>{msg.senderNickname?.[0]?.toUpperCase()}</span>}
                  </div>
                )}
                <div className="message-bubble">
                  {renderFileMessage(msg)}
                  {msg.content && <div className="message-content">{msg.content}</div>}
                  <div className="message-time">
                    {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  </div>
                </div>
                {isMe && (
                  <div className="message-avatar me">
                    {user?.avatar ? <img src={user.avatar} alt="" /> : <span>{user?.nickname?.[0]?.toUpperCase()}</span>}
                  </div>
                )}
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {pendingFiles.length > 0 && (
        <div className="upload-preview-area">
          {pendingFiles.map(pf => (
            <div key={pf.id} className={`upload-preview-item ${pf.error ? "error" : ""}`}>
              {pf.fileType === "video" ? (
                <video src={pf.preview} className="preview-thumb" muted playsInline />
              ) : (
                <img src={pf.preview} alt="预览" className="preview-thumb" />
              )}
              {pf.uploading && (
                <>
                  <div className="upload-progress-bar">
                    <div className="upload-progress-fill" style={{ width: `${pf.progress}%` }} />
                  </div>
                  <span className="upload-progress-text">{pf.progress}%</span>
                </>
              )}
              {pf.error && <span className="upload-error-text">上传失败</span>}
              {!pf.uploading && !pf.error && <span className="upload-ready-text">待发送</span>}
              <button className="upload-cancel-btn" onClick={() => void handleCancelFile(pf.id)}>×</button>
            </div>
          ))}
        </div>
      )}

      <div className="chat-input-area">
        <input type="file" ref={fileInputRef} accept="image/*,video/*" multiple onChange={handleFileChange} style={{ display: "none" }} />
        <button className="attach-btn" onClick={selectFiles} title="选择图片或视频">📎</button>
        <textarea
          className="chat-input"
          placeholder="输入消息..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          rows={1}
        />
        <button className="btn-send" onClick={() => void handleSend()} disabled={!canSend || hasUploading}>
          {hasUploading ? "上传中..." : "发送"}
        </button>
      </div>
    </div>
  );
});

export default ChatPage;