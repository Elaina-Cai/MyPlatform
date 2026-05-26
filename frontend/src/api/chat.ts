import { apiRequest } from "./http";
import { MessageVO } from "../types";

export interface UploadResult {
  fileUrl: string;
  fileType: string;
  fileName: string;
}

export async function getChatHistory(friendId: number): Promise<MessageVO[]> {
  const res = await apiRequest<MessageVO[]>(`/api/message/history?friendId=${friendId}`);
  return res.data || [];
}

export async function getUnreadCount(): Promise<number> {
  const res = await apiRequest<{ unreadCount: number }>("/api/message/unread");
  return res.data?.unreadCount || 0;
}

export async function uploadFile(
  file: File,
  onProgress?: (percent: number) => void
): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append("file", file);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const result = JSON.parse(xhr.responseText);
        if (result.code === 200) {
          resolve(result.data);
        } else {
          reject(new Error(result.message || "上传失败"));
        }
      } else {
        reject(new Error("上传失败: " + xhr.status));
      }
    };

    xhr.onerror = () => reject(new Error("网络错误"));

    const token = localStorage.getItem("token");
    xhr.open("POST", "/api/upload/file");
    xhr.setRequestHeader("Authorization", token ? `Bearer ${token}` : "");

    xhr.send(formData);
  });
}

export async function deleteFile(fileUrl: string): Promise<void> {
  await apiRequest(`/api/upload/file?fileUrl=${encodeURIComponent(fileUrl)}`, {
    method: "DELETE"
  });
}