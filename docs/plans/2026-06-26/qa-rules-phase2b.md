# 开发计划：Phase 2b — 覆盖 / 缺口 / 落库 / 信封

> 使用 create-p 技能编写。依赖 Phase 1（6 主题分类）+ Phase 2a（多规则聚合、`qa_category.compose_order`、`QaReplyComposer`）。
> Phase 2b 触及 多新列 + 新表 + qa/mail 双子系统，**超出 create-p 单计划上限**，故拆为 4 个**独立可部署、按序**的子计划。每个子计划含完整 create-p 段落（审计以"增量"形式引用前序计划，不复述）。
> 执行顺序：2b-1 → 2b-2 → 2b-3 → 2b-4（彼此不反向依赖；2b-3 依赖 2a，2b-4 独立可与 2b-3 并行）。

公共审计基线（前序已审，本文件不再复述）：QA 存储结构与读写路径见 `qa-rules-restructure-phase1.md` 与 `qa-rules-aggregation-phase2a.md`；`qaMatchService.match` 唯一调用方 = `AutoMailReplyService:378`；`mail_record.matched_qa_rule_id` 为单值 FK，写于 `AutoMailReplyService:455`、`PendingMailOperationService:114`。

---

## 子计划 2b-1：章节标题 + 信封（QA 子系统，呈现层）

### 需求描述
- **可观察结果**：多命中聚合回复带英文小标题与统一问候/落款，读起来像一封信而非答复堆叠。
- **必须不变**：单条命中仍逐字节等价（沿用 2a I-1）；`AutoMailReplyService` 零改动；`QaMatchResult` 字段名不变。
- **超出范围**：OVERVIEW 覆盖、缺口、落库（后续子计划）。

### 关键不变量
- **I-1**：N==1 时不加标题/信封，subject/body 与该规则字段逐字节相等。
- **I-2**：N≥2 时，每段前置其规则 `section_title`（缺省回退为空标题、不报错）；整体加固定问候首行 + 落款尾块；段序仍按 `(compose_order,priority,id)`（沿用 2a I-2）。
- **I-3**：`section_title` 为新列，仅 `QaReplyComposer` 读取；无其他读路径依赖。

### 现状审计（增量）
- 新增列 `qa_rule.section_title VARCHAR(120) NULL`（唯一新列，唯一受影响存储 qa_rule）。
- 读路径变更：`QaReplyComposer`（2a 新增）开始读 `section_title`。写路径：仅 V21 迁移 seed + 运营管理页（`updateRule`，本计划不改 UI，新列走 null 默认）。
- 信封文案为常量（问候/落款）放 `QaReplyComposer`，不入库。

### 实现方案
1. `V21__qa_rule_section_title.sql`：`ALTER TABLE qa_rule ADD section_title VARCHAR(120) NULL`；给需要在聚合中出现的规则 seed 英文标题（如 Funding & timeline / Meeting arrangement…）。
2. `QaRule.kt`：加 `val sectionTitle: String? = null`。
3. `QaReplyComposer`：N≥2 分支加 `section_title` 前缀 + 信封；N==1 分支不动（I-1）。
4. `QaReplyComposerTest`：补标题/信封断言 + 单命中零变更回归。

### 变更文件清单（4）
`V21__qa_rule_section_title.sql`(新) / `QaRule.kt`(改) / `QaReplyComposer.kt`(改) / `QaReplyComposerTest.kt`(改)。
文件 4 ≤10 ✅　子系统 1 ✅　新列 1（qa_rule.section_title）✅

### 验收标准
- I-1：单命中用例字节等价；`AutoMailReplyService` 无 diff。
- I-2：双命中用例 body 含两标题 + 问候 + 落款，顺序正确。
- I-3：`section_title` 为 null 的规则参与聚合不抛错（标题段省略）。

---

## 子计划 2b-2：OVERVIEW 父规则 + supersede 覆盖（QA 子系统）

### 需求描述
- **可观察结果**：新专家"共享 CV 前要项目总览"这类信，命中 OVERVIEW 后**只回一封总览**，不再把 1/3/4/8 等子规则正文一起拼进去。
- **必须不变**：未命中 OVERVIEW 时，2a/2b-1 的聚合行为不变；`AutoMailReplyService` 零改动；`QaMatchResult` 字段名不变。
- **超出范围**：缺口、落库。

### 关键不变量
- **I-1**：当命中集中存在 `supersedes_children=true` 的规则时，最终命中集**只保留该（些）复合规则**，丢弃所有被覆盖的普通规则；`primaryRuleId` 取复合规则。
- **I-2**：无复合规则命中时，命中集与 2a 完全一致（零行为漂移）。
- **I-3**：复合规则的 `supersedes_children` 为新布尔列，默认 `false`；仅匹配引擎读取。
- **I-4**：OVERVIEW 规则归入新 `OVERVIEW` 分类，`compose_order=0`（最靠前）；其 `keywords` 仅匹配"总览型"开场（如 `learn more,more information,name and background,objectives and scope,before sharing,understand the program`），避免误吞普通单问。

### 现状审计（增量）
- 新增列 `qa_rule.supersedes_children TINYINT(1) NOT NULL DEFAULT 0`（唯一新列）。
- 新增 1 个分类 `OVERVIEW` + 1 条 OVERVIEW 规则（正文取 `docs/qa提炼-完整版.md` 的打包概览）。
- 读路径变更：`QaMatchService.match` 增加"复合覆盖"过滤步骤（在求出命中集之后、组装之前）。

### 实现方案
1. `V22__qa_overview_supersede.sql`：加列 `supersedes_children`；插入 `OVERVIEW` 分类（compose_order=0）；插入 OVERVIEW 规则（supersedes_children=1、上述关键词、概览正文、display_name=项目总览）。
2. `QaRule.kt`：加 `val supersedesChildren: Boolean = false`。
3. `QaMatchService`：命中集求出后，若含 `supersedesChildren` 为真者 → 命中集替换为"仅复合规则"；再交 composer（I-1/I-2）。
4. 测试：`QaMatchServiceTest` 补——命中 OVERVIEW+若干子规则 → 仅回 OVERVIEW；只命中子规则 → 行为同 2a。

### 变更文件清单（4）
`V22__qa_overview_supersede.sql`(新)/`QaRule.kt`(改)/`QaMatchService.kt`(改)/`QaMatchServiceTest.kt`(改)。
文件 4 ≤10 ✅　子系统 1 ✅　新列 1（qa_rule.supersedes_children）✅

### 验收标准
- I-1：OVERVIEW 与子规则同时命中 → 命中集.size==1 且为 OVERVIEW；body=概览正文。
- I-2：仅子规则命中 → 与 2a 用例逐字段一致。
- I-4：纯单问邮件（如只问 funding）**不**命中 OVERVIEW。

---

## 子计划 2b-3：缺口检测 → 转人工（QA + mail 子系统）

### 需求描述
- **可观察结果**：当来信问了多点但只命中其中一部分（有"缺口"）时，不再自动发"看似答全实则漏答"的回复，而是转人工草稿。
- **必须不变**：无缺口（命中覆盖了来信意图）时，照常自动发聚合回复；现有 `QA_NO_MATCH` 转人工分支不变。
- **超出范围**：缺口的"精确语义切分"——本切片用**保守启发式**，不引入 NLP/LLM。

### 关键不变量
- **I-1**：缺口判定 = `gapDetected`；为真时走现有 `markManualReview(... MANUAL_HANDOFF, reason="QA_GAP")` 路径，**不自动发送**。
- **I-2**：启发式定义固定且可测——`questionUnits`（来信中以 `?` 结尾子句数 与 项目符号行数 取较大值）> `matchedCategoryCount` 时判 `gapDetected=true`；阈值与算法写死在一处常量。
- **I-3**：`gapDetected=false` 时，自动发送路径与 2a/2b 完全一致（零回归）。
- **I-4**：不新增任何列/表；缺口为运行期计算。

### 现状审计（增量）
- `QaMatchResult` 增 `gapDetected: Boolean`（内存字段，调用方新增消费）。
- `AutoMailReplyService:378-438` **需改**：在 `match!=null && autoReplyEnabled && !handoffRequired` 之后，新增 `if (match.gapDetected) { markManualReview(reason="QA_GAP", reasonType="QA_GAP"); return ... }`。这是本子计划唯一跨入 mail 子系统的改动点（1 处分支）。
- `inbound_mail_processing.reason_type` 已是自由 VARCHAR（V14），新增枚举值 `QA_GAP` 无需迁移；前端按 reason_type 过滤已支持。

### 实现方案
1. `QaMatchService`/`QaReplyComposer`：计算 `questionUnits` 与 `matchedCategoryCount`，产出 `gapDetected`。
2. `QaMatchResult`：加 `gapDetected`。
3. `AutoMailReplyService`：加缺口分支（复用 `markManualReview` + `confirmManualReviewWithBody`，reason/reasonType=`QA_GAP`）。
4. 测试：`QaMatchServiceTest` 缺口判定用例；`AutoMailReplyServiceTest` 补"有缺口→MANUAL_HANDOFF 不发送""无缺口→照发"。

### 变更文件清单（4）
`QaMatchService.kt`(改)/`QaMatchService` 内 `QaMatchResult`/`AutoMailReplyService.kt`(改)/`QaMatchServiceTest.kt`+`AutoMailReplyServiceTest.kt`(改)。
文件 ≤10 ✅　子系统 2（QA + mail，仅 1 处分支跨入）✅　新列 0 ✅

### 验收标准
- I-1/I-2：3 个 `?`、命中 1 个主题 → gapDetected=true → 状态 MANUAL_HANDOFF、无外发 mail_record。
- I-3：命中主题数 ≥ 问题数 → 正常自动发。
- I-4：无新迁移。

---

## 子计划 2b-4：多命中全集落库（mail 子系统 + 新表）

### 需求描述
- **可观察结果**：一封聚合回复实际用到了哪些规则被完整记录（不止主规则），供监控与 2c 审计闭环使用。
- **必须不变**：`mail_record.matched_qa_rule_id` 仍写主规则（兼容现有监控）；现有读路径不破坏。
- **超出范围**：审计报表/UI（→ 2c）。

### 关键不变量
- **I-1**：新表 `mail_record_qa_rule(mail_record_id, qa_rule_id, ordinal)`，对每封聚合外发记录，落入命中集**全部**规则（含主规则），`ordinal` 记组装顺序。
- **I-2**：`mail_record.matched_qa_rule_id` 仍 = primaryRuleId（单值，零变更），新表为附加信息，二者不冲突。
- **I-3**：FK：`mail_record_id→mail_record(id)`、`qa_rule_id→qa_rule(id)`；删除 mail_record 时级联或受限按现有约定（与 V1 其他 FK 一致，RESTRICT）。
- **I-4**：仅自动聚合外发写新表；人工/单规则/历史记录不受影响（无回填）。

### 现状审计（增量）
- 新表（新存储）`mail_record_qa_rule`。写路径：`AutoMailReplyService` 聚合外发后新增一次批量插入（紧邻 `:442-461` 保存 mail_record 之后）。读路径：暂无（2c 消费）。
- `QaMatchResult.matchedRuleIds`（2a 已预留）此处被消费。
- mail_record 写路径不变（仍 `matchedQaRuleId=match.ruleId`）。

### 实现方案
1. `V23__mail_record_qa_rule.sql`：建表（mail_record_id, qa_rule_id, ordinal, PK 复合或自增 + 唯一索引(mail_record_id,qa_rule_id)，两 FK）。
2. 域 `MailRecordQaRule.kt` + `MailRecordQaRuleRepository`（CrudRepository）。
3. `AutoMailReplyService`：保存外发 mail_record 后，按 `match.matchedRuleIds` 批量插入关联行（I-1/I-4）。
4. 测试：`AutoMailReplyServiceTest` 补——聚合外发后关联表含全部命中规则、ordinal 有序；单命中仅 1 行；matched_qa_rule_id 仍主规则。

### 变更文件清单（5）
`V23__mail_record_qa_rule.sql`(新)/`MailRecordQaRule.kt`(新)/`MailRecordQaRuleRepository.kt`(新)/`AutoMailReplyService.kt`(改)/`AutoMailReplyServiceTest.kt`(改)。
文件 5 ≤10 ✅　子系统 1（mail）✅　新存储 1 表（独立，非向既有共享存储加列）✅

### 验收标准
- I-1：双命中外发 → 关联表 2 行、ordinal=0/1 按组装序。
- I-2：同记录 `matched_qa_rule_id` = 主规则；`SELECT` 关联表含主规则行。
- I-4：人工 `MANUAL_QA_REPLY` 外发不写关联表。

---

> Phase 2b 完成后：聚合回复成形（标题+信封）、OVERVIEW 一封到位、有缺口转人工、用到的规则全程留痕。接 Phase 3（人工组装台 + 审计 + LLM）。

## 修正记录

| 日期 | 来源 | 修正项 | 原因 | 约束更新 |
| --- | --- | --- | --- | --- |
| 2026-06-26 | fix-v `docs/plans/fix/qa-rules-phase2b/fix-1.md` | 明确 2b-2 `OVERVIEW` 与 2b-3 `QA_GAP` 的接口 | `OVERVIEW` 先覆盖为单条复合规则后，如果 2b-3 用覆盖后的分类数计算缺口，概览型多问邮件会被误判为 `QA_GAP`，无法自动发送总览。 | 缺口检测不得因 `supersedesChildren=true` 覆盖压缩后的单分类而触发；实现必须使用覆盖前命中集计算覆盖度，或将复合规则显式视为已覆盖总览型多主题开场。 |
