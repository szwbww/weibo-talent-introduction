# AI 回复 CTA 内容边界治理

## 需求描述

Observable outcome：泛化项目/尽调问询的已审核事实中不再夹带“请发 CV”“确认研究方向”“安排会议”等未请求 CTA；材料知识仅由明确材料意图触发；style few-shot 不再用 CV/meeting 字面做负面示例。  
What must NOT change：明确询问材料时仍可命中 `MATERIALS_LIGHT`；明确询问企业匹配时仍可返回企业匹配事实；历史迁移不回改。  
Out of scope：运行时动作拦截、定向知识检索算法、模型选择。

## 关键不变量

### Invariant I-1: 事实答案与 CTA 分离
- Rule: `qa_rule.id=24` 只保留项目事实/保密无费用；删除 `reply with your CV...`。`qa_rule.id=23` 只保留匹配流程事实；删除反问研究方向。
- Applies to: 新 Flyway migration、线上存量 rows。
- Violation consequence: 即使 prompt 禁止，权威 facts 自身仍要求 CV。
- 来源: K-qa-rule-runtime-vs-migration-writes

### Invariant I-2: 材料关键词不得使用裸 `provide`
- Rule: `MATERIALS_LIGHT` keywords 只能为明确材料短语；不得含裸 `provide`，因为 `provide further information` 是尽调请求而非材料意图。
- Applies to: `qa-seed.json`、存量 `ai_training_qa`。
- Violation consequence: 无 CV 的邮件召回 CV 答案。
- 来源: original

### Invariant I-3: style-only 范例避免动作词锚定
- Rule: `STYLE_MULTI_DUE_DILIGENCE` 的 AGENT 文本用 `requesting unrelated materials` 替代 `request for your CV`；`STYLE_TRUST_VERIFICATION` 用 `unrelated next action` 替代 `documents or a meeting`；语义仍是先回答再推进。
- Applies to: dialogue seed 与存量 dialogue。
- Violation consequence: 模型从否定示例复制 CV/meeting 词汇。
- 来源: K-dialogue-seed-idempotent-skip

## 现状审计

### `qa_rule`
- Schema/mapping: V3 创建；正文/关键词可由 Flyway 和 `QaRuleManagementService` 双写。
- Write paths: V3/V38/V52/V57/V63/V65/V68；运营 QA 管理 CRUD。
- Read paths: `QaMatchService.match/suggestComposition`、`AiReplyDraftService` matched/grounded facts、人工组装与外发。
- Interaction points: V65 id=24 正文含 CV CTA；V65 id=23 正文含研究方向反问。迁移必须先核对线上运营改动。（来源: K-qa-rule-runtime-vs-migration-writes）

### `ai_training_qa`
- Schema: V54；唯一键 `(source, source_ref)`；enabled 默认 1。
- Write paths: `AiTrainingQaSeeder`（存在即跳过）、V58、管理接口 create/update/delete。
- Read paths: `AiTrainingQaService.list/buildKnowledgeContext`。
- Interaction points: seed `MATERIALS_LIGHT` 含裸 `provide`；只改 JSON 对存量库无效。

### `ai_training_dialogue`
- Schema: V66；`source_ref` UNIQUE。
- Write paths: seeder 存在即跳过、V69 upsert；无在线写接口。
- Read paths: dialogue list、few-shot selector。
- Interaction points: JSON 与 V69 当前均含 CV/meeting 否定措辞；必须由新迁移同步存量。（来源: K-dialogue-seed-idempotent-skip）

## 实现方案

### T1：新增 V70 幂等内容迁移（I-1/I-2/I-3）
文件：`src/main/resources/db/migration/V70__tighten_ai_reply_action_boundaries.sql`

- 上线前导出并比对目标 rows；将运营差异合入 V70。
- id=24 设置为 V65 正文去掉最后一句 CV CTA；保留 no-fee/confidentiality。
- id=23 设置为第一段企业匹配事实，删除 `could you confirm...`。
- `ai_training_qa` 以 `(source='MANUAL_IMPORT', source_ref='MATERIALS_LIGHT')` 精确更新 keywords，固定为：`what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,what should i provide,provide my cv`。
- 两条 `STYLE_*` 按 source_ref 更新 turns_json；JSON 必须有效，内容与 seed 逐字一致。

### T2：同步新库 seed（I-2/I-3）
文件：
- `src/main/resources/ai-training/qa-seed.json`
- `src/main/resources/ai-training/dialogue-seed.json`

- 仅做 I-2/I-3 指定字面替换；不改其他事实和 enabled 集合。

### T3：数据契约测试（I-1/I-2/I-3）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaSeederTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueCurationTest.kt`

- 解析 seed 和 V70；断言裸 `provide` 不存在、明确短语仍在。
- 断言两条 style 文本不含 `CV`/`meeting`，且 seed/V70 文本一致。
- 断言 V70 精确处理 id 23/24，且未修改 applied migration V65/V69。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/resources/db/migration/V70__tighten_ai_reply_action_boundaries.sql` | 存量内容治理 |
| `src/main/resources/ai-training/qa-seed.json` | 新库材料关键词 |
| `src/main/resources/ai-training/dialogue-seed.json` | 新库 style 去锚定 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaSeederTest.kt` | QA seed/V70 契约 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueCurationTest.kt` | dialogue seed/V70 契约 |

## 验收标准

- I-1：迁移后 id 23/24 正文无 direct request CV/research focus。
- I-2：`MATERIALS_LIGHT` 不匹配单词 `provide`，仍匹配 `what should i provide`。
- I-3：启用 style turns 无 `CV`、`meeting` 字面；新旧库一致。
- 命令：`mvn -Dtest=AiTrainingQaSeederTest,AiTrainingDialogueCurationTest test`。

## 人工验收清单

### A-1: 泛化尽调不召回材料 CTA
- 前置条件: 已执行 V70；使用本次专家邮件。
- 操作步骤: 在 QA 管理查看 Program overview/Partner company；在训练页生成模拟回复。
- 预期结果: 两条规则正文无 CV/研究方向反问；模拟回复不因 `provide further information` 要求 CV。
- 覆盖: I-1/I-2

### A-2: 明确材料问题仍有答案
- 前置条件: 来信 `What documents should I provide at this stage?`。
- 操作步骤: 生成模拟回复。
- 预期结果: 可命中材料知识并解释当前阶段材料；不是“无已审核依据”。
- 覆盖: I-2 / must-NOT-change

