# Plan: OpenAlex Enrichment 限流修复 + 批量优化

## 需求描述

**可观测结果**：补充学术数据任务（`EXPERT_ENRICHMENT`）不再因 OpenAlex API 限流导致 95k+ 条全部失败报 `OPENALEX_API_ERROR`。核心改进：

1. 批量 ORCID 查询（50 个/请求），将 95k 次 API 调用降至 ~1900 次
2. works/patents 获取改为可选（默认关闭），进一步降低调用量
3. 限流时自动退避 + 熔断，不再无脑重试
4. 多次运行自动续跑（已有 `enrichedAt` 过滤机制，无需额外改动）

**吞吐量估算**：
- 纯批量搜索（不含 works/patents）：95k / 50 = 1900 次调用，每次 300ms delay → **~10 分钟**
- 含 works/patents（假设 50% 命中）：1900 + 47.5k × 2 ≈ 97k 次调用 → **~8 小时**，仍可一天内完成
- 若触发限流，退避后自动恢复；极端情况熔断退出，下次运行自动续跑未完成部分

**不可改变**：
- `enrichExistingExperts` 的业务语义（遍历 CANDIDATE 层、写回三层索引）不变
- `EnrichmentOutcome.Success / NotFound` 的语义不变
- 发现任务（`discoverFromSource`）的现有限流逻辑不变
- 前端进度展示接口契约不变（`failureReasons` map 格式不变，仅增加新 key）
- `enrichAuthorByOrcid` / `enrichAuthorByOrcidWithReason` 单条接口保留（discovery 路径仍用）

**不在范围内**：
- OpenAlex Premium / API key 接入
- 将 enrichment 改为异步队列消费
- 修改 discovery 路径的限流逻辑
- 前端 UI 针对 rate-limit 的特殊展示

---

## 关键不变量

### Invariant I-1: enrichment 路径必须区分限流与其他 API 错误
- Rule: `enrichAuthorByOrcidWithReason` 和新增的 `batchEnrichByOrcids` 遇到 HTTP 429/503 时返回 `EnrichmentOutcome.RateLimited`，其他 HTTP 错误和网络异常返回 `EnrichmentOutcome.ApiError`。调用方 `enrichExistingExperts` 根据不同 outcome 计入不同的 `failureReasons` key（`RATE_LIMITED` vs `OPENALEX_API_ERROR`）。
- Applies to: `OpenAlexDataSource.enrichAuthorByOrcidWithReason`、`OpenAlexDataSource.batchEnrichByOrcids`、`ExpertDiscoveryService.enrichExistingExperts`
- Violation consequence: 限流被误归为 API 错误，无法触发退避/熔断

### Invariant I-2: enrichment 循环必须有熔断机制
- Rule: `enrichExistingExperts` 维护 `consecutiveRateLimits` 计数器，遇到 `RateLimited` 时递增并 sleep 退避（指数退避，初始 2s，上限 60s）；遇到非限流结果时归零。连续 ≥ 5 次 `RateLimited` 后熔断退出循环，任务状态写入 `COMPLETED`（非 FAILED），`failureReasons` 中包含 `CIRCUIT_BREAKER=1`，与 discovery 路径行为一致。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts` 批量处理循环
- Violation consequence: 限流后无穷循环浪费时间、API 配额永远恢复不了

### Invariant I-3: `enrichAuthor` 及其子调用的 HTTP 429/503 不可被内部 catch 吞掉
- Rule: `enrichAuthor`（line 105-131）当前对所有异常返回 `null`。修改后，对 `HttpStatusCodeException` 中 429/503 必须向上抛出。`fetchRecentWorks`/`fetchPatents` 同理。非限流异常可继续返回 `null`。
- Applies to: `OpenAlexDataSource.enrichAuthor`、`fetchRecentWorks`、`fetchPatents`
- Violation consequence: 限流发生在子调用时被吞成 `NotFound`，熔断机制失效

### Invariant I-4: OpenAlex 使用专用 RestTemplate，配有 timeout
- Rule: `OpenAlexDataSource` 注入 `@Qualifier("openAlexRestTemplate")` 的 RestTemplate，配置 connectTimeout=5s、readTimeout=15s。不使用 RetryingInterceptor（重试由业务层熔断器管理）。
- Applies to: `RestTemplateConfig`、`OpenAlexDataSource` 构造函数
- Violation consequence: 默认 RestTemplate 无超时，网络卡住时线程永久阻塞

### Invariant I-5: 批量查询必须从搜索响应直接解析 enrichment 数据
- Rule: `batchEnrichByOrcids` 调用 `/authors?filter=orcid:id1|id2|...|idN` 一次查多个 ORCID，从搜索响应的 `results[]` 中直接解析 `summary_stats.h_index`、`cited_by_count`、`works_count`、`topics`、`works_api_url`，**不再逐个调用 `enrichAuthor`**。仅当 `fetchWorksEnabled=true` 时才对匹配到的 author 调用 `fetchRecentWorks` + `fetchPatents`。
- Applies to: `OpenAlexDataSource.batchEnrichByOrcids`
- Violation consequence: 批量查询后又逐个调 enrichAuthor，失去批量优势

### Invariant I-6: works/patents 获取默认关闭
- Rule: `OpenAlexProperties.fetchWorksEnabled`（默认 false）和 `fetchPatentsEnabled`（默认 false）控制是否在 enrichment 中额外获取 recentWorkTitles 和 patentTitles。关闭时批量 enrichment 每 50 experts 仅 1 次 API 调用。
- Applies to: `OpenAlexProperties`、`OpenAlexDataSource.batchEnrichByOrcids`
- Violation consequence: 无法控制调用量，95k experts 仍产生大量 API 调用

### Invariant I-7: 批量查询的 batch size 可配置
- Rule: `OpenAlexProperties.enrichmentBatchSize`（默认 50）控制每次批量 ORCID 查询的数量。OpenAlex `/authors` 的 `per_page` 上限为 200，但 filter 中 pipe 分隔的 ORCID 过多会导致 URL 过长，50 是安全值。
- Applies to: `OpenAlexProperties`、`ExpertDiscoveryService.enrichExistingExperts`
- Violation consequence: batch size 不当导致请求失败或效率低下

---

## 现状审计

### `OpenAlexDataSource` — API 调用层

- **写路径**（对外部 API 的调用）：
  1. `searchPapers` (line 31-46) — discovery 搜索论文，有 `requestDelayMs` sleep
  2. `enrichAuthorByOrcidWithReason` (line 170-187) — 按单个 ORCID 搜 author，有 `requestDelayMs` sleep；内部调 `enrichAuthor`
  3. `enrichAuthor` (line 105-131) — GET `/authors/{id}`，有 sleep；内部调 `fetchRecentWorks` + `fetchPatents`
  4. `fetchRecentWorks` (line 133-146) — GET works API，有 sleep
  5. `fetchPatents` (line 148-161) — GET works API (filter=patent)，有 sleep
- **错误处理现状**：
  - `searchPapers`: 异常直接 throw（由 `discoverFromSource` 的 circuit breaker 处理）— ✅ 正确
  - `enrichAuthorByOrcidWithReason`: 所有异常 catch 为 `EnrichmentOutcome.ApiError` — ❌ 不区分 429
  - `enrichAuthor`: 所有异常 catch 返回 `null` — ❌ 吞掉 429
  - `fetchRecentWorks` / `fetchPatents`: 所有异常 catch 返回 `null` — ❌ 吞掉 429
- **RestTemplate**: 注入的是默认 `RestTemplate()`（line 18），无 timeout、无 retry interceptor
- **无批量 ORCID 查询方法** — 当前逐条调用

### `ExpertDiscoveryService.enrichExistingExperts` — 业务循环层

- **读路径**：`scrollExpertsFiltered`（batchSize=500）遍历 CANDIDATE 层
- **写路径**：
  1. `updateExpertAcademicFields` (line 954-982) — ES partial update 三层索引 (来源: K-enrichment-write-three-layers)
  2. `progressStore.update` — 每批次写进度
- **错误处理现状**：
  - `when (outcome)` 分支 (line 853-869)：`Success` → 写 ES；`NotFound` → 计 `ORCID_NOT_IN_OPENALEX`；`ApiError` → 计 `OPENALEX_API_ERROR`
  - **无退避、无熔断、无 consecutiveFailures 计数器** — ❌
- **调用量**：每个 expert = 1 次 `enrichAuthorByOrcidWithReason`（最多 4 次 HTTP 调用），95k experts ≈ 380k API calls
- **多次运行**：`buildEnrichmentFilters()` 已过滤 `enrichedAt` 不存在或超过 30 天的记录。成功 enriched 的 expert 会被写入 `enrichedAt`，下次运行自动跳过 → **已支持续跑，无需额外改动**

### `OpenAlexProperties` — 配置

- 当前字段：`enabled`, `politeEmail`, `baseUrl`, `requestDelayMs`(100), `maxPapersPerSource`(500)
- 无 enrichment 专用 delay、无 timeout、无 batch size、无 works/patents 开关

### OpenAlex API 特性（与本计划相关）

- `/authors?filter=orcid:id1|id2|...` 支持 pipe 分隔的 OR 过滤，单次返回多个 author 完整对象
- 搜索响应中 `results[]` 每个元素包含 `summary_stats.h_index`、`cited_by_count`、`works_count`、`topics`、`works_api_url` — 与 GET `/authors/{id}` 返回的字段一致
- `per_page` 上限 200，但 filter URL 长度有限，50 个 ORCID 约 1000 字符，安全
- polite pool 限额：~1000 req / 5 min（~3.3 req/s），日限额 ~100k
- 响应头含 `X-RateLimit-Remaining`、429 时含 `Retry-After`

### 交互点

1. `enrichAuthor` 返回 `null` 时 → `enrichAuthorByOrcidWithReason` 将其视为 `NotFound` → **若 null 因 429 引起，熔断失效**
2. `OpenAlexDataSource` 的 `restTemplate` 同时被 discovery（`searchPapers`）和 enrichment 路径使用。改为专用 RestTemplate 后需确保 `searchPapers` 也用它
3. `scrollExpertsFiltered` batchSize=500，但 OpenAlex 批量查询建议 50 个/请求。需在业务层将 500 人的 scroll 批次再分成 50 人的 API 批次

---

## 实现方案

### 阶段 1：EnrichmentOutcome 类型扩展 + 错误分类 + 批量查询方法

**遵守不变量**: I-1, I-3, I-5, I-6

**Task 1.1**: `EnrichmentOutcome` sealed class 新增 `RateLimited` 子类型

```kotlin
data class RateLimited(val retryAfterMs: Long? = null) : EnrichmentOutcome()
```

**Task 1.2**: 修改 `enrichAuthor`（line 105-131）— 429/503 重新抛出

```kotlin
} catch (e: HttpStatusCodeException) {
    val code = e.statusCode.value()
    if (code == 429 || code == 503) throw e
    log.debug("OpenAlex author enrichment failed for {}: {} (HTTP {})", openAlexAuthorId, e.message, code)
    null
} catch (e: Exception) {
    log.debug("OpenAlex author enrichment failed for {}: {}", openAlexAuthorId, e.message)
    null
}
```

**Task 1.3**: 同理修改 `fetchRecentWorks` 和 `fetchPatents` — 429/503 重新抛出

**Task 1.4**: 修改 `enrichAuthorByOrcidWithReason` — 拆分 catch，429/503 → `RateLimited`

```kotlin
} catch (e: HttpStatusCodeException) {
    val code = e.statusCode.value()
    if (code == 429 || code == 503) {
        val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
        return EnrichmentOutcome.RateLimited(retryAfter)
    }
    log.debug("OpenAlex ORCID lookup failed for {}: {} (HTTP {})", orcid, e.message, code)
    EnrichmentOutcome.ApiError("HTTP $code: ${e.message}")
} catch (e: Exception) {
    log.debug("OpenAlex ORCID lookup failed for {}: {}", orcid, e.message)
    EnrichmentOutcome.ApiError(e.message ?: "unknown error")
}
```

**Task 1.5**: 新增 `batchEnrichByOrcids` 方法

核心逻辑：
```kotlin
fun batchEnrichByOrcids(orcids: List<String>): Map<String, EnrichmentOutcome> {
    val filterValue = orcids.joinToString("|")
    val url = "${properties.baseUrl}/authors?filter=orcid:$filterValue&per_page=${orcids.size}" +
        if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""

    if (properties.enrichmentDelayMs > 0) Thread.sleep(properties.enrichmentDelayMs)

    val response = try {
        restTemplate.getForObject(url, JsonNode::class.java)
    } catch (e: HttpStatusCodeException) {
        val code = e.statusCode.value()
        if (code == 429 || code == 503) {
            val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
            // 批量请求限流 → 所有 orcid 返回 RateLimited
            return orcids.associateWith { EnrichmentOutcome.RateLimited(retryAfter) }
        }
        return orcids.associateWith { EnrichmentOutcome.ApiError("HTTP $code") }
    } catch (e: Exception) {
        return orcids.associateWith { EnrichmentOutcome.ApiError(e.message ?: "unknown") }
    }

    // 解析响应：从 results[] 中按 orcid 建立映射
    val foundMap = mutableMapOf<String, AuthorEnrichment>()
    response?.path("results")?.forEach { node ->
        val orcid = node.path("orcid").asText(null)
            ?.removePrefix("https://orcid.org/") ?: return@forEach
        val worksApiUrl = node.path("works_api_url").asText(null)
        val topics = node.path("topics")
            .takeIf { it.isArray }
            ?.sortedByDescending { it.path("count").asInt(0) }
            ?.take(5)
            ?.mapNotNull { it.path("display_name").asText(null) }

        // 可选：获取 works/patents（仅当配置开启时）
        val recentWorkTitles = if (properties.fetchWorksEnabled && worksApiUrl != null)
            fetchRecentWorks(worksApiUrl, 3) else null
        val patentTitles = if (properties.fetchPatentsEnabled && worksApiUrl != null)
            fetchPatents(worksApiUrl, 3) else null

        foundMap[orcid] = AuthorEnrichment(
            hIndex = node.path("summary_stats").path("h_index").let { if (it.isInt) it.asInt() else null },
            citationCount = node.path("cited_by_count").let { if (it.isInt) it.asInt() else null },
            worksCount = node.path("works_count").let { if (it.isInt) it.asInt() else null },
            topics = topics,
            recentWorkTitles = recentWorkTitles,
            patentTitles = patentTitles
        )
    }

    // 组装返回值：匹配到的 → Success，未匹配的 → NotFound
    return orcids.associateWith { orcid ->
        val enrichment = foundMap[orcid]
        if (enrichment != null) EnrichmentOutcome.Success(enrichment)
        else EnrichmentOutcome.NotFound
    }
}
```

注意：`fetchRecentWorks` / `fetchPatents` 中的 429/503 会通过 Task 1.3 的修改向上抛出。`batchEnrichByOrcids` 需在调用这两个方法时 catch `HttpStatusCodeException` 并对限流情况提前返回 `RateLimited`（仅影响当前批次中尚未处理 works/patents 的 orcid），而已经拿到基础数据的 orcid 仍可返回 `Success`（hIndex/citations 有值，works/patents 为 null）。

### 阶段 2：enrichExistingExperts 改为批量 + 熔断

**遵守不变量**: I-2, I-7

**Task 2.1**: 重构 `enrichExistingExperts` 的核心循环

原逻辑：scroll 500 人 → 逐个调 `enrichAuthorByOrcidWithReason`
新逻辑：scroll 500 人 → 按 50 人一组调 `batchEnrichByOrcids` → 处理结果

```kotlin
var consecutiveRateLimits = 0
var circuitBreakerTripped = false

expertSearchService.scrollExpertsFiltered(...) { batch ->
    if (circuitBreakerTripped || progressStore.isCancelled(taskType)) return@scrollExpertsFiltered false

    // 过滤掉无 ORCID 的
    val withOrcid = batch.filter { !it.orcidId.startsWith("EMAIL-") }
    val noOrcid = batch.size - withOrcid.size
    failed += noOrcid
    if (noOrcid > 0) failureReasons.merge("NO_ORCID_ID", noOrcid) { a, b -> a + b }

    // 按 enrichmentBatchSize 分组，批量查 OpenAlex
    for (chunk in withOrcid.chunked(properties.enrichmentBatchSize)) {
        if (circuitBreakerTripped || progressStore.isCancelled(taskType)) break
        scanned += chunk.size

        val orcidToProfile = chunk.associateBy { it.orcidId }
        val outcomes = openAlex.batchEnrichByOrcids(chunk.map { it.orcidId })

        for ((orcid, outcome) in outcomes) {
            when (outcome) {
                is EnrichmentOutcome.Success -> {
                    consecutiveRateLimits = 0
                    if (updateExpertAcademicFields(orcid, outcome.data)) enriched++
                    else { failed++; failureReasons.merge("ES_UPDATE_FAILED", 1) { a, b -> a + b } }
                }
                is EnrichmentOutcome.NotFound -> {
                    consecutiveRateLimits = 0
                    failed++; failureReasons.merge("ORCID_NOT_IN_OPENALEX", 1) { a, b -> a + b }
                }
                is EnrichmentOutcome.ApiError -> {
                    consecutiveRateLimits = 0
                    failed++; failureReasons.merge("OPENALEX_API_ERROR", 1) { a, b -> a + b }
                }
                is EnrichmentOutcome.RateLimited -> {
                    consecutiveRateLimits++
                    failed++; failureReasons.merge("RATE_LIMITED", 1) { a, b -> a + b }
                    if (consecutiveRateLimits >= 5) {
                        failureReasons["CIRCUIT_BREAKER"] = 1
                        circuitBreakerTripped = true
                        log.warn("Enrichment: 连续 {} 次限流，熔断退出", consecutiveRateLimits)
                        break
                    }
                    val backoffMs = (outcome.retryAfterMs
                        ?: (2000L * (1L shl (consecutiveRateLimits - 1))))
                        .coerceAtMost(60_000)
                    log.info("Enrichment: 限流退避 {}ms (第 {} 次)", backoffMs, consecutiveRateLimits)
                    Thread.sleep(backoffMs)
                }
            }
        }
    }

    // 进度上报（与现有格式一致）
    progressStore.update(taskType, ...)

    !progressStore.isCancelled(taskType) && !circuitBreakerTripped
}
```

注意：批量查询中如果整批都是 `RateLimited`（429 在 batch 请求本身），`consecutiveRateLimits` 会一次增加整批的数量。但因为 `batchEnrichByOrcids` 在 batch 请求 429 时返回所有 orcid → RateLimited，所以 `consecutiveRateLimits` 会在一轮 for 循环内被连续递增。为避免一次 429 就导致计数器飙到 50，**应改为以批次为单位计数**：

```kotlin
// 在 chunk 循环中判断：如果整批结果都是 RateLimited，计数 +1
val allRateLimited = outcomes.values.all { it is EnrichmentOutcome.RateLimited }
if (allRateLimited) {
    consecutiveRateLimits++
    failed += outcomes.size
    failureReasons.merge("RATE_LIMITED", outcomes.size) { a, b -> a + b }
    if (consecutiveRateLimits >= 5) { /* 熔断 */ }
    else { /* 退避 */ }
    continue  // 跳过逐条处理
}
consecutiveRateLimits = 0  // 非全限流则归零
// ...正常逐条处理 outcomes
```

### 阶段 3：专用 RestTemplate + 配置扩展

**遵守不变量**: I-4, I-6, I-7

**Task 3.1**: `OpenAlexProperties` 增加字段

```kotlin
val connectTimeoutMs: Int = 5000,
val readTimeoutMs: Int = 15000,
val enrichmentDelayMs: Long = 300,
val enrichmentBatchSize: Int = 50,
val fetchWorksEnabled: Boolean = false,
val fetchPatentsEnabled: Boolean = false
```

**Task 3.2**: `application.yml` 增加对应配置

```yaml
openalex:
  enabled: ${OPENALEX_ENABLED:true}
  polite-email: ${OPENALEX_POLITE_EMAIL:wuwei@qftechtalent.com}
  base-url: ${OPENALEX_BASE_URL:https://api.openalex.org}
  request-delay-ms: ${OPENALEX_REQUEST_DELAY_MS:100}
  max-papers-per-source: ${OPENALEX_MAX_PAPERS:1200}
  connect-timeout-ms: ${OPENALEX_CONNECT_TIMEOUT_MS:5000}
  read-timeout-ms: ${OPENALEX_READ_TIMEOUT_MS:15000}
  enrichment-delay-ms: ${OPENALEX_ENRICHMENT_DELAY_MS:300}
  enrichment-batch-size: ${OPENALEX_ENRICHMENT_BATCH_SIZE:50}
  fetch-works-enabled: ${OPENALEX_FETCH_WORKS_ENABLED:false}
  fetch-patents-enabled: ${OPENALEX_FETCH_PATENTS_ENABLED:false}
```

**Task 3.3**: `RestTemplateConfig` 新增 `openAlexRestTemplate` bean

```kotlin
@Bean
@Qualifier("openAlexRestTemplate")
fun openAlexRestTemplate(
    openAlexProperties: OpenAlexProperties,
    builder: RestTemplateBuilder
): RestTemplate =
    builder
        .setConnectTimeout(Duration.ofMillis(openAlexProperties.connectTimeoutMs.toLong()))
        .setReadTimeout(Duration.ofMillis(openAlexProperties.readTimeoutMs.toLong()))
        .build()
```

不加 `RetryingClientHttpRequestInterceptor`——重试由业务层熔断器管理，避免双重等待。

**Task 3.4**: `OpenAlexDataSource` 构造函数改为注入 `@Qualifier("openAlexRestTemplate") restTemplate`

### 阶段 4：测试

**Task 4.1**: `OpenAlexDataSourceTest` — 新增测试用例：
- `enrichAuthorByOrcidWithReason returns RateLimited on HTTP 429`
- `enrichAuthor rethrows 429 instead of returning null`
- `batchEnrichByOrcids parses multiple authors from search response`
- `batchEnrichByOrcids returns RateLimited for all orcids on HTTP 429`
- `batchEnrichByOrcids skips works and patents when disabled`

**Task 4.2**: `ExpertDiscoveryServiceTest` — 新增测试用例：
- `enrichExistingExperts uses batch API`
- `enrichExistingExperts backs off on RateLimited batch`
- `enrichExistingExperts trips circuit breaker after 5 consecutive RateLimited batches`
- `enrichExistingExperts resumes from where it left off on next run`（验证 `enrichedAt` 过滤）

**Task 4.3**: `RestTemplateConfigTest` — 验证 `openAlexRestTemplate` bean 存在且有 timeout

---

## 变更文件清单

| # | 文件 | 改动说明 |
|---|------|----------|
| 1 | `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | `EnrichmentOutcome.RateLimited` 新子类型；`enrichAuthor`/`fetchRecentWorks`/`fetchPatents` 429 重新抛出；`enrichAuthorByOrcidWithReason` 拆分 catch；新增 `batchEnrichByOrcids` 批量方法 |
| 2 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | `enrichExistingExperts` 改为批量调用 + RateLimited 退避 + 熔断 + 按批次计数 |
| 3 | `src/main/kotlin/.../config/OpenAlexProperties.kt` | 新增 `connectTimeoutMs` / `readTimeoutMs` / `enrichmentDelayMs` / `enrichmentBatchSize` / `fetchWorksEnabled` / `fetchPatentsEnabled` |
| 4 | `src/main/kotlin/.../config/RestTemplateConfig.kt` | 新增 `openAlexRestTemplate` bean |
| 5 | `src/main/resources/application.yml` | 新增 openalex timeout / enrichment 配置项 |
| 6 | `src/test/kotlin/.../discovery/service/OpenAlexDataSourceTest.kt` | 429→RateLimited、batch 解析、works/patents 开关测试 |
| 7 | `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` | batch 调用、退避、熔断、续跑测试 |
| 8 | `src/test/kotlin/.../config/RestTemplateConfigTest.kt` | `openAlexRestTemplate` bean 验证 |
| 9 | `src/test/resources/application.yml` | 测试配置增加 openalex 新配置项 |

共 9 个文件，≤ 10 限制。

---

## 验收标准

- **I-1**: mock `restTemplate` 抛 `HttpClientErrorException(429)` → `enrichAuthorByOrcidWithReason` 返回 `RateLimited`；抛 `HttpServerErrorException(500)` → 返回 `ApiError`
- **I-2**: mock `batchEnrichByOrcids` 连续 5 批返回全 `RateLimited` → `enrichExistingExperts` 结果含 `CIRCUIT_BREAKER=1` 且提前中止
- **I-3**: mock `restTemplate.getForObject` 在 `enrichAuthor` 调用时抛 429 → 异常传播到 `enrichAuthorByOrcidWithReason` 并返回 `RateLimited`（非 `NotFound`）
- **I-4**: `RestTemplateConfigTest` 验证 `openAlexRestTemplate` bean 存在
- **I-5**: mock 批量搜索响应含 3 个 author → `batchEnrichByOrcids` 返回 3 个 `Success`，其余 → `NotFound`；`recentWorkTitles` 和 `patentTitles` 均为 null（默认关闭）
- **I-6**: `fetchWorksEnabled=false` 时 `batchEnrichByOrcids` 不调用 `fetchRecentWorks`；设为 true 时调用
- **I-7**: scroll batch 500 人按 50 人分组，验证 `batchEnrichByOrcids` 被调用 10 次
- **集成场景**: `mvn test` 全量通过，无新增编译错误
- **续跑场景**: 第一次运行 enriched 100 个后熔断；第二次运行 `scrollExpertsFiltered` 返回的列表不含已 enriched 的 100 个（`enrichedAt` 过滤生效）
