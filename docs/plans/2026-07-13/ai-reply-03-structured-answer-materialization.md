# AI 回复生成：结构化答案与正文安全组装

## 需求描述

Observable outcome：`QA_GROUNDED` 模式的 LLM 只返回 request-index 答案 JSON，后端验证后统一组装称呼、编号标题、答案和结尾；`PARTIAL/UNSUPPORTED` 等内部状态、调试标签和确认提示不得进入草稿。模型不可用或 JSON 无效时仍按相同依据边界生成结构化 fallback。

What must NOT change：`QA_MATCHED` verbatim、`FREE_FORM`、模型选择、超时回退、few-shot 只作风格参考、CTA 最终拦截、raw 模板变量、frame 单源、usedLlm/generationState 真值语义。

Out of scope：验证任意自然语言陈述的逻辑真伪、修改 QA 内容、前端警告与发送拦截、HTML 渲染、外部资料访问。

## 关键不变量

### Invariant I-1: QA_GROUNDED 使用固定 JSON 契约
- Rule: 模型输出必须是单一 JSON 对象 `{"answers":[{"index":1,"answer":"..."}]}`；无 Markdown fence、无 salutation/closing、无额外顶层字段。每个 GROUNDED/PARTIAL index 恰好一次，UNSUPPORTED index 禁止出现。
- Applies to: 首轮生成与动作修正重试。
- Violation consequence: 后端无法证明答案对应哪一问题，内部状态可能被复述。
- 来源: original

### Invariant I-2: 内部状态只作控制数据
- Rule: `RequestGroundingStatus`、`STATUS:`、`UNSUPPORTED/PARTIAL/GROUNDED`、旧 `UNSUPPORTED_TEXT/PARTIAL_CONFIRMATION/INSUFFICIENT_SAFE_REPLY` 文案不得进入 `draftText/renderedDraftText`；UNSUPPORTED item 不生成正文 section，PARTIAL 只输出有据答案。
- Applies to: LLM materialization、fallback、action retry、空文本降级。
- Violation consequence: `This still needs confirmation on remaining details.` 外发。
- 来源: original

### Invariant I-3: 后端拥有 frame 与 heading
- Rule: 模型只写 answer；salutation/greeting/ack/closing 继续由 `ReplySnippetService` 提供，heading 仅从原 requestText 确定性清理。模型不得生成或重写标题。
- Applies to: LLM 与 fallback 正文。
- Violation consequence: 模型改写问题、重复签名或生成 Markdown。
- 来源: K-request-facts-not-flat-pool

### Invariant I-4: heading 清理固定
- Rule: 清除前导 bullet/序号、尾部 `?？;；`、尾部独立 `and`；折叠空白；首个英文字母大写；最长 160 字符。不得摘要、翻译或发明新标题。
- Applies to: 所有编号 section。
- Violation consequence: `the full name...;`、`arrangements; and` 等机械标题继续出现。
- 来源: original

### Invariant I-5: 重复内容确定性处理
- Rule: fallback 中同一 fact rule 第一次输出正文，后续 item 只输出 `Please see point {firstIndex} above.`；LLM answer 规范化后完全相同也使用同一交叉引用。不得简单复制整段。
- Applies to: 企业匹配/企业项目等共享依据项。
- Violation consequence: 第 3 与错误第 7 项内容完全重复。
- 来源: original

### Invariant I-6: 无效模型响应回退
- Rule: JSON parse 失败、字段缺失、重复/未知 index、缺少任一可回答 index、出现 unsupported index、answer 空白或含内部 marker 时，该次响应视为无效；初始响应无效走 deterministic fallback，`usedLlm=false/generationState=FALLBACK_NO_RESPONSE`。
- Applies to: QA_GROUNDED 初始调用。
- Violation consequence: 半结构响应被当作 LLM 成功草稿。
- 来源: K-llm-timeout-fallback

### Invariant I-7: 动作重试仍遵守 JSON 契约
- Rule: 初次已 materialize 的草稿触发 CTA violation 时，重试 prompt 仍要求同一 JSON；重试响应必须先 parse/materialize 再替换。无效重试不得把 raw JSON/自由文本替换进正文，改为对首次已组装正文执行现有 sanitizer。
- Applies to: `enforceActionPolicy()` retry。
- Violation consequence: 首轮结构化、二轮自由文本，安全边界被绕过。
- 来源: K-ai-reply-action-cta-variant-coverage

### Invariant I-8: fallback 事实隔离
- Rule: fallback 只读取每项 `factRuleIds`；GROUNDED/PARTIAL 输出真实非空 QA body，UNSUPPORTED 省略；研究综合项在没有模型时不得把 `expertProfile.take(500)` 直接当作答案，需省略并由 coverage 提示人工处理。
- Applies to: LLM disabled/client unavailable/no response/invalid JSON。
- Violation consequence: 原始画像字段被当作对专家的匹配结论。
- 来源: K-training-knowledge-injection-points / K-ai-reply-profile-absence-warning

### Invariant I-9: 双入口与模式边界
- Rule: 改动收口在 `AiReplyDraftService.generate()`；两个 controller 不做 parse/compose。QA_MATCHED/FREE_FORM 的 client response 仍是完整正文字符串，不使用 JSON materializer。
- Applies to: 训练模拟与收发件。
- Violation consequence: 两入口行为漂移或单问题回归。
- 来源: K-ai-generate-single-freeform-seam

### Invariant I-10: 布局与变量不变
- Rule: 组装结果保留空行，继续经过 layout-preserving action sanitizer；`${...}` raw 变量不在本阶段替换，preview/send 仍沿现有边界渲染。
- Applies to: draftText、renderedDraftText、续轮 assistantDraft。
- Violation consequence: 编号/签名再次变为单行或变量过早丢失。
- 来源: K-action-sanitizer-preserve-layout / K-ai-preview-raw-adoption-boundary

### Invariant I-11: 多请求与研究请求优先 grounded
- Rule: mode 判定顺序固定：多请求（requestCount>=2）或任一 `requiresResearchContext=true` 先进入 QA_GROUNDED，不因 sendQaRuleIds 为空降到 FREE_FORM；其后才判断单请求 QA_MATCHED；单请求、非研究、无 QA 匹配继续走 FREE_FORM。
- Applies to: `generate()` mode 选择。
- Violation consequence: 全部缺依据的多问题邮件进入自由生成并编造完整答案。
- 来源: K-research-fit-dual-evidence

## 现状审计

### QA_GROUNDED LLM response（内存）
- Schema/mapping: 当前 `LlmDraftClient.chatWithModel()` 只返回 String；无需修改 HTTP client 或 provider 请求结构。新 JSON 是 prompt 与本地 parser 契约，不是外部 API schema。
- Write paths:
  1. `AiReplyDraftService.generate()`（203-254）把 client String 直接传 `enforceActionPolicy()`。
  2. `enforceActionPolicy()`（278-348）动作重试后也把 retry String 直接替换正文。
  3. `fallbackDraftText()`（350-389）多问题调用 `AiReplyPointByPointComposer.compose()`。
- Read paths:
  1. 两个 controller 读取 `draftText`。
  2. preview service 渲染变量；前端显示/copy/adopt rendered，续轮发送 raw。
  3. 人工富文本发送仅在未编辑时携带 raw template。（来源: K-ai-preview-raw-adoption-boundary）
- Interaction points: materialization 必须在 action policy 前执行；retry 必须重复 materialization；raw/rendered 边界在 controller 后，不能接触 JSON。

### point-by-point composer
- Schema/mapping: 无数据库；当前 composer 从 `QaRuleRepository.findById()` 只读事实，从 `ReplySnippetService.resolveManualFrame()` 只读 frame。
- Write paths: 只返回 String，不落库。
- Read paths: `fallbackDraftText()` 三种模型失败路径。
- Interaction points: 当前常量 `UNSUPPORTED_TEXT/PARTIAL_CONFIRMATION` 是本次泄漏源；`PROFILE_EXCERPT_MAX_CHARS` 是研究 fallback 误答源。

### Prompt 与 few-shot
- `buildGroundedUserContent()` 当前将 `STATUS:` 和每项事实交给模型，并要求直接输出完整 plain-text 邮件。
- few-shot 由 `AiTrainingDialogueService.selectRelevantDialogues()` 提供，只允许影响风格，事实不能进入真实答案。（来源: K-training-knowledge-injection-points）
- `QA_MATCHED` 与 `FREE_FORM` 是独立分支，不纳入本计划结构化协议。

## 实现方案

### T1：新增结构化 materializer（I-1/I-2/I-5/I-6）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`

- 注入 Jackson `ObjectMapper` 与 `AiReplyPointByPointComposer`。
- 定义 internal DTO：`GroundedAnswerEnvelope(answers)`、`GroundedAnswer(index,answer)`、`MaterializedDraft(text,valid,warningCodes)`。
- strict tree 校验：顶层字段集合只能为 answers；answer 字段集合只能为 index/answer；按 I-1/I-6 验证 index 集合。
- 内部 marker 列表至少包含：`STATUS:`、独立 token `UNSUPPORTED/PARTIAL/GROUNDED`、两条旧 confirmation 文案、`INSUFFICIENT_SAFE_REPLY` 全文。
- 返回 warning code `AI_REPLY_STRUCTURED_RESPONSE_INVALID`；raw JSON 永不返回 controller。

### T2：重构 composer 为唯一正文 assembler（I-2/I-3/I-4/I-5/I-8/I-10）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`

- 提供 `composeFromAnswers(requestFacts, answersByIndex)` 与 `composeFallback(requestFacts)`；二者共用 frame、heading、编号、去重和空行算法。
- 删除 `UNSUPPORTED_TEXT`、`PARTIAL_CONFIRMATION`、profile excerpt 参数和常量。
- UNSUPPORTED 永不创建 section；PARTIAL 可创建 section但不追加确认句。
- fallback 对 `requiresResearchContext=true` 的 item 不做画像推理；即使有 factRuleIds，也省略该综合项，交由前端提示。
- 如果所有 item 均省略，只返回 frame 中可用的 salutation/greeting/ack/closing；不得写“依据不足/人工确认”内部文本。

### T3：Prompt 改成 answer-only JSON（I-1/I-2/I-3/I-8/I-9）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- `buildGroundedSystemPrompt()` 明确唯一 JSON schema；禁止 Markdown fence、标题、称呼、签名、status 文案。
- `buildGroundedUserContent()` 保留 request index/text/evidence level/逐项 facts；UNSUPPORTED 仍提供 text 供模型理解，但明确禁止为其输出 answer。
- expertProfile 仅允许用于 `requiresResearchContext=true` item；其他 item 不得引用画像事实。
- few-shot 边界追加“示例是完整邮件，但当前输出必须遵守 JSON schema”。

### T4：生成、mode 与 retry 接入 materializer（I-6/I-7/I-9/I-10/I-11）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- QA_GROUNDED 初始 client String 先 materialize；valid 才以 `LLM_USED` 进入 action policy。
- 调整 mode 判定为 I-11 顺序；多请求/研究请求即使 send ids 为空也使用 matrix/structured boundary。
- invalid 初始响应调用同一 fallback composer，state=`FALLBACK_NO_RESPONSE`、usedLlm=false，并添加 `AI_REPLY_STRUCTURED_RESPONSE_INVALID`。
- 给 action retry 增加 mode-aware materialize seam；QA_MATCHED/FREE_FORM identity，QA_GROUNDED strict parse+compose。
- invalid retry 保留首次 materialized text，再执行 sanitizer；不得把 retry raw 替换正文。
- disabled/null client/no response 三条路径继续用 fallback，generationState 现有枚举值不变。

### T5：materializer/composer 测试（I-1 至 I-8/I-10）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`

- 完整 JSON 正常组装；Markdown fence、额外字段、重复/缺失/未知/unsupported index、空 answer、内部 marker 均 invalid。
- heading 清理逐字断言：`the full name...;` → `The full name...`；`arrangements; and` 去尾部 and。
- partial 无 confirmation；unsupported section 不存在；研究 fallback 不复述 profile。
- 同 rule 与同 answer 的后项使用 `Please see point N above.`。
- 所有内容省略时只返回 frame，无内部提示。

### T6：DraftService 模式与重试测试（I-6/I-7/I-9/I-10）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- 本次 7 项邮件：LLM response 使用 JSON；draft 是 frame+编号正文，不包含 JSON/status marker。
- invalid JSON 的 `usedLlm=false/FALLBACK_NO_RESPONSE`；disabled/null/empty/invalid 四类 fallback 同构。
- CTA 违规首轮 + 合法 JSON retry；CTA 违规首轮 + 非法 retry 两条路径。
- QA_MATCHED/FREE_FORM 现有完整字符串响应测试保持；新增“多请求全无 QA 仍 QA_GROUNDED”和“单请求非研究无 QA 仍 FREE_FORM”。
- raw 模板变量、空行、编号和签名经过 action sanitizer 后保持。

### T7：训练 WebMvc context 接线回归（I-9）
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`

- 将 `AiReplyGroundedDraftMaterializer` 加入现有 `@Import`，保证 `AiReplyDraftService` 新依赖在 slice test 中可解析。
- 增加一条 invalid structured response → fallback 状态的 endpoint 断言；response 不含 raw JSON。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt` | 新增严格 JSON 解析、index/marker 校验 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | 后端唯一正文组装、去内部状态与重复 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | grounded JSON prompt、materialize、fallback、retry 接入 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt` | 新增 parser/validator 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | 组装/heading/省略/去重测试 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 生成状态、模式、retry 集成测试 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | Spring slice 接线与 endpoint fallback 回归 |

## 验收标准

- I-1：所有 schema/index 负例被拒绝；正常 JSON 不直接出现在 draft。
- I-2：全测试搜索 7 个禁用 marker，draftText 均不命中。
- I-3：模型 answer 不含 frame/heading；最终 frame 只来自 ReplySnippetService。
- I-4：四种脏 heading 得到固定期望值。
- I-5：共享 rule/相同 answer 后项仅有 `Please see point N above.`。
- I-6：invalid 初始响应 state/usedLlm/warning 真值正确。
- I-7：invalid retry 不替换首次安全正文，最终无 CTA。
- I-8：fallback 不输出 unsupported，不把 expertProfile 原文作为匹配答案。
- I-9：QA_MATCHED/FREE_FORM 回归测试不使用 JSON parser。
- I-10：raw `${expertName|Professor}`、编号换行、closing 换行保持。
- I-11：多请求/研究请求空 send ids 仍 QA_GROUNDED；单请求非研究空 send ids 仍 FREE_FORM。
- Spring slice：新 materializer bean 可解析，invalid response endpoint 不泄漏 raw JSON。
- 定向命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyGroundedDraftMaterializerTest,AiReplyPointByPointComposerTest,AiReplyDraftServiceTest,AiTrainingSimulateTest test`。

## 人工验收清单

### A-1: Pro 模型逐点结构化回复
- 前置条件: 计划 1/2 已完成；本次邮件研究画像与公司/项目/匹配/合同/流程 QA 均有审核依据；选择 DeepSeek V4 Pro。
- 操作步骤: 1. 点击生成模拟回复；2. 查看草稿；3. 在 Network 查看 response draftText。
- 预期结果: 草稿为自然邮件，不显示 JSON；研究问题排第 1；各节标题首字母大写且无尾部分号/and；无五类内部 marker。
- 覆盖: I-1/I-2/I-3/I-4/I-9

### A-2: 缺依据正文隔离
- 前置条件: 暂停职责/交付物的完整 QA 依据，使该项为 PARTIAL；另加入一项无任何 QA 的问题。
- 操作步骤: 1. 生成模拟回复；2. 搜索草稿正文。
- 预期结果: PARTIAL 节只包含已有审核事实，不含 confirmation 句；UNSUPPORTED 节不出现在正文；正文不含 `PARTIAL/UNSUPPORTED/This still needs confirmation`。
- 覆盖: I-2/I-8

### A-3: 模型无效响应回退
- 前置条件: 测试环境 LLM stub 返回普通邮件字符串而非 JSON。
- 操作步骤: 1. 生成收发件 AI 回复；2. 查看 generationState、反馈和草稿。
- 预期结果: generationState=`FALLBACK_NO_RESPONSE`、usedLlm=false；草稿仍有已支持项的编号结构；不显示 raw 模型字符串或 JSON；缺口只等待计划 4 前端提示。
- 覆盖: I-6/I-8 / LLM→fallback interaction point

### A-4: 单问题与自由回复回归
- 前置条件: 一封单问题 QA_MATCHED 邮件；一封无 QA 的 FREE_FORM 邮件。
- 操作步骤: 分别生成回复。
- 预期结果: 两封均接收模型的完整邮件正文，不要求 JSON；模型选择、称呼变量和 CTA 拦截与改动前一致。
- 覆盖: I-9/I-10 / must-NOT-change

### A-5: 超时、few-shot 与变量边界回归
- 前置条件: 配置一个相关 dialogue few-shot；LLM stub 可切换为超时；frame 含 `${expertName|Professor}` 与 sender 变量。
- 操作步骤: 1. 正常生成并查看 injectedDialogRefs；2. 切换超时再次生成；3. 在 preview 中查看变量，再发起一次续轮修改。
- 预期结果: 正常生成只记录相关 few-shot 且其事实不进入 answer；超时在既有 timeout 内返回 deterministic fallback；draftText 保留 raw 变量、renderedDraftText 显示渲染值；续轮发送 raw assistantDraft；布局空行不丢失。
- 覆盖: I-8/I-9/I-10 / few-shot、超时、raw/rendered、续轮、布局 must-NOT-change
