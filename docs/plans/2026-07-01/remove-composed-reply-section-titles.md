# 开发计划：移除组装回复正文中的子标题（sectionTitle）

> 由 create-p 生成。范围仅限「正文去子标题」。开场白/结束语可选（chip）不在本计划范围，见「不在范围」。

## 需求描述

- **可观察结果**：QA 组装回复（人工组装台预览、外发正文、LLM 润色确定性回退、自动回复拼接）中，每条规则只输出 `replyBody`，不再输出 `sectionTitle`（如 "Role & work style"、"Program & eligibility"）。段与段之间仍以空行分隔。这样多条同标题规则不再产生重复标题，整体也更像真人手写。
- **不可改变**：
  - 规则的排序契约（`qaRuleIds` 顺序 = 预览/外发/审计 ordinal 顺序，来源 K-composed-reply-order-contract）。
  - 开场白（GREETING）、结束语（CLOSING）、尊语（salutation）、致谢（ack）、自由文本的位置与拼接顺序。
  - 预览分段与规则的一一对应（1 规则 = 1 预览段、`data-rule-id`），缺口高亮/点击定位行为（K-gap-items-compose-only）。
  - `sectionTitle` 字段本身：仍保留在 `QaRule` 领域对象、DTO、以及前端**界面标签**（片段面板/已选列表/预览段标签）中，仅作运营导航用途。
- **不在范围（明确推迟到后续计划）**：
  - 开场白/结束语「每封可选/行内编辑」（此前讨论的方案 A chip）——独立子系统，涉及 `ReplySnippetService`/Controller DTO/`PendingMailOperationService`/前端 chip，与本计划合并会超 10 文件上限，另立计划。
  - 片段配置页（`reply-snippets`）本身不改。

## 关键不变量

### Invariant I-1: 组装正文不含 sectionTitle
- Rule: 所有「把规则拼进邮件正文」的 composer 只使用 `rule.replyBody`，绝不 prepend `sectionTitle`。共 3 个正文 composer（同源同序，改一必改三，来源 K-manual-frame-three-consumers）：
  1. 后端 `QaReplyComposer.formatSection`（被 `compose` 自动序 + `composeInOperatorOrder` 运营序共用）；
  2. 后端 `LlmStitchService.buildRuleSegments`（喂 LLM 的 SEGMENT 段落，走确定性回退时同样影响正文）；
  3. 前端 `app.js:buildComposedSegments`（预览 `type:"rule"` 段的 `text`）。
- Applies to: 上述 3 处；间接覆盖 `sendManualComposedReply`、`sendManualRichReply`（baseline 由预览生成）、自动回复管线 `AutoMailReplyService`→`QaMatchService`→`QaReplyComposer.compose`。
- Violation consequence: 只改一处会造成预览/外发/润色漂移；漏改自动序会让自动回复仍带标题。
- 来源: K-manual-frame-three-consumers

### Invariant I-2: sectionTitle 作为 UI 标签保留
- Rule: `sectionTitle` 字段不得从 `QaRule`、`SuggestQaRule(Response)`、前端 rule 对象删除；前端 `compose-seg-tag`、已选列表 label、片段面板 label 仍可用 `displayName || sectionTitle || 规则#id` 兜底显示。
- Applies to: `app.js` 第 5025 行（label）、5157、5371；后端 DTO passthrough（`QaMatchService.kt` / `UnmatchedInboundMailController.kt`）。
- Violation consequence: 删字段会破坏运营在组装台辨识规则的能力，且触发无关的 DTO/前端改动（越界）。
- 来源: original

### Invariant I-3: 顺序与预览-规则映射不变
- Rule: 去标题只删除「标题前缀」，不改变段落数量、顺序、`data-rule-id` 映射。预览仍是每条选中规则一段。
- Applies to: `app.js:buildComposedSegments`（`segments.push({type:"rule", ruleId, ...})` 结构不变，仅 `text` 改为 `rule.replyBody`）；`QaReplyComposer` 两个 composer 的 `joinToString("\n\n")` 不变。
- Violation consequence: 若顺带把「同标题合并成一段」会改变段数/映射，破坏缺口高亮与排序契约（K-gap-items-compose-only、K-composed-reply-order-contract）。本计划明确**不做合并**，直接删标题即可，天然无重复。
- 来源: K-gap-items-compose-only, K-composed-reply-order-contract

## 现状审计

### 正文 composer（写路径 = 生成邮件正文的地方）
- **`QaReplyComposer.kt`**
  - `compose(matches, categoryComposeOrder)`（自动序，行 10-39）：`sections = ordered.joinToString("\n\n") { formatSection(it.rule) }`，body = `GREETING + sections + CLOSING`。单条命中（`matches.size==1`）直接返回 `rule.replyBody`，本就无标题。
  - `composeInOperatorOrder(...)`（运营序，行 42-61）：`sections = matches.joinToString("\n\n") { formatSection(it.rule) }`，body = `salutation, ack, greeting, sections, closing`。
  - `formatSection(rule)`（行 63-70）：**当前 prepend `sectionTitle`**（`"$title\n${rule.replyBody}"`）。← 唯一需改。
- **`LlmStitchService.kt`**
  - `buildRuleSegments(qaRuleIds)`（行 76-82）：**当前 prepend `sectionTitle`**（`"$title\n${rule.replyBody}"`），供 `polishDraft` 的 LLM prompt。← 需改为仅 `replyBody`。
  - `composeDeterministic(...)`（行 51-74）：调 `QaReplyComposer.composeInOperatorOrder`，随 I-1 第 1 处自动生效，无需单独改。
- **`app.js`**
  - `buildComposedSegments(...)`（行 5007-5038）：rule 段 `const text = title ? `${title}\n${rule.replyBody}` : rule.replyBody;`（行 5024-5026）。**当前 prepend 标题**。← 改为 `const text = rule.replyBody;`；label（行 5025）保留。
  - `buildDeterministicComposedPreview` / `mergeSegmentsToText`：随上游生效，不改。

### 非正文（标签/透传，本计划不改，仅登记以证明不越界）
- `app.js` 行 5025 `label`、5157 已选列表、5371 片段面板：`sectionTitle` 作 label 兜底 → 保留（I-2）。
- `QaMatchService.SuggestQaRule.sectionTitle`（行 209-221）、`UnmatchedInboundMailController.SuggestQaRuleResponse.sectionTitle`（行 482-639）：DTO 透传 → 保留（I-2）。
- `AiReplyDraftService.buildMatchedUserContent`（行 181-195）：已是 `SEGMENT n=${rule.replyBody}`，**本就不含标题** → 不改（登记以免误判为遗漏写路径）。
- `QaRule.sectionTitle`（domain 行 18）：字段保留（I-2）。

### 交互点
- 预览（前端 `buildComposedSegments`）× 外发（后端 `composeInOperatorOrder`）× 润色回退（`composeDeterministic`/`buildRuleSegments`）：三者必须同时去标题，否则漂移（K-preview-mirrors-pipeline、K-manual-frame-three-consumers）。这是本计划唯一交互点，由 I-1 覆盖。

## 实现方案

### 阶段 1：后端去标题（obey I-1, I-2, I-3）
- **T1** 改 `QaReplyComposer.formatSection`（`src/main/kotlin/.../qa/service/QaReplyComposer.kt`）：直接返回 `rule.replyBody`。可将该私有方法内联为 `it.rule.replyBody`，或保留方法名仅返回 body（保留方法以减小 diff）。`compose` 与 `composeInOperatorOrder` 的 `joinToString` 结构不动（I-3）。
- **T2** 改 `LlmStitchService.buildRuleSegments`（`src/main/kotlin/.../llm/service/LlmStitchService.kt`）：每段仅 `rule.replyBody`（去掉 title 分支）。

### 阶段 2：前端预览去标题（obey I-1, I-2, I-3）
- **T3** 改 `app.js:buildComposedSegments`（`src/main/resources/static/app.js` 行 5024-5027）：`text = rule.replyBody`；**保留** `label = rule.displayName || rule.sectionTitle || \`规则 #${ruleId}\``（I-2）；`segments.push` 结构、`ruleId`、`ruleIndex` 不变（I-3）。

### 阶段 3：测试
- **T4** `QaReplyComposerTest.kt`：
  - 删除/改写 `multiple matches include section titles when present`（该行为已废除）→ 改为断言正文**不含**标题、仅含 body（如 `assertFalse(replyBody.contains("Funding & timeline"))`，`assertTrue(contains("Funding answer"))`）。
  - `null section title omits heading line without error` 保留，语义仍成立。
  - 新增：两条**同 sectionTitle**规则 → 正文中该标题出现 0 次、两个 body 均在（覆盖原始「重复标题」诉求）。
- **T5** `LlmStitchServiceTest.kt`：新增用例——规则带 `sectionTitle` 时，确定性草稿正文不含该标题、含 `replyBody`。
- **T6** `composedReplyOrder.test.js`：现有用例（断言 Body A/Body B 顺序）不受影响；新增断言 `preview` 不包含规则的 `sectionTitle`（如 `!preview.includes("A")` 用一个不易误判的独特标题值，建议把 fixture 标题改为独特串如 `"SECTITLE_A"` 再断言不出现）。

## 变更文件清单

| # | 文件 | 改动 | 关联不变量 |
|---|------|------|-----------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposer.kt` | `formatSection` 只返回 `replyBody` | I-1, I-3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchService.kt` | `buildRuleSegments` 只用 `replyBody` | I-1 |
| 3 | `src/main/resources/static/app.js` | `buildComposedSegments` rule 段 `text=replyBody`，label 保留 | I-1, I-2, I-3 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposerTest.kt` | 改标题断言 + 加同标题去重用例 | I-1 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchServiceTest.kt` | 加去标题用例 | I-1 |
| 6 | `src/test/js/composedReplyOrder.test.js` | 加「预览不含标题」断言 | I-1, I-3 |

文件数 6 ≤ 10；子系统 1（回复组装）≤ 2；无新增字段。

## 验收标准

- **I-1**：
  - 后端：`QaReplyComposer.compose` 与 `composeInOperatorOrder` 对带 `sectionTitle` 的多规则输入，`replyBody` 不含任何 `sectionTitle` 文本；`LlmStitchService.polishDraft`（LLM 禁用/失败回退）草稿正文亦不含标题。（T4/T5）
  - 前端：`buildDeterministicComposedPreview([id1,id2], suggest,...)` 结果不含规则 `sectionTitle`。（T6）
- **I-2**：`grep sectionTitle` 仍存在于 `QaRule`、`SuggestQaRule(Response)`、`app.js` label 处；组装台已选列表/片段面板/预览段标签仍能显示规则名。
- **I-3**：`composedReplyOrder.test.js` 全绿——预览段数 = 选中规则数，顺序随 `selectedRuleIds`；缺口高亮 `data-rule-id` 定位不变。
- **集成场景（覆盖原始 bug）**：选两条 `sectionTitle="Role & work style"` 的规则，预览与外发正文中 "Role & work style" 出现 **0** 次，两段 body 顺序与勾选顺序一致。
- **回归**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaReplyComposerTest,LlmStitchServiceTest` 全绿；`node --test src/test/js/composedReplyOrder.test.js` 全绿。

## 自检清单

- [x] 关键不变量 存在，且每个受影响行为都有不变量约束（无新字段）
- [x] 现状审计 列出全部正文写路径（grep 验证，非记忆）：`QaReplyComposer.formatSection`、`LlmStitchService.buildRuleSegments`、`app.js:buildComposedSegments`；并登记非正文透传点证明不越界
- [x] 无任务引入未被不变量覆盖的写路径
- [x] 文件数 6 ≤ 10；子系统 1 ≤ 2
- [x] 每个任务引用治理不变量编号
- [x] 验收标准每个不变量至少一条检查
- [x] 文件清单无「相关文件/等」占位，全部具名
- [x] 不在范围 明确推迟开场白/结束语 chip 与片段配置页
- [x] Phase 0 载入的知识（K-manual-frame-three-consumers / K-composed-reply-order-contract / K-preview-mirrors-pipeline / K-gap-items-compose-only）均被使用
- [x] 计划保存至 `docs/plans/2026-07-01/`
