# AI 训练对话范例治理与 Grounded 模式接入

> 日期：2026-07-12  
> 顺序：计划 3/3；依赖 `ai-reply-grounded-parity-backend.md` 已提供 `QA_GROUNDED`。  
> 决策：对话范例有中等参考价值，只用于结构、语气和沟通策略；QA/训练知识/已有画像才是事实来源。

## 需求描述

Observable outcomes:

1. AI 训练页只启用 6 个短小、去事实化、按场景标注的 style few-shot；现有 10 个包含姓名、年份、金额、护照、政策承诺等长历史对话保留可见但全部标记为停用，便于审计。
2. `FREE_FORM` 最多注入 2 个 style 范例；`QA_GROUNDED` 最多注入 1 个；`QA_MATCHED` 与所有 deterministic fallback 永不注入。
3. 范例只能影响“逐项回答、区分已确认/待确认、资料不足坦诚说明、避免过早索取资料”等方式，不能成为公司、项目、资金、合同、政策事实来源。
4. 新库与存量库最终启用的 sourceRef、keywords、turnsJson 完全一致：JSON seed 服务新库，Flyway V69 更新存量库；不能只改 JSON。
5. AI 训练页明确显示“仅用于结构、语气与沟通策略，不作为事实来源”，并把“关键词”列改称“触发短语”。

What must NOT change:

- `ai_training_qa`、`qa_rule` 和 prompt-config 内容/CRUD 不变。
- `AiTrainingDialogueService` 的 enabled 过滤、关键词评分、单范例 2500 字符和总计 6000 字符上限不变。
- `qaRuleIds`、mode、覆盖元数据和发送审计语义不变。
- 不增加对话范例在线编辑/删除/启停接口；本轮仍为审核后随版本发布。
- 不把真实专家姓名、完整历史通信或身份材料重新放进新范例。

Out of scope:

- 向量检索、embedding、reranker。
- 自动从历史邮件抽取或上线范例。
- A/B 测试、质量评分平台。
- 对话范例管理后台 CRUD。

## 关键不变量

### Invariant I-1: 范例不是事实源
- Rule: system boundary 必须明确 few-shot 仅供结构/语气；凡金额、名称、地址、链接、期限、职责、承诺、政策等事实只能来自本次 QA/training/profile context。范例与事实库冲突或事实库缺失时必须忽略范例事实。
- Applies to: `buildFewShotBoundaryNote`、FREE_FORM、QA_GROUNDED。
- Violation consequence: 历史年份/金额/材料要求污染当前回复。
- 来源: K-ai-generate-single-freeform-seam / K-training-knowledge-injection-points

### Invariant I-2: 模式注入边界
- Rule: FREE_FORM `max=2`; QA_GROUNDED `max=1`; QA_MATCHED `max=0`; LLM disabled/exception fallback `max=0`。`fewShotDialogRefs` 必须准确反映实际加入 messages 的范例。
- Applies to: `AiReplyDraftService.buildFreeFormMessages/buildGroundedMessages/fallback`。
- Violation consequence: verbatim QA 被范例改写，或 fallback 响应虚报注入。
- 来源: K-ai-generate-single-freeform-seam

### Invariant I-3: 六个启用场景集合固定
- Rule: 本计划完成后启用 sourceRef 只能是 `STYLE_MULTI_DUE_DILIGENCE`、`STYLE_PROFILE_CONTEXT_GAP`、`STYLE_TRUST_VERIFICATION`、`STYLE_CONTRACT_BOUNDARY`、`STYLE_PROCESS_NEXT_STEPS`、`STYLE_MATERIALS_BOUNDARY`；每条恰好 2 turns（EXPERT → AGENT）。
- Applies to: seed JSON、V69、seeder/migration tests。
- Violation consequence: 新旧环境范例集合漂移，或长会话再次进入 prompt。
- 来源: original

### Invariant I-4: 范例正文去事实化
- Rule: 六条 turns 文本禁止出现 `20xx` 年份、数字金额/百分比、`RMB/USD/CNY/million`、真实公司/专家名、`government/national-level/success rate`、`passport/degree certificate`、具体访问次数/期限、`salary/travel expenses/no fees/strictly confidential` 等业务事实；每条 AGENT 文本 <= 700 字符。
- Applies to: seed JSON、V69 turns_json。
- Violation consequence: few-shot 重新成为未经治理的隐形事实库。
- 来源: original

### Invariant I-5: 新库与存量库同源
- Rule: seeder 对已存在 sourceRef 仍“存在即跳过”；因此任何 curation 必须同时修改 JSON 和新增 V69。V69 先停用全部 `DIALOG_%` legacy，再对六条 style ref 使用幂等 upsert 并 `enabled=1`。
- Applies to: `dialogue-seed.json`、V69、`AiTrainingDialogueSeeder` 既有行为。
- Violation consequence: 测试/新库已更新，线上旧库继续注入旧范例。
- 来源: 类比 K-ai-training-seed-idempotent-skip，经本次审计确认 dialogue seeder 同样 skip

### Invariant I-6: UI 对用途诚实
- Rule: 对话 tab 必须显示 style-only 说明；不得再只写“来源：聊天记录5.27”，使运营误以为历史内容可作为事实训练材料。
- Applies to: `index.html`、`renderAiTrainingDialogueTable`。
- Violation consequence: 后续维护者继续导入长真实对话，破坏 I-1/I-4。
- 来源: original

## 样式契约

### S-1: 对话范例 panel 文案与表格（纯复用）
- 复用：`#aiTabDialogues .panel.ai-training-panel`、`.panel-head`、`.toolbar-inline`、`.muted`、`.table-wrap`、`.muted-cell`、`.badge` 现有 class（`index.html:810-828`; `styles.css:6403-6427` 及通用 table/badge 规则）。
- 新增：无 CSS、无新 class。
- DOM 结构必须改为：

```html
<div class="ai-tab-content" id="aiTabDialogues">
    <section class="panel ai-training-panel">
        <div class="panel-head">
            <h2>回复方式范例（few-shot）</h2>
            <div class="toolbar-inline">
                <span class="muted">仅用于结构、语气与沟通策略；不作为公司、项目、资金或合同事实来源</span>
                <button class="button" id="reloadAiTrainingDialoguesBtn">刷新</button>
            </div>
        </div>
        <div class="table-wrap">
            <table>
                <thead>
                <tr><th>编号</th><th>场景</th><th>触发短语</th><th>轮数</th><th>状态</th></tr>
                </thead>
                <tbody id="aiTrainingDialogueTable"></tbody>
            </table>
        </div>
    </section>
</div>
```

- 禁止项：inline style；新增 class；修改任何既有 class CSS；新增 tab/面板（因此不触发 K-ai-subtab-whitelist-mapping 的注册改动）。

## 现状审计

### `ai_training_dialogue` MySQL 表
- Schema/mapping: V66 创建 `id/title/source_ref UNIQUE/keywords/turns_json MEDIUMTEXT/enabled/created_at/updated_at`。
- Write paths:
  1. `AiTrainingDialogueSeeder.run()` 读取 `dialogue-seed.json`，仅 sourceRef 不存在时 save；存在即 skip。
  2. V66 建表；目前无后续内容迁移、无 Controller 写接口。
- Read paths:
  1. `AiTrainingDialogueService.listViews()` 全量读取供 UI。
  2. `selectRelevantDialogues()` 只读 enabled rows，按逗号关键词 substring 命中数降序、id 升序，默认 max=2。
- Interaction points: 单改 JSON 对线上已播种 rows 无效；必须通过 V69 更新/停用存量数据。

### 当前 seed 内容
- 10 条，6–23 turns；最长 `DIALOG_1095` 23 turns/3015 字符，超过单范例 2500 字符后会被截断，可能留下不完整对话边界。
- 内容含真实专家姓名、2025/2026 时间、3–12 million RMB、success rate、passport、VCR、travel/salary 等事实或阶段性要求。
- 关键词覆盖 funding/passport/video 等事实主题，选中后虽有 boundary note，仍会对措辞和事实产生强锚定。
- UI 只展示 ref/title/keywords/turnCount/enabled，不展示“style-only”边界。

### `AiReplyDraftService` 范例消费
- FREE_FORM `buildFreeFormMessages:171` 调 `selectRelevantDialogues(inboundText)`，最多 2 条；加入 system 后、真实 user 前。
- QA_MATCHED 明确 `fewShotDialogRefs=emptyList`，已有测试锁定。
- 计划 1 新增 QA_GROUNDED，但明确暂不注入，等待本计划完成 curation。
- fallback 不调用 dialogue service。
- Interaction points: 本计划只在已治理数据上线后开放 grounded max=1；不能提前改变模式边界。

### 前端样式盘点
- 可复用 class/DOM: `index.html:810-828` 已有 panel/table 全结构；`app.js:2531-2541` 按五列渲染，无编辑操作。
- 设计 token: 本计划零视觉 token 新增或修改。
- 改动前基线: heading=`对话范例（few-shot）`;说明=`来源：聊天记录5.27 · 启动自动播种`;列名=`编号/标题/关键词/轮数/状态`。

## 实现方案

### T1：建立六条 style-only seed 内容（I-3/I-4/I-5）
文件：`src/main/resources/ai-training/dialogue-seed.json`

- 删除 10 条 legacy seed，写入以下六条；每条 turns 只能为一问一答：

| sourceRef | title | keywords（逐字） | AGENT 行为契约 |
|---|---|---|---|
| `STYLE_MULTI_DUE_DILIGENCE` | `Style — multi-question due diligence` | `further information,registered location,responsibilities,deliverables,intellectual property,next stages` | 感谢列明问题；承诺按原顺序处理；明确区分已确认信息与依赖企业匹配/书面协议的待确认项；缺依据不猜测；不以索要 CV 代替回答。 |
| `STYLE_PROFILE_CONTEXT_GAP` | `Style — insufficient research context` | `research profile,research background,areas of expertise,google scholar,scopus,within the scope` | 感谢链接；只依据系统已有研究资料；不足时明确暂不能确认匹配，说明需先补齐现有记录；不得声称访问外部链接。 |
| `STYLE_TRUST_VERIFICATION` | `Style — verification before progression` | `legitimate,verify,company registration,registered location,official website,who are you` | 认可核验请求；先回答可验证身份信息；无法提供的证据标待确认；在回答前不催材料/会议。 |
| `STYLE_CONTRACT_BOUNDARY` | `Style — contract and IP boundaries` | `contractual,financial arrangements,intellectual property,compensation,ip rights` | 将已确认的一般流程与需未来合同确定的条款分开；不编造金额、权属或承诺；说明具体条款需书面确认。 |
| `STYLE_PROCESS_NEXT_STEPS` | `Style — explicit next-step sequence` | `next stages,next steps,selection process,application process,timeline` | 用短编号给出当前步骤、下一步骤、何时提供待定信息；未知时间不猜日期。 |
| `STYLE_MATERIALS_BOUNDARY` | `Style — staged material request` | `what should i provide,what materials,materials needed,cv,documents` | 先回答当前阶段真正需要的最小材料及用途；不扩展到未要求的敏感材料；允许专家先取得项目信息。 |

- 六条 AGENT 文本必须逐字采用以下内容，禁止执行时自行扩写：
  1. `STYLE_MULTI_DUE_DILIGENCE`: `Thank you for setting out the questions clearly. I will address them in the same order and distinguish confirmed information from points that depend on a future enterprise match or written agreement. If the approved information does not support a requested detail, I will mark it for confirmation instead of making an assumption or replacing the answer with a request for your CV.`
  2. `STYLE_PROFILE_CONTEXT_GAP`: `Thank you for sharing those links. At present, I can assess fit only from the research information already available in our records. That information is not sufficient to confirm a match, so I cannot give you a reliable assessment yet. I also do not want to imply that the external profiles have been reviewed when they have not.`
  3. `STYLE_TRUST_VERIFICATION`: `That is a reasonable request. Before asking you to proceed, I will provide the legal identity, registered location, and verification channels that are present in our approved information. Any item not available there will be identified for confirmation rather than replaced with a request for documents or a meeting.`
  4. `STYLE_CONTRACT_BOUNDARY`: `Thank you for raising these points. I will separate the general arrangement that is already confirmed from compensation, ownership, and other terms that depend on a later written agreement. I will not infer specific rights or commitments that are not present in the approved information.`
  5. `STYLE_PROCESS_NEXT_STEPS`: `I will set out the process as a short sequence: what happens now, what follows after review, and when any currently unavailable information will be provided. Where no confirmed date is available, I will describe the dependency instead of inventing a deadline.`
  6. `STYLE_MATERIALS_BOUNDARY`: `Thank you. I will ask only for the minimum material needed at the current stage and explain its purpose. I will not expand the request to sensitive documents that are not required, and I will answer your programme questions before asking you to send anything.`
- 六条 EXPERT 文本也必须逐字采用：
  1. `Before proceeding, could you explain your company registration, programme purpose, selection and matching process, responsibilities, contract and IP arrangements, and next steps?`
  2. `Please review my Google Scholar and Scopus profiles and confirm whether my research background fits the enterprise projects you manage.`
  3. `Before I proceed, how can I verify your company identity, registered location, and official channels?`
  4. `Could you explain the contractual, financial, compensation, and intellectual-property arrangements?`
  5. `What are the selection process, next steps, and expected timeline?`
  6. `What materials do you need from me at this stage, and why are they needed?`
- turns 不得出现模板占位符或方括号；内容测试按 I-4 禁词和长度验收。

### T2：V69 同步存量数据库（I-3/I-4/I-5）
文件：`src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql`

- `UPDATE ai_training_dialogue SET enabled=0 WHERE source_ref LIKE 'DIALOG_%';`
- 对六个 `STYLE_*` 分别使用列集 `title, source_ref, keywords, turns_json, enabled` 执行 INSERT，并追加 `ON DUPLICATE KEY UPDATE title=VALUES(title), keywords=VALUES(keywords), turns_json=VALUES(turns_json), enabled=1, updated_at=CURRENT_TIMESTAMP`；每条 VALUES 内容逐字取 T1 契约文本并做 SQL 单引号转义。
- SQL turns_json 与 JSON seed 语义和文本逐字一致；SQL 仅 ASCII 文本，避免历史编码问题。
- 不删除 legacy rows，保留审计/回滚可见性；UI 会显示其“停用”状态，但服务只选择六条 enabled。

### T3：在 QA_GROUNDED 安全接入范例（I-1/I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- `buildGroundedMessages` 调 `selectRelevantDialogues(inboundText, max=1)`；FREE_FORM 显式传 `max=2`。
- 统一 boundary note 为：范例仅示范 structure/tone/communication strategy；不得复制事实；所有 factual claims 只能来自当前 QA rule knowledge、training knowledge、existing expert profile；缺失时必须标待确认。
- QA_MATCHED 分支、fallback 分支继续 zero interaction；`fewShotDialogRefs` 只列真正加入的 sourceRef。

### T4：修正训练页用途说明（I-6/S-1）
文件：`src/main/resources/static/index.html`、`src/main/resources/static/app.js`

- `index.html` 严格替换为 S-1 DOM 文案和列名。
- `renderAiTrainingDialogueTable` 保持五列结构，第二列仍取 title，第三列仍取 keywords；空状态 `colspan=5` 不变。
- 不增加编辑按钮，不修改 tab 映射或 load Promise。

### T5：数据和模式测试（I-1 至 I-6）
文件：

- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueSeederTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueCurationTest.kt`

断言：

1. JSON 恰好六个指定 refs；每条 2 turns 且角色顺序 EXPERT/AGENT；AGENT <=700；turn text 不含 I-4 禁词/年份/金额模式。
2. V69 包含 legacy disable 和六 refs upsert；解析 SQL 中每条 turns_json 与 JSON 对应文本一致（至少 sourceRef/title/keywords/两段 text 逐项包含）。
3. seeder 新库插入第一条为 `STYLE_MULTI_DUE_DILIGENCE`，总 save 次数 6；已有 refs 全部 skip。
4. QA_GROUNDED 只注入 1 条并返回 ref；FREE_FORM 最多 2；QA_MATCHED/fallback verifyNoInteractions。
5. boundary note 包含 `structure, tone, and communication strategy` 与 `must not be used as a factual source`。

## 变更文件清单

| # | 文件 | 操作 | 任务 |
|---|---|---|---|
| 1 | `src/main/resources/ai-training/dialogue-seed.json` | 重写 | T1 |
| 2 | `src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql` | 新增 | T2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | T3 |
| 4 | `src/main/resources/static/index.html` | 修改 | T4 |
| 5 | `src/main/resources/static/app.js` | 修改 | T4 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueSeederTest.kt` | 修改 | T5 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | T5 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingDialogueCurationTest.kt` | 新增 | T5 |

文件数：8；子系统：few-shot 数据/生成消费、AI 训练只读展示，共 2。

## 验收标准

- I-1：测试检查 boundary note 与 QA/training/profile facts 限制；人工抽取 prompt 确认 examples 与真实 inbound 边界清晰。
- I-2：模式测试分别断言 refs 数量 2/1/0/0；QA_MATCHED build 函数 diff 无变化。
- I-3：JSON 和 V69 均包含且只启用六 refs；数据库查询 `SELECT source_ref FROM ai_training_dialogue WHERE enabled=1 ORDER BY source_ref` 等于固定集合。
- I-4：curation test 禁词、年份、金额、长度全部通过；不得用停用 legacy rows 逃避 JSON 检查。
- I-5：新库 seeder test save=6；存量迁移 SQL upsert/disable 测试通过；不得编辑 V66。
- I-6：index DOM 与 S-1 逐字一致；styles.css 零 diff；app.js 无新 class/新 tab 注册。
- 集成命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiTrainingDialogueSeederTest,AiTrainingDialogueCurationTest,AiReplyDraftServiceTest,AiTrainingSimulateTest`。

## 人工验收清单

### A-1：训练页用途和集合
- 前置条件：应用 V69 并启动服务。
- 操作步骤：进入 AI 训练 → 回复方式范例。
- 预期结果：标题为“回复方式范例（few-shot）”；说明明确“不作为公司、项目、资金或合同事实来源”；六个 STYLE refs 为启用，原 DIALOG refs 为停用；列名为“触发短语”。
- 覆盖: outcomes 1/5，I-3/I-6，S-1

### A-2：多问题 grounded 注入
- 前置条件：使用用户提供的多项尽调邮件，LLM 开启。
- 操作步骤：生成模拟回复，查看 meta 的 injectedDialogRefs 和回复结构。
- 预期结果：mode=`QA_GROUNDED`;最多一个 ref，优先 `STYLE_MULTI_DUE_DILIGENCE`;回复按原顺序组织；具体公司/项目事实来自 QA，不出现范例中不存在的金额、年份、姓名。
- 覆盖: outcomes 2/3，I-1/I-2

### A-3：画像不足范例
- 前置条件：来信含 Scholar/Scopus 匹配询问，已有研究画像不足。
- 操作步骤：生成回复。
- 预期结果：最多注入 `STYLE_PROFILE_CONTEXT_GAP`;回复明确当前无法确认，不声称访问链接；显示后端资料不足 warning。
- 覆盖: outcome 3，I-1/I-4

### A-4：单一 QA 回归
- 前置条件：来信仅问 application process，命中一条 QA。
- 操作步骤：生成回复并观察 meta。
- 预期结果：mode=`QA_MATCHED`;injectedDialogRefs 为空；QA 原文保持 verbatim。
- 覆盖: must-NOT-change 3，I-2

### A-5：LLM fallback 回归
- 前置条件：关闭 LLM；选择会命中 style 关键词的来信。
- 操作步骤：生成模拟和邮箱回复。
- 预期结果：两入口 fallback 一致；injectedDialogRefs 为空；正文不含任何 legacy/style 示例片段。
- 覆盖: outcome 2，I-2

### A-6：存量升级验证
- 前置条件：数据库已有十条 legacy DIALOG rows，再执行 V69。
- 操作步骤：查询 enabled rows，重启应用触发 seeder，再次查询。
- 预期结果：两次查询都只启用六条 STYLE refs；重启不重复插入、不恢复 legacy。
- 覆盖: outcome 4，I-3/I-5

### A-7：其他训练配置与只读边界回归
- 前置条件：记录 AI 训练 QA 条目数量、prompt-config 内容和一条已有 dialogue row。
- 操作步骤：打开三个训练子页并刷新；检查回复方式范例表操作列。
- 预期结果：QA 数量和 prompt-config 内容不变化；范例表没有新增编辑、删除、启停按钮；刷新仅重新读取数据。
- 覆盖: must-NOT-change 1/4，I-6，S-1

## 修正记录

（执行或复验期间的决策在此追加。）
