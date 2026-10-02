# Execution Report — c2（contact-timing-02-recommendation）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master/docs/plans/2026-10-02/contact-timing-02-recommendation.md`
- Plan SHA-256: `4d39ec1269cab2f7e73b8b91339c1970c376167149b655fb2764f12bf98aeb5b`（执行前后各重算一次，未变化）
- Brief SHA-256（环境绑定入口）: `897d4e07cdd9fad765787f6568aa9a761bb8bdef42e057b9b68c794da77f3d50`
- Execution ID: `…/docs/plans/2026-10-02/contact-timing-02-recommendation.md@4d39ec12…`
- Execution epoch: NEW（旧 execution.md 为占位模板，无同身份历史）
- Approval basis: 本次调用（c2 child brief + 已批准的 02 计划）
- Executor: C2Impl（fast-p 子代理）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- Target branch: `fast/2026-10-02-contact-timing-00-master`
- Worktree ID: `…-fast-2026-10-02-contact-timing-00-master@fast/2026-10-02-contact-timing-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- Pre-execution code SHA: `a1217b3a2995da6a725f8205ea4238adb1a1331c`（c1 证据提交后的工作树 HEAD；brief 绑定的 child_base_sha `dc5546a6e914c23249a8b7dbadcc977e5df1b7e3` 即其父提交，二者内容对 c2 等价）
- Post-execution code SHA: `ab8e4cb82bace355c06412d260a42b5fbe6cce74`
- Evidence HEAD: N/A（无单独证据提交；本报告为未提交产物，按 brief 不进入实现提交）
- Implementation boundary: `a1217b3..ab8e4cb`（8 个文件，1755 insertions / 19 deletions）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 读取与投影（I-1/I-2/I-4/I-6） | IMPLEMENTED | `ExpertContactLocationService.kt`、`ExpertContactLocationModels.kt`、`ExpertContactLocationServiceIT.kt` | 单测「timing reads only the whitelisted projection…」（逐字 SQL 六列投影、无 body/subject/from_email/process_*、`IN (:accountCodes)`、180 天边界参数、`LIMIT 1001`）；IT 7 项（来源/去重/模拟器/绑定/边界/截断/只读）全绿 |
| T-2 纯计算器（I-2/I-3/I-4/I-5） | IMPLEMENTED | `ReplyTimeRecommender.kt`（新增）、`ReplyTimeRecommenderTest.kt`（新增） | 21 项单测：固定 now 的印度/纽约（夏冬）/尼泊尔/Lord Howe/伦敦缺口与重叠/Troll 压缩重试、48 桶与平滑/先验/混合的逐点断言、日上限=2、30 天衰减=1/2、E 与 alpha 老化下降、未来样本排除 |
| T-3 只读接口与响应（I-1～I-6） | IMPLEMENTED | `ExpertContactLocationController.kt`、`ExpertContactLocationService.kt`、`ExpertContactLocationModels.kt`、controller/service 测试 | controller 测试 13 项（strict JSON 全字段冻结、两 mode 形态、404、401、零 save）；service 测试 19 项 |
| T-4 跨路径测试（I-1～I-6 / IP-1～IP-4） | IMPLEMENTED | `ExpertContactLocationServiceIT.kt` | IT 14 项（含 7 项 c2 跨路径用例）；EXPLAIN 与 1001 行读取耗时已记录 |
| A-1/A-2/A-3 人工验收 | NOT RUN（人工步骤） | — | 需在运行中的应用与界面执行；`service`/`HTTP` 层等价证据已由上列自动化用例覆盖，人工清单留待人工验收 |

## Commands

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ReplyTimeRecommenderTest,ExpertContactLocationServiceTest,ExpertContactLocationControllerTest` | PASS | exit 0 / `BUILD SUCCESS`；`ReplyTimeRecommenderTest` 21/0/0、`ExpertContactLocationServiceTest` 19/0/0、`ExpertContactLocationControllerTest` 13/0/0（合计 53，Failures 0、Errors 0、Skipped 0）；同命令内 `exec-maven-plugin` 的 `node --test src/test/js/*.test.js` 1273 pass / 0 fail，两项 `node --check` 通过 |
| 2 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'` | PASS | exit 0 / `BUILD SUCCESS`；`ExpertContactLocationServiceIT` 14/0/0（c1 基线 7，c2 新增 7）；stdout 记录 `c2 EXPLAIN plan: [{table=inbound_mail_processing, type=ALL, possible_keys=uk_inbound_mail_processing_uid_validity,fk_inbound_mail_processing_contact, key=null, rows=1001, filtered=11.11, Extra=Using where; Using filesort}]`、`c2 timing read over 1001 rows: 19 ms`（同命令在修复后、提交前的另一次运行记录为 29 ms，同量级） |

基线对照（brief 给定，非本次证据）：c1 终态 ServiceTest 10/0/0、ControllerTest 7/0/0、IT 7/0/0；`ReplyTimeRecommenderTest` 当时不存在。两次运行的差值即为 c2 新增：Recommender +21、Service +9、Controller +6、IT +7。

补充证据（只读核对）：

- `-Dspring.datasource.url` 真实传播：IT `@BeforeEach` 的 `SELECT DATABASE()` == `talent_contact_timing_it` 断言通过（落默认库会立即失败）。
- 隔离库运行后状态：`inbound_mail_processing` 0 行、本测试前缀 `mail_record` 0 行、`expert_contact_location` 0 行、`expert_contact` 0 行、`mail_sender_account` 1 行（仅迁移种子 `SIMULATOR_NOOP`）、`campaign` 1 行（V16 模拟器 campaign）。fixture 全部自清理。
- 业务库未被触碰：`talent_introduction.flyway_schema_history` version='146' 计数 0，`talent_introduction.expert_contact_location` 不存在。
- EXPLAIN 显示本数据量下优化器选择全表扫描（`type=ALL`、`key=null`、`rows=1001`）；按计划本轮不预加索引，只记录证据（1001 行读取 19 ms / 29 ms 两次实测同量级）。

## Changed Files

| # | 路径 | 操作 |
|---:|---|---|
| 1 | `src/main/kotlin/.../mail/service/ExpertContactLocationModels.kt` | 扩展 c1（+48 行：`TimingMode`、`TimingRecentSampleView`、`TimingRecommendationView`、`ContactLocationTimingView`） |
| 2 | `src/main/kotlin/.../mail/service/ExpertContactLocationService.kt` | 扩展 c1（+175/-3：`timing`/`timingAt`、样本查询与去重、响应装配；构造器新增 `MailSenderAccountRepository`） |
| 3 | `src/main/kotlin/.../mail/controller/ExpertContactLocationController.kt` | 扩展 c1（+14/-3：新增只读 `GET /{contactId}/timing`） |
| 4 | `src/main/kotlin/.../mail/service/ReplyTimeRecommender.kt` | 新增（261 行：纯计算器 + 参数常量） |
| 5 | `src/test/kotlin/.../mail/service/ReplyTimeRecommenderTest.kt` | 新增（394 行，21 用例） |
| 6 | `src/test/kotlin/.../mail/service/ExpertContactLocationServiceTest.kt` | 扩展 c1（+311：9 个 timing 用例 + 账号/来信行 mock 助手） |
| 7 | `src/test/kotlin/.../mail/controller/ExpertContactLocationControllerTest.kt` | 扩展 c1（+177：6 个用例，含 c1 的 R-1 补覆盖） |
| 8 | `src/test/kotlin/.../mail/service/ExpertContactLocationServiceIT.kt` | 扩展 c1（+394/-3：7 个跨路径用例 + fixture/cleanup/账号种子） |

`git show --stat HEAD`：8 files changed, 1755 insertions(+), 19 deletions(-)，全部落在授权清单内。未触碰 c1 的 `ExpertContactLocationCatalog.kt`、`V146` 迁移、`contact-country-timezones.json`，也未新增第九个文件（`git status --porcelain` 中 src/ 与 pom.xml 无其他改动）。

## 不变量与 IP 覆盖

- **I-1 样本来源/身份/范围**：SQL 只读 `inbound_mail_processing`（`expert_contact_id = :contactId` + `sender_account_code IN (:accountCodes)`），账号集合来自 `findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)`；不读 INBOUND `mail_record`、不读正文、不按 `process_status/process_reason` 过滤。证据：单测断言投影列与过滤列；IT `timing counts only linked processing rows and ignores inbound mail records`（同 message-id 的 INBOUND mail_record 与仅存在于 mail_record 的来信均不计入，sampleCount=3）、`simulator rows never enter the sample set`（sampleCount=1）、`timing deduplicates one message id across accounts and never merges unknown identities`（跨账号同 message-id 合 1；owner=NULL/代际=0/无 message-id 的历史行各算 1，合计 4）。
- **I-2 时间先变 Instant**：`received_at` 用 `getObject(LocalDateTime)` 取出后由代码显式 `atZone(Asia/Shanghai).toInstant()`，再投影目标 ZoneId；样本按其**当时**当地规则落桶。证据：IT `timing is read only and reprojects the same samples after a zone change`（北京 01:15 固定，巴西 14:15 → 马瑙斯 13:15，同一 Instant）；单测 `samples are bucketed by their own local rules across a DST change`（11-02 18:00Z 在纽约为 13:00，不得按固定偏移落 14:00 桶）。未使用系统默认时区或固定国家偏移。
- **I-3 三种结果**：未配置 → `configured=false` + `recommendation=null`（单测/controller/IT）；D<3 → `WORK_HOURS` 完整 08:00–17:00（单测 0/1/2 日与同日 20 封）；D≥3 → `REPLY_PATTERN` 四桶峰值（单测 3 日 14:15 → 13:00–15:00 且平局取早；5 日 23:45 → 22:30–00:30 跨午夜、越出工作时间；IT 3 日 → 13:00–15:00 / 北京次日 00:00–02:00）。
- **I-4 上限与衰减可复现**：窗口 180 天、`LIMIT 1001`、超出记 `historyTruncated`（单测 1001 行→1000/截断、1000 行→不截断；IT 1001 行→sampleCount 1000、replyDayCount 1 证明丢弃的是最旧行）；日上限 2（单测 2/100/1000 封同日同瞬时直方图总和=2.0）；30 天衰减=0.5（单测 `decayWeight(30.0)` 与直方图总和）；E 与 alpha 随老化下降（单测）；同输入同输出（单测 `identical inputs return identical outputs`）；未来样本排除（单测 + IT 边界用例）。参数常量全部集中在 `ReplyTimeRecommender` 内。
- **I-5 下一段完整可用窗口**：候选起点用专家当地今天、已过 now 则加一天；端点均为完整 ISO offset date-time（跨日不丢日期）；DST 缺口顺延（伦敦 2026-03-29）、重叠取较早 offset（伦敦 2026-10-25）、整个区间被压掉时顺延下一当地日且不伪造（Troll 2026-03-29 → 03-30）。证据：单测三条 DST 用例 + `recommended start is never before now in any zone`（10 个时区起点不早于 now、区间为正、两投影同 Instant）；IT 断言 localStart 不早于 now 且 Beijing 侧日期 = 当地 +1 天。
- **I-6 读取无副作用**：单测断言 timing 全程零 `update`、账号仓库只调用 `findAllByAccountCodeNot`（`verifyNoMoreInteractions`）；IT `timing is read only and reprojects the same samples after a zone change` 对比读取前后 `expert_contact_location`/`inbound_mail_processing`/`mail_record` 行数不变。
- **IP-1**：新来信入库（IT fixture 插入形如收信 sink 的 processing 行）后 GET 立即计入；同 message-id 在两个业务账号只计一次。**IP-2**：人工绑定未匹配记录（`expert_contact_id` 由 NULL → contact）后样本 1→2；人工解决（置 PROCESSED/MANUAL_RESOLVED）后仍 2；撤销解决走**真实** `InboundMailProcessingRepository.reopenManualResolved`（断言返回 1）后仍 2 —— 处理状态不是学习资格门槛，且不改变样本身份/时间。**IP-3**：只在 processing 计数（不叠加 INBOUND mail_record）；历史 owner=NULL/代际=0 的行不凭空合并。**IP-4**：改时区后下一 GET 用新时区重投影同一批 Instant，且 01 的配置 GET 仍返回所选时区（`America/Manaus`、`usingDefaultZone=false`）。
- **N-1 / 01 语义保持**：三个既有路由与 `ContactLocationView` 字段/语义未改；PUT 响应未变；timing 为新增只读 GET。c1 的 10 个原有用例与 controller 的 7 个原有用例全部保持绿色（ServiceTest 19 = 10+9、ControllerTest 13 = 7+6）。

## 下游接口确认（c3 逐字复用，已冻结）

- 路由 `GET /api/mail/contact-locations/{contactId}/timing`（新增只读；PUT/GET 配置/countries 三个路由与语义不变）。登录边界沿 01：`/api/**` 由 AuthInterceptor 兜底 401（controller 测试断言匿名 401 且不触达 service）；不存在 contact → 404 `NOT_FOUND`（noSuchElement → GlobalExceptionHandler）。
- 响应逐字冻结（controller 测试以 strict JSON 断言，无多余字段）：

```text
{ location: {contactId, configured, countryCode, countryLabel, zoneId, effectiveZoneId, zoneLabel, usingDefaultZone},
  recommendation: null | {
    mode: "WORK_HOURS" | "REPLY_PATTERN",
    localStart, localEnd, beijingStart, beijingEnd: ISO_OFFSET_DATE_TIME（完整日期 + offset，跨日不丢日期）,
    sampleCount: int, replyDayCount: int, historyDays: 180, historyTruncated: boolean,
    recentSamples: [{receivedAtBeijing, receivedAtLocal}]（≤8，只含时刻，无正文/主题/邮箱/内部 id）,
    calculatedAt: ISO_INSTANT } }
```

- 字段语义唯一：无 confidence/preferredHour/bestHour；当地时区 id 在 `location.effectiveZoneId`；未配置所在地时 `recommendation=null`（不猜时区）。
- 模式序列化为枚举名（Jackson 默认），controller 测试对 `"WORK_HOURS"`/`"REPLY_PATTERN"` 两种形态都做了断言（后者含 `historyTruncated=true` 与空 `recentSamples` 的非默认形态）。
- `ExpertContactLocationService` 现需注入 `MailSenderAccountRepository`（追加为第三个构造参数），并新增只读入口 `timing(contactId)` / `timingAt(contactId, now)`；`ContactLocationView` 与 `get/save` 签名未变。
- c1 的 RECORD_ONLY R-1（`configured=true` 且使用国家默认时区的 HTTP JSON 形态）在本 child 顺带补齐：controller 测试 strict JSON 断言 `zoneId=null / effectiveZoneId=America/Sao_Paulo / usingDefaultZone=true`。

## Deviations

1. **`timingAt(contactId, now)` 显式时刻入口（新增公开方法）**：计划要求「服务在每个请求开始获取一次 `Instant.now()` 并传入」——`timing(contactId)` 正是该入口；额外的 `timingAt` 只把同一 now 显式化，供 service 单测与 IT 传入固定时刻以证明 I-4 的可复现性与 180 天边界（不引入 Clock Bean，计算器本身仍不读系统时钟）。未改变 HTTP 契约。
2. **物理键去重的 IT 可观测性受限**：V134 的唯一键 `uk_inbound_mp_owner_uid (mailbox_owner_code, uid_validity, imap_uid)` 使两行具有相同物理身份在库层不可能共存，故 IT 无法构造该场景；该路径由单测（mock 行）覆盖（有效物理键相同、message-id 不同 → 合并为 1），IT 覆盖的是库里实际可能出现的形态（跨账号同 message-id；owner=NULL 或代际=0 的历史行不合并）。
3. **IT fixture 的 now 必须取秒级**：`inbound_mail_processing.received_at` 是 DATETIME（无小数秒），带纳秒的 now 入库会被舍入，使「恰好第 180 天」的包含性断言不确定。IT 统一 `Instant.now().truncatedTo(SECONDS)`，并在注释中说明。
4. **人工验收 A-1/A-2/A-3 未执行**：属人工步骤（需运行中的应用与界面）；其数据路径已由 service/controller/IT 层等价用例覆盖，人工清单（含 manual fixture SQL 的等价实现，见 IT `insertInbound`）留待人工验收执行。
5. **时区验收（总计划 G-0 ≥2026c）未通过**：本机可得 JDK 11 GA 最高 tzdb 为 2026b，故**不宣称时区验收通过**；DST 用例的时间点按 2026b 规则书写。EXPLAIN 显示本数据量下为全表扫描（无新增索引，符合计划「本轮不预加索引」）。
6. 计划中「前端消费者」不在本 child 范围：未修改任何前端文件（授权清单亦无）。

## Freshness

- Plan identity rechecked: YES（`4d39ec12…`，执行前后一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与提交 `ab8e4cb`；为目标分支 HEAD）
- Reported commits reachable from target branch: YES（`git log --oneline -3`：`ab8e4cb` → `a1217b3` → `dc5546a`）
- Required commands run this invocation: YES（两条命令均在 `ab8e4cb` 提交后于同一实现状态上全新执行，退出码 0）
- Historical evidence used only as baseline: YES（c1 的 10/7/7 计数仅用于差值对照；未复用历史测试结果作为 c2 证据）
- `git add` 仅含 8 个授权文件；`docs/plans/fast/**` 未进入提交（`ledger.md` 的改动由 controller 产生，`children/c2/` 保持未跟踪）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

## Commit

- `ab8e4cb82bace355c06412d260a42b5fbe6cce74` — `feat(fast-p): implement c2`（分支 `fast/2026-10-02-contact-timing-00-master`）
