# Child 05 执行记录（execution evidence，非独立验证）

## Execution Result: READY_FOR_VERIFICATION

- Plan（05，含 A5/A6 修正）：`docs/plans/2026-10-27/user1-mail-05-account-activation.md`
  - SHA-256：`f73efb9c8ccc311bd814e72c00c13df4db8146a3db6c77376fab0e93f5b416c3`（执行前后未变）
- Master plan：`docs/plans/2026-10-27/user1-mail-master.md`
  - SHA-256：`b4b07118053d570c3987f62602aff80df554a7f6700de8e0ede74a591bfae69a`（未变）
- Brief（完整契约）：`docs/plans/fast/user1-mail-master/children/05/brief.md`
- Execution epoch：NEW
- Executor：Child05Implementer（fast-p child 05，独占 worktree）
- Target worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Target branch：`fast/user1-mail-master`
- Pre-execution code SHA（= child 04 code head / 本次 base）：`419f49ad6182dd1f62a1445c0f3cc582c21e1d5b`
- 执行起点 HEAD（计划文档提交）：`258f435c8faefb15c187c6cd34669ac0ad92a717`
- Post-execution code SHA（实现提交）：`2553a1085cc8196923b1ff65fd274939eb744fa3`（`feat(fast-p): implement 05`，5 个授权文件，未包含 `docs/plans/fast/**`）
- Evidence HEAD：本文件未被提交（untracked），由 controller 另行提交；Evidence HEAD = N/A

## Task Status

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| I-1 首启建 `user1`，BCrypt 现场编码、`mustChangePassword=false`、时间初始化、`lastLoginAt=null`、已有行绝不重置、唯一键并发重查、其它异常重抛、不打印密码/hash | IMPLEMENTED | `auth/service/ReadOnlyUserBootstrap.kt`、`auth/service/ReadOnlyUserBootstrapTest.kt` | 单测 6/6 通过（真实 BCrypt）；AuthFlow 真实 MySQL 断言账号集合、hash 可校验且 != 明文、lastLoginAt 初始 null、重启不改 hash/时间 |
| I-2 `AuthController.login` 仅放行 trim 后 `admin`/`user1`，密码仍走 `AuthService`/BCrypt，session 轮换，DB canonical username，`mailReadOnly` 服务端派生，错误密码不建已认证 session | IMPLEMENTED | `auth/controller/AuthController.kt`、`AuthControllerMvcTest.kt`、`AuthFlowIntegrationTest.kt` | MvcTest 14/14；AuthFlow：真实 user1 登录/me/logout、session id 轮换、错误密码与 `op1`/`User1`/`user2`/` admin` 拒绝 |
| I-3 建号不写授权表；无授权=空列表/详情 403；授权 A 只读 A；B 403；reader 无改密/发送/附件/跟进/挂起；admin 原能力不变 | IMPLEMENTED | `AuthFlowIntegrationTest.kt` | 真实 MySQL：grant 行数 0 → admin 授予 1 → 列表仅 A、A 正文可读且附件字段恒空、B 403 → 撤销后列表 0、A 403；8 类写/附件/改密请求全部 403 `READ_ONLY_FORBIDDEN`，`mail_record` 与 reader hash 未变；admin 仍可登录并看到 2 条会话 |
| I-4/A5 真实登录门禁（原命令 + 接线命令分别记录） | IMPLEMENTED | `AuthFlowIntegrationTest.kt` | 原命令：exit 1（8 errors，全部 context load，预存缺陷）；接线命令：exit 0，`Tests run: 8, Failures: 0, Errors: 0`（真实 Testcontainers MySQL 8.0.36） |
| A6 替换 child-01 失效断言：user1 登录被接受 + 非白名单仍拒绝且不触碰 AuthService | IMPLEMENTED | `AuthControllerMvcTest.kt` | `reader login is accepted when AuthService validates it`、`non allowlisted usernames are still rejected without touching AuthService`；其余断言未改，14/14 通过 |

## Commands（本次 invocation 重新执行）

| # | 命令 | 结果 | 证据 |
|---|---|---|---|
| 1 | `mvn -B -Dtest=ReadOnlyUserBootstrapTest,AdminUserBootstrapTest,AuthControllerMvcTest,AuthInterceptorTest test` | PASS | exit 0；`Tests run: 43, Failures: 0, Errors: 0, Skipped: 0`（ReadOnlyUserBootstrap 6、AdminUserBootstrap 4、AuthControllerMvcTest 14、AuthInterceptorTest 19） |
| 2 | `mvn -B test`（全量） | FAIL（预存 19 errors，与基线同） | exit 1；`Tests run: 5672, Failures: 0, Errors: 19, Skipped: 24`；19 个错误全部为 `ExpertContactLocationServiceTest.<init>`（country CL `America/Coyhaique` 时区，基线同源）；`AuthFlowIntegrationTest` 在本 profile 下整体 skipped（`Tests run: 1, Skipped: 1`） |
| 3 | `node --test src/test/js/*.test.js`（surefire 失败后 Maven 不执行 JS exec 插件，故单跑） | PASS | exit 0；`tests 1911 / pass 1911 / fail 0 / skipped 0`（与基线一致） |
| 4 | `mvn -B -DskipTests package` | PASS | exit 0；`target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`（52,060,382 bytes） |
| 5 | 原 AuthFlow 命令：`DOCKER_API_VERSION=1.40 mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest test` | FAIL（预存，未修复；可复现） | exit 1；`Tests run: 8, Failures: 0, Errors: 8, Skipped: 0`；全部 8 例的根因同为 `BeanDefinitionOverrideException: ... 'attachmentTransferWorker' ... WorkerLifecycleTestConfig`（测试源顶层 `@Configuration` 被组件扫描进完整上下文）。测试内不再内联 `allow-bean-definition-overriding`，故该预存失败在原命令下必然显现 |
| 6 | 接线 AuthFlow：`DOCKER_API_VERSION=1.40 mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest -Dspring.main.allow-bean-definition-overriding=true test` | PASS | exit 0；`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`，真实 Testcontainers MySQL 8.0.36（`mysql:8.0.36` 容器真实启动），真实登录/授权/撤销/403 全部执行。覆盖开关仅由命令行 `-D` 提供，测试源码无内联项 |
| 7 | 隔离 MySQL 组：`DB_URL=...127.0.0.1:3307/talent_introduction_user1_it... mvn -B -Pmysql-it -Dtest=ExpertMailReadAccessIT,MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest test` | FAIL（预存 7 errors，与基线同） | exit 1；`Tests run: 127, Failures: 0, Errors: 7, Skipped: 0`；错误集合与基线逐条一致：`MailboxConversationRepositoryIT.replied filter ... :242` DataIntegrityViolation（`expert_replied_dismissal.dismissed_at`）+ `CalendarAttachmentIntegrationTest` 6 个 IllegalState（context）。无新增错误 |
| 8 | `git diff --check` | PASS | exit 0（无输出） |

未指向其它数据库；无 skip 冒充通过；未用静态预览替代真实服务。

## Changed Files（提交 2553a10）

- `src/main/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrap.kt`（新增）— 与 `AdminUserBootstrap` 同条件启动器；只 insert 固定 `user1`。
- `src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt` — 登录白名单 `admin`/`user1`（`MailReadOnlyPolicy` 常量，trim 后精确匹配），其余逻辑未动。
- `src/test/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrapTest.kt`（新增）— 初建（真实 BCrypt、hash != 明文、mustChange=false、时间/lastLoginAt）、重复不重置、并发唯一键重查成功、无行时重抛、`auth.enabled=false` 不建号（`ApplicationContextRunner` + `@ConditionalOnProperty`）。
- `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` — 保留原两例（含 admin-only 场景的“只有 1 个用户”断言）；新增 6 例真实 MySQL 门禁（双账号集合、重启不重置+grant 不变、user1 真实登录/me/logout 与错误凭据、无授权空列表+详情 403、admin 授权/撤销闭环、reader 无发送/附件/跟进/挂起/改密能力）；A5 测试上下文接线。
- `src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt` — A6：删除失效的“user1 登录仍拒绝”，改为“AuthService 通过则接受（mailReadOnly=true）”+“非白名单用户名仍拒绝且不触碰 AuthService”；其余断言原样保留。

授权范围外未改动：无迁移/配置/产品 schema/ES/其它测试改动；`docs/plans/fast/**`（含 `ledger.md` 与本文件）未进实现提交。

## Deviations

### D-1（A5 配方实测不足，同一授权意图内最小扩展，仅测试上下文）

A5 原文允许：“bean-definition overriding + 一个 `@Primary NamedParameterJdbcTemplate` 测试 bean”。实测该最小配方仍无法加载上下文（未删除任何失败，逐步记录如下）：

1. 仅 `allow-bean-definition-overriding=true` + 一个 `@Primary NamedParameterJdbcTemplate`：
   `Tests run: 8, Errors: 8`，根因
   `NoUniqueBeanDefinitionException: No qualifying bean of type 'javax.sql.DataSource' available: expected single matching bean but found 2: mailboxConversationDataSource,expertMailReadAccessDataSource`
   （触发点 `expertMailReadAccessFlyway` 参数 0）。
2. 追加 `@Primary DataSource`、`@Primary JdbcTemplate` 后：上下文可建，但启动期失败
   `No bean named 'transactionManager' available: No matching TransactionManager bean found for qualifier 'transactionManager'`
   （Spring Data JDBC 仓库事务代理按固定名字 `transactionManager` 解析；两个泄漏的 `*RealJdbcConfig` 的 `@ConditionalOnMissingBean(PlatformTransactionManager)` 使 Boot 的同名 bean 退避）。
3. 再追加同名 `@Primary PlatformTransactionManager transactionManager` 后：上下文另因预存 JDK 时区缺陷失败
   `IllegalStateException: 国家时区目录配置错误：国家 CL 的时区 id 无法解析：America/Coyhaique`
   （`ExpertContactLocationCatalog` 构造期校验；与全量基线 19 个 `ExpertContactLocationServiceTest.<init>` 错误同源，本环境 JDK 11 tzdb 无该时区）。
4. 追加 `@MockBean ExpertContactLocationCatalog`（仅隔离该无关产品 bean，不改产品代码、不改该缺陷）后：上下文正常，8/8 通过。

最终 `AuthFlowIntegrationTest.AuthFlowTestJdbcConfig`（嵌套 `@TestConfiguration`，仅本测试上下文可见）提供 4 个指向**同一容器库** `spring.datasource.*` 的 bean：`authFlowDataSource`、`authFlowJdbcTemplate`、`authFlowNamedParameterJdbcTemplate`、`transactionManager`（均 `@Primary`），外加 `@MockBean ExpertContactLocationCatalog`。全部为测试隔离设施：不注册产品 bean、不改产品代码、不被其它测试复用。

覆盖开关（`spring.main.allow-bean-definition-overriding=true`）**不内联在 `@TestPropertySource`**，只由接线命令的 `-D` 提供；内联会使原命令同样能建上下文，从而掩盖上述预存失败（复验 F-1 已按此修正，见 fix-log Round 1）。

请人工/验证者确认：该扩展是否接受为 A5 的等价实现；若不接受，则需要新的授权口径（或允许修改那两个测试源顶层 `@Configuration` 的扫描泄漏本身——本步无权改）。

### D-2（A5 记录口径）

原 AuthFlow 命令的失败已如实记录（exit 1、8 errors、`attachmentTransferWorker` 覆盖冲突），未以接线运行替代；两者在 Commands 表中分列 #5/#6。

### D-3（全量用例数）

`mvn test` 本次为 5672（基线 5645），0 failures，19 errors（同一预存类/位置），24 skipped。新增用例来自本步 6 个 bootstrap 单测、AuthControllerMvcTest 净 +1、以及 AuthFlow 新增 6 例（该 profile 下整体跳过，计作 1 skipped）。未对基线计数口径做额外改写。

## Freshness

- Plan identity rechecked：YES（两个计划文件 hash 执行前后未变，`git status` 显示两文件无改动）
- Worktree identity rechecked：YES（所有命令均以该 worktree 为 cwd；提交后 `HEAD = 2553a10` 位于 `fast/user1-mail-master`）
- Reported commits reachable from target branch：YES
- Required commands run this invocation：YES（8 项全部本次重新执行）
- Historical evidence used only as baseline：YES（19 / 7 预存错误与 1911 JS 基线仅作对照，未当作通过）
- `git diff --check`：PASS

## 环境/既有缺陷（记录，不修复）

- 全量 `mvn test` 19 errors：`ExpertContactLocationServiceTest.<init>`（country CL `America/Coyhaique` 在本 JDK 11 tzdb 不存在）。
- 隔离 MySQL 组 7 errors：`expert_replied_dismissal.dismissed_at` DataIntegrityViolation（1）+ `CalendarAttachmentIntegrationTest` 上下文错误（6）。
- 原 AuthFlow 命令：测试源顶层 `@Configuration` 被组件扫描污染完整 `@SpringBootTest`（`WorkerLifecycleTestConfig` 覆盖冲突 → 两个 `*RealJdbcConfig` 的 DataSource/JdbcTemplate/NLJ/transactionManager 二义）。

## Remaining Blocker

- None（D-1 为需人工确认的授权口径问题，不影响机器状态；本步不发布、不人工验收）。

## 证据校正（控制器，2026-10-28）
- O-1 校正：本文件早前引用的计划 SHA-256 `f73efb9c…` 是执行时 `258f435` 的版本；经 A8 批准的当前计划（`c03ece5`）为 `f91e0df5…`。已批准修订见 ledger A5–A8；实现 `2553a10` 已包含 A8 放宽的测试接线，故行为无变化。
- 修复轮次：F-1 由 fix `7750a720b6fa276eb6e79ee468e40b7be98c7fdc` 关闭；原 AuthFlow 命令现复现既存 8 个上下文错误，wired 命令 `-Dspring.main.allow-bean-definition-overriding=true` 8/8 通过（真实 Testcontainers MySQL 8.0.36）。
