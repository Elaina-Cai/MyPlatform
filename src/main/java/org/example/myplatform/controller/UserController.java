package org.example.myplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.myplatform.entity.User;
import org.example.myplatform.exception.BadRequestException;
import org.example.myplatform.exception.UnauthorizedException;
import org.example.myplatform.interceptor.JwtInterceptor;
import org.example.myplatform.service.authanduser.UserService;
import org.example.myplatform.vo.Result;
import org.example.myplatform.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    private final UserService userService;

    @Value("${avatar.upload-path}")
    private String uploadPath;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    /**
     * 获取当前登录用户信息（用于前端验证 token 是否有效）
     */
    @GetMapping("/info")
    public Result<UserVO> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setNickname(user.getNickname());
        if (user.getCreatedAt() != null) {
            userVO.setCreatedAt(user.getCreatedAt().toString());
        }
        if (user.getUpdatedAt() != null) {
            userVO.setUpdatedAt(user.getUpdatedAt().toString());
        }
        return Result.success(userVO);
    }
    /**
     * 上传头像
     * @param file 头像文件
     * @param request 请求对象
     * @return 头像URL
     */
    @PostMapping("/avatar/upload")
    public Result<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }

        String avatarUrl = userService.uploadAvatar(userId, file);

        Map<String, String> result = new HashMap<>();
        result.put("avatarUrl", avatarUrl);
        return Result.success(result);
    }
    /**
     * 删除头像（恢复默认）
     * @param request 请求对象
     * @return 成功消息
     */
    @DeleteMapping("/avatar")
    public Result<Map<String, String>> deleteAvatar(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        
        userService.updateAvatar(userId, null);
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "头像已删除");
        return Result.success(result);
    }
    /**
     * 根据用户ID获取用户信息
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return Result.success(userVO);
    }
    /**
     * 更新昵称
     * @param request 请求对象
     * @return 更新后的用户信息
     */
    @PostMapping("/nickname")
    public Result<UserVO> updateNickname(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        String newNickname = body.get("newNickname");

        if (newNickname == null || newNickname.isEmpty()) {
            throw new BadRequestException("昵称不能为空");
        }
        if (newNickname.length() < 2 || newNickname.length() > 20) {
            throw new BadRequestException("昵称长度需在2-20个字符之间");
        }

        boolean result = userService.updateNickname(userId, newNickname.trim());
        if (!result) {
            throw new BadRequestException("昵称更新失败");
        }

        log.info("用户{}修改昵称为：{}", userId, newNickname);

        User user = userService.getUserById(userId);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setNickname(user.getNickname());
        return Result.success(userVO);
    }
}