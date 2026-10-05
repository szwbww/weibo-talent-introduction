# Repair Plan: discovery-review-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-04/discovery-review-master.md
Verification report: aggregate verify-p, 2026-10-05, FAIL / INITIAL
Implementation boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..ead644fbff77036a09302e91acb86ef092941c19

## Objective

Make the displayed/snapshotted outreach target count identical to the deduplicated execution set, and never represent an interrupted all-page review with pending items as completed.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master I-6; child 05 I-3 | `countEsTargets` sums independently selected ES batches and is not given the retry/global dedup identity set used by `OutreachTargetIterator`. |
| V-2 | P1 | Master I-4/I-6; child 03 I-3; child 06 I-1 | An `INTERRUPTED` apply task with no summary falls through to `APPLIED`; UI consequently presents completion and refreshes success state. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Baseline Maven `ExpertContactLocationServiceTest` 19 errors | Pre-existing ancestor/environment defect; outside boundary. |
| Child record-only O-1/O-2/O-3/O-4/O-5/O-6 and D1 | Observation, external environment/policy, or no confirmed repairable mandatory violation. |
| Same-request-key asynchronous persistence window | Adjacent concurrency risk; no required concurrent-prepare acceptance evidence in the approved contract. |

## Unchanged Contract

- D1 remains a human policy/release gate; this repair must not enable historical resend, unsubscribe, or rebind behavior.
- No ES mapping, migration, SMTP, candidate projection, admission decision, scheduler, or global task-platform change.
- All-page review remains explicitly retried only; interruption must not auto-continue.
- Existing manual/scheduled/retry filtering, template snapshot, account validation, and item-level CAS semantics remain unchanged.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | Make preview/total construction use execution-equivalent global deduplication. |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | Prove preview/run total and target parity across duplicate ES/retry identities. |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt | Derive a distinct interrupted batch phase from persistent task state, preserving item counts. |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt | Prove recovered interruption remains pending and requires explicit retry. |
| src/main/resources/static/app.js | Render/poll the interrupted phase without a completed-success transition. |
| src/test/js/discoveryReview.test.js | Prove the UI labels and terminal handling do not call interrupted work complete. |

## Repair Tasks

### R-1: Deduplicated outreach target accounting

- Resolves: V-1.
- Root cause: `countEsTargets` at `ManualInitialOutreachService.kt:1870-1901` counts each ES page/level separately while `OutreachTargetIterator.kt:48-53` eliminates identities globally with the retry-populated set.
- Files: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`; `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`.
- Change: Construct the preview/total target calculation with the same cross-page and retry identity semantics as execution. Preserve selector decisions, filter/reason accounting, ES traversal, retry precedence, and cancellation behavior.
- Regression test: Duplicate one normalized identity across ES batches/levels and between NEW retry plus ES; assert the preview/execution total and actual selected outbound target count are one, with one SMTP attempt.
- Existing verification: `ManualInitialOutreachServiceTest`, full node suite, isolated repository IT, and full Maven suite.
- Must not change: no filter, template-gate, account assignment, retry policy, or send side effect semantics.
- Prohibited: no selector API redesign, schema/migration, or D1 policy change.

### R-2: Interrupted all-page review phase

- Resolves: V-2.
- Root cause: `DiscoveryReviewService.kt:1239-1243` treats any non-active apply task lacking a result summary as `APPLIED`; `app.js:2486-2492,3742-3770` then terminates polling and presents success.
- Files: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`; `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt`; `src/main/resources/static/app.js`; `src/test/js/discoveryReview.test.js`.
- Change: Surface a distinct `INTERRUPTED` phase when persistent apply-task state is interrupted and unresolved items remain. Keep exact state-count identity, show interruption rather than completion, stop automatic continuation, and leave retry user-explicit.
- Regression test: Seed a successful prepare plus an interrupted apply task, no summary, and pending items; assert API phase `INTERRUPTED`, no `APPLIED` completion rendering/refresh, and explicit retry remains the only continuation path.
- Existing verification: `DiscoveryReviewAllPagesTest`, `DiscoveryReviewServiceTest`, node suite, isolated repository IT, and full Maven suite.
- Must not change: completed `APPLIED`, cancellation, failure/stale counts, batch hash, item CAS, and normal polling semantics.
- Prohibited: no automatic retry/resume, no item mutation just to display a phase, no task schema/platform change.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest test`
2. `node --test src/test/js/*.test.js`
3. `DB_URL="jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

## Completion Criteria

- V-1 regression proves a one-identity retry/ES or cross-page duplicate yields the same one-target preview/total and one execution target.
- V-2 regression proves interrupted pending all-page work is `INTERRUPTED`, never displayed as completed, and never auto-restarted.
- Every changed file is in the authorized list; D1 and excluded observations are unchanged.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md` invocation authorizes:

1. Only these Authorized Files and required verification commands:
   - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt`
   - `src/main/resources/static/app.js`
   - `src/test/js/discoveryReview.test.js`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest test`
   - `node --test src/test/js/*.test.js`
   - `DB_URL="jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
2. After both repair tasks, the focused JVM tests, full Node suite, and isolated repository IT pass, and all required commands have run freshly, create exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the Authorized Files. The full Maven result must be reported without suppression: the already excluded 19 `ExpertContactLocationServiceTest` timezone errors and the specifically observed `RestTemplateConfigTest.kt:295` deadline-versus-connection-reset failure may remain for independent verification. This permits handoff only; it does not establish that the HTTP failure is baseline/transient or waive its disposition. Other failures still block handoff. Product commit subject: `fix(discovery-review): align target totals and interrupted review state`.
3. Append `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/review/discovery-review-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record repair execution`.
5. Return to the already authorized aggregate re-review in the same task when the human invocation requests it: `$review-fast-p docs/plans/fast/2026-10-04-discovery-review-master/human-review-handoff.md`, using the committed repair-execution handoff.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

## Human-Approved Amendment

- Approval source: the human asked for the smallest scope, explicitly instructed not to invoke `review-fast-p`, and then approved continuation with “好的 继续” after the assistant restated the boundary: amend the commit prerequisite, commit only the six authorized files, and update execution evidence.
- Only the commit/handoff prerequisite changes. R-1/R-2 acceptance, required commands, product-file scope, D1 and all unchanged contracts remain intact.
- The 19 timezone errors remain excluded. The named HTTP/deadline failure remains unresolved for independent verification; it is not accepted as harmless, baseline, or intermittent. No out-of-scope product fix is authorized.
- Record this amendment in a separate plan-only documentation commit, then create the specified six-file product commit and one report-only evidence commit for this amended execution epoch. Preserve the previous blocked report/commit as historical evidence; do not amend or rewrite history.
- Stop at `READY_FOR_VERIFICATION`. Do not invoke `review-fast-p`, `verify-p`, or aggregate re-review in this task.
