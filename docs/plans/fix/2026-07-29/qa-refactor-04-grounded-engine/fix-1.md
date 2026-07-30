# QA 重构 04：无 coverage 标签的 Grounded 事实引擎 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 4。
- 子计划：`docs/plans/2026-07-17/qa-refactor-04-grounded-engine.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 / I-2 | 只以 request、intent aliases 与规则事实字段匹配；每条候选先匹配 request，再唯一分配给得分最高的 intent。 |
| I-3 | 显式 qaRuleIds 必须服务端重载、校验，并受 I-2 的 request→intent 事实边界约束。 |
| I-4 / I-5 | readiness 由 request evidence 与 policy 收口；sendQaRuleIds 仅是稳定 evidence 并集。 |
| I-6 | prompt、fallback、风险校验都只能以 answerBody 为事实来源；引用必须可核验。 |
| I-7 / I-8 | 后端控制自然邮件结构；结构/校验失败必须 usedLlm=false、保留 fallback generationState。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | 显式选择时 `explicitMode || matchesRule(...)` 跳过 request 匹配；同一 intent 下不匹配原问题的 AUTO/REVIEW 规则可成为 evidence。 | 中等：人工续轮/工作台选择规则且选择与请求关键词不一致时。 |
| P1-2 | P1 | grounded prompt、deterministic fallback 与风险来源均以空 `answerBody` 回退 `replyBody`；废弃邮件正文可重新成为事实依据。 | 低频：规则在选择和再次读取间被手工/迁移置空，或脏数据绕过 QA 管理校验时；影响为外发事实越界。 |
| P1-3 | P1 | JSON materialize 后直接拼接 LLM `answer`；编号和章节标题只有 prompt 禁止、没有服务端 gate。 | 中等：模型未遵守格式指令时；外发草稿会恢复固定编号/章节结构。 |

## 修复规格

### P1-1：显式选择不得绕过 request 匹配

- 文件：`QaFactSelectionService.kt`、`QaFactSelectionServiceTest.kt`、`AiReplyDraftServiceTest.kt`。
- 对 explicit 与自动候选统一执行 `QaFactKeywordMatcher.matchesRule(rule, normalizedRequest)`；仅通过匹配者进入 `assignRulesToIntents`。
- 显式提交的规则若不匹配任何已提取 request，返回明确 400；多 request 时只把匹配规则加入对应 request，未被该规则覆盖的其他 request 仍按 PARTIAL/UNSUPPORTED 计算，不能跨 request 借证据。
- 回归：验证同 intent、但关键词不匹配的 AUTO/REVIEW rule 不能写入 factRuleIds/sendQaRuleIds；原有 unknown/disabled/NEVER/blank 400 继续通过。

### P1-2：grounded 事实链只读 answerBody

- 文件：`AiReplyDraftService.kt`、`AiReplyPointByPointComposer.kt`、`AiReplyHighRiskClaimValidator.kt` 及对应三组测试。
- 删除 `answerBody.ifBlank { replyBody }`（及同义）在 grounded prompt、fallback、风险来源中的回退；规则二次读取时 answerBody 为空即视作来源不可用并 fallback/blocked。
- `buildFreeFormUserContent` 的 QA 事实输入同样只用 answerBody；不改变不再产生的 QA_MATCHED 兼容 enum 语义。
- 回归：构造 `answerBody=""`、`replyBody` 含金额/URL/保证的规则，断言 prompt、fallback、validator 均不采用旧正文，usedLlm=false。

### P1-3：后端 gate 非自然 grounded 结构

- 文件：`AiReplyDraftService.kt`、`AiReplyDraftServiceTest.kt`、`AiReplyPointByPointComposerTest.kt`。
- 在 materialized text 进入 action-policy 前检测编号列表、固定章节标题及内部 `intent/status/rule ID` 标签；命中即按结构化响应失败处理，使用 `composeFallback`、`usedLlm=false`、`FALLBACK_NO_RESPONSE`。
- 初始生成与 CTA retry 共用该 gate；不新增状态、DTO 或第二条发送路径。
- 回归：LLM JSON answer 带 `1.`、`Program & eligibility`、`GROUNDED` 时均 fallback；正常 2–4 自然段仍保持 LLM_USED。

### 测试迁移（机械）

- 文件：`AiReplyDraftServiceTest.kt`、`AiTrainingSimulateTest.kt`。
- 删除或改写仍断言 `QaMatchService.suggestComposition`、coverage key、`QA_MATCHED`、固定编号 fallback 的旧测试；fixture 必须提供 answerBody 与 request 可匹配 keywords。
- 不降低 I-1 至 I-8 覆盖；将每条失效旧断言替换为本计划规定的 grounded 语义断言。

## 当前状态（修复前）

- 编译：PASS（测试前编译完成）。
- 测试：FAIL — 176 executed，22 failed，0 errors，0 skipped。
  - `QaFactSelectionServiceTest`：5/5 PASS。
  - `AiReplyDraftServiceTest`：105 executed，21 failed。
  - `AiReplyPointByPointComposerTest`：13/13 PASS。
  - `AiReplyHighRiskClaimValidatorTest`：31/31 PASS。
  - `AiTrainingSimulateTest`：22 executed，1 failed。
- 失败根因：失败项仍断言已由本计划移除的 `suggestComposition`/coverage-key/`QA_MATCHED`/固定编号行为，或 fixture 仅提供 replyBody；不是 Maven 环境错误。
- `git diff --check`：PASS。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ✅ | `QaFactSelectionService.kt:19-21,85-95` 仅从 enabled rule、keywords、answerBody 与 intent catalog 构造候选；新 grounded 调用不读 QaCoverageKeyCatalog。 |
| I-2 | ✅ | `AiReplyIntentCatalog.kt:300-354` 将每条规则按正分与 catalog 顺序唯一分配。 |
| I-3 | ❌ P1-1 | `QaFactSelectionService.kt:61-74` 校验 policy/body；但 `:89-93` 在 explicitMode 绕过 request 匹配。 |
| I-4 | ✅ | `AiReplyDraftService.kt:574-597` 按 UNSUPPORTED/PARTIAL/evidence/policy 收口，不把有 request 的无 evidence 设为 READY。 |
| I-5 | ✅ | `QaFactSelectionService.kt:139-154` 以 request→intent→priority→id 稳定去重 evidence。 |
| I-6 | ❌ P1-2 | `AiReplyDraftService.kt:820-824`、`AiReplyPointByPointComposer.kt:108-120`、`AiReplyHighRiskClaimValidator.kt:47-65` 均回退 replyBody。 |
| I-7 | ❌ P1-3 | `AiReplyPointByPointComposer.kt:16-35` 原样采纳 model answer；`AiReplyDraftService.kt:235-255` 无结构 gate 即返回 LLM_USED。 |
| I-8 | ✅ | `AiReplyDraftService.kt:256-306,325-349` 对 claim/JSON/空响应使用 fallback 与 usedLlm=false。 |
| 删除/兼容 | ✅ | `AiReplyDraftService.kt:115-120` 只产生 QA_GROUNDED/FREE_FORM；QA_MATCHED enum 未产生。 |
| 范围 | ✅ | P1 修复限定在子计划列出的 selector、draft、composer、validator 和测试文件。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；无状态机。
- Cross-plan check：❌ P1-2。Phase 2 的 answerBody 是唯一事实源；Phase 4 三个读取点回退 replyBody，使 QA 编辑→grounded 生成链无法保持单一事实源。P1-1/P1-3 分别破坏 Phase 4→Phase 6 的人工事实边界和自然工作台草稿契约。
