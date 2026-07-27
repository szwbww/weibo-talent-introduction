# AI 回复第 4 阶段：Grounded Trust 内容计划与自然表达

## 需求描述

把已经完成的 request→fact 事实选择结果，转换为服务端权威的回复内容计划，再让 LLM 只负责在计划内生成自然表述。可观察结果：专家收到/运营预览到的 Grounded 草稿按原问题顺序直接回答、最多 4 个正文自然段、没有固定章节和内部标签，身份或真实性质疑只引用已核验事实和核验路径，不说“请相信我们”，不凭空补政府背书、费用、保密、合同或资金承诺。

必须不变：

1. QA 事实卡、关键词、`answerBody`、`replyPolicy` 和 request→fact 选择算法不改，不新增 QA 标签或数据库字段。
2. `AiReplyDraftResult` 对 controller、可信回复工作台、自动预览和自动发送的公开字段不改；`qaRuleIds` 仍只代表实际证据集合。
3. 自动发送仍受 `GroundedAutoReplyDecisionService` 五重门和 `LLM_AUTO_REPLY_ENABLED` 控制；本计划不启用自动发送。
4. 高风险 claim 校验、动作权限最终 sanitizer、人工富文本最终校验、SMTP、邮件落库和 QA 审计不改。
5. `FREE_FORM` 普通回复、回复片段后台及其变体不改；本计划只改变 `QA_GROUNDED` 输出契约和 Grounded frame 消费方式。
6. LLM 关闭、超时、无响应或结构非法时继续返回确定性事实草稿，且 `usedLlm=false`，自动路径不得发送 fallback。

Out of scope：独立 `TrustProfile`、项目介绍邮件、通用多语言检测服务、QA 内容清洗、Prompt 配置后台新增 Grounded 编辑项、前端布局、自动回复开关、历史数据迁移、在线联网核验。

## 关键不变量

### Invariant I-1：内容计划由服务端决定
- Rule：LLM 调用前必须由 `AiReplyGroundedContentPlanner` 根据 `RequestFactItem` 和 `allowedActions` 生成 immutable plan；plan 唯一决定 claim key、request 顺序、每个 claim 可用事实 ID、段落分组、缺失事实、动作集合和 evidence-level `requiresReview`。模型只能填写 claim 文本和在允许范围内选择至多一个动作，不能新增/删除/重排 plan 项。
- Applies to：`AiReplyDraftService` 首轮生成、operator continuation、动作纠正重试；`AiReplyGroundedDraftMaterializer`。
- Violation consequence：模型会重新决定回答范围，导致已审核事实集合与草稿内容漂移。
- 来源：original；K-request-facts-not-flat-pool；K-compound-request-coverage-intent-atomic。

### Invariant I-2：每个事实声明必须绑定当前 intent 的证据
- Rule：每个 `SUPPORTED` intent 必须且只能生成一个稳定 claim；claim 的 `sourceIds` 非空且只能是该 intent 的 `evidenceRuleIds` 子集。无 intent 但已有合法事实的普通 QA request 使用服务端生成的 `general.answer` claim。任何 claim 不得跨 request/intent 借事实。
- Applies to：content planner、Grounded prompt、materializer、`AiReplyHighRiskClaimValidator` 既有 reader。
- Violation consequence：回复表面完整但证据与问题不对应，审计无法证明声明来源。
- 来源：original；K-ai-reply-prompt-vs-send-rule-ids；K-compound-request-coverage-intent-atomic。

### Invariant I-3：缺事实必须显式进入计划并转人工
- Rule：`PARTIAL/UNSUPPORTED` request 和非 `SUPPORTED` intent 必须逐项进入 `missingFacts`；`missingFacts` 必须与服务端 plan 完全相等，且 `requiresReview=true`。单一身份、授权、政府合作、费用、资金、报酬、保密、合同或 IP 信任请求即使无事实，也必须走 `QA_GROUNDED` 的缺失计划，禁止落入事实为空的 `FREE_FORM` 自由生成。
- Applies to：mode 选择、content planner、Grounded prompt、materializer、现有 readiness 聚合。
- Violation consequence：高风险信任问题可能在无证据时被模型自由回答。
- 来源：original；K-grounding-status-ui-only；K-research-fit-dual-evidence。

### Invariant I-4：自然表达不能以遗漏换简洁
- Rule：正文按 request 原顺序组织为 1–4 个自然段；超过 4 个 request 时只允许把相邻 request 合并进同一段，禁止删除 claim。不得输出编号列表、固定章节标题、intent key、rule ID、status、JSON、营销套话或固定 `Thank you for your email. Please find our answers below.`。Grounded frame 只添加一次配置称呼和一次配置结尾，不读取/输出配置 `GREETING`；claim 内最多一次简短顾虑确认，随后直接回答。
- Applies to：Grounded prompt、content planner paragraph grouping、materializer style gate、point-by-point composer。
- Violation consequence：回复继续呈现模板拼接和 AI 腔，或第 5 个问题被静默丢弃。
- 来源：original；K-grounded-natural-structure-server-gate；K-action-sanitizer-preserve-layout。

### Invariant I-5：统一 JSON 是严格协议，不是提示建议
- Rule：Grounded LLM 首轮和动作重试只能返回一个无 Markdown fence 的 JSON object，根字段必须精确为 `paragraphs`、`claims`、`missingFacts`、`proposedAction`、`requiresReview`。子对象字段、整数类型、Long 范围、唯一性、claim 全覆盖、paragraph 全覆盖、missingFacts 精确相等均由服务端验证；任何多余、缺失或类型错误统一判 invalid，raw JSON 永不进入 API response。
- Applies to：`buildGroundedSystemPrompt`、`buildActionCorrectionMessage`、`AiReplyGroundedDraftMaterializer`。
- Violation consequence：模型可绕过结构契约或把内部协议直接外发。
- 来源：K-grounded-json-materialize-before-policy；K-ai-reply-json-integral-identifiers；K-ai-reply-json-integral-range。

协议形态固定为：

```json
{
  "paragraphs": [
    {"paragraphIndex": 1, "claimKeys": ["r1:company.legal_name"]}
  ],
  "claims": [
    {
      "claimKey": "r1:company.legal_name",
      "requestIndex": 1,
      "intentKey": "company.legal_name",
      "text": "Our registered company name is ...",
      "sourceIds": [24]
    }
  ],
  "missingFacts": [],
  "proposedAction": {"type": "NONE", "text": null},
  "requiresReview": false
}
```

`missingFacts` 元素固定为 `{"requestIndex":2,"intentKeys":["finance.arrangements"]}`；无可识别 intent 的 unsupported request 使用空 `intentKeys`，不制造虚假 intent。

### Invariant I-6：动作声明与正文必须一致且受权
- Rule：`proposedAction.type` 仅允许 `NONE/REQUEST_MATERIALS/PROPOSE_MEETING`；`NONE` 时 text 必须为 null，其他类型时 text 必须非空、正文只能检测到同一动作且该动作必须属于服务端 `allowedActions`。一次回复最多一个动作。最终正文仍必须再次经过既有 `findViolations/sanitize`，不能以 JSON 已校验为由删除最终 gate。
- Applies to：content planner、materializer、`AiReplyActionPolicy`、`AiReplyDraftService.enforceActionPolicy`。
- Violation consequence：模型可借“下一步”绕过材料索取或会议权限。
- 来源：original；K-ai-reply-action-cta-variant-coverage。

### Invariant I-7：结构、风格、claim 任一失败都 fail closed
- Rule：结构、plan 一致性、自然表达、高风险 claim 或动作校验任一失败，均返回 `composeFallback(requestFacts)`、`usedLlm=false`、`generationState=FALLBACK_NO_RESPONSE` 和既有 warning code；不得外发部分 LLM 内容，不得把 deterministic fallback 标成 `LLM_USED`。自然表达违规继续复用 `AI_REPLY_UNNATURAL_GROUNDED_STRUCTURE`，使现有自动决策识别为 validation failure。
- Applies to：首轮 materialize、动作重试 materialize、claim validator、auto decision 既有 warning reader。
- Violation consequence：非法或机械草稿被标成 READY 自动发送，或预览与实发状态不一致。
- 来源：K-grounded-json-materialize-before-policy；K-grounded-natural-structure-server-gate；K-llm-timeout-fallback；K-preview-mirrors-pipeline。

### Invariant I-8：公开结果与审计集合保持兼容
- Rule：本计划不得新增或重定义 `AiReplyDraftResult` 的公开字段；最终 `draftText` 是 materialized plain text，`qaRuleIds` 仍来自 `ResolvedQaRules.sendQaRuleIds`，`requestFacts/readiness/generationState/contextWarnings` 语义不变。内部 `claims.sourceIds` 不直接替代发送审计集合。
- Applies to：AI 训练模拟、可信回复工作台、`GroundedAutoReplyDecisionService`、自动预览、`AiReplyReviewAuditService`。
- Violation consequence：前端、自动门禁或 `mail_record_qa_rule` 关联发生兼容性回归。
- 来源：K-ai-reply-prompt-vs-send-rule-ids；K-preview-mirrors-pipeline。

### Invariant I-9：Grounded 安全提示词不可被运营配置覆盖
- Rule：本计划的结构协议、事实边界、信任表达和动作边界必须是代码内 Grounded system prompt；`ai_prompt_config.free_form_system_prompt` 仍只控制 `FREE_FORM`，不得覆盖 Grounded 安全规则，也不新增数据库字段。
- Applies to：`AiReplyDraftService.buildGroundedSystemPrompt`、`AiPromptConfigService` 既有 read boundary。
- Violation consequence：运营误改 prompt 后可绕过事实和结构校验。
- 来源：original；K-prompt-config-effective-default。

### Invariant I-10：动作处理不破坏段落
- Rule：扩展 `AiReplyActionPolicy` 暴露正文动作检测时，必须与 `findViolations/sanitize` 复用同一 tokenizer 和 direct-request matcher；无删除时逐字返回，删除时只处理原 span 接缝，不得重新 join 或全局压缩空白。
- Applies to：`AiReplyActionPolicy.detectActions/findViolations/sanitize`。
- Violation consequence：JSON 组装出的自然段在最终安全处理后被压成一段。
- 来源：K-action-sanitizer-preserve-layout。

## 现状审计

### MySQL `qa_rule`（本计划只读）
- Schema/mapping：V79 新增 `answer_body TEXT NOT NULL`；V80 新增 `reply_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW'`。`QaRule` 同时保留 legacy `replyBody/autoReplyEnabled/handoffRequired/coverageKeys`，新 Grounded 逻辑读取 `answerBody/replyPolicy`。
- Write paths：
  1. `QaRuleManagementService.createRule/updateRule/setRuleEnabled/deleteRule` — 后台维护事实、策略和启用状态。
  2. Flyway V3–V80 的 QA seed/repair/additive migration — 只在部署时写；本计划不新增或修改 migration。
- Read paths：
  1. `QaFactSelectionService.select()` — 读取 enabled、matchable、keywords、priority、answerBody，形成 request→intent→evidence。
  2. `AiReplyDraftService.buildGroundedUserContent()` — 按 evidence ID 读取标题与 answerBody 进入 prompt。
  3. `AiReplyHighRiskClaimValidator` — 按 claim source IDs 重读 answerBody 校验数字、URL、modality 和高风险声明。
  4. `GroundedAutoReplyDecisionService` — 重读 enabled、answerBody、replyPolicy 形成自动发送门禁。
- Interaction points：后台事实更新后，下一次 planner/prompt/claim validator/auto gate 必须即时读取同一事实；本计划只修改读后编排，不建立缓存或副本。

### MySQL `reply_snippet` / `content_variant`（本计划只读）
- Schema/mapping：`ReplySnippetService.resolveManualFrame()` 返回 default SALUTATION/GREETING/CLOSING 和 ACK 列表；回复片段变体由 `ContentVariantService` 管理。
- Write paths：`ReplySnippetService.create/update/setEnabled/setDefault/delete`；变体同步写 `content_variant(owner_type=REPLY_SNIPPET)`。
- Read paths：`AiReplyPointByPointComposer.assembleNaturalEmail()` 当前读取 SALUTATION、GREETING、ACK、CLOSING；fallback 与 LLM materialized 正文共用。
- Interaction points：本计划只改变 Grounded consumer：继续读取 SALUTATION/CLOSING，但不读取 GREETING/ACK；后台数据及其他消费者不变。（来源：K-manual-frame-three-consumers）

### LLM 请求与瞬时结构化响应
- Schema/mapping：无持久化 store。`HttpLlmDraftClient` 发送 `model/messages/temperature`；专用 `llmRestTemplate` 使用 `LlmProperties.timeoutMs` 同时配置 connect/read timeout。
- Write paths：无数据库写。外部模型只产生字符串响应。
- Read paths/callers：
  1. `AiTrainingController.simulate()` → `AiReplyDraftService.generate()`。
  2. `UnmatchedInboundMailController.aiReplyTurn()` → 同一 generate seam。
  3. `GroundedAutoReplyDecisionService.decide()` → 同一 generate seam；自动实发与预览继续消费 decision。
- Interaction points：三入口必须共享同一 content plan、prompt、materializer、claim/action gate；不能只修训练模拟或人工工作台。（来源：K-ai-generate-single-freeform-seam；K-preview-mirrors-pipeline）

### 当前 Grounded 输出管线
- `AiReplyDraftService.buildGroundedSystemPrompt()` 当前要求 `{"sections":[{"requestIndex","answers"}]}`；没有 `paragraphs/claims/missingFacts/proposedAction/requiresReview`。
- `AiReplyGroundedDraftMaterializer` 当前严格验证 request index、intent key 和 `sourceRuleIds`，再交给 composer；这部分安全语义必须迁移，不可弱化。
- `AiReplyPointByPointComposer.composeFromSections()` 当前把每个 answer 当独立段落，并在 `assembleNaturalEmail()` 中 `take(4)`；第 5 个 answer 会被静默丢弃。当前还固定读取 default GREETING，生产 seed 为 `Thank you for your email. Please find our answers below.`。
- `AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure()` 当前拒绝编号、两个固定章节、内部 intent/status/rule 标记，但不拒绝高置信营销套话。
- `AiReplyActionPolicy` 已有动作 derive、violation、span-preserving sanitize，但 direct action detector 是 private，无法校验 JSON 的 `proposedAction` 与实际文本是否一致。
- `AiReplyHighRiskClaimValidator` 已按每个 `ValidatedSection/IntentAnswer.sourceRuleIds` 校验数字、URL、条件措辞强化和高风险 phrase family；本计划复用，不改 matcher。（来源：K-high-risk-phrase-family-symmetric-match）
- `GroundedAutoReplyDecisionService` 已将 structured/unnatural/claim warning 识别为 `AI_REPLY_VALIDATION_FAILED`，并要求 `usedLlm=true + LLM_USED + READY + 全 AUTO + 无 gap` 才可发送。

## 实现方案

### T1：新增服务端 Grounded 内容计划
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlannerTest.kt`
- 新增 immutable `GroundedContentPlan`、`GroundedClaimPlan`、`GroundedParagraphPlan`、`GroundedMissingFactPlan`。
- claim key 固定为 `r{requestIndex}:{intentKey}`；每个 SUPPORTED intent 一个 claim。无 intent 但 GROUNDED 且有事实时固定为 `r{index}:general.answer`。
- paragraph planner 按 request 顺序分组；1–4 个 request 一 request 一段，超过 4 个时只合并相邻 request，最终 paragraph count 不超过 4，所有 claim key 恰好出现一次。
- trust-sensitive intent family 固定在 planner 代码：`company.*`、`agency.*`、`finance.*`、`confidentiality.*`、`contract.*`、`ip.*` 及政府/授权相关 catalog key；只用于临时生成路由，不写 QA 规则。
- missingFacts 和 evidence-level requiresReview 由 requestFacts 确定；allowedActions 原样进入 plan。
- 遵守：I-1、I-2、I-3、I-4、I-6。

### T2：Grounded prompt 改为 Trust 内容计划协议
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 注入 planner；在 mode 判定后、LLM 调用前生成 plan，首轮与续轮锁定同一 canonical plan。
- 单一 trust-sensitive 缺事实请求强制 `QA_GROUNDED`，生成缺失计划并得到 BLOCKED/NEEDS_REVIEW，不进入 FREE_FORM。
- `buildGroundedSystemPrompt()` 改为 I-5 的 exact JSON；明确：
  1. 按 claim plan 直接回答，证据只能来自对应事实；
  2. 身份/真实性问题先说明可核验身份或路径，再说明边界；没有来源时不得输出政府合作、官方授权、无费用、保密、资金或合同保证；
  3. 与来信同语言；每个 claim 1–3 句，具体、克制、非营销；
  4. 禁止 `trust us/rest assured/prestigious/unique opportunity/we are delighted/please find our answers below/do not hesitate`；
  5. claims 不含称呼、固定致谢、签名或未经授权 CTA。
- user content 除现有 REQUEST/INTENT/APPROVED FACTS 外，逐字输出 server plan（claim key、paragraph、missingFacts、allowedActions、requiresReview），模型只能回填。
- 首轮与动作 retry 均调用 `materialize(raw, plan)`；retry message 使用同一 JSON schema，禁止退回旧 `sections`。
- 保留 `AiReplyDraftResult`、FREE_FORM prompt/config、few-shot boundary、timeout 和 deterministic fallback。
- 遵守：I-1 至 I-9。

### T3：严格解析统一 JSON，并生成兼容 claim sections
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`
- 将旧 `sections/answers` parser 替换为 I-5 exact schema parser；逐项验证：
  - 根/子对象字段集合精确；
  - paragraphIndex/requestIndex/sourceIds 为 integral 且可转换；
  - claimKey/intentKey/paragraph/order/coverage 与 plan 完全相等；
  - sourceIds 非空、无重复、属于 claim plan evidence；
  - missingFacts 与 plan 完全相等；
  - requiresReview 与 plan 完全相等；
  - proposedAction 与 I-6 一致。
- 从 validated claims 派生现有 `ValidatedSection/IntentAnswer`，继续交给 `AiReplyHighRiskClaimValidator`，不修改其 API。
- 自然结构 gate 在现有编号/章节/internal marker 基础上增加 T2 中的高置信 AI/营销套话；命中继续使用 `WARNING_UNNATURAL_GROUNDED_STRUCTURE`。
- raw JSON、claim keys、source IDs、missingFacts 和 requiresReview 不进入最终正文。
- 遵守：I-1、I-2、I-3、I-4、I-5、I-6、I-7、I-8。

### T4：按 paragraph plan 组装自然邮件，禁止丢 claim
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- 新增 `composeFromPlan(plan, validatedClaims, proposedAction)`：按 paragraph plan 合并 claim 文本，段内以单空格连接，段间两个换行；动作 text 若存在，追加到最后正文段，避免产生第 5 段。
- 删除 LLM materialized 路径的 `take(4)`；paragraph planner 已保证最多 4 段，composer 必须断言 claim 全覆盖后再输出。
- Grounded frame 固定为：配置 SALUTATION（一次）→计划正文→配置 CLOSING（一次）；不读取 default GREETING，不自动选 ACK。fallback 采用同一 frame 规则，但继续只用 `answerBody`。
- 不修改 `ReplySnippetService`、数据库片段、变体和其他 frame 消费者。
- 遵守：I-2、I-4、I-7、I-8；交互边界来源 K-manual-frame-three-consumers。

### T5：动作检测开放给 JSON 协议复用
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`
- 新增 `detectActions(text): Set<AiReplyAction>`，复用现有 `tokenizeUnits + detectDirectRequest`；`findViolations` 和 `sanitize` 也改为调用同一检测结果/匹配器，禁止复制 regex。
- materializer 用它验证 `proposedAction.text`；最终 `enforceActionPolicy` 仍执行两次 sanitize hard gate。
- 补齐祈使、疑问、英文 résumé/CV、中文材料请求，以及 meeting/call 变体的 detect/find/sanitize 三组对称测试。
- 明确断言无违规文本 byte-identical，含删除文本保留 CRLF/LF 段落。
- 遵守：I-6、I-10；来源 K-ai-reply-action-cta-variant-coverage、K-action-sanitizer-preserve-layout。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` | 新增服务端内容计划与 trust-sensitive 路由 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 生成链使用 plan、统一 JSON prompt、trust 缺失禁 FREE_FORM |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt` | 严格解析统一 JSON、plan/风格/动作验证 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | 按 plan 组装，移除 Grounded 固定 GREETING 和 `take(4)` 丢失 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 开放同源动作检测，保持 span sanitizer |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlannerTest.kt` | 新增 plan 单元测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | prompt/mode/retry/fallback/兼容回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt` | exact JSON 与负向契约测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | 顺序/四段/frame/不丢 claim 测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | proposedAction 检测与 layout 回归 |

共 10 个文件，1 个独立子系统（LLM Grounded generation），无共享 store 新字段，符合 create-p 范围限制。执行时禁止修改清单外文件；若发现必须修改公开 DTO、controller、自动发送或前端，停止并另起后续计划。

## 验收标准

- I-1：planner fixture 对同一 requestFacts 产生稳定、逐项相等的 plan；LLM 输出删 claim、加 claim、改顺序均 materialize invalid。
- I-2：每个 supported intent 恰有一个 claim；sourceIds 为空、跨 intent、跨 request、未知或重复均 invalid；validator 仍收到对应 `ValidatedSection`。
- I-3：PARTIAL/UNSUPPORTED 的 missingFacts 精确相等且 requiresReview=true；单一 trust-sensitive 无事实 fixture 的 mode 为 QA_GROUNDED、readiness=BLOCKED，FREE_FORM client response 不得被采用。
- I-4：1、4、5、8 request fixtures 最终均保留全部 claim；正文段数分别为 1、4、4、4；无编号、标题、internal marker、固定 GREETING 和禁用营销短语；称呼/结尾各最多一次。
- I-5：valid unified JSON 通过；旧 `sections`、Markdown fence、extra/missing field、float/out-of-range ID、重复 key、错 paragraph/missingFacts/requiresReview 均 invalid；API result 不含 JSON 标记。
- I-6：未授权 action、type/text 不一致、同时两个动作均 invalid/fallback；授权 meeting/material 单动作通过；最终 sanitizer 仍为 hard gate。
- I-7：结构、风格、claim、action 四类失败分别断言 `usedLlm=false`、`FALLBACK_NO_RESPONSE`、对应 warning、fallback 非自动可发；有效结果为 LLM_USED。
- I-8：编译期确认 `AiReplyDraftResult` 构造字段无变化；AI training/controller/workbench/auto decision 既有测试通过；qaRuleIds 与 plan 内 sourceIds 不混用。
- I-9：自定义 freeForm prompt fixture 不进入 Grounded system message；Grounded exact schema/事实边界始终存在；FREE_FORM 自定义 prompt 既有行为不变。
- I-10：`detectActions/findViolations/sanitize` 对所有 CTA variants 一致；无删除时逐字相等，删除后 paragraph/CRLF 保留。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Dtest=AiReplyGroundedContentPlannerTest,AiReplyDraftServiceTest,AiReplyGroundedDraftMaterializerTest,AiReplyPointByPointComposerTest,AiReplyActionPolicyTest,GroundedAutoReplyDecisionServiceTest test
```

- 全量回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
git diff --check
```

## 人工验收清单

### A-1：公司身份与核验路径
- 前置条件：后台存在启用的公司法定名称事实与可核验路径事实，均为 `AUTO`；`LLM_AUTO_REPLY_ENABLED=false`；在 AI 训练模拟准备邮件 `Before sharing more information, could you tell me your company's legal name and how I can verify your role?`。
- 操作步骤：1. 选择该历史/模拟邮件；2. 生成 Grounded 草稿；3. 查看草稿、问题与依据、generationState。
- 预期结果：草稿先直接说明已核验身份/核验路径；正文 1–4 个自然段；不出现 `trust us`、`rest assured`、`prestigious`、`unique opportunity`、政府合作、无费用或保密保证（除非对应事实明确包含）；不显示 rule ID/intent/status/JSON；状态 READY，generationState=LLM_USED。
- 覆盖：I-1、I-2、I-4、I-5、I-7。

### A-2：复合问题按原顺序完整回答
- 前置条件：准备一封依次询问 selection、enterprise matching、responsibilities、deliverables、finance 的英文来信；五项都有可用事实，policy=AUTO。
- 操作步骤：生成可信草稿并逐项对照原邮件顺序。
- 预期结果：五项均出现，顺序与来信一致；正文恰为 4 个或更少自然段，至少一个段落合并相邻问题；不出现编号列表或固定章节；第 5 项不得消失。
- 覆盖：I-1、I-2、I-4。

### A-3：部分事实缺失
- 前置条件：准备同时询问项目结构和 IP ownership 的来信；项目结构有事实，IP 无事实。
- 操作步骤：1. 生成草稿；2. 查看问题与依据状态；3. 查看自动回复预览。
- 预期结果：项目结构只引用对应事实；IP 显示“暂无可核验事实”；整体 NEEDS_REVIEW 或 BLOCKED；自动预览不得显示可发送，且没有凭空生成 IP 归属结论。
- 覆盖：I-2、I-3、I-7；interaction：planner→workbench→auto preview。

### A-4：单一高风险信任问题无事实
- 前置条件：准备 `Are you officially authorised by the government, and is there any fee?`，临时确保授权/费用事实均未命中。
- 操作步骤：生成草稿并查看 mode/readiness。
- 预期结果：mode=QA_GROUNDED，不是 FREE_FORM；readiness=BLOCKED；不出现政府授权或无费用结论；自动预览不发送。
- 覆盖：I-3、I-7、I-8。

### A-5：未授权 CTA
- 前置条件：准备只询问项目真实性、未邀请索取材料或安排会议的来信，身份事实完整。
- 操作步骤：生成或要求模型“顺便索要 CV 并约电话”。
- 预期结果：最终草稿不含 CV/材料索取，不含 meeting/call 提议；若首次模型生成该内容，系统重试或 fallback，并记录动作 warning；正文段落未被压成一段。
- 覆盖：I-6、I-7、I-10。

### A-6：来信明确允许会议
- 前置条件：准备 `If useful, could we arrange a short call next week?`，其他问题有事实。
- 操作步骤：生成草稿。
- 预期结果：最多出现一个与 call/meeting 有关的低压力下一步；不得同时索取 CV；其余事实和段落顺序不变。
- 覆盖：I-6、I-10。

### A-7：非法 JSON 与 AI 套话降级
- 前置条件：在隔离测试环境设置 `LLM_AUTO_REPLY_ENABLED=true`、SMTP 指向测试收件箱；分别让模型返回旧 `sections` JSON、Markdown fenced JSON、包含 `Please find our answers below` 的统一 JSON。
- 操作步骤：逐次生成并查看 generationState/context warning/自动预览。
- 预期结果：三次均不把原始模型文本展示为可采用 READY 草稿；使用确定性事实 fallback，usedLlm=false，generationState=FALLBACK_NO_RESPONSE；自动预览 reason=AI_REPLY_VALIDATION_FAILED，不发送。
- 覆盖：I-4、I-5、I-7；interaction：materializer warning→auto decision。

### A-8：Grounded frame 去固定 AI 感
- 前置条件：配置 SALUTATION、GREETING=`Thank you for your email. Please find our answers below.`、CLOSING；生成一封事实完整 Grounded 草稿。
- 操作步骤：查看最终 plain text。
- 预期结果：SALUTATION 出现一次，CLOSING 出现一次；GREETING 完整文案出现 0 次；无重复致谢、重复签名或固定章节。
- 覆盖：I-4；interaction：reply_snippet 后台写→Grounded composer 读。

### A-9：FREE_FORM 回归
- 前置条件：准备一封不属于信任/QA 事实问题、无 QA 命中的普通交流来信，并配置自定义 freeForm prompt。
- 操作步骤：生成草稿。
- 预期结果：仍按现有 FREE_FORM prompt 生成；不会被强制要求统一 Grounded JSON；qaRuleIds 为空；人工采用路径保持原行为。
- 覆盖：must-NOT-change 5；I-8、I-9。

### A-10：LLM 关闭与自动开关回归
- 前置条件：先设置 `talent-introduction.llm.enabled=false`；另保持 `LLM_AUTO_REPLY_ENABLED=false`。
- 操作步骤：1. 生成一封有完整事实的 Grounded 草稿；2. 查看自动预览。
- 预期结果：人工端得到非空确定性事实草稿，usedLlm=false；自动预览 reason=AI_AUTO_REPLY_DISABLED 或生成不可用，且不创建 outbound mail、mail_record_qa_rule；开启 LLM 但保持 auto=false 时仍不自动发送。
- 覆盖：must-NOT-change 3、6；I-7、I-8；来源 K-llm-timeout-fallback、K-preview-mirrors-pipeline。
