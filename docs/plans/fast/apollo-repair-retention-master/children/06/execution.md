# Child 06 — Execution log

## Execution Result: PLAN_CONFLICT

### Identity and approval

- Executor: ApolloRetention06; execution epoch: NEW; date: 2026-10-21.
- Approval basis: current child06 invocation, exact approved brief, and prior child05 LIGHT_PASS_WITH_NOTES / VALID checkpoint. No plan amendment authorized.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md`
- Plan SHA-256: `908af3147608b328ab9d90b755b6b97e4cad369fd324cbb3be6d0b089779e48b`
- Execution ID: the canonical plan path above followed by `@908af3147608b328ab9d90b755b6b97e4cad369fd324cbb3be6d0b089779e48b`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`
- Target branch: `fast/apollo-repair-retention-master`
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
- Worktree ID: canonical target root + `@fast/apollo-repair-retention-master@` + worktree Git directory above.
- Pre-execution code SHA / child_base_sha: `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`.
- Pre-execution HEAD / current HEAD: `626a9ecbeb3ee8d8fcf3a9406838f9749d6a3729` (`docs(fast-p): checkpoint 05`).
- Post-execution code SHA: unchanged, `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`; no child06 product commit.
- Evidence HEAD at handoff: `626a9ecbeb3ee8d8fcf3a9406838f9749d6a3729`; this report is left for the controller-owned evidence commit.
- Implementation boundary: no child06 implementation changes.
- Initial working-tree status: ` M docs/plans/fast/apollo-repair-retention-master/ledger.md`; controller-owned change preserved.

### Preflight and authoritative scope

Read the complete child plan, complete master, complete code receipts, and complete normative E-6/E-7. Read execute-p and performed its plan/worktree identity gates before source inspection. The child plan authorizes only:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt`
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`

### Conflict C-06-01 — required dependency migration reaches an unauthorized caller

Plan implementation step 2 requires injecting the verification repository into `DiscoveryPromotionProgressService`. The existing service constructor takes `jobs`, `executions`, `logs`, `restTemplate`, `properties`, and `mapper` (`DiscoveryPromotionProgressService.kt:25-32`). An existing direct constructor call lies outside the authorized file list.

Exact repository-search tool command (run twice with the same result):

```text
functions.grep({
  "i": "Recording exact constructor callers",
  "pattern": "DiscoveryPromotionProgressService\\(",
  "path": "/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/src",
  "case": true,
  "gitignore": true,
  "skip": null
})
```

Matched lines:

```text
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt:25:
class DiscoveryPromotionProgressService(

src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt:32:
    private val service = DiscoveryPromotionProgressService(jobs, executions, logs, rest,

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt:464:
        return DiscoveryPromotionProgressService(jobs, executions, logs, template, esProperties, objectMapper)
```

The unlisted caller was read at `ExpertDiscoveryServiceTest.kt:433-465`: `promotionProjection()` builds the real service with mocked job/execution/log reads and a `MockRestServiceServer` ES response. It is not a string-only assertion. The caller's own comment says its tests exercise the real read projection while preserving summary waiting text.

[INFERENCE] Adding a required constructor argument without migrating this caller would fail Kotlin test compilation; no build was run to manufacture or reconfirm that failure. Migrating it requires editing an unauthorized test file. A nullable/default repository, old-constructor compatibility overload, or setter-based legacy bypass would sidestep required dependency migration rather than perform the clean cutover. None was introduced.

Controller decision received during preflight: “Confirmed fast-p requires PAUSE for unauthorized caller migration; do not add setter/default shim. Finish PLAN_CONFLICT report with exact grep command/output and constructor/caller evidence; no product edits. Record all reachable preflight facts, current HEAD and authorized-file mismatch. Controller will persist pause; no plan amendment authorized.”

### Task status

| Requirement | Status | Evidence |
|---|---|---|
| I-1 / managed ownership and projected repository query | CONFLICT | Implementation paused before edits because its service consumer needs an unauthorized dependency migration. |
| I-2 / legacy-managed doc union and source precedence | CONFLICT | Required verification repository injection reaches `ExpertDiscoveryServiceTest.kt:464`, outside whitelist. |
| I-2 / historical aggregate lower bound | PENDING | Existing max merge inspected; no changes or compliance claim. |
| I-3 / read-only refresh parity and dependency failure behavior | PENDING | Existing refresh overloads and safe fallback inspected; new behavior not implemented or exercised. |
| Service tests, repository IT, existing controller integration and real changed-path smoke | PENDING | Not run after controller's pre-implementation PAUSE decision. |

### Commands and environment evidence

Every shell command below ran with cwd set to the exact target worktree.

| Command | Result / evidence |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md` | Exit 0 at start and handoff preflight; canonical path above, SHA-256 unchanged, 8699 bytes. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master` | Exit 0; branch/root/Git directories and HEAD recorded above. |
| `git status --short` | Exit 0; initial controller-owned ledger modification only. |
| `export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock; export JAVA_TOOL_OPTIONS=-Dapi.version=1.44; java -version && docker version && git log -3 --format="%H %s"` | Exit 0; Zulu OpenJDK 11.0.32.1, Docker client/server 29.8.2 on arm64, server minimum API 1.40. `JAVA_TOOL_OPTIONS` was picked up. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master --expect-branch fast/apollo-repair-retention-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master` | Exit 0 on final identity recheck; current HEAD still `626a9ecbeb3ee8d8fcf3a9406838f9749d6a3729`. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test` | NOT RUN — pre-implementation PLAN_CONFLICT / controller PAUSE. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test` | NOT RUN — pre-implementation PLAN_CONFLICT / controller PAUSE; no stale reports used. |
| Changed-path real smoke; existing controller tests | NOT RUN — no changed runtime path, implementation paused. |
| `feat(fast-p): implement 06` staging/commit | NOT RUN — no product edits to commit. |

Recent commits observed:

```text
626a9ecbeb3ee8d8fcf3a9406838f9749d6a3729 docs(fast-p): checkpoint 05
5d2d7ed21c64114562cad0f376ba33a72b2e976f docs(fast-p): record 05 light verification
a489c28e93e0357e26f6e5e85ab8b49962ed0b5c feat(fast-p): implement 05
```

### Changed files, boundaries and limitations

- Product/source/test changed files: none.
- Workflow artifact changed: this `children/06/execution.md`, appended once for controller handoff.
- No staging, commit, plan amendment, schema/mapping change, deployment, production mutation, production readback, or history rewrite.
- No build, test, lint, formatter, smoke, deferred master final, or whole-system suite run.
- O-04-01 remains RECORD_ONLY, unchanged: out-of-whitelist `DiscoveryVerificationIntegrationTest` expectations for new payload ruleVersion1 remain the controller's broader regression observation; not investigated or repaired.
- Child05 LIGHT_PASS_WITH_NOTES and its clean checkpoint are historical baseline, not evidence that child06 is implemented.
- No new downstream interface delivered.
- Human acceptance A-1/A-2/A-3 remains PENDING; no production 27254 count or new promotion benefit claimed.

### Freshness and next action

- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, unchanged.
- New product/evidence commits reported: none.
- Required test commands run this invocation: NO; pre-implementation conflict prevents READY_FOR_VERIFICATION.
- Historical evidence used only as baseline: YES.
- Remaining authority gap: caller migration in `ExpertDiscoveryServiceTest.kt` is outside the immutable child06 four-file whitelist.
- Next action: controller persists PAUSE / PLAN_CONFLICT. A human-authorized scope decision is required before implementation can resume; this execution did not amend the plan or add a compatibility shim.

---

## Epoch 2 — Execution Result: READY_FOR_VERIFICATION

### Identity and approval

- Executor: ApolloRetention06E2; date: 2026-10-21.
- Execution epoch: NEW for the amended immutable plan bytes; operationally the approved A1 resume. Epoch1 above is historical and unchanged.
- Approval basis: current invocation, brief's Epoch2 superseding section, HUMAN「批准 继续」A1, governing plan `commit:c337ee2aec8a7fc5f36584afeb0d00464ca5103e`.
- Plan canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md`.
- Plan SHA-256: `bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999` (9209 bytes).
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md@bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`.
- Target branch: `fast/apollo-repair-retention-master`.
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master@fast/apollo-repair-retention-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`.
- Pre-execution product code SHA: `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`; pre-execution HEAD: `59a0668aee3713dec171256e21565c71b07d36a3`.
- Post-execution code SHA / current HEAD: `462c52385bfe3b248016829a08a5b6ceb2403bb4`, subject `feat(fast-p): implement 06`.
- Evidence HEAD: `462c52385bfe3b248016829a08a5b6ceb2403bb4`; this appended report remains outside the product commit for controller-owned evidence handling.
- Product implementation boundary: `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4`; this invocation's commit delta is `59a0668aee3713dec171256e21565c71b07d36a3..462c52385bfe3b248016829a08a5b6ceb2403bb4`.
- Initial status was controller-owned ` M docs/plans/fast/apollo-repair-retention-master/ledger.md`. It was preserved and excluded from staging. No prior product work replayed.

### Preflight and scope

Read execute-p, the complete current child plan, complete master, complete code receipts, complete normative E-6/E-7, and the superseding brief before implementation. The plan identity helper resolved the exact target path; final `git show c337ee2aec8a7fc5f36584afeb0d00464ca5103e:docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md | shasum -a 256` also returned the same hash above. The target worktree helper passed at start, before each staging/commit action, and at handoff.

C-06-01 was addressed only through A1: `ExpertDiscoveryServiceTest.kt` gained the verification repository import, necessary empty-ledger mock/stub, and required constructor argument. Its existing assertions were not changed. No compatibility overload/default repository/shim was introduced.

### Task status and implementation evidence

| Requirement | Status | Files / evidence |
|---|---|---|
| I-1 / managed ownership and actual projection events | IMPLEMENTED | `DiscoveryVerificationRepository.reportingPromotionDocuments(executionId)` uses `idx_dvi_run_batch`, joins the owning run, restricts owner/ENFORCE/activation, and returns managed doc IDs plus only non-null-revision PROJECTED doc-to-source entries from the same SELECT snapshot. Descending item ID chooses the latest qualifying source deterministically. No finishedAt/projection-time filter. |
| I-2 / legacy-managed document union | IMPLEMENTED | `DiscoveryPromotionProgressService.counts` excludes all this-run managed IDs from the legacy candidate/application lookup, preserves legacy tags/discoveredAt window behavior for unmanaged IDs, then unions ledger projected sources by doc ID. Ledger source wins without source-group max/add shortcuts. |
| I-2 / stored aggregate lower bound | IMPLEMENTED | Existing `max(recorded source.promoted, verified union count)` is retained; inline comment identifies `LEGACY_AGGREGATE_FLOOR`. The dedicated test has verified union 1, recorded floor 5, displayed 5: difference 4 is legacy anonymous aggregate compatibility, not ledger-proven extra documents. |
| I-3 / read-only refresh parity and failure handling | IMPLEMENTED | Both refresh overloads share counts; no writer dependencies/calls were added. Service tests preserve original snapshots on ledger/ES dependency failures. MySQL-backed HTTP smoke verifies repeat refreshes leave task/admission/run/item/attempt/job snapshots unchanged and JdbcTemplate write-call count zero. |
| Required service/repository cases | IMPLEMENTED | Fresh service suite and reporting MySQL suite passed. Cases include old job ownership, post-finish projection, overlap/source precedence, disjoint sources, excluded projection states, revision/activation/mode/owner filtering, repeated reads, and dependency failures. |
| A1 constructor migration / existing controller boundary | IMPLEMENTED | Fresh `ExpertDiscoveryServiceTest`, `TaskProgressControllerTest`, and `TaskExecutionControllerTest` passed with their assertions retained. |
| Human acceptance A-1/A-2/A-3 | PENDING | Not performed; no production task27254 readback or deployment was authorized in this execution. |

### Actual changed-path runtime smoke

Executed inside the required `DiscoveryVerificationReportingIT` command, not a source-text-only check:

`changed path HTTP smoke reads real MySQL ledger plus legacy ES without writes or summary mutation`

- Real disposable Docker MySQL 8.0.36, real repository query, real enrichment/task/log repositories, real promotion service, and real existing task controllers dispatched through MockMvc. ES is an existing `MockRestServiceServer` fixture, not a live cluster; the progress slot is a mocked store. No new production endpoint or mock service was created.
- Fixture's collection interval is 10:00–10:20. PROJECTED and ALREADY_PRESENT ledger events are persisted at 12:00. Automatic-admission SQL count is 2; managed new-promotion contribution is 1.
- The projected expert's unique enrichment job belongs to older execution6000; this run is6101. A separate unmanaged legacy expert is CROSSREF. Repeated GETs return total promotions2, split OPENALEX1 and CROSSREF1, on both `/api/task-progress/EXPERT_DISCOVERY` and `/api/task-executions/6101/detail`.
- The ALREADY_PRESENT expert is not looked up through legacy ES; only the independent legacy expert is present in `_mget`. Repeated reads issue only the expected ES `_mget` POSTs and no ES mutation requests.
- Before/after snapshots of task execution, admission, verification run/item/attempt, and enrichment jobs are identical. JdbcTemplate update/batchUpdate/execute invocations are absent during reads; stored resultSummary and the original progress snapshot remain unchanged.
- Fresh XML receipt: `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationReportingIT.xml` has the smoke testcase at line76 (1.1 seconds) and ownership-query testcase at line77 (0.534 seconds), no failure/error child; suite tests12/errors0/skipped0/failures0 at line2.

### Environment and command evidence

All shell commands used the exact target worktree as cwd. Test commands used:

```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
export JAVA_TOOL_OPTIONS=-Dapi.version=1.44
```

`java -version && docker version` exited0: Zulu OpenJDK11.0.32.1; Docker client/server29.8.2 arm64, server minimum API1.40. JVM output confirmed JAVA_TOOL_OPTIONS was picked up. The repository IT explicitly required Docker and actually started its disposable MySQL; it did not skip for missing infrastructure.

| Command | Result / fresh evidence |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md` | Exit0 at preflight, before staging, and handoff. Canonical path/hash/size above unchanged. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master` | Exit0 at preflight; root/branch/Git directory matched. Before `git add`, before `git commit`, and at final gate the same command additionally used `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master --expect-branch fast/apollo-repair-retention-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`; each exited0. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test` | Initial exit0, tests12/failures0/errors0/skipped0, finished16:12:02+08. Fresh final-state rerun exit0, tests12/failures0/errors0/skipped0, finished16:19:18+08. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test` | Initial exit1, tests12/failures0/errors1/skipped0, finished16:15:49+08; fixture-only issue explained below. Fresh final-state rerun exit0, tests12/failures0/errors0/skipped0, finished16:23:08+08; includes actual changed-path HTTP smoke. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ExpertDiscoveryServiceTest,TaskProgressControllerTest,TaskExecutionControllerTest test` | Exit0, tests217/failures0/errors0/skipped0, finished16:26:46+08. Class receipts: ExpertDiscoveryServiceTest193; TaskProgressControllerTest13; TaskExecutionControllerTest11; all zero failures/errors/skips. |
| `git diff --check` | Exit0 before staging and at final handoff. |
| `git diff --stat` and targeted `git diff -- <repository service caller-test paths>` | Exit0; inspected product changes and retained controller-owned ledger separately. Product scope matches the authorized paths below; caller assertions unchanged. |
| `git add <each authorized product path below>` followed by `git diff --cached --name-only` and `git commit -m 'feat(fast-p): implement 06'` | Exit0; cached-path output was exactly the product list below. Commit `462c52385bfe3b248016829a08a5b6ceb2403bb4`; no fast-p report/ledger was staged. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor 462c52385bfe3b248016829a08a5b6ceb2403bb4 fast/apollo-repair-retention-master` | Exit0; the committed SHA is current HEAD of the target branch, not merely an object in the shared Git database. |
| `git status --short` at post-commit handoff gate | Exit0; only the pre-existing controller-owned ledger was modified before this report append. Index/product source were clean. This execution report is now additionally modified by the authorized append. |

Fresh surefire text-report mtimes, read from the current target worktree with `stat -f '%Sm %N' -t '%Y-%m-%dT%H:%M:%S%z' target/surefire-reports/*DiscoveryPromotionProgressServiceTest.txt target/surefire-reports/*DiscoveryVerificationReportingIT.txt target/surefire-reports/*ExpertDiscoveryServiceTest.txt target/surefire-reports/*TaskProgressControllerTest.txt target/surefire-reports/*TaskExecutionControllerTest.txt` (exit0):

```text
2026-10-21T16:19:13+0800 DiscoveryPromotionProgressServiceTest.txt
2026-10-21T16:23:03+0800 DiscoveryVerificationReportingIT.txt
2026-10-21T16:26:39+0800 ExpertDiscoveryServiceTest.txt
2026-10-21T16:26:41+0800 TaskProgressControllerTest.txt
2026-10-21T16:26:39+0800 TaskExecutionControllerTest.txt
```

Raw tool-output artifacts: initial service `artifact://196`; initial reporting failure `artifact://198`; final sequential command output `artifact://201`; scope/mtime gate `artifact://203`; staging/commit `artifact://204`; final identity gate `artifact://205`. The per-class surefire text/XML reports above are the repository-local receipts.

### Bounded mechanical test-fixture correction

The initial MySQL run reached the new fixture's deliberately inconsistent PROJECTED-without-revision row, then the fixture helper unnecessarily reread it as a `VerificationItem`. Domain invariants rejected that reread (`DiscoveryVerificationReportingIT.promotionItem:106`, called from the no-revision case at117). This was not a repository-query failure.

Mechanical correction attempt1, restricted to the authorized reporting IT: make the write-only fixture helper return Unit and remove its unused domain reread. All deliberately inconsistent persisted filter fixtures, query expectations, and production invariants were retained. No production logic, unrelated test, class, state, or interface was added during this correction. Both required exact commands then ran freshly against final code and passed, followed by the migrated caller/controller regressions. No stale passing report substituted for the failed run.

### Changed product files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — one read-only managed ownership/projection query and its nested result shape.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt` — required repository injection, doc-level union/exclusion, aggregate-floor explanation.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressServiceTest.kt` — service behavior, read parity, dependency failures, floor and dedup regression coverage.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt` — SQL filter/dedup coverage and real-MySQL changed-path HTTP smoke.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — A1-only constructor migration and necessary repository mock, original assertions preserved.

Workflow artifact: this existing `children/06/execution.md` append only. No other product docs were created; inline comments document the aggregate compatibility boundary.

### Downstream interfaces and limits

- New read API: `DiscoveryVerificationRepository.reportingPromotionDocuments(Long): PromotionDocuments`, with `managedDocIds: Set<String>` and `projectedSources: Map<String,String>`. Missing managed run, AUDIT run, or no activated managed items produces empty collections. Projection count semantics remain distinct from automatic admission.
- `DiscoveryPromotionProgressService` requires the repository constructor dependency. Existing controllers keep their interfaces; service refresh remains a read-only returned-copy projection.
- Existing query/worker/commit/admission policy behavior, SQL schemas, ES mappings/documents, task writers, resultSummary persistence, frontend, Apollo clients, and production sample storage were not changed.
- No live Apollo/ES request, production SQL read/write, deployment, push, merge, history rewrite, plan edit, or master/whole-system final suite was performed.
- O-04-01 remains RECORD_ONLY: no unrelated `DiscoveryVerificationIntegrationTest` investigation, expectation rewrite, or repair.
- Offline/unit/isolated-MySQL evidence proves the changed counting path, not a production203-person outcome. Task27254's current ledger/API reconciliation and human A-1/A-2/A-3 remain PENDING. No new admission benefit or recall claim is made.
- Historical anonymous aggregate floors can exceed the verified set; the explicit 5-vs-1 test is reported as `LEGACY_AGGREGATE_FLOOR`, not as extra ledger events.

### Freshness and next action

- Plan identity rechecked: YES; exact approved-commit bytes and on-disk hash agree.
- Worktree/root/branch/Git-directory identity rechecked: YES.
- Product commit is target HEAD and reachable from target branch: YES.
- Required exact commands run freshly after final implementation state: YES.
- Changed-path runtime smoke exercised: YES, within the real-MySQL reporting IT.
- Historical evidence used only as baseline: YES.
- Product/index clean at handoff gate: YES; only controller-owned ledger plus this subsequent authorized report append are outside the product commit.
- Remaining implementation blocker: none.
- Next action: controller runs independent `verify-p` / prescribed light verification. READY_FOR_VERIFICATION is execution evidence, not independent PASS or final human acceptance.
