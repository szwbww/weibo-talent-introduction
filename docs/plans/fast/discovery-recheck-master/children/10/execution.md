# Child 10 — Execution log

## Execution Result: READY_FOR_VERIFICATION

This is implementation evidence, **not PASS or independent verification**. The required full JVM test command exited 1; its failures remain explicitly open for the fresh verifier to classify. No production binding application, ENFORCE, admission, provider request, mail, or contact write was performed.

### Identity, authority, and writer lineage

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-10-historical-replay.md`.
- Current exact plan SHA-256: `a542ba8ed29cd38160b01eae8ab09ad65059d7ad0a2b588af49ff611764298ea`; 11676 bytes; approved plan commit `da8940153f4ad22a5ac3a094f444a7ac4858f695`.
- Execution ID: canonical plan path above `@a542ba8ed29cd38160b01eae8ab09ad65059d7ad0a2b588af49ff611764298ea`; current plan epoch **3, NEW identity**, continuing retained implementation without reset. This writer originally resumed epoch 2, plan SHA `e5127d2c245efbe97dccdad137ff37a5ca4a246da6ec476dac34d59886319956`, approved plan commit `aa66b4b68e938c8bf827c4bd9914e18fd5e2af58`.
- Authority: standing A7; epoch-2 A15/A16 retained; controller explicitly approved epoch-3 amendment before the ninth product file was edited. Governance HEAD at epoch-3 release: `08b48240ba71dd70fa1250928505a86e64700b29`; recovery started at governance HEAD `292b264d4453f024328a0a9fbcb59956576b90b7`.
- Executor: `Recheck10Resume`, sole product writer after inactive predecessor `Recheck10Implementer`. Predecessor transport exit 1 / unknown-certificate error was not a product verdict. Children 01–09 were not replayed.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`; branch `fast/discovery-recheck-master`.
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`; common directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: target root `@fast/discovery-recheck-master@` worktree Git directory above.
- Pre-execution product base: `306664e25738d78e2587e175c05bb0724e778d71`.
- Post-execution code SHA: **`0d60b43e586584319b78c4f3f9ca86b4820dd24f`**, subject **`feat(fast-p): implement 10`**. Commit result: nine files, 1150 insertions, eight deletions; executable script mode 100755.
- Implementation boundary: original product base through the product SHA above, retaining intervening governance history. Evidence HEAD: not created by this writer; this report is deliberately excluded from the product commit for controller evidence/checkpoint handling.
- Plan/worktree identity helpers were run at epoch-3 registration and before staging/commit; plan hash unchanged. Product SHA was observed as target HEAD and `git merge-base --is-ancestor HEAD refs/heads/fast/discovery-recheck-master` exited 0. Product/index clean after commit; controller ledger change and untracked fast-p evidence placeholders were preserved.

### Task status and exact changed files

| Requirement | Implementation evidence |
|---|---|
| I-1 frozen truth and layered counts | Version-2 manifest binds record/attempt/SHA, baseline/attempt anchors, expert identity/fact/revision, configuration, parser/rule/class identity, evaluation time, and replay budget mode. Two genuine full-source recomputations have identical report hashes. PARTIAL does not expose complete-gain counts. |
| I-2 shared production admission boundary | Actual retained XML/PDF parsers produce proof; archived thin consumer delegates to `consumeOutcomeInternal`. Disposable-MySQL/strict-fixture-ES runtime exercises validation, dedup, RAW, registration, worker, gate, commit, projection, and eight-step trace; no preseeded correct proof. |
| I-3 default read-only / explicit application | Actual executable freeze/compare/compare/report exits 0 without business writes; wrong manifest hash exits 2; explicit application twice creates RAW only once. Current source/identity/revision rejection remains enforced. |
| I-4 fixed unfinished work and truthful failure | Paper leases and receipts survive cancellation/retry; missing source stays failed/PARTIAL; completed papers are skipped on repeated apply. Existing identity gets an independent 08 recheck, not old-item reset. Old attempts/history/checkpoints/task statistics/mail/contact snapshots remain unchanged in actual runtime. |

Authorized product files committed, and no other product/test files:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — narrow archived-paper consumer and frozen execution-context propagation through existing consumption/write boundary.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillService.kt` — explicit fixed-manifest parse/lease/receipt/application orchestration.
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayTool.kt` — protected freeze/default compare/report/apply CLI; complete/PARTIAL reductions and SQL-backed task receipts.
4. `scripts/discovery_recheck_replay/run.sh` — executable entry point, no credential echo.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillServiceTest.kt` — cancellation, repeat, rejection, shared-consumer contracts.
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayToolTest.kt` — real parser/proof and deterministic bounded replay, including actual 101-page PDF repeated comparison with PAGE_LIMIT.
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` — real servlet/SQL runtime plus actual CLI and private historical mirror executable.
8. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — default-null frozen context at initial reservation; ordinary callers retain prior behavior.
9. `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` — explicitly approved read-only frozen-replay factory; ordinary clock/deadline/work bounds unchanged.

Existing country/verification/extractor deadline test files were not modified. No migration, new table, scheduler, live provider, production ES/SQL business write, mail pipeline, or contact change was added. No new cache framework or cached-first-report substitute was used.

### Deterministic replay amendment: concrete receipts

Targeted source search used regex `nanoTime|SOFT_NANOS|MAX_PAGES|MAX_TEXT_BYTES|MAX_FACTS|forFrozenReplay|replayAnalyzer|REPLAY_BUDGET_MODE|PaperQualificationEvidenceExtractor\(` against the extractor and Tool files. Relevant output:

```text
PaperQualificationEvidenceExtractor.kt:22  nanoTime = System::nanoTime
:25 start = nanoTime()
:35 page loop 1..minOf(totalPages, MAX_PAGES)
:36 and :43 nanoTime() - start >= SOFT_NANOS => SOFT_TIME_LIMIT
:40 MAX_TEXT_BYTES guard; :133 MAX_FACTS guard
:234 MAX_PAGES=100; :235 MAX_TEXT_BYTES=2*1024*1024
:236 MAX_FACTS=100; :237 SOFT_NANOS=10_000_000_000L
:239–240 forFrozenReplay() => new extractor with nanoTime={0L}
DiscoveryHistoricalReplayTool.kt:19 FROZEN_WORK_BOUNDED_100_PAGES_2_MIB_100_FACTS
:41 analyzer() => ordinary new extractor
:42 replayAnalyzer() => forFrozenReplay()
:90 version/rule/budget identity validation
:101 compare default analyzer=replayAnalyzer()
```

Only read-only Tool comparison selects deterministic work-bounded replay. Ordinary component, Worker, and Backfill analyzer keep the System clock and ten-second soft deadline. Existing deadline regression ran unchanged within the final 15-test extractor class. Historical 3904/PARTIAL evidence was retained, not reclassified. The new version genuinely reparsed inputs twice and remained PARTIAL for its real non-time reasons.

### Fresh final required commands

All commands used the target worktree; the final implementation/test edit preceded these final targeted/runtime/full/package gates. JDK executable observed: Zulu OpenJDK **11.0.15**. Commands were run serially, not on siblings' half-finished edits. Logs below are private under the evidence directory described later.

| Exact command | Observed result | Fresh evidence |
|---|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperBackfillServiceTest,DiscoveryHistoricalReplayToolTest,DiscoveryRecheckFlowIT -DmysqlIt=true` | Exit 0; 11 tests, 0 failures/errors/skips; 604.12 s. FlowIT 2, Tool 6, Backfill 3. | `fresh-targeted-three-wire-final.log`, SHA `5087edd62f64718b4bf7f8a0de22eeef7d8f245a4bebe8e6acc62f8bdf68b2e7` |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperBackfillServiceTest,DiscoveryHistoricalReplayToolTest,DiscoveryRecheckFlowIT,DiscoveryCountryRegistrationTest,DiscoveryCountrySupplementFlowIT,DiscoveryVerificationIntegrationTest,PaperQualificationEvidenceExtractorTest -DmysqlIt=true` | Exit 0; 67 tests, 0 failures/errors/skips; 675.71 s. Class counts respectively 3/6/2/19/8/14/15. Real MySQL exercised. | `fresh-targeted-seven-wire-final.log`, SHA `7cf9c3b9422749c8140894ad6212fe6fb48947d52ca5d654b397510a8d3c5edb` |
| `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1800m -Dapi.version=1.40 -cp "$CP" com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT --archived` | Exit 0, 38.93 s, outside JUnit. Actual script freeze/compare/compare/report: 0; wrong-hash apply: 2; explicit apply twice: 0/0. | `fresh-standalone-wire-final.log`, SHA `90ed33b5c101f2fb0c04198f11e471fa7017bc30273d8fdf0809f4a896c939b2` |
| Same Java/JVM/classpath command with `--historical "$PRIVATE/pending-audit-input.json" "$PRIVATE/pending-archive" "$PRIVATE/audit-reuse-wire-faithful"` | Exit 0; 769.87 s; 897 processed, 897 original compatible frozen caches; actual admission 0, provider HTTP 0, business unchanged. | `historical-audit-reuse-wire-faithful.log`, SHA `b4aeeda7df05ccdbedb036ee8c2b8ce9f23434c2565a1087a17e096fd5e956ab` |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | **Exit 1**, 587.76 s; **5622 tests, 1 failure, 19 errors, 24 skips**. Surefire failure prevented its later bound JS executions. Not an overall green gate. | `fresh-full-maven-test.log`, SHA `4847cca3bc3aa9eade4aeadc422fbc3c7a81948bfdf8e5a7e6af372d2d07d627` |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn package -DskipTests` | Exit 0; 166.81 s. JVM tests explicitly skipped by requested flag; bound `node-test` ran **1840 tests / 335 suites / 1840 pass / 0 fail / 0 skips**. Both bound node syntax checks also ran. | `fresh-package-skipTests.log`, SHA `fb40623d9150e4b215af7e3c02ef6f33a43c6e8404135d26a823ab72090c791d` |
| `bash -n scripts/discovery_recheck_replay/run.sh` | Exit 0; 0.01 s; script actual filesystem mode 0755 and committed 100755. | `fresh-script-syntax.log`, empty-file SHA `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `git diff --check` | Exit 0 before commit. No formatter or independent aggregate review run. | Execution tool receipt. |

`CP` was exactly `target/test-classes:target/classes:` plus the protected `runtime-classpath` file generated by Maven dependency classpath resolution. Forked Java was used, not `exec:java`. The standalone pipeline recorded first new RAW=1, repeat new RAW=0, actual synthetic fixture admitted=1, candidate sync=1, missing-source partial=1, provider HTTP=0, frozen context preserved, old history/checkpoint/mail unchanged. This synthetic positive control is not a claim of production admission.

WAR: `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`; 52,011,947 bytes; SHA-256 `dd149dba93bcd8092528a80838f54244b103abcb568523b2ac7ea42a335d437e`. Package success does not erase the full JVM test failure.

### Full test failures and skips — observed, not assumed baseline

- `com.weibo.talentintroduction.discovery.sample.DiscoverySampleStoreTest.store acceptance cases`, `DiscoverySampleStoreTest.kt:135`: `AssertionFailedError: expected QUOTA but was SAVED`. Source lines 133–135 create a fresh quota directory/store and expect the third failure package to hit QUOTA. This writer did not change this test or SampleStore and did not rerun it merely to confirm. Its origin relative to child10 is **not established** by this invocation.
- Nineteen `com.weibo.talentintroduction.mail.service.ExpertContactLocationServiceTest` errors occur in construction at `ExpertContactLocationServiceTest.kt:38`, which instantiates `ExpertContactLocationCatalog`. Observed cause chain: `IllegalStateException` for country CL timezone `America/Coyhaique`, caused by `java.time.zone.ZoneRulesException: Unknown time-zone ID: America/Coyhaique`. This is the actual required JDK's resolution failure, not an inference that tests passed on a different JDK. No timezone/catalog change or suppression was made.
- Skip distribution: 11 `mysqlIt=false`; six `migrationIt=false`; one exact-original294-raw-corpus assumption; one registered I-46 disabled deviation; one manual Europe-PMC network requirement; one absent `esIt`; three `@EnabledIf=false`. The final selected MySQL seven-class command itself had **zero skips**.
- Required full-suite exit 1 remains a gate-classification question for the independent verifier. Controller explicitly allowed READY_FOR_VERIFICATION, not PASS, without unrelated-file editing or rerunning to confirm. Membership outside the authorized file set is not proof of pre-existing/baseline origin.

### Recovered predecessor and intermediate failures

Recovered from `history://Recheck10Implementer` and bounded JSONL event selection in the private predecessor session; no whole private roster/quote dump. Earlier commands establish historical execution/failure lineage, never replace final fresh gates:

| Observed run / evidence | Actual result and subsequent bounded correction |
|---|---|
| Predecessor bg188 | Kotlin non-null profile compilation failure at Tool line 304; predecessor corrected it. |
| Predecessor bg189 / artifact1350; bg190 / artifact1362 | Nine UNSAFE_PATH errors from macOS `/var` symlink handling; canonical real paths corrected. Subsequent eight unit tests passed; not runtime proof. |
| Predecessor bg192 / artifact1367 | Nine tests / one INVALID_SCHEMA error; explicit metadata schemaVersion corrected. |
| Predecessor bg194 / artifact1398 | 51 tests / one ARCHIVE_DISABLED error in added archived runtime; existing runtime/country/verification/supplement/unit slices passed. |
| Resume bg196 / artifacts1412,1426 | 713 s, ten tests / one task `created_at` NULL error. Tool now supplies required created/updated timestamps; finish/resume timestamps also explicit. |
| Resume bg198 / artifacts1428,1431; bg199 / artifact1438 | 631 s / eleven tests / one assertion error; standalone 27.46 s located fragile Instant string assertion. Corrected to full typed frozen-context equality, not a weakened time-string comparison. |
| Resume bg201 / artifacts1439,1441 | 691 s / 67 tests / WORKER_DISABLED; actual fixture Worker registered before start. |
| Resume bg203 / artifacts1443,1445; bg204 / artifact1449 | 696 s / 67 tests / NoSuchElement; standalone 29.68 s located business-only lookup of an uncommitted recheck. Corrected to history lookup scoped by new run. |
| Resume bg205 / artifact1451; bg206 / artifact1453; bg207 / artifact1458 | Focused archived scenario failed UNKNOWN/PENDING, with missing qualification. A historical professor assertion was not valid evidence for a current role; actual synthetic body changed to completed PhD plus full birth date. Source URL and strict retained-byte precondition were added. These runs were failures, not admissions. |
| Resume bg208 / artifact1460; private `focused-archive-reference.log` | Actual paper fields empty / SOURCE_UNAVAILABLE despite exact saved SHA. Original synthetic `10.fixture/...` DOI violated the production lookup constructor. Corrected fixture to syntactically valid `10.9999/fixture-...`; retained actual parser assertions for education and birth date. |
| Resume bg209 / artifact1467 | 209.38 s; both actual CLI applications exited 0, but strict fixture rejected startup index metadata endpoints. Added exact known index GET/HEAD and validated mapping PUT protocol to fixture, without relaxing unknown-route rejection or allowing profile overwrite. |
| Initial post-fix three/seven/standalone | Passed 11 / 67 / actual standalone respectively, but preceded the final historical-mirror edits; replaced by fresh final gates above. |
| `final-historical-audit-reuse.log` | Exit 2 / 34.48 s. Original SQL snapshot has `response_sha256`; previous identifier regex rejected digits. Corrected to safe identifier grammar `[a-z_][a-z0-9_]*`; bounded diagnostic prints class and source location, never SQL/roster values. |
| `historical-audit-reuse-identifier-fixed.log` | Exit 0 / 592.02 s, but only 443 compatible caches and zero proposed admissions. This is retained **non-faithful mirror evidence**, not the final historical result: domain `externalIds` strings were incorrectly used as ES-wire strings, and report read the wrong source-ref key. |
| Final historical wire correction | Restored externalIds JSON objects to strict fixture ES, matching canonical `discoveryProfile` decoding; read canonical `paperSourceRefs`. New separate output directory preserved prior reports. Real recomputation yielded 897 compatible original caches and the final 21/17 proposal counts below. |

The original source scan comparison also observed runtime variability: prior v1 full compare report hash `4b4...` and resumed same-manifest v1 report hash `4cef5fbea64e3b5f88bae16c0bf1758669e19848af231e1a3592dc555237189f` remained historical PARTIAL outputs. Resume v1 rerun exited 0 in 1386.58 s, but was **not deterministic proof**. The controller-approved epoch-3 factory addresses the actual ten-second wall-clock cutoff without changing ordinary production budgets.

### Historical sequence and bounded results

Sequence actually exercised: structural real-parser fixtures; deployed-class/repaired-class same-SHA negative sample; complete reachable 4472-source / 4487-attempt comparison; fixed 897-person AUDIT/REUSE on disposable local MySQL and strict fixture ES. No human-approved production apply phase was inferred or performed.

1. **Observed original deployed negative sample**: 375 actual saved attempts, zero probe errors, 548 candidate bindings absent from deployed output, zero present. Report SHA `afb2f54568a406437625eccc94fb3d63fb0203f2b1bd20f3881b34cf38f80f4b`; predecessor actual probe took about 846 s. This is a source-binding negative sample, not 548 new people or admissions.
2. **Observed history coverage prerequisite**: predecessor read-only ES scan covered 4,439,077 documents, COMPLETE; scan SHA `52a598a389069eb16f627a0ed4ab540dc7675b9df5a63f04574eba63eef7f544`. Relevant history 2212 documents collapsed to 1608 expert identities; all 897 pending identities bound; 26081 admission revisions read. No inferred coverage from a small fixture.
3. **Old bound v1 report preserved**: 4472 papers / 4487 readable attempts / 3904 complete attempts / 1608 historical experts / 897 pending experts; **PARTIAL**, including SOFT_TIME_LIMIT. Baseline/reparsed emails 6840/6815; baseline/proof bindings 1277/1823; qualification facts 37. Complete new/gain counts were null; actual new RAW/admission/sync all zero.
4. **New version-2 deterministic work-bounded full compare**: remote freeze exited 0 in 93.19 s; two actual compare JVMs together exited 0 in 2660.90 s, same manifest hash `973f2c38092625501d94298489fbf19039d06d4da7f10637138c3c83e5b4b76e` and same report hash **`de0f3414bf791bed320a11bccd46223bdce868024933d94e1014ab33924096a9`**. Both genuinely recomputed all 4487 inputs. Final local class hashes match every implementation hash in this actual remote frozen manifest.
   - Denominators: papers 4472; attempts/readAttempts 4487/4487; completeAttempts **3907**; historicalExperts 1608; pendingExperts 897.
   - Coverage stays **PARTIAL**: BASELINE_BODIES_MISSING (564 rows), PAGE_LIMIT (17), EMAIL_PARSE_ERROR (one), PARSE_ERROR (one); reason memberships may overlap. No SOFT_TIME_LIMIT in this mode.
   - BaselineEmails 6840; reparsedEmails 6815; baselineBindings 1277; reparsedProofBindings 1823; qualificationFacts 37.
   - Observed set sizes only: email intersection 6810, added five, removed 30; binding added 548, removed two; existing-person evidence 39; bindings with existing email 50; potential-new-RAW bindings 498; conflicting emails zero.
   - Public complete newEmails/newBindings/existingPersonEvidence/potentialNewRaw/wouldAdmit remain **null**, not zero or certified gains. Actual newRaw/actualAdmitted/candidateSync remain **0/0/0**.
5. **Final faithful 897 AUDIT/REUSE**: input SHA `fff225116cfb7f2672ad1c593265e623658b79f4af30fa6c698e32b9f2e534cc`; evaluation time `2026-10-24T12:16:43Z`; nine newly prepared audit runs; 897 requested/processed/completed; 897 compatible original frozen caches. Fixed source references: 882 people have one, 15 have none. No synthetic proof or precomputed proposed decision was inserted.
   - Final proposal outcomes: PENDING 876, DIRECT_CLEAR 21; qualification UNKNOWN 844, MET 21, CONFLICT 32.
   - Before/proposed matrix: PENDING→PENDING 876; PENDING→DIRECT_CLEAR **17**; original DIRECT_CLEAR→DIRECT_CLEAR **4**. Thus 21 would-admit includes four already-positive cases; it is not 21 new admissions.
   - Reason memberships: SEARCH_INCOMPLETE 612, QUALIFICATION_MET 21, APOLLO_NO_MATCH 191, IDENTITY_NOT_MATCHED 37, NAME_CONFLICT 32, QUALIFICATION_EVIDENCE_MISSING four.
   - Actual admitted **0**, all audit admissionRevision values null, provider HTTP **0**, existing business snapshots unchanged; original review-epoch-zero items and original verification attempts byte-for-row unchanged. Candidate sync production **0**.
   - Final private report SHA **`e41e4959b46fb4458121b9991dd3dc095c953f2d3642985aa16eaa1f9dc6613e`**.

Remote comparison commands used the actual Java 11 executable `/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64/bin/java`, classpath `/tmp/discovery-child10-readonly/fixed-classes-v2.jar:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/lib/*`, Tool `freeze` with existing `bound-source-scope-v2.json` into `work-bounded-frozen-v2`, then Tool `compare --manifest /tmp/discovery-child10-readonly/work-bounded-frozen-v2/manifest.json --manifest-hash 973f2c38092625501d94298489fbf19039d06d4da7f10637138c3c83e5b4b76e` twice. Only protected audit files were written remotely; deployed business state was not applied.

### Private evidence reachability, protection, cleanup, and limits

- `$PRIVATE` is the actually observed `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/discovery-child10-he59d110`, mode 0700. Required input, manifests, comparison, final historical report, and new execution logs are mode 0600. No private full names/emails/quotes/rosters/credentials are included in this report or product commit.
- Retained original sources: `pending-archive/blobs`, 475 distinct SHA-verified body files, 1,038,740,867 bytes; source kinds 450 PDF_URL and 25 PMC_XML. Input SQL snapshot rows 14357: task one, run one, items 965, verification attempts 2907, admissions 965, archive records 9518. No production restoration was performed; these rows were restored only into a disposable MySQL container.
- Protected local evidence: `work-bounded-manifest-v2.json` (16,039,706 bytes, manifest hash above); `work-bounded-compare-v2.json` (10,626,576 bytes, report hash above); `pending-audit-input.json` (hash above); `audit-reuse-wire-faithful/audit-reuse.json` (final hash above); `audit-reuse-final/audit-reuse.json` (superseded non-faithful mirror retained, SHA `405a3fc5976118a5b7532fd65f316d66643970afad7c6618869fc582d63beb6f`).
- Additional preserved hashes: `source-scope.json` `676acb3806e447446e0d5896f91b2d3e7d2155bfbc6a800885086c961b963d93`; `relevant-history.json` `8184e0117c2e5006989e0977b98425827203cb7f2b13f7783199f563a73e552d`; `sql-snapshot.jsonl` `f2bdbd25f39f64ded7eedd7e5238ae28b770080c5491799cbffaa6f0ce7291f8`; recovered old manifest `2ce8ed1154cf87ba1cf2112530701f1ab48f28f39fc456180ef6ac33d058f13d`; old compare `2dd490e7a7639b2c792c8497f539fea781b1c209022a36adafd295016435b374`.
- Remote preserved directory: `/tmp/discovery-child10-readonly/` on the previously established read-only audit host, including deployed probe, original/frozen/history snapshots, work-bounded manifest/report, and class bundles. Historical source directory `/tmp/paper-full-28671-51yu5bt6` was not deleted.
- Recovery transcript: `history://Recheck10Implementer`; raw session `/Users/lukai/.omp/agent/sessions/-IdeaProjects-weibo-talent-introduction/2026-10-23T14-16-47-496Z_01a1691f-1688-74c7-ae20-59de75f1e0bd/Recheck10Implementer.jsonl`. Bounded tool-event selection was used; archived raw private material remains private.
- Cleanup: isolated synthetic runtime directories self-delete; disposable MySQL containers close; no throwaway source scripts/docs were added to the repository. Existing private `BindHistory.java`, `SameShaProbe.java`, other historical probes, original source bodies/reports, and the protected classpath/class bundle remain archived for independent reproducibility, not production scaffolding. Existing probe file permissions were tightened to 0600. No historical evidence, user data, original edits, or fast-p placeholders were deleted.
- Evidence-handling incident: an intermediate worker tool/session output accidentally included private historical email intersections when an object was printed instead of lengths. Controller attempted redaction; the artifact device reported read-only, and no mounted redaction capability was available. No credentials or Git/product files were involved. This is an unresolved session-log handling limitation, not a claim of erased output. Subsequent/report extraction is aggregate length/hash-only; the raw incident output is not reproduced or cited here.
- Limitations: full-source report is genuinely PARTIAL; 15 historical people have no fixed paper reference; the separate exact-original294 corpus test skipped for missing inputs. The actual faithful audit remains AUDIT/REUSE with no external refresh, not an assertion that every person is qualified. Human review of private source-page diffs and explicit production application remain unperformed. No release, push, merge, production apply, paid call, or automatic rollout was attempted.
- LSP device was actually unavailable in predecessor recovery (`No such tool`); actual mounted tool inventory did not expose it. Literal source search/read was used, not a claim of LSP analysis.

### Freshness and next action

- Plan identity unchanged: YES; exact epoch-3 nine-file scope used.
- Target worktree identity unchanged and product SHA reachable from target branch: YES.
- Final targeted gates, actual standalone CLI, actual faithful historical worker replay, full JVM test, package/bound JS, and script syntax were exercised in this writer invocation after their relevant final edits. Historical predecessor results are explicitly separated.
- Remaining implementation blocker: none identified within authorized scope. Required full-suite failure and privacy-log limitation remain honestly recorded; no overall PASS claim.
- Next action: controller hands this exact product SHA and protected inputs to a **fresh independent verifier**. This writer did not run verify-p/fix-v, an aggregate independent review, or change ledger/verify-log/fix-log.

## Controller closing record — Epoch3
- Independent Recheck10Verifier returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD on306664e25738d78e2587e175c05bb0724e778d71..0d60b43e586584319b78c4f3f9ca86b4820dd24f; allfourlightgatesPASS, noAUTO_FIX, actualrepair0. Fresh3class11/7class67zeroskips, actualCLI/sharedpipeline, genuinefullsourcecomparetwice/samehash/PARTIAL3907of4487attempts, faithful897AUDITactual0/provider0, package/JS1840/WAR passed. FullmvntestremainsRED5622/1failure/19errors/24skips; O-10-1/2 boundedcausalevidenceshowsunrelatedquota-clock/timezonepaths, notoveralltestPASS. O-10-3 coverage/skips andO-10-4 privateoutputincidentretained. Noaggregatecontrollerproductreview, productionapply orhumanacceptanceclaimed.
