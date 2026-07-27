# 计划 01：expert_contact 落库 country（地区维度数据基础）

> 序列计划 1/2。本计划只交付"专家国家落库 + 历史回填"这一数据基础，不含任何监控 UI。
> 计划 2（`02-monitoring-region-provider-surface.md`）依赖本计划产出的 `country` 列。

## 需求描述

- 可观察结果：`expert_contact` 表新增 `country` 列，所有**新建**联系人写入专家的原始国家；存量联系人通过一次性回填补齐。地区（大区）不落库，由查询层用 `CountryContinentMapping.toRegion(country)` 即时折算（见计划 2）。
- 不可改变：
  - 现有 `ExpertContact` 其余字段语义与写路径完全不变。
  - 发件账号分配的 `distributionKey`（`expert.country?.lowercase()...`，InitialOutreachService:70/99、ManualInitialOutreachService:420）逻辑不动——本计划只**新增**写 `contact.country`，不复用/改写该 key。
  - 回填只写 `expert_contact.country`，**不得**改写 ES、不得改写 `expert_contact` 其它列、不得清理任何历史行（参见 K-backfill-readonly-inbound）。
- 范围外（明确推迟）：
  - 任何监控接口、聚合查询、前端展示 → 计划 2。
  - `region` 列落库（已决定不落，查询层折算）。
  - 首发邮件明细表新增「地区」列、地区子 tab → 计划 2 视情况再评估，本序列默认不做。
  - 专家 `nationality` 兜底（ES 的 `regionFilter` 用 country+nationality 双字段，但联系人落库只取 `ExpertProfile.country` 单字段，nationality 兜底推迟）。

## 关键不变量

### Invariant I-1：country 仅在联系人创建时写入，单一来源
- Rule: `expert_contact.country` 的值只能来自创建该联系人时的 `ExpertProfile.country`（原样存储，可为 NULL）。除回填外，任何路径不得在后续更新中改写 `country`。
- Applies to:
  - `InitialOutreachService.kt:45-55`（`ExpertContact(...)` 构造）
  - `ManualInitialOutreachService.kt:273-281`（`existingContact ?: run { save(ExpertContact(...)) }`）
  - `ContactCountryBackfillService`（新增，唯一允许写已存在行 `country` 的路径）
- Violation consequence: 若在 `recordSuccess`/`recordFailure`/状态流转等路径误写 `country`，会与创建期口径漂移，导致计划 2 的地区聚合错乱。
- 来源: original

### Invariant I-2：回填单一职责，只写 country
- Rule: 回填服务读 `expert_contact`（取 id + orcid_id + current_index_level）与 ES（按 orcid 批量查 country），**只** `UPDATE expert_contact SET country=?`。不写 ES、不写其它列、不删行、不改状态。
- Applies to: `ContactCountryBackfillService`
- Violation consequence: 混合"回填新事实"与"修正旧状态"会破坏复验边界（K-backfill-readonly-inbound 的既有教训）。
- 来源: K-backfill-readonly-inbound

### Invariant I-3：回填按联系人所在索引层级查 ES
- Rule: 已回复专家会被晋级 L2→L1（`current_index_level` 从 `CANDIDATE` 变 `APPLICATION`），其文档可能只在 APPLICATION 索引。回填必须按 `contact.currentIndexLevel` 选择 `ExpertIndexLevel` 调 `searchByOrcidIds(orcids, level)`；查不到的归 `country = NULL`（计划 2 折算为 `Other`）。
- Applies to: `ContactCountryBackfillService`
- Violation consequence: 只查 CANDIDATE 会漏掉所有已回复（已晋级）专家的 country——而这正是转化分析最关心的人群。
- 来源: original（依据 CLAUDE.md「数据归属拆分」L3→L2→L1 晋级语义 + `ExpertContact.currentIndexLevel`）

## 现状审计

### MySQL 表 `expert_contact`
- Schema：`com/weibo/talentintroduction/campaign/domain/ExpertContact.kt`，Spring Data JDBC `data class`，不可变。现有字段含 `orcidId`、`expertEmail`、`currentStatus`、`currentIndexLevel`（默认 `CANDIDATE`）、`operatorStatus` 等。**无 country 列。**
- Write paths（grep `ExpertContact(`，全仓仅 2 处构造）：
  1. `InitialOutreachService.kt:45-55` — 自动首发流程创建联系人，作用域内有 `expert: ExpertProfile`（含 `.country`）。
  2. `ManualInitialOutreachService.kt:273-281` — 手动/调度批量发送，`existingContact ?: run { save(...) }`，作用域内有 `expert: ExpertProfile`。
  3. 其余对 `expertContactRepository.save(...)` 的调用均为 `contact.copy(...)`（更新已有行，如 :337 `copy(operatorStatus="EMAIL_INVALID")`）——这些**不构造新行**，按 I-1 不得触碰 `country`，`copy` 默认保留原值即可，无需改动。
- Read paths（与本计划相关）：
  1. `ExpertContactRepository.findFilteredContacts`（:41）等列表查询 `SELECT *` —— 新增列对 `SELECT *` + data class 映射透明，但需确认 data class 字段新增不破坏现有按位/具名映射（Spring Data JDBC 按列名映射，安全）。
  2. 计划 2 将新增聚合读路径（本计划不实现）。
- Interaction points：
  - 创建期写（I-1）↔ 计划 2 聚合读：本计划只保证写入正确，读在计划 2。
  - 回填写（I-2/I-3）↔ ES 读：回填依赖 `ExpertSearchService.searchByOrcidIds`（已存在，见下）。

### ES 专家索引（只读，回填用）
- `ExpertSearchService.searchByOrcidIds(orcidIds: List<String>, level: ExpertIndexLevel = CANDIDATE)`（:283）已存在：`terms` 批量按 orcid 查，返回 `List<ExpertProfile>`，`ExpertProfile.country` 即所需字段。
- `ExpertIndexLevel` 含 CANDIDATE / APPLICATION（CLAUDE.md L2/L1）。回填按 `contact.currentIndexLevel` 映射 level。

### 既有可复用组件
- `CountryContinentMapping.toRegion(country)`（:254）——null/blank 安全，返回 `REGION_OTHER`。本计划不用（折算在计划 2），但确认其存在以支撑「不落 region」的决策。

## 实现方案

### 阶段 A：加列 + 领域字段（obey I-1）

- 任务 A1：新增 Flyway 迁移 `src/main/resources/db/migration/V48__add_country_to_expert_contact.sql`
  - `ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;`
  - `CREATE INDEX idx_expert_contact_country ON expert_contact (country);`（计划 2 GROUP BY/JOIN 用）
  - 不回填默认值（NULL = 未知，计划 2 折算 Other）。
- 任务 A2：`ExpertContact.kt` 新增 `val country: String? = null`（放在 `currentIndexLevel` 附近，给默认值以兼容所有现有 `copy(...)` 调用）。obey I-1。

### 阶段 B：创建期写入（obey I-1）

- 任务 B1：`InitialOutreachService.kt:46` 的 `ExpertContact(...)` 构造增加 `country = expert.country`。
- 任务 B2：`ManualInitialOutreachService.kt:275` 的 `ExpertContact(...)` 构造增加 `country = expert.country`。
  - 注意：该处仅在 `existingContact == null` 时创建；`existingContact` 复用分支不改（其 country 已由首次创建或回填写入，obey I-1 不重写）。

### 阶段 C：历史回填（obey I-2、I-3）

- 任务 C1：新增 `com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt`
  - 依赖：`ExpertContactRepository`、`ExpertSearchService`、`ExpertIdNormalizer`（orcid 归一，见 ManualInitialOutreachService 用法）。
  - 算法：
    1. 分页扫 `expert_contact`（用现有 `findAllByOrderByUpdatedAtDesc()` 或新增分页查询；建议新增 `ExpertContactRepository` 不需改——可直接 `findAll()` 后内存分批，量级可控）。仅取 `country == null` 的行（幂等：已填的跳过）。
    2. 按 `currentIndexLevel` 分组（CANDIDATE / APPLICATION），每组每 ~500 个 orcid 调 `searchByOrcidIds(orcids, level)`（obey I-3）。
    3. 建 `normalizedOrcid -> ExpertProfile.country` 映射；对每个联系人 `expertContactRepository.save(contact.copy(country = profile?.country))`（查不到则保持 null）。**只改 country**（obey I-2，`copy` 其余字段原值不动）。
  - 触发方式：property 开关 `talent-introduction.backfill.contact-country.enabled`（默认 false）下的 `ApplicationRunner`，跑完日志输出处理/命中/未命中计数。开关默认关，跑过一次后置回 false。
    - 选择 ApplicationRunner 而非新增 controller endpoint，是为把回填触发收敛在本服务文件内，避免触碰计划 2 的监控 controller（隔离子系统）。
  - 不新增任何 ES 写、不改其它列（obey I-2）。

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `src/main/resources/db/migration/V48__add_country_to_expert_contact.sql` | 新增列 + 索引 | I-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt` | 加 `country: String? = null` | I-1 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 构造写 `country = expert.country` | I-1 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 构造写 `country = expert.country` | I-1 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt` | 新增回填服务（property-gated runner） | I-2, I-3 |

文件数：5 ≤ 10 ✓。子系统：1（campaign 写路径 + 回填）✓。新增字段：1（`country`）✓。

## 验收标准

- I-1：
  - 新建走 `InitialOutreachService` / `ManualInitialOutreachService` 的联系人，DB 中 `country` = 对应 `ExpertProfile.country`（含 NULL 情形）。
  - grep 确认除清单中 3、4 与回填服务外，无其它路径给 `country` 赋非默认值；所有 `contact.copy(...)` 不显式设置 `country`。
- I-2：
  - 回填运行后，`expert_contact` 仅 `country` 列发生变化；`bounce_record`、ES 索引、`current_status`、`operator_status` 等无任何写入（可通过运行前后行级 diff / ES 写日志确认）。
- I-3：
  - 构造测试数据：一个 `current_index_level = APPLICATION` 的联系人（已回复晋级），其文档只在 APPLICATION 索引。回填后该联系人 `country` 被正确填充（验证未只查 CANDIDATE）。
  - ES 查不到的 orcid，回填后 `country` 仍为 NULL，不抛异常。
- 幂等：回填重复运行，已填行（`country != null`）被跳过，结果不变。
- 集成：`mvn test` 通过；启动时 V48 迁移成功（JDK 11）。

## 备注：与先前预览/沟通的差异

沟通时曾说"country + region 两列都落"。落地按 create-p 纪律收敛为**只落 country**，region 由查询层 `CountryContinentMapping.toRegion()` 即时折算（与 `ExpertSearchService.aggregateRegions` 既有口径一致）。好处：① 单一新字段，互动面减半；② 大区映射调整无需重新回填。若后续确需物化 region（性能或固化历史口径），再单独立计划。

## 修正记录

| 修正项 |  rationale | 参考 |
|--------|------------|------|
| 回填由 `save(contact.copy(country=...))` 改为 `updateCountryById` 列级 `UPDATE expert_contact SET country=?` | 聚合 save 可能覆盖并发更新的其它列，违反 I-2 只写 country | `docs/plans/fix/01-contact-country-foundation/fix-1.md` P1-1 |
