# 01-backend 执行报告（120 秒阈值：后端查询口径）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter/docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md`
- Plan SHA-256: `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`
- Execution ID: `<plan>@8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`
- Execution epoch: NEW
- Approval basis: 本次派发消息（child `01-backend` 授权 2 文件）
- Executor: Impl01Backend
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter`
- Target branch: `fast/mail-open-tracking-120-second-filter`
- Worktree ID: `<root>@fast/mail-open-tracking-120-second-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter`
- Pre-execution code SHA: `3237f07e694565bda5e0e2d6a453fc654d695014`（= child_base_sha）
- Post-execution code SHA / commit: `84560652c3227cf95f50ddd12bd285c54ece96cb`（`feat(fast-p): implement 01-backend`）
- Evidence HEAD: N/A（fast-p 报告由控制方另行提交，未进入实现提交）
- Implementation boundary: `3237f07e..84560652`（2 文件，+140/-5）

## 1. 变更内容

### 1.1 `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt`（+13/-5）

在 `companion object` 内新增**单一私有常量源**，被列表 `SELECT`、`totalCount`、汇总、状态过滤、`detail` 全部复用：

```kotlin
/** I-1: the sole cutoff expression — a request only counts once it is later than sent_at + 120 seconds. */
private const val CUTOFF = "DATE_ADD(m.sent_at, INTERVAL 120 SECOND)"

/** I-1: the sole candidate predicate shared by the list, both filters, the summary and the detail row. */
private const val QUALIFIED = "t.last_open_at > $CUTOFF"
```

`SELECT` 新增派生列（`readPage` 与 `detail` 共用同一个 `SELECT` 常量）：

```sql
SELECT m.id AS mail_record_id, m.expert_contact_id, ec.expert_name,
       t.id AS tracking_id, t.recipient, m.sender_account_code, m.mail_type, m.subject,
       m.sent_at, t.first_open_at, t.last_open_at, m.message_id,
       CASE WHEN t.last_open_at > DATE_ADD(m.sent_at, INTERVAL 120 SECOND) THEN 1 ELSE 0 END AS qualified_signal
FROM mail_record m LEFT JOIN mail_open_tracking t ON t.id = m.open_tracking_id
LEFT JOIN expert_contact ec ON ec.id = m.expert_contact_id
```

`row()` 改为读该列判定状态，Kotlin 侧不再自行比较时间（避免第二套口径）：

```kotlin
val qualified = tracked && rs.getInt("qualified_signal") == 1
trackingStatus = if (!tracked) "NOT_TRACKED" else if (qualified) "OPENED" else "NO_SIGNAL"
```

状态过滤（`conditions(..., includeStatusAndKeyword = true)`）：

```sql
-- OPENED（替换原先的 t.first_open_at IS NOT NULL）
AND t.last_open_at > DATE_ADD(m.sent_at, INTERVAL 120 SECOND)
-- NO_SIGNAL（替换原先的 t.id IS NOT NULL AND t.first_open_at IS NULL）
AND t.id IS NOT NULL AND (t.last_open_at IS NULL OR t.last_open_at <= DATE_ADD(m.sent_at, INTERVAL 120 SECOND))
-- NOT_TRACKED 不变
AND t.id IS NULL
```

汇总分子（替换原先的 `COALESCE(SUM(t.first_open_at IS NOT NULL),0)`）：

```sql
SELECT COUNT(t.id) AS tracked_sent,
       COALESCE(SUM(CASE WHEN t.last_open_at > DATE_ADD(m.sent_at, INTERVAL 120 SECOND) THEN 1 ELSE 0 END),0) AS opened
FROM mail_record m LEFT JOIN mail_open_tracking t ON t.id = m.open_tracking_id
LEFT JOIN expert_contact ec ON ec.id = m.expert_contact_id
WHERE ...
```

未改动（计划「必须保持」①②③）：`recordSignal` / `reserve` / `setEnabled` / `isEnabled`、发送范围条件（`m.direction='OUTBOUND' AND m.send_status='SENT' AND m.sent_at IS NOT NULL` + 日期/账号）、`NOT_TRACKED` 判定、关键词条件、分页/排序、`OpenTrackingRow`/`OpenTrackingSummary` 字段与 JSON 名、`OpenTrackingFilter`、`detail` 的 with 条件与 `row(rs, detail = true)` 调用。

### 1.2 `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt`（+127/-0）

**未修改任何既有断言（0 删除行）**，仅新增 1 个测试方法。既有 5 个用例原样保留并在本次实测中通过：

- `setting upsert is idempotent and reservation commits independently of outer rollback`
- `concurrent first saves create one key without changing old settings`
- `failed independent reservation does not mark caller transaction rollback only`
- `signals atomically preserve minimum and maximum with concurrent requests and stop when disabled`（只断言时间列，不受新口径影响）
- `only successful associated outbound mail counts with stable filtering and Shanghai date boundaries`（信号写在 `sent_at + 9h`，在新 120 秒口径下仍为 OPENED，`OpenTrackingSummary(2,1,0.5)` 无需改动）

#### 新增用例：`one hundred twenty second cutoff decides status list filter summary and detail identically`

Fixture（全部 `sent_at = 2026-09-25T00:00`，账号 `tracking-test`，日期 2026-09-25）：

| mail id | 跟踪行 | 最后请求时间（相对 `sent_at`） | 期望状态 |
|---|---|---|---|
| 9301 | underCutoff | `+119.999999s` | NO_SIGNAL |
| 9302 | exactlyCutoff | `+120s`（恰 120 秒） | NO_SIGNAL |
| 9303 | overCutoff | `+120.000001s` | OPENED |
| 9304 | laterRequest | 先 `+10s`，随后 `recordSignal` 写 `+121s` | 先 NO_SIGNAL → 后 OPENED |
| 9305 | noRequest | NULL | NO_SIGNAL |
| 9306 | —（未关联） | — | NOT_TRACKED |
| 9307 | failedMail | `+121s`，但 `send_status='FAILED'`、`sent_at` NULL | 不出现 |
| 9308 | inboundMail | `+121s`，但 `direction='INBOUND'` | 不出现 |
| 9309 | beforeSent | `sent_at - 1h`（早于发送） | NO_SIGNAL |
| — | orphan | `+121s` 但无 `mail_record` | 不进入列表/分母/分子 |

请求时间全部经生产写路径 `repository.recordSignal(token, now)` 写入（9 次，逐次断言返回 1）。

断言分组：

- **I-2（无计时器自动转状态）**：9304 仅有 `+10s` 请求时（测试真实墙钟已远超 `sent_at+120s`，因 fixture 日期为 2026-09-25）`ALL` 关键字 `TRACK120-LATER` 查询为单行、`trackingStatus = NO_SIGNAL`、`totalCount = 1`、`summary = (6,1,1/6)`、`detail(9304) = NO_SIGNAL`；随后第二次 `recordSignal(+121s)` 才变为 OPENED。
- **列表与状态**：`ALL` 返回 `[9309,9306,9305,9304,9303,9302,9301]`（`sent_at DESC, id DESC`），`totalCount = 7`，逐行状态 `[NO_SIGNAL, NOT_TRACKED, NO_SIGNAL, OPENED, OPENED, NO_SIGNAL, NO_SIGNAL]`，`summary = OpenTrackingSummary(6, 2, 2.0/6.0)`。
- **I-1 边界**：`OPENED` 过滤 = `[9304,9303]`（2 行，全部 OPENED）；`NO_SIGNAL` = `[9309,9305,9302,9301]`（4 行，全部 NO_SIGNAL）；`NOT_TRACKED` = `[9306]`（1 行）。
- **I-4 汇总独立性**：四种状态过滤与关键字 `TRACK120` 查询的 `summary` 均等于 `ALL` 的 `summary`。
- **跨路径一致性**：`ALL` 列表中状态为 OPENED 的 id 集合 == `OPENED` 过滤返回的 id 集合 == `summary.opened`；且对这些 id 逐个 `detail()` 断言状态为 OPENED（筛选结果、列表状态、详情状态、汇总分子四者一致）。
- **I-1/I-3 原始时间**：`detail(9301).firstOpenAt == lastOpenAt == +119.999999s`；`detail(9302) == +120s`；`detail(9303) == +120.000001s`；`detail(9304).firstOpenAt == +10s`、`lastOpenAt == +121s`（翻转状态不重写/不截断原始时间）；`detail(9305)` 两时间均 NULL 且 NO_SIGNAL；`detail(9309)` 两时间均为 `sent_at-1h` 且 NO_SIGNAL；`detail(9306) = NOT_TRACKED`。
- **I-4 排除项**：`detail(9307) == null`、`detail(9308) == null`；`mail_record WHERE open_tracking_id = orphan.id` 计数为 0，同时断言 orphan 跟踪行确实存在且其 `last_open_at > sent_at+120s`（即“若被误计入分母/分子，计数必然变化”的强断言）——实际 `trackedSent = 6`、`opened = 2` 不含 orphan。
- **I-4 账号/日期/分页**：`senderAccountCode='tracking-test'` 得 `totalCount = 7`；`'other-account'` 得 `OpenTrackingSummary(0,0,null)`（分母为 0 → 比率 null）；`pageSize=3, pageOffset=3` 得 `[9304,9303,9302]`。

**覆盖 I-1～I-4 的差距说明（诚实标注）**：`last_open_at` 早于 `sent_at` 的行（9309）在真实数据中不可由像素 GET 产生，此处是按验收标准 I-1「早于 `sent_at` 也为 NO_SIGNAL」构造的判定边界 fixture。要求 120 秒后「只有再次请求才翻转」由 9304 两次 `recordSignal` 覆盖，未使用任何计时器/时间推进。

## 2. 必需命令（本次 invocation fresh 运行）

| # | 命令 | 结果 | 证据 |
|---|---|---|---|
| 1 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | PASS，exit 0 | `BUILD SUCCESS`；`target/surefire-reports/...MailOpenTrackingRepositoryIT.txt` = **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0**（耗时 45.804s，testcontainers 真实 MySQL 8.0.36 容器；基线为 5 tests，新增 1）。同次 Maven `test` 阶段的 exec 插件 JS 用例：`tests 1199 / pass 1199 / fail 0`（与后端改动无关，因 exec 插件绑定 `test` 阶段） |
| 2 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest,SmtpMailDeliveryServiceTest test` | PASS，exit 0 | `BUILD SUCCESS`；surefire：`MailOpenTrackingServiceTest` 4 / 0 / 0，`MailOpenTrackingControllerTest` 3 / 0 / 0，`SmtpMailDeliveryServiceTest` 40 / 0 / 0 = **Tests run: 47, Failures: 0, Errors: 0**（与基线 47/0/0 一致） |

未运行 `mvn clean package`、全量 `mvn test`、formatter/linter（fast-p 子计划不要求，且由控制方统一运行）。本仓无 Kotlin 格式门禁，未发现可运行的仓库级格式检查。

## 3. 不变式 → 证据映射

| 不变式 | 证据 |
|---|---|
| I-1 120 秒严格边界 | `CUTOFF`/`QUALIFIED` 单一常量；IT 断言 `+119.999999s`→NO_SIGNAL、恰 `+120s`→NO_SIGNAL、`+120.000001s`→OPENED，且三者原始时间原样返回 |
| I-2 无计时器自动转状态 | 9301/9302（早期请求，墙钟已过阈值）仍 NO_SIGNAL；9304 第二次 `recordSignal(+121s)` 才转 OPENED |
| I-3 原始时间与推断状态分离 | `detail` 对 `firstOpenAt`/`lastOpenAt` 原值断言（含 9304 先 10s 后 121s）；DB 列与写入路径未改 |
| I-4 统计口径与历史不变 | `trackedSent=6 / opened=2 / rate=2/6`；未跟踪、失败、入站、孤儿均不进入列表与汇总；账号/日期/分页过滤与新谓词共存 |
| 下游接口（02-ui） | `OpenTrackingRow`/`OpenTrackingSummary`/`OpenTrackingFilter` 字段名与 JSON 名零改动；状态键仍 `OPENED/NO_SIGNAL/NOT_TRACKED`；Controller/Service 未改（`MailOpenTrackingControllerTest`、`MailOpenTrackingServiceTest` 通过） |

## 4. 变更文件

- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt` — 单源 120 秒谓词；`SELECT` 输出 `qualified_signal`；列表过滤/总数/汇总/详情共用该谓词
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` — 新增 120 秒边界与跨路径一致性用例（+127/-0）

`git show --numstat` 确认实现提交仅含上述 2 文件（+140/-5），无迁移、CSS、Controller/Service/SMTP、前端或无关测试改动。

## 5. Deviations

- 无。未修改既有断言（IT 新增用例为 0 删除行），未改计划，未新建文件（IT 用例复用既有 `@BeforeEach` fixture 与本地 `mail(...)` 插入模式），未 push/merge/amend。

## 6. Freshness

- Plan identity rechecked: YES（`shasum -a 256` = `8b2f24fa…93c1`，与 brief 一致；文件未被本次修改）
- Worktree identity rechecked: YES（branch `fast/mail-open-tracking-120-second-filter`，git-dir = `.git/worktrees/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter`，提交前后一致）
- Reported commit reachable from target branch: YES（`84560652c3227cf95f50ddd12bd285c54ece96cb` 为当前分支 HEAD）
- Required commands run this invocation: YES（两条，均在最终实现状态之后运行）
- Historical evidence used only as baseline: YES（brief 中 5 tests / 47 tests 仅作基线对照）

## 7. Remaining Blocker

- None.

## 8. Next Action

- `READY_FOR_VERIFICATION` → run `verify-p`。
