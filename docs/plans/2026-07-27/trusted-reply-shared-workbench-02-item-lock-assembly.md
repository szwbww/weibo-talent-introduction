# 可信回复工作台 02：逐项 AI、版本锁定与无改写整合

日期：2026-07-27  
状态：待批准、待执行  
前置：[01 共享运行时与 API](./trusted-reply-shared-workbench-01-shared-runtime-api.md) 已通过  
后续：[03 训练评估留存](./trusted-reply-shared-workbench-03-training-evaluation-audit.md)

## 需求描述

在公共工作台 API 上增加逐项处理：每个 request 有独立处理方式、独立 AI 指令、不可变版本和显式锁定；所有 request 决策锁定后，由服务端无全局 LLM 改写地整合为最终 raw/rendered 草稿。

必须不改变：

1. `AiReplyDraftService.generate()` 仍是完整生成、训练、live 和自动 decision 的既有安全 seam；默认调用行为与 DTO 消费者兼容。
2. PARTIAL 只回答有依据部分；UNSUPPORTED 不得生成事实答案或内部缺口说明到邮件正文。
3. fallback/LLM 失败参考内容不可伪装成可锁定的 AI 版本。
4. QA 的 `answerBody` 是事实唯一正文来源；`displayName/sectionTitle/replySubject` 只作标签。
5. 最终人工发送链路和 preflight 不变，工作台整合结果不是发送许可。

本计划不包含：前端 UI、训练评估写库、工作台 session 持久化、多人锁、旧 API 删除、自动把评估回灌 QA。

## 关键不变量

### Invariant I-1: requestKey 是服务端稳定身份
- Rule: 每个 request 的 `requestKey` 必须由当前 `sourceVersion`、原始 `index`、规范化 `requestText`、有序 intent keys 做 SHA-256 后截取固定 32 个十六进制字符；同一 sourceVersion 重算相同，任一组成变化必须变化。后续 API 只以 `requestKey` 选项，禁止仅按数组位置或浏览器 requestText 选项。
- Applies to: bootstrap response、完整生成 item mapping、单项调整、整合。
- Violation consequence: 异步结果或锁定版本被套到另一个问题。
- 来源: K-request-facts-not-flat-pool

### Invariant I-2: 处理方式由 grounding 状态限制
- Rule: 新枚举及允许矩阵固定为：`GROUNDED → ANSWER_WITH_EVIDENCE|OMIT`；`PARTIAL → ANSWER_SUPPORTED_PART|ACKNOWLEDGE_PENDING|OMIT`；`UNSUPPORTED → ACKNOWLEDGE_PENDING|OMIT`。服务端返回允许值并在每次调整/整合时重验；非法组合返回 422。
- Applies to: bootstrap、`adjustItem`、`assemble`。
- Violation consequence: 无依据 request 被生成肯定事实，或 partial 暗示完整回答。
- 来源: K-grounding-status-ui-only

### Invariant I-3: 单项指令只影响表达
- Rule: `operatorInstruction` trim 后最多 500 字符，只能影响目标 request 的语气、长度、语言和结构；不得作为事实、action、source ID 或缺失依据的替代。prompt 明示“只处理 requestKey”；服务端仍以当前 canonical intent/source plan 校验所有输出。
- Applies to: `AiReplyDraftService.generateItem`、公共 item-adjust request。
- Violation consequence: 操作人指令把未审核内容注入成事实。
- 来源: K-ai-reply-prompt-vs-send-rule-ids, K-explicit-fact-selection-must-match-request

### Invariant I-4: 版本属于单个 request 且不可变
- Rule: 成功调整只返回一个新 `TrustReplyItemVersion`，字段固定为 `versionId/requestKey/handling/answerText/claims/model/generationKind/evidenceSetVersion/sourceVersion`；服务端在创建版本前完成一次输出规范化，版本创建后不得原地修改或再次 trim。`versionId` 由上述稳定字段及指令 SHA-256 有序计算，不含当前时间。失败不得覆盖旧版本，也不得返回可锁定 versionId。
- Applies to: 完整生成 item 初始版本、单项调整 response、整合 input validation。
- Violation consequence: 调整一项污染其他项，或旧 UI 条目引用最后一次全局响应。
- 来源: K-ai-draft-review-state-per-draft, K-ai-reply-evidence-version-deterministic

### Invariant I-5: generationKind 明确区分 AI、安全模板和省略
- Rule: item generationKind 仅为 `AI_GENERATED|SAFE_TEMPLATE|OMITTED`。有依据 AI 输出只有 `usedLlm=true && generationState=LLM_USED` 才能成为 `AI_GENERATED`；LLM fallback 不产生版本。`ACKNOWLEDGE_PENDING` 可在 AI 失败时返回服务端固定、安全、非事实 `SAFE_TEMPLATE`；`OMIT` 只返回空 answer/claims 的 `OMITTED`。不得把现有 QA 规则参考 fallback 标记为任一可锁定 kind。
- Applies to: `generateItem`、完整生成映射、锁定/整合。
- Violation consequence: LLM 失败参考被采用，或无依据内容伪装成 AI 事实。
- 来源: K-ai-draft-review-state-per-draft, K-ai-generation-observability-not-send-gate

### Invariant I-6: 无依据确认语必须通过专用 fail-closed 校验
- Rule: `ACKNOWLEDGE_PENDING` 正文必须为单段、1～600 字符，包含明确“尚待确认/将核实后回复”语义；禁止 URL、数字/金额/时间、合同/资金/机构身份等肯定高风险声明、任何 `AiReplyAction` CTA、内部 token 和列表。AI 输出不合格或失败时使用固定安全模板；安全模板不是 AI 成功声明。
- Applies to: `AiReplyHighRiskClaimValidator.validateNoEvidenceAcknowledgement`、`generateItem`。
- Violation consequence: 缺依据条目通过“暂缓”策略仍写入新事实或承诺。
- 来源: K-grounded-natural-structure-server-gate

### Invariant I-7: claim 与展示正文有双向精确绑定
- Rule: 有依据版本保留有序 `claims[{intentKey,text}]`；每个 intentKey 必须来自当前 request 的 supported intents，source IDs 由服务端 plan 决定。`answerText` 必须等于按 plan 顺序将 claim text 以单空格连接的结果；请求不得自报 sourceRuleIds。整合时重新构造 sections 并运行现有 claim/trust/action 校验。
- Applies to: `AiReplyDraftService` materialization result、item response、`assemble`。
- Violation consequence: 浏览器篡改组合正文后绕过逐 claim 证据校验。
- 来源: K-grounded-json-materialize-before-policy, K-explicit-fact-selection-must-match-request

### Invariant I-8: source/evidence 变化使全部旧版本失效
- Rule: `adjustItem` 与 `assemble` 必须重新 resolve source、request matrix 和 evidence snapshot；请求的 `expectedSourceVersion/expectedEvidenceSetVersion` 任一不等于当前值即返回 409 `TRUST_REPLY_SOURCE_STALE` 或 `TRUST_REPLY_EVIDENCE_STALE`，不得静默重生、改绑或部分采用旧版本。
- Applies to: 单项调整、整合。
- Violation consequence: 锁定文本基于已变化/禁用/删除的证据继续进入草稿。
- 来源: K-ai-reply-evidence-version-deterministic, K-ai-preflight-stale-response-draft-identity

### Invariant I-9: 锁定是显式选择，整合由服务端重新证明
- Rule: 服务端不持久化 UI lock；assemble 请求必须为当前每个 requestKey 提供且只提供一个 `lockedItem`。非 `OMIT` 必须携带可复算 versionId 和非空 answer；`OMIT` 必须空 answer/claims。缺项、重复 key、未知 key、额外 key、未匹配版本或非法 handling 均拒绝，不做“尽量整合”。
- Applies to: `TrustReplyWorkbenchService.assemble`、assemble controller。
- Violation consequence: 未确认项被漏掉、重复或被浏览器伪造为已锁定。
- 来源: original

### Invariant I-10: 最终整合逐字、全量、有序
- Rule: 最终整合不得调用 LLM。每个非 OMIT 的锁定 answerText 按原始 request index 顺序逐字出现一次；composer 不 trim、不格式化正文。不同 request 的相同 answerText 不去重；请求数超过 4 不截断。只允许在答案块外添加 `ReplySnippetService.resolveManualFrame()` 的 salutation/greeting/closing 与空行分隔；不得插入 QA 标题、request 状态、source label 或内部说明。
- Applies to: `AiReplyPointByPointComposer.composeLockedItems`、`assemble`。
- Violation consequence: 已确认内容被全局润色、丢项、乱序或暴露内部状态。
- 来源: K-grounded-paragraph-cap-never-drop-claims, K-grounded-natural-structure-server-gate

### Invariant I-11: raw/rendered 采用边界延续到整合结果
- Rule: assemble response 同时返回 `rawDraftText/renderedDraftText/draftHash/canonicalFactIds/evidenceSetVersion/sourceVersion`；raw 含模板变量，rendered 仅用于显示。后续 live 采用必须能保留 raw 与 text/HTML baseline；本计划不把 rendered 回写覆盖 raw。
- Applies to: `assemble`、preview response。
- Violation consequence: 联系人/发件人变量丢失，或用户编辑被二次渲染覆盖。
- 来源: K-ai-preview-raw-adoption-boundary

### Invariant I-12: 工作台整合仍不具备发送 authority
- Rule: item 调整与 assemble API 不调用发送、mail_record、mail_record_qa_rule 或最终 preflight 写路径；即使所有项锁定，真实页面也只能在后续计划中采用到人工编辑器。
- Applies to: `TrustReplyWorkbenchService`、`TrustReplyWorkbenchController`。
- Violation consequence: 绕过最终 sender/contact/HTML/text 校验直接外发。
- 来源: K-ai-adopt-direct-send-no-residual-gates, K-manual-rich-render-before-send

## 现状审计

### `AiReplyDraftService` 生成结果与三个生产入口
- Schema/state: `AiReplyDraftResult:293-319` 当前只有整封 `draftText`、requestFacts、证据和 readiness，没有逐 request 答案。`RequestFactItem:324-331` 保留 index/text/fact IDs/status/intents。
- Write paths: 无 DB 写；返回值被 live controller、training controller 和 `GroundedAutoReplyDecisionService` 消费。
- Read paths:
  1. `AiTrainingController.simulate:208-260` 映射整封草稿与 coverage。
  2. `UnmatchedInboundMailController.executeAiReplyTurn:348-454` 映射整封草稿、audit、evidence。
  3. `GroundedAutoReplyDecisionService` 用生成结果做自动 fail-closed 决策。
- Interaction points: 给 `AiReplyDraftResult` 增加一个带默认空值的 `itemAnswers` 字段；既有三入口不读取该字段，行为必须保持；公共 workbench 才消费。

### grounded plan/materializer/validator
- `AiReplyGroundedContentPlanner:33-115` 按 request index 生成 claim/missingFacts；UNSUPPORTED 不生成 claim，PARTIAL 只为 supported intents 生成 claim。
- `AiReplyGroundedDraftMaterializer:42-150` 严格解析 exact claim set，生成 `ValidatedSection(requestIndex, answers)`；当前 section 只用于校验后丢弃。
- `AiReplyDraftService.materializeAndValidateGroundedCandidate:927-1045` 依次执行结构、claim、trust、action 校验。
- `AiReplyHighRiskClaimValidator:50-148` 能验证有 source 的 sections；`validatePlainText:26-48` 在 fact IDs 为空时直接 valid，不能作为无依据确认语 gate。
- Interaction points: `buildGroundedResult` 要把已验证 sections 映射到 itemAnswers；单项有据生成必须复用同一 materializer/validators；无据确认必须新增专用 gate，不能复用空 facts 的 `validatePlainText`。

### `qa_rule` 事实存储
- Schema/mapping: `QaRule.answerBody` 在 V79 后非空；`replyPolicy` 在 V80 后非空；`QaRule.isMatchable()` 排除 `NEVER`。
- Write paths: `QaRuleManagementService.create/update/setEnabled/delete`；Flyway seed/update migrations。创建/更新同步 `replyBody=answerBody` 的兼容字段，但 AI claim 只读 `answerBody`。
- Read paths: `QaFactSelectionService`、`AiReplyDraftService`、`AiReplyHighRiskClaimValidator`、`AiReplyPointByPointComposer`、auto decision、tag/monitoring/template consumers。
- Interaction points: item API 不能把 display fields 当 source；assemble 每次重读当前 enabled/nonempty answerBody 并复验 evidence set。(来源: K-answerbody-source-exclusive)

### `reply_snippet` frame 存储
- Schema/mapping: V47 创建 `snippet_type/content/display_order/is_default/enabled`，V64 增加 variant_group；`ReplySnippet.kt:7-18`。
- Write paths: `ReplySnippetService.create/update/setEnabled/setDefault/delete:51-167`，并同步 content variants。
- Read paths: `resolveManualFrame:27-38` 被 `AiReplyPointByPointComposer` grounded/natural assembly 及 `AiReplyDraftService` prompt/fallback 使用。
- Interaction points: 新 `composeLockedItems` 必须复用 `resolveManualFrame()`，但不调用 `resolveAck(null)`；frame 运行时变化允许影响下一次 assemble 的外框，不得改变 locked answers。(来源: K-manual-frame-three-consumers)

### 当前 composer 的丢项风险
- `AiReplyPointByPointComposer.composeFromAnswers:151-169` 先用 `linkedSetOf` 去重；`assembleNaturalEmail:193-218` 再 `take(4)`，并可能加入 ack。
- 这两个方法不能用于锁定整合：相同答案会丢一份，5 个以上 request 会静默丢项。
- 新方法必须独立实现并以“答案逐字出现次数”测试；旧方法保持供既有调用方使用。

### 公共 API 扩展合同

bootstrap 每个 item 新增：

```json
{
  "requestKey": "32-char-sha256-prefix",
  "index": 1,
  "requestText": "...",
  "status": "PARTIAL",
  "factRuleIds": [10],
  "intents": [],
  "allowedHandlings": ["ANSWER_SUPPORTED_PART", "ACKNOWLEDGE_PENDING", "OMIT"],
  "recommendedHandling": "ANSWER_SUPPORTED_PART"
}
```

流式 `operation` 固定为：

- `FULL_DRAFT`：生成所有可回答项的首个版本；UNSUPPORTED 产生 `SAFE_TEMPLATE` 推荐版本，不把整封 fallback 作为版本。
- `ADJUST_ITEM`：额外要求 `requestKey/handling/operatorInstruction`，只返回目标 item 的一个新版本；`OMIT` 可同步完成、不得调用 LLM。

`POST /api/trust-reply/workbench/assemble`：请求携带 `source/expectedSourceVersion/expectedEvidenceSetVersion/lockedItems`，响应返回 raw/rendered/hash/版本与 canonical facts。

## 实现方案

### T1：先用失败测试固定逐项模型与安全矩阵
- Governs: I-1～I-9。
- Files: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`。
- 覆盖 requestKey 稳定性、三种 status 的允许 handling、500 字符指令上限、单项只返回目标 key、旧版本不覆盖、LLM failure 不产 AI version、safe template 标记、OMIT 不调用 client、非法/陈旧 source/evidence fail-closed。
- 无据 gate fixture 同时覆盖中英文安全确认语以及数字、URL、金额、期限、身份/合同肯定句、CTA、列表、内部 token 的拒绝。

### T2：保留已校验 section，增加单项生成窄 seam
- Governs: I-3～I-7。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`。
- 给 `AiReplyDraftResult` 增加默认 `itemAnswers=emptyList()`；只从 `GroundedValidationResult.sections` 构造，fallback 保持空。
- 新增 `generateItem(...)`，先由公共 service 传入当前已解析 `RequestFactItem` 和 canonical context，再用 `contentPlanner.buildPlan(listOf(target))`、现有 grounded messages、materializer、claim/trust/action policy、双 TTL/cancel/progress 完成目标项；不得复制另一个无校验 LLM client 调用。
- `ANSWER_WITH_EVIDENCE/ANSWER_SUPPORTED_PART` 只接受 plan 中 supported claims；`ACKNOWLEDGE_PENDING` 使用专用 prompt + gate，失败返回固定安全模板；`OMIT` 在 workbench service 同步构造，不进入 LLM。
- 既有 `generate()` 默认输出、fallback、auto decision 不变。

### T3：补无依据确认语 validator
- Governs: I-5、I-6、I-12。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt`。
- 新增 `validateNoEvidenceAcknowledgement(text)`；复用 action policy/internal marker/high-risk token helpers，显式执行长度、单段、pending semantic family、number/URL/action/list/claim 禁止项。
- 固定 fallback 文案由服务端常量提供：英文来信 `Thank you for raising this point. I do not have verified information to confirm it yet, so I will check and follow up.`；中文来信 `感谢您提出这一点。目前没有已核验的信息可以确认，我会核实后再回复。`。不得含时间承诺。

### T4：实现逐字且不截断的 locked composer
- Governs: I-9、I-10、I-11。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`。
- 新增 `composeLockedItems(orderedAnswers: List<String>)`：输入必须是版本创建时已经规范化的非空 answerText；composer 原样保留 list 重复项和全部数量，不再 trim/格式化；只在答案块之间添加一个外部空行。frame 顺序固定 `salutation → greeting → answers → closing`；不加 ACK、不加 heading、不调用 `take`/Set。
- 测试至少 6 项、两个完全相同项、多行项、带变量项、空 frame；断言每项完整 substring 出现次数及顺序。

### T5：公共 service 实现 requestKey、版本和 assemble authority
- Governs: I-1～I-12。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`。
- bootstrap 生成 allowed/recommended handling 与 requestKey；完整生成把 `itemAnswers` 变成首版本。
- item-adjust 重新 resolve source/evidence，按 requestKey 找唯一 item，忽略浏览器自报文本/source IDs；构造不可变 versionId。
- assemble 要求覆盖所有当前 request；重算每个 versionId、answerText/claims 绑定、handling 矩阵和 safety；收集 canonical fact IDs 时只使用非 OMIT 版本实际 supported claims 的服务端 source IDs，保持首次出现顺序。
- 调用 `composeLockedItems` 和 preview；draftHash 对 raw 计算。不得写 DB 或调用 send。

### T6：扩展公共 controller 的 operation 与 assemble DTO
- Governs: I-1、I-2、I-4、I-8、I-9、I-11、I-12。
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`。
- `FULL_DRAFT/ADJUST_ITEM` 共用 01 的 generation coordinator 和 progress/cancel；item result 不得污染其他 item。
- `OMIT` 和 assemble 为普通 POST；所有业务冲突映射稳定 409/422 code，不返回 exception、prompt、QA answerBody 或 source body。
- Controller 测试固定 JSON 字段、错误 code、无 send endpoint，以及相同 source/evidence 重试的确定性 versionId。

### T7：全回归确认既有入口兼容
- Governs: I-3、I-5、I-10、I-12。
- Files: 本计划清单内 10 个文件。
- 运行旧训练/live/auto decision/materializer/发送测试；旧消费者不读取 `itemAnswers`，原有 `composeFromAnswers` 不删除。

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | 暴露 validated item answers；增加单项生成窄 seam |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | 修改 | 无依据确认语 fail-closed gate |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | 修改 | 锁定答案逐字全量整合 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | requestKey、handling、版本、stale、assemble |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改 | FULL/ITEM SSE 与 assemble API |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | 单项生成与旧 generate 回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt` | 修改 | 无据确认语安全 fixture |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | 修改 | 逐字、重复、>4 项、frame 测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 新增 | 完整 item 状态机与 stale/assemble 测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | API operation/错误合同 |

文件数：10。子系统数：2（LLM 单项生成与校验；工作台版本/整合 API）。无 schema 变更、无持久化 session、无新共享存储字段。

## 验收标准

- I-1: 相同 sourceVersion/request 重算 requestKey 一致；request 文本、index、intent 或 sourceVersion 任一变化后 key 改变；仅传旧 index 无法调整。
- I-2: 参数化测试断言 3 种 status 的 allowed handling 与 7 个非法组合；非法组合返回 422。
- I-3: 501 字符指令拒绝；指令中加入数字/承诺/source ID 不能使无依据输出通过；只改变表达的指令可产生新版本。
- I-4: 连续调整 item A 两次得到两个 versionId，item B 数据不变；失败后 A 仍保留此前版本；versionId 对相同稳定输入确定。
- I-5: AI 成功、失败、safe template、omit 四类 fixture 的 kind/lockability 正确；旧 fallback 字样永不返回可锁定 versionId。
- I-6: 中英文安全确认语通过；数字、URL、金额、期限、肯定身份、CTA、列表、内部 token 任一存在即失败；fallback 文案逐字匹配计划。
- I-7: 浏览器改变 claims、intentKey、answerText 任一字段后 assemble 失败；合法 answerText 等于有序 claims 的精确 join。
- I-8: 修改 source body 或 QA `answerBody/enabled/updatedAt` 后，旧 expected version 返回相应 409 code，响应不含新草稿。
- I-9: 缺项、重复 key、未知/额外 key、空非 OMIT、非空 OMIT、非法 versionId 均拒绝；完整集合才整合。
- I-10: composer 测试证明 6 项全部保留、重复项保留两次、原序不变、正文 byte-for-byte；源码新方法无 `Set/linkedSetOf/take(`、无 LLM 调用。
- I-11: assemble 同时返回 raw/rendered；draftHash 等于 raw SHA-256；变量只在 rendered 解析，raw 保留。
- I-12: `rg -n "sendManualRichReply|MailDeliveryService|mailRecordRepository.save|mailRecordQaRuleRepository.save"` 对 workbench service/controller 无命中。
- 定向：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiReplyHighRiskClaimValidatorTest,AiReplyPointByPointComposerTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchControllerTest,AiReplyGroundedDraftMaterializerTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,GroundedAutoReplyDecisionServiceTest,PendingMailOperationServiceTest test
```

- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 人工验收清单

### A-1: 每个 request 独立调整
- 前置条件: 一封含 3 个 request 的来信，状态分别为 GROUNDED、PARTIAL、UNSUPPORTED。
- 操作步骤: 1. bootstrap；2. 记录三个 requestKey；3. 对 PARTIAL 项提交 `ANSWER_SUPPORTED_PART` 和“更简洁”指令；4. 再读取/比较结果。
- 预期结果: 只返回 PARTIAL requestKey 的一个新版本；另外两个 key、答案和版本不变；新版本只含该项 supported claims。
- 覆盖: I-1～I-4、需求可观察结果。

### A-2: 缺依据项安全处理
- 前置条件: 一项 UNSUPPORTED request。
- 操作步骤: 1. 选 `ACKNOWLEDGE_PENDING` 并要求 AI “承诺 3 天内给出 5000 美元方案”；2. 请求调整；3. 再选 `OMIT`。
- 预期结果: 第一次不得返回含“3 天/5000 美元”的版本，只能返回通过 gate 的 AI 确认语或计划固定 SAFE_TEMPLATE；第二次返回 `OMITTED`、空正文且不触发 LLM。
- 覆盖: I-2、I-3、I-5、I-6。

### A-3: 版本与锁定输入互不覆盖
- 前置条件: 一项 GROUNDED request，LLM 可用。
- 操作步骤: 1. 用“简洁”调整；2. 用“更正式”再次调整；3. 分别保存两个 response；4. 用第一个 versionId 组装。
- 预期结果: 两个版本 ID 不同且两份文本都可单独引用；使用第一个 versionId 时整合采用第一份文本，不会偷用最后一次 response。
- 覆盖: I-4、I-7、I-9。

### A-4: 证据变化使旧锁定失效
- 前置条件: 已得到并锁定一个有依据版本；可在 QA 管理后台禁用其事实。
- 操作步骤: 1. 禁用该 QA；2. 用旧 source/evidence/version 调 assemble。
- 预期结果: 返回 HTTP 409 和 `TRUST_REPLY_EVIDENCE_STALE`；无 raw/rendered 草稿；重新 bootstrap 后该事实不再出现在 canonical matrix。
- 覆盖: I-8；interaction point `QaRuleManagementService write → workbench re-read`。

### A-5: 六项与重复文本不丢失
- 前置条件: 构造 6 个 request 的合法 lockedItems，其中第 2、5 项 answerText 完全相同。
- 操作步骤: 调 assemble 并查看 rawDraftText。
- 预期结果: 6 个答案按 request index 顺序全部出现；相同答案出现 2 次；没有 request heading、状态词、QA 标签；salutation/greeting 在前，closing 在后。
- 覆盖: I-9、I-10；interaction point `locked versions → ReplySnippet frame → raw draft`。

### A-6: frame 更新不改锁定正文
- 前置条件: 保存一组 lockedItems；记下每个 answerText；在回复片段管理中修改默认 greeting/closing。
- 操作步骤: 用同一组 still-current lockedItems 再次 assemble。
- 预期结果: 新 raw 使用新 greeting/closing；每个 answerText 仍逐字一致、次数和顺序不变。
- 覆盖: I-10；interaction point `ReplySnippetService write → composer read`。

### A-7: raw/rendered 变量边界
- 前置条件: 有效联系人和 sender，某锁定答案或 frame 含合法变量。
- 操作步骤: 调 assemble，比较 rawDraftText 与 renderedDraftText。
- 预期结果: raw 保留变量 token；rendered 使用当前联系人/sender 展开；draftHash 对 raw；两者都包含同一锁定答案语义。
- 覆盖: I-11。

### A-8: 既有生成与纯人工发送回归
- 前置条件: 训练、live、自动 decision 各一条 fixture；另准备纯人工邮件。
- 操作步骤: 运行三个旧生成入口；再直接走 `/manual-rich-reply` 发送纯人工内容。
- 预期结果: 旧生成结果和 fallback/readiness 规则不变；纯人工发送不要求 requestKey、锁定、versionId 或 evidenceSetVersion。
- 覆盖: I-5、I-12、must-not-change 1～5。
