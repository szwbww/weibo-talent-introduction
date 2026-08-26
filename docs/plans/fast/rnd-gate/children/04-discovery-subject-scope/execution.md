# Child 04 Execution Report — discovery-subject-scope

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/2026-08-25/04-discovery-subject-scope.md`
- Plan SHA-256: `a36ebd77bc61b9a948c53bb633e1f99214aebb931bffc093a63c1468e91f51f9`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/2026-08-25/04-discovery-subject-scope.md@a36ebd77bc61b9a948c53bb633e1f99214aebb931bffc093a63c1468e91f51f9`
- Execution epoch: NEW
- Approval basis: current invocation (approved fast-p child brief + approved plan)
- Executor: Impl04SubjectScope
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate`
- Target branch: `fast/rnd-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate@fast/rnd-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-rnd-gate`
- Pre-execution code SHA: `b2fdf028d16b1669c9c3f481fb5b94abd77d4e60` (child 03 code head; pre-commit HEAD `dbfea3432e57da9f58561dc463141484ae0d95cd` was a controller docs-only ledger commit)
- Post-execution code SHA: `ee152d2b21030f6b86da16769f638b29d4be094b`
- Evidence HEAD: `ee152d2b21030f6b86da16769f638b29d4be094b` (single product commit; no separate evidence commit required by plan)
- Implementation boundary: `b2fdf028d16b1669c9c3f481fb5b94abd77d4e60..ee152d2b21030f6b86da16769f638b29d4be094b`

## Commit

- `ee152d2 feat(fast-p): implement 04-discovery-subject-scope` — 12 files changed, 386 insertions(+), 11 deletions(-)
- HEAD of `fast/rnd-gate`, reachable on the target branch. No `docs/plans/fast/` files, no logs, no target artifacts committed.

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 (I4-1) SubjectScopeCatalog | IMPLEMENTED | `discovery/domain/SubjectScopeCatalog.kt` (new) | `RND_TARGET`/`ALLOWED`/4 functions; field ids (取数日期 2026-08-25, CP-3) + arXiv official categories in comments; fragment built from single id list |
| Task 2 (I4-2) criteria field | IMPLEMENTED | `discovery/domain/PaperSearchCriteria.kt` | trailing `val subjectScope: String? = null`, last field with default |
| Task 3 (I4-1/I4-2) OpenAlex + arXiv | IMPLEMENTED | `service/OpenAlexDataSource.kt`, `service/ArxivDataSource.kt` | `parts += SubjectScopeCatalog.openAlexFilterParts(...)` after keywords branch; keywordQuery 3-branch (`all:*` fallback verbatim) |
| Task 4 (I4-3/I4-4) source enablement | IMPLEMENTED | `service/ExpertDiscoveryService.kt` | `EuropePmcProperties` appended last to constructor; `if (europePmcProperties.enabled) add(...)`; add-closure gains `&& name !in SubjectScopeCatalog.excludedSources(criteria.subjectScope)`; all six registration lines kept |
| Task 5 (I4-2/I4-5) entry enablement | IMPLEMENTED | `service/ExpertDiscoveryScheduler.kt`, `application.yml` | criteria gains `subjectScope = SubjectScopeCatalog.RND_TARGET`; yml only: OPENALEX 1200→2500, ARXIV 200→800, CROSSREF 500→300 |
| Task 6 research checkpoint | DONE (pre-execution, per plan) | — | values written verbatim into SubjectScopeCatalog comments |
| Task 7 tests | IMPLEMENTED | 5 test files (incl. new `SubjectScopeCatalogTest.kt`) | see Commands; per-class counts below |

## Commands

All run with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, cwd = worktree root.

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest='SubjectScopeCatalogTest,OpenAlexDataSourceTest,ArxivDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest'` | PASS | exit 0; Tests run: 103, Failures: 0, Errors: 0 (SubjectScopeCatalog 4, OpenAlex 22, Arxiv 10, ExpertDiscoveryService 59, ExpertDiscoveryScheduler 8) |
| `mvn test` | PASS | exit 0; Tests run: 2878, Failures: 0, Errors: 0 (full suite) |
| `mvn clean package` | PASS | exit 0; Tests run: 2878, Failures: 0, Errors: 0; `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` produced |
| `git diff --check` | PASS | exit 0, no output |

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` — NEW; single source of truth for subject scopes (I4-1)
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt` — trailing `subjectScope: String? = null` (I4-2)
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` — `buildFilter` appends `SubjectScopeCatalog.openAlexFilterParts(criteria.subjectScope)` after keywords branch
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt` — `keywordQuery` three-branch (keywords → cat:OR-join → `all:*`)
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — `EuropePmcProperties` injected (end of params); I4-4 `if (europePmcProperties.enabled) add(...)`; I4-3 scope-exclusion in add closure
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` — criteria `subjectScope = SubjectScopeCatalog.RND_TARGET`
- `src/main/resources/application.yml` — OPENALEX_MAX_PAPERS 1200→2500, ARXIV_MAX_PAPERS 200→800, CROSSREF_MAX_PAPERS 500→300 (only these three)
- `src/test/kotlin/.../service/OpenAlexDataSourceTest.kt` — +2 tests (null-scope byte-identical anchor; RND_TARGET fragment appended, hard-coded literals)
- `src/test/kotlin/.../service/ArxivDataSourceTest.kt` — +4 tests (all:* fallback, cat OR-join, keywords priority, scope-ignored-with-keywords)
- `src/test/kotlin/.../service/ExpertDiscoveryServiceTest.kt` — +4 tests (I4-4 disabled+empty sources; I4-2 six sources; I4-3 RND_TARGET exclusions; scope-overrides-manual)
- `src/test/kotlin/.../service/ExpertDiscoverySchedulerTest.kt` — +1 test (criteria subjectScope == RND_TARGET)
- `src/test/kotlin/.../discovery/domain/SubjectScopeCatalogTest.kt` — NEW; +4 tests (null/unknown empty, excludedSources exactly 2, fragment anchor, ALLOWED branch coverage)

## Acceptance Evidence

- I4-1: `grep -rn "primary_topic.field.id"` and quoted `"cs"/"eess"/"cond-mat"/"physics"` and fixed-string `listOf("22", "31", "17", "25", "21", "15")` over `src/main` hit ONLY `SubjectScopeCatalog.kt` (note: tests intentionally contain hard-coded anchor literals per I4-2 — grep must be scoped to src/main).
- I4-2: OpenAlex/Arxiv null-scope anchor assertions pass with hard-coded expected query strings (not computed from code under test).
- I4-3: `ExpertDiscoveryService.kt:217-222` — all six `add(...)` registration lines present; test `resolveEnabledSources scope exclusion overrides manual sources selection` passes.
- I4-4: test `resolveEnabledSources excludes EUROPE_PMC when disabled even with empty sources` passes (regression for the pre-fix defect).
- I4-5: `git diff` shows only `application.yml` changed; `ArxivProperties.kt` / `OpenAlexProperties.kt` / `CrossrefProperties.kt` untouched.
- Regression: full `mvn test` exit 0 (2878/0/0).

## Deviations

1. `worktree_identity.py` helper could not execute: the common git dir (`/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`) registers two stale worktree entries under `/sessions/...` that do not exist on this machine, and the script `resolve(strict=True)`s every listed worktree. Identity was computed manually with the same logic and verified (`root`/`branch`/`git_dir`) before staging and after commit. Environment artifact only; identity unambiguous.
2. `stubSource` in ExpertDiscoveryServiceTest does not stub `searchPapers`: `Mockito.any(Class)` returns null and NPEs on the Kotlin non-null parameter (repo convention avoids this with `?: PaperSearchCriteria()`; a stub would have needed that fallback too). An unstubbed mock returns null → `discoverFromSource` exits via `batch == null` while still creating the `bySource` entry, so the six-source assertions remain exact.
3. Scheduler verify uses the repo's null-fallback pattern `captor.capture() ?: PaperSearchCriteria()` (same Kotlin non-null matcher issue).
4. `coreKeywords(RND_TARGET)` returns a non-empty theme-word list (engineering/materials/computer science/chemical/energy/physics), declared but deliberately NOT wired into CORE (plan Task 3 no-wire, comment in catalog explains AND-join rationale); non-empty is required by the ALLOWED branch-coverage invariant (plan Task 7).
5. Brief noted current HEAD `b2fdf02`; actual HEAD was `dbfea34` (controller `docs(fast-p): advance ledger to 04-discovery-subject-scope`). Product code base `b2fdf02` unchanged. Two controller-owned `docs/plans/.../brief.md` modifications existed pre-execution and were left unstaged/uncommitted.

## Freshness

- Plan identity rechecked: YES (unchanged — plan file not touched)
- Worktree identity rechecked: YES (before staging and after commit)
- Reported commits reachable from target branch: YES (`ee152d2` is HEAD of `fast/rnd-gate`)
- Required commands run this invocation: YES (all four)
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` against plan identity `a36ebd77…` and child base `b2fdf02`.
