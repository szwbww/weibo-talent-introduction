# 高校专家邮件模板与批量筛选主计划

本文件只规定执行顺序、跨计划约束和整体验收，**不是第三份功能开发任务**。逐文件改动和单项验收以两份子计划为准：

1. [模板变量门禁与 CRUD](template-placeholder-gate-crud.md)：先完成模板新增、查看、编辑、启停、删除、预览及占位符决定门禁。
2. [批量任务研究方向有/无/不限筛选](batch-research-direction-filter.md)：再完成配置、手动执行、人数预估和实际收件过滤。

顺序执行并分别验证。两份计划都触及 `app.js`、`index.html`，不得并行编辑；第二份的整体验收使用第一份的 `${primaryResearchField}` / `${primaryResearchField|your research area}` 语义。任一跨计划契约变化，先同步修订本文件和相应子计划。主计划不授权额外业务文件。

## 需求描述

用户最终能够自行创建两套介绍邮件模板及独立批量任务：有个人研究方向时可使用必填变量模板；缺研究方向时可使用带默认值的通用模板，并用批量任务“研究方向：无”选人。邮件模板 CRUD、变量菜单和预览均可用；发送前门禁与收件预估按所选模板及任务配置工作。

必须保持：企业专家既有模板和任务的业务数据、已有变量取值、QA/回复片段编辑规则、旧批量任务默认收件范围、研发类型与方向筛选的独立性。范围外：代用户创建模板/任务、上传或分类 500 人、修改专家索引/邮箱验证/实际发信、扩充新变量或新增学术专家数据管道。

## 关键不变量

### Invariant M-1：模板决定变量门禁
- Rule：模板本次实际主题/正文中的 `${key}` 缺值须阻止发送；`${key|非空默认值}` 缺值须用默认值且不形成该变量门禁。ES 预筛不得比本次实际发送门禁更严格。旧 `required_keys` 不再叠加为第二套业务规则。
- Applies to：子计划 1 的 I-1/I-2；子计划 2 对模板门禁的交集。
- Violation consequence：通用模板仍排除 500 人，或必填字段缺失却被发出。
- 来源：original；代码依据 `MailComposeTemplateService.kt:140-169`、`PersonalizationGateService.kt:38-54`、`IntroductionMailComposer.kt:26-32`。

### Invariant M-2：研发类型与研究方向是两个筛选维度
- Rule：`researchFields` 无值只决定“研究方向：无”，不自动决定 `expertClassification.type=UNKNOWN`。`UNKNOWN` 与无分类字段的 `UNCLASSIFIED` 不相同；用户手动选择的类型、方向、模板预筛取 AND。
- Applies to：子计划 2 的 I-2/I-3；主计划整体验收。
- Violation consequence：用“未知”错误宣称本地 500 人都会命中，或收件范围超出任务选择。
- 来源：original；代码依据 `ExpertSearchService.kt:93-145`、`BatchExecutionModels.kt:116-145`；本地 `experts.json` 500 人均无研发类型字段。

### Invariant M-3：配置与执行一致
- Rule：模板改动后 `gate-fields`、预估及实际发送均按当前模板读取；方向三态从配置保存、回显、定时快照、手动覆盖、ES 新目标和 MySQL 重试目标全链路一致。旧任务新增字段默认 `ANY`，不因升级改变收件范围。
- Applies to：子计划 1 的 I-3/I-4，子计划 2 的 I-1/I-2。
- Violation consequence：预估与实际不一致或旧任务静默改变人群。
- 来源：original；代码依据 `ManualInitialOutreachService.kt:426-471,1001-1052,1259-1324`、`BatchSendTaskConfigService.kt:62-215`、`BatchExecutionModels.kt:260-325`。

### Invariant M-4：用户自建与数据导入边界
- Rule：实现只提供模板和任务能力，不自动创建、启用或发送任何生产任务；本地 500 人名单不是线上已入库/已分类的证明。整体验收用测试数据，生产入库与任务设置由用户后续决定。
- Applies to：两份子计划及验收。
- Violation consequence：未经核对就宣称 500 人可发送，或意外触发批量邮件。
- 来源：original；代码依据 `no-direction-email-pilot-500-20260917/experts.json` 的 500 条 `send_eligibility=NOT_ASSESSED_DIRECTION_MISSING`。

## 现状审计

- 模板存储/写读：`V61` 建 `mail_compose_template` 和块表，`V84` 加 `required_keys`；运行时唯一写入口是 `MailComposeTemplateService.create/update/setEnabled/delete`，读入口是模板列表、预览、gate-fields 和两条发送 composer。子计划 1 已逐条审计全部相关写/读路径及片段变体来源，本主计划不新增写路径。
- 批量配置/写读：`V72` 建 `batch_send_task_config`，`BatchSendTaskConfigService` create/update/legacy update 写配置；`toExecutionSnapshot`、`BatchSendControlService`、`ManualInitialOutreachService` 读并执行。子计划 2 已逐条审计旧迁移、ES/重试读路径和前端所有字段映射，本主计划不新增字段。
- 跨计划交互点：模板保存 → gate-fields → 任务门禁提示/预估；模板保存 → `IntroductionMailComposer` → SMTP 前残留拦截；方向配置保存 → 快照 → 预估/执行；方向筛选与模板门禁、研发类型在 ES 和重试路径汇合。完整 schema、写路径、读路径和样式事实均在对应子计划的 `## 现状审计`，不在主计划重复维护一份易漂移清单。

## 实现方案

1. T1（M-1/M-3/M-4）：只执行子计划 1 的 9 个文件，先过其定向 Kotlin/JS 测试和人工 A-1 至 A-4；此阶段不加任务三态字段。
2. T2（M-2/M-3/M-4）：在 T1 验证后，只执行子计划 2 的 10 个文件及 V128 迁移，过其定向 Kotlin/JS 与任务配置回归；此阶段不改模板变量解析。
3. T3（M-1 至 M-4）：执行下述跨计划验收。失败时定位并修订对应子计划，不向主计划添加第三批功能文件。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `docs/plans/2026-09-18/00-university-email-template-main.md` | 仅新增主计划 |

主计划业务文件数为 0。子计划 1/2 的业务及测试文件分别为 9/10 个，以各自穷尽清单为边界；主计划不合并清单作为一次执行授权。前端样式契约只在触及 `app.js`、`index.html` 的子计划中执行，本文件不触及前端文件。

## 验收标准

- M-1：用同一缺方向测试专家，`/gate-fields` 对 `${primaryResearchField}` 返回 `researchFields` 必填预筛，对 `${primaryResearchField|your research area}` 不返回该字段；实际 composer 分别拒绝与使用默认值，无残留 token。
- M-2：测试 `UNKNOWN`、`UNCLASSIFIED` 与方向 `ANY/PRESENT/ABSENT` 的交集；同条件下“未知＋无”和“未分类＋无”命中不同样本。
- M-3：新增模板可选入介绍邮件任务；任务保存 `ABSENT` 后刷新、定时/手动快照、预估和执行均保留；旧任务为 `ANY`，现有其他筛选字段不变。
- M-4：交付 diff 无模板实例、批量任务实例、专家数据写入或发信调用；两子计划测试各自通过，再跑跨计划组合场景、`git diff --check`。缺真实线上数据时不报告“500 人可发送”。

## 人工验收清单

### A-1：两种模板和任务交集
- 前置条件：测试环境有同校两名未联系专家，甲 `institution="Test University"`、`researchFields="Quantum Computing"`；乙机构同名、`researchFields` 缺失。两人类型均明确设为 `UNKNOWN`，邮箱有效；关闭自动发信。
- 操作步骤：1. 在 UI 自建模板甲，主题含 `${primaryResearchField}`；2. 自建模板乙，主题含 `${primaryResearchField|your research area}`；3. 自建测试任务分别选择模板甲＋方向“有”、模板乙＋方向“无”；4. 查看两任务收件预估和邮件预览。
- 预期结果：第一任务只预估甲，主题显示 `Quantum Computing`；第二任务只预估乙，主题显示 `your research area`；两模板均可编辑、预览和启停，未自动发送。
- 覆盖：M-1/M-2/M-3/M-4；模板写→任务读→预估/渲染。

### A-2：未分类不混作未知
- 前置条件：沿用 A-1，另建专家丙，机构同名、缺研究方向且 `expertClassification.type` 字段不存在；邮箱有效、未联系。
- 操作步骤：1. 在第二任务选择“未知＋无”看预估；2. 将类型临时改为“未分类＋无”看预估；3. 不保存手动覆盖，重开原任务。
- 预期结果：步骤 1 命中乙、不命中丙；步骤 2 命中丙、不命中乙；重开任务仍是原保存的“未知＋无”。
- 覆盖：M-2/M-3，类型 ES 读→方向筛选→手动覆盖。

### A-3：旧任务与 CRUD 回归
- 前置条件：迁移前既有任务和模板；另有一条测试任务引用新模板。
- 操作步骤：1. 查看旧任务的方向选项及预估；2. 编辑新模板名称并禁用/启用；3. 引用期间尝试删除；4. 改绑测试任务后删除。
- 预期结果：旧任务方向为“不限”且原预估筛选不变；新模板名称与启停回显正确；引用期间删除被明确拒绝、任务引用保留；解除引用后删除成功。
- 覆盖：M-3/M-4，模板 CRUD→任务外键、旧配置→预估。

### A-4：旧变量与其他编辑器回归
- 前置条件：现有企业专家介绍模板、QA 规则和回复片段各一条；测试环境关闭自动发信。
- 操作步骤：1. 打开旧企业模板预览，记录 `institution` 和现有发送方变量值；2. 分别在 QA/回复片段编辑器尝试保存无默认值的 nullable 专家变量，再添加默认值保存；3. 返回旧企业模板预览。
- 预期结果：旧变量值前后一致；QA/片段裸变量仍提示非法，加非空默认值后可保存；旧企业模板正文及任务绑定不变。
- 覆盖：M-1/M-4；must-not-change 的变量取值、企业模板、QA/片段规则。

人工验收开始时，从本节导出 `00-university-email-template-main-acceptance.md`；现在不生成勾选文件。
