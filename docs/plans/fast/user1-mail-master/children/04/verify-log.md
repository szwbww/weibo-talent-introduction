## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04 — docs/plans/2026-10-27/user1-mail-04-admin-grant-ui.md
Boundary: 5c104aa301505e9db8a84e1649d60cbf290f3f00..419f49ad6182dd1f62a1445c0f3cc582c21e1d5b
Verifier: Child04Verifier

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-only <base>..<head> -- src/` → exactly `src/main/resources/static/index.html`, `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxMailReadAccess.test.js`. No CSS file in diff; `-- *.css` diff empty. New JS block contains no `style=`/new selectors (grep of the added hunks for `style=` / `.mc-*{` returned nothing); test asserts `actions.innerHTML` has no `style=`. `mailbox-readonly.js` and `app.js` diff empty (head 419f49a).|
|Plan and invariants|PASS|I-1: `mailbox-chat.js:3695` `readAccessAdmin()` = `String(instance.user) === "admin"` (exact), `readAccessNodesHtml` (3729) returns `""` for non-admin/non-positive id/unmatched chip; target contact from `button.dataset.contactId` captured at click (`3818`), URL built from fixed `READ_ACCESS_TARGET="user1"` (`3685`); no actor/role in body. I-2: `beginReadAccess`→`loadReadAccess` GET on selection (`3498`, `3809`), `readAccessView` disables on loading/saving (`3711`), PUT/DELETE only after server-ok, `readAccessPayload` (`3773`) rejects non-`user1` username / mismatched contactId / non-boolean canRead; write success additionally requires returned canRead == intended grant (`3836`); failure sets `canRead=null`+`phase="error"` (`3840`/`3846`) with copy `重试授权状态` / `user1：授权操作失败，请重试`, and the error branch of `toggleReadAccess` (`3823`) only calls `loadReadAccess()`. I-3: `readAccessContextAlive` (`3761`) requires !disposed, same user, same state.contactId, same generation, and `selectedContactId===ctx.contactId`; generation bumped on selectExpert (`3502`), clearSelectedConversation (`3341`), identity refresh (`2759`) and unmount (`9934`). I-4: original actions template intact with new nodes appended last (`3667`); action dispatch adds only `mc-toggle-user1-read-access` (`9246`), reader untouched. S-1: `readAccessNodesHtml` emits `<button class="button" type="button" data-action="mc-toggle-user1-read-access" data-contact-id="<id>"[ disabled]>…</button><span class="mc-badge" data-role="mc-user1-read-access-status" aria-live="polite">…</span>` with the exact five-state copy matrix in `READ_ACCESS_TEXT` (`829-836`); mutations limited to textContent/disabled/dataset.contactId. Test-side S-1 assertions at `mailboxMailReadAccess.test.js:849-931` re-read real source/CSS and verify original actions precede the new nodes.|
|Required commands|PASS|`node --check src/main/resources/static/mailbox-chat.js` → exit 0. Focused 5-file run → exit 0, tests 285 / pass 285 / fail 0. Full `node --test src/test/js/*.test.js` → exit 0, tests 1911 / pass 1911 / fail 0 (base 1894 ⇒ +17 new, 0 regressions).|
|Downstream interfaces|PASS|`index.html:11-17,2518-2529`: every local `?v=` value is exactly `20261027-user1-mail-access`; `mailbox-readonly.js` at 2527 immediately precedes `app.js` at 2528; no residual `20261027-user1-readonly` in `src/main/resources/static/`. Reader module diff empty and test `I-4 reader 模块仍无授权入口/请求` asserts no `read-access`/`canRead` in `mailbox-readonly.js`. No test hardcodes either cache literal (grep for both keys under `src/test/js` → no matches); the I-4 test derives the key from `index.html` and enforces uniformity.|

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: I-3 requires generation invalidation on identity change; code handles it (`mailbox-chat.js:2759` via `clearSelectedConversation` + user-equality check in `readAccessContextAlive`), but `mailboxMailReadAccess.test.js` has no test that switches `instance.user` (admin→reader) while an expert is selected. Code path is correct by inspection; missing coverage is a test-quality gap, not a proven defect.
- O-2: `index.html:2517` `<script src="task-modal-runtime.js"></script>` carries no `?v=`; pre-existing at the base commit (`git show 5c104aa:…/index.html:2517`), untouched by this child and outside the "existing `?v=` assets" scope.
- O-3: Writer's 1440/390 probe was run against a generated header DOM replica in headless Chrome, not the real admin app; I did not exercise a browser. Real-browser layout remains covered only by manual checklist A-3, and the writer's probe is treated as supporting evidence, not app verification.

### Required Action
- COMPLETE_CHILD
