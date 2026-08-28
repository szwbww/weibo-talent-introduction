# 17 三条事实正文改写（V110）

顺序权威：`10-reply-orchestration-order.md`。依赖 `11-fact-supply.md` **仅为迁移版本号顺序**（V109 → V110），无逻辑依赖。可与 12～16 任意一份并行。

## 需求描述

**Observable outcome**
1. 回信中不再出现 `our PhD team`——`Application process`（id 9）的正文改为「我们的团队」，并把「初审 → 匹配 → 与企业共同准备 → 提交评审」分层说清，同时给出「无固定匹配期限 / 全周期约六个月或更久」。
2. `Agency credentials and government cooperation`（id 18）的正文给出公司官网，并明确「不公开顾问名单」；不再重复陈述项目公开性（那已由 V105 的 `Programme name and public visibility` 承载）。
3. `Partner company information`（id 23）的正文说清「按研究方向匹配、不分配通用职位」与「匹配后提供什么」。

**What must NOT change**
- `qa_rule` 中 id ∈ {1, 3, 21, 24} 的四行任何列都不变（G-1）。
- 三条目标规则的 `coverage_keys` / `priority` / `enabled` / `reply_policy` / `category_id` **全部不变**——本计划只改 `reply_body` 与 `answer_body`。
- 三条规则的 `keywords` 不变（本计划不做关键词改动）。
- V82 四条受控事实的正文不变。
- `updated_at` 不被刷新（K-qa-migration-preserve-auto-updated-timestamp）。

**Out of scope**
- 关键词改动（`Pre-contract IP boundary` 的关键词追加在 11）。
- 任何 `coverage_keys` 变动。
- 新建规则（在 11）。

**执行前置（阻塞，必须由需求方书面确认）**
本计划改写的是**对外发给海外学者的话术**。三段新正文由以下两份**需求方自己的材料**推导而成，**不是我方发明**：
- 需求方 2026-08-28 提供的人工真实回复（Al-Bitar 一信）；
- 需求方提供的《QA事实库整合方案·完整字段版》第 11、14、15、20、21、07、08 条。

推导对照见「实现方案」每条下的「来源」小节。**需求方须逐段签字确认后方可执行 T-1。**

## 关键不变量

### Invariant I-1: 冻结行的双重守卫
- Rule: 本计划的每条 `UPDATE qa_rule` 必须按 `reply_subject` 定位，**并且**携带 `AND id NOT IN (1, 3, 21, 24)`。
- 冲突核查（已完成）：三条目标为 id 9 / 18 / 23（需求方 2026-08-28 线上查询确认），与冻结四条无交集。守卫仍保留。
- 来源: G-1 / G-2

### Invariant I-2: 正文改写必须带「改动前」守卫，且守卫对换行编码不敏感
- Rule: 每条 `UPDATE` 必须携带一个 `AND reply_body LIKE '%<改动前的独有片段>%'` 守卫。**不得**用 `AND reply_body = '<整段逐字>'`——需求方提供的基线是聊天粘贴文本，其换行编码（`\n` / `\r\n`）与段间空行数量无法从粘贴内容确定，逐字相等会因不可见字符差异而静默不匹配（UPDATE 影响 0 行，迁移「成功」但什么都没改）。
- 选定的独有片段（均取自需求方提供的逐字基线，且都是本次要删除或替换的句子的一部分）：
  - id 9：`our PhD team matches relevant enterprises`
  - id 18：`talent-office certificates and participation in official talent summits`
  - id 23：`Once the partner enterprise is confirmed`
- Violation consequence: 覆盖运营运行时改动（G-3），或迁移静默空转而无人察觉。
- 来源: G-3 / K-qa-rule-runtime-vs-migration-writes

### Invariant I-3: `reply_body` 与 `answer_body` 必须同时改，且取值相同
- Rule: 两列在 V82 / V105 的新建规则里一直是同值（`V82:104-116`、`V105:22-23` 均把同一字符串同时赋给两列）。本计划改写时两列必须写同一个新值。
- 现状风险：`V79__add_qa_answer_body.sql` 当初只回填 `answer_body`；若某条规则的两列已经不同，只改一列会让 grounded 链路（读 `answerBody`）与 legacy 链路（读 `replyBody`）说两套话。
- 执行前置检查：迁移前先跑
  ```sql
  SELECT id, reply_subject, MD5(reply_body) = MD5(answer_body) AS same
    FROM qa_rule WHERE id IN (9, 18, 23);
  ```
  若某条 `same = 0`，**停止并回 create-p**——说明该条已有分叉，改写口径需要重新决定。
- 来源: K-answerbody-source-exclusive、K-qa-fact-body-required-no-legacy-fallback

### Invariant I-4: 不得与 11 新建的事实重复陈述
- Rule: 11 新建的 `Partner enterprise types and R&D needs` 已承载「不按固定公开企业名单工作 + 常见研发需求类型」；V105 的 `Programme name and public visibility` 已承载「项目不公开挂牌、无独立项目官网」。id 23 与 id 18 的新正文**不得**重复这两处陈述。
- 理由：12 的去重按 `sourceRuleIds` 判定，两条**不同规则**说同一件事不会被去重，会在一封信里出现两遍近义表述。
- Violation consequence: 12 上线后仍有语义重复，且查不出原因（去重逻辑没错，是事实库自身冗余）。
- 来源: original（12 的 I-2 的上游约束）

### Invariant I-5: 新正文不得引入未经审核的新承诺
- Rule: 三段新正文的每一个事实性断言，都必须能在需求方提供的人工真实回复或方案 docx 中找到对应出处。不得新增金额、比例、时限、机构名称或任何形式的保证。
- 验收方式：见「验收标准」的逐句出处对照表。
- 来源: original

## 现状审计

### 三条目标规则的线上逐字基线（需求方 2026-08-28 提供）

**id 9 `Application process`**（coverage: `application.steps,application.timeline`，V76:53-56）
```
First, you submit the required materials.

Then, our PhD team matches relevant enterprises according to your research direction and prepares the application documents.

Finally, the materials are submitted for review. The whole process usually takes about half a year or longer.
```
问题：① `our PhD team` 是内部说法，对外无意义且不可核实；② 「提交材料」被放在第一步，与实际流程（先谈匹配、CV 即可）不符；③ 没有区分「企业匹配期限」与「完整申请周期」。

**id 18 `Agency credentials and government cooperation`**（coverage: `company.verification_evidence`，V76:19-21）
```
We completely understand your caution, and we are happy to share information you can verify independently. Our agency is a legitimately registered company, though the talent program itself is confidential and has no public project website.

Our cooperation with the government is further documented through talent-office certificates and participation in official talent summits, which we are glad to share as supporting evidence. Please feel free to verify these before proceeding; we would like to build trust step by step at your pace.
```
问题：① 没给官网——而专家问「你们是谁」时官网是最基本的可核实项；② 「项目本身保密且无公开项目官网」与 V105 的 `Programme name and public visibility` 重复（I-4）；③ 没有回答「顾问名单」这个 Al-Bitar 明确问过的问题；④ `participation in official talent summits` 是较强的断言，人工回复里用的是更稳的 `records of official talent activities ... where available`。

**id 23 `Partner company information`**（coverage: `enterprise.matching`，V76:31-33）
```
Matching is based on your research direction. Once the partner enterprise is confirmed, we will send its full profile, website, and address, along with how its direction aligns with your expertise.
```
问题：① 没有明确「不分配通用职位」这条边界——而这正是专家最担心的；② 「confirmed」措辞过强，人工回复用的是 `Once a potential match is identified`。

### Write / Read paths
与 11 的现状审计相同（`qa_rule` 的 4 条写路径、5 条读路径）。本计划只新增一条迁移写入。
**Interaction point IP-1**：写路径 1（迁移改正文）× 写路径 2/3（运营 UI 运行时改正文）—— 由 I-2 的 LIKE 守卫处理。
**Interaction point IP-2**：本计划的新正文 × 11 新建规则 / V105 规则的正文 —— 由 I-4 处理。

### 前端样式盘点
不适用——本计划不触及任何前端文件。

## 实现方案

### T-0：执行前置检查（I-3）
跑 `SELECT id, reply_subject, MD5(reply_body) = MD5(answer_body) AS same FROM qa_rule WHERE id IN (9, 18, 23);`
三行 `same` 必须全为 1。任一为 0 则停止执行并回 create-p。

### T-1：新增迁移 `V110__rewrite_three_qa_fact_bodies.sql`

下一个可用版本号是 **V110**（11 占用 V109；当前最大为 `V108__add_expert_types_to_batch_send_task_config.sql`）。
以下 SQL 是**最终代码，逐字复制**。

```sql
-- V110: Rewrite three QA fact bodies. Plan docs/plans/2026-08-28/17-fact-body-rewrite.md.
--
-- I-1: every UPDATE is guarded on the four frozen rule ids.
-- I-2: every UPDATE carries a LIKE guard on a distinctive fragment of the
--      pre-change body, so an operator's runtime edit is never overwritten and
--      a silent no-op is detectable (0 rows affected).
-- I-3: reply_body and answer_body are always written together with the same value.
-- I-4: no sentence here duplicates the programme-visibility fact (V105) or the
--      partner-enterprise-types fact (V109).
-- G-3: updated_at is preserved on every statement.

-- ============================================================
-- 1. Application process (id 9) — remove the internal "PhD team" wording,
--    layer the steps correctly, and separate matching timing from the full cycle.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'After your materials are received, our team conducts an initial eligibility review and begins enterprise matching.\n\nOnce a suitable enterprise is identified, we prepare the application documents together with that enterprise and submit the completed materials for review.\n\nThere is no fixed enterprise-matching deadline, as the timing depends on your research direction and the availability of a genuinely suitable partner. The complete matching, application preparation, submission and review process generally takes approximately six months or longer.',
       answer_body = 'After your materials are received, our team conducts an initial eligibility review and begins enterprise matching.\n\nOnce a suitable enterprise is identified, we prepare the application documents together with that enterprise and submit the completed materials for review.\n\nThere is no fixed enterprise-matching deadline, as the timing depends on your research direction and the availability of a genuinely suitable partner. The complete matching, application preparation, submission and review process generally takes approximately six months or longer.',
       updated_at = updated_at
 WHERE reply_subject = 'Application process'
   AND reply_body LIKE '%our PhD team matches relevant enterprises%'
   AND id NOT IN (1, 3, 21, 24);

-- ============================================================
-- 2. Agency credentials and government cooperation (id 18) — add the company
--    website, answer the adviser-list question, soften the talent-activity
--    claim to the wording used in the operator's own reply, and drop the
--    programme-visibility sentence (owned by V105 'Programme name and public
--    visibility').
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'We completely understand your caution, and we are happy to share information you can verify independently. Our official company website is https://www.qingfeitalent.com, and our agency is a legitimately registered company.\n\nFor privacy and confidentiality reasons, we do not publish a current adviser list. We can, however, provide company registration information and supporting talent-office documentation, including talent-office certificates and records of official talent activities where available, for independent verification before you proceed.',
       answer_body = 'We completely understand your caution, and we are happy to share information you can verify independently. Our official company website is https://www.qingfeitalent.com, and our agency is a legitimately registered company.\n\nFor privacy and confidentiality reasons, we do not publish a current adviser list. We can, however, provide company registration information and supporting talent-office documentation, including talent-office certificates and records of official talent activities where available, for independent verification before you proceed.',
       updated_at = updated_at
 WHERE reply_subject = 'Agency credentials and government cooperation'
   AND reply_body LIKE '%talent-office certificates and participation in official talent summits%'
   AND id NOT IN (1, 3, 21, 24);

-- ============================================================
-- 3. Partner company information (id 23) — state the matching boundary
--    explicitly and soften "confirmed" to "identified". The "no fixed public
--    list of companies" sentence is deliberately NOT repeated here; it belongs
--    to the V109 fact 'Partner enterprise types and R&D needs' (I-4).
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'Matching is based on your research background and the specific technical needs of potential Chinese partners; experts are not assigned to generic vacancies.\n\nOnce a potential match is identified, we will provide the company''s full profile, website and address, together with a clear explanation of the proposed technical work and how it aligns with your expertise.',
       answer_body = 'Matching is based on your research background and the specific technical needs of potential Chinese partners; experts are not assigned to generic vacancies.\n\nOnce a potential match is identified, we will provide the company''s full profile, website and address, together with a clear explanation of the proposed technical work and how it aligns with your expertise.',
       updated_at = updated_at
 WHERE reply_subject = 'Partner company information'
   AND reply_body LIKE '%Once the partner enterprise is confirmed%'
   AND id NOT IN (1, 3, 21, 24);
```

> **换行编码提示**：上面用 `\n\n` 表示段落分隔，与 `V46__qa_reply_body_paragraphs.sql` 建立的段落约定一致。执行方须确认所用 MySQL 客户端/连接的 `NO_BACKSLASH_ESCAPES` 未开启（Flyway 默认不开）；若开启，`\n` 会被当作字面量两个字符。落库后用 A-1 的目测项确认段落渲染正常。

#### 来源（I-5 的逐句出处）

**id 9**
| 新正文句子 | 出处 |
|---|---|
| `After your materials are received, our team conducts an initial eligibility review and begins enterprise matching.` | 人工回复第六段 `After materials are received, our team conducts an initial eligibility review and begins enterprise matching.` |
| `Once a suitable enterprise is identified, we prepare the application documents together with that enterprise and submit the completed materials for review.` | 方案 docx 第 20 条 |
| `There is no fixed enterprise-matching deadline, as the timing depends on ...` | 人工回复第六段 + 方案 docx 第 15 条 |
| `The complete matching, application preparation, submission and review process generally takes approximately six months or longer.` | 人工回复第六段（逐字）+ 方案 docx 第 21 条 |

**id 18**
| 新正文句子 | 出处 |
|---|---|
| `We completely understand your caution, and we are happy to share information you can verify independently.` | **保留原正文首句**（未改） |
| `Our official company website is https://www.qingfeitalent.com` | 人工回复第五段（逐字 URL） |
| `our agency is a legitimately registered company` | **保留原正文**（未改） |
| `For privacy and confidentiality reasons, we do not publish a current adviser list.` | 人工回复第五段（逐字）+ 方案 docx 第 08 条 |
| `provide company registration information and supporting talent-office documentation ... where available` | 人工回复第五段 + 方案 docx 第 07 条（`records of official talent activities may also be provided where available`） |

**id 23**
| 新正文句子 | 出处 |
|---|---|
| `Matching is based on your research background and the specific technical needs of potential Chinese partners` | 人工回复第二段 |
| `experts are not assigned to generic vacancies` | 人工回复第二段（`assign experts to generic vacancies`）+ 方案 docx 第 11 条 |
| `Once a potential match is identified, we will provide the company's full profile, website and address, together with a clear explanation of the proposed technical work` | 人工回复第二段（逐字）+ 方案 docx 第 14 条 |

**未引入任何新的金额、比例、时限、机构名称或保证。**

### T-2：新增 `QaFactBodyRewriteMigrationTest.kt`
形式照 `ProgrammeIdentityFactsMigrationTest.kt`（文本级断言，不依赖 Docker）。断言：
1. `UPDATE qa_rule` 出现次数 == 3；把全文按 `UPDATE qa_rule` 切分后，每条语句体内均含 `AND id NOT IN (1, 3, 21, 24)`；该子串全文出现次数 == 3（I-1）。
2. 每条语句体内均含 `reply_body LIKE '%` 守卫，且三个片段字符串与本计划 I-2 列出的三个逐字一致（I-2）。
3. `updated_at = updated_at` 出现次数 == 3（G-3）。
4. 每条语句同时设置 `reply_body =` 与 `answer_body =`，且**同一条语句内两者的值字符串完全相同**（I-3；用正则提取后比对）。
5. 全文不含 `PhD team`、不含 `participation in official talent summits`、不含 `Once the partner enterprise is confirmed` 作为**新值**出现（只允许出现在 LIKE 守卫里）。
6. 全文不含 `coverage_keys` / `priority` / `enabled` / `reply_policy` / `keywords` 的赋值（What must NOT change）。
7. 全文不含 `fixed public list of companies`（I-4，防与 V109 的新事实重复）；不含 `no public project website` / `not publicly listed`（I-4，防与 V105 重复）。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V110__rewrite_three_qa_fact_bodies.sql` | 新增（T-1） |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaFactBodyRewriteMigrationTest.kt` | 新增（T-2） |

合计 2 个文件，1 个子系统（qa 数据层），0 个生产 Kotlin 文件。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactBodyRewriteMigrationTest

# 单条测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactBodyRewriteMigrationTest#'each update carries a like guard on the pre-change body'

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

- **I-1**：`grep -c "AND id NOT IN (1, 3, 21, 24)" src/main/resources/db/migration/V110__*.sql` == 3，且逐条语句体内均含该守卫（T-2.1）。
- **I-2**：T-2.2 通过；三个 LIKE 片段与本计划列出的逐字一致。
- **I-3**：T-0 的前置查询三行 `same` 全为 1；T-2.4 通过。
- **I-4**：T-2.7 通过。
- **I-5**：「来源」小节的三张对照表逐行核对，每句都能落到人工回复或方案 docx 的具体位置；**由需求方在执行前签字确认**。
- **What must NOT change**：T-2.6 通过。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 三条正文已更新且分段正常
- 前置条件：V110 已应用。
- 操作步骤：在「QA 事实库」分别打开 `Application process`、`Agency credentials and government cooperation`、`Partner company information` 三条规则。
- 预期结果：正文为新文本；**段落之间有空行**（不是 `\n` 字面量、不是挤成一段）；三条的关键词、优先级、启用状态、回复策略与更新前一致。
- 覆盖：observable outcome 1、2、3；T-1 的换行编码提示

### A-2: 回信中不再出现 "PhD team"
- 前置条件：一封询问申请流程的来信。
- 操作步骤：工作台点「一键预判」并整合，通读正文。
- 预期结果：正文出现 `our team conducts an initial eligibility review`，**全文不含 `PhD team`**。
- 覆盖：observable outcome 1

### A-3: 询问资质时给出官网与顾问名单口径
- 前置条件：一封问「你们是谁 / 能不能给我可核实的材料 / 有没有现任顾问名单」的来信。
- 操作步骤：同 A-2。
- 预期结果：正文含 `https://www.qingfeitalent.com`，含「因隐私与保密原因不公开现任顾问名单」的表述，含「可提供公司注册信息与人才办文件供独立核实」。
- 覆盖：observable outcome 2

### A-4: 不与其他事实重复陈述
- 前置条件：一封同时问「合作什么类型企业」「项目有没有官网」「怎么匹配」的来信（会同时命中 V109 新事实、V105 事实与 id 23）。
- 操作步骤：同 A-2，通读正文。
- 预期结果：`fixed public list of companies` 只出现一次（来自 V109 的事实）；`not publicly listed` / `no public project website` 只出现一次（来自 V105 的事实）；不出现两处近义重复。
- 覆盖：I-4；IP-2

### A-5（回归）: 冻结四条未被改动
- 前置条件：V110 应用**之前**保存
  ```sql
  SELECT id, reply_subject, keywords, priority, enabled, reply_policy, coverage_keys,
         MD5(reply_body) AS body_md5, MD5(answer_body) AS answer_md5, updated_at
    FROM qa_rule WHERE id IN (1, 3, 21, 24);
  ```
- 操作步骤：应用后再查一次，逐字段对比。
- 预期结果：完全相同，含 `updated_at`。
- 覆盖：What must NOT change 第 1 项；I-1

### A-6（回归）: 三条目标规则的其余列与 updated_at 未变
- 前置条件：V110 应用前保存
  ```sql
  SELECT id, reply_subject, keywords, priority, enabled, reply_policy, coverage_keys, updated_at
    FROM qa_rule WHERE id IN (9, 18, 23);
  ```
- 操作步骤：应用后再查一次。
- 预期结果：除 `reply_body` / `answer_body` 外**所有列完全相同**，`updated_at` 未刷新。
- 覆盖：What must NOT change 第 2、3、5 项；G-3

### A-7（回归）: 迁移可重复应用（幂等）
- 前置条件：V110 已应用一次。
- 操作步骤：在测试库上手工再次执行 V110 的三条语句。
- 预期结果：三条均因 LIKE 守卫不再匹配而影响 0 行，无报错。
- 覆盖：I-2；IP-1

### A-8（回归）: 受控事实正文未被波及
- 前置条件：V110 应用前后各查一次
  ```sql
  SELECT reply_subject, MD5(answer_body) FROM qa_rule
   WHERE reply_subject IN ('Application material confidentiality','Participant fee policy',
                           'Contract arrangements','Pre-contract IP boundary');
  ```
- 预期结果：四行 MD5 完全相同。
- 覆盖：What must NOT change 第 4 项
