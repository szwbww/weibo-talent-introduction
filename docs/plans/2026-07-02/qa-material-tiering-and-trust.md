# 开发计划：QA 材料分阶段 + 资金待遇前置（提升取信率）

> 背景：真实外联中取信专家难，少量愿发 CV，无人愿发详细材料。经分析 `docs/qa提炼-完整版.md`，根因是**开场/条件类自动回复一次性索取全套重资料（护照/学位证/在职证明），且项目介绍太简、资金待遇不具体**，早期高压吓退专家。本计划把材料索取拆为「轻问（自动）+ 重资料（人工）」，并把资金待遇量化前置。
>
> 用 create-p 编写。Phase 0 已加载 `docs/knowledge/qa/*`。

---

## 需求描述

**可观察结果**：
- 专家首次/早期来信问「项目是什么」「需要什么材料」时，自动回复**只轻问 CV + 专利证明 + 发表论文清单**，并逐项说明「为什么需要」，附「严格保密 / 全程绝不收费 / 可脱敏」。
- 项目介绍类自动回复中，**资金写具体额度**（政府科研经费约 300 万–1200 万 RMB / ¥3,000,000–¥12,000,000 + 企业另发个人薪资 + 全职额外房补）以吸引注意。
- **护照 / 学位证 / 在职证明等重资料不再出现在任何自动回复正文**，改由人工（`MATERIAL_REMINDER` 模板 / 已有的 handoff 规则 / 人工组装台）在短会或明确意向后索取。

**不可改变（must NOT change）**：
- QA 匹配算法（`QaMatchService`）、`supersedesChildren`/`composeOrder`/gap 检测语义。
- 现有类、状态机、接口、表结构（不新增列、不改 schema）。
- 已有的 `Passport and document reluctance`（V52，`auto=0/handoff=1`）拒发护照 handoff 规则语义。
- 发送用 `qaRuleIds` 与 prompt 用 `promptRuleIds` 的既有区分（K-ai-reply-prompt-vs-send-rule-ids）。

**Out of scope（显式延后）**：
- 首封外联邮件（INTRODUCTION 模板）的信任话术前置——本轮不动 `IntroductionMailComposer` / `V2` 模板。
- 会议规则 / 优势规则 / 其它类目文案的润色（可后续单独计划）。
- 前端 UI 改动、新增规则类目、新增状态。
- QA 匹配算法优化、关键词大改（仅为新轻问规则补必要关键词）。

---

## 关键不变量

### Invariant I-1: 重资料不入任何自动回复
- Rule: `passport` / `doctoral degree certificate` / `proof of employment`（护照 / 学位证 / 在职证明）等重资料，**绝不**出现在任何 `auto_reply_enabled=1` 的 `qa_rule.reply_body`，也不出现在 `ai_training_qa.answer`。只允许出现在：人工模板 `MATERIAL_REMINDER`、`auto_reply_enabled=0`/`handoff_required=1` 的规则、或人工组装台。
- Applies to: `qa_rule` 中 `Program overview`(OVERVIEW)、`Application criteria`、`About the talent program`；`ai_training_qa` 中 `sourceRef` = `APPLYING_CRITERIA`、`PROJECT_CONTENT`。
- Violation consequence: 早期即索重资料 → 专家高压吓退，回到当前「无人发详细材料」的问题。
- 来源: original（源自 `docs/qa提炼-完整版.md:137` 结论「反向采集清单是人工模板，非自动回复」）。

### Invariant I-2: 轻问材料三件 + 逐项理由 + 信任兜底
- Rule: 轻问材料自动回复内容 = **CV + 专利证明(patent certificates) + 发表论文/期刊清单(publication list)**，且每项附「为什么需要」（CV→匹配企业/方向；专利→加分、展示创新力；论文→佐证研究水平、评审关键项），并以「严格保密 / 全程绝不收费 / 可脱敏技术细节」收尾。不得夹带任何重资料（见 I-1）。
- Applies to: 新增 `qa_rule`（reply_subject = `Getting started materials`）；`ai-training/qa-seed.json` 新条目 + `V58` 中对应 `ai_training_qa` 行。
- Violation consequence: 材料索取仍显突兀或高压，取信率不改善。
- 来源: original（用户三点补充）。

### Invariant I-3: 资金待遇必须量化
- Rule: 项目介绍与资助类自动回复必须显式给出金额区间 **3M–12M RMB（300 万–1200 万 / ¥3,000,000–¥12,000,000）**、**企业单独发放个人薪资**、**全职额外房补**。
- Applies to: `qa_rule` 的 `Program overview`(OVERVIEW)、`About the talent program`、`Funding support`；`ai_training_qa` 的 `PROJECT_CONTENT`、`SALARY`。
- Violation consequence: 吸引力不足，专家不回。
- 来源: original（用户第 1 点）。

### Invariant I-4: ai_training_qa 播种幂等，既有环境改动须走迁移
- Rule: `AiTrainingQaSeeder` 按 `(source=MANUAL_IMPORT, sourceRef)` **存在即跳过**（`AiTrainingQaSeeder.kt:29`），仅对全新库生效。任何要在**既有已播种库**生效的 `answer`/`keywords` 变更，必须同时在 `V58` 迁移里 `UPDATE ai_training_qa ... WHERE source='MANUAL_IMPORT' AND source_ref=...`。
- Applies to: `ai-training/qa-seed.json` 的每处编辑，都要有 `V58` 对应 UPDATE。
- Violation consequence: 生产/开发既有库的训练知识仍含旧的重资料清单，`buildKnowledgeContext()` → `appendKnowledgeToProfile()` 注入 free-form prompt，AI 仍可能主动索取重资料，绕过 I-1。
- 来源: original（Phase 1b 审计发现）。

### Invariant I-5: OVERVIEW 复合规则自洽
- Rule: `Program overview`(OVERVIEW, `supersedes_children=1`) 命中时会压缩为单条外发。删掉其重资料清单后，正文必须仍自洽包含：两类项目 + 量化资金(I-3) + 轻问材料引导(CV/专利/论文) + 保密免费。不得因删清单而丢失材料引导。
- Applies to: `qa_rule` `Program overview` 的 `reply_body` 改写。
- Violation consequence: 概览多问来信被压缩成单条后，反而不再引导任何材料，转化下降。
- 来源: K-overview-gap-supersede（`supersedesChildren=true` 复合规则视为覆盖总览意图）。

### Invariant I-6: 迁移编码与分段惯例
- Rule: 新迁移字面量 **ASCII-only**；中文 `display_name`/`section_title` 用 `CONVERT(UNHEX('<utf8hex>') USING utf8mb4)`（V44/V52 惯例，避免 mojibake，参见 V44/V45 修复史）。多段 `reply_body` 段落用 `\n\n` 保留（V46 惯例，`K-plaintext-reply-client-reflow`）。
- Applies to: `V57` 新增规则与所有中文字面量。
- Violation consequence: 显示名乱码，需再来一轮 repair 迁移。
- 来源: K（V44/V45 repair history）。

---

## 现状审计

### Store: `qa_rule`（MySQL，Spring Data JDBC）
- Schema 关键列（累计迁移后）：`category_id, keywords, match_mode, priority, reply_subject, reply_body, display_name, section_title`(V40), `supersedes_children`(V41), `auto_reply_enabled, handoff_required, enabled`。
- 相关既有规则（按 `reply_subject`）：
  1. `Program overview`（类目 OVERVIEW，V41 建，`priority=5`, `supersedes_children=1`, `auto=1`）— **正文含重资料清单**（V41:16 / V46:5-13：`passport, doctoral degree certificate, ... proof of employment`）+ 资金 3–12M。**首次接触的主命中规则**。→ 违反 I-1，需改。
  2. `About the talent program`（类目 PROGRAM_AND_ELIGIBILITY，V3 建）— 两类项目描述偏简，资金仅「substantial amount」，无量化（V46:16-21）。→ 违反 I-3，需改。
  3. `Application criteria`（V3 建）— **正文含重资料清单**（V46:24-27：`passport, doctoral degree certificate, CV, proof of employment, publication list`）。→ 违反 I-1，需删清单。
  4. `Funding support`（V3 建，rule8）— 未量化金额。→ 按 I-3 补量化。
  5. `Passport and document reluctance`（V52 建，`auto=0/handoff=1`）— 拒发护照 → 人工。**保留不动**。
- Write paths（写 `qa_rule` 的地方）：仅 Flyway 迁移 `V3/V17/V18/V38/V40/V41/V44/V45/V46/V52`（种子 + repair）。无运行时代码写 reply_body。→ 本计划新增 `V57` 即唯一写入点。
- Read paths：
  1. `QaMatchService.match()`（`QaMatchService.kt:53`）→ 自动回复外发正文（经 `QaReplyComposer.compose` 逐段拼接，verbatim）。
  2. `QaMatchService.suggestComposition()`（:14）→ 人工组装台候选 + gap。
  3. `AiReplyDraftService.buildMatchedUserContent()`（`AiReplyDraftService.kt:196-210`）→ QA_MATCHED 模式把 `rule.replyBody` 作为 `SEGMENT` 注入 LLM，要求 verbatim 保留。
- Interaction points：
  - `Program overview` 命中 → `applySupersede`（QaMatchService.kt:81）压缩为单条外发（I-5）。改其正文即直接改外发内容。
  - 新增轻问规则关键词若与 OVERVIEW 关键词重叠，OVERVIEW（supersede）优先；需确保「what documents / materials needed」等词**不在** OVERVIEW 关键词内（现 OVERVIEW 关键词为 `learn more,more information,...,before sharing,understand the program,...`，不含 documents 词，OK）。

### Store: `ai_training_qa`（MySQL）— 训练知识，注入 free-form prompt
- Schema：`topic, question, answer, keywords, source, source_ref, enabled, created_at, updated_at`（V54）。
- Write paths：
  1. `AiTrainingQaSeeder.run()`（`AiTrainingQaSeeder.kt:23-52`）— 启动时从 `ai-training/qa-seed.json` 播种，**按 `(source, sourceRef)` 存在即跳过**（:29）。→ I-4 核心。
  2. `AiTrainingQaService`（人工在 UI 增改，不在本计划范围）。
  3. 本计划新增 `V58` 迁移 UPDATE 既有行。
- Read paths：
  1. `AiTrainingQaService.buildKnowledgeContext()`（`AiTrainingQaService.kt:43`）→ `AiReplyContextBuilder.appendKnowledgeToProfile()`（:24）→ 拼进 expert profile 的「Training knowledge base:」→ `AiReplyDraftService.buildFreeFormUserContent()` 注入 free-form LLM prompt（`AiTrainingController.kt:157-161`）。
  2. `extractTrainingKnowledgeSummary()`（`AiReplyDraftService.kt:314`）→ simulate 兜底草稿。
- 相关条目（`sourceRef`）：`PROJECT_CONTENT`（资金 answer）、`APPLYING_CRITERIA`（**含全套重资料 answer**，qa-seed.json:19）、`SALARY`（3-12M）。
- Interaction point：`APPLYING_CRITERIA` 的 answer 含重资料 → 注入 free-form prompt → AI 可能主动索取护照/学位证 → 绕过 I-1。**这是最隐蔽的泄漏点**，必须随 qa_rule 一起改，且因 I-4 需走 V58。

### Store: free-form 默认提示词（`FreeFormPromptDefaults`）
- 位置：`AiPromptConfigService.kt:21-37`（`baseSystemPrompt` + `defaultFreeFormSystemPrompt`）。空自定义时生效（K-prompt-config-effective-default、K-free-form-fallback-nonempty）。
- Read path：`AiReplyDraftService.buildFreeFormSystemPrompt()`（:193）与 `buildMatchedSystemPrompt()`（:182，用 baseSystemPrompt）。
- 现状：未约束「分阶段索取材料 / 犹豫时先信任」，AI 自由发挥时可能主动开口要全套材料。→ 加约束支撑 I-1/I-2。
- 注意：`baseSystemPrompt()` 同时被 QA_MATCHED 系统提示复用（:183）；改 base 会同时影响两模式。分阶段约束应加在 `defaultFreeFormSystemPrompt()`（仅 free-form），不要污染 base。

---

## 实现方案

### 阶段 1：QA 规则分层与资金量化（子系统 1）

**Task 1.1 — 新迁移 `V57__qa_material_tiering_and_funding.sql`**（遵守 I-1/I-3/I-5/I-6）
- (a) `UPDATE ... WHERE reply_subject='Program overview'`：改写 `reply_body`（I-5 自洽）——两类项目 + 量化资金（3M–12M RMB / ¥3,000,000–¥12,000,000 + 企业发薪 + 全职房补）+ 轻问材料引导（CV/专利/论文，一句）+ 保密免费；**删除**「passport, doctoral degree certificate, proof of employment, supporting certificates」清单句（I-1）。段落 `\n\n`（I-6）。
- (b) `UPDATE ... WHERE reply_subject='About the talent program'`：两类项目细节 + 量化资金（I-3）。
- (c) `UPDATE ... WHERE reply_subject='Application criteria'`：删材料清单，仅保留资格（副教授+/成果/可贡献产业），末句降门槛「we can discuss fit first — no documents needed at this stage」（I-1）。
- (d) `UPDATE ... WHERE reply_subject='Funding support'`：补量化金额 + 企业发薪 + 全职房补（I-3）。
- (e) `INSERT` 新规则 `Getting started materials`（I-2）：
  - `category_id` = PROGRAM_AND_ELIGIBILITY；`section_title`='Program & eligibility'（与同类一致，V40）。
  - `keywords`（ANY）：`what documents,materials needed,cv,what to send,provide,what do you need,send my documents,what should i send`（确认不与 OVERVIEW 关键词冲突；OVERVIEW 无 documents 词）。
  - `priority`=35（介于 criteria 与 role 之间，供人工组装排序参考）。
  - `reply_body`：CV / 专利证明 / 发表论文清单，逐项「为什么需要」+ 保密·免费·可脱敏，多段 `\n\n`。**不含任何重资料**（I-1）。
  - `display_name`（中文，如「轻问材料」）用 `CONVERT(UNHEX('<utf8hex>') USING utf8mb4)`（I-6）。
  - `auto_reply_enabled=1, handoff_required=0, enabled=1, supersedes_children=0`。
- **不**新增重资料自动规则：重资料走既有 `MATERIAL_REMINDER`(V56) + `Passport and document reluctance`(V52) + 人工组装（I-1）。

### 阶段 2：训练知识与提示词一致化（子系统 2）

**Task 2.1 — 更新 `ai-training/qa-seed.json`**（新库，遵守 I-1/I-2/I-3/I-4）
- 改 `PROJECT_CONTENT` answer：加量化资金（I-3）。
- 改 `APPLYING_CRITERIA` answer：**删重资料**，改为资格描述（I-1）。
- 改 `SALARY` answer：保持/强化量化（已 3-12M，确认含企业发薪 + 房补）。
- 新增条目 `sourceRef='MATERIALS_LIGHT'`：轻问三件 + 逐项理由 + 保密免费（I-2），与 Task 1.1(e) 文案一致。

**Task 2.2 — 新迁移 `V58__update_ai_training_qa_material_tiering.sql`**（既有库，遵守 I-4）
- `UPDATE ai_training_qa SET answer=... WHERE source='MANUAL_IMPORT' AND source_ref IN ('PROJECT_CONTENT','APPLYING_CRITERIA','SALARY')` —— 与 Task 2.1 文案逐字一致。
- `INSERT ... 'MATERIALS_LIGHT' ... ON DUPLICATE KEY` 或先 `SELECT` 存在性守卫（沿用 seeder 的 source/sourceRef 幂等语义），避免与 seeder 双插冲突。字面量 ASCII-only。

**Task 2.3 — 更新 free-form 默认提示词** `AiPromptConfigService.kt`（`FreeFormPromptDefaults.defaultFreeFormSystemPrompt`，支撑 I-1/I-2）
- 在 `defaultFreeFormSystemPrompt()`（**不改 baseSystemPrompt**，避免污染 QA_MATCHED）追加约束：
  - 「Request materials in stages: at an early stage ask only for CV, patent certificates, and a publication list, and explain why each is useful.」
  - 「Never request passport, degree certificate, or employment proof in an early auto reply; those come later, after a call or clear interest.」
  - 「If the expert shows hesitation or distrust, lead with confidentiality, no-fee assurance, and evidence of government cooperation, and offer a low-commitment next step.」

### 阶段 3：文档

**Task 3.1 — `docs/qa提炼-完整版.md`**：在第二部分补一条结论「材料分阶段：轻问(CV/专利/论文, 自动) vs 重资料(护照/学位证/在职证明, 人工)」，并标注对应规则 `Getting started materials` / `MATERIAL_REMINDER`。
**Task 3.2 —（可选）`docs/releases.json`**：加一条发布说明。

---

## 变更文件清单

| # | 文件 | 动作 | 关联不变量 |
|---|------|------|-----------|
| 1 | `src/main/resources/db/migration/V57__qa_material_tiering_and_funding.sql` | 新增（改 4 条 + 插 1 条 qa_rule） | I-1,I-3,I-5,I-6 |
| 2 | `src/main/resources/db/migration/V58__update_ai_training_qa_material_tiering.sql` | 新增（UPDATE 3 + INSERT 1 ai_training_qa） | I-1,I-2,I-3,I-4 |
| 3 | `src/main/resources/ai-training/qa-seed.json` | 改 3 条 + 增 1 条 | I-1,I-2,I-3,I-4 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt` | 改 `defaultFreeFormSystemPrompt()` | I-1,I-2 |
| 5 | `docs/qa提炼-完整版.md` | 补分阶段结论 | I-1,I-2 |
| 6 | `docs/releases.json` | （可选）发布说明 | — |
| 7 | `src/test/kotlin/.../llm/service/AiTrainingQaSeederTest.kt` | 按新 seed 数量/内容调整断言 | I-2,I-4 |
| 8 | `src/test/kotlin/.../qa/service/QaMatchServiceRestructureTest.kt` | 若断言了旧材料文案/规则数则调整 | I-1 |
| 9 | `src/test/kotlin/.../llm/service/AiPromptConfigServiceTest.kt` | 若断言默认提示词文本则调整 | I-1,I-2 |

（≤10 文件；2 子系统：QA 规则 / AI 训练+提示词。文档与测试随附。）

---

## 验收标准

- **I-1**：`rg -i "passport|degree certificate|proof of employment"` 在 `V57`、`qa-seed.json`、`V58` 的**自动**规则/条目正文中**零命中**（仅允许出现在 handoff 规则/`MATERIAL_REMINDER`）。DB 查询：所有 `auto_reply_enabled=1` 的 `qa_rule.reply_body` 不含护照/学位证/在职证明。
- **I-2**：新规则 `Getting started materials` 正文含 CV、patent、publication 三项且各带理由句 + 保密/免费/脱敏；`MATERIALS_LIGHT` 训练条目文案与之一致。
- **I-3**：`Program overview`/`About the talent program`/`Funding support` 及训练 `PROJECT_CONTENT`/`SALARY` 均含「3–12 million RMB」量化 + 企业发薪 + 全职房补。
- **I-4**：`V58` 对 `PROJECT_CONTENT/APPLYING_CRITERIA/SALARY` 有 UPDATE、对 `MATERIALS_LIGHT` 有幂等 INSERT；数量与 `qa-seed.json` 编辑一一对应（无遗漏）。
- **I-5**：模拟一封「learn more / more information」概览来信，`QaMatchService.match()` 命中 `Program overview` 且 `supersede` 后单条外发，正文自洽含项目+资金+轻问材料引导，`gapDetected=false`。
- **I-6**：`V57` 中文 `display_name`/`section_title` 用 `CONVERT(UNHEX(..))`，字面量 ASCII-only；插库后前端显示无乱码。
- **集成**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿；Flyway `V57/V58` 在测试库成功执行。
- **回归**：`Passport and document reluctance`(V52) 规则语义不变（`auto=0/handoff=1`）。

---

## Self-Review Checklist
- [x] 关键不变量存在，每个新字段/规则/条目均有对应不变量（I-1..I-6）
- [x] 现状审计列出 `qa_rule`/`ai_training_qa` 全部 write/read path（grep 核实，非记忆）
- [x] 无任务引入未被不变量覆盖的写路径（V57/V58 为唯一写入点）
- [x] 文件数 ≤ 10（9）
- [x] 子系统 ≤ 2（QA 规则 / AI 训练+提示词）
- [x] 每个任务引用其治理不变量编号
- [x] 验收标准每不变量 ≥1 检查
- [x] 文件清单无「等相关文件」——逐一命名
- [x] Out-of-scope 显式延后（首邮模板、会议/优势规则、UI、算法）
- [x] Phase 0 知识均被使用或有意识引用（K-overview-gap-supersede→I-5，K-prompt-config→Task2.3，K-ai-reply-prompt-vs-send→不改契约，K-plaintext-reflow→I-6）
- [x] 计划保存到 `docs/plans/2026-07-02/`
