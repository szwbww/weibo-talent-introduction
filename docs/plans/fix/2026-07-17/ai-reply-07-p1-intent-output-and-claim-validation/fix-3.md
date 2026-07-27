# fix-3：P1-7 Intent 输出与声明校验复验

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 7 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-07-p1-intent-output-and-claim-validation.md`
- 上轮：`docs/plans/fix/ai-reply-07-p1-intent-output-and-claim-validation/fix-2.md`

## 约束摘录

- I-1：`requestIndex`、`intentKey`、`sourceRuleIds` 必须属于后端 matrix；严格 JSON 不接受多余字段、缺失字段或不符合声明类型的值。
- I-2：`sourceRuleIds` 非空，且只能是当前 intent 的 evidence 子集，不能跨 request。
- I-3/I-4：后端统一标题、编号和邮件 frame；内部控制状态不进入外发正文。
- I-5：高风险数字、金额、时长、频次和 URL 必须由引用 QA subject/body 精确支持；否则 fail closed。
- I-6：fallback 只使用 supported intent evidence；研究匹配不输出 profile 原文。
- I-7：首轮和 CTA correction retry 都必须 materialize、source/claim validate；无效结果走 deterministic fallback。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 每次模型将任一合法 `requestIndex` 写成小数，如 `1.5` 时 | materializer 用 `canConvertToInt()` 判断 request index，仅限制 int 范围；随后 `asInt()` 将 `1.5` 截断为 `1`。小数因此可冒充 matrix 内 request 1，违反 I-1 的 strict JSON/matrix contract。 | `AiReplyGroundedDraftMaterializer.kt:83-86`；`AiReplyGroundedDraftMaterializerTest.kt` 无浮点 requestIndex 负例。 |

## 修复规格

### P1-1：requestIndex 仅接受 JSON 整数

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`

1. 在读取 `requestIndex` 前要求节点为 JSON integral number；拒绝小数、科学计数法浮点、字符串和 null，再做现有 int 范围与 matrix 成员校验。
2. 保持 supported-intent 完整集、sourceRuleIds integral/evidence subset、retry fallback 和 composer 行为不变；不新增 DTO、状态或外部校验。
3. 增加 `requestIndex:1.5` 负例，断言整次 structured response 无效，且 raw JSON 不会进入 draft。

## 当前状态（修前）

- Build/Test：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`：1667 passed，0 failed，0 errors，3 skipped（2026-07-15）。
- JS：PASS — `node --test src/test/js/*.test.js`：301 passed，0 failed，0 skipped。
- 定向：PASS — 142 passed，0 failed：materializer 15、validator 20、composer 13、DraftService 94。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 strict intent JSON | ❌ P1-1 | `AiReplyGroundedDraftMaterializer.kt:83-86` 对 `requestIndex` 仅用 `canConvertToInt()` 后调用 `asInt()`；未要求 `isIntegralNumber`。`:150-155` 已正确将 `sourceRuleIds` 限为 integral number。 |
| I-2 source 仅属本 intent evidence | ✅ | `AiReplyGroundedDraftMaterializer.kt:146-164` 要求非空 integral ID，并以当前 intent 的 `evidenceRuleIds` 拒绝集合外 ID。 |
| I-3 fixed heading/frame/numbering | ✅ | `AiReplyPointByPointComposer.kt:20-46,135-165` 使用 catalog 标题、全量 request index 和 `ReplySnippetService` frame 组装。 |
| I-4 internal state 不外发 | ✅ | `AiReplyGroundedDraftMaterializer.kt:133-143,203-228` 拒绝空答案、内部短语和状态 token；`AiReplyPointByPointComposer.kt:154-160` 空 group 仅保留 heading。 |
| I-5 high-risk fail closed | ✅ | `AiReplyHighRiskClaimValidator.kt:26-44,73-105,132-148` 缺失来源即 warning/invalid，数字/URL/复合 token、modality 和高风险 phrase 均验证；测试 `:197-407` 覆盖精确 token、URL path 与币种/单位偷换。 |
| I-6 fallback 同 matrix | ✅ | `AiReplyDraftService.kt:626-638` 将 `factRuleIds` 收窄为 supported intent evidence；`AiReplyPointByPointComposer.kt:53-83` 仅组装该集合；测试覆盖 research fallback。 |
| I-7 retry 同 schema + claim validate | ✅ | `AiReplyDraftService.kt:399-447` retry materialize 或 claim 无效均立即返回 `composeFallback`、`usedLlm=false`、`FALLBACK_NO_RESPONSE`；测试 `AiReplyDraftServiceTest.kt:2539-2629` 覆盖非 JSON 与虚构金额。 |
| T1-T5 scope/no extras | ✅ | 本轮审阅的修复仅涉及计划列出的 materializer、validator、DraftService 及对应测试；无迁移、自动回复、review UI 或 QA 事实变更。 |

## 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；readiness 为 matrix 派生值，无本阶段持久状态转换。
- Cross-plan check：❌ P1-1。Phase 6 matrix 以整数 request index 为契约，Phase 7 当前截断小数导致模型输出不能严格映射该矩阵；正常路径和 CTA retry 都复用同一 materializer，故缺口同时存在于两条跨计划路径。

## 收敛状态

- fix-2：3 个 P1。
- fix-3：1 个 P1。
- P1 数量严格下降；未触发发散或三轮上限。
