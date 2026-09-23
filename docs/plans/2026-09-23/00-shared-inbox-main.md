# 共享物理收件箱、独立业务账号与现网迁移：MAIN 总计划

> 状态：待人工审阅；不授权实施或线上 DDL/DML。本文是四份子计划的唯一跨阶段入口：规定执行顺序、接口边界、共享文件串行规则、整体发布和人工验收。各子计划在自己文件清单内定义实现与定向测试；发现冲突须先修订 MAIN 和受影响子计划，禁止执行者自行选一边。
>
> 修订（A2，2026-09-23，人工批准）：阶段 02 触发 M-5「每阶段最多改自己清单文件，超出先修计划」。02 收紧 `listAutoReceiveAccounts()` 为 owner-only 后，计划 01 在 `MailSenderAccountServiceTest.kt` 写的旧收信列表断言必然失效，故按 A1 把该测试文件加入 02 的变更文件清单；02 的授权文件数按 11 计，01/03/04 仍 ≤10。本修订只调整文件计数口径，不改 M-1～M-6 的语义与门槛。

| 阶段 | 权威子计划 | 可独立交付物 | 进入下一阶段门槛 |
|---|---|---|---|
| 01 | [配置与兼容表结构](01-shared-inbox-configuration.md) | 两个 nullable 字段、单层共享收件箱配置/API/UI；生产映射仍全 NULL | DDL/API/UI 定向测试通过；生产不得激活 alias |
| 02 | [单次抓取与业务路由](02-shared-inbox-routing.md) | owner 唯一轮询、物理 UID 去重、To/Cc/回信头路由 | 直寄/歧义/旧数据/附件/游标测试通过 |
| 03 | [机器邮件过滤与退信归属](03-shared-inbox-bounce.md) | 退信按 OUTBOUND 归属；同组 `[self-check]` 在统一入口过滤 | 两入口探针与退信全组测试通过，仍不激活 alias |
| 04 | [LuKai 现网迁移与历史修复](04-lukai-production-migration.md) | 现场备份、精确行分类、QF 配置切换、真实重复与内部探针噪声清理 | 独立核验 + 人工黑盒验收；需单独上线授权 |

## 需求描述

- 可观察结果：现有 `LuKai` / `LuKai_QF` 继续以各自地址发信和展示，但共用一个 IMAP 抓取/游标；同一封专家来信仅出现一次且归正确业务账号；内部 `[self-check]` 探针不进入待处理；已有假重复及探针噪声在可恢复的现场修复中消失。未来新别名只需经核验后配置共享 owner，不加账号硬编码。
- 不得改变：独立物理邮箱的收信行为、任何账号的 SMTP 身份/启停/额度、真实专家回复及其标签/意图/附件、历史 UIDVALIDITY 0 代际语义、退信去重/专家匹配；不能因本计划自动重发或自动回复历史邮件。
- 不在范围：自动识别所有别名域名、BCC 身份猜测、历史人工已处理探针的审计删除、其他账号的数据修复、改退信 DSN 解析器、改邮件认证系统。

## 关键不变量

### Invariant M-1：一物理源、多个业务身份
- Rule：`mail_sender_account.inbound_mailbox_code=NULL` 是独立物理邮箱；Alias 非空只指向单层 owner。物理 IMAP 登录、`mail_inbox_cursor`、`markSeen`、附件 source/transfer 始终 owner；processing/mail_record 的 `sender_account_code`、专家会话与后续回复始终是被唯一确定的逻辑账号。`enabled=false` 不阻断接收，QF 的发信配置不因归属切换而变。(来源: `K-imap-source-vs-business-account`、`K-sender-account-enabled-scope`)
- Applies to：01 的配置/DDL，02 的所有收信与写入，03 的机器邮件，04 的数据迁移。
- Violation consequence：重复待处理、错误账号发信、附件源失效。

### Invariant M-2：新收件物理唯一，旧数据不能猜代际
- Rule：新 processing 以非空 `(mailbox_owner_code, uid_validity, imap_uid)` 保证物理唯一；旧行字段 `NULL` 且 `uid_validity=0` 不回填当前代际。旧行仅在 UID 与非空 Message-ID/From/秒级 ReceivedAt 严格吻合时视为同一邮件。失败 UID 不入连续成功集。(来源: `K-inbound-seen-not-processed-marker`、`K-inbound-processing-write-paths`)
- Applies to：01 DDL、02 判重/游标、04 历史分类。
- Violation consequence：重信或 UID 重用时吞信。

### Invariant M-3：先过滤内部机器邮件，再判专家业务归属
- Rule：普通专家来信的精确收件人路由与 `RECIPIENT_UNRESOLVED` 人工兜底由 02 负责；03 的 `[self-check]` 必须在 `processSingle` 事务写入和收件人未解析人工落库之前，以“同物理组 From + 对应账号完整生成主题”识别。探针返回 `SELF_CHECK_IGNORED`、不落任何业务表；退信由统一 ingest 以唯一 OUTBOUND 证明归属。`Re:`、跨组、错代码、外部同主题邮件不能忽略。(来源: `SenderAccountSelfCheckService.kt:22-27`、`SelfCheckProbeDetector.kt:6-14`、`K-process-single-all-callers`、`K-bounce-collection-ingest-entrypoints`)
- Applies to：02 的 `AutoMailReplyService.processSingle` 入口顺序；03 的探针/退信修改；04 的历史探针分类。
- Violation consequence：自检探针继续进待处理，或专家真实邮件被吞。

### Invariant M-4：生产配置切换与数据清理不可提前
- Rule：01、02、03 均通过各自定向验证、跨阶段回归和独立审核之前，生产 `LuKai_QF.inbound_mailbox_code` 必须保持 NULL；01 的加法 DDL 在生产 Flyway 关闭时手工核对执行且保留备份。04 必须停轮询、重采 IMAP/DB/FK 现场证据、逐 ID 分类后才切换；不能把旧快照数当待删除数。未经单独上线授权，不得操作生产写路径。(来源: `K-plan-quantified-claims-need-grep-receipts`)
- Applies to：部署、维护窗口、回滚、全部子计划交接。
- Violation consequence：旧/新进程混用导致继续双抓、数据丢失或无法回滚。

### Invariant M-5：共享文件串行修改，不能以某子计划的绿测代替总验收
- Rule：`MailSenderAccountService.kt` 在 01 后由 02 增接收 owner 解析；`AutoMailReplyService.kt` 和 `AutoMailReplyServiceTest.kt` 在 02 后由 03 增机器邮件过滤。三处重叠必须按阶段串行，后阶段从前阶段已验证状态继续，不复写旧代码。`MailRecordRepository.kt` 只由 02 改，03 只消费其 OUTBOUND-only 查询；02 同步机械修正 `OperatorStatusWriteSeamGuardTest` 精确行号。每阶段最多改自己清单文件，超出先修计划。单计划验证、全系统验证、人工验收三者均不可互相替代。(来源: `CLAUDE.md:70`)
- Applies to：01～04 的执行和审核。
- Violation consequence：接口漂移、测试假绿或覆盖用户已有修改。

### Invariant M-6：历史内部探针与真实来信分开修
- Rule：04 先按 03 严格探针规则把 `LuKai_QF` 错记的内部自检从普通真实来信中分离。只有 `MANUAL_REVIEW`、无专家与任何依赖、原始头已核验的精确 ID 才可备份后删除；不能改归 LuKai 后仍挂待处理，也不能凭主题 `LIKE` 批删。已 `MANUAL_RESOLVED` 行保留审计。其余真实来信走 04 的物理重复/独有分类。(来源: `K-mailbox-inbound-source-authority`)
- Applies to：04 逐 ID 分类、DML、人工验收。
- Violation consequence：当前噪声残留，或误删已处理/真实来信。

## 现状审计

### 账号、游标与来信的跨计划契约
- Schema/mapping：`V1__create_business_tables.sql:1-25` 账号当前无 owner；`V49__create_mail_inbox_cursor.sql:1-7` 游标按账号；`V120__scope_inbound_uid_by_validity.sql:17-22` processing 当前按逻辑账号/代际/UID 唯一；01 对账号/processing 各只加 1 个 nullable 字段。
- Write paths：账号 `MailSenderAccountService.kt:74-174` 与 repository SQL `:23-90`；游标 `MailInboxCursorService.kt:60`；processing `AutoMailReplyService.kt:1231,1284`、状态 copy/save `UnmatchedInboundMailService.kt:193,238`、`PendingMailOperationService.kt:1522`、repository 重开 UPDATE `:35-47`；04 只对经核验精确 ID 做现场 DML。(来源: `K-inbound-processing-write-paths`)
- Read paths：`MailSenderAccountService.kt:43-60` 当前 `listAutoReceiveAccounts` 除模拟器外全读；`AutoMailReplyService.kt:773-907` 以账号轮询/回填并读游标；`BatchAutoMailReplyService.kt:24,57-79` 全部/联系人检查；`MailboxConversationRepository.kt:433-495` 从 processing 读收件数/账号；附件 worker 按 source 账号回取。(来源: `K-mailbox-inbound-source-authority`)
- Interaction points：01 配置→02 owner 解析；02 物理处理→会话逻辑展示；02 附件源→后续下载；03 机器邮件判断→processing 是否创建；04 配置切换→同一个游标。

### 退信与探针的跨计划契约
- Schema/mapping：`V29__create_bounce_record.sql:1-16` 退信以 `bounce_message_id` 唯一；`mail_record` V15 有业务 `sender_account_code`；探针发件格式在 `SenderAccountSelfCheckService.kt:22-27`，当前 `SelfCheckProbeDetector.kt:6-14` 只比较本轮询账号邮箱。
- Write paths：`BounceCollectionService.kt:33-105` 收集/ingest，`AutoMailReplyService.kt:804-818` UID 退信路径、`BounceBackfillService.kt:30-44` 回填；探针发件由 `DefaultSelfCheckProbeSender.sendProbe`，误处理会走 processing 两个新建 sink。(来源: `K-bounce-collection-ingest-entrypoints`)
- Read paths：`BounceController.kt:19-34`、`BounceRateMonitorService.kt:21` 和 `MailMonitoringService.kt:270-303` 按退信账号读取；`MailboxConversationRepository` 与待处理队列按 processing 读取；`MailAutomationController.kt:45-52` 可经 UID 回填直达 `processSingle`，当前外层探针过滤不能覆盖。
- Interaction points：OUTBOUND 记录→退信归属/监控；同组自检邮件→03 统一过滤→待处理数不增加；UID 回填→相同过滤结果。

### 线上证据及边界
- 只读 IMAP：两账号登录当时看到同 UIDVALIDITY `1782107786` 和同 UID 邮件；授权测试信寄 QF 在两边 UID 434 可见，顶层 To 保留 QF。它证明该样本，不证明 BCC/所有历史邮件，故 02 未识别收件人必须转人工。
- 只读 DB：`id=383` 在 QF 下为 `MANUAL_REVIEW/CONTACT_NOT_FOUND`，From 是 LuKai 更新域地址，主题是 `[self-check] LuKai 1788498000276`；因此当前检测器将它与 QF 的 senderEmail 比较失败。查询 `SELECT sender_account_code,process_status,COUNT(*) FROM inbound_mail_processing WHERE sender_account_code IN ('LuKai','LuKai_QF') GROUP BY sender_account_code,process_status` 的 QF 输出为 `MANUAL_REVIEW 44`、`PROCESSED 2`；查询 `SELECT sender_account_code,process_status,COUNT(*) FROM inbound_mail_processing WHERE subject LIKE '[self-check]%' GROUP BY sender_account_code,process_status` 的 QF 输出为 `MANUAL_REVIEW 23`、`PROCESSED 2`。对 23 条候选的专家/标签/附件/transfer/操作关联汇总为 0。此为调查时快照，04 必须停写后逐 ID 重验，不能据此直接删除。
- 线上运行参数调查时 `SPRING_FLYWAY_ENABLED=false` 且无 `flyway_schema_history`；生产迁移需手工 DDL/备份。项目本地最新 Flyway 为 V133、集成测试尚有钉 131 的断言；01 实施前重查版本占用并修测试。(来源: `K-flyway-version-follows-deploy-order`、`K-flyway-latest-version-test-pin`)

## 实现方案

### G-1：先审 01，再实施/验证 01（M-1、M-2、M-4、M-5）
- 权威文件：`docs/plans/2026-09-23/01-shared-inbox-configuration.md`。完成两字段、单层关系 API/UI 和迁移测试；生产映射保持 NULL。通过本阶段独立核验才允许 02。若 V134 被占用，先改 01 与 MAIN 的版本说明，不可覆盖已应用迁移。

### G-2：实施/验证 02（M-1～M-3、M-5）
- 权威文件：`docs/plans/2026-09-23/02-shared-inbox-routing.md`。保留机器邮件进入 `processSingle` 的入口，严格收件人路由、物理去重、owner ack/cursor/附件源。02 的 OUTBOUND-only Message-ID 查询作为 03 唯一只读依赖；独立账号回归通过后才能开始 03。

### G-3：实施/验证 03（M-1、M-3、M-5、M-6）
- 权威文件：`docs/plans/2026-09-23/03-shared-inbox-bounce.md`。同一组代码分别测试退信归属与 self-check 过滤；`receiveAndAutoReply` 和 `processByUids` 必须统一走探针判断。测试真实外部同主题邮件不会被丢弃。01～03 各自通过后再做一次联合回归；此时仍不得设生产 QF owner。

### G-4：单独批准后，实施/验证 04（M-1～M-6）
- 权威文件：`docs/plans/2026-09-23/04-lukai-production-migration.md`。停写重采→备份→手工等价 DDL→先探针、再真实来信逐 ID 分类→事务内精确修复与 QF owner 切换→只读核对→少量无害新信验收→恢复调度。任何分类不明、依赖不明、唯一键冲突，停下并修计划；不能仅为“消除待处理数字”强删。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `docs/plans/2026-09-23/00-shared-inbox-main.md` | 跨计划契约和执行/发布门禁；本 MAIN 不直接修改产品代码 |

子计划的产品文件集合严格以各自 `## 变更文件清单` 为准；MAIN 不额外授权文件。01/02/03 的共享文件重叠由 M-5 串行约束；04 的线上 DDL/DML 不因本文件存在而获授权。

## 验收标准

- M-1：每个物理 owner 的 IMAP 抓取/游标仅 1 次；两个逻辑账号可各发各显；附件源可下载；QF 启停与发信配置不变。
- M-2：同物理 UID 只产生 1 条新 processing；不同 UIDVALIDITY 不互吞；旧 0 代际无回填；首个失败 UID 之后游标不越过。
- M-3：Owner/Alias 的真探针在批量、手动、队列与 UID 回填均不落业务表；外部同主题/错 From/错代码邮件仍进入业务/人工路径；退信只按唯一 OUTBOUND 归属。
- M-4：01～03 的验收记录早于 QF 配置切换；04 有停写、备份可读、生产模式/Flyway/表结构复核及单独授权记录；所有执行时分类数来自新查询。
- M-5：四份子计划各自文件数 ≤10（A2：02 按 11 计，见上方修订）；共享文件按 01→02→03 串行 diff/复测，02 的精确行号 guard 更新；每阶段独立核验，最后联合机器验证及人工 A-n 均通过。
- M-6：修复前经逐 ID 确认的内部探针待处理修复后为 0，原 383 不在待处理，人工已处理的 366/369 仍可审计；真实信与材料/意图原样可见。验收勾选文件只在人工验收开始时从本节导出。

## 人工验收清单

### A-1：现有两个账号各自显示、只抓一次
- 前置条件：测试或经授权上线环境已按 04 配置 QF→LuKai，自动回复关闭，两个外部测试地址分别寄 updates/QF 各 1 封主题不同的邮件。
- 操作步骤：执行“检查回复”→分别打开两封信的专家会话→再次执行检查。
- 预期结果：updates 来信只显示 LuKai 1 条，QF 来信只显示 LuKai_QF 1 条；第二次新增 0 条；后台只推进 LuKai 的物理游标。
- 覆盖：M-1、M-2、01→02→04。

### A-2：内部自检不再污染待处理
- 前置条件：记录待处理基线；Owner/Alias 共享邮箱，两个账号均可从自检入口发送探针。
- 操作步骤：分别点两账号自检→“检查回复”→按两个自检主题搜索待处理/会话→对其中一封探针 UID 执行回填。
- 预期结果：待处理与会话新增均为 0，回填结果为 `SELF_CHECK_IGNORED`、`recorded=false`，没有新 INBOUND/OUTBOUND 业务邮件。
- 覆盖：M-3、01→02→03。

### A-3：外部邮件不因主题误丢
- 前置条件：从不属于该物理组的外部邮箱给 QF 发一封主题为 `[self-check] LuKai 1788498000276` 的邮件。
- 操作步骤：检查回复→按主题查会话或未匹配待处理。
- 预期结果：新增 1 条真实来信或未匹配人工行，不是 `SELF_CHECK_IGNORED`。
- 覆盖：M-3、真实邮件回归。

### A-4：历史异常、审计与材料
- 前置条件：04 runbook 保存精确 ID 分类、执行前截图和附件/意图清单，数据修复已按授权完成。
- 操作步骤：打开原重复专家会话、待处理页、材料与意图页；查原探针 id=383 及人工已处理 id=366/369。
- 预期结果：重复收件每封只剩 1 条，确认的探针待处理剩余 0；id=383 不在待处理，366/369 审计仍在；每条保留的真实来信附件/意图与 runbook 清单一致。
- 覆盖：M-4、M-6、02→03→04。

### A-5：歧义收件人与退信
- 前置条件：有一封 BCC 到共享邮箱且 To/Cc 不含组成员、无法唯一匹配 In-Reply-To 的新邮件；另有一封引用 QF 已发 OUTBOUND Message-ID 的 DSN。
- 操作步骤：手动检查→打开待处理列表和分别筛 LuKai/QF 的退信列表。
- 预期结果：BCC 邮件只产生 1 条 `RECIPIENT_UNRESOLVED`、0 自动回复；DSN 只给 QF 增加 1 条退信、LuKai 增加 0 条。
- 覆盖：M-1、M-3、02→03。

### A-6：独立账号与未来别名
- 前置条件：测试环境另有 Independent 独立邮箱和一个经两登录相同 UIDVALIDITY/同 UID 头核验的新 Alias2；记录三者 SMTP/限额/启停值。
- 操作步骤：在账号页将 Alias2 的主账号选为其已证明的 owner 并保存；给 Independent 与 Alias2 各寄 1 封不同主题测试信；执行检查。
- 预期结果：Independent 仍由自己 IMAP 抓取且会话账号为 Independent；Alias2 不增加第二次物理抓取且会话账号为 Alias2；两者各新增 1 条收件，发信地址/限额/启停均与操作前一致；无需发布新代码。
- 覆盖：M-1、M-4、01→02→03 的通用性。
