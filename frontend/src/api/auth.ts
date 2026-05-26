import { apiRequest, setStoredToken } from "./http";
import type { AuthVO, User } from "../types";

export async function login(username: string, password: string) {
  return apiRequest<AuthVO>("/api/auth/login", {
    method: "POST",
    body: { username, password },
    auth: false,
  });
}

export async function register(username: string, password: string) {
  return apiRequest<AuthVO>("/api/auth/register", {
    method: "POST",
    body: { username, password },
    auth: false,
  });
}

export async function logout() {
  try {
    return await apiRequest<null>("/api/auth/logout", {
      method: "POST",
    });
  } finally {
    setStoredToken(null);
  }
}

export async function fetchCurrentUser(accessToken?: string | null) {
  return apiRequest<User>("/api/user/info", {
    method: "GET",
    bearerOverride: accessToken,
  });
}

export async function getUserById(userId: number) {
  return apiRequest<User>("/api/user/" + userId, {
    method: "GET",
  });
}

export async function updateNickname(newNickname: string) {
  return apiRequest<User>("/api/user/nickname", {
    method: "POST",
    body: { newNickname },
  });
}