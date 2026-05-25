# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

```bash
mvn spring-boot:run    # 启动应用（端口 8086）
mvn package            # 打包 JAR 到 target/vocabulary-1.0.0.jar
mvn clean              # 清理构建产物
```

数据库：MySQL `localhost:3306/vocabulary`，密码从环境变量 `DB_PASSWORD` 读取（默认 `123456`）。JWT 密钥从 `JWT_SECRET` 读取。

## 架构概览

Spring Boot 3.5.11 + MyBatis-Plus 3.5.15 + Spring Security + JWT（jjwt 0.12.6），Java 17。

**分层结构：**
- `controller/` — 5 个 REST 控制器：`AuthController`（`/api/auth`）、`WordController`（`/api/words`）、`QuizController`（`/api/quiz`）、`TranslateController`（`/api/translate`）、`AdminController`（`/api/admin`）
- `service/` — `UserServiceImpl` 和 `WordServiceImpl` 继承 MyBatis-Plus `ServiceImpl`；`QuizServiceImpl` 是纯实现（不继承 IService），直接注入 Mapper
- `mapper/` — 9 个 Mapper 接口继承 `BaseMapper`，其中 5 个有自定义 XML 映射文件在 `resources/mapper/`
- `entity/` — 9 个实体类，统一使用 Lombok `@Getter @Setter @Accessors(chain=true)`

**安全模型：**
- 无状态会话 + JWT Bearer Token（7 天过期），密码 BCrypt 编码
- 过滤器链：`RateLimitFilter` → `JwtAuthFilter` → Spring Security
- `/api/auth/**` 公开，其他路径需认证，`/api/admin/**` 额外检查 `role == "admin"`
- 限流：登录每分钟每 IP 10 次，注册每分钟每 IP 5 次（内存滑动窗口）
- `UserContext`（ThreadLocal）持有当前用户 ID 和角色

**统一响应格式：** `R.java` 包装为 `{code, msg, data}`。异常处理不完整——`GlobalExceptionHandler` 仅处理 `AccessDeniedException`，控制器内手动 try/catch 返回 `R.fail()`。

**外部 API 调用：** `WordServiceImpl` 通过 `java.net.http.HttpClient` 同步调用有道词典 API 和 MyMemory 翻译 API，无重试/熔断/缓存。

**数据库特点：** 大量使用 MySQL 专有的 `INSERT ... ON DUPLICATE KEY UPDATE` 做 upsert，不具备数据库可移植性。

**已知问题：**
- `application.yml` 第 14 行 SMTP 密码硬编码明文
- 前端是嵌入在 `resources/static/` 的 vanilla HTML/JS（`index.html` + `login.html`），非独立前端项目
- 无测试（pom.xml 无测试依赖，无 `src/test/`）
- 无 profile 配置（仅单环境 `application.yml`）
- 无 Redis/缓存层——验证码、限流计数、在线状态全在内存 ConcurrentHashMap
- CET-6 词库初始化脚本在 `sql/import_cet6.py`，数据源在 `sql/cet6_data.json`
