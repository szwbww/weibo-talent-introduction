# Plan 1: ES esDocId 贯通 — 修复 revalidation 删除失败（P0）

## 问题

`ExpertIndexWriterService` 中 DELETE/HEAD/UPDATE 操作用 `orcidId` 字段值作为 ES `_id`。但 Discovery 写入的文档 `_id` 有三种格式（裸 orcidId、`ORCID-` 前缀、`EMAIL-` hash），导致 `_id ≠ orcidId`，所有按 `_id` 操作静默失败。

直接后果：revalidation 处理 152,643 条，报告降级 91,446 条，实际候选人索引总数未变。

## 方案

ES scroll/search 返回的 hit 本身就带 `_id`，当前 `toExpertProfile` 只读了 `_source` 把它丢了。把 `_id` 透传到 `ExpertProfile.esDocId`，所有从 ES 读出来再操作回 ES 的路径用 `esDocId`。

## 改动

### T1. `ExpertProfile` 加 `esDocId` 字段

文件：`expert/domain/ExpertProfile.kt`

```kotlin
data class ExpertProfile(
    val esDocId: String? = null,   // ← 新增，ES 文档 _id
    val orcidId: String,
    // ... 其余不变
)
```

默认 `null`，不影响 Discovery 等直接构造 ExpertProfile 的地方。

### T2. `ExpertSearchService.toExpertProfile` 读取 `hit._id`

文件：`expert/service/ExpertSearchService.kt`

当前签名 `toExpertProfile(source: JsonNode)` 只接收 `_source`。改为接收整个 hit 节点：

```kotlin
private fun toExpertProfile(hit: JsonNode): ExpertProfile {
    val source = hit.path("_source").takeUnless { it.isMissingNode } ?: hit
    val esDocId = hit.path("_id").asText(null)
    return ExpertProfile(
        esDocId = esDocId,
        orcidId = source.nullableText("orcidId") ?: ...,
        // ... 其余不变
    )
}
```

同步修改所有调用点（`searchExperts`、`scrollExperts`、`searchExpertsWithEmail`、`searchByOrcidIds`、`scrollExpertsFiltered`）：把传给 `toExpertProfile` 的参数从 `hit.path("_source")` 改为 `hit` 本身。

### T3. `ExpertIndexWriterService` — 按 `_id` 操作的方法改为接收 `esDocId`

文件：`expert/service/ExpertIndexWriterService.kt`

#### T3a. `removeFromCandidateIndex`

```kotlin
// 改前
fun removeFromCandidateIndex(orcid: String): Boolean

// 改后
fun removeFromCandidateIndex(esDocId: String): Boolean
```

内部 DELETE URL 不变（`/_doc/$esDocId`），只是调用方传入的值从 `profile.orcidId` 变为 `profile.esDocId`。

**关键修改：404 不再返回 true**，改为 `log.warn + return false`。这样真正删不到的文档会计入 `demotionFailed`。

#### T3b. `documentExistsInIndex`

```kotlin
// 改前
fun documentExistsInIndex(indexLevel: ExpertIndexLevel, orcid: String): Boolean

// 改后
fun documentExistsInIndex(indexLevel: ExpertIndexLevel, esDocId: String): Boolean
```

HEAD 按 `_id` 查，传入正确的 `esDocId` 即可。

#### T3c. `addTag` / `removeTag`

```kotlin
// 改前
fun addTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean

// 改后 — 签名不变，参数语义变化（传入 esDocId 而非 orcidId）
```

签名本身就是 `docId`，语义上已经是文档 ID，只需确保调用方传正确值。

### T4. `ExpertRevalidationService` — 调用方传 `esDocId`

文件：`expert/service/ExpertRevalidationService.kt`

#### T4a. `revalidateCandidates()` 中三处调用

```kotlin
// 改前
expertIndexWriterService.removeFromCandidateIndex(profile.orcidId)
expertIndexWriterService.addTag(profile.orcidId, "verified", ExpertIndexLevel.CANDIDATE)

// 改后
val docId = profile.esDocId ?: profile.orcidId  // 防御性回退
expertIndexWriterService.removeFromCandidateIndex(docId)
expertIndexWriterService.addTag(docId, "verified", ExpertIndexLevel.CANDIDATE)
```

#### T4b. `promoteEligibleRawExperts()` 中 `documentExistsInIndex`

```kotlin
// 改前
exists = expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, profile.orcidId)

// 改后
exists = expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, profile.esDocId ?: profile.orcidId)
```

注意：这里 profile 来自 RAW 索引 scroll，RAW 索引的 `_id` 和 CANDIDATE 索引的 `_id` 应该一致（同一个 esDocId 用于两个索引）。需要确认 Discovery 写入时 RAW 和 CANDIDATE 用的是同一个 `esDocId`（代码确认：是的，`promoteDiscoveredToCandidate(esDocId, ...)` 两个索引用同一个 ID）。

#### T4c. `promoteRawToCandidate()` 中 `readRawDocument` 和 `writeCandidateDocument`

```kotlin
// 改前
val rawDoc = expertIndexWriterService.readRawDocument(profile.orcidId)
return expertIndexWriterService.writeCandidateDocument(profile.orcidId, doc)

// 改后
val docId = profile.esDocId ?: profile.orcidId
val rawDoc = expertIndexWriterService.readRawDocument(docId)
return expertIndexWriterService.writeCandidateDocument(docId, doc)
```

同步修改 `readRawDocument` 和 `writeCandidateDocument` 的参数名从 `orcid` 改为 `docId`（可选，仅语义清晰化）。

### T5. `removeFromCandidateIndex` 404 语义修正

文件：`expert/service/ExpertIndexWriterService.kt`

```kotlin
// 改前
} catch (e: HttpClientErrorException) {
    if (e.statusCode == HttpStatus.NOT_FOUND) {
        true   // ← 错误：404 当成功
    }
}

// 改后
} catch (e: HttpClientErrorException) {
    if (e.statusCode == HttpStatus.NOT_FOUND) {
        log.warn("Candidate doc not found for esDocId={}, DELETE returned 404", esDocId)
        false
    } else {
        log.warn("Failed to remove esDocId={} from candidate index (HTTP {})", esDocId, e.statusCode)
        false
    }
}
```

## 不做（留 Plan 2）

- `demoteToRaw`、`syncCandidateOperatorStatus`、`syncCandidateOperatorStatusBatch` — 调用方来源是 MySQL ExpertContact，没有 `esDocId`，需要改 `_by_query`，属于 Plan 2
- `markApplicationClosed`、`syncApplicationStatus` — application 索引由本系统写入，`_id` = orcidId 一致，暂不改
- Discovery `ExpertIdGenerator` 抽取和 ID 统一 — Plan 2
- 全量 `_id` 数据修正迁移 — 不做

## 测试

- `ExpertSearchService` 测试：mock ES response 包含 `_id` 字段，验证 `esDocId` 正确赋值
- `ExpertRevalidationService` 测试：验证传给 writer 的参数是 `esDocId` 而非 `orcidId`
- `removeFromCandidateIndex` 测试：404 返回 false（不再返回 true）
- 线上验证：revalidation 跑完后 candidate `_count` = passed 数
