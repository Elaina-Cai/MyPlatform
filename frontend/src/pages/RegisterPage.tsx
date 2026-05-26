import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getStoredToken } from "../api/http";
import * as authApi from "../api/auth";
import { useAuth } from "../auth/AuthContext";

export function RegisterPage() {
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
      const res = await authApi.register(username.trim(), password);
      if (res.code === 200 && res.data?.token) {
        await setSessionToken(res.data.token);
        if (!getStoredToken()) {
          setError("会话校验失败，请重试");
          return;
        }
        navigate("/", { replace: true });
        return;
      }
      setError(res.message || "注册失败");
    } catch (err) {
      setError(err instanceof Error ? err.message : "网络错误");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="shell">
      <div className="card">
        <h1>注册</h1>
        <p className="subtitle">注册成功后将自动登录</p>
        {error ? <p className="banner">{error}</p> : null}
        <form onSubmit={onSubmit}>
          <div className="field">
            <label htmlFor="reg-username">用户名</label>
            <input
              id="reg-username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="reg-password">密码</label>
            <input
              id="reg-password"
              name="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button className="primary" type="submit" disabled={submitting}>
            {submitting ? "提交中…" : "注册并登录"}
          </button>
        </form>
        <p className="footer">
          已有账号？<Link to="/login">去登录</Link>
        </p>
      </div>
    </div>
  );
}
