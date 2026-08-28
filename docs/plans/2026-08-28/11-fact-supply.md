# 01 事实供给：占用「可达但无主」的覆盖键 + 修受控键死锁

顺序权威：`10-reply-orchestration-order.md`。本计划是本轮第 1 份，**零 Kotlin 改动**，可独立部署与回滚。

## 需求描述

**Observable outcome**
1. 专家询问「你们通常合作什么类型的企业 / 常见研发需求」「报酬怎么结构化（retainer / hourly / project-based）」「顾问要交付什么」「研究数据保密怎么约定」「合作多久、每周投入多少」这五类问题时，AI 回复不再输出「暂无已审核事实」，而是给出已审核事实。
2. QA 规则 `Project sensitivity concerns` 在管理后台可以正常保存与启用，不再返回 HTTP 400。
3. 专家用 "IP arising" / "advisory input" / "who owns IP" 等表达提问知识产权时，`Pre-contract IP boundary` 进入候选规则集。

**What must NOT change**
- `qa_rule` 中 id ∈ {1, 3, 21, 24} 的四行，任何列都不变（G-1）。
- 四条 V82 受控事实（G1–G4）的 `answer_body` / `reply_body` 一个字符不变。
- 现有任何规则的 `reply_body` / `answer_body` 不被改写（本计划不含正文改写）。
- `QaCoverageKeyCatalog` 与 `AiReplyIntentCatalog` 的内容不变（零 Kotlin 生产代码改动）。
- 现有规则的 `priority` 不变（不改变匹配优先级次序）。

**Out of scope（显式推迟）**
- `Application process` / `Agency credentials and government cooperation` / `Partner company information` 三条的**正文改写**（去 "PhD team"、补官网与顾问名单口径、补匹配后披露）。理由：G-3 要求正文整体改写必须携带线上逐字基线守卫，而这三条自 V38 建立后可能经 V52/V75/V77/V81/V105 及运营运行时多次修改，仓库内**没有**可信的逐字当前值。→ 移入 12 的前置任务，先导出线上基线。
- `publication.authorship`（论文署名）虽是「可达但无主」键之一，本计划**不建规则**。理由：本轮无经需求方确认的权威正文，编造对外承诺违反可追溯原则。显式登记在守卫测试的例外集合中。
- 任何新 coverage key / 新 intent（如 `enterprise.rnd_needs`、`company.official_website`）。本计划用「合并进已有可达键」的方式规避（见 I-2）。

## 关键不变量

### Invariant I-1: 冻结行的双重守卫
- Rule: 本计划的每一条 `UPDATE qa_rule` 必须同时满足两个条件：按 `reply_subject` 定位，**并且**携带 `AND id NOT IN (1, 3, 21, 24)`。两者缺一不可——前者表达意图，后者在 G-2 的映射不确定性下兜底。
- Applies to: V109 中的全部 3 条 `UPDATE`（死锁修复 1 条、关键词追加 1 条、无第 3 条正文改写）。`INSERT` 不需要该守卫（新行拿不到已存在的 id）。
- Violation consequence: 覆盖需求方已手工确认的对外话术，且不可从 `updated_at` 追溯。
- 来源: G-1 / G-2（`10-reply-orchestration-order.md`）

### Invariant I-2: 新规则只能使用「已被 intent 引用」的覆盖键
- Rule: 本计划新建的每条规则，其 `coverage_keys` 中的**每一个**键都必须已出现在 `AiReplyIntentCatalog` 某条 intent 的 `requiredCoverageKeys` 或 `alternativeCoverageKeys` 中。本计划不新增 catalog key、不新增 intent，因此不得使用目录外的键。
- 实测证据（2026-08-28 在 main 上跑脚本，比对 `QaCoverageKeyCatalog.kt` 的全部 `Entry("<key>"` 与 `AiReplyIntentCatalog.kt` 的全部 `(required|alternative)CoverageKeys = listOf(...)` 字面量）：
  ```
  catalog keys: 33   intent-referenced keys: 31
  ORPHANS: general.answer, work.relocation
  REVERSE MISMATCH: (无)
  ```
  **K-coverage-key-orphan-makes-fact-unreachable 记录的 2026-08-26 名单已过时**：该条把 `application.required_materials` 列为孤儿，但 `AiReplyIntentCatalog.kt:282-283` 已把它作为 `application.steps` 的 alternative 引用，现已不是孤儿。该 K 条目须在 Phase 6 更正。
- Violation consequence: 规则结构性不可达——`isCoverageEligible` 对所有 intent 恒 false → `selectIntentKeyForRule` 返回 null → 规则被丢弃，补关键词、补 alias 都救不回来。**非空但不相交的 coverage 比留空更差**（留空还能兜到 `general.answer`）。
- 来源: K-coverage-key-orphan-makes-fact-unreachable（名单部分已失效，机制部分仍有效）

### Invariant I-3: 新规则的覆盖集不得恰好等于任一受控组
- Rule: 新建规则的 `coverage_keys` 集合不得等于 `{confidentiality.materials}`、`{fees.policy}`、`{contract.party, contract.terms}`、`{ip.arrangements}` 中的任何一个。
- 本计划的 5 条新规则覆盖集分别为 `{enterprise.project_types}`、`{finance.compensation_structure}`、`{role.deliverables}`、`{confidentiality.research}`、`{work.advisory_duration, work.time_commitment}`——逐一核对均不等于任何受控组，安全。
- Violation consequence: `validateControlledBody` 触发正文逐字校验，规则不可保存、不可启用。
- 来源: G-4 / K-controlled-gate-trigger-exact-group

### Invariant I-4: 空 `coverage_keys` 是合法且可达的状态，不是「未配置」
- Rule: `isCoverageEligible`（`AiReplyIntentCatalog.kt:655-659`）对空 coverage 返回 `intent.key !in coverageRequiredIntentKeys`——即**对所有非高危 intent 合格**。因此把 `Project sensitivity concerns` 的 `coverage_keys` 从 `'confidentiality.materials'` 改成 `''`，是把它从「自称 G1 权威出处」降级为「legacy 通用规则」，规则仍然可达，且不再被受控门禁拦截。
- Applies to: V109 的死锁修复语句。
- Violation consequence: 若误以为空 = 不可用而改赋别的键，会重新踩 I-2 或 I-3。
- 来源: original（读 `AiReplyIntentCatalog.kt:655-662` 得出）

### Invariant I-5: 受控规则改关键词时正文必须一字不动
- Rule: `Pre-contract IP boundary` 的 `coverage_keys` 恰为 `{ip.arrangements}` = G4 组。本计划只对它做 `keywords` 的 `CONCAT` 追加，**不得触碰 `reply_body` / `answer_body`**。
- 机制：迁移的直接 `UPDATE` 不经 `QaRuleManagementService`，本身不触发 `validateControlledBody`；但运营下次在 UI 上保存该规则时会触发（`QaRuleManagementService.kt:105`，且 `command.coverageKeys == null` 时用 `parseStored(existing.coverageKeys)` 复验）。只要正文仍逐字等于 `QaCoverageKeyCatalog.kt:40` 的 G4 canonical，就能通过。
- Violation consequence: 该规则从此不可保存、不可启用，运营无自救路径。
- 来源: G-4

### Invariant I-6: 每条新规则的 `category_id` 必须解析到真实存在的 `category_code`
- Rule: `qa_rule.category_id` 是 `BIGINT NOT NULL` 且带外键（`V1__create_business_tables.sql:51,63`）。`(SELECT id FROM qa_category WHERE category_code = 'X')` 在 X 不存在时返回 NULL，INSERT 会因 NOT NULL 约束**硬失败**（不是静默写入）。
- 现存全部 7 个 `category_code`（穷举自迁移）：`PROGRAM_AND_ELIGIBILITY`、`ROLE_AND_WORKSTYLE`、`FUNDING_AND_TIMELINE`、`PROCESS_ACTIONS`、`TRUST_AND_COMPLIANCE`、`COMMUNICATION_AND_OTHER`（以上 6 个来自 `V38__restructure_qa_categories_and_seed_new_rules.sql:5-11`）、`OVERVIEW`（来自 `V41__qa_overview_supersede.sql:7`）。
- Violation consequence: 迁移失败，应用启动失败。
- 来源: original

## 现状审计

### `qa_rule` 表（MySQL，Flyway 管理）

**Schema 关键列**（`V1__create_business_tables.sql:49-64` + 后续 ALTER）
- `category_id BIGINT NOT NULL`，外键指向 `qa_category(id)`（`:51`、`:63`）
- `coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`（`V76__add_qa_rule_coverage_keys.sql:6-7`）
- `answer_body`（`V79__add_qa_answer_body.sql`）
- `reply_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW'`（`V80__add_qa_reply_policy.sql:2`）；`V80:12-13` 定义派生关系：`auto_reply_enabled = (reply_policy = 'AUTO')`，`handoff_required = (reply_policy IN ('REVIEW','NEVER'))`
- `updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP`（K-qa-migration-preserve-auto-updated-timestamp）

**Write paths**
1. Flyway 迁移 —— 触及 `qa_rule` 的迁移共 31 个文件（`grep -l qa_rule *.sql`）：V1、V3、V14、V17、V18、V38、V40、V41、V42、V44、V45、V46、V52、V53、V57、V61、V63、V65、V68、V70、V75、V76、V77、V78、V79、V80、V81、V82、V105、V106、V107。
2. `QaRuleManagementService.createRule`（`:72-`）—— 运营 UI 新建；调 `validateRuleMeta` + `validateControlledBody`（`:75`）。
3. `QaRuleManagementService.updateRule`（`:99-`）—— 运营 UI 编辑；`:105` 复验受控正文；`command.coverageKeys == null` 时用 `parseStored(existing.coverageKeys)`。
4. `QaRuleManagementService.setRuleEnabled`（`:138`）—— 启用时同样复验受控正文。

**Read paths**
1. `QaRuleRepository`（`:17`）`ORDER BY priority ASC, id ASC` —— **数字小 = 优先**。
2. `QaFactSelectionService`（`:649-656`）`sortedWith(compareBy({ priorityById[it] ?: Int.MAX_VALUE }, { it }))` —— 升序。
3. `QaReplyComposer`（`:28`、`:46`）`.thenBy { it.rule.priority }` —— 升序。
4. `AiReplyIntentCatalog.isCoverageEligible`（`:655-662`）读 `coverage_keys`，决定该规则能否给某 intent 供证。
5. `QaCoverageKeyCatalog.validateControlledBody`（`:52-62`）读 `coverage_keys` + `answer_body`。

**Interaction points**
- **IP-1**：写路径 1（迁移赋 `coverage_keys`）× 读路径 4（`isCoverageEligible`）——迁移赋的键若不被任何 intent 引用，规则结构性不可达。本计划的 5 条新规则全部落在此交互点上，由 I-2 与新增守卫测试覆盖。
- **IP-2**：写路径 1（迁移赋 `coverage_keys`）× 读路径 5（`validateControlledBody`）——迁移把某规则的覆盖集写成恰等于受控组，而其正文不等于 canonical，则该规则在写路径 2/3/4 上永久失败。**这正是 `Project sensitivity concerns` 当前的状态**（见下）。
- **IP-3**：写路径 1 × 写路径 2/3 —— 迁移的无条件 `UPDATE` 覆盖运营运行时改动（G-3）。本计划靠 `WHERE NOT EXISTS` / `NOT LIKE` / 无正文改写规避。

### 缺陷 1：`Project sensitivity concerns` 的受控键死锁（IP-2 的实例）

证据链：
- `V38__restructure_qa_categories_and_seed_new_rules.sql:110-113` 建立该规则，`reply_body` 为
  `The project is legitimate and information is kept confidential. We can build trust step by step at your pace.`
- `V76__add_qa_rule_coverage_keys.sql:73-75` 把 `coverage_keys` 设为 **`'confidentiality.materials'`（单键）**。
- 该单键集合**恰好等于** G1 组 `setOf("confidentiality.materials")`（`QaCoverageKeyCatalog.kt:22`）。
- G1 canonical body 是 `Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.`（`:24`），与上述 `reply_body` 完全不同。
- 后续无任何迁移修改该规则的 `coverage_keys`（`grep -n "Project sensitivity concerns" *.sql` 命中 V38:110、V40:30、V44:18、V76:75，其中 V40 只改 `section_title`、V44 只修 `display_name` 编码）。
- 结论：该规则一经 `createRule` / `updateRule` / `setRuleEnabled(true)` 即抛 `Answer body must match the V82 canonical body ...`（HTTP 400）。
- 与 `V107` 修复的 id=24 是**同类缺陷**，V107 只修了 24，漏了这一条。

### 缺陷 2：7 个「可达但无主」的覆盖键

方法：扫描 `src/main/resources/db/migration/*.sql` 的全部 `coverage_keys = '...'` 赋值与 INSERT 内联覆盖键列，与 I-2 实测的 31 个「intent 已引用」键取差集。

结果——以下 7 个键**被 intent 引用（因此可达），但没有任何迁移把它们赋给任何规则**：
```
confidentiality.research
enterprise.project_types
finance.compensation_structure
publication.authorship
role.deliverables
work.advisory_duration
work.time_commitment
```
其余 24 个可达键均有迁移赋值（V76 / V82 / V105 / V107）。

本计划占用其中 6 个（5 条规则），`publication.authorship` 显式留空（见 Out of scope）。

> **注意**：以上是**迁移级**基线。运营可能通过 `QaRuleManagementService` 在运行时给某条规则赋过这些键。落库前须以线上查询为准：
> ```sql
> SELECT id, reply_subject, coverage_keys FROM qa_rule
>  WHERE coverage_keys REGEXP 'confidentiality\\.research|enterprise\\.project_types|finance\\.compensation_structure|role\\.deliverables|work\\.advisory_duration|work\\.time_commitment';
> ```
> 若已有规则占用某键，则本计划对应的那条 `INSERT` 删除（`WHERE NOT EXISTS` 只按 `reply_subject` 去重，不按 coverage_keys 去重）。

### 缺陷 3：`Pre-contract IP boundary` 的关键词覆盖不到实际问法

- 该规则的 keywords 为 `intellectual property,ip rights,ip arrangements,patent ownership,who owns the,ip terms`（`V82__split_trust_reply_atomic_facts.sql:141`）。
- `QaFactKeywordMatcher` 不存在于 `qa/service` 下；关键词匹配实现在 `QaFactSelectionService`（唯一含 `matchesRule` 的文件）。
- 真实来信写法 "the ownership of IP arising from advisory input" 不含上述任一子串（`who owns the` 需要 "who owns the"，来信是 "the ownership of"）。
- **本计划只补 QA 侧关键词**。`AiReplyIntentCatalog` 的 `requestAliases` 是**另一套独立字面量**，补 alias 属于 12 的范围——只做本计划这一半，该规则仍可能因 intent 未识别而不进入证据链。这一点必须在 12 中承接。

### 前端样式盘点
不适用——本计划不触及任何前端文件。

## 实现方案

### 任务 T-1：新建迁移 `V109__qa_fact_supply_and_controlled_key_repair.sql`

下一个可用版本号是 **V109**（`ls src/main/resources/db/migration` 的最大版本为 `V108__add_expert_types_to_batch_send_task_config.sql`）。

遵循 I-1 / I-2 / I-3 / I-5 / I-6 与 G-3。以下 SQL 是**最终代码，逐字复制**，不得增删语句或改写正文。

```sql
-- V109: QA fact supply for reachable-but-unowned coverage keys + controlled-key
-- deadlock repair. Plan docs/plans/2026-08-28/11-fact-supply.md.
--
-- I-1: every UPDATE below is guarded on the four frozen rule ids — those rows were
--      hand-adjusted by the requester and must not be touched by this migration.
--      (guard literal appears exactly once per UPDATE statement, nowhere else)
-- I-2: every coverage key below is already referenced by an intent in
--      AiReplyIntentCatalog; this migration adds no catalog key and no intent.
-- I-3: no new rule's coverage set equals a V82 controlled group.
-- I-6: every category_code below exists (V38:5-11 and V41:7).
-- G-3: INSERTs are guarded by WHERE NOT EXISTS on reply_subject; the keyword
--      append is NOT LIKE guarded; every UPDATE preserves updated_at.

-- ============================================================
-- 1. Repair: Project sensitivity concerns is stuck on the G1 controlled group.
--    V76:73-75 gave it the single key 'confidentiality.materials', which equals
--    the G1 group exactly, while its body has never matched the G1 canonical
--    body. Any create/update/enable therefore returns HTTP 400. Same class of
--    defect as id=24, which V107 fixed and this one was missed.
--    Blanking coverage_keys returns it to legacy reachability (isCoverageEligible
--    returns true for every non-high-risk intent on empty coverage) — I-4.
--    Body is deliberately untouched.
-- ============================================================
UPDATE qa_rule
   SET coverage_keys = '',
       updated_at = updated_at
 WHERE reply_subject = 'Project sensitivity concerns'
   AND coverage_keys = 'confidentiality.materials'
   AND id NOT IN (1, 3, 21, 24);

-- ============================================================
-- 2. Keyword parity: Pre-contract IP boundary (V82) cannot be reached by the
--    real-world phrasing "the ownership of IP arising from advisory input".
--    Keywords only; the body is the G4 canonical text and must stay byte-identical
--    (I-5).
-- ============================================================
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%ip arising%'
         THEN ',ip arising' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%advisory input%'
         THEN ',advisory input' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%ownership of ip%'
         THEN ',ownership of ip' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%ip ownership%'
         THEN ',ip ownership' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Pre-contract IP boundary'
  AND id NOT IN (1, 3, 21, 24)
  AND (
    LOWER(keywords) NOT LIKE '%ip arising%'
    OR LOWER(keywords) NOT LIKE '%advisory input%'
    OR LOWER(keywords) NOT LIKE '%ownership of ip%'
    OR LOWER(keywords) NOT LIKE '%ip ownership%'
  );

-- ============================================================
-- 3. Five new atomic facts, one per reachable-but-unowned coverage key.
--    Priority 120 matches the V82/V105 atomic-fact convention (priority ASC =
--    higher precedence, so 120 keeps these below the overview rules).
-- ============================================================

-- 3.1 enterprise.project_types — company types AND the R&D needs question are
--     answered by one fact on purpose, so that no new coverage key is required.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'types of companies,companies typically work with,enterprise types,partner types,r&d gaps,research gaps,technical needs,common problems',
    'ANY', 120, 'Partner enterprise types and R&D needs',
    'We do not work from a fixed public list of companies. The relevant company type and industry depend on the expert''s research direction and the availability of a genuinely suitable partner. Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.',
    'We do not work from a fixed public list of companies. The relevant company type and industry depend on the expert''s research direction and the availability of a genuinely suitable partner. Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.',
    'Partner enterprise types and R&D needs', 'Communication and other', 'AUTO', 1, 0, 0, 1,
    'enterprise.project_types'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Partner enterprise types and R&D needs');

-- 3.2 finance.compensation_structure
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE'),
    'compensation structure,retainer,hourly,project-based,payment method,payment schedule',
    'ANY', 120, 'Compensation structure',
    'There is no universal compensation model for all advisers. Compensation may be structured according to the agreed workload and project arrangement. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.',
    'There is no universal compensation model for all advisers. Compensation may be structured according to the agreed workload and project arrangement. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.',
    'Compensation structure', 'Funding and timeline', 'AUTO', 1, 0, 0, 1,
    'finance.compensation_structure'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Compensation structure');

-- 3.3 role.deliverables
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE'),
    'deliverables,outputs,milestones,expected work',
    'ANY', 120, 'Advisory deliverables',
    'There is no universal deliverables list. Expected outputs, milestones, reports or other deliverables are negotiated with the matched enterprise and recorded in the written agreement.',
    'There is no universal deliverables list. Expected outputs, milestones, reports or other deliverables are negotiated with the matched enterprise and recorded in the written agreement.',
    'Advisory deliverables', 'Role and work style', 'AUTO', 1, 0, 0, 1,
    'role.deliverables'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Advisory deliverables');

-- 3.4 confidentiality.research
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'research confidentiality,confidential research,use of research data,nda',
    'ANY', 120, 'Project research confidentiality',
    'Project-specific confidentiality obligations and permitted use of research information are defined in the written agreement with the matched enterprise.',
    'Project-specific confidentiality obligations and permitted use of research information are defined in the written agreement with the matched enterprise.',
    'Project research confidentiality', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'confidentiality.research'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Project research confidentiality');

-- 3.5 work.advisory_duration + work.time_commitment. Two keys on one rule: the
--     set {work.advisory_duration, work.time_commitment} is not a controlled
--     group (I-3), and both keys are intent-referenced (AiReplyIntentCatalog
--     :294 and :305), so the rule is reachable through either intent (I-2).
--     Key order follows catalog declaration order (G-6): work.time_commitment
--     is declared before work.advisory_duration in QaCoverageKeyCatalog.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE'),
    'project duration,time commitment,weekly hours,monthly hours,how involved',
    'ANY', 120, 'Advisory duration and time commitment',
    'A research advisory project commonly runs for approximately two to three years. The exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.',
    'A research advisory project commonly runs for approximately two to three years. The exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.',
    'Advisory duration and time commitment', 'Role and work style', 'AUTO', 1, 0, 0, 1,
    'work.time_commitment,work.advisory_duration'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Advisory duration and time commitment');
```

### 任务 T-2：新增 `QaFactSupplyMigrationTest.kt`

放在 `src/test/kotlin/com/weibo/talentintroduction/qa/service/`，形式照 `ProgrammeIdentityFactsMigrationTest.kt`（文本级断言，不依赖 Docker，因此能被默认 `mvn test` 门禁住；`FlywayMigrationIntegrationTest` 默认被 `@EnabledIfSystemProperty(migrationIt=true)` 跳过，不能承担这个角色）。

断言（对应 I-1 / I-3 / I-5 / G-3）：
1. 文件中 `UPDATE qa_rule` 的出现次数 == 2；把全文按 `UPDATE qa_rule` 切分后，**每一条 UPDATE 语句体**（到其结束分号为止）都含子串 `AND id NOT IN (1, 3, 21, 24)`（I-1）。同时断言该子串在全文出现次数 == 2——多于 2 说明注释里混入了字面量，会让计数型断言失去意义。
2. `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject =` 出现次数 == 5（G-3）。
3. `updated_at = updated_at` 出现次数 == 2（每条 UPDATE 一次，K-qa-migration-preserve-auto-updated-timestamp）。
4. 死锁修复语句携带精确守卫 `AND coverage_keys = 'confidentiality.materials'`（防止误伤已被运营改过的行）。
5. 四个新增 IP 关键词 `ip arising` / `advisory input` / `ownership of ip` / `ip ownership` **各自**在 SQL 中出现 2 次 `NOT LIKE '%<kw>%'`（一次在 `CASE WHEN`、一次在 `WHERE` 的短路条件里），合计 8 处（G-3）。
6. 迁移文本中**不出现** `reply_body =` 或 `answer_body =` 形式的赋值（即无正文改写；INSERT 的列名列表不算赋值）——守住 Out of scope 与 I-5。
7. 五条 INSERT 的 `coverage_keys` 字面量逐一断言，且**没有任何一个等于** `'confidentiality.materials'` / `'fees.policy'` / `'contract.party,contract.terms'` / `'ip.arrangements'`（I-3）。

### 任务 T-3：扩展 `QaCoverageKeyIntentParityTest.kt`

该文件已存在（`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt`），已有 3 个测试守住「intent 引用的键都在目录里」「目录里的键都被 intent 引用」「例外集合精确」。其例外集合 `knownUnreferencedKeys` 当前为 `{general.answer, work.relocation}`，与本计划 I-2 的实测一致。

**新增第 4 个测试**：`every intent-reachable coverage key is owned by at least one migration-seeded rule`。
- 实现：读 `src/main/resources/db/migration/*.sql`，提取全部 `coverage_keys = '...'` 赋值与 INSERT 内联覆盖键列，得到「已有主」键集合；与「intent 可达键集合减去 `knownUnreferencedKeys`」求差。
- 例外集合 `knownUnownedKeys = setOf("publication.authorship")`，注释写明理由：本轮无经需求方确认的权威正文，编造对外承诺违反可追溯原则；补上正文后须从本集合删除。
- 违反后果与 K-coverage-key-orphan 相反但同类：intent 认得出问题，却没有任何事实能供证，该 intent 恒 MISSING。

> 该测试的例外集合是**会随 12/13/14 变化的活文件**——每次有新事实落库，`knownUnownedKeys` 应当变短，绝不允许变长来「让测试变绿」。

## 变更文件清单

| # | 文件 | 动作 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V109__qa_fact_supply_and_controlled_key_repair.sql` | 新增 | T-1 的逐字 SQL |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaFactSupplyMigrationTest.kt` | 新增 | T-2，7 条文本级断言 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` | 修改 | T-3，新增第 4 个测试 + `knownUnownedKeys` |

合计 3 个文件，1 个子系统（qa 数据层），0 个生产 Kotlin 文件。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSupplyMigrationTest,QaCoverageKeyIntentParityTest

# 单条测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSupplyMigrationTest#'every update carries the frozen id guard'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# Flyway 迁移集成测试（opt-in，需要本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：项目根 `CLAUDE.md` 的 `## Commands` 章节（逐字照抄）。

## 验收标准

- **I-1**：`grep -c "AND id NOT IN (1, 3, 21, 24)" src/main/resources/db/migration/V109__*.sql` == 2，且 `grep -c "^UPDATE qa_rule" src/main/resources/db/migration/V109__*.sql` == 2，且逐条 UPDATE 语句体内均含该守卫。由 `QaFactSupplyMigrationTest` 断言 1 覆盖。
- **I-2**：`QaCoverageKeyIntentParityTest` 全部 4 个测试通过；新增的第 4 个测试在 `knownUnownedKeys` 清空后必须失败（证明例外集合确实在起作用）。
- **I-3**：`QaFactSupplyMigrationTest` 断言 7 通过——五条 INSERT 的 coverage_keys 字面量无一等于任一受控组。
- **I-4**：死锁修复语句把 `coverage_keys` 置为 `''` 而非任何别的键；由断言 4 的守卫子句与该语句的 `SET coverage_keys = ''` 共同证明。
- **I-5**：`QaFactSupplyMigrationTest` 断言 6 通过——V109 全文不含 `reply_body =` / `answer_body =` 赋值。
- **I-6**：V109 中出现的 `category_code` 只有 `COMMUNICATION_AND_OTHER`、`FUNDING_AND_TIMELINE`、`ROLE_AND_WORKSTYLE`、`TRUST_AND_COMPLIANCE` 四种，均属 `V38:5-11` 与 `V41:7` 定义的 7 个之一。可用 `grep -o "category_code = '[A-Z_]*'" V109__*.sql | sort -u` 核对。
- **回归**：执行「验证命令」节的全量测试命令通过。
- **跨交互点（IP-2）**：在带 Docker 的环境执行「验证命令」节的 Flyway 集成测试命令通过，证明 V109 在真实 MySQL 上可应用且不破坏外键约束（I-6）。

## 人工验收清单

### A-1: 五类问题不再返回「暂无已审核事实」
- 前置条件：V109 已应用；在「AI 训练 → 历史邮件模拟回复」中选一封来信，或用「新建训练邮件」构造一封正文包含以下五句的英文来信：
  `What types of companies do you typically work with?` /
  `Is the compensation a retainer, hourly, or project-based?` /
  `What deliverables would be expected?` /
  `How is research confidentiality handled?` /
  `What is the typical project duration and weekly time commitment?`
- 操作步骤：
  1. 打开「AI 训练 → 历史邮件模拟回复」，选中该邮件
  2. 点「一键预判」，等待生成完成
  3. 展开右侧「逐问覆盖」列表
- 预期结果：这五问对应的条目状态为 `GROUNDED · 依据充分` 或 `PARTIAL · 部分有据`，**不得为 `UNSUPPORTED · 无依据`**；每条的事实列表中分别出现 `Partner enterprise types and R&D needs`、`Compensation structure`、`Advisory deliverables`、`Project research confidentiality`、`Advisory duration and time commitment`。
- 覆盖：需求描述 observable outcome 1；I-2；IP-1

### A-2: `Project sensitivity concerns` 可以保存与启用
- 前置条件：V109 已应用。
- 操作步骤：
  1. 打开「QA 事实库」，找到显示名为「项目敏感性」的规则（`reply_subject = Project sensitivity concerns`）
  2. 在关键词末尾追加一个任意字符（如 `,trustcheck`），点「保存」
  3. 若该规则当前为停用，点「启用」
  4. 撤销第 2 步的关键词改动，再次保存
- 预期结果：第 2、3、4 步均提示保存成功，**不出现** `Answer body must match the V82 canonical body ...` 或任何 HTTP 400 报错。
- 覆盖：需求描述 observable outcome 2；I-4；IP-2

### A-3: IP 归属的实际问法能命中事实
- 前置条件：V109 已应用。
- 操作步骤：
  1. 在「AI 训练 → 历史邮件模拟回复」中构造一封正文含
     `Could you clarify the ownership of IP arising from advisory input?` 的来信
  2. 点「一键预判」
  3. 展开该条摘要的事实列表
- 预期结果：事实列表中出现 `Pre-contract IP boundary`。
  **若未出现**：这不是本计划的缺陷——见「现状审计 · 缺陷 3」，`AiReplyIntentCatalog.requestAliases` 是另一套独立字面量，需 12 承接。此时应确认「该规则已进入候选集」的替代证据：在 QA 事实库页面搜索关键词 `ip arising`，该规则出现在结果中。
- 覆盖：需求描述 observable outcome 3；缺陷 3

### A-4（回归）: 四条冻结规则未被改动
- 前置条件：V109 应用**之前**先执行并保存快照：
  ```sql
  SELECT id, reply_subject, keywords, priority, enabled, reply_policy, coverage_keys,
         MD5(reply_body) AS body_md5, MD5(answer_body) AS answer_md5, updated_at
    FROM qa_rule WHERE id IN (1, 3, 21, 24);
  ```
- 操作步骤：V109 应用后，再次执行同一查询。
- 预期结果：两次结果**逐字段完全相同**，含 `updated_at`。
- 覆盖：What must NOT change 第 1 项；G-1；I-1

### A-5（回归）: 四条受控事实正文未被改动
- 前置条件：同 A-4，快照范围改为
  ```sql
  SELECT reply_subject, MD5(reply_body) AS body_md5, MD5(answer_body) AS answer_md5, coverage_keys, updated_at
    FROM qa_rule
   WHERE reply_subject IN ('Application material confidentiality','Participant fee policy',
                           'Contract arrangements','Pre-contract IP boundary');
  ```
- 操作步骤：V109 应用后再查一次。
- 预期结果：四行的 `body_md5` / `answer_md5` / `coverage_keys` 完全不变；只有 `Pre-contract IP boundary` 一行的 `keywords` 变长（新增四个词），且其 `updated_at` **不变**。
- 覆盖：What must NOT change 第 2 项；I-5；K-qa-migration-preserve-auto-updated-timestamp

### A-6（回归）: 现有规则的优先级次序未变
- 前置条件：V109 应用前保存 `SELECT id, reply_subject, priority FROM qa_rule ORDER BY priority ASC, id ASC;`
- 操作步骤：V109 应用后再查一次，对比。
- 预期结果：原有全部行的 `priority` 与相对次序完全不变；只在列表末尾（priority 120 段）新增 5 行。
- 覆盖：What must NOT change 第 5 项

### A-7（回归）: 迁移可重复应用（幂等）
- 前置条件：V109 已应用一次。
- 操作步骤：在测试库上手工再次执行 V109 的全部语句。
- 预期结果：5 条 INSERT 因 `WHERE NOT EXISTS` 全部影响 0 行；2 条 UPDATE 因守卫子句（`coverage_keys = 'confidentiality.materials'` 已不成立、四个 `NOT LIKE` 已不成立）全部影响 0 行；无报错。
- 覆盖：G-3；IP-3

### A-8（回归）: 生产代码零改动
- 前置条件：本计划的分支已完成实现，尚未合并。
- 操作步骤：执行 `git diff --stat main...HEAD`。
- 预期结果：改动文件恰为「变更文件清单」中的 3 个；`src/main/kotlin/` 下**没有任何文件**出现在 diff 中（尤其是 `QaCoverageKeyCatalog.kt` 与 `AiReplyIntentCatalog.kt`）。
- 覆盖：What must NOT change 第 4 项；Out of scope「任何新 coverage key / 新 intent」

## Phase 6 知识回写（执行后由 fix-v 或本计划作者落实）

1. **更正 `K-coverage-key-orphan-makes-fact-unreachable`**：其 2026-08-26 的孤儿名单已过时。2026-08-28 在 main 上重测结果为 `catalog 33 / intent-referenced 31 / ORPHANS: general.answer, work.relocation / REVERSE MISMATCH: 无`——`application.required_materials` 已由 `AiReplyIntentCatalog.kt:282-283` 作为 alternative 引用，不再是孤儿。更正后 bump `created`（re-validated）。
2. **新增 `K-reachable-key-without-owning-rule`**（domain: qa）：「coverage key 被 intent 引用但没有任何规则声明它」是与孤儿键**对称**的缺陷——intent 认得出问题，却没有事实能供证，该 intent 恒 MISSING，且不会体现为任何报错。2026-08-28 实测有 7 个。守卫测试见 `QaCoverageKeyIntentParityTest` 第 4 个测试。
3. **新增 `K-qa-rule-id-not-asserted-by-v3`**（domain: qa）：`V3__seed_qa_rules.sql:23-34` 的 `INSERT INTO qa_rule` 首列是 `category_id` 不是 `id`，该文件不指定 id。任何按 id 定位 qa_rule 的计划都必须以线上查询为准；仓库内有迁移级证据的 id 只有 17、24、34（V82:31-47、V76:22-25 + V107:7-11）。
4. **新增 `K-qa-category-code-seven`**（domain: qa）：`qa_category.category_code` 现存全部 7 个值及其出处（V38:5-11 六个 + V41:7 的 OVERVIEW）；`qa_rule.category_id` 是 `NOT NULL` + 外键（V1:51,63），子查询取不到会硬失败而非静默写入。
