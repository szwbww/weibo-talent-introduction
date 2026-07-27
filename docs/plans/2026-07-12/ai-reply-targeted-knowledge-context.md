# AI 回复定向知识上下文与默认目标修正

## 需求描述

Observable outcome：两入口只把与当前来信明确主题匹配的启用训练 QA 注入 prompt；无匹配时注入空知识，不再全量注入 12000 字符。默认目标改为“先完整回答当前邮件”，不再默认推进会议或索取 CV。研究资料不足继续只显示已有 warning。  
What must NOT change：训练 QA CRUD/list；QA_MATCHED verbatim 契约；只读专家画像与 mailRecordId 精确选择；自定义 prompt 仍优先。  
Out of scope：运行时输出动作扫描、外部资料抓取、向量检索。

## 关键不变量

### Invariant I-1: 训练知识按当前邮件召回
- Rule: `buildKnowledgeContext(inboundText)` 只返回 enabled 且至少一个逗号分隔 keyword 作为完整归一化短语命中的行；无命中返回空串，禁止回退全量。
- Applies to: AI 训练 simulate、收发件 aiReplyTurn。
- Violation consequence: CV/资金/会议等无关知识污染草稿。
- 来源: K-training-knowledge-injection-points

### Invariant I-2: 两入口使用相同检索输入与算法
- Rule: 两个 controller 均用实际 `inboundText` 调同一 service；不得用 subject、operatorInstruction 或不同截断规则。
- Applies to: 两个 generate 调用方全集。
- Violation consequence: 同一邮件在训练页和邮箱生成不同 facts。
- 来源: K-ai-generate-single-freeform-seam / K-ai-simulate-exact-mail-id

### Invariant I-3: 召回预算稳定
- Rule: 排序为 matched keyword 数降序、id 升序；最多 6 行、最终文本最多 6000 字符；不命中的行不因 topic/question 相似而加入。
- Applies to: `AiTrainingQaService.buildKnowledgeContext`。
- Violation consequence: DB 顺序变化导致 prompt 漂移或超预算。
- 来源: original

### Invariant I-4: 默认目标不主动推进
- Rule: base/default prompt 首要目标为按顺序回答当前请求；除非 inbound 或 operatorInstruction 明确要求，不主动索要材料、提议会议、通话或其他下一步动作。
- Applies to: QA_MATCHED、QA_GROUNDED、FREE_FORM 默认 prompt。
- Violation consequence: 专家未提供 CV 却收到 CV/meeting CTA。
- 来源: K-prompt-config-effective-default

### Invariant I-5: AI 不负责获取外部研究资料
- Rule: prompt 明确只能使用已有画像/QA/训练知识；不得访问 URL、触发 enrichment 或建议自己稍后审阅链接。`EXPERT_*` warnings 原样返回。
- Applies to: grounded/free-form prompt 与现有 context service。
- Violation consequence: 虚假声称看过 Scholar/Scopus。
- 来源: K-ai-reply-profile-absence-warning

## 现状审计

### `ai_training_qa`
- Schema: V54，keywords nullable VARCHAR(512)，enabled TINYINT，唯一键 `(source,source_ref)`。
- Write paths: seeder、V58/V70、`AiTrainingQaService.create/update/delete`。
- Read paths: list UI；`buildKnowledgeContext()` 当前全量 enabled、created desc、拼接后 take(12000)。
- Interaction points: 两个 controller 均无参调用全量方法，再经 `AiReplyContextBuilder.appendKnowledgeToProfile` 注入；因此“未命中材料”仍会看到 MATERIALS_LIGHT。（来源: K-training-knowledge-injection-points）

### `ai_prompt_config`
- Schema: V55，仅存 nullable custom prompt/constraints。
- Write path: `AiPromptConfigService.update`。
- Read paths: effective DTO、FREE_FORM system prompt。
- Interaction points: 空 custom 使用 `FreeFormPromptDefaults`；已有 custom 不应被数据库迁移覆盖。（来源: K-prompt-config-effective-default）

### 双入口上下文
- Write/data construction:
  1. `AiTrainingController.simulate:200-211`：inboundText → full knowledge → context → generate。
  2. `UnmatchedInboundMailController.aiReplyTurn:280-303`：同结构。
- Read: `AiReplyDraftService` 仅消费 context.profileText；QA send ids 另算，不能因 knowledge 改变。（来源: K-ai-reply-prompt-vs-send-rule-ids）
- Interaction points: 两 controller 测试目前 mock 无参方法，必须同步改签名并验证 exact inboundText。

## 实现方案

### T1：先写检索单测（I-1/I-3）
文件：新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaServiceTest.kt`

- `provide further information` 不命中 V70 后的 MATERIALS_LIGHT。
- `what documents should I provide` 命中 MATERIALS_LIGHT。
- 多条按 keyword 命中数 desc/id asc；disabled 永不返回；最多 6/6000；零命中空串。
- 关键词 trim/lowercase/空项处理固定。

### T2：实现定向检索（I-1/I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaService.kt`

- 将无参方法替换为 `buildKnowledgeContext(inboundText: String)`；不保留会被误用的无参 overload。
- normalize：lowercase、连续空白压一格；keyword 逗号拆分、trim、过滤空。
- score 只统计 `normalizedInbound.contains(normalizedKeyword)`。
- filter score>0 → score desc/id asc → take(6) → 现有 Topic/Question/Answer 格式 → take(6000)。

### T3：双入口改用同一输入（I-2/I-5）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- 在得到 cleanedBody/body 后调用 `buildKnowledgeContext(inboundText)`。
- context service 仍只读；不新增 URL/enrichment 调用。

### T4：修正默认 prompt（I-4/I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt`

- `baseSystemPrompt` 将 meeting goal 替换为：先按顺序完整回答当前邮件；只使用提供上下文。
- 删除 default 中无条件“early stage ask CV...”段。
- 加入：没有明确请求/授权时不得索要材料、提议会议或添加下一步 CTA；外部链接不代表已访问。
- custom prompt 仍不覆写；constraints 追加方式不变。

### T5：调用链与默认值测试（I-2/I-4/I-5）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigServiceTest.kt`

- verify 两入口 exact `buildKnowledgeContext(inboundText)`。
- 同一 inbound/knowledge 下捕获 generate 参数一致。
- 默认 prompt 无 `advance ... scheduling a meeting`、无主动 CV 指令；包含 no-unrequested-action 和 no-external-access。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaService.kt` | 定向知识检索 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt` | 默认目标/动作边界 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 模拟入口传 inbound |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 邮箱入口传 inbound |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaServiceTest.kt` | 检索单测（新增） |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigServiceTest.kt` | 默认 prompt 回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 模拟调用链 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 邮箱调用链 |

## 验收标准

- I-1/I-3：service 单测证明零命中空串、材料/非材料边界和预算。
- I-2：两个 controller verify 相同 inbound 参数，旧无参调用 grep 为 0。
- I-4：effective default 不含主动 CV/meeting，custom prompt 测试保持通过。
- I-5：本计划 diff 不新增 HTTP/ES 写/enrichment 调用；研究不足 warning 测试保持通过。
- 命令：`mvn -Dtest=AiTrainingQaServiceTest,AiPromptConfigServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`。

## 人工验收清单

### A-1: 尽调邮件不注入材料知识
- 前置条件: V70 已执行；选本次专家邮件。
- 操作步骤: 分别在训练页和收发件箱生成首轮草稿。
- 预期结果: 两入口都不要求 CV/专利/出版物；缺研究依据时显示中文 warning，不声称访问链接。
- 覆盖: I-1/I-2/I-4/I-5

### A-2: 明确材料问询可召回
- 前置条件: 来信 `What documents should I provide at this stage?`。
- 操作步骤: 两入口分别生成。
- 预期结果: 两入口均可使用材料知识，事实范围一致。
- 覆盖: I-1/I-2 / must-NOT-change

### A-3: 自定义 prompt 不被覆盖
- 前置条件: 训练页保存一条自定义 system prompt。
- 操作步骤: 刷新配置并生成草稿。
- 预期结果: 有效配置仍显示自定义内容；数据库值未被迁移或启动逻辑改写。
- 覆盖: I-4 / must-NOT-change

