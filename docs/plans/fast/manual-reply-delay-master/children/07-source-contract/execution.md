# Fast-P Execution — 07-source-contract

## Epoch 1 — Approved prerequisite seed

- Plan: docs/plans/2026-11-04/manual-reply-delay-07-source-contract.md
- Plan identity: commit:45f517a3ee337dfa74eb33cb56cdb14f0ba09d69
- Base: 4e64c1f4da4eb5acdac282731f58f2798ab9ff5d
- Implementation: —
- State: PENDING
- Authority: A23 applies existing A15 source-contract completion and all-later-child approval; exact ten-file prerequisite respects max10 rather than inventing a broader07 exception. A24 retains the subsequent seven-file activation and full integration acceptance.
- Source research: Impl07Activation pre-edit PLAN_CONFLICT, preserved in children/07-activation/execution.md; literal grep/source/transport receipts at local://07-source-contract-amendment-evidence.md, including670–692 anchor clarification. No product edits/checks/runtime evidence were performed in that research.
- Scope: complete supported draftless OUTBOUND metadata/codec/source validation/restore consumer, preserve historical compatibility and no early public enqueue. Fresh isolated implementer/independent verifier remain required.

## Epoch 1 — Interrupted writer recovery

- Timestamp: 2026-11-05T12:16:50Z
- Writer: Impl07SourceContract
- Provider failure: `[openai-codex/gpt-6-astra] getaddrinfo ENOTFOUND chatgpt.com`; agent exited1 after partial work, not READY_FOR_VERIFICATION.
- Retained HEAD: 5814ef090db9e82adbec8aff44476fe393e0cf51
- Resume identity: same retained branch/worktree; native status shows exactly the ten authorized product/test paths modified, no staged paths, plus controller ledger. No implementation commit exists; edits are preserved for isolated continuation rather than reset/replay.
- Known command result: `node --test src/test/js/mailboxDelayedReply.test.js` exited1,31 tests/30 pass/1 fail/0 skipped. Failure `late outbound restore cannot overwrite after revision` at test321/332; stale response rendered `Raw ${expertName}`. Original receipt `artifact://2674` and interrupted agent JSONL tool result are ground truth; do not rerun simply to reproduce this failure.
- Earlier `mvn -DskipTests test-compile` was backgrounded as bg_166; its final result is not present in the recovered report and proc://bg_166 is unavailable after resume. No compilation success is claimed. Required fresh final command set remains due after completing the implementation.
- Temporary replay extraction was created under `/tmp/impl07-source-contract-runtime`; startup/smoke completion is unproven. The continuation must inspect retained owned scaffolds before reuse/cleanup and must not interfere with unrelated services.
- Recovery action: acquire a fresh isolated implementer for the same epoch1/fix_round0 and exact approved plan; preserve failed history and complete current-code verification/runtime. This provider failure and reacquisition do not consume an automatic product repair round or reopen01–06.

## Epoch 1 / fix_round0 — Resume07SourceContract execution result

**READY_FOR_VERIFICATION — execution evidence only, not independent PASS.**

### Identity and retained-work continuation

- Executor: `Resume07SourceContract`; prior writer: `Impl07SourceContract`. The prior provider interruption and known failed regression above remain historical facts. No reset, replay of completed children, new worktree, repair round, or production activation occurred.
- Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`, branch `fast/manual-reply-delay-master`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master@fast/manual-reply-delay-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Product base: `4e64c1f4da4eb5acdac282731f58f2798ab9ff5d`; retained entry HEAD: `5814ef090db9e82adbec8aff44476fe393e0cf51`.
- Initial approved plan identity: `commit:45f517a3ee337dfa74eb33cb56cdb14f0ba09d69`; initially read child SHA-256: `43d15d51b9a04654d0e3cea4013696943c250120d0553cb8b10feb26edb900e3`.
- During execution, controller-approved A25/A26 added only the existing cache-contract compatibility test. Both full plans were re-read before the added-file edit. Current approved plan identity: `commit:525aeeb9e97241d5ad044f5662c75194db1a5944`; authority/brief checkpoint: `85480d9b5a39435b18dd10d0f7db1fffb2e1c46f`. This is the explicit same-epoch amendment, not silent transfer of an earlier result.
- Canonical child plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-07-source-contract.md`.
- Current child SHA-256: `109d6996fb968afc2e698a6fd667beb1ecad9075f17f35f831606523bf668a77`.
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-07-source-contract.md@109d6996fb968afc2e698a6fd667beb1ecad9075f17f35f831606523bf668a77`.
- Current master SHA-256: `87a7f545b2c218281132edef0be684aba07ea297022826eec9f44da13262d3c8`.
- Implementation commit / post-execution code SHA: **`a9c09c9abf8cf08707d86514b168318341716126`**, subject `feat(fast-p): implement 07-source-contract`.
- Commit contains only the authorized eleven product/test files. Controller plans, ledger, brief and this execution report are excluded. Product boundary: `4e64c1f4da4eb5acdac282731f58f2798ab9ff5d..a9c09c9abf8cf08707d86514b168318341716126`; evidence-only intervening commits do not change that product base.
- Stage/commit worktree gates passed; implementation is target `HEAD` and an ancestor of `fast/manual-reply-delay-master`. Final product/index are clean; only this execution report remains modified for controller evidence commit. Plan hashes were rechecked unchanged after final checks and commit.

### Completed contract and files

| Requirement | Implementation / direct evidence |
|---|---|
| T1, C-1/C-2 | `PendingMailOperationService.kt` carries the original typed conversation request without activation; `DelayedManualReplyModels.kt` adds nullable legacy source and original conversation metadata, strict mutually exclusive source validation, UUID/anchor/account/ordered attachment consistency, historical inbound decode compatibility. |
| T2, C-3 | `DelayedManualReplyService.kt` compares actual legacy source identity rather than nullable inbound equality or request UUID; corrupted unresolved snapshots fail closed. Existing lock order/transactions/lifecycle remain. Real MySQL lifecycle tests cover WAITING/SENDING/UNKNOWN, different anchors/accounts and actor isolation. |
| T2, C-4 | `DelayedManualReplyController.kt` validates current SENT OUTBOUND/contact/account/Message-ID and returns restore-only typed source/attachment data after owner/admin FULL checks. READ/public task projection is unchanged. |
| T3, C-5/C-6 | `mailbox-chat.js` reuses the existing editor/cards/transport with a distinct local legacy marker, skips saved-draft-only persistence/binding, omits draftRef, explicitly submits the frozen actual anchor and current cancelled revision, preserves the original UUID for unchanged content, and invalidates raw reuse on text or HTML edits. |
| T3, C-7 | Restore checks current timeline job revision, not a captured obsolete message object. It captures owner/epoch/target/editor revision; deliberate inbound mounting tracks the mounted host and intervening input instead of comparing against the removed ordinary editor. Context changes or edits prevent late restoration. |
| S-C1/S-C2 | No DOM/CSS design change. `index.html` changes only the existing resource keys to `20261105-manual-reply-source`. `mailboxChatStyle.test.js` retires only its indirect old06-key binding and still asserts twenty references, a single shared version, literal CSS blocks and real dialog DOM. |
| T4 | `DelayedManualReplySnapshotTest.kt`, `DelayedManualReplyControllerTest.kt`, `DelayedManualReplyLifecycleIT.kt`, `mailboxDelayedReply.test.js` cover the source/compatibility/permission/lock/editor contracts. Existing inbound raw/text/format SMTP behavior was exercised again, not inferred from old06 evidence. |

The exact eleven paths are recorded in `commit-result.json` and `identity-final-source.json` in the archive. No worker delivery code, migration, public revision/result DTO, dependency wiring or YAML activation changed.

### Failure/interruption history retained

1. Prior writer failure: focused31/30/1, stale revision restored raw input (`artifact://2674`), and unavailable bg_166 compilation result. Neither was rerun merely to confirm it. The current-timeline lookup fixes the known regression and retains its test.
2. First resumed complete command batch: the three Maven commands exited1 at test compilation because `DelayedManualReplySnapshotTest:291` attempted a cross-module smart cast. Fixed with a local explicitly cast restore value in the authorized test. No JVM tests ran in that failed batch.
3. The same batch's focused JS31/31 and syntax succeeded; fullJS2074/2073/1 exposed `mailboxChatStyle.test.js:610` indirectly pinning the old key through06's plan. Exact failure was reported before any out-of-scope edit. A25/A26 then authorized the narrow eleventh-file compatibility change. Original logs are `command-1.log` through `command-6.log`.
4. An intermediate amended check runner (`bg_168`, `final-checks/`) was cancelled when actual browser smoke exposed the inbound host-mount comparison problem. It is not claimed as a completed final run. The authorized mailbox consumer was corrected and two regressions added for intentional mount acceptance versus intervening input. No app.js edit or weakening of owner/revision guards was needed.
5. Fixture-only diagnostics are retained: a nullable placeholder without fallback was rejected, then corrected to `${expertName|Professor}`; an imported user1 password assumption failed, then the isolated user's password was set to the isolated admin fixture hash; two diagnostic SQL column guesses and a browser selector-readiness timeout were corrected. Native Cmd-A did not select the intended text, so final smoke used an actual DOM selection followed by native typing/bold controls. No product exception or safety gate was suppressed.

### Fresh final commands

All six were run after the last product/test edit, under the current amended plan, by `bg_169`; all exited0. Full logs and exact elapsed times: `final-checks-after-mount/command-{1..6}.log`, `required-commands-final.json`. Tests were not substituted with the earlier partial runs.

| Exact command | Observed result |
|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile` | BUILD SUCCESS, exit0; compilation only, no test execution claimed. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,PendingMailOperationServiceTest` | JVM82: Snapshot14, Controller16, Worker9, Pending43; failures0/errors0/skips0. Maven-bound fullJS2076/2076/skips0 also ran; BUILD SUCCESS. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true` | Real isolated MySQL IT44/44, failures0/errors0/skips0. Maven-bound fullJS2076/2076/skips0; BUILD SUCCESS. |
| `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` | 86/86, failures0/skips0. |
| `node --test src/test/js/*.test.js` | 2076/2076, failures0/skips0. |
| `node --check src/main/resources/static/mailbox-chat.js` | exit0. |

Additional final `git diff --check` exited0. No full-Maven-suite or Flyway migration-IT claim is made. Known unrelated MailReviewQueryIT/tzdb baselines were not re-established.

Environments: required Maven commands explicitly use Zulu11.0.15 (`Zulu11.56+19-CA`), Maven3.9.11; Node v25.7.0. Real Spring smoke uses installed Zulu11.0.32.1 (`Zulu11.90+205-CA`) for the known tzdb requirement, still Java11. Browser: actual headless Chrome156 via CDP. Runtime database: MySQL8.0.46, owned container `ab218e23f9a218438da55c2aabacb5285be53a227d10c4233867b590152b8ae6`. Exact version output, startup arguments and ownership are archived.

### Actual isolated A-C1–A-C3 evidence

- Infrastructure: fresh owned MySQL `resume07-source-contract-mysql` on loopback33386, Spring18086, SMTP25286, Chrome CDP19286 and local browser control19386. ES is deliberately loopback port9, LLM/automatic scheduling/RabbitMQ/discovery providers disabled. No production services or early07 public enqueue were used. The old archive supplies replay infrastructure/baseline inputs only, not passing results.
- **A-C1:** `Source07Runtime.kt` calls real preparation, the actual snapshot factory and03 lifecycle to create nine draftless OUTBOUND jobs. Original requests have null explicit anchors and null/blank scope; uploads use the real attachment API. Actual HTTP cancel is followed by inserting later SENT records. Browser restoration displays the original ordered attachments and body. The final unchanged request retains its UUID, omits draftRef, uses revision2 and explicitly names original anchor7010 rather than newer7011. `final-unchanged-browser-send.json`, `unchanged-restore.json`, original `.snapshot.json`, `cancel-api.json` and DB receipts preserve this chain.
- **Disabled-path limit observed precisely:** unchanged cancelled content is still blocked by the existing synchronous guard, with SMTP0 for that request. Its current HTTP envelope is500 `INTERNAL_ERROR` wrapping `422 UNPROCESSABLE_ENTITY "该内容已发送失败，请修改内容后重试"`. This is not claimed as successful public requeue or a new delay API result; the unchanged compatibility gate remains in place for07 activation. No exception mapping change was added outside this source-contract scope.
- **A-C2 edited delivery:** final-code native text, format-only and attachment-removal actions each produce a new request UUID, preserve the old actual anchor/revision and omit draftRef. Real HTTP returns SENT, real SMTP captures MIME, and MySQL records successful sends. `final-{text,format,attachments}-browser-send.json`, `final-browser-edit-summary.json`, `mime-decoded.json` and `.smtp-data.eml` assert final plain/HTML content, old In-Reply-To, filename order and byte hashes. Unchanged two-attachment ordering is retained; removing the first attachment sends exactly the remaining second attachment.
- **A-C2 conflicts:** ordinary browser typing autosaves a genuine DRAFT for705; a separate authenticated API session advances it to version2. Restoration preserves the local input and the server's higher version; the cancelled snapshot remains readable. `ordinary-conflict-browser.json`, `ordinary-second-session-save.json`, `higher-version-conflict.json`. Final DB shows ordinary drafts only for the intentionally edited conflict/stale-editor targets705/706, not restored legacy send targets701–704.
- **A-C2 stale responses:** actual server responses were held by CDP, never fabricated. Final-code contact switch and account switch preserve the current target; real03 requeue changes the timeline revision before an older restore response is released, and no old body appears. Native editor input during a held response survives. Logout aborts the held request and leaves no restored editor across owner/session epoch change. Receipts: `final-stale-contact-browser.json`, `final-stale-account-browser.json`, `final-stale-revision-browser.json`, `stale-editor-browser.json`, `stale-session-epoch-browser.json`. The final revision race used real lifecycle transitions, not synthetic WAITING JSON.
- **A-C3 permissions/source:** actual admin and original-owner FULL restore200; nonowner FULL and owner READ restore403 with no private restore data; READ public projection200. Changing the actual source account/contact/Message-ID/direction/send status rejects422; deleting the original source also rejects422 despite the newer SENT alternative. `permission-source-api.json` preserves all requests/results, including the initial unauthenticated fixture attempt rather than hiding it. Actual READ browser has no restore/manual-send controls or raw placeholders: `read-browser.json`, `read-owner-1440.png`.
- **Old inbound/F06-01:** actual old cancelled INBOUND job5 restores through the existing detail adapter. Fresh unchanged/raw, text-edited and format-only sends all return SENT and reach SMTP with their current bodies. Raw is preserved only on the unchanged path; edited requests do not resurrect historical template/assembly content. `inbound-{unchanged,text,format}-browser-send.json`, `inbound-fresh-browser-summary.json`, MIME receipts. These were run after the mounted-host fix.
- **S-C1:** final desktop1440 and mobile390 screenshots use the original editor/attachment cards. `final-format-390.png`, `final-format-1440.png`, `final-sizes-390.json`; measured document width equals390 with no horizontal overflow. Actual screenshots were inspected. No CSS/DOM redesign or injected preview substitute was used.
- SMTP archive contains nine actual messages total: three earlier outbound diagnostic sends, three final-code inbound sends and three final-code outbound sends. Historical diagnostic captures are distinguishable from the final receipts, not counted as a separate final acceptance matrix.

### Durable evidence and cleanup

- Archive: **`local://resume07-source-contract-epoch1-evidence.tar.gz`**.
- SHA-256: **`4a94100ee5ba7e01dbece25e6e386de9c1aeefc54ff9704c29d4e5912c8c5eb8`**; size4,288,331 bytes; prefix `resume07-source-contract-evidence/`.
- Index: `local://resume07-source-contract-epoch1-evidence.json`.
- Includes source snapshots of the eleven committed paths, plan/source hashes, commit/gate receipts, all failed/interrupted/final command logs, runtime fixture and startup scripts, original requests/snapshots, browser commands/network/results/screenshots, MIME bytes/digests, final isolated DB dump, ownership, exact environments and cleanup receipts.
- Owned Spring, SMTP, Chrome and browser controller all exited0; the owned MySQL container was removed. No unrelated service/container was changed. Temporary Chrome profile was removed. Throwaway fixture source/classes live only in archived evidence, never in the product commit.
- The source audit and old06 durable archive remain references for provenance; none substitutes for this execution's fresh commands/runtime.

### Freshness / handoff

- Current approved child/master hashes rechecked unchanged: YES.
- Target branch/worktree and product-commit reachability checked: YES.
- All current-plan required commands freshly completed after final edit: YES.
- Authorized source/test scope only: YES, eleven files under A25/A26.
- Product/index clean; execution evidence intentionally left for controller commit: YES.
- No remaining known in-scope failure. Public enqueue/real60-second/restart/masterA1–A12 integration remains the later07 activation obligation, not a waived acceptance criterion.
- Next: fresh distinct child verifier. Do not interpret READY_FOR_VERIFICATION as independent PASS, whole-plan acceptance, merge or release authorization.

## Epoch 1 — Terminal evidence closure

- Base: 4e64c1f4da4eb5acdac282731f58f2798ab9ff5d
- Implementation: a9c09c9abf8cf08707d86514b168318341716126
- Code head: a9c09c9abf8cf08707d86514b168318341716126
- Fix round: 0
- Fix commits: —
- Terminal verifier: Verify07SourceContract
- Terminal result: LIGHT_PASS_WITH_NOTES
- Required action: COMPLETE_CHILD
- Fresh independent required commands: compile exit0, JVM82/82, MySQL Lifecycle44/44, focusedJS86/86, fullJS2076/2076, syntax exit0; all tests skipped0. Source report and original output locations are in verify-log; controller did not rerun product checks.
- Independent actual runtime: six browser/API→SMTP deliveries, fourteen upload/download byte proofs, current source/permission and held-response races, actual1440/390 surfaces; exact transfer-line-ending limits and fixture history retained in the verifier report.
- Evidence: `local://verify07-source-contract-epoch1attempt1fix0-evidence.tar.gz`, SHA256 `7ade55b53a4d13311efed5dfb6aea08f285327b5c033b81557e5649d668beabd`. Owned runtimes stopped and scratch removed.
- O07SC-01 remains RECORD_ONLY: unchanged cancelled source is currently blocked by the pre-activation synchronous guard with observed500 wrapping422 and SMTP0; this is not public requeue acceptance. Subsequent07 activation must complete authoritative existing-job routing, real60 seconds, restart and all master acceptance. F06-01 remains closed by fresh inbound proof.
- Provider interruption, initial source/compile/cache failures and A25/A26 compatibility amendment remain same-epoch implementation history, not automatic repair rounds. No whole-plan review or review-fast-p performed.
