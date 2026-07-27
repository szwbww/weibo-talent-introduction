# AI 回复第 5B 步：Grounded fallback readiness

## 需求描述

- 可观察结果：Grounded fallback 若最终安全清理删掉动作，草稿不得为 `READY`；一次修复耗尽仍为 `BLOCKED`。
- 可观察结果：readiness 只在全部 evidence rule 当前存在、启用、非空 `answerBody` 且均为 AUTO 时才可为 `READY`。

必须保持不变：

1. critical/unknown `UNSUPPORTED` 为 `BLOCKED`，已分类 noncritical `UNSUPPORTED` 和 `PARTIAL` 为 `NEEDS_REVIEW`。
2. LLM 关闭/客户端不可用本身不把完整事实 deterministic fallback 改为 `BLOCKED`。
3. 不修改 QA 规则写入、schema、迁移、API DTO、动作 matcher 或自动回复 reason。

不在本计划范围：

- 敏感材料文本识别及其正则。
- `GroundedAutoReplyDecisionService` 的 warning 分类。
- 前端状态展示、人工采用或发送权限。

## 关键不变量

### Invariant I-1: 被 sanitize 的 Grounded fallback 不能 READY
- Rule：`groundedFallbackResult()` 若 `sanitize()` 返回 `removed=true`，正常 fallback 至少为 `NEEDS_REVIEW`；若已有 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`，保持 `BLOCKED` 并优先于该降级。
- Applies to：所有进入 `groundedFallbackResult()` 的 LLM disabled、client unavailable、blank/exception、首次/二次校验失败分支。
- Violation consequence：被移除未授权动作的草稿被误显示为可自动发送质量。
- 来源：K-grounded-sanitize-removal-readiness、K-validation-exhaustion-must-block-readiness。

### Invariant I-2: evidence 当前可用性是 READY 前提
- Rule：`resolveDraftReadiness()` 必须逐个重读传入 evidence ID；任一 rule 缺失、disabled、`answerBody` 空白或 policy 为 NEVER 时返回 `BLOCKED`，不得用 `mapNotNull` 静默缩小 evidence 集。
- Applies to：`resolveDraftReadiness()` 和 `resolveDraftReadinessForSelection()`；所有 generate 结果复用该函数。
- Violation consequence：规则在选择后被删除/禁用/清空时，草稿会以残缺事实标为 READY。
- 来源：K-readiness-evidence-revalidation。

### Invariant I-3: 既有三态排序不回退
- Rule：在 evidence 可用后，blocking/unknown 缺口优先 `BLOCKED`，PARTIAL 与已分类 noncritical 缺口为 `NEEDS_REVIEW`，REVIEW policy 为 `NEEDS_REVIEW`，其余才 READY。
- Applies to：`resolveDraftReadiness()`。
- Violation consequence：非关键缺口过度阻断或关键缺口被放宽。
- 来源：K-readiness-noncritical-gap-semantics。

## 现状审计

### `RequestFactItem → AiReplyDraftResult` 内存链
- Schema/mapping：`RequestFactItem` 持有 evidence IDs/status；`AiReplyDraftResult` 持有 `draftReadiness` 与 warnings，均非持久化字段。
- Write paths：
  1. `QaFactSelectionService.select()` 仅从 enabled、matchable、非空 `answerBody` 规则构造初次 evidence。
  2. `AiReplyDraftService.groundedFallbackResult()` 对 fallback 文本调用 sanitize 并写 `draftReadiness`。
  3. `AiReplyDraftService.resolveDraftReadiness()` 重读 `QaRuleRepository` 计算三态。
- Read paths：
  1. controller/workbench 通过 `AiReplyDraftResult.draftReadiness` 展示生成质量。
  2. `GroundedAutoReplyDecisionService` 使用该 readiness 作为发送门禁输入。
- Interaction points：选择后的 QA 规则可被运营停用或编辑；readiness 重读必须与生成时 evidence ID 对齐，不能依赖选择时快照。

## 实现方案

### T1：收口 fallback 删除后的 readiness
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 在 `groundedFallbackResult()` 明确三段优先级：repair exhausted → BLOCKED；否则 sanitize 删除 → NEEDS_REVIEW；否则调用统一 factual readiness。
- 覆盖每个调用来源的共同 helper，不复制新 readiness 函数或新增 state。
- 单测覆盖完整 AUTO facts 下的 sanitization removal、repair exhausted + removal、LLM disabled 正常无删除 fallback。
- 遵守：I-1、I-3。

### T2：evidence 重读 fail-closed
- 文件同 T1。
- 将 `evidenceRuleIds` 的 repository 读取改为逐项验证集合：存在、enabled、非空 `answerBody`、可解析 policy；任一失败立即 `BLOCKED`，其余 policy 再走 I-3 的原有排序。
- 单测覆盖 missing、disabled、blank answerBody、NEVER、REVIEW、全部 AUTO；断言不影响 noncritical `UNSUPPORTED` 的 NEEDS_REVIEW。
- 遵守：I-2、I-3。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | fallback 删除降级和 evidence 重读 fail-closed。 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | fallback/readiness 全矩阵回归。 |

## 验收标准

- I-1：完整 AUTO evidence 的 Grounded fallback 被删动作时为 `NEEDS_REVIEW`；含 repair exhausted 时为 `BLOCKED`；无删除的 LLM-disabled 完整事实 fallback 不因 disabled 变 BLOCKED。
- I-2：任一 evidence rule missing、disabled、answerBody 空白或 NEVER 时为 `BLOCKED`；不能因剩余 AUTO rule 返回 READY。
- I-3：critical/unknown 缺口、PARTIAL、noncritical UNSUPPORTED、REVIEW 与 AUTO 的既有矩阵保持指定三态。
- 定向测试：`mvn -Dtest=AiReplyDraftServiceTest test`。

## 人工验收清单

### A-1：fallback 删除动作后的状态
- 前置条件：完整 AUTO QA 事实；关闭 LLM；已有上一轮草稿包含未授权 `Please send your CV.`。
- 操作步骤：在同一会话继续生成 Grounded 草稿。
- 预期结果：最终正文不含 CV 索取；draftReadiness=`NEEDS_REVIEW`，不是 `READY`。
- 覆盖：I-1。

### A-2：修复耗尽优先阻断
- 前置条件：完整 AUTO QA 事实；LLM stub 连续两次返回未授权动作。
- 操作步骤：触发生成。
- 预期结果：总调用数为 2；draftReadiness=`BLOCKED`，warning 含 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`。
- 覆盖：I-1。

### A-3：运营停用 evidence 后重新评估
- 前置条件：生成前选中一条 AUTO QA 事实；在候选生成完成前或通过测试桩将该规则设为 disabled。
- 操作步骤：查看草稿状态并尝试自动回复预览。
- 预期结果：draftReadiness=`BLOCKED`，预览不显示可发送状态；其余 AUTO 规则不得使其变为 READY。
- 覆盖：I-2、现状审计 interaction point。
