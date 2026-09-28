# Fast-P Child Brief — 01-backend（120 秒阈值：后端查询口径）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md`，identity `commit:3237f07e694565bda5e0e2d6a453fc654d695014`，sha256 `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`。
- 本 child 的批准计划 = 同一份计划文件的「实现方案 / 阶段 1」task 1-3（完整合同，必须先通读全文，特别是「关键不变量」I-1～I-4、`MailOpenTrackingRepository.kt:88-160` 现状审计、验收标准 I-1～I-4）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：none。下游 child `02-ui` 消费本 child 的不变量口径（不消费新 API 字段）。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」两个文件；不得新建文件，不得改动 `src/main/resources/db/migration/**`（计划 I-4：无需迁移），不得改 CSS。
3. 不得修改 `docs/plans/**`（fast-p 证据由控制方提交）、不得写 `docs/plans/fast/**`。
4. 只改 SQL 谓词与状态判定，**不改** `recordSignal`、`reserve`、`setEnabled`、`isEnabled`，不改 Controller/Service/SMTP/邮件发送链（计划「必须保持」①②③）。
5. 提交格式：`feat(fast-p): implement 01-backend`；fast-p 报告/日志排除在该提交之外。
6. 本 worktree 基线（勿按其它工作区或主工作区推测）：
   - `src/main/resources/static/index.html` 缓存键 = `20260926-discovery-repair`（本 child 不碰前端）。
   - 当前 `readPage` 的 OPENED 判据是 `t.first_open_at IS NOT NULL`、NO_SIGNAL 是 `t.id IS NOT NULL AND t.first_open_at IS NULL`，汇总分子是 `COALESCE(SUM(t.first_open_at IS NOT NULL),0)`——本 child 必须全部换成 120 秒候选谓词。
   - Docker/OrbStack：testcontainers 必须 `-Dapi.version=1.40` 且显式 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`，否则 IT 在容器启动阶段直接 error。

## Authorized Files（2）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt` | 单一私有 120 秒截止/候选表达式；`SELECT` 输出 `qualified_signal`；列表、状态过滤、总数、汇总、详情共用同一谓词 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` | MySQL 集成边界 fixture 与跨路径断言 |

## 必须保持不变（计划 I-1～I-4、S-1/S-2 无关项）

- 内部 API 状态键仍为 `OPENED`/`NO_SIGNAL`/`NOT_TRACKED`；`OpenTrackingRow` 字段名/JSON 字段名（`trackingStatus`、`firstOpenAt`、`lastOpenAt`、`messageId`）不得变化，`OpenTrackingSummary` 三个字段名不变。
- `firstOpenAt`/`lastOpenAt` 仍是所有有效像素 GET 的最早/最晚时间（I-3），不做截断、不改写、不伪称真人打开；`NO_SIGNAL` 行仍展示这两个原始时间。
- 发送范围不变：`m.direction = 'OUTBOUND' AND m.send_status = 'SENT' AND m.sent_at IS NOT NULL` + 发送日期/账号过滤；`NOT_TRACKED` = `t.id IS NULL`；未跟踪不进入分母；失败（`sent_at` 为 NULL）、入站、孤儿跟踪行不进入记录与汇总（I-4）。
- 状态与关键词只影响列表与 `totalCount`，不影响汇总（现有 `conditions(filter, includeStatusAndKeyword = false)` 结构保留）。
- 比率 = 分子/分母，分母为 0 时 `null`。
- `detail` 与 `readPage` 必须共用同一 `SELECT` 与同一 `row()`，不得出现第二套时间口径（计划阶段 1 task 2）。

## 精确合同（计划阶段 1 task 1-2 的可执行化）

1. 在 `MailOpenTrackingRepository` 定义**单一私有常量**表达截止与候选，例如 `CUTOFF = "DATE_ADD(m.sent_at, INTERVAL 120 SECOND)"`、`QUALIFIED = "t.last_open_at > $CUTOFF"`（命名可自定，但必须唯一来源、被下列各点复用）。
2. `SELECT` 输出 `CASE WHEN <候选> THEN 1 ELSE 0 END AS qualified_signal`；`row()` 读该列决定 `trackingStatus = "OPENED"`（`t.id IS NULL` → `NOT_TRACKED`，其余 → `NO_SIGNAL`）。
3. 列表过滤：`OPENED` → 候选表达式；`NO_SIGNAL` → `t.id IS NOT NULL AND (t.last_open_at IS NULL OR t.last_open_at <= <截止表达式>)`；`NOT_TRACKED` 不变。
4. 汇总分子：`SUM(CASE WHEN <候选> THEN 1 ELSE 0 END)`；分母仍是已关联跟踪的成功外发计数。
5. `detail` 沿用同一 `SELECT` + `row()`；`firstOpenAt`/`lastOpenAt` 原样映射，不增字段。
6. 边界语义：恰好 120 秒（`= cutoff`）→ `NO_SIGNAL`；`cutoff + 1 微秒` → `OPENED`；`last_open_at` 早于 `sent_at` 或为 NULL → `NO_SIGNAL`；无计时器自动转状态（时间流逝不改变状态，只有再次像素 GET 写入更晚 `last_open_at` 才变 `OPENED`）。

## 必需测试（计划阶段 1 task 3，MySQL 真实运行）

在 `MailOpenTrackingRepositoryIT` 现有用例（并发最早/最晚写入、开关语义、日期边界等必须保留）之上至少新增：

- 边界 fixture：无请求（`last_open_at IS NULL`）、`+119.999999s`、恰 `+120s`、`+120.000001s`、先 `+10s` 后 `+121s`（`first_open_at` 保持 10s）、未跟踪、失败发送、入站、孤儿跟踪行。
- 逐项断言 `ALL`/`OPENED`/`NO_SIGNAL`/`NOT_TRACKED` 四种查询的返回集合与 `totalCount`；`OPENED` 筛选结果、列表状态、详情状态与 `summary.opened` 完全一致；汇总分子/分母/比率；详情与 `firstOpenAt`/`lastOpenAt` 原值。
- 只过时不新增请求仍 `NO_SIGNAL`；随后真实调用 `recordSignal` 写入晚期时间后变 `OPENED`（I-2）。
- 现有用例基线事实：本 worktree `mvn -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true` 基线 = **5 tests / 0 fail**（`baseline/mvn-mysqlit.txt`）。现有 `only successful associated outbound mail counts ...` 的信号写在 `sent_at + 9h`，在新 120 秒口径下仍为 `OPENED`（`OpenTrackingSummary(2,1,0.5)` 保持不变）；`signals atomically preserve min/max` 只断言时间列，不应改。若确有既有断言与新口径冲突，只允许在该授权文件内按新口径修正，并在 execution 报告逐条列出「改了哪条断言、为什么」。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest,SmtpMailDeliveryServiceTest test
```

「编译通过」不算 SQL 边界验证；第一条必须真实连上 MySQL 跑完并给出 surefire 计数。

## 下游接口（02-ui 依赖）

- HTTP/JSON 契约零变化：`GET /api/mail-open-tracking/records`（`status=ALL|OPENED|NO_SIGNAL|NOT_TRACKED`、`keyword`、`pageSize`、`pageOffset`、`senderAccountCode`、日期窗口）与 `GET /api/mail-open-tracking/records/{id}` 的字段名、字段集、分页与竞态语义不变；仅 `trackingStatus` / `summary.opened` 的**内部判定口径**变为 120 秒候选，`OPENED` 的外部文案由 02-ui 负责。

## 交付

- 执行报告写入 `docs/plans/fast/mail-open-tracking-120-second-filter/children/01-backend/execution.md`（报告不进实现提交）。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复授权文件外问题、不得重构相邻代码、不得 push/merge/amend/squash，不得改计划。
