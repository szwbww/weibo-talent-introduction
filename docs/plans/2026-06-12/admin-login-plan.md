# 管理后台登录功能（admin 单用户 + 首登强制改密）— 开发计划

> 本计划交给执行 agent 实施。实施前请通读「现状分析」一节；行号基于 2026-06-12 代码，可能有少量漂移，请以符号名定位。
> 技术决策（已与需求方确认）：**不引入 Spring Security 全家桶**，只引入 `spring-security-crypto` 做 BCrypt 哈希；用 `HandlerInterceptor` + `HttpSession` 做登录态校验。

## 修正记录

以下条目由复验过程修正，覆盖原文中对应描述。后续验证以修正后版本为准。

| 原文位置 | 原始要求 | 修正结果 | 决策来源 |
|---|---|---|---|
| 3.2 | 迁移版本 `V24__create_admin_user.sql` | 改为 `V25`，V24 已被占用 | `fix/2026-06-13-admin-login-plan-verification-fix-plan.md` P0-1 |
| 四、测试 / 六、验收 | 迁移在 MySQL 与 H2 MySQL mode 下均可执行 | 取消 H2 要求，集成测试统一使用 Testcontainers MySQL | `fix/2026-06-13-admin-login-reverification-fix-plan.md` 三-A节 |
| 3.3.6 | interceptor exclude 只列 `login`、`me` | logout 同样不排除；匿名 logout 由 interceptor 返回 401，已登录强制改密状态由 interceptor 放行 | `fix/2026-06-14-admin-login-second-reverification-fix-plan.md` P1-2 / Task 2 |
| 3.5.3 | 前端 bootstrap 测试需完整 DOM sandbox | 降级为轻量级 init 函数网络调用模式扫描 + stub bootstrap 状态机测试 + 手动冒烟，不引入 jsdom/happy-dom | `fix/2026-06-14-admin-login-third-reverification-fix-plan.md` 四、修复策略 |

---

## 一、需求描述

1. 用户名 + 密码登录，**只有一个 `admin` 用户**，暂不做权限/角色体系。
2. 默认密码与用户名一致（即 `admin` / `admin`）。
3. **首次登录后必须先修改密码才能进入页面**（mustChangePassword 强制改密门禁；原话"提示更新代码"按"提示更新密码"理解，如有歧义先与需求方确认）。
4. 未登录访问任何业务 API 返回 401，前端展示登录页。
5. 提供退出登录。

不在本期范围（明确不做，留 TODO 即可）：多用户、角色权限、登录失败锁定/验证码、记住我、HTTPS 相关配置。

---

## 二、现状分析（务必先读）

- **无任何安全设施**：`pom.xml` 没有 security 相关依赖；`src/main/kotlin` 中没有任何 `WebMvcConfigurer`、Interceptor、Filter（grep 验证过）。
- **前端是单页静态 SPA**：`src/main/resources/static/{index.html,app.js,styles.css}`，由 `common/controller/FrontendController`（`GET /` → `forward:/index.html`）兜底。`index.html` 是 sidebar + 多个 `<section class="view" id="view-*">` 的结构（L13–L73 sidebar / nav-tabs）。
- **前端统一请求封装**：`app.js` 的 `async function api(path, options)`（约 L863）：所有业务请求都走它，`fetch` 带同源 cookie（默认 `same-origin`，无需改 credentials），非 2xx 时抛 `Error(data.message)`。**401/403 的全局处理钩子加在这里最省事**。注意：另有 4 处裸 `fetch(\`${contextPath}/api/task-progress/...\`)`（约 L234/L270/L409/L3974，task-watcher 轮询）没走 `api()`，需要单独处理 401（见 3.5.4）。
- **统一错误返回格式**：`common/controller/GlobalExceptionHandler` + `ApiErrorResponse(code, message, detail)`。拦截器直接写 response 时要保持同样的 JSON 形状。
- **持久层是 Spring Data JDBC**（不是 JPA）：domain 为不可变 Kotlin `data class` + `@Table`/`@Id`，repository 继承 `CrudRepository`，参考 `qa/repository/QaRuleRepository.kt`。
- **Flyway 迁移**：`src/main/resources/db/migration`，**当前最新为 V23**（`V23__create_mail_send_attempt_and_add_mail_record_error.sql`）。新迁移用 **V24**（实施时 `ls` 确认无漂移）。测试库是 H2 MySQL 模式（`src/test/resources/application.yml`），迁移 SQL 必须 H2/MySQL 双兼容（现有迁移都满足，照抄风格即可）。
- **现有控制器测试大量使用 `@WebMvcTest`**（如 `task/controller/TaskExecutionControllerMvcTest`）。`@WebMvcTest` 会加载 `WebMvcConfigurer` Bean ⇒ **新增的登录拦截器会让所有存量 MvcTest 直接 401 挂掉**。这是本计划最大的回归风险，必须用 3.4 的开关方案规避。

---

## 三、实施方案

新建模块 `com.weibo.talentintroduction.auth`，沿用 `controller / service / domain / repository` 分层，外加 `config`（拦截器注册放 `config/` 包，与现有 `config/` 风格一致也可，二选一，建议放 `auth/config`）。

### 3.1 依赖（pom.xml）

只加一个：

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

版本由 spring-boot-starter-parent (2.7.x) 的 BOM 管理，**不要写 version**。不要引入 `spring-boot-starter-security`。

### 3.2 数据库迁移 `V24__create_admin_user.sql`

```sql
CREATE TABLE admin_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    must_change_password TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    CONSTRAINT uk_admin_user_username UNIQUE (username)
);
```

**不要在迁移里 INSERT 种子用户**（BCrypt 哈希写死在 SQL 里既不可读也难轮换）。种子用户由 3.3.4 的启动引导创建，幂等。

### 3.3 后端

#### 3.3.1 domain：`auth/domain/AdminUser.kt`

```kotlin
@Table("admin_user")
data class AdminUser(
    @Id val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val mustChangePassword: Boolean = true,
    val lastLoginAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
```

#### 3.3.2 repository：`auth/repository/AdminUserRepository.kt`

`CrudRepository<AdminUser, Long>` + `fun findByUsername(username: String): AdminUser?`。

#### 3.3.3 service：`auth/service/AuthService.kt`

注入 `AdminUserRepository` 和 `BCryptPasswordEncoder`（在 `auth/config/AuthConfig.kt` 里声明 `@Bean fun passwordEncoder() = BCryptPasswordEncoder()`）。

- `login(username, rawPassword): AdminUser`：查用户、`encoder.matches` 校验；失败统一抛 `IllegalArgumentException("用户名或密码错误")`（不区分"用户不存在/密码错"，GlobalExceptionHandler 已映射 400；登录失败用 400 可接受，无需新增 401 分支——401 语义留给"未登录访问 API"）。成功则更新 `lastLoginAt` 并返回。
- `changePassword(username, oldPassword, newPassword)`：
  - 校验 oldPassword 匹配，否则 `IllegalArgumentException("原密码错误")`；
  - 新密码规则：长度 ≥ 8、不等于用户名、不等于旧密码，不满足抛 `IllegalArgumentException`（带中文提示）；
  - 通过后 `copy(passwordHash = encoder.encode(new), mustChangePassword = false, updatedAt = now)` 保存。
- 所有写操作 `@Transactional`。

#### 3.3.4 启动引导：`auth/service/AdminUserBootstrap.kt`

`ApplicationRunner`（或 `@EventListener(ApplicationReadyEvent)`）：`findByUsername("admin") == null` 时插入 `AdminUser(username = "admin", passwordHash = encoder.encode("admin"), mustChangePassword = true, ...)` 并打 info 日志。幂等：已存在则跳过，**绝不重置已有密码**。

#### 3.3.5 controller：`auth/controller/AuthController.kt`（`/api/auth`）

会话约定：登录成功后 `session.setAttribute(AuthSessionKeys.USERNAME, user.username)`，常量定义在 `auth/config/AuthSessionKeys.kt`（`const val USERNAME = "AUTH_USERNAME"`）。

- `POST /api/auth/login`，body `{username, password}`：调 `authService.login`；成功后 **先 `request.changeSessionId()` 防会话固定**，再写 session 属性；返回 `{username, mustChangePassword}`。
- `POST /api/auth/logout`：`session.invalidate()`（容忍无 session），返回 204。
- `GET /api/auth/me`：有 session 属性 → 实时查库返回 `{authenticated: true, username, mustChangePassword}`（mustChangePassword **以 DB 为准**，不要缓存进 session，避免改密后状态不同步）；无 session → 返回 200 `{authenticated: false}`（**不要返回 401**，前端用它做首屏探测）。
- `POST /api/auth/change-password`，body `{oldPassword, newPassword}`：从 session 取 username（取不到抛 401 由拦截器兜底，见下），调 `authService.changePassword`，返回 204。改密成功后**不强制重新登录**，session 继续有效。

#### 3.3.6 拦截器：`auth/config/AuthInterceptor.kt` + `auth/config/AuthWebConfig.kt`

`AuthInterceptor : HandlerInterceptor`，`preHandle` 逻辑：

1. 放行 `OPTIONS`。
2. session 无 `AUTH_USERNAME` → 写 401 JSON 后返回 false：
   `{"code":"UNAUTHORIZED","message":"未登录","detail":null}`（与 `ApiErrorResponse` 字段一致；用注入的 `ObjectMapper` 序列化，`contentType = application/json;charset=UTF-8`）。
3. 已登录但 DB 中 `mustChangePassword == true` 且请求路径**不是** `/api/auth/change-password`、`/api/auth/logout`、`/api/auth/me` → 写 403 JSON `{"code":"PASSWORD_CHANGE_REQUIRED","message":"首次登录请先修改密码"}` 返回 false。
   - 实现提示：每次请求查一遍 admin_user 表代价可忽略（单用户低频后台）；不要把标志缓存进 session。
4. 其余放行。

`AuthWebConfig : WebMvcConfigurer`（`@Configuration`）：

```kotlin
registry.addInterceptor(authInterceptor)
    .addPathPatterns("/api/**")
    .excludePathPatterns("/api/auth/login", "/api/auth/me")
```

- 静态资源（`/`、`/index.html`、`app.js` 等）**不拦截**：页面本身无敏感数据，所有数据都在 `/api/**` 后面；前端用 `/api/auth/me` 自行决定渲染登录页还是主界面。这样最简单，且 `FrontendController` 不用动。
- **测试开关（关键）**：`AuthWebConfig` 加 `@ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)`。在 `src/test/resources/application.yml` 中加 `talent-introduction.auth.enabled: false` ⇒ 存量 `@WebMvcTest`/`@SpringBootTest` 全部不受影响。auth 自身的拦截器测试用 `@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])` 打开。`src/main/resources/application.yml` 加同名配置项默认 `true`（带 env 覆盖写法，照抄现有风格，如 `${AUTH_ENABLED:true}`）。

#### 3.3.7 会话配置

`application.yml` 增加：

```yaml
server:
  servlet:
    session:
      timeout: 8h
      cookie:
        http-only: true
```

### 3.4 对存量代码的影响清单

- 存量 `@WebMvcTest` / `@SpringBootTest`：靠 3.3.6 的开关保持绿色，**不需要逐个改测试**。实施后必须全量跑 `mvn test` 验证。
- `GlobalExceptionHandler`：不需要改（登录失败复用 IllegalArgumentException→400）。
- `FrontendController`：不需要改。
- RabbitMQ 消费者、调度器等非 HTTP 入口：拦截器只挂在 MVC 上，无影响。

### 3.5 前端（static/index.html + app.js + styles.css）

#### 3.5.1 index.html

在 `<body>` 顶部（`.layout` 外）加两个全屏遮罩：

- `#loginOverlay`：居中卡片，标题"人才引进管理后台"，表单 `#loginForm`（用户名、密码、登录按钮、错误提示 `#loginError`）。
- `#changePasswordOverlay`：表单 `#changePasswordForm`（原密码、新密码、确认新密码、提交按钮、错误提示），副标题"首次登录请先修改密码"。

sidebar 底部加"退出登录"按钮 `#logoutBtn`（显示当前用户名）。

#### 3.5.2 styles.css

`.auth-overlay`（fixed 全屏、半透明底/纯色底 + 居中卡片），复用现有表单控件样式。默认 `hidden` 属性控制显隐，与现有代码风格一致。

#### 3.5.3 app.js 启动流程改造

现有初始化（页面底部的 init/事件绑定）改为：

1. 新增 `async function checkAuth()`：调 `GET /api/auth/me`。
   - `authenticated:false` → 显示 `#loginOverlay`，**不加载任何业务数据**；
   - `mustChangePassword:true` → 显示 `#changePasswordOverlay`；
   - 否则 → 隐藏遮罩，执行原有初始化（加载默认视图数据）。
2. `#loginForm` submit → `POST /api/auth/login`；成功后按返回的 `mustChangePassword` 决定进改密遮罩还是进主界面（进主界面时再触发原有初始化）。失败把 message 显示在 `#loginError`。
3. `#changePasswordForm` submit → 前端校验两次新密码一致 → `POST /api/auth/change-password`；成功后 `showStatus("密码修改成功")`、隐藏遮罩、执行主界面初始化。
4. `#logoutBtn` → `POST /api/auth/logout` → `location.reload()`。

#### 3.5.4 全局 401/403 处理

- `api()`（约 L863）中：`response.status === 401` → 显示登录遮罩并抛错（中断当前流程）；`403` 且 `data.code === "PASSWORD_CHANGE_REQUIRED"` → 显示改密遮罩并抛错。
- 4 处裸 `fetch` 的 task-progress 轮询（约 L234/L270/L409/L3974）：收到 401 时停止轮询并显示登录遮罩（最小改动：抽一个 `handleUnauthorized(response)` 帮助函数，四处调用；或顺手把它们改走 `api()`——若改造成本高就只加 401 检查）。
- 会话过期场景：用户操作任意按钮 → api() 收到 401 → 弹登录遮罩；重新登录成功后不强制刷新页面，但**建议简单处理：登录成功后 `location.reload()`**，避免恢复半截状态的复杂度（首屏登录除外，首屏直接走初始化即可；统一 reload 也可接受，实施者二选一并保持一致）。

---

## 四、测试要求

新增（包路径 `src/test/kotlin/com/weibo/talentintroduction/auth/...`）：

1. **`AuthServiceTest`**（纯单测，mock repository）：
   - 登录成功 / 用户不存在 / 密码错误（后两者同样的异常 message）；
   - 改密成功后 `mustChangePassword=false` 且新哈希可 matches；
   - 改密校验分支：原密码错、新密码 <8 位、新密码=用户名、新密码=旧密码。
2. **`AuthControllerMvcTest`**（`@WebMvcTest(AuthController::class)` + `@MockBean AuthService`）：
   - login 成功返回 `{username, mustChangePassword}` 且 session 中有属性（`MockHttpSession` 断言）、sessionId 已变更；
   - login 失败 → 400 + ApiErrorResponse 形状；
   - me：带/不带 session 两种返回；logout 后 session 失效。
3. **`AuthInterceptorTest`**（`@SpringBootTest` + `MockMvc`，`@TestPropertySource` 打开 `talent-introduction.auth.enabled=true`）：
   - 无 session 访问任意业务 API（如 `GET /api/qa/...` 或随便一个存在的端点）→ 401 + `code=UNAUTHORIZED`；
   - 有 session 但 `mustChangePassword=true` → 业务 API 403 `PASSWORD_CHANGE_REQUIRED`，而 `/api/auth/change-password` 放行；
   - 改密后再访问业务 API → 200。
4. **`AdminUserBootstrapTest`**（`@SpringBootTest`）：启动后 admin 存在、密码 matches("admin")、mustChangePassword=true；重复执行 run 不重置已改密码（先手动改库再 run 一次断言哈希未变）。
5. **存量回归**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿（重点确认所有既有 `*MvcTest` 未被拦截器波及）。

---

## 五、实施顺序

1. pom 加 `spring-security-crypto`；`V24__create_admin_user.sql`。
2. domain / repository / AuthConfig(encoder bean) / AuthService / AdminUserBootstrap + 单测。
3. AuthController + MvcTest。
4. AuthInterceptor + AuthWebConfig + 开关配置（main/test 两个 application.yml）+ InterceptorTest。
5. 前端：index.html 遮罩、styles.css、app.js（checkAuth、表单提交、401/403 全局处理、logout）。
6. 全量 `mvn test`；手动冒烟：启动后 admin/admin 登录 → 被强制改密 → 改密后进入主界面 → 退出 → 旧密码登录失败 → 新密码登录直接进主界面。

---

## 六、验收标准

- [ ] 未登录直接打开页面：只能看到登录页，所有 `/api/**`（除 login/me）返回 401。
- [ ] `admin`/`admin` 首次登录后被强制进入改密页，未改密前任何业务 API 返回 403 `PASSWORD_CHANGE_REQUIRED`。
- [ ] 改密规则生效（≥8 位、≠用户名、≠旧密码、原密码须正确），错误提示为中文且显示在表单内。
- [ ] 改密成功后立即进入主界面，功能与改造前完全一致；重启服务后密码不被 Bootstrap 重置。
- [ ] 退出登录后回到登录页；会话过期后任意操作弹出登录页。
- [ ] `mvn test` 全绿；新增迁移 V24 在 MySQL 与 H2（测试）下均可执行。
- [ ] 密码仅以 BCrypt 哈希落库，日志中不出现明文密码。
