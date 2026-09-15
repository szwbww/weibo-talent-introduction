# 收发信箱最新邮件 MySQL 5.7 兼容修复计划

创建：2026-09-08。状态：待审阅/未执行。目标分支应包含收发信箱会话实现（`MailboxConversationRepository`）；本计划不授权直接修改线上数据库或发布。

## 需求描述

收发信箱刷新在不支持窗口函数的线上 MySQL 上可成功返回每位专家的最新邮件和最近来信，响应字段、排序和筛选语义与当前接口一致。

不得改变：来信权威来源、发件权威来源、`(event_at, source_rank, id)` 的最新记录判定、账号范围、空页短路、`latestInbound.processingId` 的真实 processing ID、现有 API 路径/响应字段、任何写入流程。

范围外：升级线上 MySQL、迁移/索引变更、双 SQL 方言分支、缓存、前端、会话表、改动收发件箱其余聚合/分页/时间线 SQL。

## 关键不变量

### Invariant I-1：无窗口函数的最新记录选择

- Rule：`latestMessageByContacts` 和 `latestInboundByContacts` 不得生成 `ROW_NUMBER`、`OVER` 或任何窗口函数；必须使用线上旧 MySQL 可解析的 SQL，且每位专家至多返回一条记录。
- Applies to：`MailboxConversationRepository.latestMessageByContacts`、`MailboxConversationRepository.latestInboundByContacts`。
- Violation consequence：MySQL 5.7/旧 MariaDB 在 `OVER (PARTITION BY ...)` 报 1064，收发信箱刷新整体失败。
- 来源：K-mailbox-groupwise-latest-mysql-compat。

### Invariant I-2：严格且一致的最近记录排序

- Rule：两类最新查询都以 `event_at DESC, source_rank DESC, id DESC` 作为严格总序；候选行仅在不存在同专家、更大的该三元组行时返回。相同时间时，`INBOUND_PROCESSING`（rank 2）优先于 `MAIL_RECORD`（rank 1），同来源以更大 ID 优先。
- Applies to：两条 latest SQL 及其集成测试。
- Violation consequence：列表最新摘要、右侧默认上下文或 `latestInbound` 可能取到旧邮件、跨来源平局不稳定。
- 来源：original。

### Invariant I-3：来源与账号范围不退化

- Rule：最新总邮件仍只合并 OUTBOUND `mail_record` 与 linked `inbound_mail_processing`；最新来信只从 `inbound_mail_processing` 选择，且两条查询都继续受 `accountCodes` 和可选 `accountCode` 限制。不得把历史 INBOUND `mail_record` 作为来信候选。
- Applies to：`rangeUnionSql` 的使用方式、latestInbound 查询、`MailboxConversationRepositoryIT`。
- Violation consequence：同信重复、跨账号泄露或把历史 mail_record ID 伪装为 processing ID。
- 来源：K-mailbox-inbound-source-authority。

### Invariant I-4：只替换追加投影，不改变列表主语义

- Rule：`MailboxConversationService.listConversations` 的调用顺序和 API DTO 保持不变：先聚合并分页，再以本页 `contactIds` 批量补充最新邮件、最新来信、账号和资料数；空页不得执行 `IN (:contactIds)` 查询。
- Applies to：repository 两个 latest 方法；service 不应被本计划改动。
- Violation consequence：专家跨页/计数变化、空 `IN ()` 语法错误或 N+1 查询。
- 来源：K-group-before-pagination、K-empty-list-in-query-guard。

## 现状审计

### `mail_record` / `inbound_mail_processing` 会话读取

- Schema/投影：`rangeUnionSql(false)` 归一化 OUTBOUND `mail_record` 与已关联 `inbound_mail_processing`；事件时间分别为 `COALESCE(mr.sent_at, mr.created_at)` 与 `imp.received_at`，`source_rank` 分别为 1/2。[MailboxConversationRepository.kt:415](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:415)
- 读路径：`MailboxConversationService.listConversations()` 在 summary 页非空后调用两个 latest 批查询，再组装现有 DTO；[MailboxConversationService.kt:101](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:101)。两个查询当前分别在 repository :223、:270，均使用 `ROW_NUMBER() OVER`（:251、:288）。
- 其他读路径：summary/count 走相同 union 但使用 `GROUP BY`/`MAX`；timeline 走 keyset SQL，均不使用窗口函数，属本计划禁止修改范围。
- `mail_record` 写路径（复核）：`ManualOutreachTxHelper`、`MeetingScheduleService`、`ManualExpertMailService`、`ManualReplySendAttemptService`、`AutoMailReplyService` 均可写入；本计划不改其字段或时序。
- `inbound_mail_processing` 写路径（复核）：`AutoMailReplyService` 创建/登记；`UnmatchedInboundMailService` 绑定/处理；`PendingMailOperationService` 标记处理；本计划不改其字段或时序。
- Interaction points：上述写入的 `event_at`、`expert_contact_id`、账号和来源 → union → 两条 latest 投影 → `MailboxConversationService` DTO。修复只能更换“每专家取最大三元组”的读法，不能调整生产者。

### 线上与测试版本差异

- 线上错误精确落在 `OVER ( PARTITION BY ...)`，说明当前连接的服务端不支持窗口函数；需在故障库只读记录 `SELECT VERSION(), @@version_comment` 作为发布证据。
- `mysql-connector-j` 是 8.0.33，[pom.xml:72](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/pom.xml:72)，仅是客户端依赖，不升级服务端能力。
- 现有 MySQL 集成测试连接本地 `mysqlIt` 数据库；仓内 Testcontainers 基线广泛为 `mysql:8.0.36`。因此当前 green 不能覆盖旧线上服务端的窗口函数兼容性。

## 实现方案

### 1. 用 groupwise-max SQL 替换窗口函数

- Governing invariants：I-1、I-2、I-3、I-4。
- 修改 [MailboxConversationRepository.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:223) 的 `latestMessageByContacts` 与 `latestInboundByContacts`，不改方法签名、映射器或 `MailboxConversationService`。
- 在当前归一化 UNION 的候选 `u` 外，加入同一账号范围/同一归一化来源的 `NOT EXISTS` 候选 `newer`：同一 `expert_contact_id` 且 `newer.event_at > u.event_at`，或时间相等而 `newer.source_rank > u.source_rank`，或前两项相等而 `newer.id > u.id` 时排除 `u`。这样只留下每专家的严格最大三元组，且 SQL 不含窗口函数。
- `latestInboundByContacts` 的候选与 `newer` 都必须限制 `source = 'INBOUND_PROCESSING'`；不得从 `mail_record` 推导 processing ID。
- 保留 `contactIds` 非空短路、`accountCodes`/`accountCode` 参数绑定、现有 `utf8mb4_unicode_ci` union 投影及所有选列。不得引入 SQL 字符串插值参数、数据库版本分支、N+1 查询或新索引。

### 2. 补齐兼容与平局语义集成证据

- Governing invariants：I-1、I-2、I-3、I-4。
- 修改 [MailboxConversationRepositoryIT.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt:70)。扩展既有混合记录用例，显式构造同一专家、同一时间的 OUTBOUND 与 INBOUND，再构造同来源不同 ID；断言 latestMessage 选择 rank 2 / 最大 ID，latestInbound 返回真实最大 processing ID。
- 增加源码级兼容断言或等价可执行断言，确保两个 latest 路径不再含 `ROW_NUMBER`/`OVER`；语义断言仍必须通过真实 MySQL `mysqlIt`，不能仅靠字符串检查。
- 测试账号范围收窄：同专家在另一个账号存在更晚记录时，`accountCode` 限制下不得越界选取；保留空 contactIds 返回空且不发 SQL 的现有行为。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 修改两条 latest 查询为 MySQL 5.7 兼容 groupwise-max SQL。 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 增加混合来源同时间/同来源 ID/账号范围/无窗口函数回归证据。 |

## 验收标准

- I-1：仓库两个 latest 查询无 `ROW_NUMBER`、`OVER`；在线上同版本数据库执行刷新请求不再报 1064。
- I-2：同时间跨来源时选 `INBOUND_PROCESSING`；同来源同时间时选最大 ID；不同时间时选最大 event_at。
- I-3：INBOUND `mail_record` 永不进入最新来信；另账号更新消息不影响受限账号的结果；`latestInbound.processingId` 是真实 processing 行 ID。
- I-4：空 summary 页不调用 latest；非空页仍是每类一条批查询；summary 计数、分页、timeline 和 API JSON 字段保持原样。
- 机器命令：
  1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test`
  2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
  3. 在与线上同版本的只读/预发 MySQL 上执行刷新接口及 `SELECT VERSION(), @@version_comment`；记录版本、HTTP 状态和无 SQL 1064。

## 人工验收清单

### A-1：线上兼容刷新

- 前置条件：预发或线上同版本 MySQL，至少一位专家有可见收发件记录；记录 `SELECT VERSION(), @@version_comment` 结果。
- 操作步骤：1. 刷新收发信箱；2. 切换全部、待专家回复、待处理；3. 打开一位专家的会话；4. 切换一个发件账号后再次刷新。
- 预期结果：每次请求 HTTP 200；不出现 `PreparedStatementCallback`、`ROW_NUMBER`、`OVER` 或 SQL 1064；最新邮件、最近来信、收/发/待处理计数均显示；账号切换后不显示其他账号的最新记录。
- 覆盖：I-1、I-3、I-4。

### A-2：平局与既有入口回归

- 前置条件：测试专家同一秒存在一条 OUTBOUND 和一条已关联 inbound processing，另有同来源较大 ID 的消息；同时准备一封历史 INBOUND `mail_record`。
- 操作步骤：1. 打开该专家会话；2. 刷新列表两次；3. 标记一封待处理来信；4. 从旧邮件记录入口打开历史邮件。
- 预期结果：列表最新摘要稳定选择 processing（同秒跨来源 rank 2）；同来源选择较大 ID；最近来信指向真实 processing；历史 INBOUND mail_record 不增加来信统计；标记处理和旧入口行为不变。
- 覆盖：I-2、I-3、I-4。

## 自检

- 文件数：2；子系统：1（会话 JDBC 读查询与其集成测试）。
- 无 schema、写路径、前端或 API 变更；无未列文件授权。
- 知识输入已复核：K-group-before-pagination、K-mailbox-inbound-source-authority、K-empty-list-in-query-guard；新增 K-mailbox-groupwise-latest-mysql-compat。
