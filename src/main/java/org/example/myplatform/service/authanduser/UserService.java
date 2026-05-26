package org.example.myplatform.service.authanduser;
import org.example.myplatform.entity.User;
import org.example.myplatform.vo.AuthVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 * 功能：
 * - 用户注册（注册成功后自动登录，返回 token）
 * - 用户登录（返回 token）
 * - 用户登出（将 token 加入黑名单）
 * - 获取用户信息
 * 设计说明：
 * - register() 成功后返回 AuthVO（包含 token），实现注册后自动登录
 * - login() 和 register() 返回结构相同，统一前端处理
 * - logout() 需要传入 token，用于加入黑名单
 */
public interface UserService {
    /**
     * 用户注册
     * 流程：
     * 1. 检查用户名是否已存在
     * 2. 密码加密存储
     * 3. 生成随机昵称和默认头像
     * 4. 插入数据库
     * 5. 生成 token 并记录活跃时间
     * 6. 返回 AuthVO（包含 token + 用户信息）
     * @param username 用户名
     * @param password 密码（明文）
     * @return AuthVO 包含 token 和用户信息
     * @throws RuntimeException 用户名已存在时抛出
     */
    AuthVO register(String username, String password);
    /**
     * 用户登录
     * 流程：
     * 1. 查找用户
     * 2. 验证密码
     * 3. 生成 token 并记录活跃时间
     * 4. 返回 AuthVO（包含 token + 用户信息）
     * @param username 用户名
     * @param password 密码（明文）
     * @return AuthVO 包含 token 和用户信息
     * @throws RuntimeException 用户名或密码错误时抛出
     */
    AuthVO login(String username, String password);
    /**
     * 用户登出
     * 流程：
     * 1. 将 token 加入黑名单
     * 2. 删除用户活跃时间记录
     * @param userId   用户ID
     * @param token    当前 token（用于加入黑名单）
     * @param remainingTime 黑名单 TTL（毫秒），来自 jwt.blacklist-retention-ms
     */
    void logout(Long userId, String token, long remainingTime);
    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户对象，不存在返回 null
     */
    User getUserById(Long id);
    /**
     * 更新用户头像
     * @param userId    用户ID
     * @param avatarUrl 头像URL（null表示删除头像，使用默认头像）
     */
    void updateAvatar(Long userId, String avatarUrl);
    /**
     * 上传用户头像
     * 核心逻辑：
     * 1. 验证文件（类型、大小）
     * 2. 确保上传目录存在
     * 3. 生成文件名（用户ID_时间戳.扩展名）
     * 4. 保存文件到磁盘
     * 5. 更新数据库中的头像URL
     * @param userId 用户ID
     * @param file   上传的文件
     * @return 头像URL路径
     */
    String uploadAvatar(Long userId, MultipartFile file);
    /**
     * 更新用户昵称
     * @param userId      用户ID
     * @param newNickname 新昵称
     * @return 是否更新成功
     */
    boolean updateNickname(Long userId, String newNickname);
}