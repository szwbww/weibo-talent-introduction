# 01 执行报告 — 收发件箱挂起：状态存储、查询与接口

## 结果

- 状态：**READY_FOR_VERIFICATION**
- 实现提交：`79fb15d350621ab2c39b79217d9a4b7a28b9cb0d` `feat(fast-p): implement 01`（仅 9 个授权文件，工作区干净）
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`，分支 `fast/2026-10-03-mailbox-suspension`
- 起始 HEAD：`6d58bd4593b250fdc3bf800a65b5ee6655fac219`；child_base_sha：`c486c5c44806b5b4c4db654606c358efb94fec5a`
- JDK：`/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`；MySQL 容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`）；Docker=OrbStack。

## 命令与结果（fresh 运行，逐条记录）

| # | 命令（cwd=worktree 根，命令前缀 JAVA_HOME 省略） | exit | 计数 | 日志 |
|---|---|---|---|---|
| B1 | `mvn -DskipTests test-compile` | **0** | BUILD SUCCESS | /tmp/fastp-01-b1.log |
| B2 | `DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root mvn -DmysqlIt=true -Dtest=MailboxConversationRepositoryIT,MailboxConversationRepositorySqlCompatTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailboxSuspensionServiceIT test` | **1** | 汇总 Tests run: **71**, Failures: **0**, Errors: **6**, Skipped: **0** | /tmp/fastp-01-b2-run2.log |
| B3 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test` | **1** | FlywayMigrationIntegrationTest: Tests run: **35**, Failures: **0**, Errors: **0**, Skipped: **0**；但 `test` 阶段尾部的 `exec-maven-plugin:node-test` 失败（18 个既有前端缓存键用例），详见「偏差」 | /tmp/fastp-01-b3-run1.log |
| D1（额外诊断，非计划要求） | `mvn -Dexec.skip=true -Dtest=OperatorStatusWriteSeamGuardTest test` | 1（同上 node-test 尾部） | OperatorStatusWriteSeamGuardTest: Tests run: **1**, Failures: 0, Errors: 0 | /tmp/fastp-01-guard.log |

B2 分文件计数：

| 测试类 | Tests | Failures | Errors |
|---|---|---|---|
| MailboxConversationRepositoryIT | 25 | 0 | 1 |
| MailboxConversationRepositorySqlCompatTest | 2 | 0 | 0 |
| CalendarAttachmentIntegrationTest | 5 | 0 | 5 |
| MailboxConversationControllerTest | 32 | 0 | 0 |
| MailboxSuspensionServiceIT（新增） | 7 | 0 | 0 |

## 基线对照（children/01/baseline.md）

| 命令 | 基线 | 本次 | 判定 |
|---|---|---|---|
| B1 | exit 0，BUILD SUCCESS | exit 0，BUILD SUCCESS | 一致 |
| B2 | exit 1；Tests 57 / F0 / E6 | exit 1；Tests 71 / F0 / E6 | 基线 6 个既有错误完全不变；**+14 新用例全部通过**（Repo IT +3、Controller +4、ServiceIT +7） |
| B3 | exit 1；Tests 34 / F25 / E0 | surefire Tests 35 / F0 / E0（整体 exit 1 见偏差） | **25 个 `145 vs 146` 基线失败全部消除**（改为 147 后迁移到 V147）；+1 个 V147 新表用例通过 |

B2 的 6 个既有错误与基线逐条对应（非本次引入，未修复/未隐藏）：
- `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives:225` — `DataIntegrityViolationException`（V143 `expert_replied_dismissal.dismissed_at` 无默认值）。
- `CalendarAttachmentIntegrationTest` 全部 5 个用例 `Application context failed to start`（同文件第二个类缺 `PendingMailOperationService` 依赖）。

B3 的 node-test 失败全部为既有前端缓存键用例（18 个），与 `index.html` 中两处不同 `?v=`（`20261003-mobile-core-03` 与 `20261003-mobile-core-03-generic-followup`）有关；`git status` 证明前端/JS 测试文件未被本片修改，且 `git show HEAD:src/main/resources/static/index.html` 已含这两个键 → 属 worktree 基线既有状态。基线 B3 因 surefire 先失败未执行到 node-test，故基线未记录该批失败。

## 文件变更（9 个授权文件，全部已提交）

1. `src/main/resources/db/migration/V147__create_expert_mailbox_suspension.sql`（新增）— 新空表：`PK(username, expert_contact_id)`、`reason VARCHAR(500) NULL`、`FK→expert_contact(id)`、`ENGINE=InnoDB CHARSET=utf8mb4`，与计划 T1 逐字一致。
2. `mail/service/MailboxSuspensionService.kt`（新增）— `get/suspend/resume`（PUT/DELETE `@Transactional` + `SELECT contact FOR UPDATE` 串行化）、`MailboxSuspensionState`、`MailboxSuspensionRequest`、`MailboxSuspensionConflictException`；参数化 JDBC。
3. `mail/repository/MailboxConversationRepository.kt` — `ConversationFilter.suspendedOnly`；共享 `expertPredicates` 增加当前用户 suspension EXISTS/NOT EXISTS；`orderByClause` 把 suspendedOnly 纳入已选队列分支；新增 `suspensionStatesByContacts`（批量 2 条参数化聚合，无 N+1/窗口函数）。
4. `mail/service/MailboxConversationService.kt` — `listConversations` 末尾新增 `suspendedOnly=false` 并透传筛选；DTO 逐项填真实挂起状态与跨账号 pending（构造参数不变）。
5. `mail/controller/MailboxConversationController.kt` — 三个 suspension 端点；列表新增 `suspendedOnly=false`；`ConversationItemResponse` 末尾新增 `suspended/suspendReason/suspensionPendingCount`。
6. `test/.../MailboxConversationRepositoryIT.kt` — 新表清理；3 个新用例（挂起归类/批量摘要/分页与 count 同谓词）。
7. `test/.../MailboxConversationControllerTest.kt` — 两 context 注入（第一个 `@Import` 真实服务，第二个 `@MockBean`）、两处 cleanup；4 个 HTTP 用例（幂等/身份/409/404/400/列表）。
8. `test/.../MailboxSuspensionServiceIT.kt`（新增）— 7 个真实 MySQL 服务用例（幂等/404/409/原因边界/跨账号计数/无副作用/并发）。
9. `test/.../FlywayMigrationIntegrationTest.kt` — 25 处 latest-target 断言 `145→147`（历史 target=xx 断言未动）；新增 V147 新表列/主键/外键/唯一性/升级空表用例。

## 验收标准对照（I-1～I-6）

- I-1（行存在即挂起）：`MailboxSuspensionServiceIT`（null reason 仍 suspended、DELETE 后 false、重复 PUT 单行）+ V147 迁移用例（reason 可空、重复 PK 拒绝）。
- I-2（无自动结束写路径）：ServiceIT `pending count ...` 处理一封后挂起仍在；ServiceIT `suspend never writes other business tables` 对 expert_contact/follow/dismissal/mail_record/inbound 全表计数前后相等。
- I-3（账号与计数口径）：ServiceIT `pending count aggregates cross account real accounts only`（acc-a + disabled inactive-acc 计入；SIMULATOR_NOOP/不存在账号/已处理不计；op2 同值）；RepoIT 批量摘要用例同口径。
- I-4（查询归类及分页）：RepoIT `suspended experts leave pending and replied ...`（待处理 NOT EXISTS、已回复 NOT EXISTS、全部/关注保留、冲突条件 AND 空集）；`suspended pagination ...`（45 专家分页 count/page 同谓词、零 pending 挂起可见、explain 同筛选）。
- I-5（身份/幂等/原因）：ControllerTest 4 用例 + ServiceIT 覆盖 401/404/409/400、trim/空白→null、500 可存 501 拒绝、并发 PUT 单行、身份隔离、SQL 参数化。
- I-6（纯附加能力）：ServiceIT 全表计数不变；`MailboxConversationService` 构造参数不变；新 DTO 字段均有默认值。

## 偏差与决策

1. **409 的实现方式**：计划 T2 允许「统一异常机制/ResponseStatusException」。实测本仓 `GlobalExceptionHandler` 含 `@ExceptionHandler(Exception::class)`，`ExceptionHandlerExceptionResolver` 先于 `ResponseStatusExceptionResolver`（spring-webmvc 5.3.29 源码 `WebMvcConfigurationSupport` 顺序与 `ExceptionHandlerMethodResolver` 无 ResponseStatusException 特判），故 `ResponseStatusException` 会被吞成 500。为满足「无未处理不能新挂起 409」这一验收，`MailboxSuspensionService.suspend` 抛 `MailboxSuspensionConflictException`（继承 `IllegalStateException`），controller 在 PUT 边界显式映射为 409 + 既有 `ApiErrorResponse` 形状；404/400/401 仍走统一机制（`NoSuchElementException`/`IllegalArgumentException`/controller session 校验）。未新增异常 advice 文件。
2. **新测试上下文事务管理器**：`MailboxConversationRealJdbcConfig` 增补一个 `@ConditionalOnMissingBean(PlatformTransactionManager)` 的 `DataSourceTransactionManager`，使 `@Transactional` 挂起服务在 `@WebMvcTest` slice 可运行（缺失时切片不提供事务管理器；该 bean 仅在缺失时注册，不与任何自带 bean 冲突）。属授权测试文件 7 内。
3. **B3 整体 exit 1**：目标测试 `FlywayMigrationIntegrationTest` 35/0/0 全绿，但 `mvn ... test` 阶段尾部绑定的 `exec-maven-plugin:node-test` 因 18 个既有前端缓存键用例失败而 exit 1（见「基线对照」）。这些文件不在本片授权范围且未被修改，属 worktree 基线状态，未修复/未隐藏。
4. **计划未修订**：执行中未修改 `docs/plans/**`（报告除外）；无 PLAN_CONFLICT。

## 未运行项

- 人工验收清单 A-1～A-5：按派发消息明确属于本轮范围外。
- 全量 `mvn test`（非 -Dtest 门禁）未运行；`exec node-test` 的失败已在 B3/D1 中显现并归因为既有状态。
- 未联网、未连线上 MySQL/ES、未发信、未部署。
