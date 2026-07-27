# fix-2：P1-7 Intent 输出与声明校验复验

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 7 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-07-p1-intent-output-and-claim-validation.md`
- 上轮：`docs/plans/fix/ai-reply-07-p1-intent-output-and-claim-validation/fix-1.md`

## 约束摘录

- I-1：每个 supported intent 必须恰好输出一次；strict JSON 不接受多余、缺失或非整数的 `sourceRuleIds`。
- I-2：`sourceRuleIds` 非空，且仅能是本 intent evidence 的子集。
- I-3/I-4：后端统一固定标题、编号与邮件 frame；内部状态不外发。
- I-5：金额、数字区间、年/月时长、访问频次和 URL 须逐 token 在引用 QA subject/body 中出现；不满足则 fail closed。
- I-6：fallback 只使用 supported intent evidence；研究匹配仍保留已确认双证据的 QA 范围事实，绝不输出 profile 原文。
- I-7：首轮与 CTA correction retry 都须 materialize、source/claim validate；任一无效响应走 deterministic fallback，不能进入 sanitizer 或 draft。
- 已有决定：matrix 是 intent/evidence 唯一来源；`sendQaRuleIds` 不等于 prompt 全集；状态仅属操作端；研究匹配要求 profile + programme scope 双证据。（K-grounded-json-materialize-before-policy、K-action-sanitizer-preserve-layout、K-research-fit-dual-evidence、K-grounding-status-ui-only、K-ai-reply-prompt-vs-send-rule-ids、K-gap-items-compose-only）

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 每次 CTA correction retry 返回非 JSON、缺 section 或其他 materialize 无效内容时 | retry 只在 claim 校验失败时 fallback；materialize 无效时保留首轮 LLM 草稿、继续 sanitizer，并保持 `usedLlm=true`。这直接违背 fix-1 P1-3 与 I-7 的“materialize 或 claim 无效均 fallback”约束。 | `AiReplyDraftService.kt:400-433`；`AiReplyDraftServiceTest.kt:2539-2583` 将该错误行为固化为通过用例。 |
| P1-2 | P1 | 模型把任一可用 rule ID 写成小数（如 `1.5`）时 | Jackson 的 `canConvertToInt()` 只检查数值范围；`1.5` 随后由 `asLong()` 截断为 `1`，因此非整数 JSON ID 可伪装成 evidence rule 1 并被接受，违反 I-1/I-2 的严格 schema。 | `AiReplyGroundedDraftMaterializer.kt:150-155`；Jackson `DoubleNode.canConvertToInt()` 仅比较 int 范围；`AiReplyGroundedDraftMaterializerTest.kt:207-215` 仅覆盖字符串混入，未覆盖小数。 |
| P1-3 | P1 | 引用事实含金额/时长/频次而模型偷换币种、时间单位或频率单位时 | validator 只验证裸数字 token 和 URL，未把货币或单位与数字作为一个需溯源的 token。故来源 `USD 8,000 per month` 可被回答 `RMB 8,000 per year` 放行。 | `AiReplyHighRiskClaimValidator.kt:73-94,140-142`；`AiReplyHighRiskClaimValidatorTest.kt:43-58,197-234` 未覆盖货币、年/月或频次单位偷换。 |

## 修复规格

### P1-1：无效 CTA retry 一律回退

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

1. 在 `QA_GROUNDED` correction retry 中，`materialized.valid=false` 与 `claimResult.valid=false` 走同一个 deterministic fallback 返回路径：`usedLlm=false`、`generationState=FALLBACK_NO_RESPONSE`，保留 structured/claim warning。
2. 不把首轮或 retry 的 LLM 文本传给 action sanitizer；QA_MATCHED/FREE_FORM retry 行为、`sendQaRuleIds` 和 readiness 聚合不变。
3. 将“invalid CTA retry keeps first materialized grounded draft”替换为回退断言；新增/保留 two-call 回归，确认无效 retry 的 raw 文本、CTA 和任意首轮模型措辞均不进入外发草稿。

### P1-2：sourceRuleIds 仅接受 JSON 整数

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`

1. 将 source ID 类型检查收紧为 JSON integral number；拒绝小数、科学计数法浮点和字符串，不进行截断/转换后再做 evidence 比较。
2. 保持现有非空、同 request、intent evidence 子集和完整 supported-intent 集校验。
3. 增加 `sourceRuleIds:[1.5]` 的负例，断言整次 structured response 无效且 JSON 不会进入正文。

### P1-3：复合高风险数值 token 精确溯源

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`

1. 在保留现有裸数字和完整 URL 校验的基础上，对金额（数字+货币）、数字区间、年/月时长及访问/频次（数字+时间/频率单位）提取并规范化为完整 token；答案 token 必须在引用 `replySubject + replyBody` 中完整出现。
2. 不新增外部事实校验、DTO、状态或语义分类；仅在当前 validator 内 fail closed。
3. 增加至少三项负例：USD→RMB、month→year、每月→每年（或等价频次单位）均拒绝；对应完全一致的来源 token 通过。

## 当前状态（修前）

- Build：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`（2026-07-15）。
- 定向：PASS — 137 passed, 0 failed, 0 skipped：`AiReplyGroundedDraftMaterializerTest` 14、`AiReplyHighRiskClaimValidatorTest` 16、`AiReplyPointByPointComposerTest` 13、`AiReplyDraftServiceTest` 94。
- JS：PASS — 301 passed, 0 failed, 0 skipped：`node --test src/test/js/*.test.js`。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 strict intent JSON | ❌ P1-2 | `AiReplyGroundedDraftMaterializer.kt:150-155` 接受可转换小数并 `asLong()` 截断；`:173-197` 的 intent/index 完整集校验已通过。 |
| I-2 source 仅属本 intent evidence | ❌ P1-2 | `AiReplyGroundedDraftMaterializer.kt:160-164` 正确限制 evidence 子集，但其前置小数截断允许伪造整数 ID。 |
| I-3 fixed heading/frame/numbering | ✅ | `AiReplyPointByPointComposer.kt:20-46,135-165` 以 catalog 标题、全部 request index 和 `ReplySnippetService` frame 统一组装。 |
| I-4 internal state 不外发 | ✅ | `AiReplyGroundedDraftMaterializer.kt:203-228` 拒绝内部标记；`AiReplyPointByPointComposer.kt:154-160` 空 section 仅有 heading。 |
| I-5 high-risk fail closed | ❌ P1-3 | `AiReplyHighRiskClaimValidator.kt:73-94` 仅校验裸数字/URL；无法证明金额币种、时长和频率单位来自引用事实。 |
| I-6 fallback 同 matrix | ✅ | `AiReplyPointByPointComposer.kt:53-83` 仅在 GROUNDED/PARTIAL 下使用 `factRuleIds`；`AiReplyPointByPointComposerTest.kt:240-269` 覆盖 research supported evidence fallback。 |
| I-7 retry 同 schema + claim validate | ❌ P1-1 | `AiReplyDraftService.kt:400-433` 对 materialize 无效仅加 warning 后保留首轮 draft；`:408-428` 的 claim 无效回退已正确。 |
| T1 materializer tests | ❌ P1-2 | `AiReplyGroundedDraftMaterializerTest.kt:179-215` 覆盖漏 intent、字符串混入，但无小数 ID。 |
| T2 validator tests | ❌ P1-3 | `AiReplyHighRiskClaimValidatorTest.kt:43-58,197-234` 覆盖裸数字与 URL，未覆盖币种/单位/频次完整 token。 |
| T3 composer tests | ✅ | `AiReplyPointByPointComposerTest.kt:156-177,240-269` 覆盖全 section、空 heading 与研究 fallback。 |
| T4 DraftService retry tests | ❌ P1-1 | `AiReplyDraftServiceTest.kt:2539-2583` 明确接受无效 retry 后保留首轮 draft。 |
| T5 scope/no extras | ✅ | 本轮修复仅需计划列出的 service/test 文件；无迁移、自动回复或 review UI 任务。 |

## 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；本阶段无持久化状态机，readiness 由 matrix 派生。
- Cross-plan check：❌ P1-1/P1-2/P1-3。Phase 6 的 matrix 仍可能被小数 source ID 绕过，且 Phase 7 retry/高风险声明合约未全程 fail closed；正常路径、失败后回退、重启后重新生成均因此不能保证在子计划边界保持同一证据约束。

## 观察（非阻断）

- `AiReplyPointByPointComposerTest.kt:272-300` 的测试名声称“不输出 profile text”，但其输入把 `Expert profile: ...` 写成 QA rule body 并断言输出该 body；不代表运行时把 actual profile 注入 fallback，故不作为本轮 P1。
