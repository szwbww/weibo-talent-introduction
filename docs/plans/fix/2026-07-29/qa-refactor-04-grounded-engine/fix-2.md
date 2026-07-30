# QA 重构 04：无 coverage 标签的 Grounded 事实引擎 — 修复计划 2

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 4。
- 子计划：`docs/plans/2026-07-17/qa-refactor-04-grounded-engine.md`。
- 前轮：`docs/plans/fix/qa-refactor-04-grounded-engine/fix-1.md`。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 / I-2 | grounded selection 不读 coverage 标签；规则先匹配 request，再唯一分配给最高正分 intent。 |
| I-3 | 每个显式 `qaRuleId` 都必须服务端重载、校验，并与至少一个已提取 request 匹配；不能静默接受不匹配的选择。 |
| I-4 / I-5 | readiness 由 request evidence/policy 收口；发送审计 ID 只能是稳定的 evidence 并集。 |
| I-6 | prompt、fallback、风险来源只读非空 `answerBody`；二次读取为空即来源不可用。 |
| I-7 / I-8 | 服务端拒绝非自然 grounded 结构；失败走 fallback，`usedLlm=false`。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `validateExplicitRulesMatchRequests()` 仅要求显式规则中“任一”匹配；混合提交一个匹配和一个无关规则时，无关规则被静默接受而非返回 400，未完成 fix-1 的逐规则事实边界。 | 中等：工作台/续轮一次选择多条规则且其中包含旧的或无关规则时。 |
| P1-2 | P1 | 风险来源二次读取时，空 `answerBody` 只要有 `displayName` 就仍被视为有效来源；选择后并发编辑/脏数据可让空事实的引用通过部分校验。 | 低频：选择与校验之间规则被编辑为空，或历史脏数据绕过管理校验时。 |

## 修复规格

### P1-1：逐条拒绝不匹配的显式选择

- 文件：`QaFactSelectionService.kt`、`QaFactSelectionServiceTest.kt`，必要时 `AiReplyDraftServiceTest.kt`。
- `validateExplicitRulesMatchRequests()` 必须对 **每个** 已通过 enabled/policy/answerBody 校验的规则要求至少匹配一个 normalized request；任一不匹配即抛 400，不能以另一个匹配规则掩盖。
- 保留多 request 的现有分配：一条规则只进入其实际匹配的 request；不得跨 request 借 evidence。
- 回归：`[匹配规则, 不匹配规则]` 的显式提交必须失败；单条匹配、多 request 的 PARTIAL/UNSUPPORTED 与稳定 send ID 顺序保持。

### P1-2：空事实不得由显示名充当风险来源

- 文件：`AiReplyHighRiskClaimValidator.kt`、`AiReplyHighRiskClaimValidatorTest.kt`，必要时 `AiReplyDraftServiceTest.kt`。
- `resolveSourceText()` 必须先要求每个引用规则的 `answerBody.trim()` 非空；为空即返回 `null`。非空时才可将可选 `displayName` 与正文拼接作诊断/高风险比对。
- 不读取 `replyBody/replySubject`，不新增状态或 DTO；来源不可用沿用现有 `FALLBACK_NO_RESPONSE`。
- 回归：`displayName` 非空、`answerBody` 空、`replyBody` 含金额/保证时，source 不可用，grounded 结果 fallback 且不泄漏旧正文。

## 当前状态（修复前）

- 编译：PASS。
- 测试：PASS — 192 passed, 0 failed, 0 skipped。
  - `QaFactSelectionServiceTest` 7；`AiReplyDraftServiceTest` 107；`AiReplyPointByPointComposerTest` 14；`AiReplyHighRiskClaimValidatorTest` 32；`AiTrainingSimulateTest` 22；`UnmatchedInboundAiReplyTurnKnowledgeTest` 10。
- `git diff --check`：PASS。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ✅ | `QaFactSelectionService.kt:19-21,106-112` 只从启用、可用、非空 `answerBody` 规则按 request 匹配；`AiReplyDraftService.kt:574-578` 统一调用选择器。 |
| I-2 | ✅ | `AiReplyIntentCatalog.kt:300-354` 仅正分规则进入唯一 bucket，平分按 catalog 顺序。 |
| I-3 | ❌ P1-1 | `QaFactSelectionService.kt:87-92` 使用 `explicitRules.any`，而非逐规则匹配；`QaFactSelectionServiceTest.kt:123-152` 未覆盖混合显式选择。 |
| I-4 | ✅ | `AiReplyDraftService.kt:580-602` 依 UNSUPPORTED、空 evidence、PARTIAL、REVIEW policy 收口。 |
| I-5 | ✅ | `QaFactSelectionService.kt:156-171` 按 request→intent→priority→id 稳定去重。 |
| I-6 | ❌ P1-2 | `AiReplyHighRiskClaimValidator.kt:55-63` 在 `answerBody` 为空而 `displayName` 非空时仍返回来源；与 fix-1 P1-2 的“空正文即不可用”不符。 |
| I-7 | ✅ | `AiReplyGroundedDraftMaterializer.kt:223-239` 检测编号、章节和内部标签；`AiReplyDraftService.kt:968-977` 在 action policy 前降级；`AiReplyPointByPointComposer.kt:82-105` 后端组装自然段。 |
| I-8 | ✅ | `AiReplyDraftService.kt:260-310,329-353` claim/结构/空响应走 fallback 且 `usedLlm=false`。 |
| 删除/兼容 | ✅ | `AiReplyDraftService.kt:115-123` 只产生 `QA_GROUNDED/FREE_FORM`；`QA_MATCHED` 仅兼容枚举分支。 |
| 范围 | ✅ | 本轮只需修复选择器、风险校验器及其回归测试；不涉及 SMTP、工作台或 schema。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；无状态机。
- Cross-plan check：❌ P1-2。Phase 2 的 `answerBody` 是唯一事实源；风险来源对空正文的放行会破坏 Phase 2→Phase 4 的事实边界。P1-1 会使 Phase 4→Phase 6 的显式事实选择无法被 API 明确拒绝。

