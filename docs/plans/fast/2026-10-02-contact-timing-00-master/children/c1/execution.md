# Execution Report — c1（contact-timing-01-location）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master/docs/plans/2026-10-02/contact-timing-01-location.md`
- Plan SHA-256: `c2821c4c8d949283dd72d580d2b70f754c45a3fadaf2a8b3f38563f0f3a82a84`（执行前后各重算一次，未变化）
- Execution ID: `…/docs/plans/2026-10-02/contact-timing-01-location.md@c2821c4c…`
- Execution epoch: NEW（旧 execution.md 为占位模板，无同身份历史）
- Approval basis: 本次调用（child brief + 已批准计划）
- Executor: C1Impl（fast-p 子代理）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- Target branch: `fast/2026-10-02-contact-timing-00-master`
- Worktree ID: `…-fast-2026-10-02-contact-timing-00-master@fast/2026-10-02-contact-timing-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- Pre-execution code SHA: `9b7c04c98f669e5c3b3e27fe09502a5e90e3eafe`
- Post-execution code SHA: `dc5546a6e914c23249a8b7dbadcc977e5df1b7e3`
- Evidence HEAD: N/A（无单独证据提交；报告为未提交产物）
- Implementation boundary: `9b7c04c..dc5546a`（9 个新增文件，1533 insertions / 0 deletions）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 迁移 + 模型（I-1/I-2/I-3/I-5） | IMPLEMENTED | `V146__create_expert_contact_location.sql`、`ExpertContactLocationModels.kt` | IT `migration creates the location table…`（列/PK/CHAR(2)/可空/no FK→expert_contact CASCADE、flyway history version=146 success=1） |
| T-2 目录（I-2/I-3） | IMPLEMENTED | `contact-country-timezones.json`、`ExpertContactLocationCatalog.kt`、`ExpertContactLocationModels.kt` | 单测 `catalog matches the frozen evidence snapshot byte for byte`（247 国 / 418 关系 / SHA-256 对齐 / default∈zones / `ZoneId.of` 全通过） |
| T-3 服务 + 接口（I-1～I-5） | IMPLEMENTED | `ExpertContactLocationService.kt`、`ExpertContactLocationController.kt` | 单测 10 项、controller 测试 7 项、IT 7 项全绿 |
| T-4 验证 | IMPLEMENTED | 三项测试文件 | 下列两条必需命令均为 exit 0 |

## Changed Files

| # | 路径 | 操作 |
|---:|---|---|
| 1 | `src/main/resources/db/migration/V146__create_expert_contact_location.sql` | 新增（执行前复核最大迁移号=V145，V146 未被占用） |
| 2 | `src/main/resources/contact-country-timezones.json` | 新增（证据快照逐字副本，`cmp` 逐字节相同） |
| 3 | `src/main/kotlin/.../mail/service/ExpertContactLocationModels.kt` | 新增 |
| 4 | `src/main/kotlin/.../mail/service/ExpertContactLocationCatalog.kt` | 新增 |
| 5 | `src/main/kotlin/.../mail/service/ExpertContactLocationService.kt` | 新增 |
| 6 | `src/main/kotlin/.../mail/controller/ExpertContactLocationController.kt` | 新增 |
| 7 | `src/test/kotlin/.../mail/service/ExpertContactLocationServiceTest.kt` | 新增 |
| 8 | `src/test/kotlin/.../mail/controller/ExpertContactLocationControllerTest.kt` | 新增 |
| 9 | `src/test/kotlin/.../mail/service/ExpertContactLocationServiceIT.kt` | 新增 |

未修改任何既有文件（`git show --stat HEAD`：9 files changed, 1533 insertions(+), 0 deletions(-)）；`ExpertContactRepository`、`MailRecordRepository`、`MailboxService`、`expert_contact` 表本身零改动。

## Commands

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ExpertContactLocationServiceTest,ExpertContactLocationControllerTest,OperatorStatusWriteSeamGuardTest` | PASS | exit 0；`ExpertContactLocationServiceTest` 10/0/0，`ExpertContactLocationControllerTest` 7/0/0，`OperatorStatusWriteSeamGuardTest` 1/0/0（合计 18，Failures 0，Errors 0，Skipped 0）；`BUILD SUCCESS`；命令内 `exec-maven-plugin` 的 `node --test src/test/js/*.test.js` 1273 pass / 0 fail、`node --check` 两项通过 |
| 2 | `JAVA_HOME=…/zulu-11.0.32.jdk/Contents/Home mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'` | PASS | exit 0；`ExpertContactLocationServiceIT` 7/0/0（Tests run: 7, Failures: 0, Errors: 0, Skipped: 0）；`BUILD SUCCESS` |

补充证据：

- `-Dspring.datasource.url` 确实传播到测试 JVM：IT 首个断言 `SELECT DATABASE()` == `talent_contact_timing_it` 通过（若落在默认库会立即失败）。
- 迁移落库核对（只读）：`talent_contact_timing_it.flyway_schema_history` 共 145 行、`MAX(version)=146`、`version='146' → success=1`；`SHOW CREATE TABLE talent_contact_timing_it.expert_contact_location` 与计划 SQL 一致（`bigint PK` / `char(2) NOT NULL` / `varchar(64) NULL` / `fk_expert_contact_location_contact … ON DELETE CASCADE` / InnoDB utf8mb4 + COMMENT）。
- 业务库未被触碰：`talent_introduction.flyway_schema_history` 中 version='146' 计数 0，`talent_introduction.expert_contact_location` 不存在。
- IT 自清理生效：运行后 `talent_contact_timing_it.expert_contact_location` 行数 0，seed contact(881001/881002) 与 campaign/sender(881000) 均已删除。
- 目录资源 `cmp` 与 `sha256sum`：`docs/plans/2026-10-02/contact-timing-evidence/country-timezones.proposed.json` == `src/main/resources/contact-country-timezones.json`，同为 `c27ad26d63ccc151a82844d757b6494bfa73ba69c772d4f45dc627c6fa6b5480`（该值亦被单测钉住）。

未运行的命令（按 brief/instruction 要求不做）：全量 `mvn test`。基线记录的 2 个既有 `MeetingConfirmationServiceTest` 错误（本机可用 JDK 的 tzdb 与中文会议时区目录差异）与本 child 无关，未触碰。

## Invariants / IP 覆盖（实现与证据）

- I-1：`expert_contact_id` 主键、每 contact 至多一行、无行=未配置不猜国家；两处直接证据：IT `unconfigured contact reads configured false…`（GET 不建行、两次 PUT 仍 1 行）、IT `deleting the contact cascades the location row`、单测 `persisted country outside the catalog is refused instead of guessed`（已保存的目录外国家码报 `IllegalStateException`，不回退、不猜）。
- I-2：只接受目录内大写 ISO 二字码；`br`→`BR`（IT+单测），`ZZ`→400（单测+controller），目录只从随包 JSON 加载（无联网、无第三方库、不解析 `expert_contact.country`）。
- I-3：`zone_id IS NULL` = 使用 `defaultZoneId`（IT 直接断言 DB 中 `zone_id` 为 NULL，不回写展开默认值）；显式值必须属于该国（`BR/Asia/Tokyo` → 400）；换配置/切回默认时两列一起覆盖，无旧显式时区残留；空白 zoneId 归一为 null。
- I-4：唯一业务写入口 `PUT → ExpertContactLocationService.save`；controller 显式从 Session 取 username（body 中 `username/operatorName` 被忽略，controller 测试断言真实调用参数为 `("admin", 42L, …)`）；未登录 401（AuthInterceptor + controller 兜底）；非法 400；不存在 contact 404；校验失败分支在 `update` 之前抛出（单测断言零写语句）。
- I-5 / N-1 / IP-1：单测断言唯一写语句只针对 `expert_contact_location` 且参数化（无 `expert_contact`/`operator_status`/字面量拼接）；IT 断言人工配置不改写 `expert_contact.country`，且模拟画像回填改原 country 后新配置完全不变、`mail_record` 行数不变。
- IP-2/IP-3：`prepare→validate→write` 顺序由单测证明（非法国家/时区/空白 username 时 `update` 次数为 0）；IT 证明被拒写入后前值保持。
- `OperatorStatusWriteSeamGuardTest` 通过：既有行号白名单未失效，本 child 未触碰其覆盖文件。

## 下游接口确认（c2/c3 逐字复用，已冻结）

- `GET /api/mail/contact-locations/countries` → 200，返回目录模型 `{sourceVersion, sourceUrl, defaultPolicy, countries:[{code,labelZh,defaultZoneId,zones:[{id,labelZh}]}]}`（controller 测试以 strict JSON 断言字段集合，索引属性不参与序列化）。
- `GET /api/mail/contact-locations/{contactId}` → 200；未配置 `configured:false` 且不创建占位行；contact 不存在 → 404 `NOT_FOUND`。
- `PUT /api/mail/contact-locations/{contactId}`，body `{countryCode, zoneId:null|string}` → 200，返回已持久化配置；未登录 401、非法 400、不存在 404。
- `ContactLocationView` 字段与语义冻结并逐字实现：`contactId, configured, countryCode?, countryLabel?, zoneId?, effectiveZoneId?, zoneLabel?, usingDefaultZone`；未配置时后三个 zone 字段全为 null 且 `usingDefaultZone=false`；已配置 `usingDefaultZone=(zoneId==null)`、`effectiveZoneId = zoneId ?: 国家默认`。controller 测试对两种形态都做 strict JSON 全字段断言。

## Deviations

- 无功能/契约偏差。执行差异说明两条：
  1. IT 的「进程重启后读回」在集成测试中以**新 `DriverManagerDataSource` + 新 `NamedParameterJdbcTemplate` + 新 service 实例**等价实现（新连接、无内存态），替代人工重启应用（人工验收 A-1 的步骤 3 仍需人工执行）。
  2. 为杜绝误写业务库，IT 在 `@BeforeEach` 用 `SELECT DATABASE()` 断言当前库必须是 `talent_contact_timing_it`（brief 允许的「IT 自保证连接隔离库」做法）。未在测试内硬编码 URL，命令中的 `-Dspring.datasource.url` 仍真实生效（已由该断言与迁移结果证明）。
  3. 时区验收（总计划 G-0 的 ≥2026c 版本线）未通过：本机可得 JDK 11 GA 最高 tzdb 为 2026b，故**不宣称时区验收通过**；实现照常，且目录 418 条 id 在本 JDK 上全部可 `ZoneId.of`（单测断言）。

## Freshness

- Plan identity rechecked: YES（c2821c4c…，未变化）
- Worktree identity rechecked: YES（root/branch/git-dir 与 HEAD `dc5546a`；提交为目标分支 HEAD）
- Reported commits reachable from target branch: YES（`git log --oneline -2`：`dc5546a` → `9b7c04c`）
- Required commands run this invocation: YES（两条命令均在最终实现状态之后执行，退出码 0；提交内容与被测工作树内容一致）
- Historical evidence used only as baseline: YES（基线仅用于对照；未复用任何历史测试结果作为本 child 证据）
- `git add` 仅含 9 个授权文件；`docs/plans/fast/**` 未进入提交（保持未跟踪，由 controller 另行提交）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

## Commit

- `dc5546a6e914c23249a8b7dbadcc977e5df1b7e3` — `feat(fast-p): implement c1`（分支 `fast/2026-10-02-contact-timing-00-master`）
