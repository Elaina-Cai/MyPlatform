import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getStoredToken } from "../api/http";
import * as authApi from "../api/auth";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const navigate = useNavigate();
  const { setSessionToken } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await authApi.login(username.trim(), password);
      if (res.code === 200 && res.data?.token) {
        await setSessionToken(res.data.token);
        if (!getStoredToken()) {
          setError("会话校验失败，请重试");
          return;
        }
        navigate("/", { replace: true });
        return;
      }
      setError(res.message || "登录失败");
    } catch (err) {
      setError(err instanceof Error ? err.message : "网络错误");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="shell">
      <div className="card">
        <h1>登录</h1>
        <p className="subtitle">使用账号密码访问 MyPlatform</p>
        {error ? <p className="banner">{error}</p> : null}
        <form onSubmit={onSubmit}>
          <div className="field">
            <label htmlFor="username">用户名</label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">密码</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button className="primary" type="submit" disabled={submitting}>
            {submitting ? "登录中…" : "登录"}
          </button>
        </form>
        <p className="footer">
          还没有账号？<Link to="/register">去注册</Link>
        </p>
      </div>
    </div>
  );
}
