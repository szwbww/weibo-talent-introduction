# Child 01-duplicate — Execution Report

- Outcome: READY_FOR_VERIFICATION
- Plan: docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md
- Plan SHA-256: d78c2b0e05a203558820645cc440c82b4420a782768ab9b88be2d74deb937902
- Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md@d78c2b0e...
- Execution epoch: NEW
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
- Target branch: fast/2026-10-13-discovery-identity-recovery-master
- Pre-execution HEAD: eb8257992d96f1e9dfa1300461766bf6d5af42ad（起始工作树干净）
- Child base: d8ca3ca28fc94c26ec796a04c6ee332c1e760533
- Post-execution commit: bc294d2e991e58346ce456f4e5eeb7525a2b5771（`feat(fast-p): implement 01-duplicate`，仅含 8 个授权文件）

## Required commands（本次新建执行，JDK 11 前缀未省略）

| 命令 | 结果 | 计数 |
|---|---|---|
| C1: `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | PASS (exit 0) | Java：294 tests / 0 failures / 0 errors（ExpertDiscoveryServiceTest 184、DiscoveryPipelineServiceTest 48、DiscoveryIdentityRepairServiceTest 13、ExpertIndexWriterServiceTest 49）；JS 门禁：1657 pass / 0 fail |
| C2: `mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | PASS (exit 0) | Java：48 tests / 0 failures / 0 errors（DiscoveryReviewServiceTest 35、DiscoveryIdentityTest 13）；JS 门禁：1657 pass / 0 fail |

两次 `mvn test` 均自动执行 JS 门禁，失败数 0，总量 1657（与基线一致）。C2 两份既有测试仅执行、未修改。

## 变更摘要（仅 8 个授权文件）

1. `DiscoveryIdentityRepairService.kt`（新增）：重复发现同身份补证协调器与结果类型。
   - 入口 `repairDuplicateIdentity(docId: String, incoming: AuthorEmail)`；结果枚举 `IdentityRepairOutcome`（REPAIRED / ALREADY_VERIFIED / MISSING_NAME_NEEDS_RECOVERY / CONFLICT / PARTIAL / FAILED）与分层类型 `IdentityRepairLayer`/`IdentityRepairLayerOutcome`；`IdentityRepairResult.retryableFailure` 只对 PARTIAL/FAILED 为真。此契约即 02/05 依赖的稳定接口。
   - I-1：仅补证不推测——完整姓名 + 有效来源证据、规范邮箱/已填姓名精确一致；空姓名（incoming 或已存层）返回 MISSING_NAME_NEEDS_RECOVERY 且零写入、不自动补名；已有 proof（含历史认可/无效摘要）不覆盖；无有效证据的 incoming 直接拒绝（零写入）。
   - I-2：按规范邮箱查三层**全量**命中并按 `_id` 去重；查询失败（异常）不当「不存在」；不同 `_id` 同邮箱或层内/跨层/与 incoming 的作者编号冲突一律 CONFLICT 且零写入；缺层不创建；每层只写 `identityVerification` 并带 `if_seq_no/if_primary_term`。
   - I-3：条件写后回读匹配才计 REPAIRED；任一层 409/失败即 PARTIAL/FAILED，且不重试旧快照；重复调用对已落目标 proof 的层视为已完成。
2. `ExpertIndexWriterService.kt`：新增 `findDiscoveryHitsByEmail(email)`（三层全量命中，传输/解析失败抛出、绝不当空表）与 `writeIdentityProofIfVersionMatches(level, docId, seqNo, primaryTerm, proof)`（只写 proof 的条件补丁，区分 UPDATED / MISSING / VERSION_CONFLICT / FAILED）；新增 `DiscoveryEmailHit`、`IdentityProofWriteResult`。
3. `ExpertDiscoveryService.kt`：`recordIdentityDuplicate` 返回 Boolean 并接入补证——合法（完整姓名 + 有效证据）重复进入协调器，按 6 类结果分别累积 `sourceStats.failureReasons["IDENTITY_REPAIR_<OUTCOME>"]`；REPAIRED/ALREADY_VERIFIED 沿用原入队（ensureEnrichmentJob）；CONFLICT/MISSING_NAME_NEEDS_RECOVERY 记独立原因且不入队；PARTIAL/FAILED 返回可重试失败。`ConsumeOutcomeResult` 与 `QueuedItemConsumption` 增加默认 false 的 `identityRepairFailed`（仅内存返回值），`QueuedItemConsumption.succeeded` 包含该项。同步论文页与 ORCID 页都捕获该失败、不推进失败页，并新增停止原因 `DiscoveryStopReason.IDENTITY_REPAIR_INCOMPLETE`。
4. `DiscoveryPipelineService.kt`：消费结果新增 `identityRepairFailed -> "IDENTITY_REPAIR_FAILED"` 重试原因，沿原重试/租约处理（可重试退避 30s/2m/10m/30m，5 次后 FAILED）。
5. `ExpertDiscoveryServiceTest.kt`：新增队列/同步接缝与计数用例（REPAIRED 入队、PARTIAL 置 retryable 不入队、CONFLICT/MISSING_NAME 独立原因不入队、同步页 IDENTITY_REPAIR_INCOMPLETE 不推进）；`createService` 增加 `identityRepairService` 参数（默认 null，保持既有用例逐字行为）。
6. `DiscoveryIdentityRepairServiceTest.kt`（新增）：真实 writer + HTTP 替身；覆盖三层同 ID 补证、双 ID 冲突、409 不重试旧快照且其他层照常、已有历史认可不覆盖、空姓名独立原因、无效 incoming、姓名/邮箱/作者编号冲突、缺层不创建、层失败后 PARTIAL 与恢复只补缺层、邮箱查询失败不当作无重复。
7. `ExpertIndexWriterServiceTest.kt`：新增三层全量命中与查询失败抛出、仅写 proof 的版本条件补丁（URL 带 if_seq_no/if_primary_term、body 仅 identityVerification）、409/404/500 分类。
8. `DiscoveryPipelineServiceTest.kt`：新增补证失败保持可重试、第 5 次才终止且原因恒为 IDENTITY_REPAIR_FAILED。

## 不变量与边界核对

- 未新增/修改 DB 迁移或 ES mapping/字段；无前端改动；未触及 SMTP/生产数据。
- `recordIdentityDuplicate` 仍记 duplicate、不计 indexed；新增人数在补证路径为 0；未创建任何文档（只条件更新既有层）。
- 未改队列 payload 契约；`QueuedItemConsumption.succeeded` 只在内存返回值上收紧。
- 「成功后入队并调用现有准入/重验接缝」按计划「准入投影交给先决方案的跨层流程」解释执行：补证成功后仅走既有 `ensureEnrichmentJob`，准入重验仍由既有 enrichProfiles/revalidateDiscovery 跨层流程负责，repair service 内不复制任何准入政策。

## 未决问题 / 说明

- 无已知 in-scope 失败。
- 说明（非阻断）：`docs/plans/fast/**` 未纳入提交，工作树内仅 `execution.md` 报告被改写；提交仅含上表 8 个授权文件。
- 说明：`identityRepairFailed` 仅存在于内存返回值（`ConsumeOutcomeResult`/`QueuedItemConsumption`），未新增 SourceStats 字段、未落库，与授权文件范围一致。
