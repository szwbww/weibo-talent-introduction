# Child 06 independent light verification log


## Light Verification: LIGHT_FAIL
Child: 06 — docs/plans/2026-10-18/apollo-06-reporting.md
Boundary: 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848..096d21504bcfbaea321bd2b535ee6c62beef31a8
Verifier: ApolloReportingVerifierE2 (fresh independent verifier; not either child06 writer)
Epoch: 2
Attempt: 1
Fix round at review: 0
Timestamp: 2026-10-19T00:22:28.292130+08:00
Approved plan identity: commit:f9435ee70c585a158b3a3414c3bf549c35c7044b (A7/A8, including the approved CONFIG_AUTH seam)
Implementation retained: bcc031775d112e1d67f20441e1c321d4b6a08967; completion: 096d21504bcfbaea321bd2b535ee6c62beef31a8.
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Branch: fast/apollo-verification-master
Review scope: child06 explicit requirements, C-5/C-3 reporting requirements, required C-7 commands, and the child07 API/DTO handoff only. No broad/final architecture, security, performance, or full-verification review. EXPLAIN is included only because child06 expressly requires it.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary `git diff --name-only ... -- src/main src/test` returns the nine authorized paths listed below. Worker diff is only the three-line current-latch getter; its test addition is observational. Plan/evidence changes in the overall boundary are controller artifacts, not extra product files. |
| Plan and invariants | FAIL | Fresh F123, history/null/zero/error, read-only, snapshot, fixture, and CONFIG_AUTH checks pass as detailed below, but F06-01 violates the required downstream Apollo-assisted filter semantics and F06-02 omits required Apollo identity/stage detail facts. |
| Required commands | PASS | Fresh JDK11 Maven exit0, 238 tests, 0 failures/errors/skips; all three new reporting suites actually ran, reporting IT 8/8 against disposable MySQL8. Focused Node70/70 and syntax exit0; full Node exit1,1694/1653pass/41fail exactly matching the supplied ledger baseline, not represented as green. Fresh actual embedded Tomcat HTTP/MySQL smoke exit0, both provider401/403 cases included. |
| Downstream interfaces | FAIL | C-5 endpoint names, parameters and fixed record/count keys are present. However `route=APOLLO` excludes the required pending/error/queued cohort (07 S-6:67), and item DTO cannot supply the matched Apollo identity/stages required by the authoritative 07 S-7 DOM (:535). Both are directly reproduced with the real read endpoints. |

### AUTO_FIX
- **F06-01 (P1) — Include every Apollo-entered item in the APOLLO filter.** Governing requirements: child06 I-1/I-3 and the child07 handoff, 07 S-6:67 explicitly includes pending/error/queued participation using apolloEntered rather than only the admitted40. Patch evidence: `DiscoveryVerificationRepository.kt:119-132`, especially :122, translates every non-ALL route to equality on `i.route`. Existing worker ERROR uses route NONE (`DiscoveryVerificationWorker.kt:126-131`); rules default pending to NONE (`DiscoveryVerificationRules.kt:37-39,77-83`). F123 similarly persists NONE for non-admitted participants while setting apollo_entered_at. Real HTTP returned APOLLO/ALL40, APOLLO/PENDING0, APOLLO/ERROR0, APOLLO/QUEUED0; expected108/45/5/18. Runtime receipt `artifact://401:270-273`. The reporting IT currently reproduces the erroneous equality in its expected cohort at :153 rather than protecting the approved semantics. Smallest authorized correction: branch APOLLO filtering on the existing non-null entered marker, keep DIRECT as the direct-admission route, preserve historical outcome/route values, and correct the authorized reporting IT to independently assert108 and the45/5/18 intersections plus batch-specific cohorts. Files: existing authorized repository and reporting IT only. No new state or changed worker policy.
- **F06-02 (P2) — Project the matched Apollo identifier and existing stage/event facts into item detail.** Governing requirement: downstream07 S-7 authoritative evidence DOM, `apollo-07-frontend.md:535`, explicitly displays `apolloPersonIdAndStages`; child06 supplies the read-only evidence API. Patch evidence: `DiscoveryVerificationQueryService.kt:93-99,180-185` never projects `item.apolloPersonId` or its entered/searched/candidate/matched timestamps, and returns only `payload.evidence`, not the typed search/progress facts. The other new DTOs do not expose that identifier either. Existing worker persists the confirmed ID at `DiscoveryVerificationWorker.kt:120-122`, domain fields at `DiscoveryVerification.kt:62-63`. A fresh real successful worker stored person-1; actual HTTP item JSON contained no person-1 (`artifact://401:277`). Thus child07 cannot render this approved identity evidence from any provided endpoint; missing data would not mean evidence was unavailable. Smallest authorized correction: expose the already persisted matched identifier and bounded, typed existing stage/event facts in the authorized item-detail projection and assert them with the existing authorized query/reporting tests; do not return raw request/response JSON, add provider calls, infer new facts, or change worker state. No unlisted dependency or caller amendment is needed.

### RECORD_ONLY
- **O06-01 — Existing full Node baseline failures remain.** Fresh `node --test src/test/js/*.test.js` exited1:1694 total,1653pass,41fail,0skipped. Ledger Baseline:27 records the same1694/1653/41. Failures include resetRecipientPreview/setRecipientPreviewState extraction-sandbox references and the meeting-confirmation asset token contract. No frontend path is changed in this boundary; no repair or blanket PASS is claimed. Fresh receipt `artifact://398:2349-2363`.
- **O06-02 — Known53 ExpertDiscoveryServiceTest fixture errors remain closed-child05 context.** The dispatch/brief explicitly attributes them to prior fixture overload changes and forbids confirmation-only reruns or repairs. That class was not selected, no old result is counted as a fresh pass, and no related product/test change was made.

### Exact boundary and authority receipts
Executed in the bound worktree:
```sh
git diff --stat 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 096d21504bcfbaea321bd2b535ee6c62beef31a8
git diff 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 096d21504bcfbaea321bd2b535ee6c62beef31a8 -- src/main src/test
git log -1 --format=%H\ %D
git diff --name-only 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 096d21504bcfbaea321bd2b535ee6c62beef31a8 -- src/main src/test
git show f9435ee70c585a158b3a3414c3bf549c35c7044b:docs/plans/2026-10-18/apollo-06-reporting.md
```
All exit0. HEAD receipt:096d21504bcfbaea321bd2b535ee6c62beef31a8, HEAD -> fast/apollo-verification-master. Scope output is exactly:
```text
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationController.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt
src/test/resources/discovery/apollo-verification-manual-fixture.sql
```
Full modified-file context read; the existing worker consumer/tick and rules routing were traced outside the changed getter. Child brief, child06, normative contracts and master were read completely; downstream07 S-1–S-8/API data consumers were consulted, not implemented or reviewed visually. `fix-log.md` contains only its header; there is no closed06 fix decision overriding these findings.

Fresh byte comparison command, exit0:
```sh
python3 -c "import pathlib,hashlib,subprocess; files=[\"docs/plans/2026-10-18/apollo-06-reporting.md\",\"docs/plans/2026-10-18/apollo-verification-master.md\"]; [(print(p,\"sha256=\"+hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest(),\"matchesApproved=\"+str(pathlib.Path(p).read_bytes()==subprocess.check_output([\"git\",\"show\",\"f9435ee70c585a158b3a3414c3bf549c35c7044b:\"+p])))) for p in files]"
```
Child SHA256=1c35f5104521eefd40de8677dc8118a2aa7f8a695bd88d91b4166a0848f4df47; master SHA256=52e6b7cf5551d4b3eb004e5d23669fa0c7657a355cc9ea468d182c3b01c0e85a. Both `matchesApproved=True` against commitf9435ee. No authority expansion is requested by these two corrections.

### Fresh mandatory commands and results
**Backend — exact command, exit0** (`artifact://396`; elapsed312.97s):
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationQueryServiceTest,DiscoveryVerificationControllerTest,DiscoveryVerificationReportingIT,DiscoveryVerificationIntegrationTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryPipelineServiceTest,DiscoveryVerificationContractTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryVerificationRulesTest,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,OperatorStatusWriteSeamGuardTest
```
Fresh selected Surefire text reports were read after this command completed (not stale reports or writer testimony):
| Suite | Tests | Failures/errors/skips |
|---|---:|---|
| DiscoveryVerificationQueryServiceTest | 5 | 0/0/0 |
| DiscoveryVerificationControllerTest | 4 | 0/0/0 |
| DiscoveryVerificationReportingIT | 8 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationIntegrationTest | 10 | 0/0/0; actual disposable MySQL8 |
| ExpertDiscoveryControllerMvcTest | 4 | 0/0/0 |
| ExpertDiscoveryControllerTest | 20 | 0/0/0 |
| ExpertDiscoverySchedulerTest | 32 | 0/0/0 |
| DiscoveryPipelineServiceTest | 49 | 0/0/0 |
| DiscoveryVerificationContractTest | 5 | 0/0/0 |
| DiscoveryVerificationRepositoryIT | 8 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationAdmissionGateTest | 6 | 0/0/0 |
| DiscoveryVerificationCommitServiceTest | 4 | 0/0/0 |
| DiscoveryVerificationWorkerTest | 4 | 0/0/0 |
| DiscoveryVerificationFlowIT | 11 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationRulesTest | 7 | 0/0/0 |
| DiscoveryAdmissionPolicyTest | 22 | 0/0/0 |
| DiscoveryReviewServiceTest | 38 | 0/0/0 |
| OperatorStatusWriteSeamGuardTest | 1 | 0/0/0 |
| **Total** | **238** | **0/0/0** |
The query/controller unit suites use mocks/MockMvc, not a claimed real-server substitute. The new reporting IT actually starts isolated MySQL8; zero Docker skips. Fresh real HTTP is separately documented below.

**Focused frontend and syntax — exact command, exit0** (jobbg_82):
```sh
node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js
```
70tests,70pass,0fail/skipped; syntax0.

**Full frontend — exact command, exit1** (`artifact://398`):
```sh
node --test src/test/js/*.test.js
```
1694tests,1653pass,41fail,0cancelled/skipped/todo. Baseline-classified, not a passing command.

The future child07 `src/test/js/discoveryVerification.test.js` is not present and was not run or counted. Browser1366/1920/390 validation belongs to07 and is not claimed. Optional legacy shared-database DiscoveryPaperQueueRepositoryIT/DiscoveryReviewRepositoryIT were excluded, not passed/skipped-as-pass; no shared/production DB was accessed. No baseline-confirmation-only ExpertDiscoveryServiceTest run, no fullverify/review/fix-v loop, and no completed child was reopened.

### Direct requirement and invariant evidence
| Requirement | Direct code/test/runtime evidence and result |
|---|---|
| Activated-only denominator and registration errors | Repository :36-77 conditionally counts activated rows, separately registrationErrors; reporting IT :164-190 adds unactivated ERROR and retains profiles123. PASS. |
| Committed ENFORCE auto events, separate qualification/route, AUDIT suggestions | Repository :42-53 checks ENFORCE plus admission_revision; :80-96 reads AUDIT suggestions. Reporting IT :164-180 checks actualauto0/suggested1, and projection error independence; unit test covers batched AUDIT summary. PASS. |
| Exact F123 and batches | Fresh IT :76-86,137-161 passes123/55/15/40/36/19/45/5/18 and batches40/40/43. Batch principal rows are40/20/6/14/14/6/13/2/5,40/18/5/13/12/6/15/1/6,43/17/4/13/10/7/17/2/7. Columns profiles/auto/direct/apollo/clear/unclear/pending/errors/queued. PASS unfiltered. APOLLO-filter expectation is wrong as F06-01, not waived by green tests. |
| Funnel and actual APOLLO attempts | Same IT asserts entered108/searched85/candidates66/matched40/attempts108; only APOLLO attempt rows counted, not ORCID calls. PASS. |
| All result/route/batch combinations, stable IDs, exact total before paging | IT :137-161 enumerates ALL/DIRECT/APOLLO crossed with seven results and null/1/2/3 batch filters; id-sorted pages and filtered reasons checked. Result/batch/paging pass, but route cohort oracle mirrors the defect; independent HTTP disproves the required Apollo-assisted semantics. FAIL F06-01. |
| Full reason objects, unknown-code preservation | Query :130-156 and reporting IT :157-161,193-216; four reasons retained, unknown APV-FIXTURE-REASON-4 label preserved verbatim, counts de-duplicated per matching item. PASS. |
| NOT_RECORDED/null vs RECORDED/0 vs503 | Query :40-83; controller :43-61; all three new suites and actual HTTP cover legacy3002, empty3003, invalid persisted data/storage failure503. PASS. |
| Current admission/HOLD and historical auto | Query :79-82,92-99 uses batched MySQL current admissions. IT :164-169 preserves auto55 while currentHOLD/revision2 appears; real HTTP reproduces. ProjectionErrors increments independently. PASS. |
| Repeatable-read single-request snapshot and run revision | Query class :19-21; IT :250-275 concurrent writer commits changes to run revision, outcome, admission, while read snapshot keeps all old values until next transaction; next transaction seesrevision124/auto54. PASS. |
| Escaped, bound LIKE; capped page; batched admission reads | Repository :113-143; IT :277-308 tests literal percent/underscore/backslash/exclamation, full name, injection-shaped q, size100 and far-offset empty page; admission IN reads bounded to100 parameters. PASS. |
| C-5 routes/validation/401 user auth/404/503 | Controller :13-69; controller tests :40-92 run existing AuthInterceptor on all five endpoints, reject bad IDs/enums/pages/query length/batch number before SQL, item404/storage503/no-secret. PASS. Provider401/403 are distinct and tested below. |
| Lifecycle and task retention | Query :110-115 derives ACTIVE/COLLECTING/COMPLETED independently from original task status; filtered empty/CLEAR pages retain entire-run ACTIVE. IT :224-247 and actual HTTP retain ledger after task deletion with EXPIRED. No original task semantics or cleanup policy changed. PASS. |
| GET zero writes/external replay and no raw-response secrets | Query :23-27 only SQL/config/current-latch reader dependencies; IT :193-222 verifies all five reads20 cycles, DB content stable, no mutating JDBC call, typed source timestamps and attempt summary omit request/response JSON. Real HTTP repeats every endpoint20 cycles with unchanged database; raw-response sentinel never reaches body. PASS. |
| CONFIG_AUTH current process latch | Worker :47-48 getter reads AtomicBoolean only; query :35 consumes optional provider. Worker4 tests and reporting8 tests pass. Actual real clients/worker/MySQL cause401 AND403 ERROR/CONFIG_AUTH, real HTTP capabilities20 reads each saysreadyfalse; no further claims/attempts/provider requests or table changes. New worker unblocked despite historical failures. No reset/retry/historical inference or worker policy change. PASS. |
| Guarded manual fixture, fixture-only IDs, idempotence | SQL :27-115 has suffix/occupied-namespace checks, transactions and APV-FIXTURE ownership. Fresh reporting IT :224-248 executes correct import twice, occupied nonfixture rejection with unchanged DB, and wrong-DB rejection without tables. Runtime imports twice. PASS. |
| Read-only evidence detail includes source/type/time/identity clues | Query :86-100,DTO :180-185 preserves sourceUrl/fetchedAt/sourceUpdatedAt, original identity/employment/institution clues, qualification/outcome, current admission/revision and projection. PASS for C-5 listed facts; required downstream Apollo matched ID/stage display missing, F06-02. |

### Required real EXPLAIN, not a general performance audit
Fresh `artifact://396:274-275`, query matching the production reporting page:
```sql
SELECT i.* FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch)
WHERE i.owner_execution_id=? AND i.activated_at IS NOT NULL ORDER BY i.id LIMIT 100 OFFSET 0
```
- Owner3001, actual123 rows: type=ref; key=idx_dvi_run_batch; key_len8; refconst; estimate123; filtered90; Using where/Using filesort.
- Owner5001, actual10000 rows: type=ref; key=idx_dvi_run_batch; key_len8; refconst; estimate4689; filtered90; Using where/Using filesort.
- Actual returned page100 in both cases; exact totals123/10000; no more than100 admission IDs in the bulk query. No unmeasured timing or general performance guarantee is made.

### Actual embedded HTTP plus isolated MySQL smoke
A verifier-owned disposable Java driver, outside the repository, booted actual embedded Tomcat9 at127.0.0.1/random port with Spring MVC production controller, transactional query proxy, production repositories, and fresh Testcontainers `mysql:8.0.36` database `independent_verification_fixture`, migrated throughV158. The actual data sources were container URLs, never application.yml/default shared database. Provider/ES transports used existing VerificationFlowFixture mock servers; real production clients/worker and MySQL transaction paths ran. No actual Apollo/ORCID/ES provider was contacted. This minimal listener does not claim full production login bootstrap; existing AuthInterceptor is freshly covered by permanent controller tests.

Runtime invocations were performed by an inline `python3 -c` runner using only tempfile source/classes outside the worktree. CP was taken from the selected Surefire XML `java.class.path`, using Python ElementTree, not another Maven build or old writer driver. Exact Java argument vectors (CP denotes that property value):
```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp CP /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo06-independent-o9pjvx0p/Independent06.java
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.44 -cp /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo06-independent-o9pjvx0p:CP Independent06 src/test/resources/discovery/apollo-verification-manual-fixture.sql
```
Both exit0; runner exit0; full runtime receipt `artifact://401` (37.74s), including launch, isolated URL, all checked observations, shutdown and temporary-source removal. Initial harness attempt `artifact://400` exited1 before migrations because its JDBC URL appended ampersand before the first query separator; the disposable verifier harness was corrected to choose question mark/ampersand appropriately, then the entire smoke ran freshly. No product fix or pass was inferred from that harness error.

Actual HTTP observations in final successful run:
- F123 summary principal counts123/55/15/40/36/19/45/5/18; legacy countsnull; empty0; batches3 with40/40/43.
- UNCLEAR19/PENDING45; currentHOLD visible on historical admitted first item; source URL/time and all four reasons visible in detail.
- Item404, size101400, invalid route400; no raw response sentinel or configured secret in responses.
- Each of all five GET endpoints repeated20 times (100 actual reads), complete task/admission/run/item/attempt table content unchanged.
- Independent route diagnostic actual40/0/0/0, proving F06-01 rather than copying the IT oracle.
- Actual provider401 through real client/worker -> ERROR/CONFIG_AUTH ->20 HTTP capability reads readyfalse/CONFIG_AUTH; interleaved ticks leave DB, attempts, Apollo/ORCID request counts unchanged; Apollo requests1. Repeated independently for403 with the same results. Current-worker accessor did not reset/resume; reconstructed worker not blocked by old attempts.
- Actual successful matched worker persisted person-1, but item HTTP response omitted the identifier: F06-02.
- Deleted only synthetic task3001: retained ledger reports EXPIRED. Stopped only this disposable MySQL: actual HTTP503, not200 zeros.
- Final receipt `INDEPENDENT06_HTTP_MYSQL_OK F123=123/55/15/40/36/19/45/5/18 legacy=null empty=0 import=2 EXPIRED=true dbDown=503 routeDefect=F06-01`.
- Context/container closed; temporary Java source/classes and Tomcat directory removed by finally blocks. No repository scaffold, product/test edit, index operation, branch change, or commit was performed by the verifier. Only this report is appended.

### Verdict summary
Fresh execution validates the approved current-auth seam and most06 requirements, but does not satisfy the exact downstream07 read contract. Findings F06-01/F06-02 have narrowly defined corrections entirely inside the already authorized nine-file scope. No PAUSE prerequisite or new authority is needed; controller should use the bounded repair path, not advance07 or perform broad cleanup. Writer execution reports and old test counts were used only as context; all passing claims above are fresh verifier observations.

### Required Action
- AUTO_FIX


## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 06 — docs/plans/2026-10-18/apollo-06-reporting.md
Boundary: 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848..cd105de17d7100466543675727a68fe1370008cb
Verifier: ApolloReportingReverifierE2R1
Epoch: 2
Attempt: 2
Fix round at review: 1
Timestamp: 2026-10-19T00:48:00.859269+08:00
Approved plan identity: commit:f9435ee70c585a158b3a3414c3bf549c35c7044b (A7/A8).
Implementation completion: 096d21504bcfbaea321bd2b535ee6c62beef31a8; repair1: cd105de17d7100466543675727a68fe1370008cb.
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Branch: fast/apollo-verification-master
Independence: this named verifier is distinct from ApolloReportingImplementer, ApolloReportingImplementerE2 and ApolloReportingFixerE2R1, and from the attempt1 verifier. Prior execution/verification/fix reports were context, not acceptance proof. This report appends to the historical attempt1 report without replacing its findings or action. The action at the end of this attempt2 report is its single current action block.
Scope: four light gates only; all explicit06 requirements, normative C-3/C-5/C-6/C-7 requirements relevant to reporting, and downstream07 API/DTO handoff. No broad/aggregate/final review, no full verification skills, no later-child implementation, no production or shared database/provider access.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact base..repair1 diff returns the nine authorized product/test paths below. Repair1 changes only repository, query DTO/projection and reporting IT. Worker delta across the whole boundary is only the current-latch getter plus its observational test. Live product paths match review HEAD and index is empty. |
| Plan and invariants | PASS | Direct requirement matrix below; fresh239 backend tests, actual isolated MySQL including9 reporting IT tests, and fresh authenticated TCP HTTP/MySQL runtime. F06-01 and F06-02 independently CLOSED; both current provider401/403 auth-latch cases remain read-only and fail-ready. No business behavior was expanded. |
| Required commands | PASS | Fresh named JDK11 Maven command exit0,239 tests,0 failures/errors/skips. Focused Node70/70 and syntax exit0. Full Node exit1 with exactly1694 total/1653pass/41fail/0skipped, classified against ledger baseline rather than called green. Fresh JDK11 runtime compile and final execution exit0; mandatory fixture guard,123/10000-row EXPLAIN and REPEATABLE_READ snapshot checks really executed. |
| Downstream interfaces | PASS | C-5 endpoint names, validation, fixed count/record keys, null/zero/503 and snapshot semantics preserved.07 S-6:67 now receives APOLLO participation108/45/5/18 and DIRECT15;07 S-7:535 can consume typed apolloPersonId plus apolloStages enteredAt/searchedAt/candidateFoundAt/matchedAt from existing persisted facts. No07 files changed or browser acceptance claimed. |

### AUTO_FIX
- N/A. No open proven four-gate violation remains.

### RECORD_ONLY
- O06-01 — Existing full Node baseline failures remain: fresh `node --test src/test/js/*.test.js` exit1,1694 total/1653pass/41fail/0skipped (`artifact://426:2350-2357`). Ledger Baseline:27 records the same totals and recipient-preview extraction-sandbox/meeting-confirmation asset failures. No frontend product/test path is changed by this boundary; no unrelated fix and no global-green claim.
- O06-02 — Known53 ExpertDiscoveryServiceTest fixture errors are closed-child05 context supplied by the dispatch, not a new06 issue. That class was not selected or rerun merely to confirm the known failure; none of its old results is counted as fresh acceptance. Optional shared-database ITs were excluded, not passed or counted as Docker skips.

### Preserved findings — independent dispositions
| Finding | Status | Fresh evidence |
|---|---|---|
| F06-01 — Apollo participation filter (prior P1) | CLOSED | Repository:119-125 explicitly dispatches APOLLO to non-null apollo_entered_at and DIRECT to the existing route predicate; ALL remains unfiltered. The same where-builder is used by total/page/count/reason reads. Fresh reporting IT:171-209 exercises every route/result/batch combination and independently asserts APOLLO ALL108/PENDING45/ERROR5/QUEUED18. Actual authenticated HTTP reproduces108/45/5/18, DIRECT15 and unchanged aggregate Apollo-admitted40 (`artifact://434:271`). The original failure and its prior receipt remain in attempt1 above. |
| F06-02 — Matched identity/stages missing from detail (prior P2) | CLOSED | Query:86-102 and DTO:182-190 copy existing item.apolloPersonId and existing entered/searched/candidate/matched timestamps to typed fields, without a new query or raw-response serialization. Fresh reporting IT:134-168 drives a real successful worker, checks persisted person-1 and all stages, and checks the HTTP detail. Fresh actual authenticated TCP HTTP compares person-1 and all four timestamps exactly with the persisted item over20 reads, with no data writes or provider replay (`artifact://434:273`). The original missing-field finding remains preserved in attempt1. |

### Boundary, scope and authority receipts
Fresh commands, all exit0, executed in the bound worktree:
```sh
git diff --stat 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 cd105de17d7100466543675727a68fe1370008cb
git diff 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 cd105de17d7100466543675727a68fe1370008cb -- src/main
git log -1 --format=%H\ %D
git diff --name-only 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 cd105de17d7100466543675727a68fe1370008cb -- src/main src/test
git diff 096d21504bcfbaea321bd2b535ee6c62beef31a8 cd105de17d7100466543675727a68fe1370008cb -- src/main src/test
git diff f9435ee70c585a158b3a3414c3bf549c35c7044b -- docs/plans/2026-10-18/apollo-06-reporting.md docs/plans/2026-10-18/apollo-verification-master.md docs/plans/2026-10-18/apollo-contracts.md
git show --format=fuller --no-patch cd105de17d7100466543675727a68fe1370008cb
git diff HEAD -- src/main src/test
git diff --cached --name-only
git diff --name-only cd105de17d7100466543675727a68fe1370008cb -- src/main src/test
```
HEAD receipt: cd105de17d7100466543675727a68fe1370008cb, HEAD -> fast/apollo-verification-master. The approved-plan diff and the three live boundary/index checks produce no output. Nine-path scope receipt (`artifact://428`):
```text
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationController.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt
src/test/resources/discovery/apollo-verification-manual-fixture.sql
```
All nine modified files were read with their surrounding context, as were the complete brief,06 plan,contracts,master and ledger, plus relevant downstream07 S-6/S-7 and prior verify/fix/execution evidence. Consumer tracing covered controller parameter dispatch into repository filtering, existing worker state/latch and persisted-stage writers, and Jackson serialization of the new detail fields. The branch contains controller plan/evidence artifacts in addition to product paths; these are not extra product scope. No new authority or amendment is necessary, and none was created.

### Fresh required commands
Backend command, exit0,339.96s, raw receipt `artifact://424`:
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationQueryServiceTest,DiscoveryVerificationControllerTest,DiscoveryVerificationReportingIT,DiscoveryVerificationIntegrationTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryPipelineServiceTest,DiscoveryVerificationContractTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryVerificationRulesTest,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,OperatorStatusWriteSeamGuardTest
```
Fresh Surefire reports were read after completion, not inferred from writer counts:
| Suite | Tests | Failures/errors/skips |
|---|---:|---|
| DiscoveryVerificationQueryServiceTest | 5 | 0/0/0 |
| DiscoveryVerificationControllerTest | 4 | 0/0/0 |
| DiscoveryVerificationReportingIT | 9 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationIntegrationTest | 10 | 0/0/0; actual disposable MySQL8 |
| ExpertDiscoveryControllerMvcTest | 4 | 0/0/0 |
| ExpertDiscoveryControllerTest | 20 | 0/0/0 |
| ExpertDiscoverySchedulerTest | 32 | 0/0/0 |
| DiscoveryPipelineServiceTest | 49 | 0/0/0 |
| DiscoveryVerificationContractTest | 5 | 0/0/0 |
| DiscoveryVerificationRepositoryIT | 8 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationAdmissionGateTest | 6 | 0/0/0 |
| DiscoveryVerificationCommitServiceTest | 4 | 0/0/0 |
| DiscoveryVerificationWorkerTest | 4 | 0/0/0 |
| DiscoveryVerificationFlowIT | 11 | 0/0/0; actual disposable MySQL8 |
| DiscoveryVerificationRulesTest | 7 | 0/0/0 |
| DiscoveryAdmissionPolicyTest | 22 | 0/0/0 |
| DiscoveryReviewServiceTest | 38 | 0/0/0 |
| OperatorStatusWriteSeamGuardTest | 1 | 0/0/0 |
| Total | 239 | 0/0/0 |
All three new reporting classes actually executed; query/controller unit suites use mocks/MockMvc, while reporting IT supplies actual isolated MySQL. The fresh actual TCP listener described below separately verifies authenticated HTTP behavior, rather than labeling MockMvc a network runtime. Reporting DB launch receipt: `jdbc:mysql://localhost:32921/reporting_verification_fixture`, container62c55ba5c2324fdcdc155bdfe8a1547bb8ee43aabb4f5338705dba302bb3432c; JDK11.0.15; migrations throughV158 (`artifact://424:24-45,272-275`).

Focused frontend plus syntax, exit0,70 tests/70pass/0fail/0skips:
```sh
node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js
```
Full frontend, exit1,1694 tests/1653pass/41fail/0cancelled/0skipped/0todo (`artifact://426:2350-2357`):
```sh
node --test src/test/js/*.test.js
```
The future07 discoveryVerification.test.js is absent (fresh glob receipt); C-7 forbids running nonexistent future classes. No missing future test is counted as a pass. Browser1366/1920/390/real DOM/CSS acceptance belongs to07 and is not claimed by this backend child. Optional shared-DB DiscoveryPaperQueueRepositoryIT and DiscoveryReviewRepositoryIT were excluded; no shared database was opened, and no optional skip is represented as success. The known53-error ExpertDiscoveryServiceTest was deliberately not selected.

### Complete explicit requirement/invariant evidence
All short Kotlin filenames below refer to the authorized discovery service/controller/repository files, not a different implementation.
| Requirement | Direct fresh code/test/runtime evidence |
|---|---|
| I-1/C-5 activated denominator and mutually exclusive result counts | Repository:36-77 counts activated rows and separately unactivated registrationErrors. Reporting IT:212-237 confirms123 remains123 after an unactivated ERROR, projectionErrors independent of auto55, and AUDIT actualauto0/suggested1. No reason-sum is used as a profile denominator. |
| F123 every count, batch and filter total | Reporting IT:76-84,171-209 asserts123/55/15/40/36/19/45/5/18; funnel108/85/66/40 and108 Apollo attempts; projectionPending/projectionErrors/registrationErrors0. Batch principal tuples are40/20/6/14/14/6/13/2/5,40/18/5/13/12/6/15/1/6,43/17/4/13/10/7/17/2/7 for profiles/auto/direct/apollo/clear/unclear/pending/errors/queued. All route/result/batch combinations, exact filtered totals/reasons and stable id pages pass. Independent HTTP confirms F123, three batches40/40/43,UNCLEAR19,PENDING45 and repaired Apollo cohort. |
| Historical auto versus current human decision | Query:79-82,86-102 batch-reads current admission in MySQL. Reporting IT:212-237 and actual HTTP retain historical DIRECT_CLEAR/auto55 while currentHOLD/revision2 is visible. A projection error increases only projectionErrors, not the Apollo-interface error bucket or historical auto total. |
| I-2 old/null, new/zero, invalid input and genuine storage503 | Query:40-83 preserves NOT_RECORDED/null versus RECORDED/0. All three reporting suites and actual HTTP cover legacy3002/empty3003. Controller:40-61/tests:40-92 cover missing item404, bad params/enums/pages/query length/batch number400 beforeSQL, persisted-data/SQL failure503 and no secret echo. Final actual runtime stops its own MySQL and obtains503 on summary/batches/records/item, not fabricated200 zeros. |
| I-3 REPEATABLE_READ and snapshotVersion | Query:19-21 wraps public reads in a read-only REPEATABLE_READ transaction. Reporting IT:298-321 commits a concurrent revision/outcome/admission update: in-flight count/page/current decision remain the old snapshot, next read seesrevision124/auto54. No snapshot is synthesized from task progress. |
| Filtering before paging, escaped bound q, stable sorting, size cap/bulk admissions | Repository:105-146 binds owner/filters/q and escapes literal percent/underscore/backslash/exclamation. Reporting IT:171-209,323-352 verifies all filters, literal search/injection-shaped strings, full names, id ordering, far-offset empty pages,size100, and bounded100-parameter admission reads; controller rejectssize101. No per-record admission lookup or extra provider query is introduced. |
| Full reasons and source evidence | Query:86-102,130-158 exposes typed field evidence with sourceUrl/fetchedAt/sourceUpdatedAt/assertionSource/temporalScope; full distinct reasons and unknown labels preserve originalcodes. Reporting IT:239-264 and HTTP verify four reasons without truncation, reliable source URL/time and attempt summaries without requestJson/responseJson. New Apollo identifier/stages are existing stored facts, not inferred from response text. |
| I-4/N-1/N-2 read-only GET boundary | Repository reporting methods:23-146 are SELECT-only; query dependencies:23-27 are SQL/config/optional current-latch reader, not provider/index repair clients. Reporting IT:239-264 checks20 cycles, no mutating JDBC SQL, complete task/admission/run/item/attempt snapshots unchanged and no secret/raw-response leakage. Actual TCP runtime additionally checks120 authenticated reads and zero JdbcTemplate write methods; matched-detail/provider-auth reads separately check no provider replay or claims/writes. No oldtask/resultSummary/progress/retention/write contract is changed. |
| Lifecycle and retained ledger | Query:110-117 derives ACTIVE/COLLECTING/COMPLETED from queued/inputClosed, not collection SUCCESS. Filtered empty/CLEAR pages retain whole-run ACTIVE. Reporting IT:268-287 and actual HTTP delete only the synthetic task3001 and still read summary/batches/records as EXPIRED from the ledger. No run is fabricated for absent old tasks. |
| Approved current CONFIG_AUTH seam,401 AND403 | Worker:47-48 only reads AtomicBoolean. Existingtick:51-60 returns before claiming when authBlocked. Query:31-38 addsCONFIG_AUTH from the optional current worker and readyfalse; absent worker retains configuration capability semantics. Fresh worker4 tests, reporting IT:86-131 and actual real-client/worker/MySQL runtime for both mocked provider401 and403 prove blocked state,20 authenticated capability reads per status, zero additional attempts/data writes/provider requests, and an unblocked newly constructed worker despite historical failures. No reset,retry,history inference,claim policy or state/table changes. |
| Manual fixture guard/idempotence/namespace | Authorized SQL:27-115 validates database suffix and occupied nonfixture execution namespace before transactional data writes; deletes only fixture-owned data. Reporting IT:268-295 actually reimports, rejects occupied namespace without data changes, and rejects wrong database before data tables appear. Runtime imports twice into a newly created suffix-qualified database. No migration or production seed is added. |
| Downstream07 exact interface dispatch | Controller:13-39 publishes all C-5 paths and forwards validated route/result/page parameters. Repository:119-125 explicitly handles participationAPOLLO versus admissionDIRECT; result predicates remain disjoint. Query:100-101,188-190 serializes apolloPersonId and typed apolloStages with all four event times. Future07 S-6:67/S-7:535 can render the required cohort and identity/stages without fetching raw provider responses. |

### Mandatory measured EXPLAIN receipts
Fresh reporting IT output `artifact://424:274-275`, against the real isolated MySQL, for the same index/owner/order/limit shape as the production reporting page:
```sql
SELECT i.* FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch)
WHERE i.owner_execution_id=? AND i.activated_at IS NOT NULL ORDER BY i.id LIMIT 100 OFFSET 0
```
- Owner3001, actual123 rows: type=ref,key=idx_dvi_run_batch,key_len8,refconst,estimate123,filtered90,Using where/Using filesort.
- Owner5001, actual10000 rows: type=ref,key=idx_dvi_run_batch,key_len8,refconst,estimate4851,filtered90,Using where/Using filesort.
- Fresh test asserts exact totals123/10000, returned page100, and no admission batch over100 IDs. This is the explicitly required child06 EXPLAIN gate only, not a general performance audit or an unmeasured latency guarantee.

### Actual authenticated HTTP/MySQL runtime
A fresh verifier-owned temporary Java driver launched actual embedded Tomcat9/Spring MVC on127.0.0.1/random port, production controller, transactional production query/repositories, existing AuthInterceptor, and a mocked AuthService with a fixed test user. A temporary non-product session servlet established an ordinary session cookie; anonymous HTTP was separately rejected401 at every reporting endpoint. This verifies the existing interceptor/cookie boundary over TCP, not production password-login bootstrap. Provider/ES transports used existing VerificationFlowFixture MockRestServiceServer fixtures; actual worker/clients/policies/MySQL transactions ran. No real Apollo/ORCID/ES provider was contacted.

CP below is the java.class.path property read from the selected Surefire XML using Python ElementTree. The inline Python runner used tempfile outside the repository, compiled and ran with these exact argument vectors (CP expanded at execution):
```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp CP /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo06-reverify2-final-mqfcuwzb/Reverify06.java
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.44 -cp /var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo06-reverify2-final-mqfcuwzb:CP Reverify06 src/test/resources/discovery/apollo-verification-manual-fixture.sql
```
Final compile exit0,Java exit0,Python runner exit0,31.37s; receipt `artifact://434`. Disposable container2e0c03fc6aa63fa2f8df35e316502f34e87e8231b121bc26f2b32df02e555e96; URL `jdbc:mysql://localhost:32930/reverify06_verification_fixture`; migrations throughV158. JDBC connect/socket timeouts were local harness settings solely to exercise DB-down503. Listener51330; JSON time serialization set to ISO in the temporary MVC converter for exact comparison with persisted Instants.

Final observed receipts (`artifact://434:271-276`):
- F06-01 CLOSED: authenticatedHTTP Apollo ALL/PENDING/ERROR/QUEUED108/45/5/18,DIRECT15; aggregate Apollo-admitted40 and F123123/55/15/40/36/19/45/5/18 unchanged.
- Every reporting endpoint anonymous401/authenticated200. Legacy null,new0,currentHOLD,complete reasons,source/time,404/400 validation all checked. Each of the five GETs plus the unclear-item detail repeated20 times (120 reads), complete task/admission/run/item/attempt content unchanged; no JdbcTemplate update/batchUpdate/execute call.
- F06-02 CLOSED: successful existing worker persists person-1 and all four timestamps; actualHTTP detail returns those exact values over20 reads, without raw request/response JSON,data writes or extra Apollo/ORCID requests.
- Current provider401 and403 each independently drive ERROR/CONFIG_AUTH;20 actual authenticated capabilities reads each report readyfalse/CONFIG_AUTH with interleaved blockedticks. Database,lease/attempt state and both provider request counters stay unchanged; newly constructed worker remains unblocked by historical attempts.
- Removing only the synthetic task gives EXPIRED on summary/batches/records. Stopping only the disposable MySQL gives503 on summary,batches,records,item.
- Final success token: `REVERIFY06_AUTHENTICATED_HTTP_MYSQL_OK F06-01=CLOSED F06-02=CLOSED auth401AND403=true legacy=null empty=0 import=2 EXPIRED=true DBdown=503`.
- Tomcat/context/container shut down; temporary source/classes and Tomcat directory removed (`TEMPORARY_RUNTIME_SCAFFOLD_REMOVED`). No repository scaffold or other evidence file was written.

Harness-only correction history, not product failures: first temporary run (`artifact://430`,exit1) incorrectly hardcoded item1 after a double fixture import advanced AUTO_INCREMENT; it was corrected to query the actual first fixture ID. Second temporary run (`artifact://431`,exit1) compared DecimalNode expected time with parsed DoubleNode time using node-class equality despite equal numeric timestamps; the temporary converter was made ISO, with exact persisted-Instant equality. The complete final smoke above reran successfully against a new disposable MySQL after both harness corrections. No product/test fix, acceptance waiver or worker policy change was used for these harness failures.

### Verdict summary
All four light gates pass at the exact repair1 boundary. F06-01 and F06-02 are independently CLOSED with fresh tests and authenticated HTTP/MySQL evidence; the approved401/403 current-latch seam remains read-only. Only the known baseline Node41 and closed-child05 fixture53 observations remain RECORD_ONLY. This is light acceptance of child06 and its downstream07 read contract, not whole-project green, deployment authorization, or visual acceptance of07. Product/tests/index/branch/commits/plans/other evidence were not modified by this verifier; only this verification report is appended.

### Required Action
- COMPLETE_CHILD
