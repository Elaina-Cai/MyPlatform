import type { ApiResult } from "../types";

const TOKEN_KEY = "token";

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  auth?: boolean;
  /** 若传入则用作 Bearer（登录后立即拉用户信息时传入，避免依赖刚写入的 localStorage） */
  bearerOverride?: string | null;
}

/**
 * 请求统一走相对路径 /api。开发时由 Vite 代理到后端 8080；生产由 nginx 反代到 8080。
 * 后端返回 Result：成功 code=200；业务/鉴权错误在 JSON 的 code 字段（如 401、422）。
 */
export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<ApiResult<T>> {
  const { method = "GET", body, auth = true, bearerOverride } = options;
  const headers: Record<string, string> = {
    Accept: "application/json",
  };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (auth) {
    const token =
      bearerOverride !== undefined ? bearerOverride : getStoredToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let json: ApiResult<T>;
  try {
    json = text ? (JSON.parse(text) as ApiResult<T>) : ({} as ApiResult<T>);
  } catch {
    throw new Error(text || `请求失败 (${res.status})`);
  }

  if (json.code === 401) {
    setStoredToken(null);
  }

  return json;
}