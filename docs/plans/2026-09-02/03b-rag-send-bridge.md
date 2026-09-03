# 03b RAG 草稿采用 → 人工发送桥接

> 顺序权威：`00-execution-order.md`。**依赖 03**；**05 与 07 必须在本计划之后**。
> 全局不变量 G-1 ~ G-8 适用，本文不重复定义。
>
> 本计划因 2026-09-02 计划评审提出的 P0-1 而新增：05 原方案把字符串 `fact_code`
> 塞进现有 `qaRuleIds: List<Long>`，反序列化即 400；即使类型对上，也会撞上
> 「必须与 verifyAssembly 的 canonicalFactIds 逐元素相等」这道校验。
> 这是后端契约问题，改前端解决不了。
>
> 二轮评审又补两点：真实入口是 `sendManualRichReply()` 而非 `sendComposedReply`（D-17，
> 后者在本仓不存在）；且只绕过发送前半段不够，`collectSafetyFindings()` 里还有两处会把 RAG
> 拖回 `qa_rule`（D-15 / I-47）。迁移版本按 G-9 分配为 **V113**。

## 需求描述

**Observable outcome**

1. RAG 工作台的草稿可以采用到人工回复区并成功发出，携带的证据是**字符串 `fact_code` 列表**
   与**语料指纹**，不再冒充 `qaRuleIds`。
2. 已发出的 RAG 回信在新表 `mail_record_rag_fact` 中留下有序的 `fact_code` 存证，
   与旧的 `mail_record_qa_rule` 并列、互不干扰。
3. 草稿生成后若语料被改动（指纹变化）才点发送，服务端返回 `409 RAG_CORPUS_STALE`，
   要求重新生成，不会用旧事实发出。

**What must NOT change**

1. 旧工作台的发送路径：带 `trustReplyAssembly` 时 `verifyAssembly` + `qaRuleIds` 逐元素相等校验
   （`PendingMailOperationService.kt:181-196`）一行不改。
2. 无 trustReplyAssembly 的 legacy 路径：`canonicalizeFactRuleIds` 严格关键词匹配
   （`PendingMailOperationService.kt:208`）一行不改。
3. `mail_record_qa_rule` 的写入与读取（已发信件的 QA 使用审计）。
4. 手动富文本发送的二次确认流程。
5. 邮件变量渲染、主题校验、发件账号解析等发送前处理。

**Out of scope**

- 把 RAG 证据回填进 `mail_record_qa_rule`（做不到：45 条里只有 33 条有 `legacy_rule_id`，
  另 12 条无映射，硬填会丢证据且违反 G-4）。
- 监控页「命中规则」列对 RAG 回信的展示（见 I-43 的已知取舍与 X-5）。
- 自动发送：D-1 明确不做。
- 旧 AI 回复弹窗（`aiReplyState.adoptContext` 那一套）的改造。

## 关键不变量

### I-39: 三条发送路径互斥且优先级固定
- Rule: `sendManualRichReply` 的证据来源恰好三选一，判定顺序固定：
  ① `trustReplyAssembly != null` → 旧工作台路径（现有逻辑，原样保留）
  ② `ragFactCodes != null` → RAG 路径（本计划新增）
  ③ 其余 → legacy `qaRuleIds` 路径（现有逻辑，原样保留）
  ① 与 ② 同时出现 → `400 SEND_EVIDENCE_SOURCE_CONFLICT`，**不得**任选其一。
- Applies to: `PendingMailOperationService.sendManualRichReply()`。
- Violation consequence: 两种证据同时被采纳 → `mail_record_qa_rule` 与 `mail_record_rag_fact`
  各写一份、互相矛盾，事后无法判定这封信到底依据什么发出。
- 来源: original

### I-40: RAG 路径不读 qa_rule
- Rule: RAG 分支内**不得**调用 `canonicalizeFactRuleIds`、`qaFactSelectionService.select()`、
  `qaRuleRepository` 的任何方法。`fact_code` 的合法性只对 `RagKnowledgeBase` 快照校验。
- Applies to: `PendingMailOperationService` 的 RAG 分支。
- Violation consequence: 违反 G-4；且 12 条无 `legacy_rule_id` 的事实会被判为非法。
- 来源: G-4

### I-41: 语料指纹是发送时的新鲜度门禁
- Rule: 请求必须携带 `ragCorpusFingerprint`（生成草稿时服务端下发的那一个）。
  发送时与 `RagKnowledgeBase.fingerprint()` 比对，不等则 `409 RAG_CORPUS_STALE`。
  **不得**自动重新生成、不得静默放行。
- Applies to: `PendingMailOperationService` 的 RAG 分支。
- Violation consequence: 运营开着页面时别人改了事实原文，点发送就把旧版逐字内容发出去了——
  而逐字内容正是最需要审定的那部分。
- 来源: original（G-2 的发送侧对应）

### I-42: fact_code 顺序即存证顺序
- Rule: `mail_record_rag_fact` 按 `ordinal` 保存请求中 `ragFactCodes` 的**原始顺序**，
  不排序、不去重（去重在生成侧已完成）。
- Applies to: `MailRecordRagFactRepository` 的写入。
- Violation consequence: 事后复盘看不出这封信里事实的实际出现顺序。
- 来源: 对齐 `mail_record_qa_rule` 的 `ordinal` 语义
  （`MailRecordQaRuleRepository.kt:7` `findByMailRecordIdOrderByOrdinalAsc`）

### I-43: RAG 路径下 `matched_qa_rule_id` 置 null
- Rule: 现有 `primaryRuleId = canonicalFactIds.firstOrNull()` 会写入
  `mail_record.matched_qa_rule_id`。RAG 路径下该字段**置 null**，
  **不得**用 `legacy_rule_id` 兜底（12 条无映射，兜底会造成一半有一半没有的假象）。
- Applies to: `PendingMailOperationService` 的 RAG 分支。
- Violation consequence: 兜底会让监控页的「命中规则」列半真半假，比全空更难排查。
- 已知取舍: 监控页对 RAG 回信的该列显示为空。登记为 X-5，本轮不修。
- 来源: original

### I-44: RAG 路径不跑 preflight
- Rule: 前端在 RAG 模式下**不调用** `POST /unmatched-inbound/{id}/composed-reply/preflight`。
  该端点的两个入参 `factRuleIds: List<Long>` 与 `expectedEvidenceSetVersion`
  （`PendingMailOperationService.kt:1278-1282`）在 RAG 下都无对应物：
  前者是 Long、后者是旧矩阵的 evidenceSetVersion。
  其职责由 I-41 的指纹门禁 + D-1 的「零强制门禁」共同覆盖。
- Applies to: `app.js` 的 `schedulePreflightCheck()` 调用点（`app.js:10276`）。
- Violation consequence: 传空 `factRuleIds` 会让 preflight 判为「未携带证据」，
  返回与实际不符的 readiness，误导运营。
- 来源: original

### I-47: RAG 路径必须绕过整条 QA safety/selection 链
- Rule: 只让发送前半段不读 `qa_rule` 是**不够的**。`collectSafetyFindings()`
  （`PendingMailOperationService.kt:800`）里还有两处会把 RAG 拖回旧库：
  - `:844-845` `carriesQa && canonicalFactIds.isEmpty()` → `add("QA_FACTS_ALL_INVALID")`
  - `:860-864` `val selection = verifiedSelection ?: qaFactSelectionService.select(...)`
    —— RAG 的 `verifiedSelection` 为 null，必然读 `qa_rule`
  RAG 路径必须同时满足：
  ① `carriesQa` 传 **false**（RAG 不携带 QA 证据），自动落入 `:846` 的 `else` 分支，
     因而**不会**产生 `QA_FACTS_ALL_INVALID`；
  ② `collectSafetyFindings` 新增 `ragSend: Boolean = false` 形参，为 true 时
     **整段短路** `:860-880` 的 `selection` 求值、`hasBlockingTrustGapForSelection`
     与其后的 intent 遍历——这一整段都依赖 `qa_rule`。
- 保留什么: `:846-853` 的 `containsHallucinatedNumberOrUrl` /
  `containsUnbackedHighRiskDeclarations` 与 `:856` 的 `containsTrustRhetoric` **保留**。
  它们是纯文本检查、不读 `qa_rule`，且是二次确认弹窗的触发源
  （`safetyWarningConfirmed` / `strongConfirmationText`）。findings 恒空会让发送变成
  零摩擦按钮，与 D-1「人是唯一的门」的本意相反。
- Applies to: `PendingMailOperationService.collectSafetyFindings()` 与其 RAG 调用点。
- Violation consequence: 不做 ① 则每封 RAG 回信都带一个运营看不懂的 `QA_FACTS_ALL_INVALID`；
  不做 ② 则 RAG 路径每次发送都全量读 `qa_rule` 并按旧意图目录判定，直接违反 G-4 / I-40。
- 来源: original（2026-09-02 第二轮计划评审 P0-2）

## 现状审计

### 发送链的实际契约（逐行核实）
- 端点 DTO：`mail/controller/UnmatchedInboundMailController.kt:978` `qaRuleIds: List<Long> = emptyList()`；
  `:1012` `qaRuleIds: List<Long>? = null`；`:1027` `qaRuleIds: List<Long>`。
- **真实发送入口**：`mail/service/PendingMailOperationService.kt:135` `fun sendManualRichReply(...)`
  —— **不是** `sendComposedReply`（该函数不存在）。其相关形参：
  `:142` `qaRuleIds: List<Long>? = null`、`:143` `suggestedRuleIds: List<Long>? = null`、
  `:150` `trustReplyAssembly: TrustReplyAssembleRequest? = null`、
  `:151` `safetyWarningConfirmed: Boolean = false`、`:152` `strongConfirmationText: String? = null`。
  新参数追加在这一串的末尾。
- 校验链（`PendingMailOperationService.kt`）：
  - `:181-186` 有 assembly 时调 `trustReplyWorkbenchService.verifyAssembly(assembly)`
  - `:191-196` **`qaRuleIds.orEmpty() != verifiedAssembly.response.canonicalFactIds` 即 422**
    （注释明确：任一缺失/增加/乱序都在 claim 前失败，绝不静默采纳客户端 ids）
  - `:199-203` `carriesQa` 判定：有 assembly 看 canonical 是否非空，否则看客户端是否提交过 qaRuleIds
  - `:204-210` `factResolution`：有 assembly 原样用 canonical；否则走
    `canonicalizeFactRuleIds(inboundText, qaRuleIds!!, researchProfileSufficient)`
  - `:213-215` `serverSuggestedFactIds` 调 `qaFactSelectionService.select(...)`
  - `:218` `primaryRuleId = canonicalFactIds.firstOrNull()`
- `verifyAssembly` 签名：`llm/service/TrustReplyWorkbenchService.kt:1758`
  `verifyAssembly(request: TrustReplyAssembleRequest)`，其入参含
  `expectedSourceVersion` / `requestFactSelections` / `requestedFactIds` / `frameSnapshot` /
  `expectedEvidenceSetVersion` —— 全部是旧矩阵模型的产物，RAG 一个都产不出来。
- **结论**：RAG 草稿无法伪装成任何一条既有路径，必须新增第三条。

### Preflight 契约
- 端点：`UnmatchedInboundMailController.kt:292` `POST /unmatched-inbound/{id}/composed-reply/preflight`
- 入参：`PendingMailOperationService.kt:1278-1282` `AiReplyPreflightRequest(factRuleIds: List<Long>,
  expectedEvidenceSetVersion: String, textBody: String)`
- 前端调用点：`app.js:10116` `schedulePreflightCheck()`（500ms 防抖）→ `app.js:10131` `doPreflightCheck()`；
  由 `app.js:10276` 在 `adoptTrustReplyAssembly` 末尾触发。

### `mail_record_qa_rule`（本计划只并列、不改）
- 仓储：`mail/repository/MailRecordQaRuleRepository.kt:6-8`
  `findByMailRecordIdOrderByOrdinalAsc(mailRecordId)`。
- Write paths: `PendingMailOperationService`（人工发送）、自动回复链路。
- Read paths: 已发信件的 QA 使用审计展示、`MailMonitoringService`。
- **Interaction point 1**：新表写入 × 旧审计读取。两者必须互不影响——
  一封信要么在旧表有行、要么在新表有行，不得两边都有（I-39）。A-4 验收。

### `mail_record.matched_qa_rule_id`
- 读取点：`monitoring/service/MailMonitoringService.kt:131-133,153`
  `matchedQaRuleDisplayName = record.matchedQaRuleId?.let { qaRules[it]?.displayName }`。
- **Interaction point 2**：RAG 路径置 null × 监控页读取。置 null 后该列为空而非报错。A-5 验收。

### 前端采用路径
- `app.js:10269-10277` `adoptTrustReplyAssembly(recordId, assembly)`：
  `manualReplyQaContext = { qaRuleIds: [...(assembly.canonicalFactIds || [])], baselineText: rendered }`，
  末尾调 `schedulePreflightCheck()`。
- 05 原方案在此处把 `canonicalFactIds` 换成 `usedFactCodes` —— 正是 P0-1 的成因。
  本计划接管这处改动（05 的变更清单相应移除该项）。

## 样式契约

本计划**不新增、不修改任何 DOM 与 CSS**。前端改动仅限 `app.js` 的数据装配与 preflight 跳过。
`git diff --stat src/main/resources/static/styles.css` 与
`git diff --stat src/main/resources/static/index.html` 均须为空。

## 实现方案

### T1 — V113 迁移
新建 `src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql`：
```
CREATE TABLE mail_record_rag_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_record_id BIGINT NOT NULL,
    fact_code VARCHAR(32) NOT NULL,
    ordinal INT NOT NULL,
    corpus_fingerprint VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mail_record_rag_fact (mail_record_id, ordinal),
    KEY idx_mail_record_rag_fact_record (mail_record_id),
    KEY idx_mail_record_rag_fact_code (fact_code)
);
```
表注释写明：**与 `mail_record_qa_rule` 并列，一封信只会写其中一张**（I-39）；
`corpus_fingerprint` 记录发出时的语料版本，用于事后复盘「当时的原文是哪一版」。
**不声明外键到 `rag_fact`**——事实可能被停用或改写，存证不应随之失效
（与 `mail_record_qa_rule` 不声明 `ON DELETE CASCADE` 的既有基线一致）。

遵循 I-42。

### T2 — 领域与仓储
新建 `mail/domain/MailRecordRagFact.kt`（`@Table("mail_record_rag_fact")`）。
新建 `mail/repository/MailRecordRagFactRepository.kt`：
`CrudRepository` + `findByMailRecordIdOrderByOrdinalAsc(mailRecordId)`
（与 `MailRecordQaRuleRepository.kt:7` 同形）。

遵循 I-42。

### T3 — 发送服务分支
修改 `mail/service/PendingMailOperationService.kt`：
- `sendManualRichReply(...)` 新增两个可空参数 `ragFactCodes: List<String>? = null`、
  `ragCorpusFingerprint: String? = null`（放在参数列表末尾，既有调用点零改动）。
- 在 `:181` 的 assembly 校验**之前**插入互斥判定（I-39）：
  `assembly != null && ragFactCodes != null` → `400 SEND_EVIDENCE_SOURCE_CONFLICT`。
- 新增 RAG 分支：
  1. `ragCorpusFingerprint` 为空 → `400 RAG_FINGERPRINT_REQUIRED`
  2. `ragCorpusFingerprint != ragKnowledgeBase.fingerprint()` → `409 RAG_CORPUS_STALE`（I-41）
  3. 每个 `fact_code` 必须在快照中存在且 `enabled` → 否则 `422 RAG_FACT_CODE_UNKNOWN`（I-40）
  4. `carriesQa = ragFactCodes.isNotEmpty()`
  5. `factResolution = CanonicalFactResolution(emptyList(), emptyList())` —— RAG 不产生 Long ids
  6. `serverSuggestedFactIds = emptyList()` —— **不调** `qaFactSelectionService.select()`（I-40）
  7. `primaryRuleId = null`（I-43）
- **`collectSafetyFindings` 改造（I-47）**：新增 `ragSend: Boolean = false` 形参；
  RAG 调用点传 `carriesQa = false` 与 `ragSend = true`。
  在 `:860` 的 `val selection = ...` 之前加守卫，`ragSend` 为 true 时跳过
  `selection` 求值、`hasBlockingTrustGapForSelection` 与其后的 intent 遍历整段。
  纯文本检查（hallucinated / unbacked / trust rhetoric）保持执行。
  既有两条路径传 `ragSend = false`，走原逻辑一字不变。
- 发送成功后写 `mail_record_rag_fact`（I-42），**不写** `mail_record_qa_rule`。
- 既有两条路径的代码块**一行不改**（What must NOT change 第 1、2 条）。

遵循 I-39 ~ I-43。

### T4 — 端点 DTO
修改 `mail/controller/UnmatchedInboundMailController.kt`：
在发送请求 DTO（`:1012` 与 `:1027` 两处，以实际使用的那一个为准，执行时 grep 确认）
新增 `val ragFactCodes: List<String>? = null` 与 `val ragCorpusFingerprint: String? = null`，
并透传给服务。既有 `qaRuleIds` 字段与类型**不动**。

### T5 — 前端采用路径
修改 `src/main/resources/static/app.js`：
- `adoptTrustReplyAssembly`（`:10269`）：根据 `assembly` 的形态分流——
  含 `usedFactCodes` 时装配 `manualReplyQaContext = { ragFactCodes, ragCorpusFingerprint,
  baselineText }`；含 `canonicalFactIds` 时保持现有 `qaRuleIds` 装配不变。
- RAG 形态下**跳过** `schedulePreflightCheck()`（I-44）。
- 发送请求组装处按 `manualReplyQaContext` 的形态选择携带 `qaRuleIds` 还是
  `ragFactCodes + ragCorpusFingerprint`。

遵循 I-39、I-44。

### T6 — 测试
新建 `src/test/kotlin/.../mail/RagSendBridgeTest.kt`：
- 三路互斥：assembly + ragFactCodes 同时给 → 400（I-39）
- 指纹缺失 → 400；指纹不符 → 409（I-41）
- 未知/已停用 fact_code → 422（I-40）
- 成功发送后 `mail_record_rag_fact` 有序写入且 `mail_record_qa_rule` **零新增**（I-39、I-42）
- 断言 RAG 分支内未调用 `canonicalizeFactRuleIds` 与 `qaFactSelectionService.select`
  （用 mock 断言零交互，I-40）
- 断言 `mail_record.matched_qa_rule_id` 为 null（I-43）
- **回归**：旧 assembly 路径与 legacy `qaRuleIds` 路径各一条冒烟，断言行为未变

新建 `src/test/js/ragAdoptSendBridge.test.js`：
- 断言 `usedFactCodes` 形态下 `manualReplyQaContext` 含 `ragFactCodes` 且不含 `qaRuleIds`
- 断言该形态下 `schedulePreflightCheck` 未被调用（I-44）
- 断言 `canonicalFactIds` 形态下行为与改动前一致

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecordRagFact.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRagFactRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改（新增 RAG 分支与两个可空参数） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改（DTO 新增两字段并透传） |
| 6 | `src/main/resources/static/app.js` | 修改（采用路径分流 + 跳过 preflight） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt` | 新增 |
| 8 | `src/test/js/ragAdoptSendBridge.test.js` | 新增 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/UnmatchedInboundTrustWorkbenchTest.kt` | 修改（A3 授权：sendManualRichReply 增 2 形参后 4 处 stub 补 `Mockito.any()` 匹配器；先例 a21784e；K-计划必需回归门禁） |

文件数 9（8 新增/修改 + 1 处 A3 授权的既有测试修改），子系统 2（mail 后端发送链 + 前端采用路径）。
**注意**：本计划改 `app.js` 但不改 `index.html`，故**不 bump 缓存键**——
`app.js` 的 `?v=` 由紧随其后的 05 统一 bump。若 03b 与 05 分开发布，
则 03b 必须自行 bump 并把 `index.html` 与 `batchSendTaskConsoleVisualFix.test.js` 加进清单
（届时文件数 10，仍不超限）。执行时按实际发布节奏二选一，在 PR 中注明。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段
> （`pom.xml:186-232`）。

```bash
# 全量测试（回归门禁，含前端 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagSendBridgeTest
node --test src/test/js/ragAdoptSendBridge.test.js

# 旧发送路径回归（必须全绿）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnmatchedInboundTrustWorkbenchTest

# 迁移集成测试（需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 前端 JS 全量 + 语法检查
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 既有校验块未被改动（应只在 diff 中看到新增分支，不见 181-215 行的改写）
git diff src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt

# 样式与 index.html 零改动
git diff --stat src/main/resources/static/styles.css src/main/resources/static/index.html

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且 `# fail 0`；
`git diff --stat` 对 styles.css / index.html 无输出（或按上文注记只含缓存键）；
`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands + `CLAUDE.md:66` 团队沉淀知识。

## 验收标准

- **I-39**：`RagSendBridgeTest` 断言同时携带 `assembly` 与 `ragFactCodes` 返回 400
  且 code 为 `SEND_EVIDENCE_SOURCE_CONFLICT`；断言 RAG 成功发送后
  `mail_record_qa_rule` 新增行数为 0。
- **I-47**：断言 RAG 发送产生的 findings 中**不含** `QA_FACTS_ALL_INVALID`；
  用 mock 断言整次 RAG 发送对 `qaFactSelectionService` 与 `aiReplyDraftService`
  **零交互**；断言纯文本检查仍会触发——构造一份含虚构数字的正文，
  断言 findings 含 `WARNING_CLAIM_HALLUCINATED_FACT`，二次确认照常要求。
- **I-40**：用 mock 断言 RAG 分支内 `qaRuleRepository` 与 `qaFactSelectionService` 零交互；
  `grep -n "canonicalizeFactRuleIds" ` 在 RAG 分支代码块内无命中。
- **I-41**：断言指纹为空 → 400 `RAG_FINGERPRINT_REQUIRED`；
  指纹与当前不符 → 409 `RAG_CORPUS_STALE`，且**未产生任何发送尝试记录**。
- **I-42**：断言 `mail_record_rag_fact` 的 `ordinal` 与请求中 `ragFactCodes` 的下标一一对应，
  重复的 code 不被去重、顺序不被排序。
- **I-43**：断言 RAG 发送后 `mail_record.matched_qa_rule_id` 为 null。
- **I-44**：`ragAdoptSendBridge.test.js` 断言 RAG 形态下 `schedulePreflightCheck` 的调用次数为 0。
- 回归：`PendingMailOperationServiceTrustWorkbenchTest` 与
  `UnmatchedInboundTrustWorkbenchTest` 全绿；
  执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: RAG 草稿能真的发出去
- 前置条件: 01-03 已落地；有一封待处理来信；发件账号已绑定。
- 操作步骤:
  1. 在工作台生成一份 RAG 草稿。
  2. 点「采用到人工回复」。
  3. 在富文本区确认正文完整。
  4. 点发送，走完二次确认，真正发出（可发到自己的测试邮箱）。
  5. 查库 `SELECT fact_code, ordinal, corpus_fingerprint FROM mail_record_rag_fact
     WHERE mail_record_id = <新记录 id> ORDER BY ordinal;`
- 预期结果: 第 4 步发送成功，**不出现** 400 / 422；
  第 5 步返回若干行，`fact_code` 顺序与工作台右栏「用到了哪些事实」的顺序完全一致，
  `corpus_fingerprint` 等于工作台页头显示的指纹。
- 覆盖: 需求 observable outcome 1、2；I-42

### A-2: 语料改了就不让发（跨路径）
- 前置条件: 04 已落地（可在知识库页改事实）；A-1 已通过。
- 操作步骤:
  1. 在工作台生成一份草稿，采用到人工回复区，**先不发**。
  2. 另开一个标签页进「RAG 知识库」，把某条事实的正文改一个字并保存。
  3. 回到第一个标签页，点发送。
  4. 把事实改回原值，回到第一个标签页重新生成草稿，再发送。
- 预期结果: 第 3 步返回 **409**，提示语料已变化需重新生成，**邮件没有发出**；
  第 4 步发送成功。
- 覆盖: 需求 observable outcome 3；I-41

### A-3: 未知或已停用的事实会被拒
- 前置条件: A-1 已通过。
- 操作步骤: 用 curl 直接发送，body 里 `ragFactCodes` 含 `KB-XXX-999`。
- 预期结果: 返回 422，code 为 `RAG_FACT_CODE_UNKNOWN`；邮件未发出。
- 覆盖: I-40

### A-4: 旧发送路径完全没变（回归 + 跨路径）
- 前置条件: 05 尚未落地，旧工作台仍可用。
- 操作步骤:
  1. 用旧可信工作台走一遍：生成 → 整合 → 采用到人工回复 → 发送。
  2. 查库 `SELECT COUNT(*) FROM mail_record_qa_rule WHERE mail_record_id = <新记录 id>;`
  3. 查库 `SELECT COUNT(*) FROM mail_record_rag_fact WHERE mail_record_id = <同一 id>;`
  4. 再用不带 assembly 的 legacy 路径发一封（如果该入口仍可达）。
- 预期结果: 第 1 步与本计划实施前完全一致；第 2 步 > 0；第 3 步 **= 0**；
  第 4 步行为不变。
- 覆盖: What must NOT change 第 1、2、3 条；现状审计 Interaction point 1；I-39

### A-4b: RAG 发送不产生看不懂的告警码
- 前置条件: A-1 已通过。
- 操作步骤: 在 RAG 路径点发送，观察二次确认弹窗里列出的告警项。
- 预期结果: **不出现** `QA_FACTS_ALL_INVALID`；若正文里有虚构数字或链接，
  仍会出现对应的文本类告警并要求确认。
- 覆盖: I-47

### A-5: 监控页不会因为 null 而报错（跨路径）
- 前置条件: A-1 已通过（库中已有一封 RAG 发出的信）。
- 操作步骤: 打开邮件监控页，找到这封信那一行。
- 预期结果: 页面正常渲染，「命中规则」列为**空**（不是报错、不是「undefined」）；
  其余列正常。
- 覆盖: 现状审计 Interaction point 2；I-43

### A-6: 二次确认没被绕过（回归）
- 前置条件: A-1 已通过。
- 操作步骤: 在 RAG 路径点发送，观察确认流程。
- 预期结果: 二次确认框正常弹出、要求输入确认词，与旧路径一致；取消则不发送。
- 覆盖: What must NOT change 第 4 条

## 已登记的后续项

- **X-5**：监控页「命中规则」列对 RAG 回信显示为空。若要显示，需给监控页增加读
  `mail_record_rag_fact` 的分支，并决定展示 `fact_code` 还是 `rag_fact.title`。
