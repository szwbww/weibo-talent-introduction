# 组装台分段高亮预览 + 拷贝到人工富文本回复

## 需求描述

可观察结果：人工回复「组装台」的合并预览从「单个纯文本 textarea」改为「只读分段块」，每段标注其来源（尊语/致谢/开场白/某条 QA 规则/自由文本/结束敬语），按 `敬语 → 致谢 → 开场白 → 规则段(运营序) → 自由文本 → 结束敬语` 顺序拼接；规则段按所属 QA 规则高亮区分。去掉「润色」「发送组装回复」两个按钮，新增「拷贝到人工富文本回复」按钮，把组装结果灌入下方已有的「人工富文本回复」编辑器，由该编辑器统一编辑并发送。发送时若带有选中的 QA 规则，QA 命中审计（`mail_record_qa_rule` 关联 + 用量报表）保持不丢。

不可改变：
- 入站**自动**回复管线（`QaMatchService` / `QaReplyComposer.compose` / 自动序常量 `GREETING`/`CLOSING`）行为不变。
- QA 用量审计报表（`QaRuleAuditService.aggregateRuleUsage`）的聚合语义、字段、查询的 action type 不变。
- 「人工富文本回复」区**纯人工**回复（不经组装台、无 QA 规则）的现有行为不变。
- AI 生成回复面板（`ai-reply/turn`、`ai-adopt-draft`）行为不变。
- 现有 `composed-reply`、`composed-reply/polish` 后端接口保持存在与原行为（仅前端不再调用）。

超出范围（显式延后）：
- 删除已废弃的 `composed-reply` / `polish` 接口、`sendManualComposedReply`、`LlmStitchService.composeDeterministic`、`composeInOperatorOrder` 及其测试。
- 调整 `QaReplyComposer.composeInOperatorOrder` 的拼接顺序（保持原样，因其不再处于实时发送路径）。
- 在分段预览内做行内编辑 / 富文本格式（分段是只读展示，编辑只在富文本编辑器里发生）。
- 报表 UI（`actionTypeLabel` 之外的 QA 报表页面）改动。

## 关键不变量

### Invariant I-1: 实时预览拼接顺序
- Rule: 组装台实时预览的段顺序固定为 `salutation → ack → greeting → ruleSections(按 composedReplyState.selectedRuleIds 运营序) → freeText → closing`。注意 `freeText` 位于 `closing` **之前**（与旧逻辑把自由文本放在 closing 之后不同）。
- Applies to: 前端 `app.js` 新增的分段构建函数（替换 `buildDeterministicComposedPreview`），以及由分段拼出的「合并文本」（用于拷贝）。
- Violation consequence: 运营看到的草稿顺序与拷贝进编辑器的文本顺序不一致，或结束敬语被错放到自由文本之后。
- 来源: K-composed-reply-order-contract

### Invariant I-2: 分段只读 + 来源归属
- Rule: 合并预览以只读分段块渲染；每个 QA 规则段必须带 `data-rule-id` 与可见来源标签（`displayName`/`sectionTitle`/`规则 #id`）；尊语/致谢/开场白/自由文本/结束敬语各为独立带类型标签的块。最终正文唯一可编辑面是 `#manualRichReplyEditor`。
- Applies to: 新分段渲染函数、移除 `#composedReplyPreview` textarea 及其 `input` 监听与 `previewEdited` 逻辑。
- Violation consequence: 运营无法分辨某段来自哪条规则；或预览仍可编辑导致与拷贝文本/编辑器三处再次漂移。
- 来源: K-suggested-rule-pin-order

### Invariant I-3: QA 审计连续性（核心）
- Rule: 当人工富文本回复携带非空 `qaRuleIds` 时，`sendManualRichReply` 必须：(a) 按运营序写入 `mail_record_qa_rule(mailRecordId, qaRuleId, ordinal)` 行；(b) 记录 `OperatorActionType.SEND_MANUAL_COMPOSED_REPLY` 日志，`after` 至少含 `mailRecordId, qaRuleIds, suggestedRuleIds, edited, freeTextPreview, ackSnippetId, sendStatus, subject, bodyPreviewText`。这样 `QaRuleAuditService` 无需任何改动即可继续聚合（它只查 `SEND_MANUAL_COMPOSED_REPLY` 并经 `resolveSelectedRuleIds` 走 `mail_record_qa_rule`）。
- Applies to: 后端 `PendingMailOperationService.sendManualRichReply`。
- Violation consequence: 运营调整规则集后审计报表的 added/removed/edited/freeText topic 统计丢失或失真。
- 来源: K-audit-selected-source、K-audit-free-text-topic

### Invariant I-4: 空 qaRuleIds = 纯人工，不进 QA 审计
- Rule: `qaRuleIds` 为空/缺省时，`sendManualRichReply` 维持现状：`mailType=MANUAL_RICH_REPLY`、`matchedQaRuleId=null`、不写 `mail_record_qa_rule`、日志 action 为 `SEND_MANUAL_RICH_REPLY`。空规则集**不得**被任何全集兜底填充。
- Applies to: 后端 `sendManualRichReply` 分支；前端「拷贝到人工富文本回复」只在 `selectedRuleIds` 非空时写入 QA 上下文。
- Violation consequence: 把无关规则写进 `mail_record_qa_rule`，污染审计。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-5: 不触碰自动管线与共享 composer
- Rule: 本次仅改实时人工路径（前端预览 + `sendManualRichReply`）。不得修改 `QaReplyComposer`（含 `compose`/`composeInOperatorOrder`/`GREETING`/`CLOSING`）。前端预览顺序与 `composeInOperatorOrder` 顺序出现分歧是允许的，因为后端 composer 已不在实时发送路径上。
- Applies to: 全部后端 QA composer 文件保持不动。
- Violation consequence: 波及入站自动回复正文，扩大回归面。
- 来源: K-manual-frame-three-consumers、K-gap-items-compose-only

## 现状审计

### `mail_record_qa_rule` 表（QA 命中关联）
- Schema: `MailRecordQaRule(mailRecordId, qaRuleId, ordinal)`，按 ordinal 升序代表运营序。
- Write paths:
  1. `PendingMailOperationService.sendManualComposedReply`（行 337-345）— 发送组装回复时按 `qaRuleIds` 顺序写入。**本计划：实时路径不再调用此方法（前端移除 send-composed-reply）。**
  2. （新增）`PendingMailOperationService.sendManualRichReply` — 携带 qaRuleIds 时写入（I-3）。
- Read paths:
  1. `QaRuleAuditService.resolveSelectedRuleIds`（行 79-81）— `findByMailRecordIdOrderByOrdinalAsc`，作为 selected 真值；空则回退日志 `qaRuleIds`。
- Interaction points: 写入点(2新增) × 读取点(1) —— 这是 I-3 的核心耦合，必须让 rich-reply 写入与 audit 读取同源。

### `operator_action_log` 表
- Write paths（相关）:
  1. `sendManualComposedReply`（行 347-370）— action `SEND_MANUAL_COMPOSED_REPLY`，after 含 suggestedRuleIds/qaRuleIds/edited/freeTextPreview/ackSnippetId/mailRecordId。
  2. `sendManualRichReply`（行 218-233）— 当前 action `SEND_MANUAL_RICH_REPLY`，after 含 mailRecordId/sendStatus/subject/bodyPreviewText。**本计划：带 qaRuleIds 时改记 `SEND_MANUAL_COMPOSED_REPLY` 并补齐 after 字段（I-3）；不带时维持原样（I-4）。**
- Read paths:
  1. `QaRuleAuditService.aggregateRuleUsage`（行 18-27）— 只按 `SEND_MANUAL_COMPOSED_REPLY` 查询。**因此选用 Approach A：带 QA 的 rich reply 复用 COMPOSED action type，审计服务零改动。**
  2. 前端 `app.js:actionTypeLabel`（行 4486-4497）— 操作日志展示用标签表，**当前缺 `SEND_MANUAL_COMPOSED_REPLY` 标签**，需补上以正确显示。

### 前端组装台（`app.js`）
- `composedReplyState`（行 59-68）：`recordId, suggest, selectedRuleIds, freeText, previewEdited, baselinePreview, activeGapIndex, ackSnippetId`。
- 拼接：`buildDeterministicComposedPreview`（行 4970-4989）顺序 `[salutation, ack, greeting, sections, closing, freeText]`。
- `refreshComposedPreviewFromRules`（行 5125-5141）写入 `#composedReplyPreview` textarea + `baselinePreview`。
- 渲染：`renderComposedWorkbenchHtml`（行 5321-5357）含 `#composedReplyPreview` textarea、`composedPolishBtn`、`send-composed-reply` 按钮。
- 监听/初始化：`initComposedReplyWorkbench`（行 5167-5221）绑定 ack chip、rule checkbox、freeText input、preview input(previewEdited)、polish 按钮显隐。
- 事件处理（行 5784-5900）：`polish-composed-reply`、`send-composed-reply`；`ai-adopt-draft`（行 5843-5867）在 `qaIds` 非空时填 `#composedReplyPreview` 并提示「发送组装回复」。
- 人工富文本回复区（行 5575-5586）：`#manualReplySubject`、`#manualRichReplyEditor`、`send-manual-rich-reply`。
- `send-manual-rich-reply` 处理（行 5901-5932）：取 subject/innerHTML/innerText，调 `manual-rich-reply`，**当前请求体无 qaRuleIds**。
- Interaction points: `ai-adopt-draft` 当前依赖 `#composedReplyPreview` 与「发送组装回复」存在 —— 移除后该分支需改道（见 实现方案 任务 4）。

## 实现方案

### 阶段 A — 前端组装台改造（`app.js` + `styles.css`）

**任务 A1：拼接顺序 + 分段数据模型**（I-1, I-2）
- 文件：`src/main/resources/static/app.js`
- 新增 `buildComposedSegments(selectedRuleIds, suggest, freeText, ackContent)`，返回有序数组，元素形如 `{ type, ruleId?, label, text }`：
  - `salutation`（label「尊语」，text=`suggest.salutation`）— 有才加
  - `ack`（label「致谢」）— 有才加
  - `greeting`（label「开场白」，text=`suggest.greeting`）— 有才加
  - 每条规则一段：`{ type:'rule', ruleId, label: displayName||sectionTitle||'规则 #id', text: sectionTitle? `${title}\n${replyBody}` : replyBody }`，顺序按 `selectedRuleIds`
  - `freeText`（label「自由文本」）— trim 非空才加
  - `closing`（label「结束敬语」，text=`suggest.closing`）— 有才加
- 保留 `buildDeterministicComposedPreview` 仅作为「合并文本」生成器，或新增 `mergeSegmentsToText(segments)`=`segments.map(s=>s.text).join("\n\n")`，并令其顺序与 A1 一致（freeText 在 closing 前）。`baselinePreview` 用该合并文本。

**任务 A2：只读分段渲染 + 移除 textarea/编辑态**（I-2）
- 文件：`app.js`、`styles.css`
- `renderComposedWorkbenchHtml`：把 `#composedReplyPreview` textarea 替换为容器 `<div id="composedPreviewSegments" class="compose-preview-segments"></div>`。移除「合并预览」textarea。
- 新增 `renderComposedPreviewSegments()`：由 `buildComposedSegments(...)` 渲染只读块；每块结构：`<div class="compose-seg compose-seg-<type>" data-rule-id="..(rule only).."><span class="compose-seg-tag">来源标签</span><div class="compose-seg-body pre">text</div></div>`。规则段按 ruleId 取调色板色（`compose-seg-rule-<n>`，n 由 selectedRuleIds 序号取模）。
- `refreshComposedPreviewFromRules`：改为调用 `renderComposedPreviewSegments()`（不再写 textarea），仍更新 `baselinePreview`、`renderComposedGapList`、`renderComposedSelectedList`。
- `initComposedReplyWorkbench`：移除 `#composedReplyPreview` 的 `input` 监听与 `previewEdited`、移除 polish 按钮显隐逻辑。`composedReplyState.previewEdited` 字段保留定义但不再用于发送（或删除引用，见任务 4）。
- `styles.css`：新增 `.compose-preview-segments`、`.compose-seg`、`.compose-seg-tag`、`.compose-seg-body`、各类型/规则色板（`salutation/ack/greeting/freeText/closing` 中性色 + `rule-0..rule-5` 高亮色）。
- 可选增强（低优先，cheap）：`applyGapHighlight` 在高亮左侧规则项时，同步给 `#composedPreviewSegments [data-rule-id=X]` 加 `gap-highlight` 类。

**任务 A3：替换按钮 —— 移除润色/发送组装，新增拷贝**（I-2, I-4）
- 文件：`app.js`
- `renderComposedWorkbenchHtml`：删除 `composedPolishBtn` 与 `send-composed-reply` 按钮；新增 `<button data-action="copy-to-manual-rich-reply" data-record-id="${recordId}">拷贝到人工富文本回复</button>`。
- 新增事件分支 `copy-to-manual-rich-reply`：
  - 若 `composedReplyState.selectedRuleIds` 为空且 freeText 为空 → `showStatus("请至少选择一条规则或填写自由文本","error")` 返回。
  - 计算 `mergedText = composedReplyState.baselinePreview`。
  - `#manualRichReplyEditor`.innerText = mergedText（复用 `ai-adopt-draft` 写法）。
  - 若 `#manualReplySubject` 为空且有选中规则：用首条选中规则的 `replySubject` 预填（无则留空）。
  - 写入 QA 上下文到新状态 `manualReplyQaContext = { qaRuleIds:[...selectedRuleIds], suggestedRuleIds:[...suggest.suggestedRuleIds], freeText: composedReplyState.freeText, ackSnippetId: composedReplyState.ackSnippetId, baselineText: mergedText }`（仅当 selectedRuleIds 非空；否则置 null —— I-4）。
  - `showStatus("已拷贝到人工富文本回复区，可编辑后发送")`，滚动到该区。
- 新增模块级 `let manualReplyQaContext = null;`，并在 `showUnmatchedDetail` / `initComposedReplyWorkbench` 重置为 null。

**任务 A4：`ai-adopt-draft` 改道 + 发送携带 QA 上下文**（I-3, I-4）
- 文件：`app.js`
- `ai-adopt-draft`（行 5843-5867）：原 `qaIds` 非空分支依赖 `#composedReplyPreview` 与「发送组装回复」，现统一改为：把草稿写入 `#manualRichReplyEditor`，若 `aiReplyState.lastQaRuleIds` 非空则同时设置 `manualReplyQaContext`（qaRuleIds=lastQaRuleIds，suggestedRuleIds=lastQaRuleIds，freeText=null，baselineText=draft），提示「草稿已填入人工富文本回复区」。空则 `manualReplyQaContext=null`（纯人工）。
- `send-manual-rich-reply`（行 5901-5932）：请求体在 `manualReplyQaContext` 非空且 `qaRuleIds.length>0` 时追加：
  - `qaRuleIds`, `suggestedRuleIds`, `ackSnippetId`
  - `freeTextPreview = (manualReplyQaContext.freeText||"").trim().slice(0,200) || null`
  - `edited = editor.innerText.trim() !== (manualReplyQaContext.baselineText||"").trim()`
  - 发送成功后 `manualReplyQaContext=null`。
- `actionTypeLabel` map 补 `SEND_MANUAL_COMPOSED_REPLY: "组装 QA 回复"`。

### 阶段 B — 后端 rich reply 承接 QA 审计（`PendingMailOperationService.kt` + 控制器）

**任务 B1：扩展请求 DTO**（I-3, I-4）
- 文件：`src/main/kotlin/.../mail/service/PendingMailOperationService.kt`
- `PendingManualRichReplyRequest` 增加可选字段：`qaRuleIds: List<Long>? = null`、`suggestedRuleIds: List<Long>? = null`、`ackSnippetId: Long? = null`、`edited: Boolean? = null`、`freeTextPreview: String? = null`。
- 文件：`src/main/kotlin/.../mail/controller/UnmatchedInboundMailController.kt`
- `sendManualRichReply` 控制器把新字段透传给 service（扩展方法签名）。

**任务 B2：`sendManualRichReply` 携带 QA 时的双写**（I-3, I-4, I-5）
- 文件：`PendingMailOperationService.kt`
- 方法签名追加上述参数。逻辑：
  - 计算 `val carriesQa = !qaRuleIds.isNullOrEmpty()`。
  - `carriesQa` 时：校验每个 ruleId 存在且 enabled（复用 composed 写法）；`val matches = rules.map { QaRuleMatch(it,1) }`；`val primaryRuleId = QaReplyComposer.selectPrimary(matches).rule.id`。
  - `MailRecord`：`mailType` 仍为 `MANUAL_RICH_REPLY`；`matchedQaRuleId = if (carriesQa) primaryRuleId else null`。
  - `carriesQa` 时按 `qaRuleIds` 顺序写 `mail_record_qa_rule` 行（复用 composed 的 forEachIndexed）。
  - 日志：`carriesQa` 时 action=`SEND_MANUAL_COMPOSED_REPLY`，after 含 `mailRecordId, qaRuleIds, suggestedRuleIds(?:emptyList), ackSnippetId, edited(?:false), freeTextPreview, sendStatus, subject, bodyPreviewText`；否则维持现状 action=`SEND_MANUAL_RICH_REPLY`、原 after。
- **不**调用 `QaReplyComposer.composeInOperatorOrder`（正文用 htmlBody/textBody 原样发送，I-5）。

### 阶段 C — 测试

**任务 C1：后端测试**（I-3, I-4）
- 文件：`src/test/kotlin/.../mail/service/PendingMailOperationServiceTest.kt`
- 新增：`sendManualRichReply` 带非空 `qaRuleIds` → 断言写入 `mail_record_qa_rule`（顺序）、`matchedQaRuleId=primary`、日志 action=`SEND_MANUAL_COMPOSED_REPLY` 且 after 含 suggestedRuleIds/edited/freeTextPreview。
- 新增：`sendManualRichReply` 空 `qaRuleIds` → 不写 `mail_record_qa_rule`、action=`SEND_MANUAL_RICH_REPLY`、`matchedQaRuleId=null`。
- 复核：`QaRuleAuditServiceTest` 不需改动（验证 Approach A 的零改动结论）。

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|------|------|--------|
| 1 | `src/main/resources/static/app.js` | 分段构建/渲染、移除 textarea+润色+发送组装、新增拷贝按钮与处理、ai-adopt 改道、send-manual-rich-reply 携带 QA、actionTypeLabel 补标签 | 前端 |
| 2 | `src/main/resources/static/styles.css` | 分段块 + 色板样式 | 前端 |
| 3 | `src/main/kotlin/.../mail/service/PendingMailOperationService.kt` | DTO 扩字段 + `sendManualRichReply` 双写分支 | 后端 |
| 4 | `src/main/kotlin/.../mail/controller/UnmatchedInboundMailController.kt` | 透传新字段 | 后端 |
| 5 | `src/test/kotlin/.../mail/service/PendingMailOperationServiceTest.kt` | 新增两条用例 | 后端 |

文件数：5（≤10）。子系统：2（前端静态资源 / 后端 Kotlin）。新增共享存储字段：0（`mail_record_qa_rule` 复用既有写法，无新列、无新迁移）。

## 验收标准

- I-1：组装台勾选 ≥2 条规则 + 填自由文本，分段顺序为 尊语→致谢→开场白→规则段→自由文本→结束敬语；拷贝进编辑器的文本顺序与之一致（结束敬语在自由文本之后）。
- I-2：合并预览区无 textarea、不可编辑；每个规则段显示来源规则名且不同规则块颜色不同；尊语/致谢/开场白/自由文本/结束敬语各有类型标签。「润色」「发送组装回复」按钮不存在；存在「拷贝到人工富文本回复」按钮。
- I-3：勾选规则 → 拷贝 → 编辑器内改几个字 → 发送；DB `mail_record_qa_rule` 出现该 mailRecord 的关联行（按运营序 ordinal）；`operator_action_log` 出现 `SEND_MANUAL_COMPOSED_REPLY`，after.edited=true、含 suggestedRuleIds/freeTextPreview；QA 用量报表能统计到该次（added/removed/edited）。后端单测 C1 通过。
- I-4：直接在人工富文本回复区手打正文（未经组装台/AI）发送 → 无 `mail_record_qa_rule` 行、日志 action=`SEND_MANUAL_RICH_REPLY`、`matchedQaRuleId=null`。
- I-5：`QaReplyComposer.kt` 与自动回复相关文件 0 改动（git diff 确认）；`QaRuleAuditServiceTest` 不改仍全绿。
- 构建/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿。
- 集成：`ai-adopt-draft` 采用带 QA 子集的草稿 → 落入人工富文本回复区并携带 QA 上下文；采用空子集草稿 → 落入编辑器但为纯人工（无 QA 上下文）。

## 自检清单

- [x] 关键不变量 ≥1/新状态：I-1..I-5 覆盖顺序、只读归属、审计双写、空集纯人工、不触自动管线
- [x] 现状审计列出每个被触存储的全部写/读路径（grep 确认：`mail_record_qa_rule` 2 写 1 读；`operator_action_log` 相关 2 写 2 读）
- [x] 无任务引入未被不变量覆盖的写路径（rich-reply 新写路径由 I-3/I-4 治理）
- [x] 文件数 5 ≤ 10
- [x] 子系统 2 ≤ 2
- [x] 每个任务引用其治理不变量编号
- [x] 验收每条不变量至少一项检查
- [x] 文件清单无「等」「相关文件」，逐一具名
- [x] 超出范围显式列出（删废弃接口/改 composer/分段行内编辑）
- [x] Phase 0 知识被使用：K-manual-frame-three-consumers(I-5)、K-composed-reply-order-contract(I-1)、K-audit-selected-source(I-3)、K-ai-reply-prompt-vs-send-rule-ids(I-4)、K-gap-items-compose-only(I-5)、K-suggested-rule-pin-order(I-2)、K-audit-free-text-topic(I-3)
- [x] 已保存至 docs/plans/2026-06-30/

## 设计决策记录（供 fix-v）

- **Approach A（审计 action 复用）**：带 QA 的人工富文本回复记 `SEND_MANUAL_COMPOSED_REPLY` 而非扩展 `QaRuleAuditService` 查询多个 action type。理由：`QaRuleAuditService.aggregateRuleUsage` 仅查单一 action type，复用可让审计服务/测试零改动，最小化耦合面；代价是该日志 action 标签语义偏「组装」而非「纯富文本」，但其本质确为「基于 QA 规则组装后发出」，可接受。
- **后端不再 compose**：实时人工路径正文以编辑器 htmlBody/textBody 原样发送，后端 `composeInOperatorOrder` 退出实时路径，因此前端预览顺序变更（freeText 前置）无需同步后端 composer（K-manual-frame-three-consumers 的三消费者同步约束在「后端 compose 不在实时路径」前提下解除）。废弃的 `composed-reply`/`polish` 接口保留不删（超范围）。
