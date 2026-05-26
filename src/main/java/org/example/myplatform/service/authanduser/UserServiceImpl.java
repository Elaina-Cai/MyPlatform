package org.example.myplatform.service.authanduser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.myplatform.entity.User;
import org.example.myplatform.mapper.UserMapper;
import org.example.myplatform.utils.JwtUtil;
import org.example.myplatform.utils.RedisUtil;
import org.example.myplatform.vo.AuthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
/**
 * 用户服务实现
 *
 * 实现 UserService 接口的所有方法
 * 核心逻辑：注册/登录时生成 token 并记录 Redis 活跃时间
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    
    private static final String AVATAR_BASE_URL = "https://api.dicebear.com/7.x/avataaars/svg?seed=";
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    /**
     * 密码加密器
     * BCrypt 每次加密结果不同（包含盐值），但 matches() 可正确验证
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${avatar.upload-path}")
    private String uploadPath;

    @Override
    public AuthVO register(String username, String password) {
        //步骤1：检查用户名是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        //步骤2：构建用户对象
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));  // 密码加密存储
        user.setNickname(generateRandomNickname());          // 生成随机昵称
        user.setAvatar(AVATAR_BASE_URL + username);           // 生成默认头像（根据用户名生成唯一头像）
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        //步骤3：插入数据库
        userMapper.insert(user);
        //给redis缓存用户信息（用于聊天和好友功能）
        redisUtil.setWithExpire("user:" + user.getId(), user);
        //步骤4：生成 token 并记录token与当下时间作为其最新的活跃时间于redis中
        String token = jwtUtil.generateToken(user.getId());
        long now = System.currentTimeMillis();
        redisUtil.setUserLastActivityTime(user.getId(), now);
        //(userId->token)
        redisUtil.setCurrentUserToken(user.getId(), token);
        //步骤5：返回 AuthVO
        return new AuthVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getAvatar());
    }
    @Override
    public AuthVO login(String username, String password) {
        //步骤1：查找用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        //步骤2：验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        //步骤3：生成 token 并记录活跃时间到redis中
        String token = jwtUtil.generateToken(user.getId());
        // 缓存用户信息到 Redis（用于聊天功能）
        redisUtil.setUserProfile(user.getId(), user.getNickname(), user.getAvatar());
        log.debug("缓存头像: userId={}, avatar={}", user.getId(), user.getAvatar());
        // 顶号逻辑(如果本来这个账号已经在线)：将用户原有的有效 token 加入黑名单
        String oldToken = redisUtil.getCurrentUserToken(user.getId());
        if (oldToken != null && !oldToken.equals(token)) {
            long ttlMillis = jwtUtil.getBlacklistRetentionMillis();
            if (ttlMillis > 0) {
                redisUtil.addTokenToBlacklist(oldToken, ttlMillis);
            }
        }
        // 保存新 token 为当前有效 token (userId -> token)
        redisUtil.setCurrentUserToken(user.getId(),token);
        //(userId -> 最后活跃时间)
        long now = System.currentTimeMillis();
        redisUtil.setUserLastActivityTime(user.getId(), now);
        // 保存会话到 Redis
        redisUtil.saveUserSession(user.getId(), token, System.currentTimeMillis());
        //步骤4：返回 AuthVO
        return new AuthVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getAvatar());
    }
    @Override
    public void logout(Long userId, String token, long remainingTime) {
        // 步骤1：将 token 加入redis的token黑名单（TTL 由调用方传入，对应 jwt.blacklist-retention-ms）
        if (remainingTime > 0) {
            redisUtil.addTokenToBlacklist(token, remainingTime);
        }
        //步骤2：删除用户活跃时间记录
        redisUtil.deleteUserActivity(userId);
        //删除(user -> token)
        redisUtil.deleteCurrentUserToken(userId);
    }
    @Override
    public User getUserById(Long id) {
        if (id == null) return null;
        String key = "user:" + id;
        // TODO: 高并发下可能短暂不一致，可考虑加分布式锁或延迟双删
        User cached = redisUtil.get(key, User.class);
        if (cached != null){
            return cached;
        }
        User user = userMapper.selectById(id);
        if (user != null) {
            // TODO: 缓存回填时若并发更新，可能导致旧值覆盖新值
            redisUtil.setWithExpire(key, user);
        } else {
            //没有找到，存储NULL值到redis中，过期时间为1小时+随机300秒（防穿透）
            redisUtil.setNullValue(key);
        }
        return user;
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        log.info("更新头像: userId={}, avatarUrl={}", userId, avatarUrl);
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新头像失败: 用户不存在, userId={}", userId);
            throw new RuntimeException("用户不存在");
        }
        String oldAvatar = user.getAvatar();
        if (avatarUrl == null) {
            user.setAvatar(AVATAR_BASE_URL + user.getUsername());
        } else {
            user.setAvatar(avatarUrl);
        }
        userMapper.updateById(user);
        // TODO: 高并发下缓存更新和DB更新之间可能有请求读到旧值
        redisUtil.setUserProfile(user.getId(), user.getNickname(), user.getAvatar());
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        if (file.isEmpty()) {
            throw new RuntimeException("请选择要上传的文件");
        }

        if (originalFilename == null ||
                (!originalFilename.toLowerCase().endsWith(".png") &&
                        !originalFilename.toLowerCase().endsWith(".jpg") &&
                        !originalFilename.toLowerCase().endsWith(".jpeg"))) {
            throw new RuntimeException("仅支持 PNG/JPG/JPEG 格式");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("文件大小不能超过5MB");
        }

        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            try {
                Files.createDirectories(uploadDir);
            } catch (IOException e) {
                throw new RuntimeException("创建上传目录失败");
            }
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = userId + "_" + System.currentTimeMillis() + extension;
        Path filePath = uploadDir.resolve(fileName);

        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }

        String avatarUrl = "/avatars/" + fileName;
        updateAvatar(userId, avatarUrl);

        return avatarUrl;
    }

    @Override
    public boolean updateNickname(Long userId, String newNickname) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新昵称失败：用户不存在，userId={}", userId);
            throw new RuntimeException("用户不存在");
        }
        
        String oldNickname = user.getNickname();
        user.setNickname(newNickname);
        // 更新数据库中的昵称
        userMapper.updateById(user);
        
        log.info("用户昵称更新成功：userId={}, oldNickname={}, newNickname={}", 
                userId, oldNickname, newNickname);

        // 更新 Redis 缓存（确保聊天消息显示最新昵称）
        redisUtil.setUserProfile(user.getId(), user.getNickname(), user.getAvatar());
        
        return true;
    }
    /**
     * 生成随机昵称
     * 格式：Player_xxxxxxxx
     * - "Player_" 前缀固定
     * - 后8位是 UUID 的前8个字符
     * 示例：
     * - Player_a1b2c3d4
     * - Player_9x8y7z6w
     * @return 随机昵称
     */
    private String generateRandomNickname() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "Player_" + uuid;
    }
}