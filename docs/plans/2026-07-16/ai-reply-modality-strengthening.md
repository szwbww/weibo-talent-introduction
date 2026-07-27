# P1：条件来源到确定回答的语气强化拦截

## 需求描述

- 可观察结果：模型引用的 QA 只写 `may/can/could/depends/subject to` 时，`will receive` 等确定承诺不再成为可采用草稿；引用正文明确支持相同确定谓词时仍正常使用 LLM 结果。
- 必须不变：QA 正文与 coverage、金额/URL/期限/高风险声明校验、严格 JSON、固定邮件样式、模型选择、前端与发送审核流程。
- 明确不做：不全局禁止单词 `will`，不靠 prompt 自律替代后端校验，不新增模型调用或数据库字段，不调整 QA seed/migration。

## 关键不变量

### I-1：条件来源不能升级为确定承诺

至少覆盖以下拒绝关系：

| 已审核来源 | 模型回答 | 结果 |
|---|---|---|
| `Selected candidates may receive salary support.` | `You will receive salary support.` | 拒绝 |
| `Travel costs can be covered depending on the project.` | `All travel costs will be covered.` | 拒绝 |
| `IP terms are subject to the enterprise agreement.` | `You will own the intellectual property.` | 拒绝 |
| `Funding may be provided after evaluation.` | `Funding shall be provided.` | 拒绝 |

普通 `will/shall/is entitled` 只在与高风险结果谓词组合时视为 definitive claim；禁止把裸 `will` 加入全局关键词，避免误伤 `We will reply`、`We will share details` 等低风险动作。（K-ai-reply-modality-plain-will）

- 适用：`AiReplyHighRiskClaimValidator.detectsModalityStrengthening()` 的所有 answer/source pair。
- 违反后果：把可能性外发为资金、费用、权利或合同承诺。
- 来源：K-ai-reply-modality-plain-will。

### I-2：来源明确支持时不得误拦截

- 来源明确写 `Selected candidates will receive ...` 时，回答同一 receive family 可通过。
- 来源明确写 `After selection, you will sign a labor contract ...` 时，回答 `a labor contract will be signed` 可通过。
- 来源同时包含条件背景和明确结果时，以回答命中的具体 definitive predicate family 是否在同一引用来源中有明确支持为准，不因来源出现任意一个 `may` 就全局拒绝。
- 适用：receive/pay/provide/cover/ownership/contract predicate families。
- 违反后果：合法审核正文被误拒，系统长期退化为 fallback。
- 来源：原始验收 A-3 与 K-ai-reply-modality-plain-will。

### I-3：引用来源仍是唯一事实 authority

- 只读取 answer 的 `sourceRuleIds` 对应 `replySubject + replyBody`；不得从其他 request、模型常识、专家画像或历史邮件补足确定承诺。
- 任一引用规则不存在或正文为空，继续返回 source unavailable 并判整个 LLM 结果无效。
- 金额、URL、期限、合同、政府、IP 等既有高风险校验继续执行，不能被 modality 结果覆盖或短路。
- 适用：validator 的 source resolution 和全部 claim checks。
- 违反后果：跨 intent 借事实或旧安全规则回归。
- 来源：K-request-facts-not-flat-pool、K-grounded-json-materialize-before-policy。

### I-4：失败作用于整次结构化响应

- 初始 grounded LLM 和 CTA/action retry 必须复用同一 materialize→claim validation 路径。
- 任一 section/answer 强化语气时，`usedLlm=false`、generation state 为 `FALLBACK_NO_RESPONSE`，并使用同一 request-intent matrix 生成确定性 fallback。
- raw JSON、被拒绝回答和内部 warning code 不得进入草稿正文；warning 只通过 `contextWarnings` 返回。（K-grounded-json-materialize-before-policy）
- 适用：`AiReplyDraftService.generate()` 的初始结构化响应与动作重试。
- 违反后果：retry 成为绕过点，或违规句子虽报警仍进入正文。
- 来源：K-grounded-json-materialize-before-policy。

### I-5：严格 JSON 契约不回归

- `requestIndex`/`sourceRuleIds` 仍必须是合法范围内的 JSON 整数并属于矩阵，禁止浮点或超范围整数截断。（K-ai-reply-json-integral-range）
- section 完整性、intent 完整性、source subset、固定标题、编号、问候与签名仍由后端拥有。
- 适用：materializer→validator→composer 全链路。
- 违反后果：模型可伪装 matrix 标识或重新拥有邮件结构。
- 来源：K-ai-reply-json-integral-range、K-grounded-json-materialize-before-policy。

## 现状审计

### 代码路径

1. `AiReplyDraftService` 获取严格 JSON 后由 `AiReplyGroundedDraftMaterializer` 映射为 `ValidatedSection`。
2. `AiReplyHighRiskClaimValidator.validate()` 对每个 answer 读取其 `sourceRuleIds` 的 QA 正文，依次检查数字/URL、modality、高风险声明。
3. 当前 `detectsModalityStrengthening()` 只要来源包含任意 conditional phrase，再看回答是否包含 `guaranteed`、`will definitely`、`unconditionally`、`entitled to`、`absolutely` 或 `certainly will`。
4. 现有名为“source says may but answer says will”的测试实际回答是 `will definitely`，未覆盖普通 `will receive`。

因此 `may receive`→`will receive` 可通过，违反原 Phase 7 人工验收 A-3。

### `qa_rule` store

- Schema/mapping：V1 `reply_subject/reply_body` 为声明来源；V76 `coverage_keys` 只决定 intent 是否有据，不参与语气推断。
- Write paths：`QaRuleManagementService.createRule/updateRule/setRuleEnabled/deleteRule`；Flyway V3/V17/V18/V38/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76 seed/backfill。全部不改。
- Read paths：`AiReplyHighRiskClaimValidator.resolveSourceText()` 按 `sourceRuleIds` 读 subject/body；`AiReplyDraftService`、`AiReplyPointByPointComposer`、`LlmStitchService`、`QaMatchService` 及 mail/tag/template/monitoring 服务也读 rule，均不改。
- Interaction points：materializer 验证 source IDs 属于当前 matrix 后，validator 读取对应 QA；失败结果回到 DraftService fallback。QA write paths 在生成时全部跳过，持久化值保持。

`operator_action_log`、`mail_record`、`mail_record_qa_rule` 不在本计划作用域。模型首轮与动作 retry 都收口在 `AiReplyDraftService.generate()`，不得另建入口专用 validator。

### 历史修复约束

Phase 7 已执行至 fix-3。该轮解决 JSON 整数问题，但未覆盖 plain-will modality。本计划是独立根因修复，不创建 `docs/plans/fix/.../fix-4.md`。

## 实现方案

### T1：把 modality 从强词列表改为谓词 family 比较

约束：I-1、I-2、I-3。文件：`AiReplyHighRiskClaimValidator.kt`。

在 `AiReplyHighRiskClaimValidator` 内定义小范围、可测试的 definitive predicate families，例如：

- receive/pay/provide：`will|shall receive`、`will|shall be paid/provided`；
- cover/reimburse：`will|shall be covered/reimbursed`；
- entitlement/ownership：`is|are entitled to`、`will|shall own`；
- contract：`will|shall sign`、`will|shall be signed`。

实现要求：

1. 对回答识别命中的具体 family；无命中则保留既有强词检测结果。
2. 对每个命中 family 检查引用来源：来源含 conditional marker 且不含相同 family 的明确 definitive 表达时，判定 strengthened。
3. 保留 `guaranteed/will definitely/...` 等既有通用强化词；它们在条件来源下继续拒绝。
4. Regex 必须使用词边界、大小写不敏感并允许有限空白/被动语态；禁止裸匹配 `will`。

### T2：validator 单元测试补齐正反矩阵

约束：I-1、I-2、I-3。文件：`AiReplyHighRiskClaimValidatorTest.kt`。

在 `AiReplyHighRiskClaimValidatorTest` 增加：

- I-1 四个拒绝样例，至少精确覆盖 `may receive`→`will receive`。
- 来源明确 `will receive` 的正例。
- `after selection ... will sign` 的合同正例。
- 低风险 `We will share details` 不误报。
- 条件来源 + `will definitely` 既有负例继续通过测试。
- 数字、URL、高风险 family、缺失来源测试继续通过；一个 answer 同时触发多个 warning 时保持 distinct 顺序契约。

### T3：DraftService 端到端 fallback 测试

约束：I-3、I-4、I-5。文件：`AiReplyDraftServiceTest.kt`。现有 QA write paths 无需调整；validator 继续消费 `QaRuleRepository` 已有 subject/body。

在 `AiReplyDraftServiceTest` 构造受控 QA 与模型 JSON：

- 来源为 `may receive`，模型 answer 为 `will receive`；断言 LLM 结果被整体拒绝、warning 包含 `AI_REPLY_CLAIM_MODALITY_STRENGTHENED`、`usedLlm=false`、`FALLBACK_NO_RESPONSE`，草稿为确定性 fallback 且不包含被拒绝句子。
- 同一 payload 用明确 `will receive` 来源时断言 `LLM_USED`。
- CTA/action retry 返回强化句时也走相同 fallback，证明无旁路。
- 保留 selected model、request/intents、固定 style 和 source IDs 的原协议断言。

## 变更文件清单（3）

| 文件 | 操作 | 目的 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | 修改 | definitive predicate family 与来源强度比较 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt` | 修改 | plain-will 正反矩阵及误报保护 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | 初始生成和 retry 的端到端 fallback |

文件数 3，单一 LLM 子系统。禁止修改 QA seed/migration 来迎合测试；禁止改 prompt 要求模型“自己不要写 will”替代服务端验证。

## 验收标准

- I-1 表中四个条件→确定样例均返回 `valid=false` 和 `AI_REPLY_CLAIM_MODALITY_STRENGTHENED`。
- I-2 两个明确来源正例返回 valid，现有合同 `will be signed` 语义不被误杀。
- 低风险 `We will share the enterprise profile once confirmed.` 在没有其他违规声明时可通过。
- I-3：只有 answer 的 source IDs 被读取；引用缺失/空正文继续 fail closed。
- I-4：plain-will 违规的初始生成和 retry 都废弃整次 LLM 输出，返回确定性 fallback；违规文本不出现在 `draftText`/`renderedDraftText`。
- I-5：金额、URL、integral/range、schema completeness、action sanitizer 既有测试全部通过。
- 执行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 人工验收清单

### A-1：条件来源被拦截

- 前置：测试环境 QA 正文为 `Selected candidates may receive salary support.`；模型 stub 对该 intent 返回 `You will receive salary support.`。
- 操作：1）在 AI 训练模拟生成一次；2）在收发邮件首轮再生成一次；3）查看两处 generation state、warning 和正文。
- 预期：两处均显示模型无有效响应/结构化 fallback；warning 可见；正文不包含 `will receive salary support`；模型选择仍显示请求模型。
- 覆盖：I-1、I-3、I-4；可观察结果与跨入口 interaction。

### A-2：明确来源允许

- 前置：将测试 QA 正文改为 `Selected candidates will receive salary support.`，其他条件不变。
- 操作：1）重复训练模拟和 mailbox 生成；2）查看 generation state；3）验收后恢复 QA 原值。
- 预期：受控合法 JSON 使用 LLM 结果，generation state 为 `LLM_USED`，不出现 modality warning。
- 覆盖：I-2、I-3；合法来源不误拦截。

### A-3：低风险 will 不误伤

- 前置：模型只输出 `We will share the matched enterprise profile once confirmed.`，引用正文明确支持分享企业资料。
- 操作：1）生成回复；2）查看 feedback；3）检查草稿句子。
- 预期：不因单词 `will` 触发 modality fallback；其他覆盖与审核状态按事实矩阵决定。
- 覆盖：I-1、I-2；必须不变项。

### A-4：既有金额与 URL 校验不回归

- 前置：已审核来源不含金额和 URL；模型 stub 返回合法结构，但 answer 新增 `RMB 12 million` 和 `https://example.invalid`。
- 操作：1）生成一次训练模拟；2）检查 warning、generation state 和正文。
- 预期：结果仍为 `FALLBACK_NO_RESPONSE`；正文不包含该金额或 URL；至少出现既有 hallucinated fact warning，modality 改动不短路该校验。
- 覆盖：I-3、I-4、I-5；必须不变项。
