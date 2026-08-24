# 02 工作台人工事实最终权威与七种处理方式全开放

> 执行顺序：第 2 份；依赖计划 01。必须与计划 03 同一发布窗口交付，避免工作台已放开但发送端仍重筛。

## 需求描述

把 `TrustReplyWorkbench` 的人工矩阵定义为最终事实选择：运营选中的、仍可外发的事实按原顺序全部成为该 request 的 `factRuleIds`、生成依据和 assembly canonical facts。关键词/intent 是否匹配只产生诊断，不得删除事实、改变人工选择或阻断处理方式。七种 `TrustReplyItemHandling` 对每条 request 全部展示、全部可选择；仅在实际生成/锁定时校验事实、说明、正文等机械前置条件。

自动回复、legacy flat selection、普通 QA 发送继续执行现有严格匹配。安全 claim 校验、动作边界、source/evidence/version stale、规则 enabled/policy/answerBody 校验不得放宽。

关联知识：[[K-explicit-fact-selection-must-match-request]]（2026-08-24 修订）、[[K-fact-matrix-two-semantics-in-one-field]]、[[K-workbench-matrix-path-is-operator-scoped]]、[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-operator-directed-authorization-seam]]、[[K-materialize-version-five-write-sites]]、[[K-shared-workbench-fixed-mode-host-adapter]]。

## 关键不变量

### Invariant I-1：显式人工矩阵是唯一最终事实集

矩阵路径逐 request 满足：

`RequestFactItem.factRuleIds == boundRuleIds == requestFactSelections.factRuleIds`

且顺序逐元素一致。`ResolvedQaRules.sendQaRuleIds` 是按 request 顺序拼接后的首次出现顺序去重；不得再从 intent coverage、claim 或关键词匹配反推/删减。

### Invariant I-2：意图识别只做诊断

系统继续计算自然 `status`、intents 和严格命中的事实集合，但人工事实拆为：`intentMatchedFactRuleIds` 与 `intentMismatchFactRuleIds`。两者并集按人工顺序等于最终 `factRuleIds`；任何一个诊断字段均不得进入授权、allowed handling、版本拒绝或发送删减逻辑。

### Invariant I-3：七种 handling 恒定开放

`TrustReplyItemHandling.values()` 的 7 个值必须出现在每条 coverage 的 `allowedHandlings` 中：`ANSWER_WITH_EVIDENCE`、`ANSWER_SUPPORTED_PART`、`ACKNOWLEDGE_PENDING`、`ANSWER_FROM_OPERATOR_INPUT`、`ANSWER_FACTS_VERBATIM`、`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`、`OMIT`。status 只决定 `recommendedHandling` 和提示，不决定可选集合。

### Invariant I-4：选择开放不等于绕过机械前置条件

- 以下 4 种在执行生成/锁定时要求本条最终 `factRuleIds` 非空：`ANSWER_WITH_EVIDENCE`、`ANSWER_SUPPORTED_PART`、`ANSWER_FACTS_VERBATIM`、`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`；为空返回稳定错误 `TRUST_REPLY_FACT_REQUIRED`，前端显示“请先添加事实”。
- `ANSWER_FROM_OPERATOR_INPUT`、`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 继续要求非空且不超过 500 字的 operator instruction。
- `OMIT`、`ACKNOWLEDGE_PENDING` 的结构校验保持现状。
- 这些校验发生在生成/锁定，不得把下拉选项隐藏或置灰。

### Invariant I-5：每个人工事实都有生成通道

grounded planner 先为严格 supported intents 生成 claims，再把尚未被任何 intent claim 使用的人工事实汇入同 request 的 `general.answer` claim；若 status 为 `UNSUPPORTED` 但有人工事实，也生成 `general.answer`，不得在 planner 的 UNSUPPORTED early return 丢失。

### Invariant I-6：跨摘要重复是合法人工决定

同一 fact 可绑定多个 request。前端不再标记“已用于摘要 N”并禁用；后端不再抛 `TRUST_REPLY_FACT_ALREADY_ASSIGNED` 或跨 item `TRUST_REPLY_DUPLICATE_CLAIM`。单条 request 内重复 id、非法 id、停用/NEVER/空 answerBody 仍是脏输入并硬拦。

### Invariant I-7：assembly canonical facts 来自选择，不来自文案形态

`TrustReplyAssembleResponse.canonicalFactIds` 必须取 `selection.sendQaRuleIds`。选择 verbatim、operator input、ack/omit 不能导致已人工选择事实从 canonical audit 集合消失。

### Invariant I-8：自动与 legacy 路径不变

`select()` 的自动、null selection、legacy flat selection 仍走 `QaFactKeywordMatcher`、intent assignment 和严格证据口径；仅 `selectForWorkbench(... selectionsByRequest=...)` 的显式矩阵分支采用人工权威语义。

## 样式契约

### S-1：复用现有 DOM/CSS，不新建视觉体系

- handling 下拉继续使用现有 `.trust-reply-field` 和原 `select`；只改变 options 数据，不改布局。
- 意图不匹配提示继续复用 `.muted`，保留原提示所在位置；字段改为 `intentMismatchFactRuleIds`，文案固定为：“人工选择已生效；系统未匹配到对应意图，已记录供后续优化。”
- 事实 picker 继续使用 `.trust-reply-fact-picker-option`、`.trust-reply-fact-state` 的 `available/selected/pending`；不再产出 `used` 状态。可保留未使用的旧 CSS，避免本计划扩大到样式文件。
- 不新增 class、不加 inline style、不改 mobile/overlay/dialog。

## 现状审计（代码证据）

### 人工矩阵与双语义

- `QaFactSelectionService.resolveMatrixSelection():195-252`：`:207` 硬拦跨 request 重复；`:223-242` 用 `operatorBound=true` 先做语义采纳，再把未采纳项写入 dropped。
- `buildRequestFact():457-565`：`effectiveCandidateRules`、general coverage 改写、`operatorBypassedRuleIds` 和 status 下调把“人工选择”与“系统证据”混在一起。
- `workbenchResult():342-374` 的 sendIds 只取 `factRuleIds`，因此 dropped 事实不会进入最终审计。
- `RequestFactItem` 位于 `AiReplyDraftService.kt:349-377`；注释明确记录了 `factRuleIds/boundRuleIds/dropped/operatorBypassed` 的双语义债。

### handling / generation / assembly

- enum 在 `TrustReplyWorkbenchService.kt:28-42`，共 7 个值。
- `allowedHandlings():2112-2143` 按 GROUNDED/PARTIAL/UNSUPPORTED 返回不同子集；`requireAllowedHandling():2152-2153` 将其变成后端硬门禁；`AiReplyDraftService.validateItemHandling():1043-1048` 复用该门禁。
- `AiReplyDraftService.generateItem():430-513` 已有 7 分支；verbatim 在 `:497-512` 事实为空时仅返回不可锁定，没有明确机械错误；混合模式在 `:868-901` 只校验 instruction，事实块可空。
- `AiReplyGroundedContentPlanner.kt:46-56` 对 UNSUPPORTED 直接 continue；`:59-84` 仅在“无 supported intent”时才用 general facts，无法承载“supported facts + mismatch residual”。
- `canonicalizeClaims():1476-1504` 同样只接受 supported claims 或单一 general claim，必须与 planner 同步改为“supported + residual general”。
- `validateNoDuplicateClaims():1343-1372` 在 claim `(intentKey,sourceId)` 或规范化正文重复时抛 422，并在 restore/assemble 两处调用（`:916/:1281`）。
- assembly `:1295-1308` 当前只从 grounded claims 收集 canonicalFactIds，verbatim/operator/ack 事实会丢失。

### 前端

- `trust-reply-workbench.js:500-518` 将 coverage DTO 投影进本地 request；`:2292` 只渲染 `availableHandlings`。
- `:1891-1913` 通过 `factOwnerById()` 将其他摘要已选 fact 标记 `used` 并 disabled。
- `:1930-1940` 显示“绑定保留但 AI 不引用正文”的 dropped 提示，与新产品决策相反。
- `computeReadiness():1180-1210` 负责是否已有可序列化版本；本计划不取消“必须为每条 request 生成/锁定版本”这一机械要求。

### 持久化

`trust_reply_workbench_state.payload_json` 保存 canonical matrix 与 locked items，不保存 `RequestFactItem`。本计划不改 schema/store；既有 matrix 顺序和 version identity 继续由 `boundRuleIds`/request selection 投影维持。

## 实现方案

### 阶段 1：把矩阵分支改成“自然诊断 + 人工最终事实”

1. `resolveMatrixSelection` 继续用 `validateExplicitSelection` 校验规则存在、enabled、policy != NEVER、answerBody 非空。
2. 删除跨 request uniqueness gate。
3. 调用 `buildRequestFact` 时不再启用 `operatorBound` 语义绕过；先得到严格匹配的自然诊断 item。
4. 记录严格 item 的 `factRuleIds` 为 `intentMatchedFactRuleIds`；按人工顺序计算 mismatch；随后 copy 为 `factRuleIds=explicitIds`、`boundRuleIds=explicitIds`。
5. 删除 `operatorBound`、`operatorBypassedRuleIds`、`droppedBindingRuleIds` 旧分支；用显式的 matched/mismatch 字段替代。自动/legacy item 的 matched 默认等于其事实集、mismatch 为空。
6. `workbenchResult.sendQaRuleIds` 保持从最终 `factRuleIds` 有序 union 得出。

### 阶段 2：全开放 handling，集中机械校验

1. `allowedHandlings` 固定返回 enum 全量；`recommendedHandling` 保持 status 建议。
2. 抽取唯一 `requireHandlingPrerequisites(item, handling, instruction)`，供 generate、adjust、locked-item validation、restore、assemble 共用；将 4 个事实模式和 2 个 instruction 模式的机械前置条件集中在此。
3. API 将事实缺失统一映射为 `TRUST_REPLY_FACT_REQUIRED`；前端在生成动作失败时显示“请先添加事实”，但 handling 下拉仍保留当前选择。
4. 保留 answerText/claims/generationKind/hash/source/evidence/context、动作策略和高风险校验。

### 阶段 3：补齐 residual fact claims

1. planner 为 supported intent claims 收集已使用 source ids。
2. `residual = item.factRuleIds - usedSourceIds`，非空时追加唯一 `r{index}:general.answer` claim。
3. UNSUPPORTED + 有事实时不进入 missing-only early return，而是生成 general claim；无事实仍保持 missing。
4. `canonicalizeClaims` 用同一 canonical projection：supported intent pairs + residual general pair；回答段落顺序与 planner claims 顺序一致。
5. `composeVerbatimFactAnswer` 继续按最终 `factRuleIds` 顺序逐字输出 `answerBody`。

### 阶段 4：取消重复事实硬门禁并修正 assembly

1. 删除矩阵跨 request uniqueness 校验。
2. 删除 restore/assemble 的跨 item `validateNoDuplicateClaims` 调用与方法；单 item 内 claim set、source ids、answer/claim 一致性仍由 `canonicalizeClaims` 保证。
3. assembly 的 `canonicalFactIds` 直接赋值 `selection.sendQaRuleIds`；`requestedFactIds` 同源。
4. coverage DTO 将 `droppedFactRuleIds` 改为 `intentMatchedFactRuleIds`、`intentMismatchFactRuleIds`。

### 阶段 5：前端解除 picker 门禁

1. 移除其他 request owner 导致的 `used/disabled` 分支；其他摘要已选 fact 仍显示“可添加”。本 request 已选和保存中仍 disabled。
2. 渲染 7 个后端 options；状态不参与过滤。
3. 将 dropped 提示替换为已生效的 mismatch 提示。
4. 保持矩阵序列化只有 `requestKey + factRuleIds`，不把诊断字段回传为用户决策。

## 变更文件清单

| 文件 | 修改 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 矩阵最终事实、自然诊断、移除重复门禁 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | RequestFactItem 字段、机械前置条件、verbatim/混合生成 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` | supported + residual general claims |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 7 handling、DTO、锁定/组装、canonical ids、删除跨项重复硬门禁 |
| `src/main/resources/static/trust-reply-workbench.js` | 全量 options、重复事实可选、mismatch 提示 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 矩阵权威、诊断、自动/legacy 回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 4 种事实前置条件、verbatim/混合顺序 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 7 handling、生成/锁定/restore/assemble |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | DTO、canonical matrix、跨摘要重复 |
| `src/test/js/trustReplyWorkbench.test.js` | picker、7 options、提示、payload 契约 |

范围：10 个文件；2 个子系统（LLM 工作台、共享工作台前端）。无 DB migration、CSS、邮箱发送代码变更。

## 验收标准

- GROUNDED/PARTIAL/UNSUPPORTED 三种 fixture 的 `allowedHandlings` 均严格等于 7 个 enum 值。
- UNSUPPORTED request 手工选择不匹配 fact 后：`factRuleIds/boundRuleIds/matrix` 均保留该 id；mismatch 诊断包含该 id；status 仍表达自然检测；verbatim 输出 `answerBody` 原文。
- 每种 fact-required handling 在空事实时可选，但生成返回 `TRUST_REPLY_FACT_REQUIRED`；加事实后无需改 handling 即可生成。
- 同一 fact 可在两个 request picker 中选择、保存、恢复、生成、assemble；不出现 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`/`TRUST_REPLY_DUPLICATE_CLAIM`。
- supported intent fact 与 mismatch fact 同时存在时，planner/claims 包含 supported claim 和 residual `general.answer`，没有人工事实丢失。
- `assemble.canonicalFactIds == selection.sendQaRuleIds ==` 人工矩阵有序 union；不随 handling 改变。
- `select()` 的严格 explicit/legacy/auto 既有拒绝测试继续通过。
- 测试：
  - `mvn -q -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest test`
  - `node --test src/test/js/trustReplyWorkbench.test.js`
  - `git diff --check`

## 人工验收清单

1. 分别打开 GROUNDED、PARTIAL、UNSUPPORTED 邮件；确认每条处理方式下拉均有 7 项。
2. 在 UNSUPPORTED 条目选择事实并选“按事实原文回答”；确认可生成，正文逐段等于事实 `answerBody`。
3. 绑定意图不匹配事实；确认事实仍生效，仅显示诊断提示。
4. 将同一事实绑定两个摘要；确认两个 picker 均可选、刷新后均保留、可整合。
5. 空事实选择四种事实模式；确认选项不消失，生成时出现“请先添加事实”。
6. 切换各 handling 后检查 assembly canonical fact ids 始终等于人工选择 union。

