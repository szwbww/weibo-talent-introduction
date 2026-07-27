# 专家列表「服务商 / 地区」数量随筛选条件互斥联动

> 计划日期：2026-06-29 ｜ 流程：create-p ｜ 验收：fix-v

## 需求描述

**可观察结果**：专家列表顶部的「服务商」(`#expertEmailDomainFilter`) 和「地区」(`#expertRegionFilter`) 两个下拉里每个选项后面的数量 `(N)`，会随当前其它筛选条件的变化而实时重算，而不是固定显示全量数量。

**互斥口径（已确认）**：每个下拉的数量 = 应用「除自身以外」的全部当前筛选后的聚合结果。即：

- 「服务商」数量：应用 `operatorStatus + region + tag`，**不**应用 `emailDomain` 自身。
- 「地区」数量：应用 `operatorStatus + emailDomain + tag`，**不**应用 `region` 自身。

**不可改变的行为**：

1. `searchExperts(...)` 主列表查询的返回结果与排序、`totalHits` 完全不变（本次只新增聚合参数，不动主查询语义）。
2. 批量发送配置里的「服务商」下拉 `#batchSendEmailDomain` 必须继续显示**全量**（未经列表筛选）的服务商数量 —— 它服务于批量发送配置，与列表筛选无关。
3. 「服务商」聚合仍只统计有邮箱的文档（`exists email`）；「地区」聚合仍覆盖全部地区桶并映射到大洲。
4. `notContactedWithEmailFilters` 对 `NOT_CONTACTED` 的特殊展开逻辑在聚合中与主查询保持一致。

**不在本次范围内（显式延后）**：

- `needsAttention`（MySQL 路径）模式下让聚合数量与 MySQL 列表精确对齐 —— ES 聚合无法反映 MySQL 侧的 `needsManualAttention` 过滤，本次按「该模式下用全量/level-only 数量」处理，不做精确对齐。
- 给下拉增加「当前结果总数」徽标、URL 持久化筛选、分页等无关增强。
- 后端聚合结果缓存 / 防抖优化。

## 关键不变量

### Invariant I-1: 服务商聚合排除自身 emailDomain
- Rule: `/api/experts/email-providers` 构建聚合 filter 时，**必须**纳入 `operatorStatus`、`region`、`tag`，并**必须**忽略传入的 `emailDomain`（即使前端误传也不得应用）。基础约束 `exists email` 保留。
- Applies to: `ExpertIndexController.getEmailProviders`、`ExpertSearchService.aggregateEmailDomains`。
- Violation consequence: 一旦选中某服务商就把 emailDomain 也加进聚合，其余服务商数量全部变 0，下拉只剩当前项，用户无法切换 —— 即上一轮讨论中明确要避免的退化。
- 来源: original

### Invariant I-2: 地区聚合排除自身 region
- Rule: `/api/experts/regions` 构建聚合 filter 时，**必须**纳入 `operatorStatus`、`emailDomain`、`tag`，并**必须**忽略传入的 `region`。
- Applies to: `ExpertIndexController.getRegions`、`ExpertSearchService.aggregateRegions`。
- Violation consequence: 同 I-1，选中某地区后其它地区数量全为 0。
- 来源: original

### Invariant I-3: 聚合 filter 与主列表 filter 同源
- Rule: 聚合接口用于构建 ES filter 的逻辑，必须与 `searchExperts` 中现有的 filter 构建逻辑**完全一致**（同样的 `term tags`、`operatorStatus`/`NOT_CONTACTED` 展开、`wildcard email`、`regionFilter`）。实现方式：抽取私有方法 `buildExpertFilters(tag, operatorStatus, emailDomain, region)`，由 `searchExperts` 与两个聚合方法共同调用。
- Applies to: `ExpertSearchService.searchExperts`、`aggregateEmailDomains`、`aggregateRegions`、新增 `buildExpertFilters`。
- Violation consequence: 聚合数量与列表实际命中数不一致，用户看到的 `(N)` 与切换后列表条数对不上。
- 来源: original

### Invariant I-4: searchExperts 行为零回归
- Rule: 抽取 `buildExpertFilters` 后，`searchExperts` 产出的 filter 列表、`query`、`sort`、`track_total_hits`、`_source`、返回的 `experts` 顺序与 `totalHits` 必须与重构前逐字段等价。
- Applies to: `ExpertSearchService.searchExperts`。
- Violation consequence: 主列表回归，影响范围远超本需求。
- 来源: original

### Invariant I-5: 批量发送下拉保持全量
- Rule: `#batchSendEmailDomain` 的选项与数量只能由「全量服务商」（不带任何列表筛选）填充，不得被列表筛选联动改写。
- Applies to: 前端 `loadEmailProviders`、`loadContacts`。
- Violation consequence: 批量发送可选服务商被列表筛选误缩小，发送配置出错。
- 来源: original

### Invariant I-6: 选中项在重渲染后保留
- Rule: 每次重建「服务商 / 地区」下拉 `innerHTML` 后，必须把用户当前选中的值重新写回 `.value`；因互斥口径下自身筛选不被聚合应用，被选中项的选项必然仍存在于新列表中，故选中值不会因数量变化而丢失。
- Applies to: 前端 `loadEmailProviders`、`loadRegions`。
- Violation consequence: 每次筛选变更后用户的当前选择被清空，触发连锁 `change`，体验崩坏。
- 来源: original

## 现状审计

### ES 索引（CANDIDATE / APPLICATION 等，按 level）
- Mapping 关键字段：`email`（聚合按 `@` 后子串脚本分桶）、`country`、`nationality`（`regionFilter` 用 `country`/`nationality` 双字段 should 匹配）、`tags`、`operatorStatus`。ES 通过裸 `RestTemplate` + basic auth 访问，无客户端库。
- Write paths：与本需求无关（本需求只读聚合，不写 ES）。
- Read paths（与本需求相关）：
  1. `ExpertSearchService.searchExperts`（`src/main/kotlin/.../expert/service/ExpertSearchService.kt:41-114`）— 读 `tag/operatorStatus/emailDomain/region` 构建 `bool.filter`，返回列表 + `totalHits`。**filter 构建逻辑的唯一权威来源**，含 `NOT_CONTACTED` 经 `notContactedWithEmailFilters(null)` 展开、`wildcard email`、`regionFilter`。
  2. `ExpertSearchService.aggregateRegions`（:453-489）— 当前 `query = match_all`，按 `country` term 聚合后用 `CountryContinentMapping.toRegion` 归并到大洲，缺失补 0。**当前完全忽略筛选**。
  3. `ExpertSearchService.aggregateEmailDomains`（:530-566）— 当前 `query = exists email`，按邮箱域名脚本分桶。**当前完全忽略筛选**。
  4. `ExpertSearchService.regionFilter`（:494-528）— 把大洲名转为 `country/nationality` 的 should filter，`REGION_OTHER` 走 `must_not terms`。聚合复用此方法。
- Interaction points：
  - `searchExperts` 的 filter 构建（写「口径」）↔ 两个聚合方法（读「口径」）跨方法但同文件 —— 这是 I-3 要消除的重复点。
  - 后端聚合参数 ↔ 前端 `loadContacts` 传参 ↔ 前端列表展示数量，跨 controller / app.js。

### 后端接口层
- `ExpertIndexController.getEmailProviders`（`src/main/kotlin/.../expert/controller/ExpertIndexController.kt:215-220`）— 仅 `@RequestParam level`，直接调 `aggregateEmailDomains(level)`。
- `ExpertIndexController.getRegions`（:222-227）— 仅 `@RequestParam level`，直接调 `aggregateRegions(level)`。
- `ExpertIndexController.listExperts`（:44-75）— 已有 `level/size/tag/sortBy/from/operatorStatus/emailDomain/region` 全套参数，前端 URLSearchParams 已在传，可作为聚合接口新增参数的命名与编码参照。

### 前端
- `loadEmailProviders(level)`（`src/main/resources/static/app.js:1886-1925`）— GET `/api/experts/email-providers?level=`，**同时**填充 `#expertEmailDomainFilter`（筛选下拉）**和** `#batchSendEmailDomain`（批量发送配置下拉）。两者都保留并回写当前 `.value`。
- `loadRegions(level)`（:1927-1949）— GET `/api/experts/regions?level=`，只填充 `#expertRegionFilter`，回写当前 `.value`。
- `loadContacts()`（:1951-1998+）— 读取 `level/size/operatorStatus(contactStatusFilter)/needsAttention/emailDomain/region/tag`。当前**仅在 level 变化时**经 `state.lastEmailProvidersLevel` / `state.lastRegionsLevel` 守卫调用聚合加载。`needsAttention` 为真时走 MySQL 路径并禁用 region/tag 下拉（:1973-1998）。
- 事件绑定（:6307-6311）：`expertIndexLevel/expertIndexSize/contactNeedsAttentionFilter/contactStatusFilter/expertTagFilter/expertSortBy/expertEmailDomainFilter/expertRegionFilter` 的 `change` 全部触发 `reloadContactsFromStart` → `loadContacts`。即每次筛选变更都会进入 `loadContacts`，是注入「每次重算聚合」的唯一入口。
- Interaction point：`loadEmailProviders` 一个函数喂两个下拉（筛选 vs 批量发送），是 I-5 的风险点 —— 联动只能作用于筛选下拉，批量发送下拉必须维持全量。

## 实现方案

### 阶段 A：后端聚合支持筛选（遵守 I-1 / I-2 / I-3 / I-4）

**A1. 抽取共享 filter 构建器**（`ExpertSearchService.kt`）
新增私有方法：
```kotlin
private fun buildExpertFilters(
    tag: String?, operatorStatus: String?, emailDomain: String?, region: String?
): MutableList<Map<String, Any>>
```
内容逐字搬运 `searchExperts` 现有 54-77 行的构建逻辑（tag term / operatorStatus 含 NOT_CONTACTED 展开 / emailDomain wildcard / region via regionFilter）。`searchExperts` 改为调用该方法获取 `filters`，其余不动（满足 I-4）。

**A2. `aggregateEmailDomains` 接收筛选**（I-1）
签名改为 `aggregateEmailDomains(level, tag, operatorStatus, region)`（**不接 emailDomain**）。
`query` 改为 `bool.filter`，filter 列表 = `buildExpertFilters(tag, operatorStatus, emailDomain=null, region)` 之上**追加**基础约束 `exists email`（保留现有 `exists email` 语义）。聚合体 `aggs.email_domains` 不变。

**A3. `aggregateRegions` 接收筛选**（I-2）
签名改为 `aggregateRegions(level, tag, operatorStatus, emailDomain)`（**不接 region**）。
`query` 由 `match_all` 改为：filter 列表非空则 `bool.filter`，为空则维持 `match_all`。filter = `buildExpertFilters(tag, operatorStatus, emailDomain, region=null)`。聚合体 `aggs.countries` 与大洲归并、补 0 逻辑不变。

**A4. 接口透传参数**（`ExpertIndexController.kt`，I-1 / I-2）
- `getEmailProviders` 增参 `tag/operatorStatus/region`（均 `required=false`），调 `aggregateEmailDomains(level, tag, operatorStatus, region)`。
- `getRegions` 增参 `tag/operatorStatus/emailDomain`（均 `required=false`），调 `aggregateRegions(level, tag, operatorStatus, emailDomain)`。
- 命名与 `listExperts` 现有参数保持一致。

### 阶段 B：前端每次筛选都重算（遵守 I-5 / I-6）

**B1. `loadEmailProviders` 改造**（`app.js`，I-5 / I-6）
- 签名改为 `loadEmailProviders(level, { filters = {}, refreshConfigDropdown = false } = {})`。
- 请求 URL 用 `URLSearchParams` 拼 `level + filters`（filters 含 `tag/operatorStatus/region`，**不含 emailDomain**）。
- 仅当 `refreshConfigDropdown === true` 时才重建 `#batchSendEmailDomain`（用全量结果）；筛选触发的联动调用传 `false`，保证批量发送下拉不被联动改写（I-5）。
- 重建 `#expertEmailDomainFilter` 后回写当前 `.value`（I-6，沿用现有回写逻辑）。

**B2. `loadRegions` 改造**（`app.js`，I-6）
- 签名改为 `loadRegions(level, { filters = {} } = {})`，URL 拼 `level + filters`（含 `tag/operatorStatus/emailDomain`，**不含 region**）。
- 重建后回写 `.value`。

**B3. `loadContacts` 调用时机改造**（`app.js`，入口在 :1961-1969）
- 移除「仅 level 变化才加载」的 `state.lastEmailProvidersLevel` / `state.lastRegionsLevel` 守卫。
- level 变化时：调用 `loadEmailProviders(level, { filters: 互斥filters, refreshConfigDropdown: true })` + `loadRegions(level, { filters })`（同时刷新批量发送全量下拉）。
- 非 level 变化（普通筛选变更）：调用 `loadEmailProviders(level, { filters, refreshConfigDropdown: false })` + `loadRegions(level, { filters })`。
- 互斥 filters 取值：服务商用 `{tag, operatorStatus, region}`，地区用 `{tag, operatorStatus, emailDomain}`，均取自 `loadContacts` 顶部已读取的当前筛选变量。
- `needsAttention` 为真（MySQL 路径）时：region/tag 已被禁用清空，按「不在范围内」处理 —— 此时聚合调用传当前可用筛选（`operatorStatus`），region/tag/emailDomain 取空，等价于接近全量；不追求与 MySQL 列表精确对齐。
- 注意避免重复并发请求：聚合加载与列表加载并行发起即可，不互相阻塞。

### 阶段 C：自测

后端：对同一 level，分别请求「无筛选」与「带 region=亚洲」的 `/email-providers`，确认带筛选时各域名计数 ≤ 无筛选、且总数等于该 region 下有邮箱专家数；反向验证 `/regions` 带 `emailDomain` 同理。确认传给 `/email-providers` 的 `emailDomain` 参数（即使前端不传）不会改变结果（I-1 防御）。
前端：选中某服务商 → 地区数量随之变化且地区下拉仍可正常切换；选中某地区 → 服务商数量变化且服务商下拉仍可切换；`#batchSendEmailDomain` 数量在两种操作下保持不变（I-5）；切 level 时批量发送下拉刷新为新 level 全量。

## 变更文件清单

| # | 文件 | 改动 | 关联不变量 |
|---|------|------|-----------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 抽取 `buildExpertFilters`；`searchExperts` 改用之；`aggregateEmailDomains`/`aggregateRegions` 增筛选参数并改 query | I-1,I-2,I-3,I-4 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | `getEmailProviders`/`getRegions` 增 `@RequestParam` 并透传 | I-1,I-2 |
| 3 | `src/main/resources/static/app.js` | `loadEmailProviders`/`loadRegions` 增 filters 参数；`loadContacts` 每次重算并区分批量发送下拉刷新 | I-5,I-6 |

文件数：3（≤10）。子系统：后端 expert 搜索 + 前端 admin UI（2 个，≤2）。无新增字段 / 状态 / 迁移。

## 验收标准

- **I-1**：阅读 `aggregateEmailDomains`，确认 filter 来自 `buildExpertFilters(..., emailDomain=null, ...)` 且追加 `exists email`；构造请求带 `emailDomain` 参数，断言结果与不带时一致。
- **I-2**：阅读 `aggregateRegions`，确认 filter 来自 `buildExpertFilters(..., region=null)`；带 `region` 参数请求，断言结果与不带时一致；filter 为空时 query 仍为 `match_all`。
- **I-3**：diff `buildExpertFilters` 与原 `searchExperts` 54-77 行，逐分支等价（tag / NOT_CONTACTED 展开 / 其它 operatorStatus / emailDomain wildcard / regionFilter）。
- **I-4**：对若干 `(tag, operatorStatus, emailDomain, region)` 组合，断言重构后 `searchExperts` 的 `experts` 顺序与 `totalHits` 与重构前一致（可用现有 `ExpertSearchService` 相关测试或新增等价性断言）。
- **I-5**：前端操作中 `#batchSendEmailDomain` 的选项集合与计数，在任意列表筛选变更下保持不变；仅 level 变更时刷新。
- **I-6**：连续变更筛选后，`#expertEmailDomainFilter` 与 `#expertRegionFilter` 当前选中值不被清空。
- **集成场景**：选「服务商=gmail.com」后，「地区」各项 `(N)` 之和应等于 gmail.com 下有邮箱专家总数；再选「地区=欧洲」后，列表实际命中条数应等于此前「地区」下拉里欧洲项显示的 `(N)`（口径一致性，跨 I-3 交互点）。

## Out of scope（重申）

- `needsAttention`/MySQL 路径下聚合与列表精确对齐 —— 延后。
- 聚合缓存、防抖、URL 持久化、结果总数徽标 —— 延后。
- 任何 ES mapping / Flyway 迁移变更 —— 本需求不涉及。
