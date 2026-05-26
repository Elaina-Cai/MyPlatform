package org.example.myplatform.dto.auth;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户注册请求（包含用户名和密码）
 * 用于用户注册，包含用户名和密码。
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}