---
id: K-workbench-evidence-two-layer-global-coupling
domain: llm
created: 2026-08-19
last_used: 2026-08-20
hit_count: 1
source: create-p:workbench-repair-03a-per-request-evidence-version
severity: P1
---

经验：工作台的 `evidenceSetVersion` 是**两层**全局耦合，不是一层。要把失效粒度从「整份草稿」降到「单个请求条目」时，只改 mapping 那一层会漏掉 base 那一层，结果改完仍然全量失效。

两层（`TrustReplyWorkbenchService.kt`，2026-08-19 实测）：
1. `baseEvidence`（:1514-1518）= `buildEvidenceSnapshotForSelection(selection.sendQaRuleIds)` 或 `localEvidenceSetVersion(...)` —— 输入是**全部条目规则的并集**。
2. `evidenceSetVersionWithMapping`（:1576-1584）—— 再拌**全矩阵** `requestKey→factRuleIds`。

给条目 3 加一个事实，`sendQaRuleIds` 并集变了，条目 1 的 base 也跟着变。所以 per-request 化必须让 **base 也按子集算**。好在两个 base 函数签名本就是 `List<Long>`，天然支持子集调用。

配套结论：
- `versionId()`（:1890-1908）把 evidence 版本算进哈希，`validateLockedItem`（:1205-1207）据此比对 → per-request 化会改变**所有**既有 versionId，须升 `TrustReplyWorkbenchStateStore.SCHEMA_VERSION`（存量 payload 里的聚合值与 per-request 值同为 64 位 sha256 串，不升版本会静默错配）。
- `requireCurrentEvidenceVersion` 有 3 个调用点：`:464` saveState（保留聚合）、`:959` adjustItem（改 per-request）、`:1051` assemble（**应删除**——逐条 `validateLockedItem` + `validateMatrixKeys` 完整性校验严格更强；留着它则改任一条目后 assemble 必 409）。
- **不需要数据库迁移**：`grep -rn "evidence_set_version" src/main/resources/db/migration/` 与 `grep -rn "evidenceSetVersion" src/main/resources/ --include=*.json` 均零命中，该值只走 `payload_json` 与审计 JSON。
- **不需要改字段名**：`TrustReplyLockedItemRequest`（controller :296）与 `TrustReplyItemVersion`（service :315）本来就每条带一个 `evidenceSetVersion`，前端也已逐条拷贝（app.js:3419/9551、trust-reply-workbench.js:603/1328）。改名会把变更面从 9 个文件撑到 17 个。

保住语义的依据：[[K-request-fact-assignment-version-must-include-mapping]] 要求「同一并集换绑到另一 request 必须产生不同版本」。把 `requestKey` 直接绑进 per-request 版本、并保留跨条目唯一性校验（`TRUST_REPLY_FACT_ALREADY_ASSIGNED`，:1510-1513），语义比全局标量更强：换绑失效两端、其余不动。

部分恢复有先例：`handleFrameStale`（trust-reply-workbench.js:381-388）的注释写着「must never reset versions」，前端已有「已保留 N 项锁定回答」文案（:578）。
