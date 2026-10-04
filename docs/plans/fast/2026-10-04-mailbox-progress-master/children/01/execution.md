# Child 01 Execution Report — 收发件箱三态标记：存储与API

## Execution Result: READY_FOR_VERIFICATION

Plan（批准版）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master/docs/plans/2026-10-04/mailbox-progress-01-backend.md`
Master plan: `docs/plans/2026-10-04/mailbox-progress-master.md`（identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`）
Execution epoch: NEW（本 worktree 首次执行 child 01）
Executor: `ImplMailboxProgress01`（fast-p child 01 implementer）
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`
Target branch: `fast/2026-10-04-mailbox-progress-master`
Worktree Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`
child_base_sha（seed）: `9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`
Pre-execution HEAD: `06c10769e37a3d4b8c9a1723fcf86731509c7776`
Post-execution code SHA（实现提交）: `dbe79c2bfb466bedb2c70d767bdd34082307579c`
Evidence HEAD: N/A（证据报告 `children/01/execution.md` 由控制方单独提交，不进实现提交）
Implementation boundary: `06c1076..dbe79c2`（仅 10 个授权文件）
Commit subject: `feat(fast-p): implement 01`

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 兼容迁移 + 迁移测试 | IMPLEMENTED | `V149__…progress_status.sql`, `FlywayMigrationIntegrationTest.kt` | B3 36/0/0；`V149 upgrades old follows to FOLLOWING without losing ownership or created_at` 通过 |
| T-2 唯一服务/枚举/新端点 | IMPLEMENTED | `ExpertFollowService.kt`, `MailboxConversationController.kt` | B2 ControllerTest 41/0/0（+8 新用例全绿） |
| T-3 分页查询与 summary | IMPLEMENTED | `MailboxConversationRepository.kt`, `MailboxConversationService.kt`, `MailboxConversationController.kt` | B2 RepositoryIT 30/0/1（+5 新用例全绿，1 个既有 error 不变） |
| T-4 挂起读取同步 | IMPLEMENTED | `MailboxSuspensionService.kt` | B2 SuspensionServiceIT 10/0/0（+1 新用例） |
| T-5 实证验证 | IMPLEMENTED | 4 个测试文件 | B2/B3 真实 MySQL + Testcontainers 计数见下 |
| I-1～I-6 | IMPLEMENTED | 见「不变量证据」 | 逐条对应真实库用例 |

## Commands（fresh，本 invocation 实际运行）

环境：JDK `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`；MySQL 容器 `ti-mysql-it`（mysql:8.0，`localhost:3306`，root/root，库 `talent_introduction`，连接串带 `allowPublicKeyRetrieval=true`）；Docker=OrbStack（`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`）。

| # | Command | Result | exit | 计数（Tests/Failures/Errors） | 日志 |
|---|---|---|---:|---|---|
| B1 | `mvn -DskipTests test-compile` | PASS | 0 | BUILD SUCCESS | `/tmp/fastp-mbp-b1.log` |
| B2 | `DB_URL="jdbc:mysql://localhost:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root mvn -DmysqlIt=true -DskipNodeTests=true -Dtest=MailboxConversationControllerTest,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT test` | 期望中的 1 个既有 error | 1 | **81 / 0 / 1** | `/tmp/fastp-mbp-b2.log` |
| B3 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -DmigrationIt=true -Dapi.version=1.40 -DskipNodeTests=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | 0 | **36 / 0 / 0**，BUILD SUCCESS | `/tmp/fastp-mbp-b3.log` |

B2 分文件：

| 测试类 | Tests | F | E | 基线 | 增量 |
|---|---:|---:|---:|---|---|
| MailboxConversationRepositoryIT | 30 | 0 | 1 | 25 / 0 / 1 | +5 通过；同一个既有 error 不变 |
| MailboxConversationControllerTest | 41 | 0 | 0 | 33 / 0 / 0 | +8 通过 |
| MailboxSuspensionServiceIT | 10 | 0 | 0 | 9 / 0 / 0 | +1 通过 |

唯一 error（与基线逐字一致，未隐藏、未顺手修复）：
`MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives`
→ `DataIntegrityViolationException: ... INSERT INTO expert_replied_dismissal (username, expert_contact_id, last_inbound_id) ...; Field 'dismissed_at' doesn't have a default value`（`MailboxConversationRepositoryIT.kt:225` fixture，V143 之后的既有缺陷；新增 import 使其由基线 :224 平移 +1，与 surefire `:226` 报错点一致）。

B3 中新增用例已确认执行：`testcase name="V149 upgrades old follows to FOLLOWING without losing ownership or created_at"`（surefire XML）。`fresh database migrates through the latest version` 断言已更新为 149 并通过。

### 与 baseline.md 对照

| 项 | 基线 | 本次 | 结论 |
|---|---|---|---|
| B1 | exit 0 / BUILD SUCCESS | exit 0 / BUILD SUCCESS | 不变 |
| B2 总计 | 67 / 0 / 1 | 81 / 0 / 1 | +14 通过（另有 1 个迁移测试新增在 B3），失败集不变 |
| B2 RepositoryIT | 25 / 0 / 1 | 30 / 0 / 1 | +5；既有 error 不增不减 |
| B2 ControllerTest | 33 / 0 / 0 | 41 / 0 / 0 | +8 |
| B2 SuspensionServiceIT | 9 / 0 / 0 | 10 / 0 / 0 | +1 |
| B3 | 35 / 0 / 0 | 36 / 0 / 0 | +1 新增 V149 用例，无回归 |

新增通过用例合计 15（8+5+1+1），无任何新增 Failure/Error。

## Changed Files（= 授权 10 文件，提交 `dbe79c2` 逐字一致）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V149__add_expert_follow_progress_status.sql`（新增） | 单列：`progress_status VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'` + COMMENT；不改 V121、不加索引/新表 |
| 2 | `…/mail/service/ExpertFollowService.kt` | 新增 `MailboxProgressStatus{NONE,FOLLOWING,PROVIDED}`、`MailboxProgressResult(contactId,progressStatus,followed)`；新 `setProgressStatus`（FOLLOWING/PROVIDED 单条 upsert，NONE 单条 DELETE）；旧 `setFollowed(true)` 复用显式 FOLLOWING 写入，`setFollowed(false)` 条件 `DELETE … AND progress_status='FOLLOWING'`；构造函数签名不变 |
| 3 | `…/mail/controller/MailboxConversationController.kt` | 新增 `MailboxProgressRequest(status)` DTO 与 `PUT /{contactId}/progress-status`（空白用户名 401）；`ConversationItemResponse` 尾部新增 `progressStatus`（默认从 followed 派生）；列表新增 `providedOnly` 参数并透传 |
| 4 | `…/mail/repository/MailboxConversationRepository.kt` | page/explain 同源投影改为 `COALESCE((SELECT ef.progress_status … ),'NONE') AS progress_status`；`ConversationSummarySqlRow` 改存 `progressStatus` 并派生 `followed`；followed 谓词加 `progress_status='FOLLOWING'`；新增参数化 `:providedOnly` EXISTS（PROVIDED，置于消息 OR 括号外）；`orderByClause` 把 providedOnly 归入非 pending-first 分支；`ConversationFilter` 尾部新增 `providedOnly`；params 新增绑定 |
| 5 | `…/mail/service/MailboxConversationService.kt` | `listConversations` 尾部新增 `providedOnly:Boolean=false`，传入 filter；列表项显式映射 `progressStatus = row.progressStatus` |
| 6 | `…/mail/service/MailboxSuspensionService.kt` | `isFollowed`（行存在）→ `progressStatusOf`（一次读取 DB 真值）；`stateOf` 构造 progressStatus 并派生 followed；`MailboxSuspensionState` 尾部新增 `progressStatus`；不改挂起写入/锁/原因/计数/权限 |
| 7 | `…/campaign/repository/FlywayMigrationIntegrationTest.kt` | 26 处“最新版本”断言 `"147"`→`"149"`；新增 V149 旧数据升级用例 |
| 8 | `…/mail/controller/MailboxConversationControllerTest.kt` | 新增 8 个用例 + `progressStatusOf`/`followCreatedAt` 助手 + 枚举 import |
| 9 | `…/mail/repository/MailboxConversationRepositoryIT.kt` | 新增 5 个用例 + `insertMark` 助手 + `filter()` 的 providedOnly + 枚举 import |
| 10 | `…/mail/service/MailboxSuspensionServiceIT.kt` | 新增 1 个跨端点 progressStatus 用例 |

`git diff --stat`：10 files changed, 821 insertions(+), 78 deletions(-)。`git status --porcelain` 在提交前仅有这 10 项，无其它改动。

## 不变量证据（I-1～I-6）

- **I-1（三态唯一表示）**：B3 `V149 upgrades old follows…` 断言列 `varchar(16) NOT NULL DEFAULT 'FOLLOWING'`、`COLUMN_DEFAULT=FOLLOWING`、无 `provided` 列、3 行旧关注全部 `FOLLOWING`、省略新列的 INSERT 仍落 `FOLLOWING`、复合主键仍拒绝重复 key。B2 ControllerTest 断言 `followRows==1`（同一主键一行）且 NONE 后表内无行（`progressStatusOf==null`）。Repository 投影实测 `PROVIDED/FOLLOWING/NONE` 三值。
- **I-2（原子、幂等、用户隔离）**：六条转换逐步核对 DB 真值与回包；转态与重复同态均不刷新 `created_at`；非法/缺失 status（400）、匿名/空白/幽灵用户（401）、未知专家（404）后表计数为 0；请求体 `username:"attacker"` 不改变 owner（op1/op2 各自隔离）；8 线程并发 `setProgressStatus` 交替值后仍 `1` 行且最终值为完整提交状态。
- **I-3（旧接口唯一写者/兼容）**：新 PUT `PROVIDED` → 旧 `DELETE /follow` 后仍 `PROVIDED`；旧 `PUT /follow` → `FOLLOWING`；重复旧 PUT 不刷新 `created_at`；旧回包 `{followed:boolean}` 由 jsonPath 断言；旧 DELETE 对 FOLLOWING 生效。未新增第二套表或同步器。
- **I-4（读写同义、分页前筛选）**：page/explain 改为同一 `COALESCE(...progress_status...)` 投影并共用 `orderByClause`；`followed` 由该单次读取派生；实测 `followed==true` 仅 FOLLOWING、`providedOnly=true` 仅 PROVIDED、两者同传 page 与 count 均为 0；21 位已提供专家分页 20+1、total=21、无重复；与 q/账号/日期/主题/标签 AND 均正确；已提供排序为最近真实来信倒序、无来信置底、contactId DESC；`explainConversationsPage` 可执行。
- **I-5（独立于消息处理；已回复存在性排除）**：标记写入前后 `expert_contact/mail_record/inbound_mail_processing/inbound_mail_tag/expert_mailbox_suspension/expert_replied_dismissal` 计数完全不变；已回复筛选对 FOLLOWING 与 PROVIDED 均按 `NOT EXISTS expert_follow` 整行排除，另一用户不受影响，删行后按原资格恢复；挂起服务的“不写其它业务表”既有断言仍绿。
- **I-6（挂起响应可完成归类）**：GET/PUT/PATCH(reason)/DELETE 四条路径的响应均带真实 `progressStatus`；`PROVIDED` 时 `followed=false`；切换标记不改 `suspended/suspendReason/suspensionPendingCount`；挂起期间消息被处理不改变标记状态；取消挂起后仍 `PROVIDED`/`FOLLOWING` 正确。

## 下游接口（child 02，已按 brief 逐字实现并有断言）

- `PUT /api/mail/mailbox/conversations/{contactId}/progress-status`，body `{"status":"NONE|FOLLOWING|PROVIDED"}` → 200 `{contactId, progressStatus, followed}`（jsonPath 三字段断言）。
- `GET /api/mail/mailbox/conversations?providedOnly=true`（`followed=true` 仍表示跟进中）。
- 列表项与挂起响应新增 `progressStatus`；`followed` 恒等于 `progressStatus==FOLLOWING`。
- 旧 `PUT/DELETE /{contactId}/follow` 与 `{followed:boolean}` 回包保持兼容。

## Deviations

- **实现层：无。** 未新增白名单外文件；未改 `docs/plans/**`；未改 `pom.xml`；未加依赖；未联网、未连生产库/ES、未发信；未 push/merge/rebase/amend/reset。
- **环境层（非文件改动，须记录）**：B2 首次运行失败（全部用例 `Failed to load ApplicationContext`），根因是本机共享测试库 `talent_introduction` 带有**并行计划**已应用的 `V148 create expert discovery review`（`flyway_schema_history` installed_on `2026-10-04 13:27:12`），而本 worktree 无 V148 文件 → `FlywayValidateException: Detected applied migration not resolved locally: 148`。该冲突无法在本 child 授权文件内解决（不可覆盖/新增 V148，不可开 outOfOrder）。依据 brief 约束 6 与计划“显式指定独立可清空测试库”，在确认无并发测试进程、无活动 DB 会话后，将**指定的可清空本地测试库**重置为干净状态后重跑 B2：
  ```sh
  docker exec ti-mysql-it mysql -uroot -proot -e "DROP DATABASE IF EXISTS talent_introduction; CREATE DATABASE talent_introduction CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  ```
  重置后 B2 全绿（仅剩 1 个基线既有 error）。**风险提示（交由控制方/人工判断）**：与该分支共用同一容器库的 `fast/2026-10-04-discovery-review-master` worktree 在下次 mysqlIt 运行时会看到“V149 not resolved locally”，需其自行重置或完成双方编号/发布顺序协调（主计划“编号协调”条款已预告此条件）。未触碰任何线上/日常库。

## Unrun Items

- `MailboxConversationRepositorySqlCompatTest`（`MailboxConversationRepositoryIT.kt` 内第二个类）与 `CalendarAttachmentIntegrationTest`（`MailboxConversationControllerTest.kt` 内第二个类）：`-Dtest=` 选择器不匹配这两个类，故本 invocation 未执行（基线 B2 同样未计入）；不属于 brief 必需命令。
- Node/JS 前端测试：按 `-DskipNodeTests=true` 跳过（child 02 范围）。
- 全量 `mvn test`：不在 brief 必需命令内，未运行。
- 人工验收清单 A-1～A-4：需部署隔离验收环境，未执行（属人工验收阶段）。
- 未生成本 child 的 `-acceptance.md`（计划规定人工验收开始时才导出）。

## Freshness

- Plan identity rechecked: YES（本 invocation 从磁盘重读 brief + 01 计划 + master 计划）
- Worktree identity rechecked: YES（`pwd`/`--show-toplevel`/`--abbrev-ref`/`--git-dir`/`--git-common-dir` 提交前核对）
- Reported commits reachable from target branch: YES（`git merge-base --is-ancestor HEAD fast/2026-10-04-mailbox-progress-master`）
- Required commands run this invocation: YES（B1/B2/B3 全部 fresh，日志文件已保留）
- Historical evidence used only as baseline: YES（baseline.md 仅用于对照）

## Remaining Blocker

- None（实现与必需命令均已完成）。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

## Fast-P Archive

本报告与同目录 verify-log.md、fix-log.md 随 01 证据提交归档（fast-p 控制方）。
