## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-09b-scope-facts.md`
Plan SHA-256: `a3252f2a5532edf05967d571a7bc3912749a9554d6105c0b753bdc052f6440eb`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-09b-scope-facts.md@a3252f2a5532edf05967d571a7bc3912749a9554d6105c0b753bdc052f6440eb`
Execution epoch: NEW
Approval basis: current child09b invocation and approved plan commit `152028fb4f6adf467a5254ed3627bf84c561f6bc`
Executor: ScopeFactImplementer
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`
Target branch: `fast/2026-09-26-discovery-repair-00-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master@fast/2026-09-26-discovery-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`
Pre-execution code SHA: `f9caa0b6fa5a1893aa37a6dd8612c8b930635eaf` (docs-only preparation HEAD `78ec3d68e514cb7699d1e5982eb46510e833a414`)
Post-execution code SHA: `3df3d79a1806081639ad318fb6b638c59f5bca85`
Evidence HEAD: N/A; this report remains separate from the product/test commit for the controller's evidence commit.
Implementation boundary: `78ec3d68e514cb7699d1e5982eb46510e833a414..3df3d79a1806081639ad318fb6b638c59f5bca85` (exactly nine authorized product/test paths; the tenth guard file needed no correction).

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 structured fact contract | IMPLEMENTED | `ExpertProfile.kt`, `ExpertSearchService.kt`, three ES mapping JSONs, `ExpertSearchServiceTest.kt` | Trailing nullable default; all three root `dynamic=false` mappings declare keyword; all sourceFields users share the explicit projection; actual three-layer mock ES readback checks missing/null/empty and `["17","22"]`, preserves real `_id`, never infers from free text. |
| I-2 six-field catalog | IMPLEMENTED | `SubjectScopeCatalog.kt`, `SubjectScopeCatalogTest.kt` | Read-only target set reuses the existing query ID list; six IDs and unchanged query fragment checked, mutation rejected. No university researcher filter was added. |
| I-3 mapping/bootstrap and unchanged writers | IMPLEMENTED | `ExpertIndexServiceTest.kt`, three ES mapping JSONs | Existing RAW/CANDIDATE/APPLICATION mapping PUTs carry keyword; newly created APPLICATION mapping carries keyword with dynamic=false; RAW conflict emits field-specific warning and other layers continue. Existing full-source promotion and production writer untouched. `OperatorStatusWriteSeamGuardTest` passes without any line adjustment. |
| Offline acceptance evidence | IMPLEMENTED | `ExpertSearchServiceTest.kt` | Isolated execution wrote `target/discovery-plan-acceptance/09b.json` with three layer mappings/projections, actual four-record readback per layer, target IDs and real `_id` values. No production ES writes. |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertSearchServiceTest,ExpertIndexServiceTest,SubjectScopeCatalogTest,OperatorStatusWriteSeamGuardTest` (intermediate edit/compile race) | FAIL, exit 1 | New catalog mutation assertion compiled while the catalog implementation was still old; 89 JUnit tests, 1 failure, 0 errors. Stale generated evidence included `99`; it was replaced by the fresh successful run. |
| Same exact JDK11 command after final edits (artifact `artifact://767`) | PASS, exit 0 | JUnit 89 (8+1+70+10), 0 failures/errors/skips. Maven-bound Node phase 1,194 pass, 0 fail/skipped; `node-check-app` and `node-check-task-modal-runtime` completed; BUILD SUCCESS. |
| `git diff --cached --check` before commit | PASS, exit 0 | No whitespace errors on staged authorized paths. |

### Changed Files
- `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` — one trailing defaulted fact list.
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` — explicit projection and safe array readback.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` — read-only six-field set and predicate based on existing IDs.
- `src/main/resources/es/orcid_info_raw.json`, `orcid_info_candidate.json`, `orcid_info_application.json` — same keyword declaration without changing dynamic setting.
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` — three-layer readback and generated JSON.
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` — existing/new index declarations and conflict reporting.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` — exact immutable set and unchanged query.

### Deviations
- None. `OperatorStatusWriteSeamGuardTest.kt` remained unchanged because the insertion follows its line-499 anchor. Existing modified ledger and concurrently appearing untracked child09c directory were preserved untouched and excluded from the product commit.

### Freshness
- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, exact root/branch/git-dir before staging and commit.
- Reported product commit reachable from target branch: YES, target branch HEAD.
- Required commands run this invocation: YES, successful fresh run after final edits.
- Historical evidence used only as baseline: YES.

### Remaining Blocker
- None.

### Next Action
- Independent verifier runs `verify-p`; controller separately commits this execution report as evidence.
