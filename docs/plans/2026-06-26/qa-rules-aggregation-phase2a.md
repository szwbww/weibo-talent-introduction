# 开发计划：QA 多规则聚合回复（Phase 2a，引擎层）

> 使用 create-p 技能编写。依赖 **Phase 1**（6 主题分类已落地）。
> 原"Phase 2"含 多列 + 引擎 + 多命中落库 + OVERVIEW覆盖 + 缺口检测，超出 create-p 上限（单存储仅 1 新列、≤2 子系统），故拆分：本计划 = **2a 仅做"按主题有序聚合多命中、组装成一封完整回复"**；OVERVIEW父规则/覆盖、缺口转人工、多命中落库、章节标题与信封、UI 全部推迟到 2b/2c（见文末）。

---

## 需求描述

**可观察结果**：一封同时问了多个问题的专家来信，自动回复不再只答命中最高的那一条，而是把**所有命中规则**的答复**按主题逻辑顺序**拼成一封完整回信发出。

**必须不变（Must NOT change）**：
- **单条命中场景完全等价**：当邮件只命中 1 条规则时，输出的 `subject` 与 `body` 与 Phase 1 当前行为**逐字节相同**（不加问候/落款/标题）。
- `QaMatchService.match(...)` 的**返回类型 `QaMatchResult` 的现有字段名与语义**（`ruleId / replySubject / replyBody / handoffRequired / autoReplyEnabled`）对调用方保持兼容 → `AutoMailReplyService` **不改一行**。
- 单条命中决胜规则（`matchedKeywordCount` → `priority`）决定 `primaryRuleId`。
- `mail_record.matched_qa_rule_id` 单值外键语义：仍只落"主规则"id，schema 不变。
- 现有挂起/转人工触发条件、会话状态机。

**超出范围（Out of scope，明确推迟）**：
- OVERVIEW 父规则与 `supersede` 覆盖（→ 2b）。
- 缺口检测、"命中 ≥2 即转人工"等新策略（→ 2b）；**本切片仍照常自动发送聚合回复**。
- 多命中全集落库（关联表）（→ 2b）；本切片只落主规则。
- 章节标题（英文 `section_title`）、问候/落款信封、内容级去重、LLM 缝合（→ 2b/2c）。
- 人工组装台 UI、审计闭环（→ 2c）。

---

## 关键不变量

### Invariant I-1：单条命中零回归
- Rule：当且仅当匹配到 1 条规则时，`QaMatchResult.replySubject = 该规则.replySubject`、`replyBody = 该规则.replyBody`、`ruleId = 该规则.id`，**不做任何包装**。
- Applies to：`QaReplyComposer` 的组装逻辑；`QaMatchService.match`。
- Violation consequence：所有现存单问自动回复内容被改写，回归面失控。

### Invariant I-2：多命中聚合的有序、完整、不重复
- Rule：命中 N≥2 条时，组装 body 必须包含**每条命中规则的 reply_body 恰好一次**，顺序严格按 `(qa_category.compose_order ASC, qa_rule.priority ASC, qa_rule.id ASC)`；不得丢条、不得重复、不得改写正文。
- Applies to：`QaReplyComposer`。
- Violation consequence：答非所问 / 漏答 / 重复段。

### Invariant I-3：开关标志按命中集聚合
- Rule：`QaMatchResult.handoffRequired = 命中集中任一规则 handoffRequired 为真`；`autoReplyEnabled = 命中集中所有规则 autoReplyEnabled 均为真`。
- Applies to：`QaMatchService.match`。
- Violation consequence：原本"需转人工/已禁用"的规则被聚合掩盖而误自动发送。

### Invariant I-4：主规则稳定且单值落库
- Rule：`primaryRuleId`（即 `ruleId`）= 按 `(matchedKeywordCount DESC, priority ASC, id ASC)` 选出的首条，与 Phase 1 单命中胜出者算法一致；该值（且仅该值）写入 `mail_record.matched_qa_rule_id`。
- Applies to：`QaMatchService.match`；`AutoMailReplyService` 写库路径（行 455，**不改**，靠字段兼容）。
- Violation consequence：监控页/历史按 `matched_qa_rule_id` 反查错乱；多值写入破坏单值外键。

### Invariant I-5：分类排序权威存在
- Rule：每个 `qa_category` 行都有非空 `compose_order`；聚合排序只依赖它。若某命中规则的分类缺 `compose_order`，视为配置错误。
- Applies to：V20 迁移；`QaMatchService` 读分类。
- Violation consequence：排序退化为不确定，违反 I-2。

---

## 现状审计

### QA 存储
- `qa_category`（V1 + 本计划 V20 加列）：现有列见 Phase 1 审计；**本计划新增 `compose_order INT NOT NULL DEFAULT 100`**（唯一新列，唯一受影响共享存储）。
- `qa_rule`：本计划**不加列、不改数据**。
- 域：`QaCategory.kt`（需加 `composeOrder` 字段）、`QaRule.kt`（不改）。
- `QaCategoryRepository`：继承 `CrudRepository`，已有 `findAll()`（聚合时取分类→compose_order 映射用），无需新查询方法。

**写路径（qa_category / qa_rule）**
1. V20 迁移：`ALTER TABLE qa_category ADD compose_order` + 给 6 个主题 UPDATE 排序值。仅此。
2. `QaRuleManagementService.createCategory/updateRule…`：不改；新列走默认值 100（运营新建分类默认排末尾，可后续在管理页维护——管理页编辑 compose_order 属 2b/可选，本计划不做）。

**读路径（qa_rule）**
1. `QaMatchService.match` → `findAllEnabledOrdered()`。**本计划改造点**：除规则外，新增读 `QaCategoryRepository.findAll()` 构建 `categoryId→composeOrder` 映射，用于聚合排序。
2. 管理页 `listRules`、人工 `ManualExpertMailService.composeQa`、`PendingMailOperationService`、`MailMonitoringService`：均不经 `match()`，**不受影响**。

**调用方审计（关键）**
- 全仓 `qaMatchService.match(` **唯一调用点 = `AutoMailReplyService` 行 378**。它消费 `match.autoReplyEnabled / handoffRequired / replySubject / replyBody / ruleId`（行 379、436-437、455）。
- → **只要 `QaMatchResult` 保持这 5 个字段名与语义，`AutoMailReplyService` 零改动**，整个改造收敛在 QA 子系统内（1 个子系统）。

### mail 存储（仅交互点，不改）
- `mail_record.matched_qa_rule_id BIGINT FK→qa_rule(id)`（V1）：单值。写路径：`AutoMailReplyService` 行 455 写 `match.ruleId`（=primaryRuleId）。本计划保持单值，不触碰。多命中全集落库 = **2b 的关联表**，此处为明确推迟的交互点。

**测试现状**
- `QaMatchServiceTest`：Mockito 单测，`QaMatchService(repository)` 单参构造，断言 `result?.ruleId`。→ 本计划改构造（加 category repo）后**必须更新该测试**（mock 第二个 repo）。

---

## 实现方案

### 数据（Task 1，守 I-5）
新增 `V20__qa_category_compose_order.sql`：
1. `ALTER TABLE qa_category ADD COLUMN compose_order INT NOT NULL DEFAULT 100 COMMENT '聚合回复时主题章节排序，越小越靠前';`
2. 给 6 主题 UPDATE：PROGRAM_AND_ELIGIBILITY=10、ROLE_AND_WORKSTYLE=20、FUNDING_AND_TIMELINE=30、PROCESS_ACTIONS=40、TRUST_AND_COMPLIANCE=50、COMMUNICATION_AND_OTHER=60（按 `category_code` 定位）。

### 域（Task 2）
`QaCategory.kt` 增 `val composeOrder: Int = 100`（Spring Data JDBC 映射 `compose_order`）。

### 组装器（Task 3，守 I-1/I-2）
新增 `QaReplyComposer`（纯函数、确定性、无 LLM）：
- 输入：命中规则列表（含各自 matchedKeywordCount）+ `categoryId→composeOrder` 映射。
- N==1：直接返回该规则的 `replySubject` / `replyBody`（I-1）。
- N≥2：
  - 排序：`(composeOrder ASC, priority ASC, id ASC)`。
  - body：各命中规则 `replyBody` 按序以 `"\n\n"` 连接，每条一次（I-2）。**本切片不加标题/问候/落款**（留 2b）。
  - subject：取 primaryRuleId 对应规则的 `replySubject`（不新造文案）。

### 匹配服务（Task 4，守 I-2/I-3/I-4/I-5）
改 `QaMatchService`：
- 构造注入新增 `QaCategoryRepository`。
- `match()`：求出**全部**命中规则（非仅最优）；`primary` = 按 `(matchedKeywordCount DESC, priority ASC, id ASC)` 选首条（I-4）；调用 `QaReplyComposer` 得 subject/body；`handoffRequired = any{...}`、`autoReplyEnabled = all{...}`（I-3）。
- `QaMatchResult`：保留 `ruleId(=primary.id) / replySubject / replyBody / handoffRequired / autoReplyEnabled`；可加 `matchedRuleIds: List<Long>`（供 2b 用，本切片调用方忽略）。

### 测试（Task 5，守 I-1/I-2/I-3/I-4）
- 改 `QaMatchServiceTest`：构造加 mock `QaCategoryRepository`；保留原 3 个单命中用例断言不变（I-1）；新增多命中用例（两条不同主题命中 → body 含两段且按 compose_order 排序、subject=primary、ruleId=primary）。
- 新增 `QaReplyComposerTest`：N==1 字节等价；N≥2 有序/完整/不重复；同主题多命中按 priority 排。

---

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V20__qa_category_compose_order.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaCategory.kt` | 改（+composeOrder） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposer.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | 改（聚合 + 注入 category repo） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposerTest.kt` | 新增 |

文件数：6（≤10）✅　子系统：1（QA，`AutoMailReplyService` 零改动）✅　新增共享存储列：1（`qa_category.compose_order`）✅

---

## 验收标准

- **I-1**：`QaReplyComposerTest` + `QaMatchServiceTest` 单命中用例断言 subject/body 与对应规则字段逐字节相等；`AutoMailReplyService` 无 diff。
- **I-2**：多命中用例断言——body 段数 = 命中数、每条 reply_body 恰出现一次、顺序符合 `(composeOrder,priority,id)`。
- **I-3**：构造"命中集中含一条 handoffRequired=true"用例 → `result.handoffRequired==true`；含一条 autoReplyEnabled=false → `result.autoReplyEnabled==false`（从而 `AutoMailReplyService` 走 MANUAL_HANDOFF，行为同今）。
- **I-4**：多命中用例 `result.ruleId` == 按现算法选出的 primary；阅 `AutoMailReplyService` 行 455 仍写单值。
- **I-5**：V20 后 `SELECT * FROM qa_category WHERE compose_order IS NULL` 为空；6 主题 compose_order = 预期值。
- **集成**：`JAVA_HOME=…/zulu-11.jdk/… mvn test` 全绿；构造一封同时含 funding + meeting 关键词的来信，自动回复 body 同时包含"资金"段与"会议安排"段且资金段在前（compose_order 30 < 60）。

---

## 后续切片路线图

- **Phase 2b — OVERVIEW 覆盖 + 缺口检测 + 多命中落库 + 章节信封**：qa_rule 加 `section_title` 与父子/`supersede`；OVERVIEW 父规则命中即压制子规则；识别"问了几点 vs 答了几点"，有缺口或命中≥阈值转人工草稿；新增 `mail_record_qa_rule` 关联表落多命中全集；给聚合回复加英文章节标题 + 问候/落款信封。（多个新列 + 新表 + 跨 mail 子系统，需再拆。）
- **Phase 3 — 人工组装台 UI + 审计闭环 + LLM 缝合**。
