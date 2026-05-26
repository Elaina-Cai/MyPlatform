import { useEffect, useState, useRef, forwardRef, useImperativeHandle } from "react";
import { GroupMessageVO } from "../types";
import { getGroupMessages, uploadFile } from "../api/chatgroup";
import { useAuth } from "../auth/AuthContext";

interface GroupPendingFile {
  id: string;
  file: File;
  preview: string;
  fileType: string;
  progress: number;
  uploading: boolean;
  error: string | null;
  result: { fileUrl: string; fileType: string } | null;
}

interface GroupChatPageProps {
  groupId: number;
  groupName: string;
  groupAvatar: string | null;
  onBack: () => void;
  onShowGroupInfo: () => void;
  onRead?: () => void;
}

export interface GroupChatPageRef {
  cleanup: () => Promise<void>;
}

const GroupChatPage = forwardRef<GroupChatPageRef, GroupChatPageProps>(({
  groupId,
  groupName,
  groupAvatar: _groupAvatar,
  onBack,
  onShowGroupInfo,
  onRead,
}, ref) => {
  const { user, sendWsRaw, maxFileSize } = useAuth();
  const [messages, setMessages] = useState<GroupMessageVO[]>([]);
  const messagesRef = useRef<GroupMessageVO[]>([]);
  const pendingFilesRef = useRef<GroupPendingFile[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [pendingFiles, setPendingFiles] = useState<GroupPendingFile[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUserId = user?.id;
  const isSending = useRef(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewType, setPreviewType] = useState<"image" | "video" | null>(null);
  const [previewPos, setPreviewPos] = useState({ x: 0, y: 0 });
  const previewRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const isDragging = useRef(false);
  const dragOffset = useRef({ x: 0, y: 0 });
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const currentPage = useRef(1);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const [newMessageCount, setNewMessageCount] = useState(0);
  const isAtBottom = useRef(true);
  const prevMessagesLength = useRef(0);

  useImperativeHandle(ref, () => ({
    cleanup: cleanupPendingFiles,
  }));

  useEffect(() => {
    console.log("GroupChatPage mounted, groupId:", groupId);
    pendingFilesRef.current = pendingFiles;
  }, [pendingFiles]);

  useEffect(() => {
    console.log("GroupChatPage effect run, groupId:", groupId);
    void loadHistory();
    setPendingFiles([]);
    pendingFilesRef.current = [];
    const unsub = listenMessages();
    subscribeChannel();
    return () => {
      console.log("GroupChatPage cleanup, groupId:", groupId);
      void cleanupPendingFiles();
      unsub();
      unsubscribeChannel();
    };
  }, [groupId]);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    if (!messagesContainerRef.current) return;
    const handleScroll = () => {
      if (!messagesContainerRef.current || loadingMore || !hasMore) return;
      if (messagesContainerRef.current.scrollTop < 100) {
        void loadMore();
      }
      if (messagesContainerRef.current) {
        const { scrollTop, scrollHeight, clientHeight } = messagesContainerRef.current;
        isAtBottom.current = scrollHeight - scrollTop - clientHeight < 100;
        if (isAtBottom.current) {
          setNewMessageCount(0);
        }
      }
    };
    messagesContainerRef.current.addEventListener("scroll", handleScroll);
    return () => {
      messagesContainerRef.current?.removeEventListener("scroll", handleScroll);
    };
  }, [loadingMore, hasMore]);

  useEffect(() => {
    if (newMessageCount === 0 && isAtBottom.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  async function loadHistory(append = false) {
    try {
      if (append) {
        setLoadingMore(true);
      }
      const history = await getGroupMessages(groupId, currentPage.current, 50);
      if (append) {
        const newCount = history.length;
        if (!isAtBottom.current) {
          setNewMessageCount(prev => prev + newCount);
        }
        setMessages(prev => [...prev, ...history]);
      } else {
        setMessages(history);
        prevMessagesLength.current = history.length;
      }
      setHasMore(history.length === 50);
      onRead?.();
    } catch (err) {
      console.error("加载群聊记录失败", err);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }

  async function loadMore() {
    if (loadingMore || !hasMore) return;
    currentPage.current += 1;
    await loadHistory(true);
    if (messagesContainerRef.current) {
      const prevHeight = messagesContainerRef.current.scrollHeight;
      requestAnimationFrame(() => {
        if (messagesContainerRef.current) {
          messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight - prevHeight;
        }
      });
    }
  }

  function goToNewMessage() {
    setNewMessageCount(0);
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }

  function subscribeChannel() {
    const msg = JSON.stringify({ type: "subscribe_group", groupId });
    sendWsRaw(msg);
  }

  function unsubscribeChannel() {
    const msg = JSON.stringify({ type: "unsubscribe_group", groupId });
    sendWsRaw(msg);
  }

  function listenMessages() {
    function handleNewMessage(e: Event) {
      const msg = e as CustomEvent<{
        groupId: number;
        messageId: number;
        senderId: number;
        senderNickname: string;
        senderAvatar: string;
        content: string;
        messageType: number;
        fileUrl?: string;
        fileType?: string;
        createdAt: string;
      }>;
      if (msg.detail.groupId === groupId) {
        const exists = messagesRef.current.some(m => m.id === msg.detail.messageId);
        if (exists) return;

        const message: GroupMessageVO = {
          id: msg.detail.messageId,
          groupId: msg.detail.groupId,
          senderId: msg.detail.senderId,
          senderNickname: msg.detail.senderNickname,
          senderAvatar: msg.detail.senderAvatar,
          content: msg.detail.content,
          messageType: msg.detail.messageType,
          fileUrl: msg.detail.fileUrl,
          fileType: msg.detail.fileType,
          createdAt: msg.detail.createdAt,
          isRead: false,
        };
        setMessages(prev => [...prev, message]);
      }
    }
    window.addEventListener("group-message", handleNewMessage);
    return () => window.removeEventListener("group-message", handleNewMessage);
  }

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

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

      const pendingFile: GroupPendingFile = {
        id: Math.random().toString(36).slice(2),
        file,
        preview,
        fileType: isImage ? "image" : "video",
        progress: 0,
        uploading: false,
        error: null,
        result: null,
      };
      setPendingFiles(prev => [...prev, pendingFile]);
    }
    e.target.value = "";
  }

  async function cleanupPendingFiles() {
    for (const pf of pendingFilesRef.current) {
      if (pf.preview) {
        URL.revokeObjectURL(pf.preview);
      }
    }
    pendingFilesRef.current = [];
    setPendingFiles([]);
  }

  async function uploadAndSend(pf: GroupPendingFile, retryCount = 0): Promise<{ fileUrl: string; fileType: string } | null> {
    try {
      const result = await uploadFile(pf.file, (percent) => {
        setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, progress: percent } : p));
      });
      return result;
    } catch (err) {
      if (retryCount < 3) {
        await new Promise(resolve => setTimeout(resolve, 1000 * (retryCount + 1)));
        return uploadAndSend(pf, retryCount + 1);
      }
      const errorMsg = err instanceof Error ? err.message : "上传失败";
      setPendingFiles(prev => prev.map(p => p.id === pf.id ? { ...p, uploading: false, error: errorMsg } : p));
      return null;
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

    for (const pf of readyFiles) {
      const result = await uploadAndSend(pf);

      if (result) {
        if (pf.preview) {
          URL.revokeObjectURL(pf.preview);
        }

        const wsMsg = JSON.stringify({
          type: "group_chat",
          groupId,
          content: content || "",
          messageType: 1,
          fileUrl: result.fileUrl,
          fileType: result.fileType,
        });
        sendWsRaw(wsMsg);
      }
    }

    if (content && readyFiles.length === 0) {
      const wsMsg = JSON.stringify({
        type: "group_chat",
        groupId,
        content,
        messageType: 1,
      });
      sendWsRaw(wsMsg);
    }

    setPendingFiles(prev => prev.filter(p => !readyFiles.some(r => r.id === p.id)));
    setInput("");
    isSending.current = false;
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

  function isOwnMessage(senderId: number): boolean {
    return senderId === currentUserId;
  }

  return (
    <div className="chat-page">
      <div className="chat-header">
        <div className="chat-title">
          <div className="chat-title-name">{groupName}</div>
        </div>
        <div className="chat-header-actions">
          <button className="icon-btn" onClick={onShowGroupInfo} title="群信息">ℹ</button>
          <button className="icon-btn close-btn" onClick={onBack} title="关闭">×</button>
        </div>
      </div>

      <div className="chat-messages" ref={messagesContainerRef}>
        {newMessageCount > 0 && (
          <div className="new-message-btn" onClick={goToNewMessage}>
            {newMessageCount}条新消息 ↑
          </div>
        )}
        {loading ? (
          <div className="loading">加载中...</div>
        ) : messages.length === 0 ? (
          <div className="empty-state">暂无消息</div>
        ) : (
          messages.map((msg, index) => (
            <div key={msg.id}>
              {shouldShowDateDivider(index) && (
                <div className="date-divider">{formatDate(msg.createdAt)}</div>
              )}
              <div className={`message ${isOwnMessage(msg.senderId) ? "own" : ""}`}>
                <div className="message-left">
                  <div className="message-sender">{isOwnMessage(msg.senderId) ? user?.nickname : msg.senderNickname}</div>
                  <div className="message-avatar">
                    {isOwnMessage(msg.senderId)
                      ? (user?.avatar ? <img src={user.avatar} alt="" /> : <span>{user?.nickname?.[0]?.toUpperCase()}</span>)
                      : (msg.senderAvatar ? <img src={msg.senderAvatar} alt="" /> : <span>{msg.senderNickname?.[0]?.toUpperCase()}</span>)
                    }
                  </div>
                </div>
                <div className="message-bubble-wrap">
                  {msg.fileUrl ? (
                    <>
                      {msg.fileType === "image" ? (
                        <img src={msg.fileUrl} alt="" className="message-image" onClick={() => window.open(msg.fileUrl)} />
                      ) : msg.fileType === "video" ? (
                        <video src={msg.fileUrl} className="message-video" controls />
                      ) : null}
                      {msg.content && <div className="message-bubble">{msg.content}</div>}
                    </>
                  ) : (
                    <div className="message-bubble">{msg.content}</div>
                  )}
                  <div className="message-time">{formatTime(msg.createdAt)}</div>
                </div>
              </div>
            </div>
          ))
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
              <button className="upload-cancel-btn" onClick={() => void handleCancelFile(pf.id)}>×</button>
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
            <button className="preview-modal-close" onClick={() => setPreviewUrl(null)}>×</button>
          </div>
        </div>
      )}

      <div className="chat-input-area">
        <button className="attach-btn" onClick={selectFiles} title="选择图片或视频">📎</button>
        <input
          type="file"
          ref={fileInputRef}
          style={{ display: "none" }}
          accept="image/*,video/*"
          onChange={handleFileChange}
        />
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
          disabled={!input.trim() && !pendingFiles.some(p => !p.uploading && !p.error)}
        >
          发送
        </button>
      </div>
    </div>
  );
});

export default GroupChatPage;