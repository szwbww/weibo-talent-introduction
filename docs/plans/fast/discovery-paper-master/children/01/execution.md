# Child 01 execution log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-01-storage.md`

Plan SHA-256: `6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-01-storage.md@6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3`

Execution epoch: NEW

Approval basis: explicit fast-p invocation and child 01 brief; one local implementation commit authorized.

Executor: `PaperStorageImplementer`, using execute-p. Independent verification remains the controller's responsibility.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`

Target branch: `fast/discovery-paper-master`

Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`

Pre-execution product base: `c18dbc060dcf4c8440f853ede4d1d3aaf737994f`

Pre-execution HEAD: `4d88186f46d127fab14477deaf4f23fb83603180` (intervening commit contains fast-p governance files, not product changes).

Post-execution code SHA / Evidence HEAD: `90dec47f8604886df890c20e9e31dd394c20173d`

Implementation commit: `feat(fast-p): implement 01`

Implementation boundary: `4d88186f46d127fab14477deaf4f23fb83603180..90dec47f8604886df890c20e9e31dd394c20173d`

This execution report is intentionally not in the implementation commit. The controller-owned, already-dirty `docs/plans/fast/discovery-paper-master/ledger.md` was preserved and not staged.

### Task status and implementation evidence

| Requirement | Status | Files / evidence |
|---|---|---|
| T-1 schema and protocol | IMPLEMENTED | V159 creates the singleton setting, record, blob and attempt tables with binary comparison, UTC DATETIME(3), identity/index/enum constraints; Models contains the fixed API field names and separate persisted/derived states. Real MySQL migration, CAS, rollback, binary-key and millisecond-time assertions executed. |
| T-2 storage and recovery | IMPLEMENTED | Properties/Repository/Service provide persistent settings, beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction and analysis CAS seams, raw-byte SHA256 deduplication, restrictive permissions, temp+fsync+atomic rename, JVM/file locks, actual filesystem capacity, recovery and missing-file detection. |
| T-3 authenticated API | IMPLEMENTED | Controller exposes settings/stats/list/detail/content/protection/delete-preview/delete under the approved prefix. It uses existing authentication interception, integral request-ID validation, parameterized/escaped SQL filters, ownership checks, PDF magic and single Range handling, plain-text fallback and safe headers. |
| I-1 independent archive | IMPLEMENTED | Archive roots are rejected when overlapping old samples; no expert/admission/mail/queue repository is injected. Shared-body and deletion tests preserve record metadata and success history. Existing sample tests pass unchanged. |
| I-2 truthful recoverable states | IMPLEMENTED | File and SQL failure fixtures cover BEFORE_RENAME, AFTER_RENAME, AFTER_DELETING and AFTER_UNLINK. A real transaction commit failure rolls back SAVED registration. Recovery is idempotent; missing content returns 410; unprotect never unlinks. Lifecycle recovery failure logs a fixed reason rather than preventing application startup. |
| I-3 shared/protected deletion | IMPLEMENTED | A-only shared skip; A/B protected shared skip; unprotect then physical deduplicated delete; multi-file partial delete; protection/new-reference snapshot invalidation; DELETING protection/reuse rejection; unlink-error retry recovery all exercised. |
| I-4 settings/capacity | IMPLEMENTED | Default-disabled/no-row values, fresh coordinator settings reread, CAS, legal below-usage cap reduction, exact-byte cap/free-space boundary, concurrent writers, unknown/null disk values and database-unavailable 503 assertions executed. Real FileStore values also observed in the standalone smoke. |
| I-5 access boundary | IMPLEMENTED | Real AuthInterceptor exercised with MockMvc; unauthorized requests, positive/integral IDs, cross-record attempt, 404/410, symlink/relative/overlap attacks, falsely-labelled PDF/HTML/XML, inline/attachment and valid/invalid Range assertions executed. |

### Changed files

Only these approved product/test files were staged and committed:

- `src/main/resources/db/migration/V159__create_discovery_paper_archive.sql` — archive-only schema.
- `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryPaperArchiveProperties.kt` — deployment directory/allowed configuration registration.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt` — persisted models, derived states, DTOs and fixed errors.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` — JDBC transactions/CAS, parameterized list/counts, immutable success accounting.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` — storage, locking, lifecycle recovery, access, protection and shared deletion.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveController.kt` — authenticated management/content API.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt` — isolated MySQL fixture, SQL assertions and separate smoke launcher.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveServiceTest.kt` — real-file/real-SQL storage, failure and recovery scenarios.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt` — authenticated transport/content/protocol assertions.

`git diff --cached --stat` before commit reported `9 files changed, 1347 insertions(+)`; `git diff --cached --check` exited 0. No applied migration, existing sample implementation, auth whitelist, legacy business store or frontend file was changed.

### Required command

Executed freshly after the final implementation edits:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DmigrationIt=true
```

Final result: exit 0, BUILD SUCCESS, completed 2026-10-23 22:57:54 +08:00.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveRepositoryIT | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveServiceTest | 14 | 0 | 0 | 0 |
| DiscoveryPaperArchiveControllerTest | 7 | 0 | 0 | 0 |
| DiscoverySampleStoreTest | 7 | 0 | 0 | 0 |
| DiscoverySampleCaptureTest | 4 | 0 | 0 | 0 |
| Total JVM | 36 | 0 | 0 | 0 |

The same Maven command also executed the configured Node regression phase: 1807 tests passed, 0 failed, 0 skipped, plus configured app/task-modal syntax checks. Evidence: session `artifact://31`, suite summaries and final Node summary.

SQL evidence is not mocked or silently gated: Testcontainers connected to Docker 29.8.2 / OrbStack, started MySQL 8.0.36 for database `archive_child01`, and applied the actual V159 resource through ResourceDatabasePopulator. RepositoryIT and the service/controller fixtures issue real JdbcTemplate operations and transactions. No production Spring application context or production DB was started/accessed.

### Standalone behavioral smoke

Executed separately from JUnit, after final compilation, with a child JVM:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher'
```

Result: exit 0, BUILD SUCCESS, 2026-10-23 22:58:14 +08:00. Evidence: session `artifact://34`.

This launcher creates a separate disposable MySQL container, uses V159 and actual temporary files, constructs the new service/controller directly without a production application context, and uses the actual FileStore rather than the test disk probe.

Observed output:

```text
SMOKE settings reread: 25/15 GiB; FileStore total=494384795648, usable=55366455296, measuredAt=2026-10-23T14:58:12.412101Z
SMOKE saved=3072 bytes/2 files; B protected => deletable=2048; unprotected => all bytes unchanged; controller Range=206/100 bytes
SMOKE shared delete=2 files/3072 logical bytes; remaining=0; metadata/outcome/analysis retained; content=410
```

The scenario checked default 20/15 GiB disabled settings, saved 25/15 GiB settings and reread them through a fresh coordinator, created A/B sharing 1024 identical bytes and C with 2048 distinct bytes, persisted extraction/analysis, protected B, cancelled via preview only, unprotected B without changing its bytes, read exact saved content, fetched an exact 100-byte PDF Range through the controller, then deleted both physical files once. Record metadata/outcome/analysis remained accessible, and deleted content returned 410. `deletedBytes` is logical file bytes, not a physical free-block claim. Temporary fixture directories were removed and the isolated MySQL container stopped.

### Other commands, failed attempts and resolutions

| Command / attempt | Result and resolution |
|---|---|
| Initial required Maven invocation | Exit 1: 36 tests, 1 failure, 22 errors, 0 skipped. Archive fixture paths inherited macOS's `/var` symlink; production correctly rejected the symlink ancestor. Canonicalized only the test-created temporary directory with `toRealPath()`; did not relax production path checks. |
| Second required Maven invocation | Exit 1: 36 tests, 1 failure, 0 errors, 0 skipped. Real MySQL exposed JSON-boolean versus JDBC-boolean comparison in hasGain filtering. Kept the acceptance assertion and changed SQL to compare JSON_UNQUOTE against the textual Boolean parameter. |
| Subsequent required invocations before final lifecycle completion | Exit 0: 36 JVM tests and 1807 Node tests passed, no failures/errors/skips. Final command above supersedes these runs. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=target/archive-smoke-classpath.txt -DincludeScope=test` | Exit 0. Generated only ignored target output, no product dependency changes. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:java -Dexec.mainClass=com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher -Dexec.classpathScope=test` | Exit 1 after all scenario checks/output: Maven's in-process exec classloader teardown interrupted Testcontainers/Ryuk/MySQL cleanup threads and produced classloading warnings. Switched invocation to the separate child JVM command above; no product workaround or weakened assertions. The final smoke exited 0. |
| Plan identity helper, initial and pre-handoff | Exit 0; unchanged canonical path and SHA-256 recorded above. |
| Worktree identity helper, initial / before staging / before commit / after commit | Exit 0 with expected root, branch and worktree Git directory. |
| `git diff --cached --check && git diff --cached --stat` | Exit 0; exact authorized files only. |
| `git commit -m 'feat(fast-p): implement 01'` | Exit 0; product/test-only commit `90dec47f8604886df890c20e9e31dd394c20173d`. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor HEAD refs/heads/fast/discovery-paper-master` | Exit 0; recorded implementation SHA is target HEAD and reachable from the target branch. |

### Deviations and limitations

- No approved-plan amendment or product-scope deviation.
- The small controlled failpoints simulate abrupt interruption at the approved filesystem/SQL boundaries; this is not a production process-kill or deployment test.
- The smoke uses a fresh coordinator to observe persistent settings, not a production Spring restart (the brief forbids starting that context).
- Manual A-1/A-2/A-3 acceptance remains human PENDING; automated tests and standalone smoke are execution evidence, not independent verification or human acceptance.
- The required focused JVM suites and Maven-bound Node checks ran. A full unfiltered Maven suite, WAR build, deployment and browser session were not requested for this child and were not claimed.
- No real Apollo, production database, external paper/data request, push, merge or history rewrite. No later child plan was reviewed.
- Existing baseline sample results supplied by the controller were treated as historical baseline only; the final required command freshly reran the unchanged sample suites.

### Freshness and handoff

- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES.
- Reported commit is target HEAD and target-branch reachable: YES.
- Required command ran after final implementation edits: YES.
- Standalone real-file/real-SQL smoke ran after final implementation edits: YES.
- Product/test worktree and index clean after implementation commit: YES; the preserved controller ledger and this intentionally uncommitted report are control-plane evidence only.
- Remaining blocker: None.
- Next action: controller runs independent verify-p against this exact child plan and implementation commit.


## Epoch 1 — Terminal controller record
- Implementation: 90dec47f8604886df890c20e9e31dd394c20173d
- Fix round: 1
- Terminal code head: 74c00bdab46796084c5d4a1a7a3c572b7bee6915
- Independent terminal verdict: LIGHT_PASS, PaperStorageReverifier1, epoch 1 attempt 2.
- F-1 resolved by the separately committed round 1 repair; fresh verifier evidence records 37 JVM tests, 1807 Node tests and separate real-file/MySQL smoke, all passing.
- Human acceptance and whole-system verification remain unperformed. The next gate is controller-owned artifact validation, not another product review.
