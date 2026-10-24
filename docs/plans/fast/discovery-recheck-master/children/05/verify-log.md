# Child 05 — Verification log

## Epoch 1 — Verification not started

- Executor Recheck05Implementer returned PLAN_CONFLICT before any implementation commit.
- No verifier was acquired and no independent light verdict is claimed.
- Source-only precondition evidence in execution.md identifies six unlisted tests pinned before required V161 schema; they require human scope amendment. No checks were run to reconfirm a known mismatch.
- Required Maven command and actual MySQL/runtime smoke NOT RUN. Nine authorized product drafts remain uncommitted/uncompiled/unverified; no PASS, skip, or completed-child claim.
- Last terminal product code head: 6884eb7f31b1763dbb14422e91dfd63dc022fb94. Actual pre-pause governance HEAD: 11a9cedf0074f831bc59f7816818355e30310c92.

## Epoch 2 / Attempt 1 — 2026-10-25T03:17:10+08:00

## Light Verification: LIGHT_FAIL
Child: 05 — docs/plans/2026-10-24/discovery-recheck-05-history.md, approved identity commit:863530b3f0d8aca6c0cd03fa29d9af7df8cb09d6
Boundary: 6884eb7f31b1763dbb14422e91dfd63dc022fb94..74a4c94e88230d88710e35eb9b45612a4234fb52
Verifier: Recheck05Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff 6884eb7f31b1763dbb14422e91dfd63dc022fb94..74a4c94e88230d88710e35eb9b45612a4234fb52 -- src/main src/test` returned exactly the fifteen approved product/test paths, 723 insertions/52 deletions (`artifact://1031`). The separate exact six-file diff (`artifact://1033`) contains only ten Flyway target substitutions to161; no assertions were removed/weakened. Governance/evidence paths in the wider boundary are not product scope expansion. A5/A6 authorize these fixtures and the15-file exception. No applied migration/controller/start endpoint, mail/sender, provider/cache identity, or later-child production file changed. |
| Plan and invariants | PASS | Direct child05 I-1–I-5 evidence: V161:1–35; domain:34–40,57–85,113–143,161–214; Repository:374–547,550–625,628–702,725–865,895–925; RepositoryIT:508–758. Actual MySQL cases cover V158 field preservation, ordinary epoch0 dedup, independent owner epochs/retry, spec409/stale+concurrent CAS, claims, business/history and staged reads, eight slots/terminal append retries, cancellation/interruption/CLI/request/outcome/commit/projection boundaries. Lock helpers906–915 acquire run before item; new methods contain SQL/local computation, not network operations. Fresh primary and independent runtime observations below confirm these paths. The downstream preparation/activation discrepancies are separately FAIL in Gate4, not hidden by these passing child05 tests. |
| Required commands | PASS | Both exact JDK11 Maven commands ran freshly and sequentially with `-DmysqlIt=true`: primary exit0,60 JVM/0 failures/0 errors/0 skips; additional exit0,89 JVM/0 failures/0 errors/0 skips. Each also ran1827 JS/1827 passes/0 failures/0 skips and both bound syntax checks. Independent actual migrated MySQL/domain/Repository/Gate/Query standalone command exit0, with the downstream deviations explicitly observed rather than treated as passing requirements. Details/limits below. |
| Downstream interfaces | FAIL | Default null run JSON/epoch0 and existing ordinary signatures remain compatible; immutable spec/control, trace/history, filtered Gate and Query interfaces exist. However approved08 I-2 (`discovery-recheck-08-api.md:17–19`) requires preparation/READY items remain RESERVED, with no step rows before activation; implementation08:45–46 starts via READY→RUNNING then activates. Current Repository:424–426 refuses READY unless items are already QUEUED;574 permits recheck activation only PREPARING;462 allocates eight steps during reservation. Independent actual SQL smoke observed `reservedReady=rejected prematureStepRows=8` (`artifact://1039:17671`). See F-05-01/F-05-02. |

Fresh command evidence (cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`; no product/test edits by verifier):

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest -DmysqlIt=true`
   - Exit0;209.48s wall, Maven03:28. RepositoryIT24, Contract8, Gate9, Query19; total60, failures0/errors0/skips0. Actual disposable MySQL8.0.36 includes V158→V161 preservation and concurrent CAS/claims.
   - Bound JS1827/pass1827/fail0/cancelled0/skipped0; node-check-app and node-check-task-modal-runtime succeeded.
   - `artifact://1036:991–1007,3514–3530`.
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationReportingIT,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationIntegrationTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationWorkerTest -DmysqlIt=true`
   - Exit0;462.59s wall, Maven07:41. Reporting12, Country8, Flow32, Integration14, Replay7, Worker16; total89, failures0/errors0/skips0. Mandatory MySQL tests actually executed; no mocked-SQL or skipped-IT substitution.
   - Bound JS1827/pass1827/fail0/cancelled0/skipped0; both bound syntax checks succeeded.
   - `artifact://1038:753,999,1621,1938,2198,3526–3532,6038–6055`.
3. `python3 /tmp/run_recheck05_verifier_smoke.py`
   - Exit0;25.86s wall. JDK11/Kotlin1.9.25 compiled a new standalone main outside the repository; runtime classpath excluded `target/test-classes`, and no JUnit runner/test fixture was called. Explicit DockerAPI1.40 matched the established harness requirement; no DockerAPI failure occurred.
   - Actual isolated MySQL8.0.36 database `child05_verifier`: migrated to158, inserted synthetic old run/item with request_count11 and original evidence, compared every old run/item field after migrating161, then used production domain/transaction-proxied Repository/Gate/Query.
   - Observed ordinary dedup and preserved count; two real task-owned epochs and same-round retry; main-doc history3; immutable spec409; repeatable scope hash; stale CAS and PREPARING/READY claim exclusion; eight slots, terminal rewrite rejection and appended retry; staged business exclusion then committed visibility; cancellation blocking renewal/new attempt/late completion/outcome/new commit; preserved committed projection; actual Query detail/history/cancelled counts and Gate business context.
   - Crucially, a frozen RESERVED roster failed READY with `Scope is not fully frozen`, and reservation had already written8 trace rows: `CHILD05_DOWNSTREAM_DEVIATION_OBSERVED reservedReady=rejected prematureStepRows=8 required08=RESERVED_READY_AND_ZERO_PREACTIVATION_STEPS` (`artifact://1039:17671`). The remaining smoke then exercised the current preactivation sequence to observe the other required boundaries; its exit0 does not clear this downstream failure.
   - Marker: `CHILD05_VERIFIER_RUNTIME_OK migrated=158-to-161 ordinary=1 epochs=2 mainHistory=3 eightSlots=verified terminalRetry=append staleCAS=rejected specRewrite=409 earlyClaims=blocked stagedBusiness=isolated committedBusiness=visible cancelled=1 committedProjection=preserved gateQuery=actual providerRequests=0` (`artifact://1039:19270`).
   - Limits: Gate selection used actual `findContext`; staged/attach/projection boundaries used production repository methods against disposable SQL, not the full CommitService→candidate ES projection or a production start API. No production data/schema writes, production/provider HTTP, paid requests, ES calls, deployment, full-system PASS, or human acceptance claimed.
   - MySQL container/volumes removal observed (`artifact://1039:19491–19492`). Temporary source/launcher/jar removed successfully. SHA256 before removal: source `ca3e41dec9ace4deaeae4a10cf5b20fa955f575ce3072b26e59144b33273a579`; launcher `c8cb1a4ed36ab8300f3f07a8a1c0b9c1db6436c49edd6a45ca4cd811d90efcbe`; jar `9e7cc0469505095cf3c33263555dd8a84805537ebe51900247dd06793d69e241`.

These fresh test totals match the recorded final Epoch2 baseline60/89 JVM and1827 JS per command, all zero failures/errors/skips. The historical SQL3819 exception assertion and initial DockerAPI launcher failures were resolved baseline events, not current failures. The new findings are the independently observed downstream contract deviations.

### AUTO_FIX
- F-05-01 — Governing downstream requirement: approved08 I-2:18 and implementation:45–46, linked by child05 brief:44 and Gate4. READY must represent a fully frozen, closed roster whose items remain RESERVED until the transactional start changes control to RUNNING and activates them. Current Repository:424–426 instead requires QUEUED; activateItem:574 only permits PREPARING. Actual disposable smoke rejects READY for unchanged RESERVED inputs (`artifact://1039:17671`); RepositoryIT:589–604 currently asserts that incompatible sequence. Smallest authorized correction: in `DiscoveryVerificationRepository.kt` align READY validation with frozen RESERVED items and allow their true activation at RUNNING, retaining frozen hash/spec, revision CAS, input_closed and run→item locking, with no early claim/lease/attempt permission or new endpoint; update the corresponding cases in authorized `DiscoveryVerificationRepositoryIT.kt` to exercise RESERVED→READY→RUNNING→activation and idempotence. This changes the shared storage interface, not later-child orchestration.
- F-05-02 — Governing downstream requirement: approved08 I-2:18 explicitly defers step-table initialization until true activation. `reserveRecheckItem` calls `initializeSteps` at Repository:462, so a PREPARING RESERVED item already has8 rows; actual disposable smoke measured8 before any activation (`artifact://1039:17671`), and RepositoryIT:568–569 currently expects that premature allocation. Smallest authorized correction: defer recheck slot initialization from reservation to the actual activation boundary in `DiscoveryVerificationRepository.kt`; keep eight-slot idempotence, append-only terminal attempts, and ordinary historical no-synthetic-trace behavior. Adjust authorized `DiscoveryVerificationRepositoryIT.kt` to require zero preactivation rows and exactly8 after activation/repeated activation. No migration/domain/controller/later-child changes are needed for this correction.

### RECORD_ONLY
- O-05-01 — Fresh logs retain MySQL deprecation/intentional injected-failure and invalid-configuration warnings; the standalone launcher also emits a deprecated TransactionInterceptor constructor warning (`artifact://1039:1`). These did not fail either required command or the runtime harness and do not establish an additional four-gate violation. No unrelated warning cleanup/investigation is proposed.

### Required Action
- AUTO_FIX


## Epoch 2 / Attempt 2 — 2026-10-25T03:44:00+08:00

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 05 — docs/plans/2026-10-24/discovery-recheck-05-history.md, approved identity commit:863530b3f0d8aca6c0cd03fa29d9af7df8cb09d6
Boundary: 6884eb7f31b1763dbb14422e91dfd63dc022fb94..440b7e0cb1daae1eb0d985fef3e12f78d1c7b607
Verifier: Recheck05Reverify1

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Complete product/test diff reviewed (`artifact://1055`, 1243 lines): fifteen authorized paths, 735 insertions/52 deletions. Separate repair boundary74a4c94e88230d88710e35eb9b45612a4234fb52..440b7e0cb1daae1eb0d985fef3e12f78d1c7b607 (`artifact://1057`) changes only Repository and RepositoryIT,25 insertions/13 deletions. Six amended consumer fixtures retain original assertions and change only Flyway schema targets. Wider boundary names (`artifact://1061`) additionally contain controller governance/evidence; these are not additional product behavior. No verifier product/test/plan/index/branch/commit edits. |
| Plan and invariants | PASS | V161:1–35 and domain defaults/constraints retain ordinary epoch0/null and four-part identities. Repository:374–547,550–625,628–702,794–865,895–925 and changed tests directly cover child05 I-1–I-5: independent epochs, immutable inputs/spec, revision CAS, business versus audit history, append-only step retries, cancellation/admission/projection and ordinary recovery/CLI isolation. Fresh real MySQL primary tests cover V158 old-field preservation, concurrent CAS/claims, late-completion/cancellation/interruption exclusion and retained regressions. Independent standalone production-path runtime below directly observes repaired preparation/activation, history/business reads and cancellation/projection. |
| Required commands | PASS | Both exact prescribed JDK11 Maven commands executed freshly after repair, sequentially, with mysqlIt=true: primary exit0,60 JVM; additional exit0,89 JVM; both zero failures/errors/skips. Each also executed1827 JS tests with1827 passes and zero failures/skips plus both bound syntax checks. Independent standalone actual migrated MySQL/domain/Repository/Gate/Query runtime exit0. Counts match execution Epoch2 and repair1 baselines; baseline results were not reused as current proof. |
| Downstream interfaces | PASS | Approved08 I-2:18 and implementation45–46 now align with Repository:422–436,440–463,569–580: frozen closed RESERVED roster reaches READY, reservation writes no steps, and READY→RUNNING plus activation in one SQL transaction creates eight NOT_STARTED slots. READY claims remain excluded; RUNNING RESERVED items cannot be claimed before activation. Repeated activation preserves slots/batch. Fresh RepositoryIT:585–624 plus independent SQL marker prove closure. Ordinary null/epoch0 defaults and ordinary no-synthetic-trace behavior remain; explicit staged Gate context is retained separately from filtered business reads. No future start API or full CommitService/ES projection claim. |

Fresh commands, all in `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`:

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest -DmysqlIt=true`
   - Exit0;217.53s wall, Maven03:35; finished2026-10-25T03:36:06+08:00.
   - RepositoryIT24, Contract8, Gate9, Query19 =60; failures0/errors0/skipped0. Actual isolated MySQL8.0.36 tests ran, including intentional158→161 old-data migration stage; mandatory ITs were not skipped.
   - Bound JS1827/pass1827/fail0/cancelled0/skipped0; node-check-app and node-check-task-modal-runtime succeeded.
   - Evidence `artifact://1064:991–1007,3514–3529`.
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationReportingIT,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationIntegrationTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationWorkerTest -DmysqlIt=true`
   - Exit0;450.01s wall, Maven07:28; finished2026-10-25T03:43:39+08:00.
   - Reporting12, Country8, Flow32, Integration14, Replay7, Worker16 =89; failures0/errors0/skipped0; actual disposable MySQL, not substituted mocked SQL.
   - Bound JS1827/pass1827/fail0/cancelled0/skipped0; both bound syntax checks succeeded.
   - Evidence `artifact://1066:753,999,1621,1938,2198,3526–3532,6038–6054`.
3. `python3 /tmp/run_recheck05_attempt2.py`
   - Exit0;28.37s wall. Newly written independent Kotlin standalone main compiled with Kotlin1.9.25/JDK11; runtime classpath explicitly removed target/test-classes. No JUnit runner/test fixture was invoked. Production Repository writes used explicit Spring TransactionTemplate boundaries with actual JdbcTemplate; production domain/Gate/Query used actual SQL.
   - Disposable MySQL8.0.36 database `independent05a2`; actual Flyway158→161 with old run field-map comparison. Ordinary epoch0/null defaults, dedup and no synthetic steps; two real task-owned epochs and same-round retry; three-item history; immutable frozen scope and stale CAS; business exclusion before attach and visibility after attach; terminal step rewrite rejection and appended attempt; cancelled item label/count isolation; committed admission preserved through cancellation and projection retry.
   - Direct repair marker (`artifact://1059:18243`): `REPAIR_CLOSURE reservedReady=true readyStepRows=0 transactionRunningActivationSlots=8 repeatedActivation=idempotent`. SQL COUNT was0 at READY; one explicit transaction changed READY→RUNNING and activated the item, with exact1..8 NOT_STARTED slots; repeated activation did not modify them. This exercises the repaired path, not the old failing sequence.
   - Final marker (`artifact://1059:18649`): `ATTEMPT2_RUNTIME_OK ordinaryDefaults=retained epochs=2 history=3 staleCAS=blocked terminalRetry=append stagedBusiness=isolated committedBusiness=visible cancellation=separate committedProjection=retained actualGateQuery=true providerRequests=0`.
   - Limits: standalone staged-business observation used saved completed-unattached versus attached repository rows, not a full CommitService transaction or ES projection; explicit staged Gate recomputation is covered by fresh Gate tests. Cancellation of a READY item and preservation of an admitted item's projection were observed standalone; running-item lease/HTTP-attempt/late-outcome/interruption restrictions are covered by fresh actual MySQL RepositoryIT. No production/provider HTTP, paid requests, production schema/data, ES, deployment, future start endpoint, whole-system PASS or human acceptance claimed.
   - Container and associated volumes removed (`artifact://1059:18870–18871`). Temporary source/launcher/jar removed with successful `shasum -a 256 /tmp/Recheck05Attempt2.kt /tmp/run_recheck05_attempt2.py /tmp/recheck05-attempt2.jar && rm /tmp/Recheck05Attempt2.kt /tmp/run_recheck05_attempt2.py /tmp/recheck05-attempt2.jar`; SHA256 respectively `13df1b54ee5c00fbf5dc06baa14492280dfd940f395240df70ca33e0c7938ca3`, `a78ca5e60e633a61babb9b5c9a397c4996ef69d3342775451d6f5eea3101a0e4`, `70ad409a1a10c7de307aa4e561091bce1c219cfef23b36754e99b7c1c419b483`.

### AUTO_FIX
- N/A — no OPEN four-gate deviation proved.
- F-05-01 — CLOSED. Approved08 RESERVED/READY and transactional RUNNING activation contract is directly proved by repaired Repository:422–436,569–580, fresh RepositoryIT and actual SQL runtime marker above.
- F-05-02 — CLOSED. Reservation no longer initializes step rows (Repository:440–463); activation alone initializes recheck slots (575–577). Actual SQL observed0 at READY and eight after true activation, with repeated activation idempotent. Ordinary items retained no synthetic trace.

### RECORD_ONLY
- O-05-01 — Retained observation: successful fresh logs include MySQL deprecation/intentional invalid-configuration and injected-failure warnings (e.g. primary1064:997–1000; additional1066 around1620). These do not establish a further four-gate violation; no warning cleanup or unrelated investigation undertaken.

### Required Action
- COMPLETE_CHILD

Terminal verdict: LIGHT_PASS_WITH_NOTES. Repair round1 closes both prior findings; no OPEN finding or authority conflict. Master A7 continuing in-goal amendment authority was read as controller governance, not permission for verifier edits.

