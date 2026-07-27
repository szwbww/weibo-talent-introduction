# 入站收信改用 UID 游标 + 一次性补抓漏邮件（UID 22）

> 方案 A（UID 游标，不再依赖 SEEN）+ 一次性补抓被漏掉的 UID 22。
> 起因：专家 Sandra（sandrartorres2022@gmail.com）18:41 的回复（INBOX UID 22）在被「检查回复」拉取前已被标记 `\Seen`，而 `fetchUnread()` 只拉 `!SEEN`，导致永久跳过，收发信箱看不到。

## 需求描述

- **可观察结果**：
  1. 入站「检查回复」不再因为邮件已被标记已读（用户在手机/网页端打开等）而漏掉专家回复；只要邮件 UID 高于该账号上次处理的游标就会被处理。
  2. 一次性把已漏掉的 UID 22 补进系统，使其出现在收发信箱（写入 `mail_record` INBOUND + `inbound_mail_processing`），并走正常入站流水线（联系人匹配 / 状态推进 / 自动回复判定）。
- **不可改变**：
  - 入站去重的唯一真相源仍是 `inbound_mail_processing(sender_account_code, imap_uid)`（见 I-1）。
  - 退信采集 `BounceCollectionService.collectBounces()` 现有行为（仍走 `fetchUnseenMessages` + `bounce_record` 按 `bounceMessageId` 去重）不变（见 I-6）。
  - 会话状态仍只能经 `ConversationStateService.transition(...)` 流转（本计划不直接改状态机）。
  - self-check / 退信 / DMARC 三类特殊邮件的判定与「不入 `mail_record`」行为不变。
- **超出范围（显式延后）**：
  - 退信路径 `fetchUnseenMessages()` 改为游标化（仍保留 SEEN 过滤，独立计划评估）。
  - 「毒邮件」（持续抛异常的单封邮件）自动跳过 / 重试上限 `retry_count` 自动推进游标的策略（本计划只记录并告警，靠补抓端点作为人工兜底，见「已知限制」）。
  - 前端 UI 改动（补抓与游标重置仅提供后端端点，用 curl/postman 调用）。
  - RabbitMQ 异步路径 `publishAutoReply` 的语义不变（它最终仍调用 `receiveAndAutoReply`，自动继承游标逻辑）。

## 关键不变量

### Invariant I-1: 去重真相源是 DB 表，游标只是下界优化
- Rule：判断一封邮件「是否已处理」**必须**查 `inbound_mail_processing(sender_account_code, imap_uid)`；UID 游标只能用于**缩小拉取范围（下界）**，绝不能单独作为「已处理」的依据。对游标下方 UID 的重新拉取（如补抓）仍必须由该表去重，而不是被游标静默跳过。
- Applies to：`AutoMailReplyService.processSingle`（已有 `findBySenderAccountCodeAndImapUid` 去重）、新增的游标拉取逻辑、新增的补抓逻辑。
- Violation consequence：若以游标当「已处理」标记，补抓 UID 22（在游标下方）会被错误跳过；或反之重复处理。
- 来源：original（佐证 K-backfill-readonly-inbound：回填只能走已声明的写路径，不得另立去重事实）

### Invariant I-2: SEEN 标志不得作为业务回复的拉取/处理依据
- Rule：业务回复的拉取**不得**再用 `Flags.Flag.SEEN` 过滤。`markSeen()` 仅可作为「降噪 / 已处理可视化」的副作用保留，不参与是否拉取/处理的判定。
- Applies to：`ImapMailReceiveService` 新增的游标拉取方法、`AutoMailReplyService.receiveAndAutoReply` 循环（保留现有 markSeen 调用，但不依赖其结果）。
- Violation consequence：复现本 bug——邮件被外部标记已读后永久漏处理。

### Invariant I-3: 游标只推进到「已终结处理且无更低未处理缺口」的最高 UID（不丢邮件）
- Rule：一个批次处理完后，游标只能推进到「按 UID 升序排列、从批次最小 UID 起连续都被终结处理（未抛异常）」的最高 UID。任一封抛异常的邮件，其 UID 及以上的 UID 都不得越过——游标停在它下方，下轮重试。
- Applies to：新增 `MailInboxCursorService.advance(...)` 与 `receiveAndAutoReply` 的逐封 try/catch 收集 handledUids。
- Violation consequence：若无条件推进到 max(fetchedUid)，一封解析失败的邮件会被永久跳过（与本 bug 同类的丢信）。

### Invariant I-4: UIDVALIDITY 守卫
- Rule：游标记录必须同时存储拉取时 folder 的 `UIDVALIDITY`。下次拉取若发现当前 `UIDVALIDITY` 与存储值不一致，则视存储 `last_uid` 失效，从 0 重新全量扫描（仍由 I-1 去重），并 WARN 日志，**绝不**沿用旧 UID。
- Applies to：`ImapMailReceiveService` 拉取方法返回当前 `uidValidity`；`MailInboxCursorService` 在 mismatch 时重置。
- Violation consequence：邮箱重建/迁移导致 UID 复用时，旧游标会让新邮件被误判为「已过」而漏掉。

### Invariant I-5: 补抓走同一条入站流水线
- Rule：一次性补抓指定 UID 必须调用现有 `processSingle(account, received, skipImapAck=false)`，由它写 `inbound_mail_processing` + `mail_record`（或对应的人工复核记录），使补抓邮件在收发信箱中与正常入站邮件完全一致；重复补抓由 I-1 去重为 `duplicate`。
- Applies to：新增 `AutoMailReplyService.processByUids(...)` 与 `ImapMailReceiveService.fetchByUids(...)`。
- Violation consequence：另起旁路写库会绕开去重 / 附件 / 状态推进，产生不一致或重复记录。

### Invariant I-6: 退信采集路径隔离
- Rule：本计划只改业务回复拉取（原 `fetchUnread`）；`fetchUnseenMessages()`（退信 MIME 解析用）保持 SEEN 过滤与现有签名不变。业务循环对 self-check/bounce/dmarc/正常回复仍调用 `markSeen`，从而 `collectBounces()`（在业务循环之后执行）观察到的「未读集合」语义不变。
- Applies to：`ImapMailReceiveService.fetchUnseenMessages`（不改）、`BounceCollectionService`（不改）。
- Violation consequence：误改会导致退信重复采集或漏采。

## 现状审计

### Store: IMAP INBOX（外部，非 DB）
- 当前拉取（`ImapMailReceiveService.kt`）：
  - `fetchUnread(account, max)`（:21-42）：`filterNot { it.flags.contains(SEEN) }.take(max)` —— **本 bug 根因**。READ_WRITE 打开。
  - `fetchUnseenMessages(account, max)`（:48-67）：同样 `filterNot SEEN`，READ_ONLY，返回 detached `MimeMessage`，供退信 DSN 解析。**本计划不改**（I-6）。
  - `markSeen(account, uid)`（:69-83）：READ_WRITE，`getMessageByUID(uid).setFlag(SEEN,true)`。
  - 已用 `UIDFolder`：`uidFolder.getUID(message)`、`getMessageByUID(uid)` 均已在用，`getMessagesByUID(start,end)` / `getUIDValidity()` 为同一接口能力，可直接用。
- Read paths（谁调用拉取）：
  1. `AutoMailReplyService.receiveAndAutoReply`（:547）→ `fetchUnread`。**要改为游标拉取**。
  2. `BounceCollectionService.collectBounces`（:33）→ `fetchUnseenMessages`。不改。
  3. 测试 `AutoMailReplyServiceTest`：约 20 处 `Mockito.when(receiveService.fetchUnread(account,5))...`（需机械改为新方法）。

### Store: `inbound_mail_processing`（MySQL，去重真相源）
- Schema（`V5__create_inbound_mail_processing.sql`）：`UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid)`；含 `process_status`、`process_reason`、`retry_count`、`last_error`。
- Write paths：
  1. `AutoMailReplyService.processSingle` 各分支：`confirmManualReviewWithBody`(:78)、`confirmProcessed`(:146/:155)、正常回复保存(:173 mail_record + 后续 inbound 行)、以及 :877/:912 两处 `inboundMailProcessingRepository.save(...)`。
  2. `BounceBackfillService`（退信回填，K-backfill-readonly-inbound 标记：只读此表，不得改写历史行）。
- Read paths：
  1. `processSingle` 入口 `findBySenderAccountCodeAndImapUid`(:70) —— 去重判断。
- Interaction points：游标拉取会把「游标以上、含已 SEEN」的 UID 都拿来；其中已在本表有行的（正常回复）→ 去重为 duplicate；self-check/bounce/dmarc **不写本表**（见下），靠游标推进越过它们。

### Store: `mail_record`（MySQL，收发信箱可见性来源）
- INBOUND 行只由 `processSingle` 写（:114/:173/`saveMailRecord` :628）。补抓必须经此路径才能在收发信箱出现（I-5）。
- self-check/bounce/dmarc 三类在 `receiveAndAutoReply` 循环里 `return@forEach`，**不写 mail_record、也不写 inbound_mail_processing**（仅 markSeen / 写 bounce_record）。

### Store: 新表 `mail_inbox_cursor`（本计划新增）
- 无现状。参考既有 `discovery_source_cursor`（`V32`）的单表游标模式。

### 关键交互点小结
- IP-1：原 `fetchUnread`（写路径无，纯读 IMAP）被 `receiveAndAutoReply` 消费 → 改游标后，self-check/bounce/dmarc 这些「不入去重表」的邮件靠游标推进越过；若批次中存在更低 UID 的失败邮件造成游标停滞（I-3），这些特殊邮件会被重复拉取，但它们各自幂等（self-check 丢弃、bounce 按 messageId 去重、dmarc 重复 ingest 影响可忽略）。需在验收中覆盖。
- IP-2：补抓 UID 22 写 `inbound_mail_processing` + `mail_record` → 收发信箱列表读 `mail_record` 立即可见。

## 实现方案

### 阶段 1：游标存储（DB + 领域 + 仓库 + 服务）

**Task 1.1 — 新建迁移 `V49__create_mail_inbox_cursor.sql`**（遵 I-1/I-4）
- 表结构：
  - `id BIGINT AUTO_INCREMENT PK`
  - `sender_account_code VARCHAR(64) NOT NULL`
  - `uid_validity BIGINT NOT NULL`
  - `last_uid BIGINT NOT NULL DEFAULT 0`
  - `updated_at DATETIME NOT NULL`
  - `UNIQUE KEY uk_mail_inbox_cursor_account (sender_account_code)`
- 不预置任何行（首次按账号惰性创建）。

**Task 1.2 — 新建领域类 `MailInboxCursor.kt`**（Spring Data JDBC，immutable data class，`@Table("mail_inbox_cursor")` + `@Id`）。

**Task 1.3 — 新建仓库 `MailInboxCursorRepository.kt`**（`CrudRepository`，加 `findBySenderAccountCode(code): MailInboxCursor?`）。

**Task 1.4 — 新建 `MailInboxCursorService.kt`**（遵 I-1/I-3/I-4）：
- `get(accountCode): CursorState(uidValidity: Long?, lastUid: Long)`（无行返回 `null,0`）。
- `resolveStart(stored, currentUidValidity): Long`：若 `stored.uidValidity != currentUidValidity` → 返回 0 并 WARN（I-4）；否则返回 `stored.lastUid`。
- `advance(accountCode, currentUidValidity, fetchedUids: List<Long>, handledUids: Set<Long>, oldStart: Long)`：按 I-3 计算「从批次最小 UID 起连续 handled 的最高 UID」，与 `oldStart` 取 max，写表（upsert）。无 handled 时不前进。

### 阶段 2：IMAP 拉取改造（去 SEEN 依赖 + 暴露 UIDVALIDITY + 补抓）

**Task 2.1 — 扩展 `MailReceiveService` 接口**（遵 I-2/I-5）
- 删除 `fetchUnread(...)`，新增：
  - `fetchInboundSince(account, afterUid: Long, maxMessages: Int): InboundFetchResult`
  - `fetchByUids(account, uids: List<Long>): List<ReceivedMail>`
- `markSeen` 保留不变。
- 新增数据类 `InboundFetchResult(mails: List<ReceivedMail>, uidValidity: Long, maxUidInWindow: Long)`。

**Task 2.2 — 实现 `ImapMailReceiveService.fetchInboundSince`**（遵 I-2/I-4）
- READ_WRITE 打开 INBOX；`uidValidity = uidFolder.uidValidity`。
- `msgs = uidFolder.getMessagesByUID(afterUid + 1, UIDFolder.LASTUID)`；过滤掉 UID `<= afterUid`（`LASTUID` 区间含边界需校验）；**不**按 SEEN 过滤；按 UID 升序 `take(maxMessages)`；映射为 `ReceivedMail`（复用 `toReceivedMail`）。
- 返回 `InboundFetchResult(mails, uidValidity, mails.maxOfOrNull{uid} ?: afterUid)`。

**Task 2.3 — 实现 `ImapMailReceiveService.fetchByUids`**（遵 I-5）
- READ_WRITE 打开；`uidFolder.getMessagesByUID(uids.toLongArray())`，对非 null 映射为 `ReceivedMail`。供补抓使用。

### 阶段 3：`AutoMailReplyService` 接入游标 + 补抓入口

**Task 3.1 — 改 `receiveAndAutoReply`**（遵 I-1/I-2/I-3）
- 注入 `MailInboxCursorService`。
- 取 `stored = cursorService.get(accountCode)`；先做一次轻量拉取拿 `uidValidity`（由 `fetchInboundSince` 一并返回），`start = cursorService.resolveStart(stored, uidValidity)`。
  - 实现上：直接 `val fetch = fetchInboundSince(account, stored.lastUid, max)`；进入循环前用 `fetch.uidValidity` 判定是否需要重扫——若 `stored.uidValidity != null && stored.uidValidity != fetch.uidValidity`，则重新 `fetchInboundSince(account, 0, max)`（I-4）。
- 维护 `handledUids = mutableSetOf<Long>()`、`fetchedUids = fetch.mails.map{it.imapUid}`。
- 逐封处理：把现有 self-check/bounce/dmarc/`processSingle` 调用各自包进 `try { ... ; handledUids.add(uid) } catch (e){ log.error(...); /* 不加入 handled，留待重试，I-3 */ }`。
- 循环后：`cursorService.advance(accountCode, fetch.uidValidity, fetchedUids, handledUids, start)`。
- `AutoMailReplyBatchResult.fetched` 仍取本批 `mails.size`（语义微调：从「未读数」变「游标以上拉取数」，文档说明）。

**Task 3.2 — 新增 `processByUids(accountCode, uids): List<SinglePipelineResult>`**（遵 I-5）
- 解析账号；`mails = fetchByUids(account, uids)`；对每封调用 `processSingle(account, it, skipImapAck=false)`；返回结果列表（含 duplicate）。
- 仅处理业务流水线；补抓不更新游标（避免把游标拉低/拉高产生副作用，I-1：游标只是下界，补抓靠 DB 去重）。

### 阶段 4：补抓端点（一次性兜底 + 通用 escape hatch）

**Task 4.1 — `MailAutomationController` 新增端点**（遵 I-5）
- `POST /api/mail/backfill-uids`，`@RequestBody BackfillUidsRequest(accountCode: String, uids: List<Long>)`。
- 校验：`accountCode` 非空、`uids` 非空且 `<=100`、每个 `>0`。
- 调 `autoMailReplyService.processByUids(accountCode, uids)`，返回每个 UID 的 outcome（`uid -> outcome/reason`）。
- 用途：本次补抓 Sandra 的 UID 22；以及将来「毒邮件阻塞游标」时人工兜底。

**Task 4.2 — 执行一次性补抓（运行期操作，非代码）**
- 部署后调用：`POST /api/mail/backfill-uids {"accountCode":"<Sandra 所在账号, 即 LuKai>","uids":[22]}`。
- 预期：返回 outcome 为正常回复入库（或对应人工复核），收发信箱出现该邮件。重复调用应返回 `duplicate`（验证 I-1）。

### 测试改造

**Task T.1 — `AutoMailReplyServiceTest`**：把全部 `receiveService.fetchUnread(account,5)` 桩改为 `receiveService.fetchInboundSince(account, <start>, 5)` 返回 `InboundFetchResult(...)`；新增 cursorService 桩（`get`/`resolveStart`/`advance`）。新增用例：
- 一封 `\Seen` 但 UID > 游标的回复仍被处理（I-2）。
- 批次中低 UID 抛异常 → 游标停在其下方、高 UID 不被提交为已推进（I-3）。
- `UIDVALIDITY` 变化 → 从 0 重扫（I-4）。
- `processByUids` 对已存在 UID 返回 duplicate（I-1/I-5）。

> 注：测试文件改动较大但均为机械替换 + 4 个新用例，集中在单一测试类，不扩散。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|------|------|------|
| 1 | `src/main/resources/db/migration/V49__create_mail_inbox_cursor.sql` | 新增 | 游标表 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailInboxCursor.kt` | 新增 | 领域类 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailInboxCursorRepository.kt` | 新增 | 仓库 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorService.kt` | 新增 | 游标读取/推进/UIDVALIDITY 守卫 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt` | 修改 | 接口：删 `fetchUnread`，加 `fetchInboundSince`/`fetchByUids` + `InboundFetchResult` |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 修改 | 实现新拉取方法（去 SEEN 依赖、暴露 uidValidity、按 UID 补抓） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | `receiveAndAutoReply` 接游标 + 逐封 try/catch；新增 `processByUids` |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt` | 修改 | `POST /api/mail/backfill-uids` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | 桩改造 + 新用例 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorServiceTest.kt` | 新增 | 游标推进 / UIDVALIDITY 单测 |

文件数 = 10（上限）。子系统 = 1（mail 入站拉取）。新建共享存储字段 = 0（新表，非给现有共享表加字段）。

## 验收标准

- I-1：补抓 UID 22 两次，第二次返回 `duplicate`；游标下方 UID 经 `processByUids` 仍能被处理（不被游标跳过）。`inbound_mail_processing` 中该 (account, 22) 仅一行。
- I-2：构造一封 `\Seen=true` 且 UID > 游标的专家回复，`receiveAndAutoReply` 仍写入 `mail_record` INBOUND（单测断言）。
- I-3：批次 `[uid=10 抛异常, uid=11 成功]` → `advance` 后游标 `< 10`（不前进越过 10）；批次 `[10 成功,11 成功]` → 游标=11。
- I-4：`stored.uidValidity=100`，`fetch.uidValidity=200` → 走 0 重扫分支，WARN 日志；游标记录更新为新 uidValidity。
- I-5：`POST /api/mail/backfill-uids {accountCode, uids:[22]}` 后，收发信箱（读 `mail_record`）出现 Sandra 该封；走联系人匹配 + 状态推进与正常回复一致。
- I-6：跑现有 `BounceCollectionServiceTest`、退信相关用例全绿；`fetchUnseenMessages` 签名/行为未变。
- 集成（IP-1）：批次含 1 封 self-check + 1 封正常回复（均 UID > 游标，且 self-check 已 SEEN）→ self-check 被丢弃、正常回复入库、游标推进到两者中较高 UID。
- 全量：`mvn test` 通过（JDK 11 / zulu-11）。

## 已知限制（显式承认，非缺陷）

- **毒邮件头阻塞**：I-3 的「停在缺口下方」使一封持续抛异常的邮件会阻塞该账号后续更高 UID 的处理（不同于旧 SEEN 方案的逐封独立）。本计划用 ERROR 日志 + `backfill-uids` 端点作为人工兜底；自动重试上限推进游标延后到独立计划（`retry_count`/`last_error` 列已具备）。
- **UIDVALIDITY 变更全量重扫**：邮箱重建时从 0 重扫，靠 `inbound_mail_processing` 去重；低邮件量下成本可接受。

## 自检清单

- [x] 关键不变量 ≥1/新状态：游标、UIDVALIDITY、补抓均有不变量。
- [x] 现状审计列全写/读路径（grep 实证：`fetchUnread`/`fetchUnseenMessages`/`markSeen` 调用点、`inbound_mail_processing` 写点）。
- [x] 无未被不变量覆盖的新写路径（新表写入由 I-3/I-4 约束；mail_record 复用既有路径 I-5）。
- [x] 文件数 ≤ 10。
- [x] 子系统 ≤ 2（=1）。
- [x] 每个任务引用其治理不变量编号。
- [x] 验收每个不变量 ≥1 检查。
- [x] 文件清单无「等」「相关文件」。
- [x] 超范围段显式延后退信游标化 / 毒邮件策略 / 前端。
- [x] Phase 0 知识（K-backfill-readonly-inbound、K-mime-dsn-before-heuristic、K-cleanedbody-inbound-only）均已用于审计或确认无关。
- [x] 保存至 `docs/plans/2026-06-29/`。
