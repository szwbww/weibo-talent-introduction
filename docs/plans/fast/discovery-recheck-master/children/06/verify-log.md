# Child 06 — Verification log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 06 — docs/plans/2026-10-24/discovery-recheck-06-evidence.md
Boundary: 440b7e0cb1daae1eb0d985fef3e12f78d1c7b607..feb58f65512484c742f7bf5f0edb6ec0e2207df8
Verifier: Recheck06Verifier

Epoch: 1; Attempt: 1; Timestamp: 2026-10-25T04:15:00+08:00.

Read the complete brief and exact approved child plan, linked master scope/interface sections and evidence catalog. Baseline execution.md is comparison evidence only; the command and standalone process below were freshly executed by this verifier after changed-path research.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only 440b7e0cb1daae1eb0d985fef3e12f78d1c7b607..feb58f65512484c742f7bf5f0edb6ec0e2207df8` lists precisely the nine brief-authorized product/test paths: archive/PaperBodyAnalyzer.kt, DiscoveryPaperAnalysisService.kt, DiscoveryPaperArchiveRepository.kt; service/PaperVerificationEvidenceService.kt, DiscoveryRecheckAcademicEvidenceService.kt; archive/DiscoveryPaperAnalysisServiceTest.kt, DiscoveryPaperArchiveRepositoryIT.kt; service/PaperVerificationEvidenceServiceTest.kt, DiscoveryRecheckAcademicEvidenceServiceTest.kt. The boundary additionally contains controller governance changes in children/05/{execution,fix-log,verify-log}.md and ledger.md, not additional product/test changes. |
| Plan and invariants | PASS | PaperBodyAnalyzer.kt:15–105 is a value-input parse/compute/assemble kernel with versions, identities, qualification, partial coverage and delta; no repository/client dependency. AnalysisService.kt:17–42 preserves running guard/cache2/lease and explicit commit. ArchiveRepository.kt:63–98 bounds tuple/keyset/attempt lookup. PaperVerificationEvidenceService.kt:27–47 validates normalized IDs; :52–105 reads fixed refs under leases, verifies source/attempt/hash and parses each SHA once, without replacement download; :111–158 requires current reliable email/name or bound author ID, rejects shared/multiple owners, maps only that author's PAPER/SELF/HISTORICAL facts with exact provenance. AcademicEvidenceService.kt:22–85 requires current trusted receipt/allowed identity, returns REUSE before client resolution, uses existing batch HISTORY_ENRICHMENT clients for REFRESH. Fresh tests and runtime below directly exercise these requirements. |
| Required commands | PASS | Exact prescribed JDK11 four-class Maven command freshly exits 0 (193.65s; artifact://1090), JVM30/failures0/errors0/skipped0: RepositoryIT5, AnalysisService9, PaperBridge10, Academic6. Actual mysql:8.0.36 JDBC localhost:33189/archive_child01; mandatory IT not skipped. Maven-bound JS1827/1827,334 suites, failures0/skips0/cancellations0; both node-check-app and node-check-task-modal-runtime pass. Matches writer's final baseline totals. Independent fresh standalone actual-path JVM process exits0 (16.523s), real disposable MySQL localhost:33191, strict loopback provider HTTP; details below. |
| Downstream interfaces | PASS | Public compute/parse/assemble returns existing PaperAnalysisResult schema1/analysis2; existing AnalysisService constructor and analyze(id,force) remain compatible and its nine regressions pass. PaperSourceRef freezes record/attempt/SHA/source execution/source/key; collect takes frozen VerificationSnapshot, refs, mode and evaluatedAt and returns read-only evidence/coverage/diagnostics/analysis/binding provenance. Academic collect returns per-identity metrics/research/provenance/insufficiency, no admission decision or writer dependency. These are reusable outputs for07 and fixed preparation refs for08, not a new Worker/API/start flow. No claim of later integration. |

#### Fresh command and runtime receipts

Working directory for both processes: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`.

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperAnalysisServiceTest,DiscoveryPaperArchiveRepositoryIT,PaperVerificationEvidenceServiceTest,DiscoveryRecheckAcademicEvidenceServiceTest -DmysqlIt=true
EXIT 0; JVM 30, failures 0, errors 0, skipped 0
JS tests/pass 1827; suites 334; fail/cancelled/skipped/todo 0
BUILD SUCCESS; Maven 03:12
```

Runtime dependency preparation: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/recheck06-verifier-classpath.txt -DincludeScope=test`, exit0. Fresh standalone command, launched through subprocess without JUnit runner or test-method invocation:

```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.40 -cp <worktree>/target/test-classes:<worktree>/target/classes:<resolved test dependency classpath> com.weibo.talentintroduction.discovery.service.RecheckEvidenceRuntimeLauncher
CHILD06_RUNTIME PASS realMySQL=true duplicateSHAParses=1 pureWrites=0 explicitAnalysisWrites=1 first/bodySHA=unchanged PAPER=SELF/HISTORICAL ownFacts=3 shared/adjacent=0 fixedMissing/changed=SOURCE_UNAVAILABLE partialPages=100/101 REUSE_HTTP=0 REFRESH_HTTP=2 unexpectedHTTP=0 trustedID=A123 ORCID=AMBIGUOUS EMAIL=NO_TRUSTED_ACADEMIC_ID profileMutations=0
VERIFIER_RUNTIME_EXIT 0 SECONDS 16.523
```

This is an independent fresh invocation of the committed reusable standalone launcher, not a separately authored alternative harness. Its actual assertions were read before execution (PaperVerificationEvidenceServiceTest.kt:188–278). It creates disposable real SQL/archive files, performs historical lookup and actual lease/analyzer/bridge calls, compares pure JSON to explicit saved JSON with equal analyzedAt, checks revision+1/unchanged first+bodySHA, duplicate-SHA parse1, own facts versus shared/adjacent negatives, student=false and reversed-name bound ID. It checks fixed wrong hash, actual source-byte drift and deletion without replacement; PDF bounded coverage100/101; real OpenAlexDataSource through strict local HttpServer; trusted A123 metrics, ambiguous ORCID and absent trusted ID; REUSE before/after REFRESH adds zero HTTP. Provider budget reservations are three HISTORY_ENRICHMENT attempts for two wire requests, not three HTTP requests. No JUnit method was invoked by this process.

Temporary `/tmp/recheck06-verifier-classpath.txt` was removed after execution. Runtime finally closes local server, removes temporary archive directory and stops isolated MySQL. No temporary source or smoke script was added to the repository.

Limits: no production/live provider/paid call, production ES or admission before-after exercise, full application startup, full JVM suite, or07/08 Worker/Gate/orchestrator integration was performed. Zero ES writes is supported by the read-only dependency/code boundary and unchanged frozen profile values, not a live three-layer ES instrumentation claim. Existing bounded parser semantics remain100 PDF pages/2MiB text/100 facts and soft deadline, not OCR or guaranteed hard timeout. This verdict is only the four child06 gates, not whole-system, production or human acceptance.

### AUTO_FIX
- N/A.

### RECORD_ONLY
- O-06-01: Fresh Maven compilation emits existing unrelated Kotlin/source-root warnings (artifact://1090); these do not fail the required command and no proven child06 four-gate defect follows. No unrelated warning investigation or repair performed.

### Required Action
- COMPLETE_CHILD

