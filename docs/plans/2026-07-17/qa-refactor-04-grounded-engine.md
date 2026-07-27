# QA 重构 04：无 coverage 标签的 Grounded 事实引擎

## 需求描述

用“专家请求 → 内部意图 → 原子 QA 事实”替换 `coverageKeys` 判定；只要存在 QA 事实，AI 草稿统一走 `QA_GROUNDED`，LLM 只能引用 `answerBody`，后端校验引用并组织成自然邮件。可观察结果：单问题和多问题都不再直接拼接规则正文，草稿减少编号、模板化开场和重复表达。

必须不变：

- `sendQaRuleIds` 仍只包含真实匹配/人工显式选择并实际作为证据的规则；无匹配不得回退 QA 全集。
- 专家画像、历史邮件、对话 few-shot 只能提供上下文/风格，不能替代 QA 业务事实。
- 模板变量仍保留 raw draft，最终由现有 preview/send seam 渲染。
- 本计划不切换自动 SMTP 路径；自动回复仍由子计划 05 控制。

Out of scope：工作台 UI；自动发送；TrustProfile；embedding/RAG；删除 coverage_keys 物理列。

## 关键不变量

### Invariant I-1：事实映射不依赖 QA 标签
- Rule：request→fact 只使用 request 原文、`AiReplyIntentCatalog` 内部别名、规则 `displayName/keywords/matchMode/priority/answerBody/replyPolicy/enabled`；不得读取 `coverageKeys` 或 `QaCoverageKeyCatalog`。
- Applies to：QaFactSelectionService、AiReplyDraftService。
- Violation consequence：运营仍需维护额外能力标签，复杂度回归。
- 来源：original。

### Invariant I-2：原子事实分配可解释
- Rule：每个 request 先识别内部 intent；每条候选规则必须先匹配 request，再按“规则匹配短语与 intent title/aliases 的归一化重合分数”分配到唯一最佳 intent；最高分必须 >0，平分按 intent catalog 顺序；无具体 intent 时进入 `general.answer`。
- Applies to：QaFactSelectionService。
- Violation consequence：同一规则被复制给多个子问题，形成假覆盖。
- 来源：original；K-request-facts-not-flat-pool。

### Invariant I-3：显式选择仍受事实边界约束
- Rule：操作员显式 `qaRuleIds` 可加入 AUTO/REVIEW 事实，但服务端必须重新加载、校验 enabled/policy/answerBody，并按 I-2 分配；NEVER、disabled、空事实、未知 ID 拒绝，不能只信浏览器。
- Applies to：AiReplyDraftService.generate/resolve、后续工作台。
- Violation consequence：客户端注入内部事实或无关规则绕过 grounded。
- 来源：original。

### Invariant I-4：状态由 intent 证据与事实策略计算
- Rule：一个 request 的全部 intent 有事实=`GROUNDED`；部分有事实=`PARTIAL`；全部无事实=`UNSUPPORTED`。整体有 UNSUPPORTED=`BLOCKED`，否则有 PARTIAL=`NEEDS_REVIEW`，否则由事实策略收口：任一证据为 `REVIEW` 则 `NEEDS_REVIEW`，全部为 `AUTO` 才是 `READY`。无 QA 事实且存在请求不能默认为 READY。
- Applies to：RequestFactItem、resolveDraftReadiness、API response。
- Violation consequence：缺失事实被伪装为完整草稿。
- 来源：original。

### Invariant I-5：引用集合与审计集合分离
- Rule：promptFacts 可包含服务端确认的候选；`sendQaRuleIds` 必须等于最终 requestFacts 中 evidenceRuleIds 的稳定去重并集，顺序为 request index→intent order→priority→id；不得用 QA 全集或仅“建议过”的 ID。
- Applies to：ResolvedQaRules/AiReplyDraftResult、后续 mail_record_qa_rule。
- Violation consequence：审计记录无关事实，无法证明回复来源。
- 来源：K-ai-reply-prompt-vs-send-rule-ids、K-audit-selected-source。

### Invariant I-6：LLM 只能改表达
- Rule：grounded JSON 中每个 answer 必须引用非空 sourceRuleIds 且为对应 intent evidence 子集；数字、URL、金额、期限、政府、费用、合同、IP、保密与确定性强度必须由引用 answerBody 支持。
- Applies to：grounded prompt、materializer（既有）、high-risk validator。
- Violation consequence：产生不可核验承诺，损害专家信任。
- 来源：original；现有高风险校验知识。

### Invariant I-7：自然邮件结构由后端控制
- Rule：正常 grounded 草稿使用称呼/一次必要致谢 + 2–4 个自然段 + 结束语；默认不生成 `1./2.` 标题、`Program & eligibility` 类章节、内部状态和机械 cross-reference。专家原信明确编号且超过 3 项时，允许正文按原顺序使用简短项目符号，但不暴露 intent/status/rule ID。
- Applies to：AiReplyPointByPointComposer、system prompt、deterministic fallback。
- Violation consequence：AI 感、模板感仍明显。
- 来源：original。

### Invariant I-8：LLM 失败不伪装成功
- Rule：LLM disabled/unavailable/empty/invalid/claim-failed 时保留对应 generationState、usedLlm=false，并输出按 request 顺序的事实摘要；readiness 不得被 fallback 提升。该摘要只供人工，不是自动发送候选。
- Applies to：AiReplyDraftService fallback、子计划 05 auto gate。
- Violation consequence：确定性拼接被误当自然且已校验的 AI 邮件。
- 来源：K-llm-timeout-fallback。

## 现状审计

### `qa_rule`（只读）
- Schema/mapping：子计划 02/03 后有 `answer_body` 与 `reply_policy`；旧 `reply_body/coverage_keys` 仍存在。
- Write paths：Flyway + QaRuleManagementService；本计划不新增写路径。
- Read paths：
  1. `AiReplyDraftService.resolveQaRules()` 当前从 `QaMatchService.suggestComposition().gapItems` 取候选，再读取 `replyBody/coverageKeys`。
  2. `buildMatchedUserContent/buildGroundedUserContent/buildFreeFormUserContent` 当前多处读取 replyBody。
  3. `AiReplyPointByPointComposer.joinFacts()` 与 HighRiskClaimValidator source text 读取 replyBody。
- Interaction points：QA 后台写 answerBody/policy → 新选择器与 prompt/validator 读；规则启停/策略变化必须在下一次生成即时生效。

### AI 草稿内存协议
- `AiReplyMode` 当前有 QA_MATCHED/QA_GROUNDED/FREE_FORM；单问题命中进入 QA_MATCHED 并要求 verbatim 拼接。
- `RequestIntentDefinition` 当前持有 required/alternative coverage keys；`resolveIntentCoverage` 依赖 ruleCoverageKeys。
- `AiReplyGroundedDraftMaterializer` 已严格校验 JSON schema、requestIndex、intentKey、sourceRuleIds 子集，可继续复用。
- `AiReplyPointByPointComposer` 当前为每个 request 固定编号和标题，fallback 直接 join replyBody。
- Interaction points：选择器输出 requestFacts → prompt → materializer → validator → composer → controller response。

### `operator_action_log`（本计划沿用写路径）
- Schema：V19；after_value TEXT JSON。
- Write path：`AiReplyReviewAuditService.recordInitialDraft()` 按 readiness 写 READY/NEEDS_REVIEW/BLOCKED action。
- Read path：`QaRuleAuditService.aggregateAiReplyQualityMetrics()` 统计 action 数量。
- Interaction points：新 readiness 语义必须继续落到同一三种 action，不新增虚假 READY。

## 实现方案

### T1：新增事实选择器
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`
- 输入：inboundText、optional selectedRuleIds、researchProfileSufficient。
- 输出：requestFacts、promptFactIds、sendQaRuleIds、unsupportedRequests、readiness 输入数据。
- 实现 I-1..I-5；归一化与 keyword ANY/ALL 语义抽成 service 内纯函数，测试可直接调用。
- 不读取 content variants、replyBody、coverageKeys；只读 answerBody。

### T2：简化 intent catalog
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`
- 删除 required/alternative coverage key 作为判定依据；保留稳定 intent key/title/aliases/requiresProfile。
- 新增规则关键词与 intent alias 的 score helper；保留现有 URL mask、programme/dash/大小写归一化和 selection/matching/project-types 消歧。
- 为兼容 response，`missingEvidenceKeys` 暂返回缺失 intent key，而非 coverage key；前端子计划 06 只显示中文状态/标题，不显示该字段。
- 遵守 I-1、I-2、I-4。

### T3：AiReplyDraftService 统一 grounded
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 注入 QaFactSelectionService，移除 resolve 对 QaMatchService/coverage catalog 的依赖。
- 有任何 QA evidence 时固定 QA_GROUNDED；QA_MATCHED enum 暂保留兼容但不再产生。
- grounded/free-form knowledge 都读取 answerBody；无 evidence 不回退 QA 全集。
- system prompt 加入 I-6/I-7 的信任表达约束；保留 action policy、few-shot 仅风格、模型选择、raw/rendered 边界。
- readiness 与 audit ID 分别遵守 I-4、I-5；fallback 遵守 I-8。

### T4：自然后端组装与风险源切换
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- composer 按 I-7 组织自然段，去掉固定编号/标题/cross-reference；fallback 使用 answerBody，重复事实只保留一次。
- validator 的 source text 改为 displayName + answerBody，不读取 replySubject/replyBody；保留数字、URL、modality、高风险 phrase family 对称校验。
- 遵守 I-5、I-6、I-7、I-8。

### T5：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`
- 覆盖：无标签选择、复合 intent 唯一分配、显式选择校验、readiness、无全集兜底、自然段结构、answerBody source、高风险失败与 fallback 状态。
- 测试必须逐项断言 I-1 至 I-8。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 新增 request→fact 选择器 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 去 coverage 判定、保留内部 intent |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | grounded 统一入口 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | 自然段组装 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | answerBody 事实校验 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 新选择器测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 生成模式/状态测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | 自然结构测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt` | 高风险测试 |

共 9 个文件、2 个子系统（QA fact selection + LLM generation），符合限制。

## 验收标准

- I-1：生产代码对 `coverageKeys/QaCoverageKeyCatalog` 的 grounded 引用为 0；旧类可暂留给兼容 UI/API但不参与生成。
- I-2：selection+matching 复合请求分别绑定对应原子规则；一个规则不会同时出现在两个 intent evidence 中。
- I-3：selected unknown/disabled/NEVER/blank answer 返回 400；显式选择 REVIEW 可生成但 readiness 至少 NEEDS_REVIEW（若策略本身要求审核）。
- I-4：全/部分/无证据的 request/overall 状态断言准确；请求非空且无事实不为 READY。
- I-5：sendQaRuleIds 精确等于 evidence 并集；无匹配为空；顺序稳定。
- I-6：非法 sourceRuleIds、额外数字/URL、modality strengthening、高风险无来源均 fallback 且 usedLlm=false。
- I-7：正常邮件不含 `1.`、内部 intent key、GROUNDED/PARTIAL/UNSUPPORTED、固定章节；正文 2–4 个自然段（多于 3 个明确编号请求例外）。
- I-8：四种失败路径保留 generationState，readiness 不提升。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,AiReplyPointByPointComposerTest,AiReplyHighRiskClaimValidatorTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test
```

## 人工验收清单

### A-1：单问题可信回复
- 前置条件：身份质疑来信；公司法定名称和核验方式各有原子 QA 事实，policy=REVIEW。
- 操作步骤：调用 `/ai-reply/turn` 首轮生成。
- 预期结果：mode=`QA_GROUNDED`；草稿自然回应质疑并给核验事实；不出现章节标题、规则 ID、coverage key；draftReadiness=`READY` 或因 policy 显示人工审核，但不可为 BLOCKED。
- 覆盖：I-1、I-6、I-7。

### A-2：复合问题部分缺失
- 前置条件：来信同时问 responsibilities 和 deliverables；只有 responsibilities 事实。
- 操作步骤：生成草稿并查看 request coverage。
- 预期结果：该 request 为 PARTIAL，整体 NEEDS_REVIEW；sendQaRuleIds 只含 responsibilities 规则；正文不得用 responsibilities 冒充 deliverables。
- 覆盖：I-2、I-4、I-5。

### A-3：无匹配不回退全集
- 前置条件：一封 QA 中没有依据的问题。
- 操作步骤：生成首轮。
- 预期结果：qaRuleIds=[]；不存在任意 QA 规则正文；readiness=BLOCKED；草稿若生成只能是明确人工处理的自由表达，不含具体项目承诺。
- 覆盖：I-4、I-5。

### A-4：人工显式选择校验
- 前置条件：准备 AUTO、REVIEW、NEVER、disabled 四条事实。
- 操作步骤：分别作为 qaRuleIds 提交续轮。
- 预期结果：AUTO/REVIEW 可进入对应 request；NEVER/disabled 请求被后端拒绝，不能靠 API 注入。
- 覆盖：I-3。

### A-5：高风险增写
- 前置条件：事实只写“may receive support”，不含保证金额和 URL。
- 操作步骤：模拟模型返回“will definitely receive 10 million RMB”或新增 URL。
- 预期结果：校验失败，usedLlm=false，generationState=FALLBACK_NO_RESPONSE；草稿不含新增保证/URL。
- 覆盖：I-6、I-8。

### A-6：表达自然度
- 前置条件：三条有依据的问题。
- 操作步骤：连续生成 5 次并人工阅读。
- 预期结果：每封只有一次致谢；无 `Program & eligibility`、固定编号清单、重复签名；事实一致，表达可变化。
- 覆盖：I-7、observable outcome。

### A-7：QA 编辑到生成跨路径
- 前置条件：编辑一条 answerBody，保留 keywords/policy。
- 操作步骤：保存后立即对命中来信生成。
- 预期结果：prompt/source validation 使用新 answerBody；旧 replyBody 内容不再进入 AI grounded 草稿。
- 覆盖：qa_rule 写→grounded 读 interaction point。
