# Child 03 — Verification log

## Epoch 1 — Paused before independent verification

- Writer: Recheck03Implementer.
- Execution outcome: PLAN_CONFLICT; six authorized files retained uncommitted, no implementation commit available for independent review.
- No verifier dispatched; no LIGHT_PASS/LIGHT_FAIL verdict claimed. Two prior completed children retain their independent terminal reports and valid checkpoints.
- Missing authority: add DiscoveryPaperArchiveCaptureTest.kt, PdfEmailExtractorTest.kt and ExpertDiscoveryServiceTest.kt to retire obsolete version/unbound-owner expectations for source-proven unique markers. Exact paths, fresh failures and evidence in execution.md.
- Required action: PAUSE for explicit child03 scope amendment, six→nine files (within master ten-file cap). Later children undispatched.

## Light Verification: LIGHT_PASS_WITH_NOTES

Child: 03 — identity parser; epoch2 / attempt1
Boundary: 5487f3b45085f20f8db8554fb94888de1446c378..bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a
Verifier: Recheck03Verifier
Timestamp: 2026-10-25T01:51:24+08:00

Independent of Recheck03Implementer and Recheck03Epoch2. Read the complete brief including epoch2 and the complete exact approved child plan before inspecting the scoped patch. Plan SHA-256 freshly read: `3088b1b12f0897d349a4e054794e33348a2e95d0951cc5f2455d8d4952a1a3b0`. Target HEAD was the boundary endpoint on `fast/discovery-recheck-master`; `git diff --name-only HEAD -- src/main src/test` was empty. Prior execution entries were baseline only. No full verification skill, broad audit, later-child investigation, product/test/plan/index/commit/branch edit, or production write was performed.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized paths | PASS | `git diff --stat <boundary>` / `git diff --name-only <boundary>` (artifact://930), followed by `git diff <boundary> -- src/main src/test` (complete artifact://932), show exactly the nine approved product/test paths and 238 insertions / 45 deletions. Interleaved plan/evidence files are governance, not additional child implementation paths. Original resource/PDF bytes and consumer/admission implementations are unchanged. |
| Explicit requirements / key invariants | PASS | Inspected complete product/test patch and relevant ownership/segment/conflict code. Only layout RULE_VERSION changes to 20261024. Unique explicit mailbox labels require one matching full-name/complete-initial owner; results retains conflict rejection and creates source proof. Hard x/footer cropping is removed; marker and continuation boundaries remain. Bare at/dot requires labelled complete contact shape; no provider blacklist added. Actual runtime below exercised x32/y96%, unique labels, structural negatives, normalization and proof validity. Precise Mette/Nour/Marwa/nguyen reconciliations retain genuine unknown/shared/damaged/adjacent/wrong-owner negatives rather than weakening to nonempty results. CaptureTest resolved/unknown assertions match the approved child02 first.outcome contract, with no product-logic change. |
| Fresh required commands / baseline comparison | PASS WITH NOTES | Both exact epoch2 Maven commands ran after patch inspection, with `-DmysqlIt=true`, sequentially; both exit 0. C1 347 JVM tests / 0 failures / 0 errors / 2 allowed skips; C2 299 / 0 / 0 / 0. Each also executed 1827 JS tests with zero failures/skips and both bound syntax checks. Counts match the final executor baseline, but all results here are independently fresh. Disposable actual parser/proof/capture/analysis smoke subsequently exited 0. Allowed skips are recorded below and are not passes. |
| Downstream interfaces | PASS | Capture and analysis compose parserVersion dynamically from layout RULE_VERSION; runtime observed the new version in both. `git diff <boundary> -- src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` was empty; actual constants remain identity 20260925 / extraction 20261004. Current-cache consumption and superseded-cache rejection ran in ExpertDiscoveryServiceTest; real queue/MySQL compatibility tests ran without skips. Actual analysis preserves first, and no new admission/ES/mailbox/contact/outreach path or saved source configuration change appears in the scoped patch. |

#### Fresh commands and receipts

All repository commands used `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master` as cwd.

**C1 — exit 0, artifact://940; finished 2026-10-25T01:45:47+08:00:**
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=DiscoveryPaperLayoutRegressionTest,SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,DiscoveryPaperArchiveCaptureTest,*EmailExtractorTest,*Queue*Test,*DiscoveryIdentity*Test,*Source*Test' -DmysqlIt=true
```

Fresh resolved C1 classes, verified against actual Surefire XML and artifact://940:

| Class | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveCaptureTest | 11 | 0 | 0 | 0 |
| DiscoveryIdentityRecoveryControllerTest | 7 | 0 | 0 | 0 |
| ArxivDataSourceTest | 18 | 0 | 0 | 0 |
| CoreDataSourceTest | 19 | 0 | 0 | 0 |
| CrossrefDataSourceTest | 12 | 0 | 0 | 0 |
| DiscoveryIdentityRecoveryServiceTest | 24 | 0 | 0 | 0 |
| DiscoveryIdentityRepairServiceTest | 20 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 3 | 0 | 0 | 1 |
| EuropePmcDataSourceTest | 31 | 0 | 0 | 1 |
| OpenAlexDataSourceTest | 89 | 0 | 0 | 0 |
| OrcidDataSourceTest | 16 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 43 | 0 | 0 | 0 |
| PlainTextEmailExtractorTest | 8 | 0 | 0 | 0 |
| PmcOaDataSourceTest | 3 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 27 | 0 | 0 | 0 |
| DiscoveryIdentityTest | 13 | 0 | 0 | 0 |
| MailQueueConsumerTest | 3 | 0 | 0 | 0 |
| **Total** | **347** | **0** | **0** | **2** |

**C2 — exit 0, artifact://943; finished 2026-10-25T01:50:55+08:00:**
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryVerificationIntegrationTest,DiscoveryPaperQueueRepositoryIT,DiscoveryPaperArchiveFlowIT' -DmysqlIt=true
```

| Class | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| ExpertDiscoveryServiceTest | 194 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 52 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 14 | 0 | 0 | 0 |
| DiscoveryPaperQueueRepositoryIT | 31 | 0 | 0 | 0 |
| DiscoveryPaperArchiveFlowIT | 8 | 0 | 0 | 0 |
| **Total** | **299** | **0** | **0** | **0** |

`glob` inventory of `src/test/**/*Queue*`, `src/test/**/*Source*`, and `src/test/**/*DiscoveryIdentity*` identified queue repository IT outside C1's `*Queue*Test`; C2 covers it and existing source consumption/verification/pipeline/archive-flow classes. No missing/skipped mandatory queue or real-MySQL compatibility evidence. Each Maven invocation completed its bound `node --test src/test/js/*.test.js` (1827 pass, 0 fail/cancelled/skipped), `node --check src/main/resources/static/app.js`, and `node --check src/main/resources/static/task-modal-runtime.js`.

The initial harness-only javac invocation exited 1 because an inherited Surefire XML classpath pointed at the original worktree's classes. This was a classified preparation failure, not a product/test failure. Prefixing the explicit target worktree's absolute `target/test-classes` and `target/classes` resolved it; the next JDK11 javac exited 0. The target-prefixed standalone Java invocation below exited 0. No repository file was modified to resolve harness preparation.

#### Fresh actual runtime smoke

External disposable `Child03Independent.java` was compiled with JDK11 and the test dependency classpath, with this target worktree's classes first, then executed after both required Maven commands:
```text
<JDK11>/bin/javac -cp <target-prefixed test classpath> <temporary-directory>/Child03Independent.java
<JDK11>/bin/java -cp <temporary-directory>:<target-prefixed test classpath> Child03Independent
```

Used generated PDF bytes and the existing actual-fixture constructor, production PDFBox/layout/resolver/plain/PdfEmailExtractor code and `DiscoveryIdentity.validEvidence`; no prefilled proof or textual source assertion substituted for behavior. Independently invoked real capture and analysis using a disposable mysql:8.0.36 fixture and temporary archive, with network collaborators verified untouched. Observed actual results:

```text
left32Bottom96 / inlineAffiliationMarker / sameContactNextLine / wrappedContactDomain:
  email=bernd@sample.edu owner=Bernd Bayerlein validEvidence=true contacts=1
sharedMarker / unknownMarkerOwner / damagedSignature / referenceMailbox /
nextAuthorBoundary / unknownNextAuthorBoundary / emptyParagraphBoundary / nextMarkerBoundary:
  email=bernd@sample.edu owner=null null validEvidence=false contacts=0
b@example.edu (B. Bayerlein); other@example.edu:
  b@example.edu owner=Bernd Bayerlein validEvidence=true
  other@example.edu owner=null null validEvidence=false
b@example.edu (Unknown Owner) / b@example.edu (B? Bayerlein):
  owner=null null validEvidence=false
b@example.edu (B. Bayerlein), Bernd + Bob Bayerlein roster:
  owner=null null validEvidence=false
a@example.edu (A.M.A.), unique Alessandro Maria Achille roster:
  owner=Alessandro Maria Achille validEvidence=true
a@example.edu (A.M.A.), Alessandro Maria Achille + Alice Mary Anderson:
  owner=null null validEvidence=false
available at www.nature.com -> []
Email: bayerlein at example.edu -> [bayerlein@example.edu]
Contact: bayerlein at example dot edu -> [bayerlein@example.edu]
a(at)example.edu -> [a@example.edu]
PDF_PARSER email=bernd@sample.edu owner=Bernd Bayerlein
  identityEvidence=SOURCE_SHA256:08fe39fb867f15459951d227e22f135c611766bb5d650a82948c6e9c539a99f1
CAPTURE_FIRST_VERSION={"identityExtraction":20261004,"pdfLayout":20261024}
ANALYSIS_VERSION={"identityExtraction":20261004,"pdfLayout":20261024,"format":1,"qualification":2}
ANALYSIS_AFTER=[{"email":"bernd@sample.edu","authorName":"Bernd Bayerlein","identityEvidence":"SOURCE_SHA256:08fe39fb867f15459951d227e22f135c611766bb5d650a82948c6e9c539a99f1"}]
FIRST_UNCHANGED=true
CONSTANTS identity=20260925 extraction=20261004
SMOKE_COMPLETE disposableMysqlStopped=true temporaryArchiveRemoved=true networkInteractions=0 productionWrites=0
```

The harness asserted actual PDF and analysis proof validity, exact owner sets, negative non-binding, normalizations, version values, and first immutability. Its finally block stopped its own disposable MySQL; fixture close removed the archive. The entire external temporary source/class directory was then removed (`HARNESS_REMOVED=True`). No harness scaffold was placed in the repository.

Inspection receipts: artifact://930 and full artifact://932; fresh command receipts: artifact://940 and artifact://943. The final executor baseline was C1 347/0/0/2, C2 299/0/0/0 and 1827 passing JS tests per invocation; this fresh verification matches those counts with no new command failures. Historical epoch1 and intermediate epoch2 failures were not rerun to confirm them and were not used as fresh evidence.

### AUTO_FIX

N/A — no proven violation of the four scoped gates; no repair requested or performed.

### RECORD_ONLY

- O-03-01 — Two explicitly authorized skips, read from fresh Surefire XML, are not counted as passes: `DiscoveryPaperLayoutRegressionTest.private original corpus replay explicitly skips when unavailable` reports `SKIPPED: exact original294 frozen raw corpus unavailable; no real-effect claim`; `EuropePmcDataSourceTest.smoke test real Europe PMC API returns papers` reports `Requires network access to Europe PMC API - run manually to verify`. No private full-corpus replay or live Europe PMC validation is claimed. These do not waive any queue/MySQL compatibility test.
- O-03-02 — Existing compiler/PDF-font and fixture-simulated provider warning/error logs remain record-only, consistent with prior O-02 baseline; no unrelated investigation or repair undertaken. Harness target-first classpath also emitted duplicate logback-resource warnings because inherited dependency entries included the original worktree; actual target classes were explicitly first, and smoke completed with zero network interactions. No product defect established.

### Required Action

- COMPLETE_CHILD
