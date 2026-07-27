# P1：真实邮件 intent fixture 与匹配归一化

## 需求描述

- 可观察结果：以 Pracheta Janmeda 原始七组问题为输入，两个 AI 回复入口都返回 7 个有序 request groups、14 个精确 intents 和既有固定标题；`programme`、复合词序、`intellectual-property` 不再退化为 `general.answer` 或漏项。
- 必须不变：request 原文、QA 规则正文/关键词/coverage/variants、研究双证据、模型选择、前端样式、发送与审计流程。
- 明确不做：不修改 `QaRequestExtractor`，不新增 QA migration，不抓取 Scholar/Scopus/CV，不扩展未知业务 intent，不调整 prompt 或回复措辞。

## 关键不变量

### I-1：匹配归一化不改变原文

- URL/query 片段必须先屏蔽，再进行 lowercase、dash/hyphen→空格、连续空白折叠和 programme/program 词形归一化。
- 归一化文本只用于 alias 匹配；`requestText`、fallback heading、回复正文和审计不得被改写。
- URL 内 `selected=true`、`authorId`、`citations?user=` 等片段不得成为 selection、author 或 general request。
- 适用：`AiReplyIntentCatalog.matchIntents()` 及其在 `AiReplyDraftService` 的两个入口调用。
- 违反后果：request 标题/审计失真，URL 噪声形成虚假问题。
- 来源：K-ai-reply-intent-alias-fixture-fidelity。

### I-2：复合问题拆为原子 intent

原始 fixture 必须按顺序得到以下精确矩阵，既不能缺少，也不能额外混入：

| Group | 原始问题语义 | 精确 intent 列表 |
|---|---|---|
| 1 | expertise 是否在 programme/enterprise scope | `expertise.programme_fit`, `enterprise.project_types` |
| 2 | full name and registered location | `company.legal_name`, `company.registered_location` |
| 3 | purpose and structure of the programme | `programme.purpose`, `programme.structure` |
| 4 | selected and matched | `researcher.selection`, `enterprise.matching` |
| 5 | responsibilities and deliverables | `role.responsibilities`, `role.deliverables` |
| 6 | contractual, financial, intellectual-property arrangements | `contract.terms`, `finance.arrangements`, `ip.arrangements` |
| 7 | next stages | `application.next_stages` |

同一复合短语可以命中多个业务 intent；不得靠第一个 alias 命中后短路。（K-compound-request-coverage-intent-atomic）

- 适用：catalog definitions、group title、request coverage 组装与 fallback。
- 违反后果：复合问题被误报完整或缺少逐点段落。
- 来源：K-compound-request-coverage-intent-atomic。

### I-3：目录是唯一 intent authority

- `AiReplyIntentCatalog.definitions` 仍是 intent key、固定标题和 coverage 要求的唯一来源。
- 不让模型生成 intent，不从 QA 正文反推 intent，不新增 flat fact pool。
- group title 继续由完整 intent set 解析；七组标题保持既有固定英文标题。
- 适用：catalog、DraftService、materializer、deterministic fallback。
- 违反后果：模型或事实顺序可任意改变问题结构。
- 来源：K-request-facts-not-flat-pool。

### I-4：证据与 readiness 语义不变

- 匹配到 intent 不等于有事实依据；`resolveIntentCoverage()` 仍只从本 request 的 candidate IDs 与 prompt set 交集取证。
- Group 只有全部 intents SUPPORTED 才是 GROUNDED；任一 MISSING/PARTIAL 必须继续影响 readiness。
- 研究匹配仍要求 profile + `programme.scope` 双证据，不调用资料获取功能。（K-research-fit-dual-evidence）
- 适用：DraftService request facts 与两入口 response。
- 违反后果：无审核依据的内容被当作 READY，或 AI 自行补充外部资料。
- 来源：K-compound-request-coverage-intent-atomic、K-research-fit-dual-evidence。

### I-5：边界误命中不回归

- `selected` 可命中 `researcher.selection`；`preselected`、URL query 中的 `selected` 不得命中。
- `program/programme` 兼容不得把 `programme` 子串匹配进更长单词。
- 公司名称、项目类型、申请时间等既有 alias 的顺序和语义保持不变。
- 适用：所有 catalog aliases。
- 违反后果：修复真实 fixture 时引入宽泛误命中和既有回归。
- 来源：K-ai-reply-intent-alias-fixture-fidelity。

## 现状审计

### 代码路径

1. `QaRequestExtractor` 从专家正文提取有序 request group，并已屏蔽 URL；它保留原始文本供后续展示。
2. `AiReplyDraftService` 对每个 group 调用 `AiReplyIntentCatalog.matchIntents()`，再以 `resolveIntentCoverage()` 建立 nested request→intent→rule evidence 矩阵。
3. `AiReplyGroundedDraftMaterializer` 和 deterministic fallback 消费同一矩阵；controller 只映射 `requestCoverage`。

当前 `matchIntents()` 仅执行 `lowercase()` 和 URL 替换，然后对原 alias 做 word-boundary regex：

- `purpose and structure of the programme` 不包含 `purpose of the program` 或 `structure of the program`，两个 intent 均漏掉。
- `intellectual-property arrangements` 与 alias `intellectual property` 的空格不同，IP intent 漏掉。
- 当前没有以原始七问为整体 fixture 的 catalog→DraftService 回归测试，所以改写后的单句测试通过仍无法证明真实邮件正确。

### `qa_rule` store

- Schema/mapping：V1 建表；V14/V41/V76 分别增加 display/supersede/coverage 字段；`coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`，由 `QaRule.coverageKeys` 映射。
- Write paths：
  1. `QaRuleManagementService.createRule()` — 新建 rule 与 coverage。
  2. `QaRuleManagementService.updateRule()` — 更新正文、关键词、flags、coverage 与 variants。
  3. `QaRuleManagementService.setRuleEnabled()` / `deleteRule()` — 状态写入或删除。
  4. Flyway V3/V17/V18/V38/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76 — seed/backfill/结构迁移。
- Read paths：`QaRuleManagementService` 管理列表；`QaMatchService` 匹配；`AiReplyDraftService` 构建 prompt/coverage；`AiReplyPointByPointComposer` 与 `AiReplyHighRiskClaimValidator` 取正文；`LlmStitchService`、`InboundMailTagService`、`PendingMailOperationService`、`MailMonitoringService`、`MailComposeTemplateService` 消费 rule。
- Interaction points：本计划只改变 request→intent，之后由 `AiReplyDraftService` 读取既有 coverage；所有 write paths 跳过，字段值保持原样。

`mail_record_qa_rule`、`operator_action_log` 和发送服务均不在本计划作用域。

### 历史修复约束

Phase 6 已在 `docs/plans/fix/ai-reply-06-p1-intent-coverage-matrix/fix-3.md` 判定 Verification Blocked。本计划是新的结构性执行单元，不创建 `fix-4`，也不携带 Phase 6 其他已通过内容。

## 实现方案

### T1：建立 catalog 内部 canonical matcher

约束：I-1、I-5。文件：`AiReplyIntentCatalog.kt`。

在 `AiReplyIntentCatalog` 增加私有、确定性的匹配归一化函数：

1. 使用大小写不敏感规则屏蔽 `http/https` URL 和 query fragment。
2. lowercase。
3. 将 ASCII hyphen、en dash、em dash 等连接符统一为空格。
4. 折叠连续空白并 trim。
5. 只在完整词边界上将 `programme` 映射为 `program`。

aliases 在初始化/匹配时走同一 canonicalization，禁止只规范化 request 而保留 alias 原形。`wordBoundaryContains` 对 canonical phrase 做边界安全匹配。

### T2：补全复合 phrase aliases

约束：I-2、I-3、I-5。文件：`AiReplyIntentCatalog.kt`。

- 为 `programme.purpose` 与 `programme.structure` 都增加可覆盖 `purpose and structure of the program/programme` 的 alias，不用单个宽泛词 `purpose` 或 `structure` 扩大误命中。
- `ip.arrangements` 通过连接符规范化命中 `intellectual property`，无需新增带每种 Unicode dash 的重复 alias。
- 保持 definitions 顺序，从而保持 intent 列表与固定标题确定性。

### T3：catalog 精确 fixture 测试

约束：I-1、I-2、I-3、I-5。文件：`AiReplyIntentCatalogTest.kt`。

新增 `AiReplyIntentCatalogTest`：

- 七个原始 request group 逐个断言精确 intent key 列表与顺序。
- 对 group 3/6 单独覆盖 programme 与 intellectual-property。
- 覆盖 `selected=true` URL、`preselected`、company/project/order 等正反例。
- 断言 `resolveGroupTitle()` 对七组返回既有固定标题。

### T4：DraftService 端到端矩阵回归

约束：I-2、I-3、I-4。文件：`AiReplyDraftServiceTest.kt`。现有 `QaRuleManagementService` 写入的数据无需调整；`AiReplyDraftService` 继续读取同一 coverage 字段。

在 `AiReplyDraftServiceTest` 使用完整原始专家邮件 fixture 和受控 QA/profile：

- 断言 extractor 后得到 7 groups，顺序与原始文字保持。
- 断言每组 nested intents 与 I-2 完全一致。
- 删除一个 IP 或 programme coverage 时，对应 group 和总 readiness 必须降级；其他 group 不受影响。
- URL 不形成额外 group/intent；不调用 enrichment 或外网。

## 变更文件清单（3）

| 文件 | 操作 | 目的 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修改 | canonical matching 与精确复合 aliases |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | 新增 | 真实七问、标题及边界正反例 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | extractor→intent→coverage→readiness 端到端回归 |

文件数 3，单一 LLM 子系统。禁止顺手修改 `QaRequestExtractor`、migration 或 QA 数据；若 canonicalization 无法在 catalog 内完成，应停止并重新规划，而不是扩大范围。

## 验收标准

- I-1：API 返回的 `requestText` 与原始专家问题一致；URL 被排除，归一化文本不外泄。
- I-2：完整 fixture 精确产生 7 groups 和表中 14 个 intent，顺序固定，无 `general.answer`。
- I-3：七组 heading 分别为既有 `Research fit and enterprise projects`、`Company details`、`Programme purpose and structure`、`Selection and enterprise matching`、`Responsibilities and deliverables`、`Contractual, financial and IP arrangements`、`Next stages`。
- I-4：移除 `ip.arrangements` 依据只降级第 6 组；移除 profile 或 programme scope 只降级第 1 组；不跨 group 借证据。
- I-5：URL `selected=true` 与 `preselected` 不命中 selection；普通 `selected` 仍命中。
- 现有 `AiReplyDraftServiceTest` 全部通过，QA_MATCHED、FREE_FORM、fallback 和模型选择行为不变。
- 执行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 人工验收清单

### A-1：训练模拟真实七问

- 前置：使用原始 Pracheta Janmeda 邮件，不改写正文；准备包含七组所需 coverage 的已审核 QA 和已有专家画像。
- 操作：1）记录原始七组文字；2）在“AI 回复训练”选择任一模型；3）点击生成模拟回复；4）检查网络响应的 `requestCoverage`。
- 预期：7 项顺序与原邮件一致；每项 intents 与 I-2 完全相同；邮件标题从 1 开始连续编号，不出现 URL 片段或 `general.answer`。
- 覆盖：I-1、I-2、I-3；可观察结果。

### A-2：缺少 IP 依据

- 前置：只移除/禁用测试环境中的 IP coverage 依据，其他依据保持。
- 操作：1）再次模拟同一邮件；2）查看第 6 项 coverage 和总 readiness；3）恢复该测试 QA。
- 预期：第 6 项显示 IP 缺口并进入非 READY；第 2、3、4、5、7 项不因该缺口改变；正文不输出内部 status 文案。
- 覆盖：I-4；request→intent→QA read interaction。

### A-3：收发邮件入口一致性

- 前置：同一专家邮件已进入未匹配收件箱；训练模拟的 QA/profile 条件保持。
- 操作：1）打开收件详情；2）选择与训练模拟相同模型；3）生成首轮 AI 回复；4）对照两处逐点标题和 coverage。
- 预期：逐点标题、顺序、缺口状态与训练模拟一致；加载遮罩、模型下拉框、草稿样式不变；未触发任何 Scholar/Scopus/CV 获取。
- 覆盖：I-3、I-4；必须不变项与跨入口 interaction。

### A-4：URL 与边界词不误命中

- 前置：测试邮件包含 Scholar/Scopus URL query `selected=true`，正文另含单词 `preselected`，但没有 researcher selection 问题；记录 `qa_rule` 行数与相关行 `updated_at`。
- 操作：1）在训练模拟生成回复；2）检查 `requestCoverage`；3）再次查询 QA 行数与 `updated_at`。
- 预期：没有 `researcher.selection`；URL 不成为 request；QA 行数和 `updated_at` 与操作前一致。
- 覆盖：I-1、I-5；QA 所有 write paths 必须跳过。
