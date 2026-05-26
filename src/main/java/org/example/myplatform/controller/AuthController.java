package org.example.myplatform.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.example.myplatform.dto.auth.LoginRequest;
import org.example.myplatform.dto.auth.RegisterRequest;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.service.authanduser.UserService;
import org.example.myplatform.utils.JwtUtil;
import org.example.myplatform.vo.AuthVO;
import org.example.myplatform.vo.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
/**
 * 认证控制器
 *
 * 接口列表：
 * - POST /api/auth/register  → 用户注册（注册成功后自动登录）
 * - POST /api/auth/login     → 用户登录
 * - POST /api/auth/logout    → 用户登出
 *
 * 返回格式：统一使用 Result<T> 包装
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil){
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }
    /**
     * 用户注册
     *
     * 流程：
     * 1. 接收注册请求（username + password）
     * 2. 调用 UserService.register() 注册并自动登录
     * 3. 返回 AuthVO（包含 token + 用户信息）
     *
     * @param request 注册请求（包含 username 和 password）
     * @return Result<AuthVO> 包含 token 和用户信息
     */
    @PostMapping("/register")
    public Result<AuthVO> register(@RequestBody @Valid RegisterRequest request) {
        AuthVO authVO = userService.register(request.getUsername(), request.getPassword());
        return Result.success("注册成功", authVO);
    }
    /**
     * 用户登录
     *
     * 流程：
     * 1. 接收登录请求（username + password）
     * 2. 调用 UserService.login() 验证并返回 token
     * 3. 返回 AuthVO（包含 token + 用户信息）
     *
     * @param request 登录请求（包含 username 和 password）
     * @return Result<AuthVO> 包含 token 和用户信息
     */
    @PostMapping("/login")
    public Result<AuthVO> login(@RequestBody @Valid LoginRequest request) {
        AuthVO authVO = userService.login(request.getUsername(), request.getPassword());
        // 返回登录成功结果，包含 token 和用户信息
        return Result.success("登录成功", authVO);
    }
    /**
     * 用户登出
     *
     * 流程：
     * 1. 从请求属性中获取 userId（由 JwtInterceptor 存入）
     * 2. 从请求头中获取 token
     * 3. 使用配置的 blacklist TTL，将 token 加入黑名单
     * 4. 调用 UserService.logout() 清理 Redis 会话
     *
     * 说明：
     * - userId 从 JwtInterceptor 的 request 属性获取
     * - token 从请求头获取（Authorization: Bearer xxx）
     *
     * @param request HTTP 请求（用于获取 userId）
     * @return Result<Void> 只返回成功状态，无 data
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 从请求属性获取 userId（由 JwtInterceptor 存入）
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        // 从请求头获取 token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(400, "未携带 token");
        }
        String token = authHeader.substring(7);  // 去掉 "Bearer " 前缀
        long blacklistTtl = jwtUtil.getBlacklistRetentionMillis();
        userService.logout(userId, token, Math.max(0, blacklistTtl));
        return Result.success("登出成功", null);
    }
}