# AI 回复第 4 阶段 Grounded Trust 内容计划：修复计划 1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-04-grounded-trust-content-plan.md`
- 复验轮次：fix-1（首次）
- 范围：仅原计划列出的 LLM Grounded generation 文件与对应测试；不得改公开 DTO、controller、自动发送、前端或数据库。

## 约束摘录

1. I-1：服务端 immutable plan 唯一决定 claim、request 顺序、段落、missing facts、动作和 review；模型不能重排计划项。
2. I-3：所有 trust-sensitive 缺事实请求必须进入 `QA_GROUNDED` 缺失计划并转人工，不能进入 `FREE_FORM`。
3. I-5：统一 JSON 的 claim、paragraph、order、coverage、missingFacts、requiresReview 必须与 plan 完全相等；任何结构失败 fail closed。
4. I-6：动作仅限授权集合，`proposedAction` 必须与正文同一动作且最多一个；最终 sanitizer 仍是硬门。
5. I-7：结构、style、claim 或 action 校验失败均返回 deterministic fallback、`usedLlm=false`、`FALLBACK_NO_RESPONSE`。
6. T4：`composeFromPlan(plan, validatedClaims, proposedAction)` 把动作文本追加到最后正文段，不产生第 5 段。

## 修正记录表

| P1 | 触发频率 | 根因 |
|---|---|---|
| Trust family 缺事实误入 FREE_FORM | 每次单一 `company.verification_evidence`、`contract.party` 或 `fees.policy` 等缺事实来信；高风险人工/自动入口均会触发 | planner 以少数具体 key 替代原计划要求的完整 family。 |
| JSON plan 接受重排和 paragraph claim 重复 | 模型产生合法字段但乱序/重复 key 时；LLM 非确定输出，常规重试均可能触发 | materializer 使用 `Set` 比较，丢失顺序和次数。 |
| 允许 CTA 丢失或 action failure 仍标为 LLM 成功 | 来信授权 meeting/material CTA，或模型重试后仍含未授权 CTA；取决于模型输出 | `proposedAction.text` 未传入 composer；retry 后的 sanitizer 只删除文本，仍保留 `LLM_USED`。 |

## 修复规格

### P1-1：完整 trust-sensitive 路由

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlannerTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 改动：按原计划用完整 family 前缀覆盖 `company.*`、`agency.*`、`finance.*`、`confidentiality.*`、`contract.*`、`ip.*`，并显式包含费用/政府/授权 catalog key；不再用当前不完整的单 key 白名单。
- 预期：所有上述 catalog key 在无证据时强制 `QA_GROUNDED`，`missingFacts` 精确、readiness `BLOCKED`，不调用或采用 `FREE_FORM` 输出。
- 测试：新增 planner 测试，逐个覆盖当前 catalog 的 trust-sensitive key；保留 A-4（政府授权+费用）端到端 fixture。

### P1-2：严格保留 plan 的 JSON 顺序与次数

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`
- 改动：claims array 必须逐项等于 `plan.claims` 的 claim key 顺序；paragraph array、`paragraphIndex` 和每个 `claimKeys` list 必须与 `plan.paragraphs` 逐项相等，拒绝重复 key 和任意重排；`missingFacts.intentKeys` 同样按 plan 顺序精确相等。
- 预期：重排 claims、重排 paragraph objects/claim keys、重复 claim key 或重复 missing intent 全部 materialize invalid，进入 I-7 fallback。合法 JSON 仍可通过。
- 测试：为上述四种负向 JSON 各加一例，断言 `valid=false` 与 structured warning。

### P1-3：动作协议写入正文并 fail closed

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`、`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`，以及三者已有测试文件。
- 改动：materializer 保留已验证的 `proposedAction.type/text`；composer 按 T4 将非空 text 追加到最后计划正文段。验证 materialized body 的所有 direct action 与 `proposedAction` 精确一致：`NONE` 不得有动作，非 `NONE` 必须只有同一种且含声明的 action text。首轮或 retry 的 action 不一致/仍违规时直接走 fallback；不得以 sanitizer 删除后继续返回 `LLM_USED`。
- 预期：授权单一 meeting/material CTA 只出现一次且保留在最后正文段；未授权、type/text 不一致、双 CTA 或 retry 后仍违规均 `usedLlm=false`、`FALLBACK_NO_RESPONSE`，且自动发送门禁判 validation failure。
- 测试：覆盖授权 CTA 输出、`NONE`+正文 CTA、声明/正文不一致、双动作、retry 后仍违规，以及 CRLF/段落不变。

## 当前状态（修复前）

- 编译：PASS（Maven Kotlin compile 完成）。
- 定向测试：PASS — 157 passed, 0 failed, 0 skipped。
  - `AiReplyDraftServiceTest` 107；`AiReplyGroundedDraftMaterializerTest` 18；`AiReplyPointByPointComposerTest` 14；`AiReplyActionPolicyTest` 10；`GroundedAutoReplyDecisionServiceTest` 8。
  - 计划列出的 `AiReplyGroundedContentPlannerTest` 尚不存在，未执行。
- JS 回归：PASS — 336 passed, 0 failed, 0 skipped。
- `git diff --check`：PASS（本轮仅移除一处测试尾随空白）。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 server plan | ❌ | `AiReplyGroundedDraftMaterializer.kt:210-308` 只以 key set 校验 claims，允许 array 重排。 |
| I-2 intent-local evidence | ✅ | `AiReplyGroundedContentPlanner.kt:59-82` 为 supported intent/general answer 生成 claim；`AiReplyGroundedDraftMaterializer.kt:237-290` 校验 request、intent 和 sourceIds 子集。 |
| I-3 missing facts / trust route | ❌ | `AiReplyGroundedContentPlanner.kt:177-195` 未覆盖 `company.verification_evidence`、`contract.party`、`fees.policy`；catalog 见 `QaCoverageKeyCatalog.kt:14-16,30,45-46`。 |
| I-4 natural paragraphs / no dropped claims | ✅ | `AiReplyGroundedContentPlanner.kt:114-173` 最多四段并合并相邻 request；`AiReplyPointByPointComposer.kt:12-23,92-106` 按 plan 组装、无 GREETING/ACK。 |
| I-5 exact JSON protocol | ❌ | `AiReplyGroundedDraftMaterializer.kt:311-379` 用 set 比较 paragraph keys，接受重排和重复；`382-425` 也用 set 比较 missing intent keys。 |
| I-6 action consistency | ❌ | `AiReplyGroundedDraftMaterializer.kt:103-135` 校验 action text 但未返回给 composer；`AiReplyPointByPointComposer.kt:12-23` 无 proposedAction 参数，动作不进入正文。 |
| I-7 fail closed | ❌ | `AiReplyDraftService.kt:482-510` retry 后若仍有动作，sanitize 删除文本却可保留 `used=true`/`LLM_USED`。 |
| I-8 public result/audit compatibility | ✅ | `AiReplyDraftService.kt:514-528` 仍以 `resolved.sendQaRuleIds` 构造结果；未改 DTO。 |
| I-9 grounded prompt code-owned | ✅ | `AiReplyDraftService.kt:744-789` 为代码内 Grounded schema/rules；FREE_FORM 路径独立于 `223-233`。 |
| I-10 action layout preservation | ✅ | `AiReplyActionPolicy.kt:91-148` 与既有 tokenizer/direct matcher 同源，sanitize 保留 span seam。 |
| Deleted code | ✅ | Grounded materialized/fallback 已走 `assembleGroundedEmail`，未调用其 `take(4)` 路径：`AiReplyPointByPointComposer.kt:48-70`。 |
| No extras | ❌（P2 观察） | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` 不在原计划十文件清单；`AiReplyGroundedContentPlannerTest.kt` 缺失。该项不构成运行时 P1，但修复时应回到原计划范围。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 本计划未定义新的状态机；现有自动发送门禁仍需 `usedLlm + LLM_USED + READY`，见 `GroundedAutoReplyDecisionService.kt:104-126`。
- Cross-plan check：✅ N/A（单一计划）。
