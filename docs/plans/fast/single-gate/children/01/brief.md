# Child 01 Brief — lastPublicationYear 补齐并重新分类

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA: 1f5a916489933fc9b2e8e469037fc912d55edd5d
- Execution report: docs/plans/fast/single-gate/children/01/execution.md

## Approved Contract

Exact approved child plan: `docs/plans/2026-08-28/01-lastpublicationyear-recovery.md`
(same directory in this worktree; read it in full first — it is the complete approved contract).
Master plan (context): `docs/plans/2026-08-28/00-single-gate-master.md`.

## Global Constraints (master plan)

- **M-3**: `ExpertClassificationService` 的 `classify()` 判定链、`productionScore()`、
  `researchScore()`、任何阈值/词表常量、`VERSION`（`rnd-v2-2026`）——一行不改。
  唯一允许的 `ExpertClassificationService` 改动属于子计划 03（删 `ACCEPTED_CLASSIFICATION_VERSIONS`），
  本子计划对 `ExpertClassificationService.kt` 零改动。
- **M-4**: 禁止修改 `ExpertDiscoveryService.kt:800/871` 的 `minusDays(30)` 常量或参数化。
- 本子计划零门禁改动（门禁属子计划 02/03/04）。

## Authorized Files (exactly these 8; nothing else)

1. `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` — 解析 `counts_by_year`；`AuthorEnrichment` 尾部加字段（I1-7 范式）
2. `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` — 写入 doc；新增补采过滤器与枚举值；`EnrichmentStats` 加字段
3. `src/main/resources/static/index.html` — 按 S1-1 插入**一行**
4. `src/main/resources/static/app.js` — `handleDiscoverOption` 新增一支
5. `src/test/resources/openalex/author-response-sample.json` — fixture 加 `counts_by_year`
6. `src/test/kotlin/.../discovery/service/OpenAlexDataSourceTest.kt` — Task 5 第 1~4 条
7. `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` — Task 5 第 5~7 条
8. `src/test/kotlin/.../discovery/controller/ExpertDiscoveryControllerTest.kt` — 新枚举值 scope 透传断言

`src/main/resources/static/styles.css` 必须零改动。`src/main/kotlin/.../expert/service/ExpertClassificationService.kt` 必须零改动。

## Key Invariants (from plan; must hold after implementation)

- **I1-1/I1-2**: `lastPublicationYear` = `counts_by_year` 中 `works_count > 0` 的最大 year（数组顺序不可依赖）。
- **I1-3**: 无键/空数组/全零 → 派生值 null，`updateExpertAcademicFields` 的 doc **不写入**该键。
- **I1-4**: 非 null 时无条件覆盖（`?.let` 同款，无「仅当原值为空」守卫）。
- **I1-5**: 补采口径 = `must: exists enrichedAt` + `must_not: [exists lastPublicationYear, prefix orcidId "EMAIL-"]`。
- **I1-6**: `buildEnrichmentFilters(cutoff)` 与 `when` 既有两支逐字不改。
- **I1-7**: `AuthorEnrichment` 新字段在最后位置带 `= null`。
- **I1-8**: 不改 VERSION；重算靠运维 `onlyPending:false`（Task 6 属运维，**不归你执行**）。
- **S1-1**: `index.html` 只有一行新增，与计划 S1-1 代码块逐字一致；`styles.css` 零改动。

## Downstream Interfaces (for later children)

- 子计划 02/03/04 不依赖本计划的产物。本计划不得改动
  `ExpertClassificationService`、`expertTypesFilter`/`expertTypePredicate`、门禁相关代码。
- `EnrichmentScope` 新增枚举值只增不改（控制器已透传 `@RequestParam scope`，无需改控制器）。

## Required Commands (must run; JDK 11 zulu required)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest
node --check src/main/resources/static/app.js
git diff --check
```

Full-suite regression (`mvn test` without `-Dtest`) is required before returning READY_FOR_VERIFICATION
(baseline at de228e1: 2952 Kotlin tests green, 755 JS tests green — any new failure is yours to fix in authorized files).

## Working Notes

- Plan line numbers may drift; **symbol/identifier names are authoritative** for locating change points.
- The worktree contains untracked fast-p docs (`docs/plans/fast/`, `docs/runbooks/institutiontype-backfill-run.md`) — never include them in your commit. Commit **only the authorized files** listed above.
- Do not touch `src/main/resources/es/*.json` (mapping), migrations, or any file outside the authorized list.
- Task 6 (运维三步) is out of scope for you; do not attempt it.

## Deliverable

Implementation commit message: `feat(fast-p): implement child 01`.
Full result (files changed, commands + exit codes, test counts, deviations) to the execution report path.
