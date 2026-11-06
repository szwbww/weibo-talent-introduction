# Child Brief — 06-frontend

## Identity
Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master; branch fast/manual-reply-delay-master. Product base is the terminal 05-query Code head supplied at dispatch, not its evidence commit. Do not start before the clean 05 checkpoint. Read master docs/plans/2026-11-04/manual-reply-delay-master.md and exact child docs/plans/2026-11-04/manual-reply-delay-06-frontend.md (including the full literal CSS/DOM contracts). Commit subject `feat(fast-p): implement 06-frontend`.

## Authorized files
1. src/main/resources/static/app.js
2. src/main/resources/static/mailbox-chat.js
3. src/main/resources/static/mailbox-readonly.js
4. src/main/resources/static/mailbox-chat.css
5. src/main/resources/static/index.html
6. src/test/js/mailboxDelayedReply.test.js (new)
7. src/test/js/mailboxServerDrafts.test.js
8. src/test/js/mailReviewAdmin.test.js
9. src/test/js/mailboxChatStyle.test.js
10. src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt (A16: restore-only authoritative source contract)
11. src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt (A16: source return/validation and preserved permission regressions)
12. src/test/js/mailboxChatBehavior.test.js (A18: existing clock/timer harness compatibility, original assertions preserved)
13. src/test/js/mailboxReadOnly.test.js (A18/A20: clock/SVG/timer harness compatibility; delete superseded focus-no-refresh assertion, preserve other DOM/scroll/access-coalescing assertions)
14. src/test/js/mailboxMailReadAccess.test.js (A20: performance and single-macrotask timer drain only)
15. src/test/js/mailboxOutboundAttachments.test.js (A20: performance and single-macrotask timer drain only)
16. src/test/js/mailboxReplyIdentity.test.js (A20: directly required extractFn closure fixture dependencies only)
17. src/test/js/mailboxSuspension.test.js (A20: performance and single-macrotask timer drain only)
18. src/test/js/meetingConfirmationIntegration.test.js (A20: performance and single-macrotask timer drain only)
19. src/test/js/authFlow.test.js (A22: directly required legacyDelayedReplies Map sandbox dependency only)
20. src/test/js/mailReviewReader.test.js (A22: remove superseded focus-only-access assertion/start capture, preserve editor/content/CAS/reload assertions)
No other files, no plan modifications. Scope expansion requires a specific PLAN_CONFLICT, not silent test weakening. Prior reports are under docs/plans/fast/manual-reply-delay-master/children/01-storage through 05-query.

## Required behavior and invariants
Execute child T0–T4, I-6/I-7/I-9/I-10 and S-1–S-4 exactly.
- Both app submit helpers and both host adapters distinguish WAITING from SENT. WAITING text is exactly 已加入发送队列，60 秒内可撤回; do not clear QA/RAG/assembly/adoption context or saved draft. Preserve boolean return contract with optional onResult callback. Async SENT cleans only captured matching owner/target/seq; never another expert/new draft.
- WAITING/SENDING/UNKNOWN lock editor/autosave/discard/send; cancellation rereads DRAFT, REVIEW remains APPROVED read-only; LEGACY restores original editor input without overwriting a newer draft. Use existing action dialog on conflict. Cancelled resubmit carries authoritative revision recovered after refresh.
- Footer translation-row actions use same-line two-stage confirmation (open0 POST, dismiss0, repeated confirm1). Bind confirmation to user/account/contact/job/revision/action; expiry/revision changes invalidate. 409 rereads server, never automatic repeat POST; 401/403 dispose current access state. Keep network uncertainty and drafts, do not infer SENT.
- Server canCancel/canRetry plus current permission control actions; reader whitelist exact methods/numeric routes only. Unknown/permanent/historical no executable retry.
- List failure icon uses failedCount including nonlatest failures, admin and reader surfaces. Dynamic content escaped. Literal child CSS blocks appended, existing CSS untouched; fixed SVG, tooltip keyboard/aria, no new undeclared classes, inline styles, overlay confirmation or preview-script injection. Cache key 20261104-manual-reply-delay for all existing versioned index references, unchanged reference count.
- One lifecycle poll schedule per host: visible list<=5sec without selection, waiting detail2sec/other5sec, countdown1sec via textContent/performance.now server offset. No overlap. Hidden pauses UI polling only; focus/visibility resume promptly. Owner/query/page/mount generations reject stale responses. Unmount/logout/loss clears timers/abort/confirmation; no change-notification refresh loop.

## Required verification
Run after implementation is complete:
1. `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailReviewAdmin.test.js src/test/js/mailboxChatStyle.test.js`
2. Existing affected reader, attachment, meeting, identity and behavior tests (derive exact files from repo, do not guess; report commands).
3. `node --check` for each modified JS file.
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplyControllerTest` (A16; after all edits).
Browser verification is mandatory against real 04/05 interfaces and isolated SMTP: 1440px/390px screenshots, keyboard tooltip/confirmation, >=44px hit targets, no overflow, refresh/switch/logout/hidden state. Do not inject fake data and claim integration passed. If prerequisite fails, record exact failure, do not reduce acceptance. No production database/SMTP. Read browser tool docs before use; close tabs. Remove throwaway harnesses after proof.

## Delivery
Use execute-p. One writer, no push/merge/rebase/amend/reset. Commit only authorized product/tests, exclude fast-p artifacts. Append report with agent/base/head, real commands/counts, browser evidence paths, unexecuted items and conflicts to docs/plans/fast/manual-reply-delay-master/children/06-frontend/execution.md. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit, command summary and report path.

## Epoch 2 — A15/A16 restore source contract resume

- Master/06 identity `commit:c4bea7830eacbaafdd933de5461c05de53066997`; product base223b8e4aed8852734552ef662c3b732c90e60e42. Epoch1 made no product edits. User selected 「授权本次及同类契约补齐」, including06's11file exception.
- ImplementT0 first: restore-only nullable trailing inboundProcessingId for LEGACY, validated positive/currentsource/contact/account/snapshot identity; never confuse mail_record.id/source_inbound_id with processing id. PublicDelayedManualReplyView unchanged; READ cannot restore, REVIEW no editablecopy, oldmissingoriginalInput422 retained. Only server-returned source ID may choose editor afterrefresh; no job/body/latestmail inference.
- Required controller regression plus existingNode/browserproof all stillrequired. Completed01–05logs stayunchanged. Actual06browser uses real04/05API+isolatedSMTP anddesktop/narrowscreenshots; noearly07activation/fakeUI.
- A5 existing-test compatibility, A11 internal visibility/pure-batch-read and A15 narrowly necessary missing source/status/response contract completion are preapproved only within their explicit master bounds. Report exact evidence/files/interface to controller for recorded amendment before unlisted edits. No new business/compatibilitybreak/schema/safetyweakening.
- Append epoch2execution; fix_round0. Continue through07aftercleancheckpoint; neverreview-fast-p.


## Epoch 2 — A17/A18 harness and factual baseline correction

- Latest master/06 identity `commit:801bb85329dab0ddfa06e1b93e124a6c4bf20fe9`. Prior A5/A15 authority applied to exact reported compatibility evidence; sameepoch2/fix0.
- Authorized set13files: prior11 plus two existingJS test harnesses only for performance.now/SVG namespace/timer cleanup. Preserve original assertions; include both in affected test command set and ensure Node exits.
- Current index has20existing versioned references (7CSS+13JS), not originalplan's mistaken19. Preserveall20, samecount and uniform20261104-manual-reply-delay; no resource removal. Include actual full grep receipt in execution. LiteralCSS/DOM contract otherwise unchanged.


## Epoch 2 — A19/A20 copied harness compatibility

- Latest master/06 identity `commit:4d8e42200b8c61ca49f51dd62ac4416d72ae2231`, authorized18files; added5existingtest fixtures only underA5. Actual affected test432/436 and exactcopiedharnessgrep are evidence; retain them and interruptedMaven honestly.
- Fix mandatoryclock, execute one current macrotask snapshot perdrain (do not discard callbackunderassertion), directextractFnclosuredependencies andproperteardown. Preservebusinessassertions. ExistingReadonly obsolete focus-does-not-refresh assertion is removed, not count4→6 repinned; DOM/scroll/accesscoalescing remains.
- All newcompatibilityfiles enter affectedtestcommandset; final fresh mandatorychecks/browser stillrequired. No new delayed-jobUNKNOWN lock weakening; record any legacy-attempt distinction precisely forverifier. Sameepoch2/fix0.


## Epoch 2 — A21/A22 observed auth/review compatibility

- Latest master/06 identity `commit:a5c1ddb90e92b5aef7bf8f16b031dd4d51c49b80`, authorized20files; two newexistingtestpaths only underA5. FullJS2037/2054 failures/controller13pass fromartifact2339 remain recorded, not overallpass.
- authFlow gets onlymissingMap closurefixture; mailReviewReader obsolete only-access focusassertion/startcapture removed, not repinned. All otherauth/editor/content/CAS/reloadassertions retained. taskActivityCenter.test.js staysrun-only; ownnewtest duplicatedkey fix withinoriginalscope.
- Addboth to affectedtests, obtainfinalfreshchecks/browser. Sameepoch2/fix0, no productauthority expansion.

