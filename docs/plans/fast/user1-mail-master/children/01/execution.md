# Child 01 执行记录（untracked；不进入实现提交）

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-01-access-boundary.md
Plan SHA-256: 7576aea83072891b8addf47d434f10c58e8534128b47bacbd9bc7b43949f03cc
Master plan: docs/plans/2026-10-27/user1-mail-master.md
Executor: Child01Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master
Target branch: fast/user1-mail-master
Pre-execution code SHA: 4d1698bb7c51120e98cfe8f6a412a4589fc9defd
Implementation boundary: 4d1698b..HEAD（10 个授权文件）

> 说明：`docs/plans/fast/user1-mail-master/children/01/brief.md` 为 0 字节空文件，无法作为完整合同读取；本执行以 master plan + child plan（上述 SHA-256 版本）为准，授权文件以 child plan 变更文件清单（10 行）为准。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 固定身份与默认拒绝（精确 user1；handler 级白名单） | IMPLEMENTED | MailReadOnlyPolicy.kt, AuthInterceptor.kt | AuthInterceptorTest 19 项：白名单=真实映射全集扫描出的 3 个 handler；POST 同方法拒绝；非白名单 403；匿名 401；admin/其他身份不回归 |
| I-2 grant 唯一表示与全部新字段 | IMPLEMENTED | V166 迁移, ExpertMailReadAccessService.kt | ExpertMailReadAccessIT 9 项：复合主键、重复 PUT 不刷新、并发 1 行、DELETE 幂等、撤销后新时间、FK 级联、其它表零写入 |
| I-3 管理接口只给 admin | IMPLEMENTED | ExpertMailReadAccessController.kt, ExpertMailReadAccessService.kt | IT：匿名 401、reader 403、其他 session 403 ADMIN_REQUIRED、body 伪造无效、0/负 id 400、不存在专家 404；service 直接调用同样拒绝 |
| I-4 元数据与错误稳定 | IMPLEMENTED | AuthController.kt, GlobalExceptionHandler.kt | AuthControllerMvcTest 13 项：admin/匿名 mailReadOnly=false，reader me=true，reader 登录仍拒绝，权限异常 403 固定 code，改密与 session 轮换回归 |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile` | PASS | exit 0（预检，无输出） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AuthInterceptorTest,AuthControllerMvcTest test` | PASS | BUILD SUCCESS；AuthInterceptorTest 19/0/0/0，AuthControllerMvcTest 13/0/0/0（run/failures/errors/skipped）；同生命周期 node 测试 1851 pass / 0 fail |
| `DB_URL='jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=ExpertMailReadAccessIT test` | PASS | BUILD SUCCESS；ExpertMailReadAccessIT 9/0/0/0（skipped=0，不是 skip 冒充 PASS）；Flyway 在隔离库应用 V166 success=1，`SHOW CREATE TABLE expert_mail_read_access` 与计划 schema 一致（含 `ON DELETE CASCADE`） |

数据库：仅使用 controller 指定的隔离 MySQL `127.0.0.1:3307 / talent_introduction_user1_it`（root/root）。brief 为空，DSN 由环境探测确认（唯一非系统库，Flyway 历史止于 V165）；测试通过既有 `DB_URL/DB_USERNAME/DB_PASSWORD` 环境变量注入，未在测试源码硬编码凭据。

## Changed Files (10)

- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`（新增：固定 user1 策略、handler 白名单、`MailReadAccessDeniedException` 与三个固定 code）
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt`（账号校验/首改密规则后增加 reader 拒绝；构造参数未变）
- `src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`（`MailReadAccessDeniedException` → 403 固定 code）
- `src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt`（login/me 尾部 `mailReadOnly`，admin-only 登录条件未改）
- `src/main/resources/db/migration/V166__create_expert_mail_read_access.sql`（新增表，编号执行前确认为 165 之后首个）
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`（唯一 grant 写者/读者，参数化 JDBC，事务 + 复合主键 upsert，FK 并发删除转 404）
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt`（管理 API + 同文件 DTO）
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`（真实 MVC 边界 + 全映射 HandlerMethod 白名单扫描）
- `src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt`（mailReadOnly、reader 登录拒绝、403 handler）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`（mysqlIt：真实迁移/FK/幂等/并发/撤销 + MVC 401/403/404）

## Deviations

1. **brief 为空**：`children/01/brief.md` 0 字节；合同取自 master plan 与 child plan（SHA-256 已记录）。未据此扩大范围。
2. **控制器实际类名**：计划文字写 `ConversationController.list/messages`；仓库真实映射类是 `MailboxConversationController`（`/api/mail/mailbox/conversations`）。白名单绑定真实 `declaringClass + method.name`，未使用 URL 前缀。
3. **DSN 来源**：brief 无内容；使用唯一隔离库（127.0.0.1:3307/talent_introduction_user1_it），通过既有环境变量注入，未改测试配置。
4. **未保留 MVC 405 断言**：`POST /api/mail/mailbox/conversations` 在无匹配 handler 时被既有 `GlobalExceptionHandler` 的通用 `Exception` 分支映射为 500（框架 `HttpRequestMethodNotSupportedException`），属本 child 之外既有行为；I-1 只要求“方法不支持可 405”，故改由真实 `HandlerMethod` 测试证明同一方法在 POST/PUT/DELETE 下不在白名单（`allowed handler method is rejected under a different HTTP method`）。
5. **执行事故（已复原）**：前几次 `write`/`edit` 因工具默认工作目录为主工作区，误将新文件与两处修改写入 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；已把产物移动到本 worktree，并在主工作区删除 4 个新增文件、对 `AuthInterceptor.kt` / `AuthInterceptorTest.kt` 执行 `git checkout --` 还原（还原前确认 diff 仅含本次改动，未丢失用户既有修改）。主工作区当前无任何 child 01 文件残留。

## Freshness

- Plan identity rechecked: YES（实现完成后重读 child plan，未变）
- Worktree identity rechecked: YES（分支 fast/user1-mail-master，HEAD 4d1698b 起步）
- Required commands run this invocation: YES（两条命令均在最终实现状态后重新执行）
- Historical evidence used only as baseline: YES（仅引用既有测试模式，不改其断言语义）

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → 交给主 agent / verify-p。
