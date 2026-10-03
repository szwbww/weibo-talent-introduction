# 02 验证日志（append-only）

（待验证者填写）

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 02 (docs/plans/2026-10-03/mailbox-suspension-02-frontend.md)
Boundary: 069df6ab04ba446c012a53b163f9662297c966a4..0837c372f45d69374084113d8258d8c3320d748c
Verifier: VerifyMailboxSuspension02

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|1 Authorized-file scope|PASS|`git diff --name-status 069df6ab..0837c372` = exactly 6 files: index.html, mailbox-chat.js, styles.css, mailboxChatBehavior.test.js, mailboxSuspension.test.js(A), mailboxSuspensionStyle.test.js(A). app.js / mailbox-chat.css / trust-reply-workbench.js / other tests / Kotlin not in range.|
|2 Plan requirements + I-1..I-7 + S-1..S-4|PASS|Code+test evidence below. S-4 block in styles.css is byte-identical to evidence/target-suspension.css (len 9395, sha256 00289a6c0a550c9c2946f304bfc00ca48f14767b08e82a2e818b7c571bde7da2; block sits at file end, one trailing NL; diff hunk only `@@ -12642,3 +12642,53 @@`, numstat 50/0). mailbox-chat.css `cmp` exit 0. index.html diff is exactly 11 `?v=` lines: before 10×`20261003-mobile-core-03`+1×`20261003-mobile-core-03-generic-followup`, after 11×`20261003-mailbox-suspension` (`rg -o '\?v=' | sort | uniq -c` = 11 same); `rg 20261003-mailbox-suspension src/main/static/*.js src/test/js/*.js` = none (no test fixed literals); `rg 20261003-mobile-core src/test/` = none.|
|3 Required commands (fresh, cwd=worktree)|PASS|`node --check mailbox-chat.js` exit 0. subset (mailboxChatBehavior+mailboxSuspension+mailboxSuspensionStyle+mailboxChatStyle) tests 207/pass 207/fail 0 exit 0. full `node --test src/test/js/*.test.js` tests 1421/pass 1421/fail 0 exit 0. `cmp mailbox-chat.css evidence/baseline-mailbox-chat.css` exit 0. Baseline 1385/1367/18 → +36 new tests (25+11), the 18 cache-key failures resolved.|
|4 Downstream interface match|PASS|JS consumes 01/01b shapes: chip→`suspendedOnly=true` (`chipParams` :506); conversationsParams sets `suspendedOnly=true` (:1481); list item fields `suspended/suspendReason/suspensionPendingCount` (:1824-1838); state obj `suspended/suspendReason/suspensionPendingCount/followed` (:1869-1872); PUT/DELETE `/…/conversations/{id}/suspension`; POST `/api/mail/unmatched-inbound/{id}/mark-resolved` body `{note:null}` validates `{id,processStatus:"PROCESSED",resolvedBy}` (:4224-4255).|

Invariant spot-checks:
- I-1: FILTER_CHIPS fixed order :59-66 = 全部/关注/待处理/已挂起/已回复/待匹配. Instance default :612-621: no focus → CHIP_PENDING with `defaultProbe.active`; focus deep-link → pending/all, probe inactive. fetchList :1715-1728 switches to FOLLOWED only when first pending total=0, and re-queries; `freezeDefaultProbe` on user Tab/filter (:7812, :8353). Tab counts via page=0&size=1 (:1655-1674), failure hides span (:1640-1650), no fake 0.
- I-2/I-3: recomputeCompletionLine (:1890-1915) requires `suspended && count===0`; anchor =本次 pendingAnchor if PROCESSED else last PROCESSED inbound; no anchor → no line (only banner). Completion line rendered inside `<article>` after `<footer>`, keyed by `completion.anchorKey === key` → at most one per expert (:3845-3856). keepSuspension writes nothing (:2315-2320); endSuspension exactly one DELETE (:2251-2268). Test asserts DELETE=0 before end, =1 on end, N>0 → no line.
- I-7: `mc-mark-resolved` → openProcessConfirm only sets state, no POST (:2324); `mc-process-confirm` → markResolvedByKey POST `{note:null}` (no resolvedBy/operatorName); success only updates PROCESSED then GETs suspension. Test 第一次点击/取消 = 0 POST, 确认 = 1 POST with exact body. No added line uses openActionDialog/showModal/alert/confirm/prompt (`git diff | rg '^\+.*(dialog|alert|confirm|prompt)'` = NONE); `openDialog("mark-unmatched-resolved")` removed from markResolvedByKey.
- S-3 Escape: document-level onDocumentKeyDown (:8138-8153) cancels reason form / process confirm. Buttons `type=button`; reason `maxlength=500`, trim, PUT `{reason: trim||null}`; escapeText rendering.
- New-DOM-id rule: no new ids introduced; mailboxSuspensionStyle.test.js asserts `id="view-mailbox"` / `id="mailboxList"` exist in index.html source.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (implementer deviation a): auth-failure feedback is deferred to a click on an already-disabled control. When `/api/auth/me` fails/absent, `instance.auth.ready=false` → the new suspend/process buttons carry `disabled` (mailbox-chat.js:1934, :3825) and the "登录状态读取失败，请刷新重试" path is only reachable from `onSuspensionClick`/`openProcessConfirm` guards (:2216, :2328). In a real browser a disabled button dispatches no click, so the message may never surface; the unit test passes only because the DOM stub fires the handler on a disabled node (mailboxSuspension.test.js:1600-1609). No four-gate violation: I-7/T3 do not fix the display stage and the plan does not uniquely define a mount-time repair (implementer notes mount-time surfacing would trip the non-authorized `mailboxOutboundAttachments.test.js`), so no AUTO_FIX.
- O-2 (implementer deviation b): T4 real-browser evidence is missing for "结束挂起 → exactly 1 DELETE + post-result Tab switch" and for the failure-retry path; implementer reports these as unit-tested only. This is a process/evidence gap, not a proven product defect and not repairable by an authorized code change, so outside the light gate.

### Required Action
- COMPLETE_CHILD

