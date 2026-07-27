# AI 回复第 5 步：信任边界、动作权限与草稿状态

## 需求描述

- 可观察结果：Grounded 草稿中的身份、机构角色、企业、资金、费用、合同、知识产权和保密声明必须可追溯到对应 `answerBody`；“请相信我们”、用保密/敏感替代证明、隐藏服务机构角色、虚构已匹配企业等内容不能成为可采用的 LLM 草稿。
- 可观察结果：AI 不得索要护照、身份证、工作证明等敏感材料；索要 CV 仅在服务端授权、关键疑虑已解除，且正文同时说明用途和自愿性时允许；会议/通话只能由专家来信或运营指令明确授权。
- 可观察结果：所有确定性校验共享一次修复机会；修复仍失败时降级为安全事实草稿，并用 `READY / NEEDS_REVIEW / BLOCKED` 准确表达当前草稿是否可自动发送。

必须保持不变：

1. 第 4 步的统一 JSON schema、claim/sourceIds 精确校验、问题顺序、最多 4 个自然段、无固定章节标题和不丢 claim 的契约不变。
2. QA 规则仍以 `answerBody` 为唯一事实正文，`coverageKeys` 只表示事实覆盖，`replyPolicy` 仍决定 AUTO/REVIEW；不得重新使用 `replyBody` 或变体作为事实来源。
3. FREE_FORM 的 prompt、自然表达和既有 fallback 不变；本计划新增的敏感材料硬门禁例外，必须同样适用于 FREE_FORM。
4. 自动回复仍要求全 AUTO、全量 grounded、`READY`、`LLM_USED` 和 kill switch 开启；本计划不放宽任何自动发送条件。
5. AI 初稿状态继续仅用于生成质量展示和自动回复决策；人工采用后直接发送不得读取历史 READY/NEEDS_REVIEW/BLOCKED 作为发送审批条件。（来源: K-ai-generation-observability-not-send-gate）
6. 不修改数据库 schema、QA 数据、API DTO、前端 DOM/CSS、审计事件、变量渲染、邮件投递和 QA 关联表写入。

不在本计划范围：

- 前端新增原因文案、状态组件或样式；第 6 步再处理草稿审计、证据展示和人工编辑提示。
- SMTP 前对最终变量渲染正文再次执行信任校验；该项与预览/实发同源门禁一起放入第 7 步。
- QA `coverageKeys` 后台编辑、历史数据补齐、规则拆分或数据库迁移。
- 护照、身份证、工作证明在正式申请阶段的材料采集流程；本计划只约束人才引进初始邮件回复。
- 新增 `AiReplyAction` 类型或修改公开 JSON 的 `proposedAction.type` 枚举。

## 关键不变量

### Invariant I-1: 声明必须逐项追溯到批准事实
- Rule：Grounded 每个 claim 必须只引用其计划内 `sourceIds`；正文数字、URL、确定性措辞和高风险声明必须存在于这些 ID 对应的非空 `answerBody`。缺 ID、未知/禁用 ID、跨 intent 借据、`replyBody` 回退均不得通过。
- Applies to：`AiReplyHighRiskClaimValidator.validate()`、`AiReplyDraftService` 首次生成和修复生成、现有 materializer → validator 调用链。
- Violation consequence：模型可把无关事实拼成公司身份、资金、费用或合同承诺。
- 来源：K-grounded-json-materialize-before-policy、K-high-risk-phrase-family-symmetric-match、K-ai-reply-modality-plain-will。

### Invariant I-2: 信任疑虑不能靠修辞或信息遮蔽回答
- Rule：正文不得包含 `trust us`、`you can trust us`、`rest assured` 及中英文同义“请相信/放心”等信任替代话术。存在关键身份/授权/资金缺口时，不得用“项目敏感/保密所以无法说明”替代证明。若 claim 的批准事实明确表明我方是 service provider/agency/intermediary/adviser，正文必须保留同族角色披露；若 enterprise 事实明确表示尚未匹配具体企业，正文必须保留“尚未确定/匹配后披露”的不确定性，不得声称已有具体企业。
- Applies to：`AiReplyHighRiskClaimValidator` 的结构化 claim 校验和整封 Grounded 正文校验。
- Violation consequence：回复形式上引用了事实，实际却用高置信措辞隐藏角色或制造不存在的确定性，直接损害专家信任。
- 来源：original；条件措辞同族判定遵守 K-high-risk-phrase-family-symmetric-match。

### Invariant I-3: 敏感材料永不由初始 AI 回复索取
- Rule：护照、身份证/national ID、工作或在职证明、银行证明等身份/资质敏感材料的直接索取，在本流程任何状态下都不授权；即使专家来信或运营指令出现同类词，也只能由后续正式申请流程处理。检测必须覆盖祈使、疑问、委婉请求及中英文别名。
- Applies to：`AiReplyActionPolicy.detectActions/findViolations/sanitize`、Grounded 修复门禁、FREE_FORM 最终动作门禁。
- Violation consequence：在信任尚未建立时索取高敏材料，形成诈骗特征和合规风险。
- 来源：original；matcher 对称性来源 K-ai-reply-action-cta-variant-coverage、K-action-sanitizer-preserve-layout。

### Invariant I-4: CV 与会议动作必须满足服务端权限
- Rule：保留公开 `REQUEST_MATERIALS / PROPOSE_MEETING / NONE`。CV/履历请求仅在以下条件同时成立时有效：`REQUEST_MATERIALS` 位于 server plan、没有 blocking trust gap、请求所在段落包含明确用途（资格初核/研究匹配/申请评估之一）和自愿性（optional/if you wish/if comfortable/自愿/方便时之一）。会议/通话仅在 `PROPOSE_MEETING` 位于 server plan 时有效。正文最多出现一种 proposed action，且继续与 JSON `proposedAction` type/text 完全一致。
- Applies to：`AiReplyActionPolicy`、`AiReplyGroundedContentPlanner.buildPlan()`、`AiReplyDraftService` 首次/修复候选校验；现有 materializer proposedAction parity 校验保持。
- Violation consequence：模型绕过流程提前索取资料、强推会议，或 JSON 声明 NONE 但正文暗含 CTA。
- 来源：K-grounded-proposed-action-body-parity、K-ai-reply-action-cta-variant-coverage。

### Invariant I-5: 三态只由服务端确定性事实计算
- Rule：`READY` 仅当所有请求均有完整事实、所有证据规则为 AUTO、无 deterministic validation/action failure；`NEEDS_REVIEW` 仅用于不存在 blocking gap 但含 PARTIAL、已分类的非关键 UNSUPPORTED 或 REVIEW 规则；`BLOCKED` 用于关键 trust intent（`company.* / agency.* / finance.* / fees.*`）缺失、无法分类的 UNSUPPORTED、请求存在但 evidence 为空、NEVER evidence、或一次修复后仍有声明/信任/动作失败。LLM 关闭、客户端不可用或无响应本身不得把事实完整的确定性 fallback 改成 BLOCKED，但自动发送仍因非 `LLM_USED` 被拒绝。
- Applies to：`AiReplyGroundedContentPlanner` trust family 判定、`AiReplyDraftService.resolveDraftReadiness*()` 和所有 `AiReplyDraftResult` 返回分支、`GroundedAutoReplyDecisionService`。
- Violation consequence：非关键缺口被过度阻断，或关键身份/资金缺口被误标 READY；人工工作台和自动回复对同一事实集显示不同状态。
- 来源：K-grounded-trust-family-route、K-ai-generation-observability-not-send-gate。

### Invariant I-6: 确定性失败最多修复一次
- Rule：Grounded 一次 generate 调用最多产生“首次模型调用 + 1 次校验修复调用”。统一校验顺序固定为 strict materialize → source claim → trust boundary → action boundary；任一步失败都进入同一个修复入口，修复使用同一 content plan、同一 JSON schema 和同一 sourceIds。第二次失败直接确定性 fallback，不得再进入现有 action 专用重试或递归调用。
- Applies to：`AiReplyDraftService.generate()`、Grounded candidate/materialize 分支、`enforceActionPolicy()`。
- Violation consequence：同一模型无限自证、调用成本失控，或 claim 失败和 action 失败获得不同安全待遇。
- 来源：original；统一 schema 顺序来源 K-grounded-json-materialize-before-policy。

### Invariant I-7: 失败原因稳定且不泄露事实正文
- Rule：新增/复用稳定 warning code：`AI_REPLY_CLAIM_TRUST_RHETORIC`、`AI_REPLY_CLAIM_CONFIDENTIALITY_SUBSTITUTE`、`AI_REPLY_CLAIM_ROLE_DISCLOSURE_OMITTED`、`AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED`、`AI_REPLY_ACTION_SENSITIVE_MATERIAL`、`AI_REPLY_ACTION_CV_PURPOSE_MISSING`、`AI_REPLY_ACTION_CV_OPTIONALITY_MISSING`、`AI_REPLY_TRUST_REPAIR_EXHAUSTED`。`contextWarnings` 去重且顺序稳定，只含 code；不得写入 answerBody、邮件全文、prompt 或 source text。
- Applies to：validator/action result、修复 prompt 的 reason 列表、`AiReplyDraftResult.contextWarnings`、自动回复 reason 归类。
- Violation consequence：前端/审计无法稳定分类，或批准事实、专家来信被错误暴露到日志/指标。
- 来源：K-review-event-audit-payload-bounds。

### Invariant I-8: 自动决策优先识别校验失败
- Rule：`GroundedAutoReplyDecisionService.resolveReason()` 必须先识别 structured/claim/trust/action validation warnings，再判断通用 grounding gap；校验失败统一返回 `AI_REPLY_VALIDATION_FAILED`。只有无校验失败时，BLOCKED/NEEDS_REVIEW 才分别映射 grounding gap/policy review。`passesSendGate()` 继续要求 exact verified AUTO IDs、无 PARTIAL/UNSUPPORTED、READY、LLM_USED 和非空正文。
- Applies to：自动回复真实处理和 `AutoReplyPreviewService` 共享的 decision seam。
- Violation consequence：确定性校验失败被伪装成普通无匹配，预览与自动处理无法区分模型不安全输出。
- 来源：K-preview-mirrors-pipeline、K-preview-runtime-gates-visible。

### Invariant I-9: 第 4 步结构与版式契约不可回退
- Rule：本计划不得放宽 exact JSON 字段、paragraph/claim 顺序、sourceIds integral/范围、missingFacts/requiresReview/proposedAction parity、最多四段、不丢 claim、无固定标题和无内部标签。修复响应也必须走相同 materializer，不得直接把模型 plain text 交给 action sanitizer。
- Applies to：`AiReplyDraftService` 首次和修复路径；现有 materializer/composer 只作为未修改依赖。
- Violation consequence：为修复信任门禁重新引入第 4 步已关闭的 JSON、段落和来源缺陷。
- 来源：K-grounded-json-paragraph-order、K-grounded-json-materialize-before-policy、K-grounded-paragraph-cap-never-drop-claims。

### Invariant I-10: 人工发送权限与生成状态解耦
- Rule：本计划只改变生成结果和自动 decision；不得修改 `PendingMailOperationService.sendManualRichReply()`，不得新增 draft identity/review confirmation，也不得让历史 READY/NEEDS_REVIEW/BLOCKED 阻断采用后的人工邮件。人工发送仍执行现有占位符、最终渲染、QA 事实和关联审计校验。
- Applies to：变更文件边界、回归测试与人工验收。
- Violation consequence：辅助写作状态重新变成不可绕过的发送审批，破坏已确认的运营流程。
- 来源：K-ai-generation-observability-not-send-gate、K-ai-review-server-authoritative-snapshot、K-manual-rich-render-before-send。

## 现状审计

### `qa_rule` MySQL 表
- Schema/mapping：由 `V1__create_business_tables.sql` 建表，`id` 主键、`category_id` 外键；有效事实字段由后续迁移形成：`answer_body TEXT NOT NULL`（V79）、`coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`（V76）、`reply_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW'`（V80），并保留 `keywords/match_mode/priority/enabled/reply_body/auto_reply_enabled/handoff_required`。`QaRule.replyPolicyEnum()` 只接受 AUTO/REVIEW/NEVER。
- Write paths：
  1. `QaRuleManagementService.createRule()` — 校验事实正文后写 `replyBody=answerBody`、`answerBody`，但固定 `coverageKeys=""`，并同步 legacy policy 字段。
  2. `QaRuleManagementService.updateRule()` — 更新 category/keywords/matchMode/priority/displayName/answerBody/enabled/replyPolicy；当前不更新 `coverageKeys`。
  3. `QaRuleManagementService.setRuleEnabled()` / `deleteRule()` — 启停或删除规则。
  4. Flyway V3/V17/V18/V38/V40/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76/V77/V79/V80 — 历史 seed、修复、拆分、coverage/answer/policy backfill；本计划不新增迁移。
- Read paths：
  1. `QaMatchService`、`QaFactSelectionService` — 读取 enabled/matchable rules、keywords、coverageKeys、answerBody 形成 request → intent → evidence。
  2. `AiReplyDraftService`、`AiReplyPointByPointComposer` — 读取 answerBody/source IDs 和 replyPolicy 生成 prompt/fallback/readiness。
  3. `AiReplyHighRiskClaimValidator` — 按 source ID 读取 displayName + answerBody，执行数字、URL、情态和高风险声明校验。
  4. `GroundedAutoReplyDecisionService` — 重新读取 enabled、answerBody、replyPolicy，验证自动发送 evidence。
  5. `PendingMailOperationService`、`MailComposeTemplateService`、`InboundMailTagService`、`MailMonitoringService` — 规则展示、模板引用、标签与监控；本计划不得改变其语义。
- Interaction points：后台/迁移写入的 `answerBody + coverageKeys + replyPolicy + enabled` 被 selection、planner、validator、readiness、auto decision 连续读取。由于后台 create/update 当前不维护 coverageKeys，本计划必须对“有请求但无法分类/无 evidence”fail closed，不得用 displayName/keywords 推断批准事实。

### Grounded 内存契约：`RequestFactItem → GroundedContentPlan → AiReplyDraftResult`
- Schema/mapping：非持久化对象。`RequestFactItem` 保存 request 顺序、GROUNDED/PARTIAL/UNSUPPORTED、intentKey/evidenceRuleIds；`GroundedContentPlan` 保存 claims/paragraphs/missingFacts/allowedActions/requiresReview；`AiReplyDraftResult` 对外保存正文、qaRuleIds、warnings、generationState、draftReadiness。
- Write paths：
  1. `QaFactSelectionService.select()` — 唯一构造 requestFacts/evidence 选择结果。
  2. `AiReplyGroundedContentPlanner.buildPlan()` — 从 requestFacts 和初步 allowedActions 构造服务端计划。
  3. `AiReplyDraftService.generate()` — 所有 LLM、fallback、修复分支构造最终 `AiReplyDraftResult`。
- Read paths：
  1. `AiReplyDraftService` prompt/materialize/composer — 读取 plan 约束模型和组装正文。
  2. `UnmatchedInboundMailController.aiReplyTurn()`、`AiTrainingController.simulate()` — 返回 readiness、coverage、warnings 和 generationState。
  3. `AiReplyReviewAuditService.recordInitialDraft()` — 读取 readiness/generationState 写 best-effort 初稿观测事件；失败不得影响返回。
  4. `GroundedAutoReplyDecisionService.decide()` — 读取 readiness/warnings/usedLlm/qaRuleIds，决定 preview/真实自动回复是否可发。
  5. `PendingMailOperationService.suggestComposedReply()/evaluateComposedReply()` — 调用同一 `resolveDraftReadinessForSelection()` 展示工作台事实状态。
- Interaction points：
  1. planner 对 blocking trust gap 的判定必须同时喂给 allowedActions 和 DraftService readiness，不能出现“状态 BLOCKED 但 JSON 仍允许索取 CV”。
  2. DraftService warning/readiness 被 controller、audit、workbench 和 auto decision 消费；公开 enum/DTO 不改，只改变服务端计算结果。
  3. 首次和修复模型输出必须经同一 materializer/validator/action 链；否则 auto decision 无法相信 READY。
  4. auto preview 与真实自动处理共享 `GroundedAutoReplyDecisionService`；reason 判定顺序必须同源。（来源: K-preview-mirrors-pipeline）

### 动作与声明校验现状
- `AiReplyActionPolicy` 当前只有 `REQUEST_MATERIALS / PROPOSE_MEETING`，可从来信/运营指令授权，能检测并按原 offset 删除未授权句子；尚不区分敏感证件、CV 用途/自愿性，也未把 trust gap 纳入授权。
- `AiReplyHighRiskClaimValidator` 当前能校验 source availability、数字/URL、条件措辞强化和高风险 phrase family；尚不校验信任修辞、保密替代证明、机构角色披露和未匹配企业的不确定性保留。
- `AiReplyDraftService` 当前只对 action violation 做一次专用重试；structured/claim/trust 失败直接 fallback，存在校验类型处理不一致。action 重试还嵌在 `enforceActionPolicy()`，改造时必须避免统一修复后再次触发第二轮。
- 当前 readiness：任一 UNSUPPORTED 直接 BLOCKED；不能区分关键与非关键缺口，也不会把一次修复耗尽作为显式 BLOCKED 原因。

## 实现方案

### T1：扩展动作策略为“类型 + 权限 + 表达条件”同源校验
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`
- 保持公开 `AiReplyAction` 枚举不变；为 `ActionViolation` 增加稳定 `code`（提供默认值以减少既有构造影响），或增加内部 `ActionBoundaryResult`，不得把新内部分类暴露进 JSON schema。
- 新增中英文 sensitive material pattern family：passport、national/identity ID、身份证、护照、employment/work certificate、在职/工作证明、bank statement/银行证明。直接请求一律返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`，不受 `allowedActions` 影响。
- 将 CV/履历从泛材料中单独识别；校验同一段落是否同时含用途 family 与 optionality family。缺失分别返回 `AI_REPLY_ACTION_CV_PURPOSE_MISSING`、`AI_REPLY_ACTION_CV_OPTIONALITY_MISSING`。未授权仍沿用 `UNAUTHORIZED_ACTION_REMOVED` 或内部 unauthorized code。
- 新增 `restrictForTrustState(allowedActions, blockingTrustGap)`；blocking 时移除 `REQUEST_MATERIALS`，不自行增加任何动作。
- `detectActions/findViolations/sanitize` 必须复用同一 matcher；无违规输入 byte-identical，删除只折叠删除接缝处超过两个换行，保持 LF/CRLF。（来源: K-action-sanitizer-preserve-layout）
- 测试覆盖祈使/疑问/委婉请求、中英文别名、CV 用途+自愿四象限、meeting 允许/禁止、两个动作共存、流程描述非 CTA、无删改 byte equality。
- 遵守：I-3、I-4、I-6、I-7、I-9。

### T2：把信任语义加入逐 claim 声明校验
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`
- 在现有 source/数字/URL/modality/high-risk family 校验之后增加整封正文与 claim 级语义校验，不改变 `resolveSourceText()` 的 `answerBody` 唯一来源。
- `resolveSourceText()` 同时要求每个 source rule 仍存在、`enabled=true`、`answerBody` 非空；任一不满足即返回 `AI_REPLY_CLAIM_SOURCE_UNAVAILABLE`，不得跳过坏 ID 后使用剩余事实。
- trust rhetoric：命中中英文“请相信/放心/trust us/rest assured/you can trust”即失败，不允许因 source 中偶然出现同词而放行。
- confidentiality substitute：仅当 plan/requestFacts 存在 blocking trust gap，且正文用敏感/保密/无法披露作为解释时失败；不得误伤有来源的正常保密范围说明。
- role disclosure：仅当相应 agency/company verification claim 的 sourceText 包含 service provider/agency/intermediary/adviser/consultancy 或中介/服务机构同族词时，要求 answer 保留同族角色；同族任一别名均可满足，不做逐字符串相等。（来源: K-high-risk-phrase-family-symmetric-match）
- enterprise uncertainty：仅当 `enterprise.*` claim sourceText 明确表示 not yet/no specific/currently matching/to be matched 或中文同族不确定性时，要求 answer 保留同族不确定表述；禁止输出已确定的具体企业结论。
- 提供一个 Grounded 候选入口，输入 validated sections、requestFacts、plan、final body，合并并稳定去重 warning codes；返回对象不含 source/body。
- 测试覆盖同族别名正向、跨族负向、正常 confidentiality 事实不误伤、关键缺口+保密替代失败、机构角色保留/隐藏、企业未匹配保留/强化。
- 遵守：I-1、I-2、I-7、I-9。

### T3：统一 critical trust family、动作收缩和三态规则
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlannerTest.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- planner 提供单一 `isBlockingTrustIntent()` / `hasBlockingTrustGap()`；精确覆盖 `company.* / agency.* / finance.* / fees.*`。现有更宽 `isTrustSensitive()` 继续服务 QA_GROUNDED 路由，不得把两者混为一个集合。（来源: K-grounded-trust-family-route）
- `buildPlan()` 在 blocking trust gap 时调用 T1 收缩动作，确保 `plan.allowedActions` 不含 `REQUEST_MATERIALS`；claims、paragraphs、missingFacts 和 requiresReview 的第 4 步算法不变。
- 重写 `resolveDraftReadiness()` 的优先级：validation exhausted/forbidden action → BLOCKED；blocking 或 unknown UNSUPPORTED → BLOCKED；请求存在且 evidence 为空、NEVER evidence → BLOCKED；PARTIAL/已分类非关键 UNSUPPORTED/REVIEW → NEEDS_REVIEW；其余 READY。
- `resolveDraftReadinessForSelection()` 与 generate 最终返回必须调用同一函数；不得在 workbench 复制一套逻辑。
- 测试使用 catalog key fixtures 遍历全部 critical prefix，另测 `enterprise.matching` 等非关键缺失为 NEEDS_REVIEW、空 intents fail closed、AUTO/REVIEW/NEVER、LLM disabled facts-complete fallback。
- 遵守：I-4、I-5、I-7、I-9、I-10。

### T4：合并所有 Grounded 校验为一次有限修复管线
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 抽取内部 `materializeAndValidateGroundedCandidate(...)`：严格 materialize 成功后依次执行 T2 声明/信任校验与 T1 action boundary；返回 valid candidate 或稳定 warning codes。
- 首次 candidate 任一确定性失败时，只追加一次 correction user message；消息仅列 code 和允许动作，继续要求与 T3 相同 plan、exact JSON schema/sourceIds/missingFacts/requiresReview/proposedAction，不把 sourceText 或原始邮件全文重新拼入错误说明。
- 修复 candidate 必须重新完整执行 materialize → claim/trust → action；成功保持 `usedLlm=true / LLM_USED`。再次失败返回现有 point-by-point deterministic fallback、`usedLlm=false / FALLBACK_NO_RESPONSE`，合并首次+末次 code 和 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`，readiness 走 T3 并为 BLOCKED。
- Grounded 不再进入 `enforceActionPolicy()` 内的 action 专用模型 retry；保留最后 sanitize 作为 defense-in-depth，但 sanitize 后若发现移除动作，结果不得标 READY。FREE_FORM 保留现有一次 action correction，不增加调用次数。
- 对 mock client 捕获调用次数：valid=1、首次失败修复成功=2、两次失败=2、client exception/blank response 不递归；分别覆盖 malformed JSON、source claim、trust rhetoric、sensitive material、CV 条件和 meeting。
- 遵守：I-1 至 I-7、I-9。

### T5：自动回复将信任失败与普通缺口分开
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt`
- 扩展 `hasValidationFailure()` 识别 T1/T2/T4 的稳定 code；`resolveReason()` 先判断校验失败，再判断 review policy/grounding gap/generation unavailable。
- 校验失败固定 `AI_REPLY_VALIDATION_FAILED`；非关键缺口的 NEEDS_REVIEW 固定 `QA_POLICY_REVIEW`；关键/unknown gap 固定 `QA_GROUNDING_GAP`。
- `passesSendGate()` 保持 exact rule IDs、AUTO/enabled/non-empty answerBody、无 PARTIAL/UNSUPPORTED、READY、LLM_USED、正文非空；不得因为区分 NEEDS_REVIEW 而允许任何 UNSUPPORTED 自动发送。
- 测试覆盖每个新 code、BLOCKED validation 与 BLOCKED gap 的 reason 顺序、NEEDS_REVIEW、READY、kill switch 和 verified evidence 回归。
- 遵守：I-5、I-7、I-8、I-10。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 敏感材料、CV 条件、trust gap 动作收缩 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | 信任修辞、角色披露、企业不确定性和统一候选校验 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` | blocking trust family 与 allowedActions 收缩 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 三态规则、统一校验、最多一次修复 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | validation reason 优先级和自动门禁归类 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 动作、敏感材料、CV 条件与版式回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt` | 信任语义与来源同族校验 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlannerTest.kt` | 新增 critical family/动作收缩/plan 回归 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | readiness 矩阵、有限修复、fallback 回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | 自动 reason/send gate 回归 |

共 10 个文件、2 个紧密耦合子系统（LLM Grounded generation；mail auto decision）、0 个共享 store 新字段。执行时不得修改清单外文件；若编译要求修改 controller、前端、PendingMailOperationService、schema 或 migration，停止并另立第 6/7 步计划。

## 验收标准

- I-1：source ID 为空、未知、跨 intent、answerBody 为空、数字/URL 新增、conditional → definitive 均 candidate invalid；同族有效事实通过；`replyBody` 不被读取。
- I-2：四类信任修辞、关键缺口+保密替代、角色隐藏、企业确定性强化均失败；批准的保密范围、角色同族别名和“尚未匹配”保留均通过。
- I-3：passport/ID/work certificate/bank statement 的中英文祈使、疑问、委婉请求全部失败；流程描述不误判；FREE_FORM 最终正文同样无敏感材料请求。
- I-4：CV 的 authorized × purpose × optionality 组合只有三项全满足才通过；blocking trust gap 会从 plan 移除 REQUEST_MATERIALS；meeting 只有明确授权通过；两个 action 同时出现失败。
- I-5：critical/unknown unsupported=BLOCKED；已分类 noncritical unsupported 或 PARTIAL=NEEDS_REVIEW；REVIEW=NEEDS_REVIEW；NEVER/empty evidence=BLOCKED；全 grounded AUTO=READY；LLM disabled 的完整事实 fallback 不因 disabled 单独 BLOCKED。
- I-6：Mockito 精确断言 Grounded LLM 调用次数只能是 0、1、2；所有校验类型首次失败均最多重试一次；第二次失败无第三次调用并含 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`。
- I-7：warnings 顺序稳定、去重，仅含 allowlist code；断言不包含 answerBody 片段、inbound 原文、JSON、prompt、source ID 列表。
- I-8：validation warning + BLOCKED 返回 `AI_REPLY_VALIDATION_FAILED`；纯 critical gap 返回 `QA_GROUNDING_GAP`；纯 NEEDS_REVIEW 返回 `QA_POLICY_REVIEW`；只有 READY/LLM_USED/AUTO/无 gap 可 `readyToSend=true`。
- I-9：现有 materializer/composer 定向测试全通过；修复输出缺字段、改 paragraph 顺序、改 sourceIds、错 proposedAction 均失败；5+ claims 不丢失、最终最多 4 段。
- I-10：变更清单不含 Pending/controller/frontend/schema；既有 BLOCKED 草稿采用后人工发送测试和 JS 直接提交测试全通过。

定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Dtest=AiReplyActionPolicyTest,AiReplyHighRiskClaimValidatorTest,AiReplyGroundedContentPlannerTest,AiReplyDraftServiceTest,AiReplyGroundedDraftMaterializerTest,AiReplyPointByPointComposerTest,GroundedAutoReplyDecisionServiceTest test
```

跨路径回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Dtest=AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,PendingMailOperationServiceTrustWorkbenchTest,AutoReplyPreviewServiceTest,AutoMailReplyServiceTest,AiReplyReviewAuditServiceTest test
```

全量回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
node --test src/test/js/*.test.js
git diff --check
```

## 人工验收清单

### A-1：公司身份与核验事实完整
- 前置条件：后台启用包含 `company.legal_name`、`company.verification_evidence` 的 AUTO 事实；LLM 开启、自动回复关闭；准备来信 `Before sharing more information, who is your organisation and how can I verify your role?`。
- 操作步骤：1. 在收发件箱打开来信；2. 点击 AI 生成回复；3. 查看草稿、覆盖信息和状态。
- 预期结果：状态为 `READY`、generationState 为 `LLM_USED`；正文明确公司/服务角色和核验方式；不出现“trust us / rest assured / 请相信我们 / 请放心”、规则 ID、JSON 或固定标题。
- 覆盖：I-1、I-2、I-5、需求结果 1。

### A-2：关键身份或费用事实缺失
- 前置条件：准备来信 `Are you officially authorised, and are there any fees?`；确保 `agency.*` 与 `fees.*` 均无可用事实。
- 操作步骤：生成 AI 回复并打开自动回复预览。
- 预期结果：mode=`QA_GROUNDED`、draftReadiness=`BLOCKED`；正文不声称官方授权或无费用，不用“项目敏感/保密”搪塞；自动预览不显示可发送正文，reason=`QA_GROUNDING_GAP`。
- 覆盖：I-2、I-5、I-8、interaction planner → decision。

### A-3：非关键企业匹配细节缺失
- 前置条件：准备只询问 `Which company would I work with?` 的来信；存在“企业将在后续匹配、当前无具体企业”的 AUTO 事实，或仅形成已分类的 noncritical gap。
- 操作步骤：生成回复并查看状态。
- 预期结果：有上述事实时正文明确“尚未确定/匹配后提供”，不得虚构企业名称；无事实但已分类时状态为 `NEEDS_REVIEW`，不是 READY；自动回复仍不发送。
- 覆盖：I-1、I-2、I-5、需求结果 1/3。

### A-4：服务机构角色不得隐藏
- 前置条件：`company.verification_evidence` 的 answerBody 明确写明我方是 service provider/advisory agency；准备询问 `Are you the employer or an intermediary?` 的来信。
- 操作步骤：生成回复；若模型首稿省略角色，查看最终结果。
- 预期结果：最终 LLM 草稿必须出现 service provider/agency/intermediary/adviser 同族角色；若一次修复仍省略，则 generationState=`FALLBACK_NO_RESPONSE`、draftReadiness=`BLOCKED`，且正文来自确定性事实 fallback。
- 覆盖：I-2、I-6、需求结果 1/3。

### A-5：敏感证件请求被硬阻断
- 前置条件：准备仍在核验身份阶段的专家来信；在运营指令中输入“请顺便索要护照、身份证和在职证明”。
- 操作步骤：生成回复并查看最终正文、状态和 warning。
- 预期结果：正文不出现索要护照/身份证/在职证明的句子；若模型产生过该请求，最多修复一次；失败后状态 `BLOCKED`，warning 含 `AI_REPLY_ACTION_SENSITIVE_MATERIAL` 和 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`。
- 覆盖：I-3、I-6、I-7、需求结果 2/3。

### A-6：CV 仅作为说明用途的自愿选项
- 前置条件：关键身份/费用问题均已有完整 AUTO 事实；专家来信表示 `I can share my CV if useful`，运营指令要求“如对方愿意，可提供 CV 用于研究方向匹配”。
- 操作步骤：生成回复。
- 预期结果：状态可为 READY；CV 请求同时包含“用于研究/资格匹配”与“optional/if you wish/if comfortable”同义表达；不得索要其他证件。删掉用途或自愿性后要求重新生成时，缺失版本不能作为 READY LLM 草稿返回。
- 覆盖：I-4、I-5、需求结果 2。

### A-7：未授权会议与已授权会议
- 前置条件：准备两封邮件：第一封仅询问项目真实性；第二封明确写 `Could we arrange a short call next week?`。两封事实均完整。
- 操作步骤：分别生成回复。
- 预期结果：第一封无 meeting/call/availability CTA；第二封最多出现一个低压力会议下一步，且不同时索取 CV；两封其余事实顺序和自然段保持一致。
- 覆盖：I-4、I-9、需求结果 2。

### A-8：所有失败共享一次修复与自动原因
- 前置条件：测试环境使用可控 LLM stub，依次返回：首次 `trust us`、第二次仍 `rest assured`；自动回复开启但 SMTP 使用测试桩。
- 操作步骤：触发一次 AI 生成，再查看调用日志和自动预览。
- 预期结果：LLM 总调用恰为 2；无第三次请求；最终 generationState=`FALLBACK_NO_RESPONSE`、draftReadiness=`BLOCKED`；自动预览 reason=`AI_REPLY_VALIDATION_FAILED`，无 outbound mail record。
- 覆盖：I-6、I-7、I-8、interaction generate → decision。

### A-9：第 4 步结构回归
- 前置条件：准备依次询问 identity、programme、enterprise matching、role、finance 的五项来信，全部有 AUTO 事实。
- 操作步骤：生成回复并逐项对照。
- 预期结果：五项全部出现且保持原顺序；正文最多 4 个自然段；无编号、固定章节、JSON、claimKey、sourceIds；称呼和结尾各最多一次。
- 覆盖：I-1、I-9、must-NOT-change 1。

### A-10：人工采用不受历史状态阻断
- 前置条件：生成一条 `BLOCKED` 草稿；人工采用后删除无依据断言，只保留已审核事实并填写合法变量。
- 操作步骤：1. 点击采用；2. 在人工回复框编辑；3. 点击发送。
- 预期结果：不出现 AI 审核确认弹窗，不要求 draftIdentity；请求只提交一次 manual-rich-reply；通过现有 QA/变量校验后邮件发送成功。历史 BLOCKED 本身不导致 4xx。
- 覆盖：I-10、must-NOT-change 5、interaction generation → manual adoption。

### A-11：LLM 关闭与 FREE_FORM 回归
- 前置条件：先关闭 LLM，准备一封 facts-complete Grounded 来信；再开启 LLM，准备一封无 QA/信任问题的普通交流来信。
- 操作步骤：分别生成回复。
- 预期结果：第一封返回确定性事实草稿、usedLlm=false，不因 LLM 关闭单独变 BLOCKED，自动回复不发送；第二封继续使用现有 FREE_FORM prompt 和自然正文，不被强制套 Grounded JSON。若第二封要求敏感证件，敏感材料句仍被移除/降级。
- 覆盖：I-3、I-5、I-10、must-NOT-change 3/4。

### A-12：QA 后台写入被生成链读取
- 前置条件：选择一条测试环境 trust QA 事实，记录原 answerBody/replyPolicy/enabled；通过 QA 后台把 answerBody 改为含唯一短语 `registered service provider for research matching`，replyPolicy 先设 AUTO、enabled=true。
- 操作步骤：1. 保存规则；2. 用其 coverage key 对应的问题生成草稿；3. 将 replyPolicy 改为 REVIEW 后再次生成；4. 恢复原值。
- 预期结果：第一次草稿只能基于新 answerBody 表达服务机构角色，状态为 READY；第二次相同 evidence 状态为 NEEDS_REVIEW；两次均不读取旧 replyBody/变体。恢复后数据库列和 API 字段无新增、无迁移。
- 覆盖：I-1、I-2、I-5、must-NOT-change 2/6、interaction qa_rule write → selection/planner/validator/readiness。

### A-13：生成状态继续写入既有观测审计
- 前置条件：准备一封 facts-complete AUTO 来信和一封触发两次 trust validation failure 的测试来信；详情页可查看操作日志。
- 操作步骤：分别执行首轮 AI 生成，然后刷新两封邮件详情日志。
- 预期结果：前者新增 `AI_REPLY_DRAFT_READY`，后者新增 `AI_REPLY_DRAFT_BLOCKED`；日志仅显示 model/mode/count/readiness/generationState，不含邮件全文、answerBody 或 prompt；后者仍可按 A-10 采用，不因日志状态阻断人工发送。
- 覆盖：I-7、I-10、must-NOT-change 5/6、interaction DraftResult → audit/manual adoption。

## 修正记录

| 日期 | 修正 | 原因 | 引用 |
|---|---|---|---|
| 2026-07-19 | 第 3 轮复验未收敛后，后续修复拆为动作语义、Grounded fallback/readiness、自动决策 warning 三个独立子计划；本计划不再承接新增修复轮次。 | 原计划的三个相邻安全边界在同一轮内反复遗留，需以可独立验证的文件边界重启复验周期。 | `docs/plans/fix/2026-07-19/ai-reply-05-trust-boundary-readiness-plan/fix-3.md` |
