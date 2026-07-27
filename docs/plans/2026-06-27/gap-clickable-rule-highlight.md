# 开发计划：缺口清单可点击 + 高亮可满足缺口的 QA 规则

> 由 create-p 生成（2026-06-27）。仅为逻辑变更计划，前端横向布局样式已先行落地，不在本计划范围。

## 需求描述

可观察结果：
- 在「组装台回复」的缺口清单中，点击某一条缺口项后，片段面板中**能回答该缺口的 QA 规则片段被高亮**（区别于普通/已选状态），便于运营快速找到应勾选的片段。
- 缺口是否「已覆盖」（✓ 绿色 / 计数 N/M）改为**语义判定**：当且仅当该缺口的候选规则中至少有一个被勾选时才算覆盖，取代当前「按下标染前 N 条」的伪逻辑。

不可改变（must NOT change）：
- 入站自动管线的缺口检测（`detectGap` / `QA_GAP` 转人工）行为完全不变。本计划只动「人工组装台」的可视化覆盖与高亮，不动自动回复判定（见 I-3，来源 K-overview-gap-supersede）。
- 规则组装顺序契约：预览 / payload / 后端正文 / 审计 ordinal 仍以 `selectedRuleIds` 当前顺序为准（来源 K-composed-reply-order-contract）。
- 候选规则「建议」置顶排序与默认勾选行为不变（来源 K-suggested-rule-pin-order）。
- QA 审计报表复用 `.compose-gap-list` 的两处渲染（`app.js:1452,1473`）不受影响：新增的点击/高亮样式必须限定在缺口面板作用域内。

超出范围（out of scope）：
- 用 LLM 做缺口→规则的语义匹配。本计划只用既有关键词匹配能力（确定性）。
- 缺口项的人工编辑 / 自定义。
- 片段面板 chip 化的进一步交互（点击 chip 即加入草稿等）。

## 关键不变量

### Invariant I-1: 缺口项结构携带候选规则
- Rule: 后端返回的每个缺口项从 `String` 升级为对象 `{ text: String, candidateRuleIds: List<Long> }`。`candidateRuleIds` 是「关键词命中该缺口文本」的启用规则 id 列表，可能为空（无任何规则能回答）。
- Applies to: `QaMatchService.extractGapItems`（唯一生产者）、`CompositionSuggestResult.gapItems` 类型、`ComposedReplySuggestResponse.gapItems` 类型及映射、前端 `renderComposedGapList`（唯一消费者）。
- Violation consequence: 前端拿不到候选规则就无法高亮；若仍返回裸字符串，前端点击无对象可用。
- 来源: original

### Invariant I-2: 覆盖判定为语义判定，禁止下标染色
- Rule: 缺口 `g` 覆盖 ⟺ `g.candidateRuleIds ∩ selectedRuleIds ≠ ∅`。计数 = 满足该条件的缺口数 / 缺口总数。禁止再出现 `index < coveredCount` 形式的按序染色，`getSelectedCategoryCount` 不得再用于覆盖判定。
- Applies to: 前端 `app.js:renderComposedGapList`、`getSelectedCategoryCount`（覆盖路径上将被移除引用）。
- Violation consequence: 覆盖状态与实际勾选不符，运营误判已答/漏答（即当前缺陷）。
- 来源: original

### Invariant I-3: 自动管线缺口检测不变
- Rule: `detectGap(...)` 的入参、返回与触发 `QA_GAP` 的逻辑保持原样；缺口检测仍使用 supersede **之前**的命中集（`rawMatches`），不得因本次重构改用压缩后的 `matches`。
- Applies to: `QaMatchService.suggestComposition`（`gapDetected = detectGap(...)` 一行）、`QaMatchService.match`、`detectGap` 自身。
- Violation consequence: 概览型多问来信被误转人工（已知历史回归）。
- 来源: K-overview-gap-supersede

### Invariant I-4: 候选匹配复用既有关键词与归一化逻辑
- Rule: 判断「规则能否回答某缺口文本」必须复用与 `matchRule` 一致的归一化（`normalize`）和 `matchMode`（ANY/ALL）关键词匹配语义，只是把匹配目标从整封正文换成单条缺口文本。不得新引入一套相似度算法。
- Applies to: `QaMatchService` 内新增的缺口→规则匹配 helper。
- Violation consequence: 候选集与系统其它匹配口径不一致，运营困惑。
- 来源: original

## 现状审计

### CompositionSuggestResult / 缺口数据（无 DB 表，纯计算 DTO）
- 定义：`QaMatchService.kt:157-164`，`gapItems: List<String>`。
- 生产路径（写）:
  1. `QaMatchService.suggestComposition` `QaMatchService.kt:19` — `val gapItems = extractGapItems(messageBody)`；`:39` 放入结果。
  2. `QaMatchService.extractGapItems` `QaMatchService.kt:98-108` — 正则抽问句（`QUESTION_SENTENCE_PATTERN`）或项目符号行（`BULLET_LINE_PATTERN`），二者取数量较多者，返回 `List<String>`。
- 读路径:
  1. `UnmatchedInboundMailController.kt:480-489` `CompositionSuggestResult.toResponse` — 透传 `gapItems` 进 `ComposedReplySuggestResponse`（`:386` 类型 `List<String>`）。
  2. 前端 `app.js:4634-4652 renderComposedGapList` — 唯一前端消费者；`coveredCount = getSelectedCategoryCount(...)`（按选中分类去重计数），再 `index < coveredCount` 染绿，渲染 ✓/○ 和计数。
- 交互点:
  - 写(`extractGapItems`) × 读(前端 `renderComposedGapList`)：缺口文本如何被覆盖判定 —— 本计划核心交互点。
  - `gapItems` 字段无其它后端/前端消费者（已 grep 确认）。`detectGap`（`:40`、`:60`）与 `gapItems` 相互独立，不读取 `gapItems`。

### 规则匹配相关
- `QaRule` 域：`qa/domain/QaRule.kt` — 关键字段 `keywords`（逗号分隔）、`matchMode`（ANY/ALL）、`categoryId`、`enabled`、`supersedesChildren`。
- `matchRule` `QaMatchService.kt:110-134` — 关键词按 `normalize` 后 `contains` 整封正文匹配；ANY=任一命中，ALL=全部命中。可作为缺口文本匹配的复用蓝本（目标改为单条缺口文本）。
- `findAllEnabledOrdered()` 已用于取启用规则。
- `rulesByCategory` 已含全部启用规则及其 `categoryId`/`id`，前端高亮所需的 id 已具备。

### 前端组装台状态与渲染
- 状态 `composedReplyState` `app.js:58-65`：`suggest`、`selectedRuleIds`、`freeText` 等。
- `findSuggestRule` `app.js:4597-4605` — 按 ruleId 在 `rulesByCategory` 中查规则。
- 规则复选框渲染 `app.js:4785`：`<input class="compose-rule-checkbox" data-rule-id="${rule.id}">`，外层 `<label class="compose-rule-item">`（横向 chip 样式已落地）。
- 勾选事件 `app.js:4739-4749` 更新 `selectedRuleIds` 并 `refreshComposedPreviewFromRules()`。
- 缺口渲染 `app.js:4634-4652`（含本次新增的计数 span `#composedGapCount`）。
- 复用风险：`app.js:1452,1473` 在 QA 审计报表中复用 `.compose-gap-list` class（非 suggest.gapItems），新增点击样式须用 `.compose-gaps .compose-gap-list` 作用域避免污染。

## 实现方案

### 阶段 1：后端 —— 缺口项携带候选规则（I-1、I-4、I-3）

任务 1.1　在 `QaMatchService.kt` 新增缺口数据结构与匹配 helper
- 新增 `data class GapItem(val text: String, val candidateRuleIds: List<Long>)`（与现有 DTO 同文件）。
- 新增私有 `fun matchRuleAgainstText(rule: QaRule, normalizedGapText: String): Boolean`，复用 `normalize` + `matchMode`（ANY/ALL）关键词逻辑（I-4）。可将 `matchRule` 中关键词匹配核心抽出为共享私有函数，避免重复；但**不得改变 `matchRule` 对外行为**。
- 文件：`qa/service/QaMatchService.kt`。

任务 1.2　改造 `extractGapItems` 返回 `List<GapItem>`
- 抽取文本逻辑不变（问句/项目符号，二者取多者）；对每条文本，遍历 `findAllEnabledOrdered()`，用 `matchRuleAgainstText` 收集命中规则 id 作为 `candidateRuleIds`（保持规则原有顺序）。
- 将函数签名改为 `List<GapItem>`，传入启用规则列表参数以避免重复查库。
- obey: I-1、I-4。
- 文件：`qa/service/QaMatchService.kt`。

任务 1.3　更新 `CompositionSuggestResult.gapItems` 类型
- `gapItems: List<String>` → `gapItems: List<GapItem>`（`QaMatchService.kt:161`）。
- `suggestComposition`（`:19`、`:39`）随之调整；**`detectGap` 行（`:40`）不动**（I-3）。
- 文件：`qa/service/QaMatchService.kt`。

### 阶段 2：后端 —— 接口 DTO（I-1）

任务 2.1　新增响应 DTO 并更新映射
- 在 `UnmatchedInboundMailController.kt` 新增 `data class GapItemResponse(val text: String, val candidateRuleIds: List<Long>)`。
- `ComposedReplySuggestResponse.gapItems: List<String>` → `List<GapItemResponse>`（`:386`）。
- `CompositionSuggestResult.toResponse`（`:487`）映射 `gapItems = gapItems.map { GapItemResponse(it.text, it.candidateRuleIds) }`。
- 读路径影响：前端是唯一消费者（阶段 3）。无其它读取方需调整。
- 文件：`mail/controller/UnmatchedInboundMailController.kt`。

### 阶段 3：前端 —— 语义覆盖 + 点击高亮（I-2）

任务 3.1　`renderComposedGapList` 改语义覆盖
- 读 `item.text` / `item.candidateRuleIds`；`covered = candidateRuleIds.some(id => selectedRuleIds.includes(id))`（I-2）。
- 计数 = 覆盖数 / 总数；移除 `getSelectedCategoryCount` 在此处的使用与 `index < coveredCount` 逻辑。
- 每个 `<li>` 加 `data-gap-index`，无候选规则的项标注「无可用规则」弱提示（不可点击或点击无高亮）。
- 写路径喂入：被 `selectedRuleIds` 变化驱动 —— 在勾选事件（`app.js:4749`）与拖拽/移动后一并 `renderComposedGapList()`，保证覆盖随勾选实时更新。
- 文件：`src/main/resources/static/app.js`。

任务 3.2　缺口点击 → 高亮候选规则片段
- 点击缺口项时，给其 `candidateRuleIds` 对应的 `.compose-rule-item`（通过 `.compose-rule-checkbox[data-rule-id]` 定位父 label）加 `.gap-highlight` 类；再次点击同项或点击空白处清除。
- 维护一个「当前高亮缺口」状态（可放 `composedReplyState.activeGapIndex` 或局部变量）；重渲染时保持。
- 不修改 `selectedRuleIds`（高亮 ≠ 勾选）。
- 文件：`src/main/resources/static/app.js`。

任务 3.3　样式：可点击缺口 + 高亮片段（作用域限定）
- `.compose-gaps .compose-gap-list li`：`cursor:pointer`、hover/active 态；当前选中缺口加视觉强调（左边框或背景）。
- `.compose-rule-item.gap-highlight`：明显但区别于 `:has(:checked)` 选中态的高亮（如外环/警示色描边）。
- 必须用 `.compose-gaps` 前缀，避免影响 `app.js:1452,1473` 的审计报表复用。
- 文件：`src/main/resources/static/styles.css`。

### 阶段 4：测试（验收支撑）

任务 4.1　`QaMatchServiceTest` 扩展
- 用例 A：构造含问句的来信 + 关键词能命中的规则 → 断言对应 `GapItem.candidateRuleIds` 含该规则 id。
- 用例 B：无规则可答的缺口 → `candidateRuleIds` 为空。
- 用例 C：ALL 模式规则部分命中 → 不计入候选（I-4）。
- 用例 D：回归 —— 概览型多问来信 `detectGap` 仍按 supersede 前命中集判定，行为不变（I-3）。
- 文件：`src/test/kotlin/.../QaMatchServiceTest.kt`（按现有测试位置）。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | 新增 `GapItem`、`matchRuleAgainstText`；`extractGapItems` 返回 `List<GapItem>`；`CompositionSuggestResult.gapItems` 类型 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 新增 `GapItemResponse`；`ComposedReplySuggestResponse.gapItems` 类型 + 映射 |
| 3 | `src/main/resources/static/app.js` | `renderComposedGapList` 语义覆盖；缺口点击高亮；勾选/排序后刷新缺口 |
| 4 | `src/main/resources/static/styles.css` | 缺口可点击态 + `.gap-highlight` 片段高亮（`.compose-gaps` 作用域） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 候选映射 / 覆盖 / 回归用例 |

文件数：5 ≤ 10 ✓　子系统：QA 建议计算 + 组装台 UI（2）✓　新增共享存储字段：0（纯计算 DTO）✓

## 验收标准

- I-1：调 `GET /api/mail/unmatched-inbound/{id}/composed-reply/suggest`，`gapItems[]` 每项含 `text` 与 `candidateRuleIds`；单测 A/B 通过。
- I-2：勾选某规则后，仅当该规则属于某缺口候选集时该缺口才变绿、计数 +1；取消勾选还原。手工/集成验证 + 无 `index < coveredCount` 残留（grep 断言）。
- I-3：`QaMatchServiceTest` 用例 D 通过；`detectGap` 调用点与签名 diff 为空（grep `detectGap` 调用未变）。
- I-4：用例 C 通过；缺口匹配与 `matchRule` 共用归一化/匹配核心（代码审查确认无第二套算法）。
- 集成：点击一条缺口 → 片段面板对应片段出现 `.gap-highlight`；点击无候选缺口无高亮；审计报表页 `.compose-gap-list` 渲染与点击行为不受影响（回归查看）。
- 构建/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 备注（知识沉淀，Phase 6）

- 复用并命中 K-overview-gap-supersede（I-3）、K-composed-reply-order-contract、K-suggested-rule-pin-order（均在「不可改变」中固化）。
- 新增可沉淀点（执行后由 fix-v 落库）：`CompositionSuggestResult.gapItems` 的唯一消费者是 suggest 接口→前端 `renderComposedGapList`，且与 `detectGap` 解耦——后续改缺口结构无需担心自动管线。
