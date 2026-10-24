# Repair Plan: discovery-paper-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md
Governing master: commit 3c5a68ad9917fdee22e0548b5afc57a6df04d580; SHA-256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3; approved amendments A1–A16.
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/review/discovery-paper-master/machine-verification.md — Epoch 1, aggregate_master_reviewer, FAIL / INITIAL, finding V-1. Controller persists the reviewer's complete returned report at this exact destination; this repair identifies that initial report, not a similarly named report.
Implementation boundary: c6e9709f349f32c5feb1d067b47c697b0dff05b6..37da0edbdb9737ed75c27ea8e6e525e35ef78354.
Reviewed evidence head: ef84aabfc0e509a8178c626f22c430dcdffb78bb.
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
Target branch: fast/discovery-paper-master
Prior aggregate repair lineage: N/A. One root cause; no implemented repair or mechanical attempt in this epoch.

## Objective

An already accepted archive scope can reuse a verified, existing blob without charging its bytes as new disk/archive growth. Genuine new writes retain the approved live capacity protection.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 / REPAIRABLE / NEW | Child01 T-2: existing blob reuse adds no bytes; child02 I-1/T-1a: preserve complete bodies through the shared storage seam with live capacity checks; master I-4 | DiscoveryPaperArchiveService.kt:228 calls preflight(bytes.size) before hash lookup/reuse at 229–233. It subtracts a complete existing body's size from current remaining capacity even when physical growth is zero. |

Fresh diagnostic: disposable MySQL/actual 1024-byte blob, minimum usable space +1023 bytes. beginScope and beginScopeAttempt pass. Scoped same-byte save returns ARCHIVE_STORAGE_BLOCKED; the unchanged direct save path reuses the same blob successfully, reused=true, archiveBytes=1024. Probe process exit 0; output /private/tmp/aggregate-discovery-dedup.log; source /private/tmp/AggregateDedupProbe.java. Trigger occurs whenever an accepted scope receives an existing body larger than remaining headroom but the actual zero-growth reuse still meets current thresholds. This can unnecessarily abort a synchronous page or defer its queued job.

## Findings Excluded

| Finding / evidence | Reason |
|---|---|
| Original294/page34 effect proof and original23/person-label effect proof | Effect evidence is incomplete/unavailable; no product repair authority and no synthetic substitute. Preserve the master exception and explicit incomplete-effect disclosure. |
| All historical RECORD_ONLY entries | No confirmed mandatory product defect established; no cleanup or historical evidence rewrite. |
| Documentation whitespace in the seed code-receipts snapshot | Non-behavioral and unrelated to this root cause. |

## Unchanged Contract

- A1–A16 remain governing. No plan amendment is proposed.
- New-download preflight remains before provider HTTP; blocked SYNC page consumption/checkpoint and QUEUE PENDING/+60s/unchanged attempts/cache/lease-generation semantics remain.
- Accepted-scope enablement permission stays bound to server-issued record/attempt identity. A later DB enabled=false does not discard that scope's received body. Deployment allowed, path safety, file/hash/length verification and current capacity remain effective.
- Real new physical writes count their actual bytes; no cap/free-space bypass, false SAVED, blob overwrite shortcut or catch-and-ignore fallback.
- first outcome remains immutable; latest updates only on actual extraction; deletion/protection/recovery/analysis and historical success statistics remain.
- Old sample schema/cleanup, parser/identity versions, selected pages, download budgets, admission/SELF/professor/country/manual protection, Apollo permissions/accounting and all frontend behavior remain.
- No applied migration, new schema/class/interface/state, dependency, public testing endpoint, production context, real provider/paid request, production DB, push/merge/deployment/history rewrite.

## Authorized Files

Exactly two product/test paths:

| File | Purpose |
|---|---|
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt | Apply capacity accounting to actual incremental storage after identifying/verifying reusable content, retaining approved scope and safety checks. |
| /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt | Add the discriminating accepted-scope reuse regression in the existing capture fixture; retain all existing assertions. |

The handoff below separately permits only its named execution-evidence file. It is not a third product/test path.

## Repair Tasks

### R-1: Correct zero-growth scope reuse accounting

- Resolves: V-1 only.
- Root cause: scoped preflight reserves full incoming bytes before the existing verified-blob decision; direct reuse does not take that branch.
- Files: exactly the two Authorized Files above.
- Change: distinguish zero-growth verified reuse from a genuinely new body when applying current cap/free-space checks. An already accepted scope must not need a second body's worth of free space for an existing blob. Preserve the pre-request new-download gate and all current permission/path/deletion/hash checks. Do not treat a missing/corrupt/DELETING blob as reusable.
- Regression test: use actual existing 1024-byte content and an accepted capture scope. With usableSpace=minFreeBytes+1023, same-byte reuse succeeds, yields reused=true, increments saved/reused attempts, leaves physical used bytes/file count unchanged, and preserves first. A distinct 1024-byte body under the same remaining headroom still blocks with no SAVED increase. Exercise the same zero-growth versus positive-growth distinction for archive-cap headroom within this one root-cause regression if the implementation has a separate cap branch. Keep scope enablement-toggle/real-time-threshold tests intact.
- Existing verification: executor commands below plus independent aggregate/master re-review before any PASS claim.
- Must not change: all Unchanged Contract items.
- Prohibited: expectation repinning, test deletion/skipping/exclusion, moving required guards into a test-only path, direct repository writes from Capture, broad catch/ignore, changes to any third product/test file, or implementing effect/corpus recovery.

## Verification Commands

Every command uses cwd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master and JDK11. Tests use disposable MySQL, temporary files and strict fake HTTP only.

Executor required gates, after final edits:

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest -DmigrationIt=true`
2. `node --test src/test/js/*.test.js`
3. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher'`
4. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher'`
5. Reproduce R-1 through the newly committed capture regression and record exact success/reuse/no-growth and new-body-blocking counts. No reliance on a stale /tmp probe or stale classpath.

Before aggregate PASS, a fresh independent aggregate reviewer must rerun the complete Epoch 1 master matrix and its mandatory command/runtime/browser gates. Its JVM union is:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,SourceAuthorEmailResolverTest,DiscoverySampleReplayTest,DiscoveryPaperLayoutRegressionTest,PaperQualificationEvidenceExtractorTest,DiscoveryPaperAnalysisServiceTest,DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest,DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT -DmigrationIt=true -DmysqlIt=true`

Independent aggregate also reruns the report's exact Node/syntax, seven comparator controls, six standalone runtime entries, real Chromium UI gates, and `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn package -DskipTests` with WAR hash. Effect-only missing corpus/labels remain explicitly incomplete; machine tests do not manufacture human acceptance.

## Completion Criteria

- R-1 discriminating regression fails on reviewed code and passes on the repair, demonstrating zero-growth reuse and preserved positive-growth blocking.
- Executor gates exit 0 with exact run/failure/error/skip counts. Maven-bound Node/syntax gates run without exclusion.
- No contract weakening; existing scope/disabled/archive-blocked/first-latest/deletion/analysis/sample/cursor/queue assertions remain.
- Product/test changes are confined to the two Authorized Files; index is empty after authorized commits; only the separately permitted handoff is added as evidence.
- Return READY_FOR_VERIFICATION only after the exactly one local product commit and one handoff-only evidence commit below. No executor machine-PASS or human acceptance claim.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md` invocation approves these current bytes and the resolved handoff below. After approval, run execute-p with this exact file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md` invocation authorizes:

1. Only the two Authorized Files and required executor verification commands in this plan.
2. After all repair tasks and required executor commands pass, exactly one local product commit before emitting READY_FOR_VERIFICATION, staging only those two Authorized Files, with product commit subject `fix(discovery-papers): avoid charging reused scope blobs twice`.
3. Appending /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/review/discovery-paper-master/repair-execution.md with exact approval source, repair identity/hash, pre/post code SHAs, changed files, commands/counts/results, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/review/discovery-paper-master/repair-execution.md, with evidence commit subject `docs(review-fast-p): record discovery paper scope reuse repair`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan. The repair.md itself and controller-owned machine/ledger evidence must not be included in either executor commit. If another defect or an unavoidable third product/test path is found, preserve the completed authorized work and report the concrete conflict; do not broaden this plan.
