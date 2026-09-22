# Fast-P Ledger — master: docs/plans/2026-09-22/openalex-daily-budget-design.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-22/openalex-daily-budget-design.md (commit ee1dfcd5439de54475c12ff51c9c713e984a82ec)
- Amendments: N/A
- Master base: e2247680592603b091af791ef3629d70739a015b
- Branch: fast/2026-09-22-openalex-daily-budget-design
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-22T12:28:00Z
- Current child: c1
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- Approval basis: explicit `$fast-p docs/plans/2026-09-22/openalex-daily-budget-design.md` invocation (2026-09-22), which authorizes one worktree, one local branch, and local commits for this run. The master plan and its three child plans were untracked on `main` at run start.
- MASTER_BASE_SHA `e2247680592603b091af791ef3629d70739a015b` = `main` HEAD at run start; branch `fast/2026-09-22-openalex-daily-budget-design` created there in a dedicated worktree.
- Plans were seeded on the branch as plan-only commit `ee1dfcd5439de54475c12ff51c9c713e984a82ec` (`docs/plans/2026-09-22/openalex-daily-budget-design.md`, `01-openalex-account-budget.md`, `02-discovery-paper-queue.md`, `03-discovery-continuous-run.md`); seeding is not an amendment. Master and all three child plan identities = `commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec`.
- Child order and dependencies follow the master plan's change table: c1 none; c2 c1; c3 c1,c2. Execution is serial (one writer at a time; `RestTemplateConfig.kt` is shared by c1 and c2, `ExpertDiscoveryService.kt` and the discovery entry points are shared by c2 and c3).
- Baseline commands at seed commit `ee1dfcd5439de54475c12ff51c9c713e984a82ec`: JS `node --test src/test/js/*.test.js` exit 0 (`tests 1037, pass 1037, fail 0`, duration 3599.8ms); Java targeted baseline (see `## Baseline Commands`) recorded below.
- `main` carried uncommitted work-in-progress at run start (a separate `task-activity-center` feature: untracked `TaskActivityController.kt`, `taskActivityCenter.test.js`, modified `ExpertDiscoveryController.kt` / `app.js` / `index.html` / `styles.css`). None of it is committed, authorized, or present in this worktree. Child plan 03's audit snapshot ("scope 已有 RND_TARGET 修复", "index.html 当前 11 个版本化资源均 `20260922-task-activity-center`") describes that uncommitted state and is therefore stale on this base; the child plans themselves require re-checking before implementation.
- Environment: JDK zulu-11 present; Docker (OrbStack, server 29.4.0) available for Testcontainers MySQL ITs; highest Flyway migration at base is `V131__create_expert_academic_enrichment_job.sql` (child plans expect V132/V133).

## Baseline Commands

| Command | Exit | Result |
|---|---|---|
| `node --test src/test/js/*.test.js` | 0 | tests 1037, pass 1037, fail 0 |
| `JAVA_HOME=<zulu-11> mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,RestTemplateConfigTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test` | 0 | Tests run: 277, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=<zulu-11> mvn -B -Dtest=ExpertAcademicEnrichmentJobRepositoryIT -DmigrationIt=true -Dapi.version=1.40 test` | 0 | Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 — Testcontainers MySQL 8.0.36 |

Full command transcripts and the two failing Docker-environment variants are in `baseline-java.txt`. Every MySQL IT in this run requires `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` and `-Dapi.version=1.40`; without them testcontainers reports a pre-existing environment failure, not a child regression.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| c1 | docs/plans/2026-09-22/01-openalex-account-budget.md | commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec | none | 1 | LIGHT_PASS_WITH_NOTES | e2247680592603b091af791ef3629d70739a015b | 2dd074ad28ee9eeb4a350beba0be85a872ef2c12 | 1 | b96470e4be86408b185c2fbdd2fb0037e8f4fc2c | b96470e4be86408b185c2fbdd2fb0037e8f4fc2c | 6aa09e3de91b705dfb3e423bff71d24d37e4b080 | Implementer C1Implementer; verifier C1Verifier; fixer C1Fixer; re-verifier C1Reverifier. Verifier verdict LIGHT_PASS_WITH_NOTES with AUTO_FIX N/A, but its note O-1 (negative RATE_LIMIT wait reaches Thread.sleep and throws instead of returning Deferred) is a proven violation of plan I-4 with a uniquely determined repair inside an authorized file, so the controller routed it as fix round 1; re-verification confirmed F-1 FIXED. Notes O-2/O-3/O-4 carried forward. |
| c2 | docs/plans/2026-09-22/02-discovery-paper-queue.md | commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec | c1 | 1 | LIGHT_VERIFYING | b96470e4be86408b185c2fbdd2fb0037e8f4fc2c | 58b96c7b2f66aee703eede6c283ce807ea9d16fe | 1 | ef1eb2b8c1e17aabaf04178f5f7931852872a42b | ef1eb2b8c1e17aabaf04178f5f7931852872a42b | — | Implementer C2Implementer; verifier C2Verifier (LIGHT_FAIL, AUTO_FIX F-1: streams keyed on the pipeline-level hash instead of the per-source hash → I-1); fixer C2Fixer; re-verifier C2Reverifier. Declared deviations 1-4 all judged CONFORMANT. |
| c3 | docs/plans/2026-09-22/03-discovery-continuous-run.md | commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec | c1,c2 | 1 | PENDING | — | — | 0 | — | — | — | Entry points, scheduler, frontend; audit snapshot stale on this base (see Baseline) |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
