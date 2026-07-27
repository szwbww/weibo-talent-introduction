# Plan 2: ES ID 统一 — MySQL 侧路径改 _by_query + Discovery ID 生成归一（P1）

## 前置依赖

Plan 1 已完成（`ExpertProfile.esDocId` 贯通、revalidation 修复）。

## 问题

Plan 1 修复了「从 ES 读 → 操作回 ES」的路径。但还有一类调用方来源是 MySQL `ExpertContact`，只有 `orcidId` 字段，没有 `esDocId`：

- `demoteToRaw(contact.orcidId, contact)` — 运营手动降级
- `syncCandidateOperatorStatus(contact.orcidId, status)` — 外呼后标记 CONTACTED、运营改状态
- `syncCandidateOperatorStatusBatch` — 批量同步

这些路径同样存在 `_id ≠ orcidId` 的问题，只是线上影响较小（仅运营操作过的专家，数量有限）。

另外，Discovery 的 ID 生成逻辑散落在 `ExpertDiscoveryService` 里，有两套（ORCID 前缀 / EMAIL hash），应统一收敛。

## 改动

### T1. `ExpertIndexWriterService` — MySQL 侧方法改 `_by_query`

#### T1a. `demoteToRaw(orcid, contact)`

candidate 和 application 两个索引的删除改为 `_delete_by_query`：

```
POST /{index}/_delete_by_query
{
  "query": { "term": { "orcidId": orcid } }
}
```

返回值：`deleted >= 1` 为 true，否则 false。

同时保留按 `_id` 删除作为第一步尝试（如果 `_id` = `orcidId` 则更快），失败再 fallback 到 `_delete_by_query`。或者直接只用 `_delete_by_query`（更简洁）。

#### T1b. `syncCandidateOperatorStatus(orcidId, operatorStatus)`

改为 `_update_by_query`：

```
POST /{candidateIndex}/_update_by_query
{
  "query": { "term": { "orcidId": orcidId } },
  "script": {
    "source": "ctx._source.operatorStatus = params.status; ctx._source.updatedAt = params.now",
    "params": { "status": operatorStatus, "now": now }
  }
}
```

`NOT_CONTACTED` 场景（remove 字段）同理用 script。

#### T1c. `syncCandidateOperatorStatusBatch`

bulk API 按 `_id` 操作。两个选项：

- **选项 A**：改为逐条 `_update_by_query`（简单但慢）
- **选项 B**：先批量查询获取 `orcidId → _id` 映射，再用 bulk API（快但多一次查询）
- **选项 C**：改为 `_update_by_query` + terms 查询按状态分组批量执行

推荐选项 B：用 `_mget` 或 `terms` 查询批量拿到 `_id` 映射后走原有 bulk 逻辑。

### T2. Discovery ID 生成归一

文件：新建 `expert/service/ExpertIdGenerator.kt`

```kotlin
object ExpertIdGenerator {
    /** 统一生成 ES 文档 _id，同时作为 orcidId 字段值 */
    fun generate(orcidId: String?, email: String?): String = when {
        !orcidId.isNullOrBlank() -> orcidId   // 有 ORCID 直接用
        !email.isNullOrBlank() -> generateFromEmail(email)
        else -> error("Cannot generate ID: both orcidId and email are null")
    }

    private fun generateFromEmail(email: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(19)
        return "EMAIL-$hash"
    }
}
```

#### T2a. `ExpertDiscoveryService` 改用 `ExpertIdGenerator`

替换两处 ID 生成：

```kotlin
// 改前（ORCID 数据源）
val esDocId = authorEmail.orcidId ?: "ORCID-${record.orcidId}"

// 改后
val esDocId = ExpertIdGenerator.generate(authorEmail.orcidId ?: record.orcidId, authorEmail.email)
```

```kotlin
// 改前（论文数据源）
val esDocId = authorEmail.orcidId ?: generateIdFromEmail(authorEmail.email)

// 改后
val esDocId = ExpertIdGenerator.generate(authorEmail.orcidId, authorEmail.email)
```

删除 `ExpertDiscoveryService.generateIdFromEmail` 私有方法。

#### T2b. 确保 `orcidId` 字段值 = `_id`

在 `toIndexMap` 中，将 `esDocId` 同时写入 `orcidId` 字段：

```kotlin
private fun toIndexMap(profile: ExpertProfile, paper: PaperMetadata?, esDocId: String, ...): Map<String, Any?> {
    return mapOf(
        "orcidId" to esDocId,   // ← 确保 orcidId 字段 = _id
        // ... 其余不变
    )
}
```

这样新写入的文档 `_id` = `orcidId` 字段值，从根源消除不一致。

### T3. `ExpertRevalidationService.promoteRawToCandidate` — 用 `esDocId` 写入

Plan 1 已改为用 `profile.esDocId` 读写，这里无额外改动。仅确认 `writeCandidateDocument(docId, doc)` 中 `doc` 里的 `orcidId` 字段也是 `docId`（保证新晋升的候选人 `_id` = `orcidId`）。

## 存量数据

新 ID 策略去掉了 `ORCID-` 前缀。存量数据中 `_id` 带 `ORCID-` 前缀但 `orcidId` 字段不带前缀的文档：

- Plan 1 的 `esDocId` 贯通已解决读→写路径
- Plan 2 T1 的 `_by_query` 解决 MySQL→写路径
- 不做全量迁移，存量数据自然消亡（被 revalidation 清理或被新数据覆盖）

## 不做

- 全量 `_id` 修正迁移（风险大、收益低，Plan 1 + Plan 2 已覆盖所有操作路径）
- `markApplicationClosed` / `syncApplicationStatus`（application 索引 `_id` 已对齐）

## 测试

- `ExpertIdGenerator` 单测：ORCID 优先、email fallback、两者都空抛异常
- `syncCandidateOperatorStatus` 测试：mock `_update_by_query` 响应，验证 updated 判断
- `demoteToRaw` 测试：mock `_delete_by_query` 响应
- `syncCandidateOperatorStatusBatch` 测试：验证 `_id` 映射查询 + bulk 流程
- 集成验证：运营降级、状态同步功能正常
