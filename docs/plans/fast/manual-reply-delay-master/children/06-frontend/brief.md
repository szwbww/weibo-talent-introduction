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

