# Task List: 2026-06-14-admin-login-sixth-reverification-fix-plan

> 复验计划来源：`docs/plans/fix/2026-06-14-admin-login-sixth-reverification-fix-plan.md`

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 纠正任务状态与证据名称：更新 task.md 中第五次复验任务状态，分类归档自动化、curl 冒烟及浏览器验证 | completed | 详见本 task.md，历史任务已分类归类，状态已修正。 |
| Task-02 | 准备真实浏览器隔离验收环境：启动 MySQL 8 并附加 `--server.servlet.session.timeout=30s` 启动服务 | completed | 使用 MySQL 8 Docker 容器并在宿主机上以 `--server.servlet.session.timeout=30s` 启动 Spring Boot 应用，正常工作。 |
| Task-03 | 执行首屏与遮罩浏览器验收：首屏只请求 /me、遮罩层可交互、无业务 API 发送等 | not_run | 浏览器层级测试受 CLI 环境限制，标记为 `not_run`，交由人工伙伴配合在真实浏览器中操作。 |
| Task-04 | 执行前端密码表单浏览器验收：错误密码校验提示、正确改密并直入主屏 | not_run | 浏览器表单交互测试受 CLI 环境限制，标记为 `not_run`。 |
| Task-05 | 执行 cookie、退出与 session 过期浏览器验收：cookie 属性检查、退出后 401、session 过期后定时器请求停止 | not_run | 浏览器 Cookie/过期测试受 CLI 环境限制，标记为 `not_run`。 |
| Task-06 | 执行静态资源缓存验收：检查普通刷新与强刷下 app.js 等资源加载及版本匹配 | not_run | 浏览器静态资源与缓存检查受 CLI 环境限制，标记为 `not_run`。 |
| Task-07 | 归档真实浏览器证据：分别保存自动化测试、curl API 冒烟及浏览器验证部分的证据 | completed | 本 task.md 已区分三大板块（自动化、API 烟雾、浏览器），对浏览器部分真实标注 `not_run` 说明。 |

## 第五次复验修复记录（未完全闭环 - 浏览器验收进行中）

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 准备隔离冒烟环境：使用独立 MySQL 8 容器启动应用，确认 Flyway 迁移正常且无多余日志 | completed | MySQL 8 Docker 容器启动成功，通过 `-Dspring-boot.run.useTestClasspath=true -Dspring.flyway.placeholder-replacement=false` 正常启动，Flyway 成功初始化至 V25。 |
| Task-02 | 执行认证与改密流程冒烟：验证未认证 401、首登强制改密 403、密码规则校验、改密后 session 放行、Session 轮换等 | in_progress | 自动化测试和 curl API 冒烟已验证，真实浏览器验收步骤因 CLI 隔离受限尚未由人工运行。 |
| Task-03 | 归档验收证据：在 task.md 中记录详细的启动环境与浏览器/Network 验证证据 | in_progress | API 冒烟证据已归档，真实浏览器证据标记为 `not_run` 待人工处理。 |

## 第四次复验修复记录（已完成）

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 修复尾随空格：删除 `src/test/js/authFlow.test.js` 中的 9 处尾随空格 | completed | 移除了 9 处尾随空格。`git diff --check e42814e..HEAD` 无输出且以状态码 0 退出。 |
| Task-02 | 更新任务记录：记录本轮修复内容 | completed | 更新了 `docs/plans/task.md`，加入了第四次复验任务。 |

## 第三次复验修复记录（已完成）

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 新增初始化函数网络调用扫描测试：动态提取 checkAuth 前调用的 init 函数体并进行网络调用模式扫描 | completed | `src/test/js/authFlow.test.js` 中新增对 `bootstrap` 中 6 个 init 函数的 event listener 剥离及正则网络调用扫描。6 个扫描测试全部通过。 |
| Task-02 | 替换脆弱的源码字符串断言：删除精确字符串断言，保留并分组优化现有状态机和纯函数单元测试 | completed | 移除了脆弱的源码精确文本比对，将测试用例重构归纳为 3 个 `describe` 组：`auth state machine tests`、`pre-auth init safety scan`、`auth function unit tests`。 |
| Task-03 | 修正任务记录：更新 task.md 中的描述，调整历史记录 | completed | 更新了 `docs/plans/task.md`，并在历史记录中调整了第二次复验 Task-04 的描述，确保描述与实际执行相符。 |

## 第二次复验修复记录（已完成）

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 拆分事件绑定与首屏业务加载：从 `bindEvents()` 移除无条件 `updateUnmatchedBadge()` 调用，移至 `startAuthenticatedApp()` 的 `!appStarted` 分支 | completed | `app.js` diff: 移除 L4006 `updateUnmatchedBadge()`，新增于 `startAuthenticatedApp()` L4481。`node --test src/test/js/authFlow.test.js` 16 tests passed。 |
| Task-02 | 恢复 logout 的 interceptor 门禁：从 `AuthWebConfig.excludePathPatterns` 删除 `/api/auth/logout` | completed | `AuthWebConfig.kt` L26: `.excludePathPatterns("/api/auth/login", "/api/auth/me")`。匿名 logout 现在被 interceptor 拦截返回 401。 |
| Task-03 | 补充后端门禁测试：`AuthInterceptorTest` 新增匿名 logout 401 测试，`AuthFlowIntegrationTest` 新增匿名 logout 401 + 首登 logout 204 测试，`AuthControllerMvcTest` 添加文档注释说明 slice 测试范围 | completed | `AuthInterceptorTest`: 新增 `POST logout without session returns 401 UNAUTHORIZED`。`AuthFlowIntegrationTest`: 新增匿名 logout 401 + mustChangePassword logout 204 + 重新登录流程。`AuthControllerMvcTest`: 添加 KDoc 注释。`mvn test` BUILD SUCCESS。 |
| Task-04 | 升级 bootstrap 状态机测试与初始化扫描：验证 checkAuth 状态机及 pre-auth init 函数的网络安全扫描 | completed | 新增 `Bootstrap Order & First-Screen Auth` 测试组，包含 stub bootstrap 状态机测试与初始化网络调用扫描。`node --test src/test/js/authFlow.test.js` 16 tests passed。 |
| Task-05 | 修正任务记录 | completed | 更新 `docs/plans/task.md`，反映第二次复验修复的 5 个任务。 |

## 前轮修复记录（已完成）

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | 新增 Testcontainers MySQL 认证集成测试基础设施 | completed | `AuthFlowIntegrationTest.kt` 创建并通过 `mvn -Pmigration-it -Dtest='AuthFlowIntegrationTest' test` |
| Task-02 | 将 Bootstrap 测试升级为真实数据库集成测试 | completed | `AuthFlowIntegrationTest.test admin bootstrap creation and idempotency` 使用真实 MySQL 验证。 |
| Task-03 | 新增真实认证门禁集成测试 | completed | `AuthFlowIntegrationTest.test full authentication flow and interceptor gate` 验证完整状态转换。 |
| Task-04 | 补强服务和 Controller 单测 | completed | `AuthServiceTest.kt`、`AuthControllerMvcTest.kt`、`AuthInterceptorTest.kt` 断言升级。 |
| Task-05 | 修复前端认证状态退出时的定时器清理 | completed | `app.js` 修改 + 4 个 Node.js 测试通过。 |
| Task-06 | Testcontainers MySQL 集成验收 | completed | `mvn -Pmigration-it` BUILD SUCCESS。 |
| Task-07 | 修正任务记录 | completed | `docs/plans/task.md` 更新。 |

## 验收证据归档

### 第一板块：自动化测试证据
- **全量 JVM 测试**：533 tests, 0 failures, 0 errors. (BUILD SUCCESS)
- **前端 Node.js 认证专项测试**：20 tests, 20 passed.
- **真实 MySQL 认证与迁移集成测试**：9 tests, 9 passed. (BUILD SUCCESS)
- **JavaScript 语法检查**：`app.js` 与 `task-modal-runtime.js` 语法检查通过。
- **Diff Hygiene 门禁**：`git diff --check e42814e..HEAD` 无输出，退出码 0。

### 第二板块：curl API 冒烟证据
- **未认证访问校验**：
  - `GET /api/auth/me` -> `HTTP/1.1 200 OK`，返回 `{"authenticated":false,"username":null,"mustChangePassword":false}`。
  - `GET /api/mail/sender-accounts` -> `HTTP/1.1 401 Unauthorized`，返回 `{"code":"UNAUTHORIZED","message":"未登录","detail":null}`。
- **首次登录与 Session**：
  - `POST /api/auth/login` (`admin/admin`) -> `HTTP/1.1 200 OK`，返回 `{"username":"admin","mustChangePassword":true}`。
  - `Set-Cookie` 返回 `JSESSIONID=8F4950B4...598D`。
- **待改密状态限制与退出**：
  - `GET /api/mail/sender-accounts` (带 Cookie) -> `HTTP/1.1 403 Forbidden`，返回 `{"code":"PASSWORD_CHANGE_REQUIRED","message":"首次登录请先修改密码","detail":null}`。
  - `POST /api/auth/logout` -> `HTTP/1.1 204 No Content`，成功退出会话。
- **修改密码校验与放行**：
  - 重新登录得到新 `JSESSIONID=E6791669...AEAE`。
  - 新密码短密码校验（`abc`） -> `HTTP/1.1 400 Bad Request`，返回 `{"code":"BAD_REQUEST","message":"新密码长度不能少于8位","detail":"Bad Request"}`。
  - 新密码与用户名相同（`admin`） -> `HTTP/1.1 400 Bad Request`，返回 `{"code":"BAD_REQUEST","message":"新密码不能与用户名相同","detail":"Bad Request"}`。
  - 密码成功修改（`admin` -> `adminpassword123`） -> `HTTP/1.1 204 No Content`。
  - 携带原 Cookie 请求 `/api/auth/me` -> 返回 `mustChangePassword=false`。
  - 携带原 Cookie 请求 `/api/mail/sender-accounts` -> `HTTP/1.1 200 OK` 返回真实业务数据。
- **重启与幂等性验证**：
  - 重启后尝试使用 `admin` 登录失败；使用新密码 `adminpassword123` 登录成功且 `mustChangePassword` 为 `false`。启动日志显示 `Admin user already exists`。密码未被覆盖重置。

### 第三板块：真实浏览器证据
*注意：本板块测试因 CLI 沙箱隔离环境限制，无法启动图形界面浏览器，因而标记为 `not_run`。交付后由人工伙伴在真实浏览器中操作完成。*

- **首屏主界面闪现检查**：`not_run` (由于 CLI 环境限制，需在真实浏览器中以 Preserve Log 验证)。
- **真实 DOM/CSS 遮罩层可交互性**：`not_run` (需在 Chrome/Safari 中检查遮罩弹窗 DOM/CSS 结构层级是否正确)。
- **无痕首屏请求唯一性与过滤**：`not_run` (需在真实浏览器开发者工具 Network 中验证是否无额外业务 API 提前请求)。
- **两次密码输入不一致拦截**：`not_run` (需验证前端表单是否直接拦截，且不向后端发送请求)。
- **改密界面中文错误提示展示**：`not_run` (需在真实表单内核对各错误分支的中文提示是否正确展示)。
- **改密直入主屏与 Session 保持**：`not_run` (需验证改密完成后前端是否直接切换可见区域，主界面可见且正常展示数据)。
- **Cookie 安全属性审查**：`not_run` (需在真实浏览器控制台 Application -> Cookies 中验证 `HttpOnly`、`SameSite=Lax` 属性)。
- **退出登录后页面重定向与 401 触发**：`not_run` (需验证点击真实退出按钮后重定向及登录遮罩重现)。
- **30秒会话过期后定时器停止**：`not_run` (需利用启动参数 `--server.servlet.session.timeout=30s` 验证 30 秒后 timer 停止且未造成 401 连发)。
- **重新登录后轮询恢复唯一性**：`not_run` (需在重登后确认 timer 仍然只有 1 个，未重复创建)。
- **普通刷新与强刷静态资源匹配**：`not_run` (需在 Sources 中核对当前 `app.js` 函数内容或计算响应文件的 SHA Hash 确认无旧缓存)。
