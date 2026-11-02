# Repair Plan: paper-reprocess-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-master.md
Governing identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e; recorded commit 53c2b0018e239e3c30db25942e99c2cddc69e9d4; AMENDMENT_RECORDED; approved A1–A13 in /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fast/paper-reprocess-master/ledger.md.
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/machine-verification.md — Epoch 1, independent reviewer /root/paper_reprocess_aggregate_epoch1, resumed attempt 2; FAIL / INITIAL. Controller persists the returned report; no report is written by this reviewer.
Implementation boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..e213acb50791fe39a0de0a4d9d599bc4e2398cc1
Reviewed evidence HEAD: 73f77441f45f6b1c949a167a60e7b155d58dd025
Selected worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Selected branch: fast/paper-reprocess-master

## Objective

Explicit retry resumes unfinished owned qualification work; paper results open the actual eight-step record for new and existing experts; dialog and completion messages match the durable state; reconciliation rejects incorrect target identities.

## Findings in Scope

| Finding | Severity | Requirement | Root cause and evidence |
|---|---|---|---|
| V-1 | P1 | Master I-5/I-7; 03 I-5/retry; 04 I-2/I-4 | ReprocessRepository retry reopens TARGET only; cancelled owned verification remains CANCELLED. ApplyService.observe treats CANCELLED as successful terminal qualification. Real SQL probe: cancelled NEW=1, admitted=0; explicit retry returns COMPLETED/TARGET COMPLETE with NOT_EVALUATED=1, pending=0. /tmp/paper-aggregate-cancel-probe.log; repository lines 425–483; apply lines 252–287. |
| V-2 | P1 | 05 I-2/I-4 and S-6 Escape/focus | New dialogs have no Escape keydown boundary; app's host handler guards only old dialogs. Actual Chrome and assembled-handler probes call closeTaskModal once from the new confirm dialog. /tmp/paper-aggregate-browser-utf8-final.log; /tmp/paper-aggregate-ui-probes.js; new JS lines 307–312; app lines 20352–20358. |
| V-3 | P1 | Master I-1/I-7; 06 I-1/I-2 and per-identifier acceptance | Reconciler treats any outcome outside three denied values as a binding and compares docId only for baseline ALREADY_RECOVERED. Record 31372 expects 0000-0002-0178-9985, yet wrong-doc, duplicate AMBIGUOUS_TARGETS, absent outcome and SKIPPED become EXPECTED_MATCH; a known conflicting replay binding becomes EXPECTED_MISSING. /tmp/paper-aggregate-reconciler-probes.json; script lines 38–65, 162–174. |
| V-4 | P1 | Master I-7; 05 I-1/I-3 and implementation step 8 | API defines summary.targetTotalKnown, while render reads snapshot.targetTotalKnown. Real completed 121-paper run says target total is still forming. /tmp/paper-aggregate-browser-utf8-final.log; JS line 72; ReprocessSummary/Detail lines 60–71. |
| V-5 | P1 | Master I-7/X-8/X-9; 04 I-4; 05 S-4 and detail acceptance | New authors use ordinary epoch0 items, but original detail opener reads the recheck-only endpoint. Real HTTP NEW parent 30714/item 1 returns 404 RECHECK_NOT_FOUND/zero steps; REPAIRED child 30715/item 4 returns 200/eight steps. The shared syncRetry entry also begins with requireRecheck and cannot service this ordinary ownership. /tmp/paper-aggregate-crosspath-browser.log; QueryService lines 126–136,175–177; RecheckService lines 367–378. |

## Findings Excluded

| Finding/item | Reason |
|---|---|
| V-6 / child05 F-05: deterministic 503 retains an ENFORCE pending key and locks mode | Non-mandatory P2 recovery usability edge. No human approval to repair; do not fix opportunistically. |
| V-7: preview.activeRun remains stale after a resumed run completes, leaving the batch button disabled until context reload | Non-mandatory P2 usability edge; do not fix opportunistically. |
| Original numbered reference-heading limitation, optional older corpus, generic catalog summaryRule=null, historical rapid-tick projection trace overlap | Observations with no confirmed introduced mandatory violation; no parser, catalog or general projection-concurrency expansion. |
| Initial combined Maven paperBatchGroups error | Original failing receipt retained; complete fresh rerun passes 203/0/0/1. Root cause of the 8304.735-second first attempt is not established; no speculative test/product repair. |
| Production #30711 ENFORCE, live ES/ORCID/Apollo and human acceptance | Outside this repair's execution authority; not converted into PASS. |
| Historical reconciliation improvement-label wording and documentation count discrepancies | Optional wording/hygiene only; no extra report or baseline-plan edits. |

## Unchanged Contract

- Master I-1..I-9, M-1..M-5, all child acceptance criteria, and A1–A13 remain in force. This is implementation repair, not a baseline amendment.
- No business writes from AUDIT, source acquisition, parser/qualification rule changes, paid-detail expansion, mail/cursor changes, archive/first-result rewrites, schema changes, reindex or production activity.
- Cancellation preserves committed RAW/name/person/admission results. Retry never replays completed business or qualification actions, adopts foreign records, resets historical epochs or removes durable receipts.
- Only confirmed server-owned paper-reprocess ordinary records may gain the missing read/sync path. Existing generic recheck authorization and mutation guards stay strict.
- Original CSS/DOM contract S-1..S-6 stays byte exact. No CSS, app.js, generic theme or new UI subsystem is authorized.
- No product edits are authorized by this draft. No push, merge, deployment, applied-migration edit, history rewrite or unrelated cleanup.

## Authorized Files

Exactly ten product/test files:

| File | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt | Persist explicit retry intent/ownership and preserve receipts for unfinished owned qualification. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt | Resume cancelled unfinished qualification and report cancellation truthfully. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt | Only necessary parent-owned retry/ownership persistence; preserve generic epoch0/recheck behavior. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt | Idempotent owned continuation and narrow existing projection-only retry for confirmed paper-owned ordinary items. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt | Existing detail route can read confirmed paper-owned ordinary eight-step facts without relaxing generic recheck guards. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/main/resources/static/discovery-paper-reprocess.js | Dialog Escape isolation and correct nested target-total field only. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/js/discoveryPaperReprocess.test.js | Discriminating host-Escape and real DTO-shape regressions. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/scripts/discovery_admission_probe/reconcile_paper_identity_replay.py | Reject conflicting/malformed/duplicate/wrong-target actual evidence. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt | Reuse existing suite for reconciliation CLI identity/error regressions with temporary synthetic inputs. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt | Real-SQL cancellation/retry and actual detail/sync API cross-path regressions. |

Temporary diagnostic/build outputs may use /tmp and target. The execution-handoff evidence file authorized below is separate from these ten product/test files. No other tracked file is authorized.

## Repair Tasks

### R-1: Resume unfinished owned qualification after explicit cancellation retry

- Resolves: V-1.
- Files: ReprocessRepository, ApplyService, VerificationRepository, RecheckService, EndToEndTest from the exact Authorized Files table.
- Change: distinguish CANCELLED qualification from a successful conclusion; retain a truthful cancelled trace before retry. An explicit same-parent retry must resume only unfinished owned qualification under current parent permission and frozen settings. Preserve old cancelled/history records; use a durable, idempotent continuation if a new owned qualification epoch is necessary. Repeated polls, lost responses and restart must not duplicate that continuation. Successful items, RAW/person/admission receipts and prior provider conclusions are retained.
- Regression: real SQL, cancel after RAW creation/ordinary activation but before qualification, then explicitly retry the same parent; finish actual qualification, retain actualNew=1 and one RAW/person registration, and do not label a CANCELLED/NOT_EVALUATED item as successful qualification. Cover a cancelled existing-author child as the same root cause; preserve its old epoch and completed peers.
- Existing verification: combined fifteen-class gate, EndToEndTest, Paper03/04/06 runtime entries.
- Must not change: cancellation does not undo committed effects; frozen settings/provider permissions; parent/lease/generation fences.
- Prohibited: global ordinary-item reset, reopening completed historical rechecks, re-consuming successful authors, direct admission SQL or a new qualification engine.

### R-2: Keep Escape inside the active new dialog

- Resolves: V-2.
- Files: discovery-paper-reprocess.js and discoveryPaperReprocess.test.js.
- Change: native Escape closes only the new confirm/detail/history dialog and returns focus to its trigger; the task host stays open. No cancel POST is sent.
- Regression: use the actual host Escape handler with the real new DOM/handlers, not a standalone cancel-event assertion. Exercise confirm and detail, verify host close count zero, focus restoration and zero task-cancel requests.
- Existing verification: focused three-file Node command and full Node suite; browser dialog/keyboard checks.
- Must not change: native dialog behavior, explicit task cancel, suspend/dispose request cleanup, S-1..S-6.
- Prohibited: editing app.js/global keyboard handling or fixing excluded pending-key/active-preview issues.

### R-3: Validate actual reconciliation identities before accepting a match

- Resolves: V-3.
- Files: reconcile_paper_identity_replay.py and DiscoveryPaperIdentityReplayToolTest.kt.
- Change: explicit valid binding/outcome evidence is required. Never accept absent/unknown outcome, duplicate/ambiguous targets or a known conflicting production binding as a successful match. A SKIPPED business outcome alone is not binding proof; separately validate its actual identity, and do not turn a valid source binding into a business/admission success. Compare known expected docId for every applicable baseline state; match the stable record/body/email/name keys and actual target identity, not merely totals. Emit a supported failure classification or explicit input error for invalid actual evidence. Keep legitimate gains and honest remaining-missing rows distinct.
- Regression: temporary end-to-end CLI inputs demonstrate known existing doc with wrong docId, two actual targets for one stable key, missing/unknown outcome, SKIPPED evidence with a known wrong docId, and conflicting replay name; each fails the semantic acceptance instead of becoming EXPECTED_MATCH/ordinary EXPECTED_MISSING. A valid control input still succeeds. Reuse the existing Kotlin suite; no new test file/fixture in Git.
- Existing verification: replay-tool suite, same frozen 337-combination replay and reconciliation.
- Must not change: immutable manifest/audit/bytes, historical 337/333/325 units, SOURCE_MISSING handling, current truthful gain counts.
- Prohibited: changing the frozen truth, accepting equal totals as identity proof, adding network/business writes or repairing optional label wording by changing the approved vocabulary.

### R-4: Display the server's actual target-total state

- Resolves: V-4.
- Files: discovery-paper-reprocess.js and discoveryPaperReprocess.test.js.
- Change: read targetTotalKnown from the actual ReprocessDetail.summary contract; do not manufacture a parallel root field or compute it from fetched pages.
- Regression: actual JSON shape with summary.targetTotalKnown=true and no root property renders “目标总量已固定”; false renders forming. Counts/runId remain server authoritative.
- Existing verification: focused/full Node commands, real completed AUDIT API/browser summary.
- Must not change: DTO/API, persistence, category units and CSS/DOM.
- Prohibited: frontend global item aggregation or optional stale-preview/pending-mode cleanup.

### R-5: Open and retry the actual ordinary paper-owned verification record

- Resolves: V-5.
- Files: VerificationQueryService, RecheckService and EndToEndTest from Authorized Files.
- Change: complete the existing eight-step detail seam for ordinary items proven to belong to this paper-reprocess parent. Read their actual saved trace, source refs and result, with no synthetic recheck or duplicate verification run. Keep ordinary non-paper runs, foreign run/item combinations and ordinary mutation paths outside this exception. The same confirmed ownership must allow only existing candidate projection retry for an admitted ordinary item, preserving admission/protection checks and using the existing project leaf without provider/qualification replay. Do not globally relax requireRecheck/requireRecheckItem.
- Regression: actual controller/query path for a NEW epoch0 item returns 200/eight real steps and proper IDs; existing REPAIRED child still does so. Wrong parent/item and unrelated ordinary run are rejected. Inject ordinary candidate projection failure; the existing public sync-retry flow retries projection only, retains admission, and leaves provider call count unchanged.
- Existing verification: EndToEndTest, existing Query/Recheck/Commit/Integration suites in the combined gate, focused Node opener tests, fresh real browser NEW/REPAIRED eight-step and source-reader paths.
- Must not change: original recheck history, actor/authentication, normal prepare/start/cancel semantics, existing projection writer.
- Prohibited: preparing a duplicate child merely for display, adding an HTTP caller-controlled ownership flag, accepting arbitrary epoch0 items, resetting qualification, a new endpoint/DTO, new writer or extra product file.

## Verification Commands

All Maven commands run in /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master with JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home. Use this JDK11 for child Java processes too. Local Docker permission and target output permission may require the already established sandbox escalation. Use finite command timeouts; retain failures and their complete output. Do not alter tests to hide a failed gate.

1. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true`
2. `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js`
3. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest,RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest,DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true`
4. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true`
5. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`
6. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn package -DskipTests`
7. `node --test src/test/js/*.test.js`
8. `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py`
9. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.Paper03RuntimeSmoke'`
10. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke'`
11. `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-DpaperReprocessIt=true -DmysqlIt=true -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke'`
12. `/usr/bin/time -l /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-reprocess-master-repair-replay.json`
13. `python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay /tmp/paper-reprocess-master-repair-replay.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv /tmp/paper-reprocess-master-repair-reconciled.csv --report /tmp/paper-reprocess-master-repair-reconciled.md --runtime-evidence /tmp/paper-reprocess-30711-runtime.json`
14. Fresh isolated browser acceptance: real API run; confirm/detail Escape keep the host; completed summary uses nested targetTotalKnown; NEW and REPAIRED open their actual eight steps; source-reader open sends no analysis POST; admitted ordinary projection-only retry adds no provider request. Preserve exact CSS/DOM and 1440/390 layouts at 100%/125% viewport-equivalent zoom. Save screenshots, HTTP statuses and real IDs only under a new /tmp repair diagnostics directory. Do not call CSS zoom with an unchanged 100vw viewport native 125% proof.

Before commands 12–13, verify their /tmp output paths are absent; never overwrite original inputs or a prior receipt. If a diagnostic output already exists, select a fresh /tmp output basename and record the exact resolved command; this authorizes temporary outputs only. The manifest/baseline/runtime hashes remain de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c / e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c / 5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3.

## Completion Criteria

- V-1..V-5 each has its discriminating passing regression and actual runtime/API/browser evidence; no excluded issue is required for completion.
- All mandatory machine gates above pass; the existing optional old private corpus skip is disclosed separately. Any other NOT_RUN is not PASS.
- Fixed-source replay still accounts for all 337 combinations, 244 readable records, stable truth/input hashes and no new conflicting binding. Reconciliation rejects the demonstrated invalid evidence rather than masking it.
- Cancellation/retry preserves completed effects and old epochs; no duplicate RAW/person/qualification/projection replay or unrelated task cancellation.
- Original code/file scope remains bounded to the ten Authorized Files. No baseline plan, report, ledger, migration, index, HEAD or branch is modified by the verifier.
- Executor verifies that product inputs still match reviewed code e213acb50791fe39a0de0a4d9d599bc4e2398cc1 before editing; documentation-only review commits may be newer. Any real product drift or need for another file is a stop requiring an explicit amendment, not inferred authority.
- Human acceptance remains PENDING. No release, production run or manual sign-off is implied.

## Human Approval

Execution is prohibited until an explicit human-originated invocation approves this exact current artifact:

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md` invocation authorizes:

1. Only the ten Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting READY_FOR_VERIFICATION, staging only Authorized Files, with subject `fix(discovery): repair paper reprocess retry and result tracing`.
3. Appending /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md with the exact approval source, repair path and SHA-256, pre/post code SHAs, changed files, commands and exits/counts, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/review/paper-reprocess-master/repair-execution.md, with subject `docs(review-fast-p): record paper reprocess repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, production write, automatic extra repair round or product repair beyond this plan. Approval does not authorize the executor to perform the independent aggregate review itself.
