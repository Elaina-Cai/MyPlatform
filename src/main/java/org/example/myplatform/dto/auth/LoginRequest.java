package org.example.myplatform.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求（包含用户名和密码）
 * 用于用户登录认证，包含用户名和密码。
 */
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
