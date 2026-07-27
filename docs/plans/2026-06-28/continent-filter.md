# 专家列表大洲级别筛选

## 需求描述

**可观测结果**: 专家列表页增加一个"大洲"下拉筛选器，选项动态聚合自 ES 中 `country` 和 `nationality` 字段，按大洲归并后显示各大洲专家数量（如 "Europe (4523)"）。选择某大洲后，列表只显示该大洲下的专家。

**不能变的**:
- 现有 emailDomain、operatorStatus、tag、needsAttention 筛选行为不受影响
- 分页、排序逻辑不变
- ES 索引 mapping 不做修改（`country` 和 `nationality` 均已为 `keyword` 类型）
- `SenderAccountAssignmentService`、`CandidateEligibilityService` 等读取 country/nationality 的模块不受影响

**超出范围**:
- 不增加国家级别筛选（只做大洲级别）
- 不修改 ES mapping 或数据
- 不涉及 campaign/mail 模块代码
- 不涉及 MySQL schema 变更

## 关键不变量

### Invariant I-1: 区域映射为纯服务端逻辑，不依赖 ES 数据结构
- Rule: 国家→区域映射完全在 Kotlin 侧完成（内存 Map），不使用 ES scripted aggregation 或 pipeline aggregation。ES 只负责返回 country/nationality 的 terms bucket，服务端归并为区域。
- Applies to: `ExpertSearchService.aggregateRegions()`
- Violation consequence: 如果映射逻辑放入 ES script，则无法在单元测试中验证，且 ES 版本兼容性受限。
- 来源: original

### Invariant I-2: region 筛选使用 terms filter 匹配该区域下所有国家值
- Rule: 当用户选择某区域后，后端将该区域对应的所有国家值列表传入 ES `terms` query，同时匹配 `country` OR `nationality` 字段（`bool.should`）。不能用前端本地过滤（因为分页是 ES 端的）。
- Applies to: `ExpertSearchService.searchExperts()` 中 region 参数处理
- Violation consequence: 如果用前端过滤，totalHits 和分页将不正确。
- 来源: original

### Invariant I-3: 未识别国家归入 "Other" 区域
- Rule: ES 返回的 country/nationality bucket key 如果在映射表中找不到对应区域，统一归入 "Other"。前端的 "Other" 选项 value 固定为 `"Other"`。
- Applies to: `CountryContinentMapping.toRegion()`, `aggregateRegions()` 归并逻辑
- Violation consequence: 某些国家值可能被丢失，导致 region 聚合总数与 match_all 总数不一致。
- 来源: original

### Invariant I-4: 聚合同时覆盖 country 和 nationality 两个字段
- Rule: `aggregateRegions()` 对 `country` 和 `nationality` 分别做 terms 聚合，然后合并（同一文档可能被计两次，但按区域去重后取 max 计数——或者使用 multi_terms/script）。实际推荐方案：因 ES 中 country 和 nationality 语义相近且大多数文档 nationality 为空，先只聚合 `country` 字段即可（覆盖 95%+ 场景），搜索时用 `should` 同时匹配两个字段。
- Applies to: `aggregateRegions()`, `searchExperts()` region filter
- Violation consequence: 如果只看 country 聚合但搜索时只 filter country，会丢失 nationality 填了但 country 为空的文档。
- 来源: original（基于 `CandidateEligibilityService` 中 `expert.nationality ?: expert.country` 的优先级逻辑）

## 现状审计

### CANDIDATE ES index (`orcid_info_candidate`)

- **Schema/mapping**: `country: keyword`, `nationality: keyword`, `dynamic: false`
- **Write paths**:
  1. `ExpertIndexWriterService` — promotes from RAW or writes enriched fields (preserves existing country/nationality)
  2. `ExpertRevalidationService` — revalidates but does not modify country/nationality
  3. Discovery pipelines (OpenAlex, EuropePmc etc.) — write raw data including country
- **Read paths**:
  1. `ExpertSearchService.searchExperts()` — reads country/nationality in `_source` fields list, returns to frontend
  2. `ExpertSearchService.scrollExperts()` — same
  3. `SenderAccountAssignmentService.distributionKey()` — reads `expert.country` for distribution balancing
  4. `CandidateEligibilityService.evaluateEligibility()` — reads `expert.nationality ?: expert.country` to check Chinese nationality
  5. `InitialOutreachService` / `ManualInitialOutreachService` — reads `expert.country` for distribution key
- **Interaction points affected by this plan**: None — this plan only adds a new read path (aggregation + filter), does not modify any existing read or write path.

### Frontend (`app.js` + `index.html`)

- **Read path for emailDomain filter (pattern to copy)**:
  1. `loadEmailProviders(level)` — calls `GET /api/experts/email-providers?level=...`, fills `#expertEmailDomainFilter` dropdown
  2. `loadContacts()` — reads `#expertEmailDomainFilter` value, appends `emailDomain` param to API call
  3. Auto-refresh listener — array at line 5922 includes `"expertEmailDomainFilter"`

## 实现方案

### Phase A: 后端 — 国家→大洲映射 (I-1, I-3)

**Task A1**: 新建 `src/main/kotlin/.../expert/domain/CountryContinentMapping.kt`

- Object 类，包含 `private val MAPPING: Map<String, String>` — key 为国家名/ISO code 小写, value 为区域名
- 区域划分（共 8 个）:
  - `China` — 中国（含 "china", "cn", "chinese", "people's republic of china" 等变体）
  - `Asia (Japan & Korea)` — 日本、韩国（含 "japan", "jp", "korea", "kr", "south korea" 等）
  - `Asia (Other)` — 亚洲其余国家（印度、新加坡、泰国、越南、马来西亚等）
  - `Europe` — 欧洲国家
  - `North America` — 美国、加拿大、墨西哥等
  - `South America` — 巴西、阿根廷、智利等
  - `Africa` — 非洲国家
  - `Oceania` — 澳大利亚、新西兰等
  - `Other` — 未识别值
- 覆盖 ~200 个常见国家名称和 ISO 2-letter code 变体
- 公开方法: `fun toRegion(countryOrNationality: String?): String` — normalize + lookup, 找不到返回 "Other"
- 公开方法: `fun countriesForRegion(region: String): Set<String>` — 返回该区域下所有已知 key（用于构建 ES terms filter）
- 公开方法: `fun allRegions(): List<String>` — 返回固定顺序的区域列表
- 遵守 I-1: 纯内存映射
- 遵守 I-3: 默认归 Other

**Task A2**: 为 `CountryContinentMapping` 编写单元测试
- 文件: `src/test/kotlin/.../expert/domain/CountryContinentMappingTest.kt`
- 验证: "China", "CN", "Chinese" → `China`
- 验证: "Japan", "JP", "Korea", "KR", "South Korea" → `Asia (Japan & Korea)`
- 验证: "India", "IN", "Singapore", "SG" → `Asia (Other)`
- 验证: "US", "United States", "Canada" → `North America`
- 验证: "Brazil", "Argentina" → `South America`
- 验证: "GB", "Germany" → `Europe`
- 验证: null/blank/unknown → `Other`
- 验证: `countriesForRegion("China")` 包含 "china", "cn" 等
- 验证: `countriesForRegion("Asia (Japan & Korea)")` 包含 "japan", "korea" 等

### Phase B: 后端 — 聚合接口 (I-1, I-3, I-4)

**Task B1**: `ExpertSearchService` 新增 `aggregateRegions(level)` 方法

- 向 ES 发送 `size: 0` 查询，aggs 为 `terms` on `country` field, size=500
- 遍历 buckets，用 `CountryContinentMapping.toRegion(key)` 归并为 `Map<String, Long>`（区域 → sum of doc_count）
- 返回 `List<RegionCount(region: String, count: Long)>`，按固定区域顺序排列（China → Asia (Japan & Korea) → Asia (Other) → Europe → North America → South America → Africa → Oceania → Other）
- 遵守 I-1: ES 只做 terms，映射在 Kotlin 侧
- 遵守 I-4: 先只聚合 country 字段（nationality 为辅助搜索字段）

**Task B2**: 新增 data class `RegionCount(val region: String, val count: Long)`（放在 `ExpertSearchService.kt` 文件末尾，仿 `EmailDomainCount`）

**Task B3**: `ExpertIndexController` 新增端点

```kotlin
@GetMapping("/regions")
fun getRegions(
    @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel
): List<RegionCount>
```

- 调用 `expertSearchService.aggregateRegions(level)`

### Phase C: 后端 — 搜索支持 region 参数 (I-2, I-4)

**Task C1**: `ExpertSearchService.searchExperts()` 新增 `region: String? = null` 参数

- 当 region 不为空时:
  - 调用 `CountryContinentMapping.countriesForRegion(region)` 获取该区域所有国家 key 集合
  - 构建 filter: `bool.should: [terms(country, [...keys]), terms(nationality, [...keys])], minimum_should_match: 1`
  - 将此 filter 加入外层 `filters` 列表
- 遵守 I-2: 在 ES 端过滤
- 遵守 I-4: 同时匹配 country 和 nationality

**Task C2**: `ExpertIndexController.listExperts()` 新增 `@RequestParam(required = false) region: String?` 参数
- 透传给 `searchExperts()`

### Phase D: 前端 (I-2)

**Task D1**: `index.html` — 在 `#expertEmailDomainFilter` 后面增加 region 下拉

```html
<label class="toolbar-label">
    地区:
    <select id="expertRegionFilter">
        <option value="">全部地区</option>
    </select>
</label>
```

**Task D2**: `app.js` — 新增 `loadRegions(level)` 函数

- 调用 `GET /api/experts/regions?level=${level}`
- 填充 `#expertRegionFilter` options: `${d.region} (${d.count})`
- 仿 `loadEmailProviders` 模式

**Task D3**: `app.js` — `loadContacts()` 中读取 region 值并传参

- `const region = $("#expertRegionFilter")?.value || "";`
- ES 查询路径: `if (region) params.set("region", region);`
- needsAttention 路径: 前端无法过滤区域（MySQL 接口无此字段），region 筛选在 needsAttention 模式下禁用（类似 tag 的处理方式）

**Task D4**: `app.js` — 自动刷新监听

- 在 line 5922 的 forEach 数组中增加 `"expertRegionFilter"`
- 在 `updateFilterBadge` 中增加对 `expertRegionFilter` 的判断
- `loadContacts()` 中切换 level 时调用 `loadRegions(level)`（仿 `loadEmailProviders` 时机）

### Phase E: 测试

**Task E1**: `ExpertSearchServiceTest` 新增测试
- `aggregateRegions merges country buckets into region counts` — mock ES 返回含 "China", "Japan", "India", "Germany", "US", "Brazil" 等 bucket，验证归并为 China / Asia (Japan & Korea) / Asia (Other) / Europe / North America / South America 且计数正确
- `searchExperts with region adds terms filter on country and nationality` — 验证请求体中包含 should + terms

**Task E2**: `ExpertIndexControllerTest` 新增测试
- `getRegions delegates to searchService` — mock 返回，验证端点调用
- `listExperts passes region parameter to searchService` — 仿 emailDomain 测试

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../expert/domain/CountryContinentMapping.kt` | 新增 | 国家→区域静态映射 |
| 2 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改 | 加 `aggregateRegions()` + `searchExperts` 加 region 参数 |
| 3 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | 修改 | 加 `/regions` 端点 + region 参数 |
| 4 | `src/main/resources/static/index.html` | 修改 | 加 region 下拉 |
| 5 | `src/main/resources/static/app.js` | 修改 | loadRegions + loadContacts 传参 + 监听 |
| 6 | `src/test/kotlin/.../expert/domain/CountryContinentMappingTest.kt` | 新增 | 映射单测 |
| 7 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | 修改 | 聚合 + 搜索测试 |
| 8 | `src/test/kotlin/.../expert/controller/ExpertIndexControllerTest.kt` | 修改 | 端点测试 |

**Total: 8 files, 1 subsystem**

## 验收标准

- **I-1**: `CountryContinentMappingTest` 通过，`aggregateRegions` 单测中 ES mock 只返回 raw terms buckets（无 script），Kotlin 侧正确归并
- **I-2**: `searchExperts` 单测验证当 `region="Europe"` 时，请求体包含 `{"bool":{"should":[{"terms":{"country":[...]}},{"terms":{"nationality":[...]}}],"minimum_should_match":1}}`，且在外层 `filter` 中
- **I-3**: `CountryContinentMappingTest` 验证 `toRegion("xyzabc")` 返回 "Other"；`aggregateRegions` 单测验证未知国家归入 Other 的计数
- **I-4**: `searchExperts` 的 region filter 同时包含 country 和 nationality 两个 terms 条件

**集成场景**:
- 前端选择 "China" → API 请求含 `region=China` → 返回结果只含中国专家 → totalHits 正确 → 分页正常
- 前端选择 "Asia (Japan & Korea)" → 只返回日韩专家
- 前端选择 "North America" → 返回美国、加拿大等
- 前端切换 level (RAW→CANDIDATE) → region 下拉刷新数据 → 计数对应新 level
- needsAttention 模式下 region 下拉 disabled
- 所有现有筛选 (`mvn test`) 不受影响
