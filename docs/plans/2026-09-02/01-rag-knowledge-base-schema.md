# 01 RAG 知识库数据层：V112 五张表 + 45 条种子 + 快照与指纹

> 顺序权威：`00-execution-order.md`。本计划无前置依赖，是全轮第一份。
> 全局不变量 G-1 ~ G-4 适用，本文不重复定义。

## 需求描述

**Observable outcome**

1. 数据库出现 5 张 `rag_*` 表，`rag_fact` 有且仅有 45 行，内容与 `scripts/spike_deepseek_reply.py`
   的 `RAG_KNOWLEDGE_BASE` 逐字一致。
2. 应用启动时校验语料指纹；库里的事实被人手改动导致指纹与迁移写入的常量不符时，应用**启动失败**并
   在日志中打印期望值与实际值。
3. 新增 `scripts/export_rag_kb_sql.py`，可从脚本的 `--dump-kb` 输出重新生成 V112 的 INSERT 段与指纹，
   保证「库里的事实 == 脚本里的事实」是机器保证的。

**What must NOT change**

1. `qa_rule` / `qa_category` 的表结构、数据、任何读写路径。
2. 现有可信工作台、自动回复、手动发信的任何行为——本计划不接任何调用方。
3. 现有 Flyway 迁移链（V1..V111）一行不改。

**Out of scope**

- 检索逻辑（→ 02）、LLM 调用（→ 03）、任何前端（→ 04/05/06）、旧页下线（→ 07）。
- `rag_fact` 的管理端点与审计（→ 04）。
- 中文全文检索、向量检索：45 条 / 检索文本合计 18 042 字符，实测无需向量库。

## 关键不变量

### I-1: fact_code 唯一且与 area/seq 自洽
- Rule: `rag_fact.fact_code` 唯一，格式恒为 `KB-<AREA>-<NNN>`，其中 `<AREA>` 等于 `area` 列、
  `<NNN>` 等于 `seq` 列补零三位。迁移中以 CHECK 或种子期断言保证；快照加载时再校验一次。
- Applies to: V112 种子、`RagKnowledgeBase.load()`。
- Violation consequence: 强制事实规则按 `fact_code` 引用，格式漂移即静默失配。
- 来源: original（实测：45 条 fact_code 全唯一；编号 36 在 `FUND` 与 `CONF` 各用一次，area 不同故不冲突）

### I-2: enabled=false ⇒ status 呈现为 DISABLED 且永不进入候选
- Rule: 脚本 `_fact()` 的 `effective_status = "DISABLED" if not enabled else status`。
  库中 `enabled` 与 `status` 两列都存，但**任何读取路径**必须先按 `enabled=false → DISABLED` 归一，
  且 `enabled=false` 的事实永不进入检索候选。当前唯一一条是 `KB-APP-017 申报条件与材料（停用）`。
- Applies to: `RagKnowledgeBase.enabledFacts()`；02 的预筛；03 的提示词。
- Violation consequence: 已停用的资格条件事实被发到专家邮件里。
- 来源: original（`spike_deepseek_reply.py` `_fact()` 定义）

### I-3: 校验与重发布是两个操作，绝不合并
- Rule: `RagKnowledgeBase` 暴露**两个**互不相同的入口：
  - `verifyAndPublish()` —— **只在启动时调用**。读全表 → 算指纹 → 与 `rag_kb_meta.fingerprint`
    比对，不一致抛 `IllegalStateException` 终止启动 → 发布快照。**只读，绝不写 meta**。
  - `republish(writeInTx: () -> Unit)` —— **运营编辑时调用**（04）。在**同一个事务内**：
    执行传入的写操作 → 重读全表 → 重算指纹 → **UPDATE `rag_kb_meta.fingerprint`** →
    事务提交后再发布新快照。**全程不做旧指纹比对**。
- Applies to: `RagKnowledgeBase`；04 的 `RagFactAdminService`。
- Violation consequence: 若编辑路径复用启动期的校验逻辑，第一次合法编辑就会因
  「新算的指纹 ≠ 库里的旧指纹」被自己的门禁拦下 —— 门禁与编辑顺序互斥，知识库永远改不了。
  这是 2026-09-02 计划评审发现的 P0-3。
- 来源: original（2026-09-02 计划评审修正）

### I-3b: 快照发布在事务提交之后
- Rule: `republish()` 的新快照必须在**事务成功提交后**才赋给 `@Volatile` 字段。
  事务回滚时快照保持旧值。
- Applies to: `RagKnowledgeBase.republish()`。
- Violation consequence: 事务回滚而快照已换 → 内存里是没落库的事实，生成用了库里不存在的原文。
- 来源: original

### I-4: 列表字段的分隔符是数据契约
- Rule: `question_variants` 与 `keywords` 用 `|` 分隔（与脚本 `variants.split("|")` 一致）；
  `coverage_keys` 与 `source_refs` 用 `,` 分隔（与脚本 `coverage.split(",")` 一致）。
  解析时对每段 `trim()` 并丢弃空段——与脚本 `if item.strip()` 完全同构。
- Applies to: V112 种子生成、`RagFact` 解析。
- Violation consequence: 分隔符用错 → 短语与覆盖键全部错位，预筛结果与脚本分叉。
- 来源: original（`spike_deepseek_reply.py` `_fact()` 的 `phrases` / `keys` 构造）

### I-5: retrieval_text 的拼接顺序固定
- Rule: `retrieval_text = title | question_variants… | keywords… | coverage_keys… | answer`，
  各段以 `" | "` 连接，顺序与脚本 `RagFact.retrieval_text` 逐字一致。
  question_variants 与 keywords 同源导致短语出现两次——**照抄，不去重**
  （词重叠打分取集合，重复不影响分值；去重反而会改变字符串）。
- Applies to: `RagFact.retrievalText`；02 的打分；03 的候选 JSON。
- Violation consequence: 检索候选 JSON 与脚本不同字节，模型选择结果分叉。
- 来源: original

### I-6: 快照不可变且整体替换
- Rule: 45 行在启动时一次性读入不可变内存快照；不做逐条查库。管理页保存后（04）整体重建快照并重算指纹。
  快照对象一经发布不得就地修改。
- Applies to: `RagKnowledgeBase`。
- Violation consequence: 并发下半新半旧的事实集，逐字出信内容不确定。
- 来源: original（本仓无任何缓存框架，只有「服务字段上的 ConcurrentHashMap」或「建表」两条路 —— K-no-cache-framework-in-repo）

## 现状审计

### `qa_rule` 表（本计划只审计、不触碰）
- Schema: `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt:37-59` —
  `id / categoryId / keywords / matchMode / priority / replySubject / replyBody / answerBody /
  displayName / sectionTitle / replyPolicy / autoReplyEnabled / handoffRequired /
  supersedesChildren / enabled / coverageKeys / createdAt / updatedAt`。
- 建表与种子分散在 10 份迁移：`V1`, `V3`, `V38`, `V41`, `V52`, `V57`, `V68`, `V75`, `V105`, `V109`。
- 读路径（本计划确认这些**不会**被改动）：
  1. `llm/service/QaFactSelectionService.kt:33,214` — `findAllEnabledOrdered()`
  2. `llm/service/QaFactSelectionService.kt:149,469` — `findById`
  3. `llm/service/AiReplyDraftService.kt:743,901,1066,2210,2407,2461,2527,2553,2671` — `findById`
  4. `llm/service/TrustReplyWorkbenchService.kt:2597,2684,2783`
  5. `llm/service/AiReplyHighRiskClaimValidator.kt:227`
  6. `llm/service/AiReplyPointByPointComposer.kt:136`
  7. `mail/service/PendingMailOperationService.kt:558,1147`
  8. `mail/service/GroundedAutoReplyDecisionService.kt:107,227`
  9. `mail/service/InboundMailTagService.kt:57,76,132`
  10. `template/service/MailComposeTemplateService.kt:383,467`
  11. `monitoring/service/MailMonitoringService.kt:133`
- 写路径：`qa/service/QaRuleManagementService.kt`（唯一应用层写入点）+ 上述 10 份迁移。
- Interaction points: **本计划为零**——`rag_*` 五张表没有任何调用方（G-4）。这是刻意的：
  01 落地后系统行为完全不变，可独立部署与回滚。

### 语料实测数据（`scripts/spike_deepseek_reply.py --dump-kb` 输出）
- 事实数 45，`fact_code` 全唯一。
- `render_mode`: `COMPOSE` 38 / **`VERBATIM` 7**。
- `risk_level`: `LOW` 23 / `MEDIUM` 12 / `HIGH` 10。
- `status`: `APPROVED` 35 / `REVIEW` 9 / `DISABLED` 1。
- `reply_policy`: `AUTO` 37 / `REVIEW` 8。
- `area` 15 种，`category` 7 种，覆盖键 56 个。
- `answer` 合计 8 314 字符，单条最长 545 字符。
- `retrieval_text` 合计 18 042 字符。
- `question_variants` 合计 222 条，`source_refs` 合计 114 条。
- `legacy_rule_id` 非空 33 条，值为
  `1,2,3,4,5,6,7,8,9,10,11,12,13,15,16,18,19,20,21,22,23,24,29,32,33,35,36,37,38,39,40,41,42`。
- 语料指纹（45 行按 fact_code 排序后 SHA-256 前 16 位）：`2b29a2152f2671df`。

### Flyway 现状
- `CLAUDE.md:59` —「Schema 变更必须是新的 `V<n>__*.sql`，绝不编辑已应用的迁移」。
- 当前最大版本 `V111__create_expert_material_status.sql`，本计划占用 **V112**。
- 迁移集成测试：`FlywayMigrationIntegrationTest`，由
  `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` 门控，默认跳过。

### 配置类现状（作为 `RagProperties` 的同构模板）
- `llm/config/FactRetrieverProperties.kt`（34 行）、`llm/config/AskEnumeratorProperties.kt`（32 行）
  均为 `@ConfigurationProperties` + 默认值，注入点用 `= XxxProperties()` 保持单参构造兼容。
- 运行期配置在 `src/main/resources/application.yml`（`CLAUDE.md:56`）。

## 实现方案

### T1 — 导出脚本（先于迁移）
新建 `scripts/export_rag_kb_sql.py`：
1. `import spike_deepseek_reply`，读取 `RAG_KNOWLEDGE_BASE`。
2. 生成 `rag_fact` 的 45 行 INSERT（列顺序固定，字符串按 SQL 单引号转义）。
3. 生成 `rag_phrase_group` / `rag_intent_coverage` / `rag_mandatory_rule` / `rag_prefilter_exclusion`
   四张表的 INSERT（内容见 T2 的表定义）。
4. 计算并打印指纹（G-2 的算法），追加写入 `rag_kb_meta`。
5. 输出到 stdout，由人重定向进 V112 文件。**禁止手抄 45 条事实**（I-5、G-2）。

遵循 I-1、I-4、I-5。

### T2 — V112 迁移
新建 `src/main/resources/db/migration/V112__create_rag_knowledge_base.sql`：

**`rag_fact`**（45 行）
```
id BIGINT PK AUTO_INCREMENT
fact_code VARCHAR(32) NOT NULL          -- KB-FUND-033，UNIQUE
area VARCHAR(8) NOT NULL                -- FUND
seq INT NOT NULL                        -- 33
title VARCHAR(128) NOT NULL             -- 中文内部名，绝不进提示词（G-3）
category VARCHAR(64) NOT NULL
question_variants TEXT NOT NULL         -- '|' 分隔（I-4）
keywords TEXT NOT NULL                  -- '|' 分隔，与 question_variants 同源
answer MEDIUMTEXT NOT NULL              -- 对外正文唯一来源（G-3）
coverage_keys VARCHAR(512) NOT NULL DEFAULT ''   -- ',' 分隔（I-4）
reply_policy VARCHAR(16) NOT NULL       -- AUTO/REVIEW/NEVER，仅展示标签
status VARCHAR(16) NOT NULL             -- APPROVED/REVIEW/DISABLED
risk_level VARCHAR(8) NOT NULL          -- LOW/MEDIUM/HIGH
render_mode VARCHAR(16) NOT NULL        -- COMPOSE/VERBATIM
source_refs TEXT NOT NULL DEFAULT ''    -- ',' 分隔
legacy_rule_id BIGINT NULL              -- 只读对账，运行时禁读（G-4）
enabled TINYINT(1) NOT NULL DEFAULT 1
sort_order INT NOT NULL
created_at / updated_at / updated_by
UNIQUE KEY uk_rag_fact_code (fact_code)
KEY idx_rag_fact_enabled (enabled, status)
```
表注释必须写明：**`reply_policy` 与 `status=REVIEW` 在无自动发送的前提下退化为纯展示标签，
不参与任何分支判断**（D-1）。

**`rag_phrase_group`**（约 120 行）：`group_code VARCHAR(48)` + `phrase VARCHAR(128)` + `sort_order`，
`UNIQUE(group_code, phrase)`。来源为脚本的 `_XXX_PHRASES` 常量；`_INTENT_COVERAGE` 里 5 组内联短语
补名字：`VERIFICATION` / `CONTRACT_PARTY` / `RESPONSIBILITY` / `DURATION` / `AFFILIATION` /
`CONFIDENTIALITY`，另加 `COMPENSATION`（`compensation|remuneration|salary|paid`）。

**`rag_intent_coverage`**（21 行）：`group_code` + `coverage_key` + `sort_order`。

**`rag_mandatory_rule`**（6 行，含 D-3 新增行）：
`rule_code` + `match_groups`（any-of，`,` 分隔）+ `fact_codes`（有序，`,` 分隔）+ `sort_order`。
```
10  DETAIL_INQUIRY                  -> KB-PROG-002,KB-FUND-033
15  COMPENSATION                    -> KB-FUND-033          # D-3 新增
20  PROGRAMME_NAME                  -> KB-PROG-003
30  GOVERNMENT_ORG                  -> KB-GOV-004
40  PROGRAMME_NAME,GOVERNMENT_ORG   -> KB-COMP-007          # any-of
50  IP                              -> KB-IP-039,KB-CONF-036
```

**`rag_prefilter_exclusion`**（3 行）：
`rule_code` + `when_groups` + `unless_groups` + `target_type`(`FACT_CODE`|`COVERAGE_KEY`) + `target_value`。
```
COMPENSATION_MENTION / GOVERNMENT_FUNDING_MENTION / COVERAGE_KEY / finance.government_funding
COMPENSATION_MENTION / GOVERNMENT_FUNDING_MENTION / COVERAGE_KEY / finance.additional_support
DETAIL_INQUIRY       / (空)                        / FACT_CODE    / KB-FUND-034
DETAIL_INQUIRY       / COMPENSATION_STRUCTURE      / FACT_CODE    / KB-FUND-035
```
（前两条同 `rule_code`，拆两行以保持 `target_value` 单值。）

**`rag_kb_meta`**：单行，`fingerprint VARCHAR(64)` + `fact_count INT` + `updated_at`。

遵循 I-1、I-4、G-1、G-2。

### T3 — 领域与仓储
新建：
- `rag/domain/RagFact.kt` — `@Table("rag_fact")` data class，字段与 T2 一一对应；
  提供 `variants(): List<String>`、`coverageKeys(): List<String>`、`sourceRefs(): List<String>`、
  `retrievalText: String`（I-5）、`effectiveStatus(): String`（I-2）。
- `rag/repository/RagFactRepository.kt` — `CrudRepository<RagFact, Long>`，
  只暴露 `findAllByOrderBySortOrderAscIdAsc()` 与 `findByFactCode(code)`；不暴露全表扫描以外的查询。
- `rag/repository/RagRetrievalRuleRepository.kt` — 用 `NamedParameterJdbcTemplate` 一次性读出
  其余四张规则表（表小、启动期一次），返回四个不可变列表。

遵循 I-1、I-2、I-4、I-5、G-1。

### T4 — 快照与指纹
新建 `rag/service/RagKnowledgeBase.kt`：
- 持有 `@Volatile` 的不可变 `RagCorpusSnapshot(facts, phraseGroups, intentCoverage, mandatoryRules,
  exclusions, fingerprint)`（I-6）。
- `private fun load(): RagCorpusSnapshot` —— 读全表 → 校验 I-1 格式 → 归一 I-2 → 计算指纹。
  **不比对、不写库**，是下面两个公开入口共用的纯读部分。
- `verifyAndPublish()`（`@PostConstruct`，只在启动时调）：`load()` →
  与 `rag_kb_meta.fingerprint` 比对，不一致抛 `IllegalStateException` 并在异常消息里
  同时打印期望值与实际值 → 发布快照。**不写 meta**（I-3）。
- `@Transactional fun republish(writeInTx: () -> Unit)`（供 04 调用）：
  事务内 `writeInTx()` → `load()` → `UPDATE rag_kb_meta SET fingerprint = <新值>, fact_count = <新值>`
  → 事务提交后（`TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit`，
  或由调用方在事务外发布）才把新快照赋给 `@Volatile` 字段 → 返回新指纹（I-3、I-3b）。
- 指纹算法在此处**唯一实现**，`export_rag_kb_sql.py` 的 Python 实现必须与之等价，
  由 T6 的测试用固定样本交叉验证。

遵循 I-3、I-3b、I-6、G-2。

### T5 — 配置
新建 `rag/config/RagProperties.kt`（同构 `FactRetrieverProperties.kt`）：
`prefilterLimit=18`、`retrievalLimit=14`、`minLexicalScore=2`、
`coverageWeight=100.0`、`phraseWeight=12.0`、`overlapWeight=1.0`、
`retrievalTemperature=0.0`、`generationTemperature=0.2`、
`retrievalMaxTokens=900`、`generationMaxTokens=2600`。
在 `src/main/resources/application.yml` 增加对应 `rag:` 块（值与上面一致）。

这些常量的实测来源：`spike_deepseek_reply.py` 的 `prefilter_facts(limit=18)`、
`selected_ids[:14]`、`_lexical_score` 的 `100.0 / 12.0 / 1.0`、
`call_deepseek_json` 的 `temperature=0.0 / 0.2` 与 `max_tokens=900 / 2600`。

### T6 — 测试
新建 `src/test/kotlin/com/weibo/talentintroduction/rag/RagKnowledgeBaseTest.kt`：
- 45 行、指纹 `2b29a2152f2671df`（I-3、G-2）
- `fact_code` 唯一且与 `area`/`seq` 自洽（I-1）
- `VERBATIM` 恰好 7 条、`enabled=false` 恰好 1 条且 `effectiveStatus()=="DISABLED"`（I-2）
- `retrievalText` 对 `KB-GOV-004` 的逐字期望值（I-5）
- 分隔符解析：`KB-GOV-004` 的 6 个 variants、4 个 coverage keys、3 个 source refs（I-4）
- 强制规则 6 行且 `sort_order 15` 那行是 `COMPENSATION -> KB-FUND-033`（D-3）

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `scripts/export_rag_kb_sql.py` | 新增 |
| 2 | `src/main/resources/db/migration/V112__create_rag_knowledge_base.sql` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/domain/RagFact.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/domain/RagRetrievalRule.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/rag/repository/RagFactRepository.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/rag/repository/RagRetrievalRuleRepository.kt` | 新增 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagKnowledgeBase.kt` | 新增 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/rag/config/RagProperties.kt` | 新增 |
| 9 | `src/main/resources/application.yml` | 修改（新增 `rag:` 块） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagKnowledgeBaseTest.kt` | 新增 |

文件数 10，子系统 1（后端数据层）。无前端改动，故无 `## 样式契约`。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagKnowledgeBaseTest

# Flyway 迁移集成测试（需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 种子与脚本一致性（人工执行，比对指纹）
python3 scripts/spike_deepseek_reply.py --dump-kb | python3 -c "import sys,json,hashlib;d=json.load(sys.stdin);print(len(d))"
python3 scripts/export_rag_kb_sql.py | grep -o "fingerprint[^;]*"

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`export_rag_kb_sql.py` 打印的指纹等于 `2b29a2152f2671df`；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands 章节。

## 验收标准

- **I-1**：`RagKnowledgeBaseTest` 断言 45 个 `fact_code` 去重后仍为 45，且每个满足
  `fact_code == "KB-" + area + "-" + String.format("%03d", seq)`。
- **I-2**：断言 `enabled=false` 的事实恰好 1 条（`KB-APP-017`），其 `effectiveStatus()=="DISABLED"`；
  `enabledFacts()` 返回 44 条且不含 `KB-APP-017`。
- **I-3 / G-2**：断言 `verifyAndPublish()` 后 `fingerprint == "2b29a2152f2671df"`；
  另加一条用例：绕过 `republish` 直接改库中某条 `answer` 一个字符，再调 `verifyAndPublish()`，
  断言抛 `IllegalStateException` 且异常消息同时含期望值与实际值。
  **另加一条关键用例**：调 `republish { 改一条 answer }`，断言
  ① 不抛异常 ② `rag_kb_meta.fingerprint` 已更新为新值 ③ 随后 `verifyAndPublish()` 通过。
  这一条直接钉死 P0-3。
- **I-3b**：用例让 `writeInTx` 抛异常触发回滚，断言事务后 `snapshot()` 仍是旧实例、
  `rag_kb_meta.fingerprint` 未变。
- **I-4**：断言 `KB-GOV-004` 解析出 6 个 variants、4 个 coverage keys、3 个 source refs，
  且首个 variant 逐字为 `responsible government organization`。
- **I-5**：断言 `KB-GOV-004.retrievalText` 以 `项目组织层级 | responsible government organization | ` 开头、
  以其 `answer` 结尾，且包含 `responsible government organization` 两次。
- **I-6**：断言 `RagKnowledgeBase.snapshot()` 两次调用返回同一实例引用；
  `republish { }` 成功提交后返回新实例。
- **D-3**：断言 `mandatoryRules` 有 6 行，`sort_order==15` 的那行 `matchGroups==["COMPENSATION"]`
  且 `factCodes==["KB-FUND-033"]`。
- **G-4**：`grep -rn "qa_rule\|QaRule" src/main/kotlin/com/weibo/talentintroduction/rag/` 无输出。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 表与种子落地
- 前置条件: 本地或测试库已应用到 V111。
- 操作步骤:
  1. 启动应用（`mvn spring-boot:run`，见 `CLAUDE.md:27`）。
  2. 在数据库执行 `SELECT COUNT(*) FROM rag_fact;`
  3. 执行 `SELECT fingerprint, fact_count FROM rag_kb_meta;`
  4. 执行 `SELECT COUNT(*) FROM rag_phrase_group; SELECT COUNT(*) FROM rag_intent_coverage;
     SELECT COUNT(*) FROM rag_mandatory_rule; SELECT COUNT(*) FROM rag_prefilter_exclusion;`
- 预期结果: 第 2 步返回 `45`；第 3 步返回 `2b29a2152f2671df` 与 `45`；
  第 4 步依次返回约 `120`、`21`、`6`、`4`。
- 覆盖: 需求 observable outcome 1；I-1；G-2

### A-2: 指纹门禁真的会拦住启动
- 前置条件: A-1 已通过，应用已停止。
- 操作步骤:
  1. 执行 `UPDATE rag_fact SET answer = CONCAT(answer, ' X') WHERE fact_code = 'KB-FUND-033';`
  2. 重新启动应用。
  3. 查看启动日志。
  4. 执行 `UPDATE rag_fact SET answer = TRIM(TRAILING ' X' FROM answer) WHERE fact_code='KB-FUND-033';`
     后再次启动。
- 预期结果: 第 2 步应用**启动失败**；第 3 步日志中出现期望指纹 `2b29a2152f2671df` 与实际指纹两个值；
  第 4 步启动成功。
- 覆盖: 需求 observable outcome 2；I-3

### A-3: 逐字出信的 7 条事实内容正确
- 前置条件: A-1 已通过。
- 操作步骤: 执行
  `SELECT fact_code, LEFT(answer, 40) FROM rag_fact WHERE render_mode='VERBATIM' ORDER BY fact_code;`
- 预期结果: 返回 7 行，`fact_code` 恰为
  `KB-COMP-007`、`KB-CONF-036`、`KB-FUND-033`、`KB-GOV-004`、`KB-IP-039`、`KB-PROG-002`、`KB-PROG-003`；
  其中 `KB-GOV-004` 的 answer 前 40 字符为 `The programme is led at the national leve`。
- 覆盖: 需求 observable outcome 1；G-3

### A-4: 回归 —— 现有功能零变化
- 前置条件: A-1 已通过，应用运行中。
- 操作步骤:
  1. 打开「AI 回复训练 → QA 知识库」子 Tab，翻页查看规则列表。
  2. 打开「收发件箱」，选一封待处理来信，点开可信工作台，生成一次草稿。
  3. 打开「AI 回复训练 → 历史邮件模拟回复」，选一封历史邮件，生成一次。
- 预期结果: 三处行为与本计划实施前**完全一致**，没有任何新增提示、报错或空白区域。
- 覆盖: What must NOT change 第 2、3 条

### A-5: 回归 —— qa_rule 未被触碰
- 前置条件: 应用已启动过一次。
- 操作步骤: 执行 `SELECT COUNT(*) FROM qa_rule;` 并与实施前记录的数值比对；
  执行 `SHOW CREATE TABLE qa_rule;` 与实施前比对。
- 预期结果: 行数与建表语句均无变化。
- 覆盖: What must NOT change 第 1 条；G-4
