# Child 05 Brief — 删除 sendable 概念（词汇清理）

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA: 960fbe48e0b1ad7edd3f2ca68eccd29adafa654b
- Execution report: docs/plans/fast/single-gate/children/05/execution.md
- NOTE (amendment A4, HUMAN-approved 2026-08-28T20:55:48+0800, supersedes prior NOTE): authorized list is now 12 files — the 10 original plan files PLUS
  `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` (delete the two obsolete I1-5 derivation tests
  `parses expertClassification with type-derived sendable ignoring untrusted ES sendable (I1-5)` ~:1874 and
  `ES sendable=true cannot override OUT_OF_SCOPE derived sendable (I1-5)` ~:1929 — they read the deleted `c.sendable`
  property and break compilation) PLUS
  `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` (replace the 3 `ExpertClassification.SENDABLE_TYPES`
  membership checks in the `classification(type)` fixture helper ~:4304/:4305/:4307 with a fixture-local set
  `setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)` — the former constant's exact values,
  behavior unchanged).
  I5-5 gate fixed exclusions (see amended plan): ManualInitialOutreachService.kt, InitialOutreachServiceTest.kt,
  V109ExpertTypesMigrationTest.kt, BatchSendTaskRuntimeIntegrationTest.kt, ExpertClassificationService.kt,
  ExpertSearchService.kt — their `sendable` hits are account semantics / comments / constructors, NOT property reads;
  do NOT modify them.
  IMPORTANT: 11 of the 12 authorized files are ALREADY EDITED in the working tree (uncommitted) by a crashed implementer.
  Verify each edit before proceeding; do not redo them wholesale.

## Approved Contract

Exact approved child plan: `docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md`
(same directory in this worktree; read it in full first — it is the complete approved contract).
Master plan (context): `docs/plans/2026-08-28/00-single-gate-master.md`.

## Global Constraints (master plan)

- **M-3**: `ExpertClassificationService.classify()` 判定链、打分、阈值、`VERSION` 一行不改。
- 子计划 04 已删除所有 sendable 过滤用途；本计划清三处非过滤用途：序列化、回填统计、API DTO。
- `ManualInitialOutreachService` 中的 `sendable` 变量是**发件账号列表**语义，与专家分类无关，**不得触碰**。

## Authorized Files (exactly these 10; nothing else)

1. `src/main/kotlin/.../expert/domain/ExpertClassification.kt` — 删 `sendable` 派生属性 + `SENDABLE_TYPES` + 相关注释
2. `src/main/kotlin/.../expert/service/ExpertIndexWriterService.kt` — 删 `put("sendable", ...)` 一行
3. `src/main/kotlin/.../expert/service/ExpertClassificationBackfillService.kt` — 统计改按类型分项 `byType`
4. `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` — 删 DTO 字段 `expertSendable` 两处
5. `src/test/kotlin/.../expert/service/ExpertClassificationServiceTest.kt` — 删 sendable 断言，改断言 type
6. `src/test/kotlin/.../expert/service/ExpertIndexWriterServiceTest.kt` — 断言序列化不含 sendable 键
7. `src/test/kotlin/.../expert/service/ExpertClassificationBackfillServiceTest.kt` — 改 byType 分项断言
8. `src/test/kotlin/.../expert/controller/ExpertClassificationAdminControllerTest.kt` — 结果结构断言同步
9. `src/test/kotlin/.../expert/service/ExpertClassificationSchedulerTest.kt` — 结果结构断言同步
10. `src/test/kotlin/.../expert/service/ExpertClassificationVersionGateGuardTest.kt` — sendable 守卫白名单收窄为空集

**禁止**: 改 `ExpertSearchService.kt`（I5-4：解析不读 sendable，本计划不改此文件）；
改 `src/main/resources/es/*.json`（mapping 声明保留为孤儿字段，I5-3）；
动 `src/test/js/gateTemplateFilter.test.js`（发件账号语义）；任何迁移/清理存量脚本。

## Key Invariants (from plan; must hold after implementation)

- **I5-1**: `BackfillCounters` 删 `sendable`/`notSendable`，替换为 `byType: MutableMap<String, Long>`；进度消息与结果对象都输出分项计数。
- **I5-2**: `byType` 键从 `classification.type.name` 派生；不得手写六值名单字面量。
- **I5-3**: 只删 `ExpertIndexWriterService.kt:355` 的 `put("sendable", ...)`；不改 mapping、不清存量文档。
- **I5-4**: `ExpertSearchService.kt` 零改动（本计划不改此文件）。
- **I5-5（范围闸门）**: 执行前必须先跑 `grep -rln "sendable" src/main/kotlin src/test/kotlin`；
  命中文件（除 `gateTemplateFilter.test.js` 与 `ManualInitialOutreachService.kt` 的发件账号语义外）
  若多于本清单，**停止执行并返回 PLAN_CONFLICT**，不得就地扩大范围。
- **M-1 终局判据**: `ExpertClassificationVersionGateGuardTest` 的 sendable 守卫白名单 = **空集** 且用例通过。

## Downstream Interfaces (for finalization / human review)

- 05 之后全仓库不再有任何一处读取 `expertClassification.sendable`（守卫白名单空集）。
- 分类结果与发信人群必须零变化（A5-4/A5-5：只删表述，不动判定）。

## Required Commands (must run; JDK 11 zulu required)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
# 范围闸门（I5-5）
grep -rln "sendable" src/main/kotlin src/test/kotlin
# 相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertClassificationServiceTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,ExpertClassificationSchedulerTest,ExpertClassificationVersionGateGuardTest'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Full-suite `mvn test` regression required before READY_FOR_VERIFICATION
(baseline: after children 01-04, expect ~2980+ Kotlin + 755 JS green).

## Working Notes

- Plan line numbers may drift; **symbol/identifier names are authoritative**.
- Worktree contains untracked fast-p docs (`docs/plans/fast/`, `docs/runbooks/`) — never commit them; commit only the authorized files.
- This plan is at the file-count ceiling (10, per I5-5). Any additional file needed = PLAN_CONFLICT (amendment requires human approval).

## Deliverable

Implementation commit message: `feat(fast-p): implement child 05`.
Full result (files changed, commands + exit codes, test counts, deviations) to the execution report path.
