# 收信流：self-check 探针 / 退信识别 / 退信可见性（含历史回填）

> 本计划用 create-p 编写。范围超过单计划上限（>10 文件、3 子系统），按规则拆为 **3 个顺序子计划 A→B→C**，每个独立可部署、可验收。
> 用户硬性要求：**修复后历史退信也必须能看到** —— 由子计划 C 的回填 + 退信名单接口保证。

## Phase 0 知识加载结果
- `docs/knowledge/` 仅有 `qa/` 域 6 条，均与 mail 收信/退信无关 → 本次无可复用知识条目（有意识地不引入）。
- Phase 6 将把本计划发现的写入路径与不变量回写到 `docs/knowledge/mail/`。

---

## 现状审计（共享）

### store: `inbound_mail_processing`（MySQL，Spring Data JDBC）
- 关键列：`sender_account_code, imap_uid, message_id, in_reply_to, from_email, subject, body, cleaned_body, received_at, process_status, process_reason, reason_type, expert_contact_id, resolved_*`。**含 `body` 原文** → 历史回填可直接重扫 body，无需重连 IMAP。
- 写路径：
  1. `AutoMailReplyService.confirmManualReviewWithBody`（`AutoMailReplyService.kt:847`）—— 未匹配/人工复核时写入，含 `UNMATCHED_CONTACT`。
  2. `AutoMailReplyService.confirmProcessed`（`:880`）—— 已处理回执。
  3. `AutoMailReplyService.processSingle` 去重判断 `findBySenderAccountCodeAndImapUid`（`:68`）。
  4. `UnmatchedInboundMailService.bindToContact / markResolved`（`:177/:217`）—— 更新为 PROCESSED。
- 读路径：
  1. `UnmatchedInboundMailService.listManualReviewQueue` → `findManualReviewQueue`（`InboundMailProcessingRepository.kt:50`，过滤 `process_status='MANUAL_REVIEW'`）。
  2. `monitoring` 的 `listInboundActivity / countInboundActivity`（`:123/:140`）。
  3. 前端「监控 → 待人工(pending)」`app.js:5406,5454`。

### store: `bounce_record`（MySQL）
- 列：`sender_account_code, bounce_message_id, original_message_id, original_expert_contact_id, bounce_type, dsn_status, bounce_reason, received_at`（`BounceRecord.kt`）。
- 写路径：仅 `BounceCollectionService.collectBounces`（`BounceCollectionService.kt:54`），去重 `existsByBounceMessageId`（`:42`）。
- 读路径：仅 `countHardBouncesSince/countSoftBouncesSince`（聚合，给 `BounceRateMonitorService`），以及 `getBounceStats`（`MailMonitoringService.kt:252`）只出**速率/计数**。
- **结论：没有任何"逐条退信名单"接口或前端视图**，只有 `/api/mail-monitoring/bounce-stats` 聚合数。

### 收信主流程 `AutoMailReplyService.receiveAndAutoReply`（`:540`）
顺序：`fetchUnread` → forEach{ `isBounce`(仅 from/subject，传 `contentType=null`) 跳过 → `isDmarc` 跳过 → `processSingle` } → forEach 后 `collectBounces`(`fetchUnseenMessages`)。

### 关键缺陷链（三问题同根）
1. **self-check 探针**（`SenderAccountSelfCheckService.kt:25`，账号给自己发 `[self-check] <code> <ts>`）无人识别 → 当普通信进 `processSingle` → 未匹配 → `MANUAL_HANDOFF`/未匹配表，被当"待处理"。
2. **退信漏判**：`BounceDetector.isBounce`（`BounceDetector.kt:12`）只认 from 含 `mailer-daemon/postmaster` + 少量英文主题 + `report-type=delivery-status`。中文退信、Exchange/Amazon NDR（主题 `Delivery has failed to these recipients or groups`，真实失败收件人/`554 5.x.x` 在**正文**）全漏，且 `isBounce` 不看 body。
3. **顺序吞噬**：即便后面 `collectBounces` 想补救，`processSingle` 已先 `markSeen`，`fetchUnseenMessages` 取不到。漏判的退信落到 `inbound_mail_processing`(UNMATCHED_CONTACT)，既不进 `MailRecord`（收发邮件），也不进 `bounce_record`（退信名单）。

### 交互点
- IP-1：`receiveAndAutoReply.forEach` 写 `inbound_mail_processing` × 退信识别 —— 退信不应进未匹配人工队列。
- IP-2：`bounce_record` 写（live `collectBounces` + 新增 forEach 即时入库 + C 的回填）三处共写 → 必须统一去重键。
- IP-3：历史 `inbound_mail_processing` 行（已是 UNMATCHED_CONTACT）× C 回填生成 `bounce_record` —— 回填须只读 inbound 表、幂等。

---

# 子计划 A：丢弃 self-check 探针

## 需求描述
- 可观察结果：账号自检产生的 `[self-check] …` 邮件不再出现在"待处理/未匹配"，收发邮件与退信名单也不收录。
- 不可改变：正常专家来信、退信、DMARC 处理路径不变。
- 不在范围：自检逻辑本身（发送/缓存/暂停）不动。

## 关键不变量
### I-A1: 探针识别条件
- Rule：判定为探针当且仅当 `subject` 去空格后以 `[self-check]` 开头 **且** `from` 规范化后等于该账号 `senderEmail`。
- Applies to：`receiveAndAutoReply.forEach` 新增守卫。
- Violation：误杀真实来信，或漏过导致仍进人工队列。
- 来源：original

### I-A2: 探针零落库
- Rule：命中探针 → `markSeen` 后 `return@forEach`，不写 `MailRecord` / `inbound_mail_processing` / `bounce_record`。
- Applies to：同上守卫，位置必须在 `isBounce`、`isDmarc`、`processSingle` **之前**。
- Violation：探针被当退信或未匹配。
- 来源：original

## 实现方案
- 新增 `SelfCheckProbeDetector.isSelfCheckProbe(from, subject, accountEmail): Boolean`（纯字符串、可单测）。遵守 I-A1。
- `receiveAndAutoReply.forEach` 顶部插入守卫（遵守 I-A2）：命中 → `mailReceiveService.markSeen(account, it.imapUid)` + `return@forEach`。
- 历史清理：一次性把 `inbound_mail_processing` 中 `subject LIKE '[self-check]%'` 且 `from_email = sender_email` 的 `MANUAL_REVIEW` 行标 `PROCESSED/reasonType=SELF_CHECK_DISCARDED`（写在 C 的回填 runner 里执行，或单独 SQL 维护脚本；见 C-任务4）。

## 变更文件清单（A）
| 文件 | 动作 |
|---|---|
| `mail/service/SelfCheckProbeDetector.kt` | 新增 |
| `mail/service/AutoMailReplyService.kt` | 改：forEach 守卫 |
| `test/.../mail/service/SelfCheckProbeDetectorTest.kt` | 新增 |
共 3 文件。

## 验收标准（A）
- I-A1：单测 `[self-check] X 123` + from=self → true；`Re: [self-check]…` 或 from≠self → false。
- I-A2：集成测试投入一封探针 → forEach 后 `inbound_mail_processing`、`mail_record`、`bounce_record` 均无新增，IMAP 该 uid 已 seen。

---

# 子计划 B：退信识别加宽 + 顺序前置 + 即时入库

## 需求描述
- 可观察结果：中文退信 / Exchange-Amazon NDR / 标准 DSN 均被识别为退信，进入 `bounce_record`，不再落入未匹配人工队列。
- 不可改变：正常来信仍走 `processSingle`；现有标准 DSN 解析能力不退化；DMARC 路径不变。
- 不在范围：退信名单 UI / 历史回填（属 C）；退信率阈值策略不动。

## 关键不变量
### I-B1: 单一检测来源（字符串化）
- Rule：检测核心是纯函数 `BounceDetector.detect(from, subject, body): BounceSignal?`；`Message` 版 `parseBounceDetails` 与 live forEach、C 回填 **全部** 委托同一函数。
- Applies to：`BounceDetector`、`AutoMailReplyService.forEach`、`BounceCollectionService`、（C）`BounceBackfillService`。
- Violation：live 与历史判定不一致，用户看到"有的退信进、有的不进"。
- 来源：original

### I-B2: 退信不进 processSingle
- Rule：forEach 中命中退信 → 交给退信入库 + `markSeen` + `return@forEach`，绝不进入 `processSingle`（不得产生 REPLY MailRecord / 误置某 contact 为 MANUAL_HANDOFF）。
- Applies to：`receiveAndAutoReply.forEach`（位置在 self-check 守卫之后、`processSingle` 之前）。
- Violation：复现当前"退信被未匹配路径吞掉"。
- 来源：original（IP-1）

### I-B3: bounce_record 去重键唯一
- Rule：去重以 `bounce_message_id` 为准；`message_id` 为空时合成稳定键 `NOID:<sha1(from|subject|received_at)>`。任何写路径写前 `existsByBounceMessageId`。
- Applies to：live forEach 即时入库、`collectBounces`、（C）回填。
- Violation：同一退信多路重复入库（IP-2）。
- 来源：original

### I-B4: 失败收件人兜底关联
- Rule：原始 `Message-ID` 解析不到时，从正文提取失败收件人邮箱（`无法发送到 X` / `<...@...>` / `said:` 上下文），按 `expert_contact` + alias 反查 `originalExpertContactId`；仍无 → 该字段留 null，但 **BounceRecord 必须照常保存**（退信名单不丢条目）。
- Applies to：退信入库逻辑。
- Violation：用户那两封退信因关联失败而被丢弃。
- 来源：original（IP-2/IP-3）

## 实现方案（B）
1. `BounceDetector` 重构（遵守 I-B1）：
   - 抽 `detect(from, subject, body): BounceSignal?`。`isBounce(...)` 与 `parseBounceDetails(Message)` 内部改为提取出 from/subject/body 字符串后委托 `detect`（Message 版仍可额外读 `message/delivery-status` part 提升 DSN 精度，但分类判定走 detect）。
   - 主题关键词补：`delivery has failed`、`recipients or groups`、`failure notice`、`mail system error`、`退信`、`被退回`、`无法发送`、`邮件被退回`。
   - 正文扫描：`5\d\d\s+5\.\d\.\d`、`bounced address`、`could not resolve`、`poor reputation`、`access to this mail system has been rejected`。
   - `BounceSignal` 含：`bounceType(HARD/SOFT)`、`dsnStatus`、`failedRecipient`、`reason`、`originalMessageId?`。
2. 退信入库收口（遵守 I-B3/I-B4）：把 `collectBounces` 内"构造并保存 BounceRecord + 关联 contact + HARD→EMAIL_INVALID"逻辑抽到 `BounceCollectionService.ingest(signal, senderAccountCode, bounceMessageId, receivedAt)`，供 live 与回填复用（避免新建类，控制文件数）。
3. `receiveAndAutoReply.forEach`（遵守 I-B2）：self-check 守卫之后立刻：`val sig = bounceDetector.detect(it.from, it.subject, it.body)`；`if (sig != null) { ingest(...); markSeen; return@forEach }`。保留末尾 `collectBounces` 作为 MIME 富解析兜底（去重保证不重复）。
4. `findContactByEmailOrAlias` 反查失败收件人用于 I-B4 关联。

## 变更文件清单（B）
| 文件 | 动作 |
|---|---|
| `mail/service/BounceDetector.kt` | 改：`detect()` + 加宽关键词/正文扫描 + 失败收件人提取 |
| `mail/service/BounceCollectionService.kt` | 改：抽 `ingest()`，合成去重键，复用 detect |
| `mail/service/AutoMailReplyService.kt` | 改：forEach 前置退信守卫 |
| `mail/repository/BounceRecordRepository.kt` | 改：（如需）按 from+received 合成键不依赖 schema |
| `test/.../mail/service/BounceDetectorTest.kt` | 改/增：三类样本 |
| `test/.../mail/service/AutoMailReplyServiceTest.kt` | 改：退信不进 processSingle |
共 6 文件。

## 验收标准（B）
- I-B1：同一封中文退信样本，`detect()`、live forEach、回填三处判定一致（共享单测夹具）。
- I-B2：投入中文退信 + Exchange NDR → 二者均生成 `bounce_record`，`inbound_mail_processing` 无新增 UNMATCHED_CONTACT、无误改 contact 状态。
- I-B3：同一退信经 forEach 与 collectBounces 两路 → `bounce_record` 仅 1 行。
- I-B4：失败收件人匹配到专家 → `original_expert_contact_id` 填充且 HARD 时该专家 `EMAIL_INVALID`；匹配不到 → 仍存 BounceRecord，字段为 null。

---

# 子计划 C：退信名单接口 + 前端 + 历史回填（满足"历史退信也能看到"）

## 需求描述
- 可观察结果：(1) 新增逐条「退信名单」视图（含 backfilled 历史退信）；(2) 未匹配列表已存在，作为入口暴露；(3) 运行一次回填后，**修复前已收到的历史退信全部出现在退信名单**。
- 不可改变：不删除/不破坏 `inbound_mail_processing` 历史行；现有 `bounce-stats` 接口不变。
- 不在范围：退信自动重试/再营销；退信名单的批量导出。

## 关键不变量
### I-C1: 回填幂等
- Rule：回填可重复运行，依赖 I-B3 去重键，绝不产生重复 `bounce_record`。
- Applies to：`BounceBackfillService.run()`。
- Violation：重复执行污染退信名单与退信率。
- 来源：original（IP-3）

### I-C2: 回填只读 inbound 表
- Rule：回填遍历 `inbound_mail_processing`（按 `received_at` 分页），对每行 `detect(from_email, subject, body)`，命中则经 `ingest()` 建 `bounce_record`；**不修改/删除 inbound 行**（无 schema 变更，不加新字段）。可选：把命中行 `reason_type` 标注留作 C 的增强，本计划不做（避免改写历史语义）。
- Applies to：`BounceBackfillService`。
- Violation：篡改历史人工队列数据。
- 来源：original

### I-C3: 退信名单含历史
- Rule：`GET /api/mail/bounces`（分页，可按账号/类型筛）返回所有 `bounce_record`，回填产生的历史条目与新退信同列同序（`received_at DESC`）。
- Applies to：`BounceRecordRepository` 新查询 + 新接口 + 前端视图。
- Violation：历史退信仍不可见（违背用户硬性要求）。
- 来源：用户要求

## 实现方案（C）
1. `BounceRecordRepository`：加分页 `findPaged(accountCode?, bounceType?, limit, offset)` + `countPaged(...)`。
2. 新增 `BounceBackfillService.run(batchSize)`：分页扫 `inbound_mail_processing` → `detect` → 命中 `BounceCollectionService.ingest(...)`（去重，I-C1）。`bounceMessageId` 取行 `message_id`，空则合成键（I-B3）。`receivedAt` 用行 `received_at`。同一 run 内顺带执行 A 的 self-check 历史清理（标 PROCESSED）。遵守 I-C2。
3. 新增 `BounceController`：`GET /api/mail/bounces`（列表，I-C3）+ `POST /api/mail/bounces/backfill`（触发回填，受操作日志记录）。响应 dto 内联本文件。
4. 前端：`app.js` 加「退信名单」子标签调用 `/api/mail/bounces`，列：时间/账号/类型/失败原因/关联专家/dsn；`index.html` 加导航项；并把已有「待人工/未匹配」入口显式命名为"未匹配邮件"。

## 变更文件清单（C）
| 文件 | 动作 |
|---|---|
| `mail/repository/BounceRecordRepository.kt` | 改：分页 list/count |
| `mail/service/BounceBackfillService.kt` | 新增：幂等回填 + self-check 历史清理 |
| `mail/controller/BounceController.kt` | 新增：列表 + 回填触发接口 + dto |
| `resources/static/app.js` | 改：退信名单视图 + 未匹配入口命名 |
| `resources/static/index.html` | 改：导航项 |
| `test/.../mail/service/BounceBackfillServiceTest.kt` | 新增：幂等 + 历史可见 |
共 6 文件。

## 验收标准（C）
- I-C1：构造 5 行含 2 行退信的历史 `inbound_mail_processing`，`run()` 两次 → `bounce_record` 恰 2 行（第二次 0 新增）。
- I-C2：`run()` 后比对 `inbound_mail_processing` 行数与内容无变化（除可选标注，本计划不做）。
- I-C3：`run()` 后 `GET /api/mail/bounces` 返回这 2 条历史退信，按时间倒序，含失败收件人与 dsn；前端退信名单可见。
- 端到端：用用户真实样本（中文退信 + Amazon NDR 文本）放入 `inbound_mail_processing.body`，回填后两封均出现在退信名单。

---

## Phase 4 自检
- [x] 每个新概念（探针识别/退信检测/合成去重键/回填）均有不变量。
- [x] 现状审计列全 `inbound_mail_processing` 与 `bounce_record` 全部读写路径（grep 实证）。
- [x] 无任务引入未被不变量覆盖的写路径（bounce_record 三写口收口到 `ingest`+去重）。
- [x] 每子计划文件数 ≤10（A:3 / B:6 / C:6），子系统 ≤2，无共享 store 新字段。
- [x] 每任务引用治理不变量编号。
- [x] 验收标准每不变量 ≥1 检查；含跨交互点端到端场景。
- [x] 文件清单无"等/相关文件"。
- [x] Out-of-scope 显式声明。
- [x] Phase 0 知识无相关条目，已说明不引入。
- [x] 保存至 `docs/plans/2026-06-26/`。

## 执行顺序与依赖
A → B → C（C 的回填依赖 B 的 `detect()`/`ingest()`）。各自独立可部署、可验收。

## Phase 6 待回写知识（执行后）
- `docs/knowledge/mail/`：`bounce_record` 三写口必须经 `ingest()`+去重键（K 候选）；`inbound_mail_processing.body` 留存原文可支持离线重分类（K 候选）；收信 forEach 守卫顺序 self-check→bounce→dmarc→processSingle（K 候选）。

## 修正记录

| 日期 | 来源 | 修正项 | 决策 |
|---|---|---|---|
| 2026-06-26 | fix-v:fix-1 | 子计划 C 同时写了“回填只读 inbound 表，本计划不做可选标注”和“顺带执行 self-check 历史清理”，要求冲突。 | 以 I-C2 为准：本计划的退信回填不得修改 `inbound_mail_processing`；self-check 历史清理若仍需要，另起独立计划。 |
| 2026-06-26 | fix-v:fix-1 | C 验收要求退信名单“含失败收件人与 dsn”，但 `bounce_record` 现有模型没有持久化失败收件人，接口无法稳定返回未关联专家的失败收件人。 | 修复必须让 `failedRecipient` 成为可持久化、可查询、可返回字段；若选择不加字段，需明确降级并删除该验收项。 |
