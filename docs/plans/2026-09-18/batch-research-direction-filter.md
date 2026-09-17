# 批量任务研究方向有/无/不限筛选

本计划在 `template-placeholder-gate-crud.md` 后执行；跨计划约束见同目录 `00-university-email-template-main.md`。目的仅是让用户自行新建任务时可明确选“无”，筛选入库后仍缺研究方向的人；本地 500 人名单不等于线上命中人数。不自动创建任务、不导入或修改专家数据。

## 需求描述

批量任务配置和手动执行界面均增加“研究方向：不限 / 有 / 无”；保存、回显、复制配置、执行前人数预估及实际执行使用同一值。“无”筛出 ES 中 `researchFields` 不存在、null 或空字符串的人；“有”为已有 `fieldPresenceFilter("researchFields")` 的补集。“未知”研发类型仍是独立条件，用户后续自行设置。

必须保持：旧任务默认“不限”、原研发类型筛选、现有按模板门禁、其他筛选及手动覆盖差异提示。范围外：500 人上传/分类、自动选择 UNKNOWN、新模板或任务创建、ES 映射/回填、专家搜索页新增“无”按钮、全局研究方向清洗。

## 关键不变量

### Invariant I-1：一个持久化三态字段
- Rule：`batch_send_task_config.research_direction_filter` 只取 `ANY/PRESENT/ABSENT`；旧行及未传值为 `ANY`，非法值在保存/启动时拒绝。任务配置写、读、快照、手动覆盖均原样传递，旧 typed API 更新不得重置。
- Applies to：迁移、`BatchSendTaskConfig`/commands/view、`BatchSendTaskConfigService`、`BatchExecutionSnapshot`、`BatchSendControlService`、前端表单。
- Violation consequence：用户选“无”后执行变成不限，或旧任务人群改变。
- 来源：original。

### Invariant I-2：ES、重试对象与预估同口径
- Rule：`ANY` 不加查询；`PRESENT` 使用既有 `ExpertSearchService.fieldPresenceFilter("researchFields")`；`ABSENT` 对该 filter 做 bool.must_not。内存重试路径按同一有效有值判断（null/空串＝无，其他＝有）。人数预估与执行均通过 `ManualInitialOutreachService.resolveScope` 和同一 `RecipientScope`，不可仅改一条路径。ES 中纯空格字符串按当前 keyword/`term ""` 规则算“有”；本计划不重定义存量数据清洗。
- Applies to：`ManualInitialOutreachService` 的 ES 计数/扫描/执行过滤、`BatchExecutionModels.RecipientScope.matchesExpert`。
- Violation consequence：预估与实发人数不一致，或错误纳入/排除缺方向专家。
- 来源：original。

### Invariant I-3：方向筛选与类型/模板门禁独立取交集
- Rule：“无研究方向”与 `UNKNOWN`、`UNCLASSIFIED` 不等价；`UNKNOWN` 要求 `expertClassification.type=UNKNOWN`，未写分类字段须选 `UNCLASSIFIED`。类型、方向、模板门禁同时指定时为 AND。方向为 `ABSENT` 且模板要求无默认值 `${primaryResearchField}` 时结果可为 0，不得绕过发送端门禁。
- Applies to：`RecipientScope`、ES 过滤、前端提示和预估。
- Violation consequence：把未分类误认为未知，或给缺必填变量的人发信。
- 来源：original。

## 样式契约

### S-1：任务配置下拉
- 复用：`styles.css:5356-5379` 的 `.bsc-input/.bsc-select`，`styles.css:9065-9084,9282-9315` 的 `.batch-config-field/.batch-config-field-label`；不改 CSS、不新增 class。
- DOM 结构：复制 `index.html:1277-1286` 既有“学科” `<label class="batch-config-field"><span class="batch-config-field-label">…</span><select class="bsc-input bsc-select">…</select></label>` 结构，仅用新 id `batchConfigEditorResearchDirectionFilter` 与三个 option；手动页复制 `index.html:1481-1492` 的 `label#manualFieldDiscipline` 结构，id 为 `manualFieldResearchDirectionFilter`，保留 `.batch-config-diff-badge/.batch-config-diff-original`。
- 新增：无 CSS 规则；所有新增 DOM 元素只用上述既有 class。
- 禁止项：inline style、未声明 class、就地修改现有 class。

## 现状审计

### `batch_send_task_config`（MySQL）
- Schema：`V72__create_batch_send_task_config.sql:1-33` 建表，有模板外键；`V108` 增 `expert_types_json`，`V110` 回填三种研发类型；`V99` 增 `gate_filter_enabled`。当前最高迁移号 V127，本计划新迁移 V128，单一新字段，不改现有列。
- 运行时写路径：`BatchSendTaskConfigService.kt:62-163` create/update/setEnabled/softDelete；`updateLegacyConfig:169-215` 用旧 typed API 组装 update command，必须显式保留新值。已存在的迁移写路径 `V72/V74/V91/V93/V97/V98/V99/V108/V110` 已由 `rg 'batch_send_task_config' src/main/resources/db/migration` 核对，只读审计、不改。
- 读路径：`BatchSendTaskConfigService.kt:40-59,465-490` list/get/view；`BatchExecutionModels.kt:260-325` `toExecutionSnapshot`；`BatchSendControlService.kt:55-160,255-265` 自动/手动启动；`ManualInitialOutreachService.kt:426-471,490-515` 预估与执行；`app.js:15930-15980,16700-17160` 回显、编辑、预估、手动快照及差异提示。
- 交互点：UI 保存→MySQL→回显；MySQL→定时快照→收件筛选；手动覆盖→快照→预估/执行；旧 typed API 更新→MySQL 保值。

### `BatchExecutionSnapshot` / `RecipientScope`（进程内数据）
- Schema：`BatchExecutionModels.kt:10-27,54-145`；当前 `gateEsFields` 只表达“有字段”，没有“无研究方向”语义。`toExecutionSnapshot:260-325` 将持久配置传快照；`RecipientScope.fromSnapshot:133-152` 将快照传筛选对象。
- 写路径：持久配置 `toExecutionSnapshot`、前端手动 JSON、`BatchSendControlService.toLegacySnapshot:569-588`、`ManualInitialOutreachService.toSnapshot:1328-1351`。后两条 legacy 构造不携带新值，默认 `ANY`。
- 读路径：`BatchSendControlService.validateSnapshotFields:417-438`；`ManualInitialOutreachService.resolveScope/countBySnapshot/buildFilters/buildRetryableTargets`；`RecipientScope.matchesExpert` 用于重试人群。
- 交互点：保存配置→定时/手动执行；手动覆盖→预估与运行；ES 扫描→内存重试，必须相同。

### 专家 ES（仅查询）
- Mapping：`src/main/resources/es/orcid_info_candidate.json:1-50` 的 `dynamic:false`、`researchFields:keyword`；`orcid_info_application.json:36` 同字段。无需映射变更。
- 相关写路径（不改）：`ExpertDiscoveryService.kt:1135` 形成 `researchFields`、`ExpertIndexWriterService.writeCandidateDocument` 写候选文档；脚本 `scripts/expert_discovery/enterprise_batch/import_candidate_es.py`、`import_es_documents.py`、`import_personal_email_candidates.py`、`scripts/import_contactout_visible_candidates.py` 导入，以及 `scripts/build_sbir_research_fields_update.py`、`scripts/expert_discovery/enterprise_batch/update_es_research_fields.py` 更新。按 `rg -l 'researchFields' src/main/kotlin scripts` 复核；新字段只在任务 MySQL，不写专家 ES。
- 读路径：`ExpertSearchService.kt:35-77` 的 `fieldPresenceFilter` 对 keyword 做 `exists AND NOT term ""`；`ManualInitialOutreachService.kt:1259-1324` 计数/扫描；`RecipientScope.matchesExpert` 重试匹配。
- 专家类型独立：`ExpertSearchService.kt:93-145` 把 `UNKNOWN` 和字段不存在的 `UNCLASSIFIED` 区分，`app.js:16368-16382` 分别提供选项。500 人源文件无 `expertClassification` 字段，不能在本计划声称线上 500 人已命中 `UNKNOWN`。

### 前端样式盘点
- 可复用 class 和设计基准：S-1 所列；主色 `#1e40af`、弱文本 `#94a3b8`、边框 `#cbd5e1`、小圆角 `7px` 见 `styles.css:1-76`，暗色 token 覆盖见 `styles.css:9706-9745`。下拉直接复用 `.bsc-input/.bsc-select`，没有新色值/状态规则。
- DOM 结构约定与改动前基线：`index.html:1277-1286` 保存配置的“学科” select；`index.html:1481-1492` 手动页同字段附差异 badge。对应逐字 CSS 在 `styles.css:5356-5379,9065-9084,9282-9315`。只追加同骨架字段。

## 实现方案

1. 存储和快照（I-1/I-3）：新迁移 `V128__add_research_direction_filter_to_batch_send_task_config.sql` 增 `VARCHAR(16) NOT NULL DEFAULT 'ANY'`（若现有 MySQL 版本不支持约束，服务层白名单为权威）；`BatchSendTaskConfig.kt` 的 entity/view/create/update 各加一个字段；`BatchSendTaskConfigService.kt` 在所有 create/update/toView/updateLegacyConfig 路径传值并校验三态；`BatchExecutionModels.kt` 的快照、`toExecutionSnapshot`、`RecipientScope.fromSnapshot` 传值，默认 ANY。
2. 收件人选择（I-2/I-3）：`ManualInitialOutreachService.kt` 在共用 `resolveScope` 后对 ES filter 增 PRESENT/ABSENT 分支，使用现有 `fieldPresenceFilter("researchFields")` 及其 bool.must_not；`BatchExecutionModels.kt` 中 `matchesExpert` 对重试对象同口径过滤。`BatchSendControlService.kt` 的手动启动校验三态；模板门禁与类型继续按 AND，预估与执行复用同一路径。
3. 界面（I-1/I-3/S-1）：`index.html` 在配置/手动页各加一个已有样式的 select：不限(ANY)、有(PRESENT)、无(ABSENT)。`app.js` 补全配置回显、创建默认值、保存 payload、克隆草稿、手动快照、预估快照、差异值展示；文案说明“研发类型未知”和“无研究方向”互不等价。
4. 测试（I-1 至 I-3）：`ManualInitialOutreachServiceTest.kt` 覆盖三态在 ES 预估/实际及内存重试的一致性；`batchSendTaskConsoleInteraction.test.js` 覆盖配置/手动/差异/预估/保存往返。运行既有 `BatchSendTaskConfigServiceTest`、`BatchSendTaskRuntimeIntegrationTest` 回归。

## 变更文件清单

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql` | 三态字段与旧行默认 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | Entity、view、commands |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 全写/读路径及白名单 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 快照、scope、内存匹配 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 手动快照启动校验 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | ES 预估/执行筛选 |
| 7 | `src/main/resources/static/app.js` | 配置和手动页数据闭环 |
| 8 | `src/main/resources/static/index.html` | 两处三态下拉 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | ES/重试/预估路径 |
| 10 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 前端保存、回显、差异 |

## 验收标准

- I-1：迁移后旧任务值均为 `ANY`；新任务选 `ABSENT` 保存/list/get/编辑/定时快照仍为 `ABSENT`；旧 typed API 更新不清除；非法值在保存及手动启动被拒。
- I-2：ES `PRESENT` 精确等于现有 `fieldPresenceFilter("researchFields")`；`ABSENT` 为其 bool.must_not；null/空串在 ES 和重试对象都进“无”，非空串进“有”。同一快照预估人数与执行收件筛选相同。
- I-3：`UNKNOWN`、`UNCLASSIFIED` 与三态分别组合测试；`ABSENT + 模板无默认值 primaryResearchField 门禁开启` 不允许任何缺值者越过发送端；旧任务 `ANY` 不额外加方向过滤。
- S-1：新增两处 DOM 只含契约所列既有 class，CSS diff 为空，无 inline style；下拉在普通/暗色主题下与“学科”一致。
- 运行：所列 Kotlin/JS 定向测试及既有配置服务/运行时集成测试，`git diff --check`。

## 人工验收清单

### A-1：保存和回显
- 前置条件：测试环境有一条介绍邮件任务，未启动；候选库有方向空与方向非空各一人。
- 操作步骤：1. 编辑该任务，选“研究方向：无”，保存；2. 刷新页面重开；3. 切到手动执行页载入该任务；4. 改为“有”，查看差异提示和预估。
- 预期结果：刷新后仍显示“无”；手动页初值为“无”；改“有”出现该字段差异；预估切换到有方向人群，未修改原任务。
- 覆盖：I-1/I-2/S-1，UI→DB→回显→快照。

### A-2：有/无/不限与独立类型
- 前置条件：候选库准备 3 个同筛选条件专家：甲 `researchFields` 缺失、`expertClassification.type=UNKNOWN`；乙 `researchFields=""`、无分类字段；丙 `researchFields="AI"`、`type=UNKNOWN`；均有可用邮箱且未联系。关闭自动发信，只看预估或用不实际 SMTP 的测试环境。
- 操作步骤：1. 类型选“未知”，方向依次选“无/有/不限”看预估；2. 类型改“未分类”、方向选“无”看预估；3. 开启模板门禁并选择 `${primaryResearchField}` 无默认值模板看预估。
- 预期结果：步骤 1 分别计甲/丙/甲+丙；步骤 2 计乙；步骤 3 缺方向者为 0。`UNKNOWN` 与 `UNCLASSIFIED` 不混淆。
- 覆盖：I-2/I-3，类型 ES 读→方向 ES 读→模板门禁。

### A-3：旧任务与启动回归
- 前置条件：一条迁移前存在的任务、一个未修改的旧介绍模板。
- 操作步骤：1. 打开任务；2. 保持“不限”做预估；3. 手动执行页另选“无”但不保存任务；4. 再打开原任务。
- 预期结果：旧任务为“不限”，预估无额外方向过滤；手动覆盖只作用本次快照；原任务仍为“不限”，既有研发类型与模板选择不变。
- 覆盖：I-1/I-3、must-not-change。
