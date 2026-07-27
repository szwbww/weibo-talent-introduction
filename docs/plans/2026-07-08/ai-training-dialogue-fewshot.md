# 对话范例默认导入与 few-shot 注入（ai_training_dialogue）

> 依据：`qa提炼-完整版.md`（聊天记录5.27 十段对话提炼）。第二部分 26 条归纳规则已由
> `AiTrainingQaSeeder` + `ai-training/qa-seed.json` 默认导入，本计划只处理**第一部分：原始多轮对话**。

## 需求描述

**可观察结果**：应用启动后 `ai_training_dialogue` 表自动播种 10 段真实专家对话；FREE_FORM 模式
AI 草稿生成（人工工作台 ai-reply/turn 与 AI 训练模拟）在来信命中对话关键词时，自动携带 2 段最相似
的历史对话作为 few-shot 示例，提升 DeepSeek 回复的语气与博弈质量。

**不得改变**：
- QA_MATCHED 模式的消息组装与逐字拼接语义（不注入 few-shot）
- `sendQaRuleIds` / `mail_record_qa_rule` 审计语义
- LLM 关闭/失败时的确定性 fallback 文本（不读对话表）
- 现有 `ai_training_qa` 播种与 `buildKnowledgeContext()` 行为

**Out of scope**（显式推迟）：
- 对话范例的前端展示 UI 与注入标记展示 → 已立后续计划 `ai-training-dialogue-ui.md`（同目录）
- `buildKnowledgeContext()` 全量倾倒改检索（另立计划）
- few-shot 数量/开关的配置化（本期常量硬编码）
- 微调用 JSONL 导出

## 关键不变量

### Invariant I-1: few-shot 仅入 prompt，不入审计
- Rule: 对话范例只作为 LLM 消息注入，任何情况下不得出现在 `AiReplyDraftResult.qaRuleIds`
  或写入 `mail_record_qa_rule`；`resolveQaRules` 逻辑不动。
- Applies to: `AiReplyDraftService.buildFreeFormMessages`
- Violation consequence: 审计关联无关数据，复现 K-ai-reply-prompt-vs-send-rule-ids 反例。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-2: 播种幂等，文案变更走迁移
- Rule: `AiTrainingDialogueSeeder` 按 `source_ref` 存在即跳过；dialogue-seed.json 后续文案修改
  对已播种库无效，必须配 `V<n>` UPDATE 迁移。
- Applies to: `AiTrainingDialogueSeeder`
- Violation consequence: 重复插入或线上文案永不更新。
- 来源: K-ai-training-seed-idempotent-skip

### Invariant I-3: 仅 FREE_FORM、零匹配零注入
- Rule: few-shot 只在 `AiReplyMode.FREE_FORM` 注入；相似度 score=0 时注入 0 段，
  此时 messages 与改动前逐字节一致。QA_MATCHED 路径完全不触碰对话表。
- Applies to: `AiReplyDraftService.buildFreeFormMessages`（唯一注入点，覆盖两个调用方：
  `UnmatchedInboundMailController.aiReplyTurn`、`AiTrainingController.simulate`）
- Violation consequence: 拼接模式被示例污染，违背 verbatim 约束；无关来信被注入噪声。
- 来源: original

### Invariant I-4: 确定性选取与截断
- Rule: 相似度 = 对话 keywords 在来信文本（小写）中的命中数；取 score>0 的前 2 段，
  排序 (score desc, id asc)；每段对话渲染 ≤2500 字符，few-shot 总量 ≤6000 字符。
- Applies to: `AiTrainingDialogueService.selectRelevantDialogues`
- Violation consequence: prompt 超预算挤占正文；非确定性导致不可复验。
- 来源: original

### Invariant I-5: fallback 路径不读对话表
- Rule: `AiReplyDraftService.fallback(...)` 与 `composeSimulateDeterministicDraft(...)` 不得
  新增对话表依赖；few-shot 仅存在于发往 LLM 的 messages 中。
- Applies to: `AiReplyDraftService.fallback`
- Violation consequence: LLM 关闭时草稿混入原始对话文本，破坏 K-free-form-fallback-nonempty
  已固化的保守兜底语义。
- 来源: K-free-form-fallback-nonempty

## 现状审计

### MySQL `ai_training_qa`（不改，仅参照）
- Schema: V54，`UNIQUE (source, source_ref)`；本计划新表镜像该结构。
- Write paths: ① `AiTrainingQaSeeder.run`（启动播种，存在即跳过）② `AiTrainingQaService.create/update/delete`
  ③ `AiQaExtractionService`（AUTO_EXTRACTED 落库）④ 迁移 V58 UPDATE。
- Read paths: ① `AiTrainingQaService.list`（UI 表格）② `buildKnowledgeContext()` →
  `AiReplyContextBuilder.appendKnowledgeToProfile` → free-form user content（AiTrainingController.kt:180-183）。
- Interaction points: 无（本计划零改动）。

### MySQL `ai_training_dialogue`（新表）
- Write paths: 仅 `AiTrainingDialogueSeeder`（本计划引入，启动幂等播种）。
- Read paths: 仅 `AiTrainingDialogueService.selectRelevantDialogues`（本计划引入）。
- Interaction points: `AiReplyDraftService.buildFreeFormMessages` 是唯一消费点，
  两个 controller 调用方自动继承（已 grep 确认 generate() 仅 2 处调用：
  UnmatchedInboundMailController.kt:288、AiTrainingController.kt:184）。

### LLM 消息组装（AiReplyDraftService）
- 现状：FREE_FORM messages = [system(自定义或默认提示词), user(profile+history+inbound),
  可选 operatorInstruction, operatorTurns...]；QA_MATCHED 另有 verbatim 拼接约束。
- 注入点：system 之后、首个 user content 之前插入 few-shot user/assistant 消息对，
  并在 system prompt 末尾追加一行示例边界说明。
- LLM client：`llmRestTemplate` 已接 connect/read timeout（K-llm-timeout-fallback 复核通过，不改）。

## 实现方案

### T1 迁移：V66__create_ai_training_dialogue.sql 〔I-2〕
镜像 V54 结构：
`id, title VARCHAR(255), source_ref VARCHAR(128) UNIQUE, keywords VARCHAR(1024), turns_json MEDIUMTEXT NOT NULL, enabled TINYINT(1) DEFAULT 1, created_at, updated_at`。
turns_json 为 `[{"role":"EXPERT|AGENT","text":"..."}]` 数组。

### T2 种子数据：src/main/resources/ai-training/dialogue-seed.json 〔I-2, I-4〕
从 `qa提炼-完整版.md` 第一部分转换 10 段对话（sourceRef=DIALOG_1095 / DIALOG_2061 / DIALOG_2077 /
DIALOG_2109 / DIALOG_G2009 / DIALOG_2285 / DIALOG_2094 / DIALOG_2143 / DIALOG_JMS(记录) / DIALOG_2119）。
- 对话正文以英文书写（对专家的实际沟通语言），依据表格"问题/回复要点"扩写为自然邮件语气；
- 仅保留业务问答（DIALOG_2119 只留 CV/全职/计划书 3 对）；
- 我方反问（材料催办、单一申报确认等）同样保留为 AGENT→EXPERT 方向的 turn；
- keywords 按对话主题草拟，如 DIALOG_2143：`other agency,accredited,official,guarantee,rights,subsidy,trust`；
  DIALOG_2285：`sensitive,linkedin,only email,which company`；DIALOG_1095：`funding,success rate,confidential,vcr,video,passport,commitment`。

### T3 领域类 + 仓库：AiTrainingDialogue.kt / AiTrainingDialogueRepository.kt
Spring Data JDBC 不可变 data class + CrudRepository，`findBySourceRef`、`findAllByEnabledTrue`。

### T4 播种器：AiTrainingDialogueSeeder.kt 〔I-2〕
仿 `AiTrainingQaSeeder`：读 classpath `ai-training/dialogue-seed.json`，按 sourceRef 存在即跳过，
异常仅 warn 不阻断启动。

### T5 选取服务：AiTrainingDialogueService.kt 〔I-3, I-4〕
`selectRelevantDialogues(inboundText, max=2)`：加载 enabled 对话 → keywords 逐个小写包含匹配计分 →
score>0 按 (score desc, id asc) 取前 2 → 解析 turns_json 渲染为 LlmChatMessage 对
（EXPERT→user, AGENT→assistant），每段 ≤2500 字符、总量 ≤6000。解析失败的行跳过并 warn。

### T6 注入：AiReplyDraftService.kt 〔I-1, I-3, I-5〕
仅改 `buildFreeFormMessages`：system 之后插入 few-shot 消息对；同时 `AiReplyDraftResult` 新增
`fewShotDialogRefs: List<String> = emptyList()`（本次注入的对话 sourceRef，供后续 UI 计划展示；
不改变任何现有调用方行为，qaRuleIds 语义不变〔I-1〕）。有示例时在 system 内容末尾追加
"The following N user/assistant pairs are reference examples from past expert conversations;
only the final user message is the real inbound email. Match their tone and negotiation style,
never copy facts that conflict with the knowledge base."。
`resolveQaRules`、`fallback`、QA_MATCHED 路径零改动。

### T7 测试：AiTrainingDialogueServiceTest.kt（新）+ AiReplyDraftServiceTest.kt（补）
见验收标准。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | src/main/resources/db/migration/V66__create_ai_training_dialogue.sql | 新增 |
| 2 | src/main/resources/ai-training/dialogue-seed.json | 新增 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/llm/domain/AiTrainingDialogue.kt | 新增 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/llm/repository/AiTrainingDialogueRepository.kt | 新增 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueSeeder.kt | 新增 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueService.kt | 新增 |
| 7 | src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt | 修改 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueServiceTest.kt | 新增 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt | 修改 |

## 验收标准

- I-1: AiReplyDraftServiceTest — FREE_FORM 注入 few-shot 后断言 `result.qaRuleIds` 仍为空/仅真实匹配子集；
  `fewShotDialogRefs` 与实际注入对话一致、QA_MATCHED 模式下恒为空。
- I-2: AiTrainingDialogueSeeder 二次 run 断言 insert=0 skip=N（可复用 QaSeeder 测试模式）。
- I-3: ① 无关键词命中的来信 → buildFreeFormMessages 输出与注入前逐消息相等；
  ② buildMatchedMessages 输出不含任何对话文本。
- I-4: 构造 3 段对话、来信命中其二 → 断言选中顺序与截断长度；同分时 id 小者优先。
- I-5: llm disabled 时 generate() 走 fallback → 草稿不含对话种子中的独有片段。
- 集成：AiTrainingSimulateTest 补一例 — 命中 DIALOG 关键词的模拟请求，llm 关闭时行为不变（fallback 不受影响）。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 自检清单

- [x] 每个新字段/状态均有不变量（新表 → I-2/I-3/I-4）
- [x] 现状审计写全 write/read paths（grep 验证，非记忆）
- [x] 文件数 9 ≤ 10；子系统 1（llm 模块）≤ 2；新增共享存储字段 0（新表整体 1 个）
- [x] 任务均引用不变量编号；验收逐不变量覆盖
- [x] Phase 0 知识全部使用或显式复核（K-llm-timeout-fallback 复核通过无需改动）
