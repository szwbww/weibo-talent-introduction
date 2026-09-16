# Fast-P Execution — 01-calendar-api

## Epoch 1 — PAUSED_FOR_HUMAN

- Required role: isolated implementer.
- Dispatch attempts: 3 successful acquisitions; agents `01a0aa69-b511-7063-ace0-13f73e806f2a`, `01a0aa89-7344-7320-b494-e609044da557`, and `01a0aa8f-92dd-7710-bf04-b72c907dacc4` became unresponsive before returning the required result. All were shut down after bounded polling.
- Existing observed test evidence: `mvn test -Dtest=MeetingCalendarServiceTest,MeetingCalendarControllerTest` after partial implementation exited 0; 4 service tests and 3 controller tests passed. No execution report or implementation commit was produced.
- Retained partial authorized files: `V125__create_meeting_calendar_event.sql`, `MeetingCalendarEvent.kt`, `MeetingCalendarEventRepository.kt`, `MeetingCalendarService.kt`, `MeetingCalendarController.kt`, `MeetingCalendarServiceTest.kt`, `MeetingCalendarControllerTest.kt`, and the authorized Flyway test edit.
- Product code head: `24f5c8205a304d3682e09e02458960bc2caa0463`; the retained product files are uncommitted in the worktree.
- Result: `PAUSED_FOR_HUMAN`.
- Resume action: acquire a fresh isolated implementer, inspect the retained files against this brief, complete the required execution report and implementation commit, then dispatch a distinct verifier.

## Epoch 2 — RESUMED

- Resume instruction: user said `继续`.
- Preflight: branch/worktree/ledger identities match; product-code index has no staged changes; retained product files remain in the worktree.
- Next action: fresh isolated implementer inspects and completes the retained files, then writes the execution report and implementation commit.

## Epoch 2 — PAUSED_FOR_HUMAN

- User requested pause to continue with another agent.
- Current product code head: `24f5c8205a304d3682e09e02458960bc2caa0463`; retained partial authorized files remain uncommitted.
- Result: `PAUSED_FOR_HUMAN`.

## Epoch 3 — RESUMED

- Resume instruction: user said `其他agent已经执行一部分了 现在你来继续 这是工作区 /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`.
- Preflight: ledger identity (master plan `docs/plans/2026-09-16/meeting-mail-master.md` at commit `59e909070529b4b1e8ae62e61d03e67855f479ba`), master base `24f5c8205a304d3682e09e02458960bc2caa0463`, branch `fast/meeting-mail-master`, and worktree path all match; base is an ancestor of `HEAD` (`0f5b75f`); no staged index changes; the 7 new + 1 modified child-01 files remain as documented uncommitted partial work.
- Next action: fresh isolated implementer (epoch 3, `fix_round=0`) inspects the retained files against the brief, completes the child, writes the execution report, and commits `feat(fast-p): implement 01-calendar-api`.

## Epoch 3 — IMPLEMENTATION

- Executor: isolated implementer `Implementer01` (`execute-p`, fresh invocation for this exact plan identity).
- Plan: `docs/plans/fast/meeting-mail-master/children/01-calendar-api/brief.md` (canonical absolute path; byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-01-calendar-api.md`).
- Plan SHA-256: `2d5153946014a3b43141e0205351120c0bea9e707f8b9607a79525549cbc860e`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fast/meeting-mail-master/children/01-calendar-api/brief.md@2d5153946014a3b43141e0205351120c0bea9e707f8b9607a79525549cbc860e`
- Execution epoch: RESUME (prior epochs recorded above; no prior implementation commit existed, only retained uncommitted files).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`; branch `fast/meeting-mail-master`; worktree id `<root>@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`.
- Pre-execution code SHA: `0f5b75f6393b79833f30391aec3aaa5182857722`. Post-execution code SHA: `906f241cf685145d70c7917bb8b950f69c711835`. Evidence HEAD: N/A (evidence committed separately by the controller).
- Implementation boundary: `24f5c8205a304d3682e09e02458960bc2caa0463..906f241cf685145d70c7917bb8b950f69c711835` (product base .. implementation commit), plus the untracked documentation edits `docs/plans/fast/meeting-mail-master/{children/01-calendar-api/execution.md,ledger.md}` intentionally excluded from the commit.
- Result: `READY_FOR_VERIFICATION`.

### 变更文件（8/8，全部在授权清单内；无清单外编辑）

| # | 文件 | 操作 | 内容 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V125__create_meeting_calendar_event.sql` | 新增 | 结构化排期表：`datetime(6)` UTC 列、`varchar(1024)`/`varchar(200)` 边界、`uk_meeting_calendar_source_mail` 唯一来源、两条 RESTRICT FK、两条区间索引；不回填旧排期 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MeetingCalendarEvent.kt` | 新增 | 排期实体 + `ACTIVE`/`CANCELLED` 常量 + 内部值对象 `MeetingCalendarInput(startUtc, endUtc, meetingLink)` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt` | 新增 | 参数化 JDBC 显式 DTO 映射；区间/专家/摘要查询；`insert`；`findByIdForUpdate`/`findBySourceMailRecordIdForUpdate` 锁读；版本条件 UPDATE |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt` | 新增 | createManual/update/cancel/list/get/summaries + 事务内 `createFromSentMail`（`Propagation.MANDATORY`） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt` | 新增 | `/api/meeting-calendar` 六个端点 + 窄 DTO + 专用 `MeetingCalendarConflictExceptionHandler`（409 `ApiErrorResponse`） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt` | 新增 | 单元契约类（18 例）+ `MeetingCalendarServiceMysqlTest`（mysqlIt，7 例真实 MySQL） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt` | 新增 | 接口契约类（6 例，`@WebMvcTest` + 真实异常解析链）+ `MeetingCalendarControllerMysqlTest`（mysqlIt，3 例真实认证 + 真实 MySQL） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 | 13 处「最新版本」断言 124→125；新增 `V125 creates the structured calendar table and constraints`；历史 target 版本断言未动 |

### 相对上一 implementer 保留文件的修正（brief 为准）

1. 实现缺陷 A（I-2/I-3 的 400 契约）：`validateMeetingLink` 原用 `uri.scheme.equals("http", true)`，`URI("meet.example/a")`/`URI("//host/path")` 的 scheme 为 null，抛 NPE 而非 `IllegalArgumentException`，经 `GlobalExceptionHandler` 会变成 500。改为显式 scheme 判定（`uri.scheme?.lowercase() in {http, https}`），非法链接稳定 400。
2. 实现缺陷 B（I-1 同源并发）：`createFromSentMail` 唯一键冲突分支原用普通一致性读 `findBySourceMailRecordId`。REPEATABLE READ 下该事务的读视图早于并发方提交，读不到对方刚提交的行，于是并发重复提交会抛 `DuplicateKeyException` 而不是「读取原记录并返回」。新增并改用锁读 `findBySourceMailRecordIdForUpdate`（current read），并发重复提交收敛为返回同一行。此缺陷由新增的同源并发用例实测复现（`Duplicate entry '55' for key 'uk_meeting_calendar_source_mail'`），修复后用例通过。
3. 测试补全：上一轮的 4+3 个用例只做 stub 回显，未覆盖 brief 的 I-1～I-5 验收。重写为「单元/接口契约 + mysqlIt 真实 MySQL」两层，全部断言服务/接口自己算出的值或真实库状态（含 MySQL 列文本、真实唯一键、真实行锁、真实 HTTP 401/409）。

### 命令回执（本次 invocation，最终代码状态之后；JDK11 = `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）

| 命令 | 结果 | 证据 |
|---|---|---|
| `mvn test -Dtest=MeetingCalendarServiceTest,MeetingCalendarControllerTest` | PASS | exit 0，`BUILD SUCCESS`；`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`（service 18 + controller 6） |
| `mvn test -DmysqlIt=true -Dtest=MeetingCalendarServiceMysqlTest,MeetingCalendarControllerMysqlTest` | PASS | exit 0，`BUILD SUCCESS`；`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`（service 7 + controller 3）；真实 MySQL `jdbc:mysql://localhost:3306/talent_introduction`，Flyway `Successfully validated 124 migrations` / `Current version of schema talent_introduction: 125` |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 未验证（环境） | exit 1；`java.lang.IllegalStateException: Docker is required for Flyway migration tests`（`@BeforeAll startMysql()`，先于任何迁移断言）。根因：本仓 testcontainers 自带 docker-java 以 Docker API 1.32 握手，本机 OrbStack 守护进程要求 ≥1.40 —— `BadRequestException (Status 400: {"message":"client version 1.32 is too old. Minimum supported API version is 1.40, please upgrade your client to a newer version"})`；`DOCKER_HOST=unix:///var/run/docker.sock` 与 `DOCKER_API_VERSION=1.44` 两次追加尝试同样失败。**未将跳过/阻塞的用例写成通过。** |

V125 的替代（非命令）证据（真实库、非容器）：

- 命令 2 的 Flyway 日志显示 V125 已在真实库应用并校验通过（schema version 125）。
- `docker exec ti-mysql-it mysql ... information_schema`：`meeting_calendar_event` 列为 `id bigint / expert_contact_id bigint NOT NULL / source_mail_record_id bigint NULL / starts_at_utc,end_at_utc datetime(6) NOT NULL / meeting_link varchar(1024) / note,cancel_reason varchar(200) / status varchar(16) NOT NULL / created_at,updated_at datetime(6) NOT NULL`；索引 `PRIMARY(id)`、`idx_meeting_calendar_status_start_id(status,starts_at_utc,id)`、`idx_meeting_calendar_contact_status_start_id(expert_contact_id,status,starts_at_utc,id)`、`uk_meeting_calendar_source_mail(source_mail_record_id)`；FK `fk_meeting_calendar_contact→expert_contact`、`fk_meeting_calendar_source_mail→mail_record`。
- 因而 Flyway IT 中新增的「表/约束」断言本身仍为未验证；其断言内容已由上述真实库事实与命令 2 的并发唯一键行为间接确认。

### 不变量覆盖（对应 brief 验收标准）

- **I-1**：同一 `source_mail_record_id` 顺序提交两次只有 1 行且不覆盖首次排期；两线程并发同源提交只产生 1 行且两个调用方拿到同一 id（mysqlIt）；来源邮件属于其他专家时拒绝（单元）；来源唯一由真实 `uk_meeting_calendar_source_mail` 保证。
- **I-2**：北京 `2026-09-18T10:00–10:30` 存储并读回 `2026-09-18T02:00:00Z–02:30:00Z`；JVM 默认时区 `UTC`/`Asia/Shanghai`/`America/New_York` 三种取值结果一致（单元 + 真实 MySQL，另断言列文本 `2026-09-18 02:00:00…` 而非 +08:00 墙上时间）；相等/反向/非法格式时间 400。
- **I-3**：两客户端持同一版本，先改期成功且 `updated_at` 严格递增 ≥1µs，后改期 409；取消后改期 409；重复取消返回现状 200 且保留原取消原因、不重写版本；取消保留行、默认查询 0 条而 `showCancelled=true` 1 条；无变化保存返回原记录且不写版本；HTTP 层 409 由本 controller 专用 advice 返回（`@WebMvcTest` 真实异常解析链，未被 `GlobalExceptionHandler` 兜成 500）。
- **I-4**：单元层排期写入只触碰日历仓储（`verifyNoMoreInteractions(repository)`；专家仓储只 `existsById`、永不 `save`）；MySQL 层改期/取消前后 `mail_record` 正文与 ICS 快照 JSON 逐字一致、`expert_contact.current_status`/`operator_status` 不变、无新的 `expert_contact_status_history`、无新邮件行；接口不接收换专家/换来源/换状态字段（响应字段集合断言，无 `isCancelled`/`hasSchedule`/`changed`）。
- **I-5**：区间交集按 `starts_at_utc < to AND ends_at_utc > from`，跨午夜场次在同一北京日的两个相邻窗口都出现、且不越界到第三天；201 行以 limit 200 两页取全（顺序 `starts_at_utc,id`，无重无漏）；`limit>200` 400；范围 >62 天 400；摘要零场 `activeCount=0/next=null`、非零时优先最近未来否则最近过去、顺序与请求一致；`>100` 个 contactId 拒绝；游标绑定过滤条件（换 contactId / 换 showCancelled / 乱码均 400）；空 contactIds 直接返回 `[]` 且不触库。

### 下游接口（供 02 使用）

- `MeetingCalendarService.createFromSentMail(record: MailRecord, input: MeetingCalendarInput): EventRow`，标注 `@Transactional(propagation = MANDATORY)`；`MeetingCalendarInput(startUtc: Instant, endUtc: Instant, meetingLink: String?)` 定义在 `MeetingCalendarEvent.kt`；无公开「标记已发送」端点。
- `MeetingCalendarService.MeetingCalendarConflictException` + controller 文件内 `MeetingCalendarConflictExceptionHandler`（`@RestControllerAdvice(assignableTypes = [MeetingCalendarController::class])`, `Ordered.HIGHEST_PRECEDENCE`）→ 409 `ApiErrorResponse("CONFLICT", …)`；不使用 `ResponseStatusException`。
- 版本串即 `updatedAt` 原样回传的 ISO Instant（带 `Z`），可作为不透明版本提交回 `expectedUpdatedAt`。

### 偏离 / 未决

- `MeetingCalendarConflictException` 的物理位置：brief T3 写「版本/取消冲突定义同文件」，本实现按服务语义放在 `MeetingCalendarService`（controller 文件提供专用 advice）。若人类要求该类移入 controller 文件，属无契约影响的小幅重排。
- 新增 `MeetingCalendarEventRepository.findBySourceMailRecordIdForUpdate`（授权文件内）为修正缺陷 B 所必需，是 brief「来源唯一冲突只读取原记录并返回」的唯一可行实现方式。
- 未运行全量 `mvn test`（按执行约定只跑 brief 指定命令），故未对既有测试做回归扫描。
- 未验证项：命令 3（`FlywayMigrationIntegrationTest`）因 Docker API 版本不兼容无法运行，见上表；无其他未验证项。

### 身份复算

- Plan identity rechecked: YES（SHA-256 未变）。
- Worktree identity rechecked: YES（root/branch/git-dir 未变）。
- Reported commit reachable from target branch: YES（`906f241` 即 `fast/meeting-mail-master` 当前 HEAD）。
- Required commands run this invocation: YES（命令 1/2 在最终代码状态后新跑；命令 3 已尝试并被环境阻断）。
- Historical evidence used only as baseline: YES。
- Implementation commit file/subject 规则：`feat(fast-p): implement 01-calendar-api`，仅含 8 个授权文件，未含 `docs/plans/**`。
