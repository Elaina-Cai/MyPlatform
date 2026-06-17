# MyPlatform

一个基于 Spring Boot 3.3.5 的综合平台后端服务，包含用户认证、好友系统、聊天室、文件管理和小游戏等功能。

## 技术栈

- **框架**: Spring Boot 3.3.5
- **语言**: Java 21
- **数据库**: MySQL 8.0+ + Redis 6.0+
- **ORM**: MyBatis-Plus 3.5.10.1
- **认证**: JWT (jjwt 0.11.5)
- **WebSocket**: Spring Boot WebSocket
- **构建工具**: Maven

## 功能模块

| 模块 | 说明 |
| :--- | :--- |
| **Auth** | 用户注册、登录、JWT认证 |
| **User** | 用户信息管理、头像上传 |
| **Friend** | 好友申请、好友关系管理 |
| **ChatGroup** | 群聊创建、成员管理、消息发送 |
| **Message** | 私信消息管理 |
| **File** | 文件上传下载 |
| **Xiaoxiaole** | 消消乐小游戏积分系统 |

## 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

## 部署方法

### 1. 克隆项目

```bash
git clone <repository-url>
cd MyPlatform
```

### 2. 数据库配置

#### MySQL 配置

创建数据库：

```sql
CREATE DATABASE my_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### Redis 配置

确保 Redis 服务运行，默认配置为：
- 主机：`192.168.75.133`
- 端口：`6379`
- 密码：`123456`
- 数据库：`0`

### 3. 配置文件

项目提供三种环境配置：

| 配置文件 | 环境 | 激活方式 |
| :--- | :--- | :--- |
| `application-local.yml` | 本地开发 | 默认激活 |
| `application-dev.yml` | 开发环境 | 通过环境变量或命令行参数 |
| `application.yml` | 基础配置 | 被其他配置继承 |

#### 环境变量配置（推荐用于生产环境）

| 环境变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `DB_HOST` | localhost | 数据库主机 |
| `DB_PORT` | 3306 | 数据库端口 |
| `DB_NAME` | my_platform | 数据库名称 |
| `DB_USERNAME` | root | 数据库用户名 |
| `DB_PASSWORD` | 123456 | 数据库密码 |
| `REDIS_HOST` | 192.168.75.133 | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_DB` | 0 | Redis 数据库 |
| `JWT_SECRET` | dev-only-not-for-production-32chars-min | JWT 密钥（生产环境必须更换） |
| `AVATAR_UPLOAD_PATH` | `${user.dir}/uploads/avatars` | 头像上传路径 |
| `FILE_UPLOAD_PATH` | `${user.dir}/uploads/files` | 文件上传路径 |

### 4. 构建项目

```bash
mvn clean package -DskipTests
```

构建产物位于 `target/MyPlatform-0.0.1-SNAPSHOT.jar`。

### 5. 运行项目

#### 方式一：使用 Maven（开发模式）

```bash
mvn spring-boot:run
```

#### 方式二：运行 JAR 包（生产模式）

```bash
java -jar target/MyPlatform-0.0.1-SNAPSHOT.jar
```

#### 方式三：指定环境配置

```bash
# 使用开发环境配置
java -jar target/MyPlatform-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 使用环境变量覆盖配置
DB_HOST=192.168.1.100 DB_PASSWORD=yourpassword java -jar target/MyPlatform-0.0.1-SNAPSHOT.jar
```

### 6. 验证服务

服务启动后，访问以下地址验证：

- API 基础路径：`http://localhost:8080`
- 健康检查：`http://localhost:8080/api/user/info`（需要登录）

## 项目结构

```
src/main/java/org/example/myplatform/
├── config/           # 配置类（全局异常处理、Redis、Web等）
├── controller/       # REST API 控制层
├── dto/              # 数据传输对象（请求/响应）
├── entity/           # 数据库实体类
├── event/            # 事件监听
├── exception/        # 自定义异常
├── interceptor/      # 拦截器（JWT、限流）
├── mapper/           # MyBatis Mapper 接口
├── service/          # 业务逻辑层
├── task/             # 定时任务
├── utils/            # 工具类（JWT、Redis、XSS）
├── vo/               # 视图对象
├── websocket/        # WebSocket 配置（实时消息）
├── xiaoxiaole/       # 消消乐游戏模块
└── MyPlatformApplication.java  # 启动类
```

## API 接口示例

### 用户注册

```bash
POST /api/auth/register
Content-Type: application/json

{
    "username": "testuser",
    "password": "password123",
    "email": "test@example.com"
}
```

### 用户登录

```bash
POST /api/auth/login
Content-Type: application/json

{
    "username": "testuser",
    "password": "password123"
}
```

### 创建群聊

```bash
POST /api/chat-group/create
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
    "name": "我的群聊",
    "description": "测试群聊",
    "avatar": "base64_image_data"
}
```

## 安全注意事项

1. **JWT 密钥**: 生产环境必须修改 `JWT_SECRET` 为至少32位的随机字符串
2. **数据库密码**: 生产环境使用强密码，并通过环境变量注入
3. **CORS**: 生产环境限制 `allowed-origins` 为具体域名
4. **文件上传**: 限制上传文件类型和大小，防止恶意文件上传
5. **日志**: 生产环境关闭调试日志，避免敏感信息泄露

## 许可证

MIT License