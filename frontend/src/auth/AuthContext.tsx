import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getStoredToken, setStoredToken } from "../api/http";
import { apiRequest } from "../api/http";
import * as authApi from "../api/auth";
import type { User } from "../types";
import type { MessageVO } from "../types";

declare global {
  interface Window {
    webSocket?: WebSocket;
  }
}

interface AuthState {
  token: string | null;
  user: User | null;
  loading: boolean;
}

interface AuthContextValue extends AuthState {
  refreshUser: (accessToken?: string | null) => Promise<void>;
  signOut: () => Promise<void>;
  setSessionToken: (token: string | null) => Promise<void>;
  sendWsMessage: (receiverId: number, content: string, fileUrl?: string | null, fileType?: string | null) => void;
  sendWsRaw: (msg: string) => void;
  maxFileSize: number;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(!!getStoredToken());
  const [maxFileSize, setMaxFileSize] = useState<number>(524288000);
  const wsRef = useRef<WebSocket | null>(null);
  const configLoadedRef = useRef(false);

  const refreshUser = useCallback(async (accessToken?: string | null) => {
    const t =
      accessToken !== undefined && accessToken !== null
        ? accessToken
        : getStoredToken();
    if (!t) {
      setUser(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.fetchCurrentUser(t);
      if (res.code === 200 && res.data) {
        setUser(res.data);
      } else {
        setUser(null);
        if (res.code === 401) {
          setStoredToken(null);
          setToken(null);
        }
      }
    } finally {
      setLoading(false);
      if (!configLoadedRef.current) {
        configLoadedRef.current = true;
        try {
          const configRes = await apiRequest<{ maxSize: number }>("/api/upload/config");
          if (configRes.data?.maxSize) {
            setMaxFileSize(configRes.data.maxSize);
          }
        } catch (err) {
          console.error("加载上传配置失败", err);
        }
      }
    }
  }, []);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const initWebSocket = useCallback(() => {
    const wsToken = localStorage.getItem("token");
    if (!wsToken) return;

    wsRef.current?.close();

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws/chat?token=${encodeURIComponent(wsToken)}`;
    const ws = new WebSocket(wsUrl);
    window.webSocket = ws;

    ws.onopen = () => {
      console.log("WebSocket 连接成功");
      window.dispatchEvent(new Event("ws-connected"));
    };

    ws.onmessage = (event) => {
      console.log("WebSocket 收到消息:", event.data);
      try {
        const message = JSON.parse(event.data);
        switch (message.type) {
          case "chat":
            window.dispatchEvent(new CustomEvent("chat-message", { detail: message as MessageVO }));
            break;
          case "group_message":
            window.dispatchEvent(new CustomEvent("group-message", { detail: message }));
            break;
          case "friend_online":
            window.dispatchEvent(new CustomEvent("friend-online", { detail: message }));
            break;
          case "friend_offline":
            window.dispatchEvent(new CustomEvent("friend-offline", { detail: message }));
            break;
          case "friend_away":
            window.dispatchEvent(new CustomEvent("friend-away", { detail: message }));
            break;
          case "friends_status_ready":
            window.dispatchEvent(new CustomEvent("friends-status-ready"));
            break;
          case "friend_apply":
            window.dispatchEvent(new CustomEvent("friend-apply", { detail: message }));
            break;
          case "friend_apply_success":
            window.dispatchEvent(new CustomEvent("friend-apply-success", { detail: message }));
            break;
          case "friend_apply_error":
            window.dispatchEvent(new CustomEvent("friend-apply-error", { detail: message }));
            break;
          case "friend_accepted":
            window.dispatchEvent(new CustomEvent("friend-accepted", { detail: message }));
            break;
          case "friend_rejected":
            window.dispatchEvent(new CustomEvent("friend-rejected", { detail: message }));
            break;
          case "group_invite":
            window.dispatchEvent(new CustomEvent("group-invite", { detail: message }));
            break;
          case "group_invite_success":
            window.dispatchEvent(new CustomEvent("group-invite-success", { detail: message }));
            break;
          case "group_invite_error":
            window.dispatchEvent(new CustomEvent("group-invite-error", { detail: message }));
            break;
          case "group_member_added":
            window.dispatchEvent(new CustomEvent("group-member-added", { detail: message }));
            break;
        }
      } catch (err) {
        console.error("解析 WebSocket 消息失败", err);
      }
    };

    ws.onclose = () => {
      console.log("WebSocket 断开");
      window.dispatchEvent(new Event("ws-closed"));
    };

    ws.onerror = (error) => {
      console.error("WebSocket 错误:", error);
    };

    wsRef.current = ws;
  }, []);

  useEffect(() => {
    if (token) {
      initWebSocket();
    } else {
      wsRef.current?.close();
      wsRef.current = null;
    }
  }, [token, initWebSocket]);

  useEffect(() => {
    function handleVisibilityChange() {
      if (document.visibilityState === "visible" && token) {
        void refreshUser();
        if (wsRef.current?.readyState !== WebSocket.OPEN) {
          void initWebSocket();
        }
      }
    }
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [token, refreshUser, initWebSocket]);

  const sendWsMessage = useCallback((receiverId: number, content: string, fileUrl?: string | null, fileType?: string | null) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const msg: Record<string, unknown> = { type: "chat", receiverId, content };
      if (fileUrl) msg.fileUrl = fileUrl;
      if (fileType) msg.fileType = fileType;
      console.log("发送 WebSocket 消息:", JSON.stringify(msg));
      wsRef.current.send(JSON.stringify(msg));
    } else {
      console.warn("WebSocket 未连接，消息发送失败");
    }
  }, []);

  const sendWsRaw = useCallback((msg: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(msg);
    } else {
      console.warn("WebSocket 未连接，发送失败");
    }
  }, []);

  const setSessionToken = useCallback(async (newToken: string | null) => {
    setStoredToken(newToken);
    setToken(newToken);
    if (newToken) {
      await refreshUser(newToken);
    } else {
      setUser(null);
      setLoading(false);
    }
  }, [refreshUser]);

  const signOut = useCallback(async () => {
    wsRef.current?.close();
    wsRef.current = null;
    try {
      if (getStoredToken()) {
        await authApi.logout();
      }
    } finally {
      await setSessionToken(null);
    }
  }, [setSessionToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      user,
      loading,
      refreshUser,
      signOut,
      setSessionToken,
      sendWsMessage,
      sendWsRaw,
      maxFileSize,
    }),
    [token, user, loading, refreshUser, signOut, setSessionToken, sendWsMessage, sendWsRaw, maxFileSize],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}