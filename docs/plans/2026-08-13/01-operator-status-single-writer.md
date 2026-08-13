# P-A：operator_status 收敛为唯一写入口

优先级 **P0（根源 P1）** ｜ 前置：无 ｜ 子系统：1（后端） ｜ 文件数：9
**发布耦合：必须与 P-D 同一发布列车**

## 需求描述

**Observable outcome**

1. 运营在专家详情页手动发送介绍邮件后，专家列表状态由「未联系」变为「已联系」，无需额外操作。
2. 该专家不再计入批量发送待发送池。
3. 历史上「已发过介绍信但仍显示未联系」的存量联系人一次性修正（生产实测 1 行）。
4. 全仓 `operator_status` 的**自动**写入收敛为唯一出口，为 P-D 提供可断言白名单。

**What must NOT change**

- `ExpertOperatorStatusService.changeStatus()`（运营手工改状态）的可设值域与行为不变。
- `EMAIL_INVALID` **不进** `OperatorStatus` 枚举（需求方已确认）。
- 批量发送对外产出（execution 日志的 target / success / skipped）不变。
- 手动发送 `COMPOSE_TEMPLATE` / `MATERIAL_REMINDER` 时 `operator_status` 不得改动。
- `ConversationStateService.transition` 与 `nextStatus()` 的会话状态机逻辑不变。
- `ExpertIndexWriterService` 零改动（ES 写入语义留给 P-B）。

**Out of scope**：ES 三层一致性（P-B）；对账作业（P-C）；守卫测试（P-D，同发布）；
ES 同步改挂 `afterCommit`（独立立项，批量路径同病）。

## 关键不变量

### I-1：operator_status 单调不回退
- **Rule**：自动写入路径只允许沿 `OperatorStatus` ordinal 正向推进。
  目标 ordinal ≤ 当前 ordinal 时直接返回入参对象，不写 DB、不写 ES。
- **Applies to**：`ExpertOperatorStatusService.updateAutomatically`（改造后唯一自动出口）。
- **Violation consequence**：给已 `REPLIED`(2) 的专家手动发任意邮件会把状态打回 `CONTACTED`(1)，
  该专家重新进入待发送池被重复触达。**比当前 bug 更严重。**
- **证据**（现有保护只覆盖单一分支）：

```kotlin
// ExpertOperatorStatusService.kt:56-59 逐字
if (targetStatus == OperatorStatus.REPLIED &&
    current != null &&
    current.ordinal > OperatorStatus.REPLIED.ordinal) {
    return contact
}
```

- **来源**：original

### I-2：EMAIL_INVALID 是枚举外的旁路终态
- **Rule**：`contact.operatorStatus == "EMAIL_INVALID"` 时 `updateAutomatically`
  **无条件短路返回**。该值不加入枚举，不参与 ordinal 比较。
- **Violation consequence**：`OperatorStatus.entries.firstOrNull { it.name == "EMAIL_INVALID" }`
  恒为 `null`（枚举仅 6 值：NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED /
  INVITED / COMPLETED），当前所有保护对该值全部落空 → 已知无效邮箱被洗成 `CONTACTED`，
  反复投递，损害发件域声誉。
- **决策依据**（需求方已确认"不要"加进枚举）：`index.html:485` 的状态下拉只有
  `NOT_CONTACTED` 一个 option；`app.js:609-616` 的 `operatorStatusLabels` 仅 6 个键，
  全文 0 处 `EMAIL_INVALID`。加进枚举会让 `changeStatus`
  （经 `ExpertContactManagementController:156` 对外暴露）接受该值，属未经要求的 API 值域扩张。
- **来源**：original

### I-3：唯一自动写入口（本计划核心）
- **Rule**：全仓对 `expert_contact.operator_status` 的**自动**写入有且仅有
  `ExpertOperatorStatusService.updateAutomatically` 一处。
  `ManualOutreachTxHelper` 与 `ManualExpertMailService` 均改为调用它，不得自持实现。
  允许的例外仅两处，须在 P-D 白名单显式声明：
  `ExpertOperatorStatusService.changeStatus`（人工）、
  `ManualInitialOutreachService:611`（建行初始化 `NOT_CONTACTED`）、
  `ManualInitialOutreachService:706`（退信标记 `EMAIL_INVALID`）。
- **Violation consequence**：两套独立实现并存正是本次 bug 的成因——
  `ManualOutreachTxHelper:46` 写字符串、`ExpertOperatorStatusService:61` 走枚举，
  二者可各自演化而互不感知。
- **证据**：`grep -rn "OperatorStatus.CONTACTED" src/main/kotlin` → **0 hits**。
  最常用的状态从未走过枚举，类型系统零保护。
- **来源**：K-expert-contact-two-write-sites（本次复核确认写入点全集）

### I-4：mailType → operatorStatus 白名单
- **Rule**：`sendManualMail` 仅对 `INTRODUCTION → CONTACTED`、
  `MEETING_INVITATION → INVITED` 推进；其余一律返回 `null` 表示不动状态。
- **值域证据**：`composed.mailType` 来自 `ManualExpertMailService:245` 的
  `rendered.mailType ?: "COMPOSE_TEMPLATE"`；`mail_compose_template.mail_type` 在迁移中的
  实际值域经统计为 `INTRODUCTION`(3) / `MATERIAL_REMINDER`(8) / `MEETING_INVITATION`(3)。
  合并代码兜底值，全集 = {INTRODUCTION, MATERIAL_REMINDER, MEETING_INVITATION, COMPOSE_TEMPLATE}，
  与 `nextStatus()`（`:277-285`）的 when 分支**完全一致**。
- **映射依据**：`MEETING_INVITATION → INVITED` 取自自动路径既有语义
  `AutoMailReplyService:484`：`updateAutomatically(meetingContact, OperatorStatus.INVITED, "MEETING_INVITATION_SENT")`。
- **来源**：original

### I-5：ES 中 NOT_CONTACTED = 字段缺失
- **Rule**：ES 侧"未联系"的唯一表示是 `operatorStatus` 字段**不存在**。
  本计划**不改** `ExpertIndexWriterService`，执行方不得"优化"成写字符串。
- **证据**：`ExpertIndexWriterService:76` 逐字 `ctx._source.remove('operatorStatus')`；
  消费端 `ExpertSearchService:122-127` 的 `must_not exists operatorStatus`。
- **来源**：original

> 本计划**不触及任何前端文件**，按 create-p 规则跳过 Step 1b-fe 与 `## 样式契约`。

## 现状审计

### Store：expert_contact.operator_status（MySQL，SSOT）

Schema：`V19__add_operator_status_and_action_log.sql` 追加列；建表 `V1:79-95`，
`updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP`（回补会 bump，对
`CandidateOperatorStatusSyncService` 的 `findAllByOrderByUpdatedAtDesc().distinctBy` 无副作用）。

**Write paths 全集**（`grep -rn "operatorStatus = " src/main/kotlin`，已剔除 DTO 赋值噪声）

| # | 位置 | 写什么 | 现状 | 本计划 |
|---|---|---|---|---|
| 1 | `ExpertOperatorStatusService:30`（`changeStatus`） | 运营指定 | 人工出口 | 不改，P-D 白名单 |
| 2 | `ExpertOperatorStatusService:61`（`updateAutomatically`） | 自动推进 | 保护不全 | **T-1 补齐** |
| 3 | `ManualOutreachTxHelper:46` | 硬编码 `"CONTACTED"` | 绕过 | **T-3 收敛** |
| 4 | `ManualInitialOutreachService:611` | 建行初始化 | 构造路径 | 不改，P-D 白名单 |
| 5 | `ManualInitialOutreachService:706` | 硬编码 `"EMAIL_INVALID"` | 绕过 | 不改（I-2 依赖它） |

> **剔除的 DTO 噪声**（逐条核对均为响应对象字段赋值，非 DB 写入）：
> `UnmatchedInboundMailController:203/1097`、`MailboxService:165`、
> `ExpertContactManagementController:549`、`ExpertIndexController:85/410`、
> `ExpertSearchService:332`。对应 `K-plan-quantified-claims-need-grep-receipts`
> 的"同名 key 混入清单"陷阱。P-D 的排除规则必须覆盖这 7 处。

**Read paths**

1. `ExpertIndexController:85` —— 专家列表。
   `contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"`，**DB 优先于 ES**。
   这是「回刷 ES 修不好列表显示」的直接原因。
2. `ExpertIndexController:410` —— 详情，同一 fallback 链。
3. `CandidateOperatorStatusSyncService:20` —— 回刷按钮数据源，读 DB 推 ES，**不重算**。
4. `ManualInitialOutreachService:936` —— 重试目标筛选 `it.operatorStatus != "EMAIL_INVALID"`。
5. `ExpertContactRepository:47` —— 联系人列表按状态过滤。

### 生产数据基线（需求方提供，已交叉验证）

| 指标 | 值 |
|---|---|
| `expert_contact` 总行数 | 2062 |
| 待回补（有 SENT 介绍信但 NOT_CONTACTED） | **1**（id=2089） |
| 旁证（`current_status != NEW` 且 NOT_CONTACTED） | 1（同一行） |
| `mail_record` 总行数 | 2157 |
| `CONTACTED × 无 SENT 介绍信` | **0** ← 证明无未审计的第三条写入路径 |

id=2089 实测：`current_status=INTRO_SENT`，`operator_status=NOT_CONTACTED`。因果链实锤。

### 依赖方向核验（防循环依赖）

`ManualExpertMailService`（`mail.service`）需注入 `ExpertOperatorStatusService`（`campaign.service`）。
该方向依赖**已存在**——`AutoMailReplyService:484`（同为 `mail.service`）已注入同一服务；
`ExpertOperatorStatusService` 的三个依赖（`ExpertContactRepository` / `OperatorActionLogService` /
`ExpertIndexWriterService`）均不反向依赖 `mail.service`。**不构成新循环。**

### 构造点波及面（全集，改构造参数的成本）

```
grep -rn "ManualExpertMailService(" → ManualExpertMailServiceTest.kt:38
                                      ManualExpertMailServiceGateTest.kt:54
grep -rn "ManualOutreachTxHelper("   → ManualOutreachTxHelperTest.kt:27
```

### Interaction points

| # | 写路径 | 读路径 | 验收 |
|---|---|---|---|
| IP-1 | `sendManualMail`（新增） | `ExpertIndexController:85` 列表 | A-1 |
| IP-2 | `sendManualMail`（新增 ES） | `notContactedWithEmailFilters` 批量池 | A-2 |
| IP-3 | V94 回补 | `CandidateOperatorStatusSyncService` 回刷 | A-5 |
| IP-4 | `ManualInitialOutreachService:706` 写 EMAIL_INVALID | `updateAutomatically`（新增短路） | A-4 |
| IP-5 | `ManualOutreachTxHelper`（收敛） | 批量 execution 日志 | A-7 |

## 实现方案

### T-1 补齐 updateAutomatically 保护【I-1, I-2】
文件：`campaign/service/ExpertOperatorStatusService.kt`

在 `:51`（`val current = ...`）之前插入 I-2 的字符串短路；
把 `:56-59` 的 REPLIED 专用判断替换为 I-1 的通用单调判断
（`current != null && current.ordinal >= targetStatus.ordinal → return contact`）。
`COMPLETED` 短路（`:53-55`）**保留**——`ExpertOperatorStatusServiceTest:72` 有专门断言，
且它是 I-1 的醒目特例。`changeStatus`（`:24-43`）一行不改。

### T-2 ManualExpertMailService 接入唯一出口【I-3, I-4】
文件：`mail/service/ManualExpertMailService.kt`

1. 构造参数追加 `expertOperatorStatusService`，位置须在 `senderAccountBindingService` 之后、
   **两个带默认值的参数之前**（`personalizationGateService` / `mailVariableService`，`:32-33`），
   否则破坏既有位置参数调用。
2. 新增 `operatorStatusFor(mailType: String): OperatorStatus?`，与 `nextStatus()`
   （`:277-285`）**并排放置**，两个轴同处决策。
3. 在 `:101-111` 的 `transition(...)` **之后**追加调用，用 transition 的返回值
   （已含最新 `lastMailAt`）传入。

> **顺序不可颠倒**：`transition` 内部执行 `save(update(contact.copy(...)))`
> （`ConversationStateService:25-32`），先调 `updateAutomatically` 会让 transition
> 用旧快照把 `operator_status` 覆盖回去。Spring Data JDBC 无实体跟踪
> （CLAUDE.md：no lazy loading / entity graph）。

### T-3 ManualOutreachTxHelper 收敛【I-3】
文件：`campaign/service/ManualOutreachTxHelper.kt`

删除 `:46` 的 `operatorStatus = "CONTACTED"`（lambda 只留 `lastMailAt = now`）
与 `:84` 的直接 ES 同步；改为 transition 之后调
`updateAutomatically(saved, OperatorStatus.CONTACTED, "MANUAL_BULK_OUTREACH")`。
构造参数用 `expertOperatorStatusService` 替换 `expertIndexWriterService`
（**执行前须 grep 复核该文件内 `expertIndexWriterService` 除 `:84` 外无其他用途**）。

**行为等价性论证**：批量路径目标集按构造必为 `NOT_CONTACTED`——
重试路径筛 `currentStatus == "NEW"` 且 `operatorStatus != "EMAIL_INVALID"`（`:934-936`），
ES 路径筛 `must_not exists operatorStatus`（`ExpertSearchService:122-127`）。
故 I-1 的单调判断对该路径恒真，**产出不变**。这是 A-7 要验的。

### T-4 存量回补【I-5】
新增：`resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql`
（现存最大版本 V93）

```sql
-- 条件与 ManualInitialOutreachService.hasSentIntroduction():895 逐字一致
-- (direction + mail_type + send_status 三条件缺一不可)
-- 幂等：以 operator_status='NOT_CONTACTED' 为前置，重复执行为 no-op
-- 单调：不触碰任何已推进到 CONTACTED 及之后的行，符合 I-1
-- 无 ${} 占位符，不触发 K-flyway-placeholder-replacement

UPDATE expert_contact ec
   SET ec.operator_status = 'CONTACTED'
 WHERE ec.operator_status = 'NOT_CONTACTED'
   AND EXISTS (
       SELECT 1 FROM mail_record mr
        WHERE mr.expert_contact_id = ec.id
          AND mr.direction   = 'OUTBOUND'
          AND mr.mail_type   = 'INTRODUCTION'
          AND mr.send_status = 'SENT'
   );
```

因用 SQL migration 而非 Kotlin 回填服务，`K-backfill-column-specific-update`
（要求走 `@Modifying @Query` 列级更新）**不适用**——SQL `UPDATE ... SET operator_status`
本身即列级。此为**有意识拒绝**，非遗漏。

### T-5 测试【I-1..I-4】

- `ExpertOperatorStatusServiceTest`：+4 例
  （CONTACTED 不覆盖 REPLIED / 不覆盖 INVITED / EMAIL_INVALID 全值域不被覆盖 /
  NOT_CONTACTED→CONTACTED 正常且 ES 同步一次）
- `ManualExpertMailServiceTest`：补构造参数 +3 例
  （INTRODUCTION→CONTACTED / MEETING_INVITATION→INVITED / COMPOSE_TEMPLATE 零调用）
- `ManualExpertMailServiceGateTest`：仅补构造参数
- `ManualOutreachTxHelperTest`：换构造参数 +1 例
  （verify `updateAutomatically(_, CONTACTED, _)` 被调用，且不再直接调 `expertIndexWriterService`）

## 变更文件清单（9 个，≤10 ✅ ／ 子系统 1 ✅）

| # | 文件 | 类型 | 任务 |
|---|---|---|---|
| 1 | `campaign/service/ExpertOperatorStatusService.kt` | 改 | T-1 |
| 2 | `mail/service/ManualExpertMailService.kt` | 改 | T-2 |
| 3 | `campaign/service/ManualOutreachTxHelper.kt` | 改 | T-3 |
| 4 | `resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql` | 新增 | T-4 |
| 5 | `test/…/campaign/service/ExpertOperatorStatusServiceTest.kt` | 改 | T-5 |
| 6 | `test/…/mail/service/ManualExpertMailServiceTest.kt` | 改 | T-5 |
| 7 | `test/…/mail/service/ManualExpertMailServiceGateTest.kt` | 改 | T-5 |
| 8 | `test/…/campaign/service/ManualOutreachTxHelperTest.kt` | 改 | T-5 |
| 9 | `docs/knowledge/campaign/K-operator-status-single-writer.md` | 新增 | Phase 6 |

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertOperatorStatusServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualExpertMailServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualExpertMailServiceGateTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualOutreachTxHelperTest

# 迁移集成测试（需本地 Docker，默认 skip）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

git diff --check
```

通过判据：退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」+ 项目元信息 `test_command:` / `build_command:`。

## 验收标准

- **I-1**：断言 current=REPLIED/INVITED、target=CONTACTED 时返回入参对象，
  且 `verifyNoInteractions(expertContactRepository, expertIndexWriterService)`。
- **I-2**：断言 current="EMAIL_INVALID"、target 遍历全部 6 个枚举值时均短路，DB/ES 零交互。
- **I-3**：`grep -rn "operatorStatus = " src/main/kotlin` 的 DB 写入结果恰为 4 处
  （`ExpertOperatorStatusService:30`、`:61`、`ManualInitialOutreachService:611`、`:706`）；
  `ManualOutreachTxHelper.kt` 中 `operatorStatus` 出现次数为 **0**。
- **I-4**：断言 COMPOSE_TEMPLATE / MATERIAL_REMINDER 路径
  `verifyNoInteractions(expertOperatorStatusService)`。
- **I-5**：`git diff --stat` 断言 `ExpertIndexWriterService.kt` **零改动**。
- **回归**：执行『验证命令』节的全量测试与构建命令通过。

## 人工验收清单

### A-1：手动发介绍信后状态变「已联系」【outcome 1 / IP-1】
- 前置：一位 `operator_status='NOT_CONTACTED'` 且邮箱有效的专家。
- 步骤：① 列表确认显示「未联系」→ ② 详情页选介绍邮件模板发送 → ③ 返回列表刷新。
- 预期：状态列显示 **「已联系」**（非「未联系」、非裸英文串）。

### A-2：发送后退出待发送池【outcome 2 / IP-2】
- 前置：承 A-1。
- 步骤：① 记录批量面板待发送数 N → ② 执行 A-1 → ③ 重新查询。
- 预期：变为 **N-1**；漏斗页按「未联系」筛选该专家不再出现。

### A-3：已回复专家不被降级【must-NOT-change / I-1】
- 前置：`operator_status='REPLIED'` 的专家。
- 步骤：给他手动发一封介绍邮件。
- 预期：状态**仍为「已回复」**。

### A-4：邮箱无效专家不被洗白【must-NOT-change / IP-4】
- 前置：`UPDATE expert_contact SET operator_status='EMAIL_INVALID' WHERE id=<x>;`
- 步骤：给他手动发一封介绍邮件。
- 预期：查库**仍为 `EMAIL_INVALID`**；ES 中该值不变。

### A-5：存量回补 + 回刷生效【outcome 3 / IP-3】
- 前置：生产实测待回补 = **1**（id=2089）。
- 步骤：① 部署含 V94 版本 → ② `SELECT operator_status FROM expert_contact WHERE id=2089`
  → ③ 点「回刷 ES」→ ④ 读 toast。
- 预期：② 为 `CONTACTED`；④ 失败数 0；列表该专家显示「已联系」。
  **若"跳过"≥1，记录条数**——那是已晋升 APPLICATION、CANDIDATE 无文档的情形，
  属 P-B 范围，不算本计划验收失败。

### A-6：模板邮件不动状态【must-NOT-change / I-4】
- 前置：`operator_status='CONTACTED'` 的专家。
- 步骤：发一封 `COMPOSE_TEMPLATE` 邮件。
- 预期：状态**仍为「已联系」**；`expert_contact_status_history` 无新增 operator 相关行。

### A-7：批量路径行为等价【must-NOT-change / IP-5】
- 前置：批量任务有 ≥3 位待发送专家。
- 步骤：手动执行一次批量介绍邮件。
- 预期：execution 日志的 target / success / skipped 与升级前同条件执行**一致**；
  成功专家状态变「已联系」；ES 中这些专家 `operatorStatus=CONTACTED`。

### A-8：唯一写入口可断言【outcome 4 / I-3】
- 步骤：执行 `grep -rn "operatorStatus = " src/main/kotlin | grep -v "val operatorStatus"`。
- 预期：DB 写入点恰为 4 处（`ExpertOperatorStatusService:30`、`:61`、
  `ManualInitialOutreachService:611`、`:706`），其余均为 DTO 赋值。
  **此结果即 P-D 白名单的初始内容。**
