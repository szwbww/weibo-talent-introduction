# Child 01 light verification log


# Epoch 1 / Attempt 1 — 2026-10-23T23:06:38+08:00

## Light Verification: LIGHT_FAIL
Child: 01 — docs/plans/2026-10-23/discovery-paper-01-storage.md
Boundary: c18dbc060dcf4c8440f853ede4d1d3aaf737994f..90dec47f8604886df890c20e9e31dd394c20173d
Verifier: PaperStorageVerifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary diff contains the nine product/test paths authorized by the child plan; fast-p governance files excluded as instructed. `git diff 90dec47f8604886df890c20e9e31dd394c20173d -- src/main src/test` produced no differences. No applied migrations, legacy samples, expert/admission/mail stores or auth configuration changed. |
| Plan and invariants | FAIL | F-1: DiscoveryPaperArchiveService.kt:93-110 sanitizes textual object fields, but lines 104-105 deep-copy textual array elements unchanged; beginRecord/finishExtraction/saveAnalysis persist that result (113-132), and detail returns it (271-276). Consequently T-3/I-5 prohibition on exposing local paths, URL query parameters and credentials is not upheld for array values. Remaining reviewed storage/settings/protection/shared deletion/recovery invariants have direct implementation and fresh focused-test/smoke evidence. |
| Required commands | PASS | Fresh JDK11 Maven command and standalone smoke below both exited 0. JVM: 36 tests, 0 failures/errors/skips; RepositoryIT actually ran 4 tests against isolated MySQL 8.0.36 on Docker 29.8.2. Legacy sample suites: 7+4=11 passing, identical to supplied baseline (0 failures/errors/skips). Maven-bound Node phase: 1807 passing, 0 failures/skips; configured syntax checks succeeded. |
| Downstream interfaces | PASS | Service.kt:113-134 exposes beginRecord/beginAttempt/finishAttempt/finishExtraction/saveBody and saveAnalysis for the declared child seams. Models.kt:17-69 supplies the fixed DTO names/shapes and enums; Repository.kt:38-65,118-128 handles persisted kind/status values; Service.kt:217-233,245-276,377-399 handles gates, derived states and recovery; Controller.kt:13-55 routes settings/stats/list/detail/protection/delete/content and Range. Existing AuthWebConfig.kt:23-26 covers the prefix without a new public exception. No later child plans inspected. |

Fresh required command (exit 0; BUILD SUCCESS at 2026-10-23T23:05:23+08:00; full output artifact://46):

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DmigrationIt=true`

Suite counts: RepositoryIT 4; ServiceTest 14; ControllerTest 7; SampleStoreTest 7; SampleCaptureTest 4. All failures/errors/skips zero. MySQL used disposable database archive_child01 at localhost:32783; fixture applies the actual V159 resource with ResourceDatabasePopulator and uses JdbcTemplate/real transactions, not an in-memory repository.

Fresh separate behavioral smoke (exit 0; BUILD SUCCESS at 2026-10-23T23:05:41+08:00; full output artifact://48):

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test "-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher"`

Observed: settings reread 25/15 GiB through a fresh coordinator; real FileStore total=494384795648, usable=55271780352, measuredAt=2026-10-23T15:05:39.677283Z. A/B shared 1024 bytes and C owned 2048 bytes: saved=3072 bytes/2 physical files; B protection limited preview to 2048 deletable bytes; unprotection left content unchanged; controller PDF Range returned 206 and exactly 100 bytes. Shared delete removed exactly 2 files/3072 logical bytes, remaining=0, retained metadata/outcome/analysis, and content returned 410. Smoke used real temporary files and its own disposable MySQL at localhost:32785; no production Spring context, production database, Apollo or external paper request.

### AUTO_FIX
- F-1 (P2), T-3/I-5: sanitize textual array values at DiscoveryPaperArchiveService.kt:104-105. A valid input such as `{"schemaVersion":1,"links":["https://user:password@host/p?api_key=secret","/private/source.pdf"]}` is currently stored and returned verbatim inside links because array recursion reaches the unmodified scalar fallback. This is static direct data-flow evidence, not an additional exercised test. Smallest authorized correction: apply the existing safeUrl/path sanitization to textual nodes at every recursive position; add nested-array regression coverage in the already-authorized DiscoveryPaperArchiveServiceTest.kt and/or DiscoveryPaperArchiveControllerTest.kt. Preserve non-sensitive scalar values and existing DTO shapes. No product fix applied by this verifier.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX


# Epoch 1 / Attempt 2 — 2026-10-23T23:17:39+08:00

## Light Verification: LIGHT_PASS
Child: 01 — docs/plans/2026-10-23/discovery-paper-01-storage.md
Boundary: c18dbc060dcf4c8440f853ede4d1d3aaf737994f..74c00bdab46796084c5d4a1a7a3c572b7bee6915
Verifier: PaperStorageReverifier1

Execution epoch: 1; verification attempt: 2; completed fix rounds: 1.
Original implementation: 90dec47f8604886df890c20e9e31dd394c20173d
Fix round 1 commit: 74c00bdab46796084c5d4a1a7a3c572b7bee6915

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only c18dbc060dcf4c8440f853ede4d1d3aaf737994f 74c00bdab46796084c5d4a1a7a3c572b7bee6915` lists the nine product/test paths in the approved Authorized Files table, plus fast-p governance artifacts. Repair diff `90dec47f8604886df890c20e9e31dd394c20173d..74c00bdab46796084c5d4a1a7a3c572b7bee6915` changes only DiscoveryPaperArchiveService.kt and DiscoveryPaperArchiveServiceTest.kt. `git diff 74c00bdab46796084c5d4a1a7a3c572b7bee6915 -- src/main src/test` returned no differences; `git log -1` identifies that exact HEAD on fast/discovery-paper-master. No applied migration, sample implementation, auth whitelist or business store changes. |
| Plan and invariants | PASS | F-1 is RESOLVED: Service.kt:90-108 routes object values and every array element recursively through the same textual-node sanitizer, removing absolute paths and URL userinfo/query/fragment while preserving other scalars. The real-SQL regression ServiceTest.kt:228-241 verifies persisted metadata/outcome/analysis and returned detail for arrays nested in arrays and objects. T-1/T-2/I-1 through I-4 have direct schema/repository/service and fresh storage/settings/capacity/recovery/protection/shared-deletion test evidence; ControllerTest covers I-5 authentication, ownership, status codes, safe content and Range. |
| Required commands | PASS | Both brief-required command invocations below ran freshly after evidence inspection and exited 0. JVM: 37 tests, 0 failures/errors/skips; RepositoryIT ran 4 against isolated MySQL 8.0.36. Existing sample suites ran 7+4=11 with 0 failures/errors/skips, matching the supplied JDK11 baseline. Maven-bound Node: 1807 passed, 0 failed/cancelled/skipped; configured app/task-modal syntax checks completed successfully. Separate smoke observed actual temporary files, real SQL and FileStore. |
| Downstream interfaces | PASS | Service.kt:115-136 exposes beginRecord/beginAttempt/finishAttempt/finishExtraction/saveAnalysis/saveBody, with fixed DTOs in Models.kt:17-69 unchanged by the repair. Consuming dispatch was inspected: Repository.kt:38-71,118-128 persists/reads attempt kind/status and computes record states; Service.kt:217-233,245-278,374-401 handles gates, derived states, JSON detail and recovery; Controller.kt:13-55 forwards settings/stats/list/detail/protection/delete/content including Range. AuthWebConfig.kt:23-26 covers the prefix with no public exception. No later child implementation or plan inspected. |

### Fresh Required Commands

1. Focused full brief-required test command:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DmigrationIt=true`

Exit 0; BUILD SUCCESS; finished 2026-10-23T23:16:25+08:00. Output: artifact://61.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveRepositoryIT | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveServiceTest | 15 | 0 | 0 | 0 |
| DiscoveryPaperArchiveControllerTest | 7 | 0 | 0 | 0 |
| DiscoverySampleStoreTest | 7 | 0 | 0 | 0 |
| DiscoverySampleCaptureTest | 4 | 0 | 0 | 0 |
| Total JVM | 37 | 0 | 0 | 0 |

Testcontainers connected to Docker 29.8.2 / OrbStack and started MySQL 8.0.36 at localhost:32791/archive_child01. The inspected fixture applies the actual V159 resource with ResourceDatabasePopulator and uses real JdbcTemplate transactions; SQL integration was not skipped or replaced by a map. Maven also ran Node regression (1807 passing, 0 failed/cancelled/skipped) and both configured syntax checks. This is the focused command, not an unfiltered whole-system Maven verification.

2. Separate standalone behavioral smoke:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test "-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher"`

Exit 0; BUILD SUCCESS; finished 2026-10-23T23:16:46+08:00. Output: artifact://63. Its own disposable MySQL 8.0.36 ran at localhost:32793/archive_child01; the fixture constructs the service/controller without a production Spring context and cleans up its temporary directory/container.

Observed output:

- `SMOKE settings reread: 25/15 GiB; FileStore total=494384795648, usable=55250108416, measuredAt=2026-10-23T15:16:44.423217Z`
- `SMOKE saved=3072 bytes/2 files; B protected => deletable=2048; unprotected => all bytes unchanged; controller Range=206/100 bytes`
- `SMOKE shared delete=2 files/3072 logical bytes; remaining=0; metadata/outcome/analysis retained; content=410`

The scenario freshly exercised settings reread through a new coordinator, A/B sharing 1024 bytes plus C owning 2048 bytes, protection, preview without deletion, unprotection with bytes unchanged, exact saved-content reads, a 100-byte PDF Range response, deduplicated shared deletion and retained metadata/outcome/analysis with gone content. Deleted bytes are logical bytes, not a physical free-block claim. Verification command count: 2 required invocations; both passed without retry.

### Finding Lineage
- F-1, originally raised in epoch 1 / attempt 1 against 90dec47f8604886df890c20e9e31dd394c20173d for T-3/I-5 nested-array sanitizer bypass: RESOLVED by fix round 1 commit 74c00bdab46796084c5d4a1a7a3c572b7bee6915. The repair moves existing path/safeUrl handling to the scalar textual-node branch after object/array recursion. Fresh passing regression verifies the persisted and returned representations; ordinary strings, numbers, booleans and nulls remain intact. No DTO or downstream interface changes. The original finding/report above remains intact.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

This is child 01 lightweight re-verification only. No product/test/plan/index/branch/commit changes, later-child review, production context/database, Apollo/external paper request, deployment, broad verification or human acceptance was performed. Only this verification report was appended; human A-1/A-2/A-3 acceptance remains pending.
