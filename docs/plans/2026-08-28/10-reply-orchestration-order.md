# 00 执行顺序：回信生成整封编排改造（2026-08-28）

本轮需求横跨 4 个子系统、远超单计划上限（≤10 文件 / ≤2 子系统），必须拆成 4 份顺序计划。
本文件是**顺序权威**与**跨计划共享约束**，不含可执行任务。

## 前提更正（2026-08-28 实测，推翻本轮早期判断）

「一键预判」的正文**不经过** `AiReplyDraftService` 的 `GroundedContentPlan` / `composeFromPlan` 路径。真实链路：

```
一键预判 = 工作台 data-action="auto-run"
  -> autoRun()                trust-reply-workbench.js:1363
  -> runItemSequence(keys)    :894   逐条串行，每条一次 POST /workbench/generations/stream
  -> assemble() -> POST /workbench/assemble
      -> verifyAssembly()                                    TrustReplyWorkbenchService.kt:1396
          -> orderedAnswers = versions.mapNotNull { answerText }  :1466-1468
          -> composeLockedItems(orderedAnswers, resolvedFrame)    :1472
```

`composeLockedItems`（`AiReplyPointByPointComposer.kt:34-44`）是逐条 answerText 空行拼接——零去重、零动作对账、零整封视角。`TrustReplyWorkbenchService.kt:1462-1464` 的注释「跨 item 重复 claim 查重**已删除**」是重复的成文根因。

`/api/ai-training/simulate` 在 `app.js` 中**无任何调用点**，不是一键预判的链路。

据此，原 `12-whole-letter-dedup.md` 与 `13-paragraph-protocol.md` 已作废（保留存根说明），改为 `12-letter-closer.md` 与 `13-letter-orchestrator.md`。详见 `docs/knowledge/llm/K-oneclick-assembles-by-concatenation.md`。

## 拆分依据

按 create-p 的硬上限（<=10 文件 / <=2 子系统）拆成 7 份计划。

| 计划 | 落点 | 文件数 | 依赖 | 状态 |
|---|---|---|---|---|
| `11-fact-supply.md` | 迁移 V109 + 测试 | 3 | 无 | 已成文 |
| `12-letter-closer.md` | `verifyAssembly` 处的**确定性**收口 | 4 | 11 | 已成文 |
| `13-letter-orchestrator.md` | 把收口的分段升级为**一次编排 LLM 调用** | 5 | 12 | 已成文 |
| `14-workbench-concurrency.md` | 局部遮罩 / 条目级持久化 / autoBootstrap | 7 | **无（可与 12 并行）** | 已成文 |
| `15-workbench-three-step.md` | 三步界面 / 事实集 / 段落 pinned / 运营事实 | 7 | 13、14 | 已成文 |
| `16-unsupported-index.md` | 索引入库放宽 / topic 检索 / 两个回流通道 | 10 | 13、15 | 已成文 |
| `17-fact-body-rewrite.md` | 迁移 V110：三条事实正文改写 | 2 | 仅版本号顺序（11 之后） | 已成文 |

依赖理由：

- **11 -> 12**：12 消除「同一事实写两遍」，事实不够多时看不出收益；11 零代码、可独立部署。
- **12 -> 13**：12 是纯确定性后处理（零新增 LLM 调用），先拿掉重复 / 段落=条目 / 双 CTA；13 只替换其中「主题归并产段」这一步。**12 独立上线即有可观测收益，13 失败时自动退回 12 的结果。**
- **14 独立**：纯缺陷修复，不消费编排结果。**建议与 12 并行开工。**
- **13 + 14 -> 15**：15 的界面消费 13 的 `paragraphPlan`，其高频交互依赖 14 的持久化粒度。
- **13 + 15 -> 16**：16 的措辞回流通道 A 的安全性完全建立在 13 的「来源封闭」校验上。

推荐推进顺序：`11` -> `12` ∥ `14` -> `13` -> `15` -> `16`。`17` 只受迁移版本号约束（V109 之后），可与 12～16 任意一份并行；但它改的是对外话术，**须需求方逐段签字确认后才执行**。

## 跨计划共享不变量

### Invariant G-1: 冻结规则不可变（本轮全局）
- Rule: `qa_rule` 中 **id ∈ {1, 3, 21, 24}** 的四行已由需求方手工调整完毕。本轮四份计划的任何迁移、任何运行时脚本，**不得修改这四行的任何列**（含 `keywords` / `reply_body` / `answer_body` / `coverage_keys` / `priority` / `enabled` / `reply_policy` / `category_id` / `updated_at`）。
- Applies to: 本轮全部 `V<n>__*.sql`；任何通过 `QaRuleManagementService` 的批处理脚本。
- 实现方式（强制）：每条 `UPDATE qa_rule` 必须携带 `AND id NOT IN (1, 3, 21, 24)`，作为**独立于 reply_subject 定位的第二道守卫**。理由见下方「id ↔ reply_subject 映射的证据边界」。
- Violation consequence: 覆盖需求方已确认的对外话术；且因 `qa_rule` 带 `ON UPDATE CURRENT_TIMESTAMP`，覆盖后无法从 `updated_at` 区分是迁移写的还是运营写的。
- 来源: original（需求方 2026-08-28 明确指定）

### Invariant G-2: id ↔ reply_subject 映射的证据边界
- Rule: 本轮计划中，**只有 id=24 的映射有迁移级证据**；id 1 / 3 / 21 的 reply_subject 未被任何迁移断言，不得在计划或代码中假定。
- 证据：
  - `V76__add_qa_rule_coverage_keys.sql:22-25` 把 `programme.purpose,...,confidentiality.materials` 赋给 `reply_subject = 'Program overview'`；`V107__strip_controlled_keys_from_program_overview.sql:7-11` 对 **`WHERE id = 24`** 断言了同一串 coverage_keys → **id 24 = 'Program overview'**（双迁移交叉确认）。
  - `V82__split_trust_reply_atomic_facts.sql:31-47` 断言 `id = 17 AND reply_subject = 'Document confidentiality and no fees'`、`id = 34 AND reply_subject = 'Contract and IP arrangements'` → id 17 / 34 确认。
  - `V3__seed_qa_rules.sql:23-34` 的 `INSERT INTO qa_rule` **首列是 `category_id`，不是 `id`**；该文件不指定 id，id 由自增产生。因此「V3 第 n 条 = id n」是**推断**，不是事实。
  - `V105__add_programme_identity_facts.sql:3-4` 的注释写「id=6 (Full-time and part-time options) 和 id=18 (Agency credentials and government cooperation)」，但其 `UPDATE` 实际按 `reply_subject` 定位（:52、:68）——注释中的 id 是作者笔记，未被 SQL 断言。
- Applies to: 全部七份可执行计划。
- 强制前置：**执行 11 之前**，需求方或执行方须在线上库运行下列查询，并把结果贴回本文件的「线上基线」小节：
  ```sql
  SELECT id, reply_subject, enabled, priority, coverage_keys
    FROM qa_rule WHERE id IN (1, 3, 21, 24);
  ```
  若结果显示某个冻结 id 的 reply_subject 与某份计划打算修改的规则同名，该计划的对应任务必须**删除**（而不是加守卫绕过）。
- Violation consequence: 按 reply_subject 定位时误伤冻结行；或反之，以为改了某条实际没改。
- 来源: original

### Invariant G-3: 迁移不得覆盖运营运行时改动
- Rule: `qa_rule` 有两类写路径——Flyway 迁移与 `QaRuleManagementService`（运营 UI 运行时改 keywords / reply_body / enabled）。迁移中的无条件 `UPDATE ... SET keywords/reply_body` 会静默覆盖运营改动。
- Applies to: 本轮全部迁移（当前只有 11 含迁移）。
- 实现方式（强制）：
  - `INSERT` 一律 `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = '<subject>')`。
  - `keywords` 追加一律用 `CONCAT` + `NOT LIKE` 防重。
  - `reply_body` / `answer_body` **整体改写**必须携带 `AND <列> = '<线上导出的逐字基线>'` 守卫；**没有线上逐字基线就不得在本轮改写正文**。
  - 所有 `UPDATE` 携带 `updated_at = updated_at`。
- 来源: K-qa-rule-runtime-vs-migration-writes（hit_count 23）、K-qa-migration-preserve-auto-updated-timestamp（hit_count 7）

### Invariant G-4: 受控事实组是「集合相等」触发
- Rule: `QaCoverageKeyCatalog.validateControlledBody`（`QaCoverageKeyCatalog.kt:52-62`）用 `controlledCoverageGroups.firstOrNull { it.keys == parsed }` 判定——**覆盖集恰好等于某组**才触发正文逐字校验；多一个普通键即放行。四个组为 G1 `{confidentiality.materials}`、G2 `{fees.policy}`、G3 `{contract.party, contract.terms}`、G4 `{ip.arrangements}`（`:19-42`）。
- 调用点 3 处：`QaRuleManagementService.kt:75`（createRule）、`:105`（updateRule）、`:138`（setRuleEnabled(true)）。`updateRule` 在 `command.coverageKeys == null` 时用 `parseStored(existing.coverageKeys)` 复验——**只改关键词也会触发**。
- Applies to: 任何新增/修改 `coverage_keys` 的任务；任何打算通过运营 UI 保存受控规则的操作。
- Violation consequence: 规则不可保存、不可启用，且运营在 UI 上无自救路径。
- 来源: K-controlled-gate-trigger-exact-group

### Invariant G-5: coverage key 与 intent 必须成对
- Rule: `qa_rule.coverage_keys` 非空、但其中没有任何键被某条 intent 的 `requiredCoverageKeys` / `alternativeCoverageKeys` 引用时，该规则在 grounded 链路里**结构性不可达**（永远为 0，不是命中率低）。空 coverage 反而可达——`isCoverageEligible`（`AiReplyIntentCatalog.kt:655-662`）对空集返回 `intent.key !in coverageRequiredIntentKeys`。
- Applies to: 任何新增 catalog key、新增 intent、或给规则赋 coverage_keys 的任务。
- 强制：新增 catalog key 与新增 intent **必须同一计划同时提交**，并由 `QaCoverageKeyIntentParityTest` 守卫。
- 来源: K-coverage-key-orphan-makes-fact-unreachable（**注意该条 2026-08-26 的孤儿名单已过时，见 01 计划的实测**）

### Invariant G-6: `QaCoverageKeyCatalog` 新 Entry 只能追加在列表末尾
- Rule: `normalizeAndValidate`（`QaCoverageKeyCatalog.kt:121-130`）返回 `all().map { it.key }.filter { it in trimmed }`——**按目录声明顺序**。中插会改变既有规则下次保存时 `coverage_keys` 的字符串顺序，使一切逐字比对的迁移守卫失配（如 `V107:10`）。
- Applies to: 12 计划（若新增 key）。11 计划不动 catalog。
- 来源: K-coverage-catalog-append-only

### Invariant G-7: `requestKey` 哈希不得混入新 id 空间
- Rule: `requestKey = sha256(sourceVersion, index, requestText, intentKeys)`（`TrustReplyWorkbenchService.kt:1702-1715`）。02/03 引入的运营事实 id（`op1`、`op2`…）与 `paragraphPlan` 分组信息**绝不能进入该哈希**，否则 `trust_reply_workbench_state`（V83）中的历史 requestKey 全部失配。
- Applies to: 12、13。
- 来源: K-request-key-includes-intent-keys

## 各计划的范围冻结（防止后续 create-p 跑偏）

### 11-fact-supply（本轮唯一零代码计划）
**做**：新建 5 条 QA 规则占用「可达但无主」的 coverage key；修 `Project sensitivity concerns` 的受控键死锁；给 `Pre-contract IP boundary` 追加关键词；补一条「可达键必须有主」的守卫测试。
**不做**：任何 `reply_body` / `answer_body` 整体改写（无线上逐字基线，违反 G-3）；任何 Kotlin 改动；任何新 catalog key / 新 intent。

### 12-letter-closer（零新增 LLM 调用）
**做**：在 `verifyAssembly:1466-1468` 插入确定性收口——按 `sourceRuleIds` 去重、按主题归并、单 CTA 收口、全锁定时退回 `composeLockedItems`。
**不做**：任何 LLM 调用；逐条生成链路；逐条锁定语义；前端；索引。

### 13-letter-orchestrator
**做**：把 12 收口的第 3 步（主题归并产段）换成一次整封编排 LLM 调用；`paragraphPlan` / `topicOrder` 服务端给定；六道确定性校验；受控与冻结事实的逐字插槽；缺口挂主题不独立成段；失败退回 12。
**不做**：句子级 pinned；改逐条生成的提示词与协议；前端；索引回流；**G3 canonical body 的措辞变更**（需需求方书面确认，单独立计划）。

### 14-workbench-concurrency
**做**：单条操作只锁单条；条目级持久化端点 `PATCH /state/item`；并发守卫按作用域拆开；`autoBootstrap: false` + 「开始分析」按钮。
**不做**：三步界面、事实集、段落 pinned、运营事实（全在 15）。

### 15-workbench-three-step
**做**：三步页签（复用既有 page-nav 组件）；事实集表格；段落编辑 / 锁定 / 并入上段 / 上下移；「按回答说明生成」产出转为逐字运营事实 `op*`；重排端点。
**不做**：句子级 pinned；索引入库与回流。

### 16-unsupported-index
**做**：入库门槛放宽（handling 允许集合、`operatorInstruction` 可选、去掉「正文一字未改」）；`topic` / `finalParagraphText` / `editedByOperator` 三字段 + mapping 演进；topic 检索；CANDIDATE/ACTIVE 改为「是否已转化」；措辞通道 A（默认关闭）与待转事实通道 B。
**不做**：把索引作为**内容**来源接进生成链路；向量/语义检索；自动转事实。


### 17-fact-body-rewrite
**做**：迁移 V110 改写 id 9 / 18 / 23 三条的 `reply_body` + `answer_body`（去 `PhD team`、补官网与顾问名单口径、补匹配边界与匹配后披露），带「改动前片段」LIKE 守卫。
**不做**：任何 `coverage_keys` / `keywords` / `priority` / `enabled` 变动；新建规则（在 11）；引入任何新的金额、比例、时限或保证。
## 验证命令（七份可执行计划共用，唯一权威文本）

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# Flyway 迁移集成测试（opt-in，需要本地 Docker；默认被
# @EnabledIfSystemProperty(named="migrationIt", matches="true") 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且输出 `Tests run: N, Failures: 0, Errors: 0, Skipped: S`。
来源：项目根 `CLAUDE.md` 的 `## Commands` 章节（逐字照抄，未简化）。

## 线上基线（需求方 2026-08-28 已提供）

### 冻结规则（G-1 / G-2）

| id | reply_subject | 校验 |
|---|---|---|
| 1 | `About the talent program` | 状态如预期，MD5 已取 |
| 3 | `Application criteria` | 状态如预期，MD5 已取 |
| 21 | `Meeting arrangement` | 状态如预期，MD5 已取 |
| 24 | `Program overview` | 与 G-2 的迁移级交叉确认一致 |

**冲突核查（已完成）**：本轮六份计划的全部改动目标为 `Project sensitivity concerns`（id 20）、
`Pre-contract IP boundary`（V82 建）、以及 11 新建的 5 条规则——**与冻结四条无任何交集**。
11 的两条 `UPDATE` 仍保留 `AND id NOT IN (1, 3, 21, 24)` 作为第二道守卫。

### 其余核查结果

- **七个「可达但无主」覆盖键**：线上查询无匹配行 -> 迁移级基线 == 线上真相，11 的 5 条 INSERT 全部有效。
- **死锁行**：id 20，enabled，`coverage_keys = 'confidentiality.materials'`，正文仍为原状态 -> V109 的守卫子句会精确命中。
- **V82 四条受控事实正文**：与 `QaCoverageKeyCatalog.kt:19-42` 的 canonical **逐字一致**，未被运营改写。G3 / G4 的逐字约束成立。
- **待改写的三条正文**（`Application process` id 9、`Agency credentials and government cooperation` id 18、`Partner company information` id 23）：需求方 2026-08-28 已提供逐字基线 -> 已立 `17-fact-body-rewrite.md`（迁移 V110）承接，不再挂在 12 / 13 的 Out of scope。
- **无依据回答索引**：`{"items":[],"total":0}` -> 索引确认为**空**。16 的根因坐实是入库门槛，不是列表侧空值丢弃。

### 冻结事实正文中的三处危险（12 的 I-5、13 的 I-4 直接依赖）

- **id 1** 正文含 `${researchFields|your field}` 与 `${recentWorkTitle|your recent research}` 两处变量占位符，末段是动作句 `Would you be open to learning more about the program and the possible cooperation format?`
- **id 21** 正文末段含动作句 `Could you please let us know when you would be available?`，且含 en dash 的 `15-20`（原文为 U+2013）
- **id 3** 正文含双连字符 `--`：`We can discuss fit first -- no documents needed at this stage.`

三条**均不可改正文**，但其中两条自带 CTA、一条含占位符、两条含特殊标点。
12 的 CTA 收口与 13 的逐字比对必须对它们特殊处理；归一化只允许压缩空白，
**不得**触碰 `--`、U+2013 或 `${...}`。

## 迁移版本号分配（避免两份计划抢同一个号）

| 版本 | 计划 | 内容 |
|---|---|---|
| V109 | `11-fact-supply.md` | 5 条新规则 + 死锁修复 + IP 关键词追加 |
| V110 | `17-fact-body-rewrite.md` | 三条事实正文改写 |

当前仓库最大版本为 `V108__add_expert_types_to_batch_send_task_config.sql`。
**11 与 17 若并行开发，合并顺序必须保证 V109 先于 V110 落库**（Flyway 按版本号顺序执行，与合并顺序无关，但两份 PR 同时改 `db/migration` 时容易撞号——开分支前先看一眼目录）。
