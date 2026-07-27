# fix-1：P1-7 Intent 输出与声明校验复验

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 7 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-07-p1-intent-output-and-claim-validation.md`

## 约束摘录

- I-1：每个 supported intent 必须恰好输出一次；missing intent 不能输出；request/index/intent/source 必须属于后端 matrix，严格 JSON 不接受多余或不完整内容。
- I-2：`sourceRuleIds` 非空，且只能是本 intent evidence 的子集，不能跨 request。
- I-3/I-4：后端生成固定标题、连续编号与邮件 frame；内部状态不进入外发正文。
- I-5：高风险数字、金额/区间、年限、频次和 URL 必须与引用 QA subject/body 精确规范化对应；未解析到引用事实时 fail closed。
- I-6：deterministic fallback 仅按 supported intent 的 evidence 组装；不能因为 research intent 而丢弃已确认的双证据。
- I-7：首轮与 CTA correction retry 都必须 materialize、source/claim validate；无效 response 走 fallback，不能进入 sanitizer 或 draft。
- 已有决定：coverage matrix 是 intent/evidence 的唯一来源；`sendQaRuleIds` 与 prompt 全集分离；GROUNDED/PARTIAL/UNSUPPORTED 仅是操作端数据；research fit 需双证据且不触发外部资料抓取。（K-compound-request-coverage-intent-atomic、K-ai-reply-prompt-vs-send-rule-ids、K-grounding-status-ui-only、K-research-fit-dual-evidence）

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 每个 request 同时有两个及以上 supported intent、模型遗漏其中一个时 | materializer 只验证输出 intent 属于 allowed 集且不重复，没有验证每个 request 的输出 key 集等于 supported 集。模型可只回答其中一项，整次结构仍被接受并外发，违反逐 intent 覆盖。 | `AiReplyGroundedDraftMaterializer.kt:106-109,139-143,171-194` |
| P1-2 | P1 | 含数值/金额/年限或 URL 的模型答案；URL 同 host 但不同路径，或数值是来源较长数字的子串时 | validator 只以 host 或任意子串判定 URL/数字存在，且只读取 `replyBody`；`https://host/other` 会被已引用 `https://host/approved` 放行，`2` 可被来源 `2026` 放行，QA subject 的合法依据又被忽略。引用 rule 在验证时缺失/空时还 `continue` 并判定有效。 | `AiReplyHighRiskClaimValidator.kt:26-30,47-55,58-82` |
| P1-3 | P1 | LLM 初稿有 CTA，correction retry 改写时带入任一高风险虚构声明 | retry claim 校验失败后仍把无效 `materialized.text` 赋给 `text` 并标记 `used=true`，最终仅经 CTA sanitizer；金额、URL、保证性等不会被该 sanitizer 移除，故虚构内容可外发。 | `AiReplyDraftService.kt:399-419` |
| P1-4 | P1 | `expertise.programme_fit` 已由 profile + `programme.scope` 支持，但初稿结构/claim 校验失败时 | fallback 无条件跳过 `requiresResearchContext` item，空掉已 supported 的研究匹配 section，而不是按该 intent 的 evidence 组装；与 I-6 的同矩阵 fallback 不符。 | `AiReplyPointByPointComposer.kt:60-86` |

## 修复规格

### P1-1：每个 section 严格覆盖全部 supported intent

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`

1. 对每个 section 求 `answers.intentKey` 集合，并与该 request 的 `SUPPORTED` intent key 集合做精确相等校验；漏项、未知项和重复项一律返回 structured-invalid。
2. `sourceRuleIds` 的每个元素都必须是 JSON 整数，不能以过滤无效元素的方式接受混合数组；继续要求非空、同 request、intent evidence 子集。
3. 新增一个 request 有两个 supported intent、模型只输出一个的负例；再覆盖混合类型 source ID 的负例。完整 schema 仍走既有 composer，raw JSON 不得进入正文。

### P1-2：高风险 token 与来源解析严格 fail closed

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`

1. 对每个已引用 rule 使用其规范化 `replySubject + replyBody` 作为来源；任一 `sourceRuleId` 缺失或来源文本为空时返回 machine warning 并使整个校验无效。
2. 数字、金额、区间、年/月时长、频次按完整规范化 token 比较，禁止子串命中；URL 按完整规范化 URL 比较，不能以同 host 替代。保留当前的空白/逗号规范化，不增加外部校验或语义分类。
3. 保持 modality 和 phrase-family 校验不变，仅复用同一来源规范化文本。
4. 新增：`2` 不得从 `2026` 取得依据、同 host 不同 URL path 被拒绝、subject 中的合法声明可通过、引用 rule 缺失/空文本被拒绝。

### P1-3：retry claim 无效必须回退

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

1. QA_GROUNDED correction retry 的 materialize 或 claim 校验失败时，丢弃 retry text，调用现有 deterministic fallback；返回 `usedLlm=false`、`FALLBACK_NO_RESPONSE`，累积 structured/claim warning。
2. 不将未通过 claim 校验的 `materialized.text` 传给 action sanitizer，也不改变 QA_MATCHED/FREE_FORM 的 retry 路径、sendQaRuleIds 或 readiness 聚合。
3. 新增 two-call client 回归：首轮触发 CTA correction，第二次 JSON 含未引用金额或 URL；断言 fallback 文本中无该虚构内容、状态为 fallback、warning 含 `AI_REPLY_CLAIM_VALIDATION_FAILED` 与 validator code。

### P1-4：research intent fallback 仍消费已支持 evidence

**文件**：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`

1. 移除以 `requiresResearchContext` 一律跳过 fallback 的分支；仅由 `SUPPORTED` intent 的 evidence rule IDs 选择可组装内容。
2. 不把 profile 原文写入正文，也不把 QA scope 伪装成研究匹配结论；仍只输出该 intent 审核规则正文，未支持 intent 仍仅保留空 heading。
3. 新增 research fit 双证据已确认、fallback 发生时正文保留其 `programme.scope` QA facts 的测试；保留 profile 原文不得出现在外发草稿的断言。

## 当前状态（修前）

- Build：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`（2026-07-15）。
- 定向测试：PASS — 125 passed, 0 failed, 0 skipped：`AiReplyGroundedDraftMaterializerTest` 11、`AiReplyHighRiskClaimValidatorTest` 10、`AiReplyPointByPointComposerTest` 11、`AiReplyDraftServiceTest` 93。
- JS：PASS — 301 passed, 0 failed, 0 skipped（由 Maven node-test 执行）。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 strict intent JSON | ❌ P1-1 | `AiReplyGroundedDraftMaterializer.kt:106-109` 得到 allowed 集，`:139-143` 只做成员/重复检查，`:171-194` 只要求 section 非空和 request index 完整，未要求每个 supported intent 都出现。 |
| I-2 source 属于本 intent evidence | ✅ | `AiReplyGroundedDraftMaterializer.kt:158-168` 从该 request 的同 key intent 取 `evidenceRuleIds` 并拒绝集合外 ID。 |
| I-3 fixed heading/frame/numbering | ✅ | `AiReplyPointByPointComposer.kt:24-46,126-169` 用 catalog group title，按所有 request index 组装 frame 与 heading。 |
| I-4 internal state 不外发 | ✅ | `AiReplyGroundedDraftMaterializer.kt:197-222` 拒绝 status/internal marker；`AiReplyPointByPointComposer.kt:157-164` 空 section 仅写 heading。 |
| I-5 high-risk fail closed | ❌ P1-2 | `AiReplyHighRiskClaimValidator.kt:26-30` 空 source 直接跳过；`:47-55` 只读取 body；`:58-82` 用数字子串和 URL host 判定。 |
| I-6 fallback 同 matrix | ❌ P1-4 | `AiReplyPointByPointComposer.kt:60-86` 遍历 request facts 但 `:64-65` 直接丢弃所有 research item。 |
| I-7 retry 同 schema + claim validate | ❌ P1-3 | `AiReplyDraftService.kt:399-415` retry 虽调用 validator，却在失败分支把无效 materialized draft 继续赋给 `text`。 |
| T1 materializer tests | ❌ P1-1 | `AiReplyGroundedDraftMaterializerTest.kt:58-176` 仅含每 request 单 supported intent；无漏 supported intent 或 mixed source-id 负例。 |
| T2 validator tests | ❌ P1-2 | `AiReplyHighRiskClaimValidatorTest.kt:25-194` 有简单金额/URL/phrase 覆盖，但无 exact-token、same-host-different-path、subject 或缺失来源场景。 |
| T3 composer tests | ❌ P1-4 | `AiReplyPointByPointComposerTest.kt:72-96` 固化 research item 为空，未验证双证据 research fallback。 |
| T4 DraftService retry tests | ❌ P1-3 | `AiReplyDraftServiceTest.kt:2519-2570` 只覆盖 CTA retry 成功，未覆盖 retry claim 失败回退。 |
| T5 scope/no extras | ✅ | 仅列入计划的 9 个文件；没有修改迁移、自动回复、review UI 或 QA 事实。 |

## 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；本阶段无持久化状态机，readiness 只由 matrix 派生。
- Cross-plan check：❌ P1-1/P1-4。Phase 6 写出的 supported intent/evidence matrix 在本阶段被不完整 JSON 接受或 fallback 丢弃；P1-2/P1-3 使 Phase 7 的引用与 retry 合约无法保证端到端 fail closed。

## 观察（非阻断）

- `AiReplyHighRiskClaimValidator.validate` 当前未使用 `requestFacts` 参数；待 P1 修复后若仍无必要可另行清理，不纳入本轮功能修复。
