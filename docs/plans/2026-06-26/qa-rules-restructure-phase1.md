# 开发计划：QA 规则分类重构 + 新增 FAQ 规则（Phase 1，数据层）

> 使用 create-p 技能编写。本计划是整体"QA 规则体系重构"的**第 1 个可独立交付切片**，只动数据（一条 Flyway 迁移）+ 回归测试，**不改匹配引擎、不加新列、不动前端逻辑**。
> 后续切片见文末「## 后续切片路线图」。

---

## 需求描述

**可观察结果**：QA 管理页中，现有 12 条规则被归入 6 个顶层主题分类（不再是 12 个一一对应的散分类）；同时新增一批高频问题规则（承诺视频、单一申报承诺、会议安排、资料保密、代理资质等），这些问题在被单独问到时能命中并自动回复。

**必须不变（Must NOT change）**：
- `QaMatchService` 的匹配算法（仍是单条命中、`matchedKeywordCount` → `priority` 决胜）。
- 现有 12 条规则的 `keywords / match_mode / priority / reply_subject / reply_body / auto_reply_enabled / handoff_required / enabled / display_name`——**只允许改 `category_id`**。
- 任何一封"当前已能命中某条现有规则"的邮件，重构后仍命中**同一条**规则（新规则只补盲区，不抢现有命中）。
- 会话状态机、`mail_record.matched_qa_rule_id` 单值外键语义、人工回复/挂起流程。

**超出范围（Out of scope，明确推迟）**：
- 多规则聚合 / 结构化拼接 / 缺口检测（→ Phase 2）。
- `compose_order` / `section_title` / 父子`supersede` 等新列与 OVERVIEW 父规则（→ Phase 2）。
- 人工组装台 UI、片段库、审计闭环（→ Phase 3）。
- LLM 缝合/翻译（→ Phase 3）。
- 前端按分类折叠分组的展示优化（→ Phase 2，可选）。

---

## 关键不变量

### Invariant I-1：现有规则匹配行为零变更
- Rule：迁移对现有 12 条种子规则**仅可 UPDATE `category_id` 一列**；`keywords/match_mode/priority/reply_subject/reply_body/auto_reply_enabled/handoff_required/enabled/display_name` 一个字符都不能改。
- Applies to：V19 迁移中针对现有行的所有 UPDATE 语句。
- Violation consequence：现存自动回复内容/命中结果被悄悄改写，回归不可控。

### Invariant I-2：新规则只补盲区，不造成命中漂移
- Rule：对代表性入站邮件语料，**任何在重构前能命中规则 X 的邮件，重构后仍命中规则 X**；新规则只对"重构前无任何命中"的邮件产生新命中。新规则的关键词 token 不得与现有规则关键词产生会改变现有结果的重叠。
- Applies to：V19 中所有新规则的 `keywords` 设计；`QaMatchService.match` 的读取路径。
- Violation consequence：老问题被新规则截胡，答非所问。

### Invariant I-3：分类外键完整、无悬空引用
- Rule：迁移顺序必须是「先插入新分类 → 再 UPDATE 所有规则 `category_id` 指向新分类 → 最后 DELETE 旧分类」；删除旧分类前不得有任何 `qa_rule` 仍指向它。
- Applies to：V19 迁移的语句顺序；`fk_qa_rule_category` 外键。
- Violation consequence：外键报错导致迁移失败，或规则指向被删分类。

### Invariant I-4：分类 code 唯一
- Rule：新增 6 个顶层分类的 `category_code` 互不重复，且与迁移执行期间仍存在的旧 code 不冲突（旧 code 在同一迁移内删除）。
- Applies to：`qa_category.category_code UNIQUE` 约束。
- Violation consequence：唯一约束冲突，迁移失败。

### Invariant I-5：新规则字段完整
- Rule：每条新规则必须有非空 `keywords`、`reply_body`、`match_mode IN ('ANY','ALL')`、`priority > 0`、有意义的 `display_name`，且 `category_id` 指向一个已存在的新分类。
- Applies to：V19 中所有新规则 INSERT。
- Violation consequence：`QaMatchService`/管理页/人工下拉显示异常。

---

## 现状审计

### QA 存储（qa_category + qa_rule，MySQL，Spring Data JDBC）

**Schema（V1 创建）**
- `qa_category`：`id` PK AI、`category_code VARCHAR(64) UNIQUE`、`category_name`、`description`、`enabled TINYINT default 1`、时间戳。
- `qa_rule`：`id` PK AI、`category_id BIGINT NOT NULL FK→qa_category(id)`、`keywords TEXT`、`match_mode VARCHAR(16) default 'ANY'`、`priority INT default 100`、`reply_subject`、`reply_body TEXT`、`auto_reply_enabled`、`handoff_required`、`enabled`、时间戳；`display_name VARCHAR(120)`（V14 增列）。
- 域对象：`QaCategory.kt`、`QaRule.kt`（不可变 data class，无 `compose_order`/`section_title` 等列）。

**种子现状**
- V3：插入 12 个分类（id 1–12，code = PROJECT_CONTENT…RETIRED）与 12 条规则（与分类 1:1）。
- V17/V18：按 `category_id + reply_subject` 给这 12 条规则补 `display_name`（V18 用 UNHEX 修编码）。→ **新迁移若用 `reply_subject` 定位现有规则，可精确命中且不破坏 display_name。**

**写路径（qa_rule / qa_category）**
1. 迁移 V3（种子）、V17/V18（display_name）——已应用，不可改。
2. `QaRuleManagementService.createRule/updateRule/setRuleEnabled`、`createCategory/setCategoryEnabled`（运营经 `/api/qa/*`）。本计划不改这些代码；新分类结构对其透明（仍按 categoryId 增改）。

**读路径（qa_rule）**
1. `QaMatchService.match` → `findAllEnabledOrdered()`（`WHERE enabled=1 ORDER BY priority,id`）——**自动回复引擎**。读 keywords/matchMode/priority/replySubject/replyBody/handoffRequired/autoReplyEnabled/id。**不读 category_id** → 重分类不影响匹配。✅
2. `QaRuleManagementService.listRules` → 按 priority 或 categoryId 列表，关联 category 名称（管理页表格 + 表单分类下拉，见 `app.js` 1389-1416）。
3. `ManualExpertMailService.composeQa(qaRuleId)`（行 167-179）：运营手动选 1 条规则回信，读 `replySubject/replyBody`，写 `matchedQaRuleId=rule.id`。
4. `PendingMailOperationService`（行 114）：运营挂起操作选规则，写 `matchedQaRuleId=rule.id`。
5. `MailMonitoringService`（行 126-148）：按 `matchedQaRuleId` 反查 `displayName` 做监控展示。

**读路径（qa_category）**
- `QaRuleManagementService.listCategories`（`findAllByOrderByCategoryCodeAsc`，无 enabled 过滤）→ `/api/qa/categories` → `app.js` 填充规则表单分类下拉 + 表格 categoryName 列。

**测试现状**
- `QaMatchServiceTest`、`QaRuleManagementServiceTest`：均为 **Mockito 单元测试**，用假 `QaRule(categoryId=…)` 列表，**不加载 Flyway/真实种子，不断言分类总数**。→ V19 迁移不会破坏现有单测；但需新增回归测试守 I-2。

**交互点**
- 写路径2/读路径2、5 都依赖 `category_id` 关联出 category 名称：重分类后管理页与监控页展示的分类名会变（这是预期的可观察结果，非缺陷）。
- 读路径1（引擎）不依赖 category：**重分类对自动回复零影响**，命中变化只可能来自"新增规则"——故 I-2 是唯一真实风险点。

---

## 实现方案

### 顶层分类设计（6 个主题）
| category_code | category_name | 归入规则 |
|---|---|---|
| PROGRAM_AND_ELIGIBILITY | 项目与对象 | 现有1 项目内容、2 申报方式、3 申报条件与材料 |
| ROLE_AND_WORKSTYLE | 角色与工作方式 | 现有4 角色、5 职责权益、6 全职/兼职、7 工作地点 |
| FUNDING_AND_TIMELINE | 钱与时间 | 现有8 薪资资金、9 申报流程、10 截止时间 + 新增「成功率/未入选」 |
| PROCESS_ACTIONS | 流程动作 | 新增「承诺视频VCR」「单一申报承诺」「入选后流程」 |
| TRUST_AND_COMPLIANCE | 信任与合规 | 现有11 我们的优势 + 新增「资料保密·绝不收费」「代理资质·政府合作证明」「多代理·权益保障」「项目敏感性」 |
| COMMUNICATION_AND_OTHER | 沟通与其它 | 现有12 退休专家 + 新增「会议安排」「只邮件·不用LinkedIn」「合作企业信息」 |

> OVERVIEW 主题与父规则**不在本切片**（需 supersede 机制，Phase 2）。

### 任务（单一迁移 V19）— 遵守 I-1..I-5

**Task 1**：在 `V19__restructure_qa_categories_and_seed_new_rules.sql` 中，按序写：

1. **插入 6 个新分类**（I-4）：`INSERT INTO qa_category (category_code, category_name, description, enabled)`，6 行，code 同上表。
2. **重指现有 12 条规则的 category_id**（I-1、I-3）：用 `UPDATE qa_rule SET category_id=(SELECT id FROM qa_category WHERE category_code='<theme>') WHERE reply_subject='<原 subject>'`，逐条按 `reply_subject` 精确定位（沿用 V17 的定位方式），**只改 category_id**。12 条对应关系见上表。
3. **删除旧 12 个分类**（I-3）：`DELETE FROM qa_category WHERE category_code IN ('PROJECT_CONTENT','ENTRY_FORMAT','APPLYING_CRITERIA','ROLE','DUTY_AND_RIGHT','FULL_TIME_PART_TIME','WORKPLACE','SALARY','PROJECT_STREAM','DEADLINE','OUR_ADVANTAGE','RETIRED')`。必须在第 2 步之后执行。
4. **插入新规则**（I-2、I-5）：每条 `INSERT INTO qa_rule (category_id, keywords, match_mode, priority, reply_subject, reply_body, display_name, auto_reply_enabled, handoff_required, enabled)`，`category_id` 用子查询按新 code 取。回复正文取自 `docs/qa提炼-完整版.md` 第二部分 B 段。关键词须经 Task 2 校验不与现有规则冲突。
   - 新规则清单（display_name / 主题 / 关键词草案）：
     - 承诺视频 VCR / PROCESS_ACTIONS / `record a video,confirmation video,self-statement video,show passport,read the statement`
     - 单一申报承诺 / PROCESS_ACTIONS / `apply through one company,duplicate application,only apply,single agency,commitment to apply`
     - 入选后流程 / PROCESS_ACTIONS / `after selected,research topic,labor contract,sign contract,after selection`
     - 成功率/未入选 / FUNDING_AND_TIMELINE / `success rate,not selected,chance of success,probability of selection`
     - 资料保密·绝不收费 / TRUST_AND_COMPLIANCE / `confidential,keep my documents,never charge,any fee,money transfer`
     - 代理资质·政府合作证明 / TRUST_AND_COMPLIANCE / `accredited,official agency,prove government,cooperation with government,authorized`
     - 多代理·权益保障 / TRUST_AND_COMPLIANCE / `other agency,switch agency,guarantee selection,subsidy not paid,protect my rights`
     - 项目敏感性 / TRUST_AND_COMPLIANCE / `sensitive project,classified,national project confidential,security concern`
     - 会议安排 / COMMUNICATION_AND_OTHER / `arrange a meeting,zoom,teams,webex,time zone,available for a call`
     - 只邮件·不用LinkedIn / COMMUNICATION_AND_OTHER / `only email,not on linkedin,no social media,contact me by email`
     - 合作企业信息 / COMMUNICATION_AND_OTHER / `which company,partner company,company profile,is it a good match`
   - `priority` 统一给较大值（如 120）以低于"成功率"等？——决胜先看命中词数，故 priority 仅在同词数时生效；新规则间默认 120，避免压过现有规则在边界场景的优先级（满足 I-2 由 Task 2 实测兜底）。

**Task 2**（回归测试，守 I-1/I-2）：新增 `QaMatchServiceRestructureTest.kt`。构造一个**镜像 V19 最终规则集**（旧 12 条原样 + 新规则，关键词与迁移一致）的假 repo，断言：
- 代表性"老意图"邮件仍命中原规则：如 Khooshab 式总览信 → 仍命中 `About the talent program`（项目内容，行为同重构前）；问 funding 的 → `Funding support`；问 deadline 的 → `Application deadline`。
- "新意图"邮件命中新规则：纯问"record a confirmation video / show passport" → 承诺视频规则；"can I apply through more than one agency" → 单一申报承诺；"is this company a good match" → 合作企业信息。
- 不存在"老意图邮件被新规则截胡"的用例（逐条对照 I-2）。

---

## 变更文件清单

| # | 文件 | 动作 | 说明 |
|---|------|------|------|
| 1 | `src/main/resources/db/migration/V19__restructure_qa_categories_and_seed_new_rules.sql` | 新增 | 插入6新分类、重指12规则category_id、删12旧分类、插入新规则 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceRestructureTest.kt` | 新增 | I-1/I-2 回归：老意图不漂移、新意图被覆盖 |

文件数：2（≤10）✅　子系统：1（QA 数据/匹配）✅　新增共享存储列：0 ✅

---

## 验收标准

- **I-1**：对比迁移前后 `qa_rule` 现有 12 行，除 `category_id` 外所有列逐字段相等（可用 SQL 快照或测试断言）；`display_name` 非空且未变。
- **I-2**：`QaMatchServiceRestructureTest` 全绿——所有"老意图"用例命中规则 id 与重构前一致；所有"新意图"用例命中对应新规则；无漂移用例。
- **I-3**：迁移在干净库 + 现有库上 `mvn flyway`/启动均成功；执行后 `SELECT COUNT(*) FROM qa_rule r LEFT JOIN qa_category c ON r.category_id=c.id WHERE c.id IS NULL` = 0（无悬空）；旧 12 个 category_code 已不存在。
- **I-4**：`SELECT category_code,COUNT(*) FROM qa_category GROUP BY category_code HAVING COUNT(*)>1` 为空。
- **I-5**：`SELECT * FROM qa_rule WHERE keywords='' OR reply_body='' OR priority<=0 OR match_mode NOT IN ('ANY','ALL')` 为空；新规则 display_name 全非空。
- **集成**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿；启动后 `/api/qa/categories` 返回 6 个分类，`/api/qa/rules` 中现有规则 categoryName 已归入新主题、新规则出现在对应主题下。

---

## 后续切片路线图（本计划之外，依赖 Phase 1）

- **Phase 2 — 组装元数据 + 多规则聚合引擎**：加列 `compose_order`、`section_title`、父子/`supersede`；新增 OVERVIEW 父规则；`QaMatchService` 改为返回有序多命中并按主题去重拼接；缺口检测；命中 ≥2 或有缺口转人工。（注意：`mail_record.matched_qa_rule_id` 为单值外键，多规则落库需在此切片设计关联表或主规则约定。）
- **Phase 3 — 人工组装台 UI + 审计闭环**：邮件详情页"片段面板 + 两层草稿 + 缺口清单"，运营增删重排规则成稿；记录"引擎命中 vs 人工选用 vs 实际发送"用于规则优化；可选 LLM 缝合/翻译。
