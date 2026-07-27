# 专家发现吞吐量提升 + 游标持久化 + 国籍过滤修复

## 需求描述

**可观测结果：** 每日定时专家发现任务从实际有效产出接近 0（因重复扫描同一批论文）提升至每天稳定发现数百名新专家，通过游标持久化实现跨运行断点续传，并扩大各数据源限额至总计 ~5000 篇/天。同时修复国籍未知专家被误拒的 bug。

**不得改变的行为：**

- 各 API 的 `requestDelayMs` 不变，不触碰风控阈值
- CORE 数据源的 ES scroll 游标仍不持久化（跨会话失效）
- `ExpertDiscoveryScheduler` 的调用入口和 `PaperSearchCriteria` 构造逻辑不变
- `excludeChineseNationality=true` 仍拒绝明确标注为中国籍的专家
- 所有已有 Flyway 迁移（V1–V31）不动
- `ExpertDiscoveryProperties` 的 Kotlin 数据类默认值不改（只在 YAML 层覆盖）

**不在本次范围：**

- 数据源新增或移除
- 搜索关键词策略调整
- 调整 `requestDelayMs` 或添加自适应限流
- 前端展示游标状态
- 游标穷尽后的自动重置策略（当前：穷尽 → cursorValue 存 null → 下次从头开始）

---

## 关键不变量

### Invariant I-1: 游标表一行一源

- Rule: `discovery_source_cursor.source_name` 有唯一约束（`uk_source_name`）。每个数据源名称（如 `EUROPE_PMC`, `OPENALEX`, `ORCID` 等）在表中最多一行，通过先 `findBySourceName` 再 `save` 的 read-then-update 模式维护。
- Applies to: `ExpertDiscoveryService.saveSourceCursor()`
- Violation consequence: 插入重复行导致 `DataIntegrityViolationException`，游标保存失败，下次从头扫描。

### Invariant I-2: CORE 游标不持久化

- Rule: `nonPersistableCursorSources = setOf("CORE")`。`loadSourceCursor("CORE")` 始终返回 `null`，`saveSourceCursor("CORE", ...)` 是 no-op。原因是 CORE 使用 ES scroll context，5 分钟后过期，跨运行无法复用。
- Applies to: `ExpertDiscoveryService.loadSourceCursor()`, `ExpertDiscoveryService.saveSourceCursor()`
- Violation consequence: 持久化一个已过期的 scroll ID → 下次运行时 CORE 搜索 400 错误 → 该源整批失败。

### Invariant I-3: 游标加载/保存失败不阻塞发现

- Rule: `loadSourceCursor` 和 `saveSourceCursor` 内部 catch 所有异常并 log.warn，不抛出。加载失败等价于无游标（从头开始）；保存失败仅丢失进度但不中断当前运行。
- Applies to: `ExpertDiscoveryService.loadSourceCursor()`, `ExpertDiscoveryService.saveSourceCursor()`
- Violation consequence: 游标 DB 不可达时整个发现任务失败，降级为 0 产出。

### Invariant I-4: discoverFromSource 返回最后成功游标

- Rule: `discoverFromSource` 返回值类型为 `String?`，返回最后一个成功批次的 `batch.nextCursor`。如果第一个批次就失败或无结果，返回 `null`（意味着下次从头开始）。
- Applies to: `ExpertDiscoveryService.discoverFromSource()`
- Violation consequence: 返回错误的游标 → 下次跳过一段论文，永久丢失部分专家。

### Invariant I-5: 国籍空值放行

- Rule: `isNotChineseNationality(null)` 和 `isNotChineseNationality("")` 均返回 `true`。只有明确匹配 `china/chinese/cn/people's republic of china` 才返回 `false`。
- Applies to: `CandidateEligibilityService.isNotChineseNationality()`
- Violation consequence: 大量国籍字段缺失的海外专家被误标 `CHINESE_NATIONALITY` 拒绝 → 晋升率骤降。

### Invariant I-6: YAML 限额覆盖不改 Kotlin 默认值

- Rule: `ExpertDiscoveryProperties` 数据类的 `maxPapersPerRun` 默认值仍为 500、`maxAuthorsPerRun` 默认值仍为 2000。限额提升仅通过 `application.yml` 的值（5000 / 20000）实现。各数据源 Properties 类的默认值也不改。
- Applies to: `application.yml`, `ExpertDiscoveryProperties.kt`（不改此文件）
- Violation consequence: 改了 Kotlin 默认值 → 测试中 `ExpertDiscoveryProperties()` 构造出的默认对象限额变大 → 测试行为不可预期。

---

## 现状审计

### MySQL `discovery_source_cursor` 表（新建）

- Schema: 见 V32 迁移。`id` BIGINT PK, `source_name` VARCHAR(50) UNIQUE, `cursor_value` TEXT nullable, `papers_processed_total` BIGINT default 0, `last_run_at` DATETIME, `updated_at` DATETIME NOT NULL。
- Write paths:
  1. `ExpertDiscoveryService.saveSourceCursor()` — 每个数据源在 `discover()` 主循环结束时调用一次，upsert cursor_value 和 papers_processed_total
- Read paths:
  1. `ExpertDiscoveryService.loadSourceCursor()` — 每个数据源在 `discover()` 主循环开始时调用一次，读取 cursor_value
- Interaction points: 无跨模块交互。只有 `ExpertDiscoveryService` 读写此表。

### `application.yml` 限额配置

- 当前值:
  - `max-papers-per-run`: 500 → **5000**
  - `max-authors-per-run`: 2000 → **20000**
  - `europe-pmc.max-papers-per-source`: 无显式值 → **1500**
  - `openalex.max-papers-per-source`: 500 → **1200**
  - `pmc-oa.max-papers-per-source`: 500 → **1000**
  - `crossref.max-papers-per-source`: 300 → **500**
  - `core.max-papers-per-source`: 300 → **500**
  - `arxiv.max-papers-per-source`: 100 → **200**
- Write paths: 仅人工编辑此文件
- Read paths:
  1. `ExpertDiscoveryProperties` — 读取 `maxPapersPerRun`, `maxAuthorsPerRun`
  2. `EuropePmcProperties` — 读取 `maxPapersPerSource`
  3. `OpenAlexProperties` — 读取 `maxPapersPerSource`
  4. 各数据源 Properties 类 — 读取各自 `maxPapersPerSource`
  5. `ExpertDiscoveryService.discover()` — 用 `discoveryProperties.maxPapersPerRun` 作全局上限
  6. `ExpertDiscoveryService.discoverFromSource()` — 用 `source.maxPapersPerSource` 作单源上限
- Interaction points: 限额提升使更多数据源有机会运行（之前 Europe PMC 一家耗尽全局 500 额度）。

### `CandidateEligibilityService.isNotChineseNationality()`

- 调用者:
  1. `CandidateEligibilityService.evaluateEligibility()` — 传入 `expert.nationality ?: expert.country`
- 被谁调用:
  1. `ExpertDiscoveryService.processPaper()` → `eligibilityService.evaluateEligibility(profile)`
  2. `ExpertDiscoveryService.discoverFromOrcid()` → 同上
  3. `ExpertRevalidationService` → 重验已有专家
  4. `ExpertIndexPromotionService` → L3→L2 晋升时检查
- Interaction points: 国籍放行后，更多 nationality=null 的专家会通过资格检查，被索引到 CANDIDATE (L2)。这是预期行为。

### `ExpertDiscoveryService` 构造函数

- 原有参数: 18 个依赖注入参数（europePmc, ...providers, emailValidationService, eligibilityService, ...services, restTemplate, esProperties, discoveryProperties, objectMapper, progressStore）
- 新增: `cursorRepository: DiscoverySourceCursorRepository`（第 19 个参数）
- 调用者: Spring 容器自动注入 + `ExpertDiscoveryServiceTest.createService()`

---

## 实现方案

### 阶段一：游标持久化基础设施

**任务 1: Flyway 迁移** [I-1]

- 文件: `src/main/resources/db/migration/V32__create_discovery_source_cursor.sql`
- 创建 `discovery_source_cursor` 表，包含 `source_name` 唯一约束
- V31 是当前最新迁移，V32 顺序正确

**任务 2: Domain + Repository** [I-1]

- 新建 `src/main/kotlin/.../discovery/domain/DiscoverySourceCursor.kt`
  - Spring Data JDBC `@Table("discovery_source_cursor")` data class，`@Id val id: Long?`
  - 字段与 V32 表结构一一对应，camelCase 自动映射 snake_case
- 新建 `src/main/kotlin/.../discovery/repository/DiscoverySourceCursorRepository.kt`
  - 继承 `CrudRepository<DiscoverySourceCursor, Long>`
  - 自定义 `@Query` 方法 `findBySourceName(sourceName: String): DiscoverySourceCursor?`

### 阶段二：服务层集成

**任务 3: ExpertDiscoveryService 游标集成** [I-1, I-2, I-3, I-4]

- 文件: `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt`
- 构造函数新增 `cursorRepository: DiscoverySourceCursorRepository` 参数
- 新增常量 `nonPersistableCursorSources = setOf("CORE")` [I-2]
- 新增 `loadSourceCursor(sourceName)` 私有方法 [I-2, I-3]:
  - CORE → 直接返回 null
  - 其他 → `cursorRepository.findBySourceName()`，catch 异常返回 null
- 新增 `saveSourceCursor(sourceName, cursorValue, papersInRun)` 私有方法 [I-1, I-2, I-3]:
  - CORE → 直接返回
  - 其他 → read existing → copy with updated fields / create new → save，catch 异常 log warn
- 修改 `discover()` 主循环 [I-4]:
  - 每个 source 开始前调用 `loadSourceCursor(source.sourceName)`
  - 非 null 时 `criteria.copy(cursor = savedCursor)` 替换默认 criteria
  - 每个 source 结束后调用 `saveSourceCursor(source.sourceName, finalCursor, papersInRun)`
  - ORCID 部分同理
- 修改 `discoverFromSource()` 返回类型 `Unit → String?` [I-4]:
  - 新增 `var lastNextCursor: String? = null`，每次成功批次后 `lastNextCursor = batch.nextCursor`
  - 方法末尾 `return lastNextCursor`
- 修改 `discoverFromOrcid()` 返回类型 `Unit → String?`:
  - 方法末尾 `return cursor`

### 阶段三：配置调整

**任务 4: 限额提升** [I-6]

- 文件: `src/main/resources/application.yml`
- 只改 YAML 值，不改任何 Properties Kotlin 数据类
- 全局: `max-papers-per-run` 500→5000, `max-authors-per-run` 2000→20000
- 各源: Europe PMC 新增 1500, OpenAlex 500→1200, PMC OA 500→1000, Crossref 300→500, CORE 300→500, ArXiv 100→200
- `requestDelayMs` 全部不变

### 阶段四：国籍过滤修复

**任务 5: isNotChineseNationality 空值放行** [I-5]

- 文件: `src/main/kotlin/.../expert/service/CandidateEligibilityService.kt`
- `isNotChineseNationality()` 方法中，`normalize(nationality)` 后增加: `if (normalized.isBlank()) return true`
- 现有 read path 无需调整，所有调用者通过 `evaluateEligibility()` 间接调用

### 阶段五：测试适配

**任务 6: ExpertDiscoveryServiceTest 适配** [I-1]

- 文件: `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt`
- 新增 `cursorRepository` mock 字段
- `setUp()` 中 `cursorRepository = Mockito.mock(DiscoverySourceCursorRepository::class.java)`
- `createService()` 调用传入 `cursorRepository` 作为最后一个参数
- 默认 mock 行为: `findBySourceName` 返回 null（Mockito 默认），即测试中不使用持久化游标

---

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `src/main/resources/db/migration/V32__create_discovery_source_cursor.sql` | 新建 | Flyway 迁移 |
| 2 | `src/main/kotlin/.../discovery/domain/DiscoverySourceCursor.kt` | 新建 | 实体类 |
| 3 | `src/main/kotlin/.../discovery/repository/DiscoverySourceCursorRepository.kt` | 新建 | 仓库接口 |
| 4 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | 修改 | 游标加载/保存 + 返回类型变更 |
| 5 | `src/main/resources/application.yml` | 修改 | 限额提升 |
| 6 | `src/main/kotlin/.../expert/service/CandidateEligibilityService.kt` | 修改 | 空国籍放行 |
| 7 | `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` | 修改 | 适配构造函数变更 |

共 7 个文件，2 个子系统（discovery 游标/限额、eligibility 国籍过滤）。

---

## 验收标准

### 按不变量验证

- **I-1**: 检查 V32 迁移有 `UNIQUE KEY uk_source_name (source_name)`；`saveSourceCursor` 使用 `findBySourceName` + `save` 模式（不是裸 insert）。
- **I-2**: `nonPersistableCursorSources` 包含 `"CORE"`；`loadSourceCursor("CORE")` 返回 `null`；`saveSourceCursor("CORE", ...)` 不调用 `cursorRepository`。
- **I-3**: `loadSourceCursor` 和 `saveSourceCursor` 的 catch 块捕获 `Exception`、log warn、不 rethrow。
- **I-4**: `discoverFromSource` 返回类型为 `String?`；`lastNextCursor` 在每次 `batch.papers` 非空且 `batch.nextCursor` 非 null 时更新；方法末尾 `return lastNextCursor`。
- **I-5**: `isNotChineseNationality(null)` → `true`；`isNotChineseNationality("")` → `true`；`isNotChineseNationality("Chinese")` → `false`。现有 `CandidateEligibilityServiceEnhancedTest` 中 nationality="Chinese" 的测试仍通过。
- **I-6**: `ExpertDiscoveryProperties.kt` 文件未被修改（diff 为空）。YAML 中 `max-papers-per-run` 值为 5000。

### 集成场景

1. **首次运行**: 表中无数据 → `loadSourceCursor` 返回 null → 各源从默认游标开始 → 运行完毕后表中出现每个源的游标行（CORE 除外）
2. **第二次运行**: 表中有游标 → `loadSourceCursor` 返回上次值 → 各源从断点续传 → 游标更新
3. **源穷尽**: `discoverFromSource` 返回 null → `saveSourceCursor` 存 null → 下次 `loadSourceCursor` 返回 null → 从头开始（期望此时有新论文入库）
4. **DB 不可达**: `loadSourceCursor` catch 返回 null → 从头扫描但不崩溃；`saveSourceCursor` catch warn → 游标丢失但当次结果已写入 ES
5. **国籍字段缺失**: `ExpertProfile(nationality=null, country=null)` → `evaluateEligibility` 不产生 `CHINESE_NATIONALITY` 拒绝理由

### 编译 + 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
```

全部已有测试必须通过，特别关注：
- `ExpertDiscoveryServiceTest` — 构造函数参数匹配
- `ExpertDiscoverySchedulerTest` — 不直接构造 Service，不受影响
- `CandidateEligibilityServiceEnhancedTest` — nationality="Chinese" 仍被拒

---

## 自检清单

- [x] 关键不变量 section 存在，每个新字段/状态至少 1 条不变量（cursor_value → I-1/I-4, source_name → I-1, CORE 排除 → I-2, nationality 空值 → I-5）
- [x] 现状审计列出了 `discovery_source_cursor` 的所有写路径（仅 `saveSourceCursor`）和读路径（仅 `loadSourceCursor`）
- [x] 没有任务引入未被不变量覆盖的写路径
- [x] 文件数 = 7 ≤ 10
- [x] 子系统数 = 2 ≤ 2
- [x] 每个任务引用了其对应的不变量编号
- [x] 验收标准中每个不变量至少有一条检查
- [x] 文件清单中无 "and related files" 或 "etc."
- [x] Out-of-scope 明确列出了延迟项
