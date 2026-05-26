package org.example.myplatform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户VO，用于前端展示用户信息的视图对象
 * 包含用户的基本信息，如ID、用户名、昵称、头像URL、手机号（可选）、创建时间、更新时间
 * 用于前端展示用户信息，避免直接暴露数据库实体字段，保护用户隐私
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long Id;           // 用户ID
    private String username;   // 用户名
    private String nickname;   // 昵称
    private String avatar;     // 头像URL
    private String phone;      // 手机号（可选）
    private String createdAt;  // 创建时间
    private String updatedAt;  // 更新时间
}
