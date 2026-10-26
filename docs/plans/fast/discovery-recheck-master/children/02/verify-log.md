# Child 02 — Verification log


## Light Verification: LIGHT_PASS_WITH_NOTES

Child: 02 — epoch1 attempt2, 2026-10-25T00:44:00+08:00
Boundary: 7d0cb8c276533576d79f28fe970ccfbac4114d9e..5487f3b45085f20f8db8554fb94888de1446c378
Verifier: Recheck02VerifierFull

Fresh independent command-executing verifier; not Recheck02Implementer or the previous attempted reviewer. Read the exact brief and complete approved `docs/plans/2026-10-24/discovery-recheck-02-archive.md`. This is four-gate light verification, not a full-system review or a repair round. All shell commands used cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`; HEAD was the boundary tip and branch was `fast/discovery-recheck-master`. Product, tests, index, plans, staging and commits remained read-only.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-only 7d0cb8c276533576d79f28fe970ccfbac4114d9e 5487f3b45085f20f8db8554fb94888de1446c378` returned exactly the ten authorized product/test paths plus interleaved child01 execution/fix/verify logs and master ledger governance files. No other product path. |
| Plan and invariants | PASS | Scoped diff and changed implementation inspection: byte magic is shared by save/preview/analysis; first-email array yields 3/2/1 without requiring identity proof; missing array returns unknown; analysisVersion=2 and format/qualification parser keys invalidate old cache without changing identity extraction/first version. XML allows DOCTYPE with external general/parameter entities, external DTD/schema and XInclude disabled. Existing lease/use/finally and commit boundaries remain. Fresh tests and separate runtime harness confirm first/SHA preservation, zero preview record writes, safe DOCTYPE, and zero external requests. UI retains existing bindings/delta and muted nodes; only approved copy/resource keys change. |
| Required commands | PASS | Fresh post-inspection Maven 34/34 JVM tests and 1827/1827 bound JS tests, zero failures/errors/skips; bound app/task-modal syntax checks passed. Standalone discovery tests 10/10; discovery syntax exit 0; shared asset/mount regressions 18/18. Separate Java runtime smoke and real Chromium fixture UI observed below. |
| Downstream interfaces | PASS | No admission, ES, contact, mail, queue or child01 counts implementation changed in boundary. Shared resource registrations retain 17 entries, all `20261024-paper-format` (actual index src/href extraction observed all 17 values); shared mount/resource regressions passed. Qualification VERSION=2 and format VERSION=1 are exposed in analysis parserVersion, analysisVersion=2; first version unchanged. Existing concurrent lease/delete, protection, cache and first-history tests passed. No later-child investigation. |

Fresh commands/outcomes:

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveServiceTest,DiscoveryPaperAnalysisServiceTest,PaperQualificationEvidenceExtractorTest` — exit 0, BUILD SUCCESS, 3:16; archive 17, extractor 9, analysis 8, total 34, failures/errors/skips 0. Bound `node-test`: 1827 tests, 334 suites, 1827 pass, fail/cancel/skip/todo 0. Bound `node-check-app` and `node-check-task-modal-runtime` passed. Fresh full output `artifact://852`.
2. `node --test src/test/js/discoveryPapers.test.js` — exit 0, 10 pass, fail/skip 0.
3. `node --check src/main/resources/static/discovery-papers.js` — exit 0.
4. `node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` — exit 0, 18 pass, fail/skip 0.
   Commands 2–4 were issued in that order in one `&&` chain; all completed.
5. Smoke classpath preparation: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/child02-verifier-classpath` — exit 0.
6. Disposable source launcher invocation: `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java --class-path "<target>/target/test-classes:<target>/target/classes:<resolved Maven dependency classpath>" /tmp/Child02Smoke.java`, where `<target>` is the exact cwd above and the dependency suffix was read from `/tmp/child02-verifier-classpath` and passed as a literal expanded argument. Actual changed services/controllers' fixture graph ran outside JUnit, with a new disposable Docker MySQL, generated PDF, temporary blob directory/secret file and loopback HTTP request counter; no live application or production writes.
   Final run exit 0: `legacy persisted MIME=text/plain`; `summary=ArchiveExtractionSummary(emails=3, resolved=2, unresolved=1)`; `preview=application/pdf`; `detected=PDF analysisVersion=2 scannedPages=1 first/SHA preserved; preview writes=0`; `DOCTYPE facts=1 external requests=0 secret exposed=false; XML first/SHA preserved`. Historical MIME was explicitly changed only in the isolated fixture DB after save, before preview/analysis. The smoke compared entire record equality across preview and first JSON/body hash across analysis. The DOCTYPE contained a loopback external DTD, local-file entity and HTTP entity. Both forbidden content leakage into result and HTTP request count were asserted.
   Harness development disclosure: the first invocation exited 1 at its DOCTYPE fact assertion because the synthetic XML omitted the biography-section heading required by the unchanged qualification rule. Adding `<title>Biographies</title>` to the external fixture (not product code) produced exit 0; a final fresh run additionally exercised persisted historical text/plain metadata and passed. This is not a product repair or suppressed error.
7. Real Chromium: read `xd://eval/browser`, opened dedicated managed headless Chromium tab `child02-independent` at about:blank, loaded actual index markup without bootstrap scripts/external links and injected the actual unmodified discovery-papers.js. Reparented actual archive section/dialog nodes into an isolated visible host and mounted with an in-memory GET-only fixture transport. Observed rendered list `邮箱 3 / 姓名关联 2未运行解析对照`, and facts `未运行解析对照`. Clicked actual row `view` button; observed native reader dialog and `尚未运行解析对照；首次提取结果见下方`. Observed transport calls only GET settings, stats, list and record/1; no analysis POST. Reader analysis/download controls disabled for this no-readable-attempt UI fixture. Initial harness root lookup wrongly assumed reader was nested in a section; corrected by hosting the actual sibling nodes together before mounting, with no product change. Closed tab afterward.
   Scope limit: this proves actual changed JavaScript/DOM copy and bindings in Chromium with isolated fixture transport, not authenticated live deployment, PDF browser rendering, or full human A-1/A-2/A-3 acceptance. PDF content routing/analysis and entity safety were exercised separately through real service methods above; protection/busy deletion through the required fresh regressions.
8. `rm /tmp/Child02Smoke.java /tmp/child02-verifier-classpath` — exit 0. Fixture temporary directories/secret removed through finally/close, isolated MySQL stopped, managed tab released. No smoke scaffold retained.

### AUTO_FIX

N/A — no proven four-gate deviation.

### RECORD_ONLY

- O-02: Maven emits unrelated compiler warnings, e.g. unused parameter in `ManualInitialOutreachService.kt:2769`. Required command still succeeds. Recorded only; no investigation or cleanup. Prior-child O-01 remains outside this child's scope and was not reopened.

### Required Action
- COMPLETE_CHILD
