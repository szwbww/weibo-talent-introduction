# Fast-P Child Brief — 04

- Plan (exact approved contract): `docs/plans/2026-10-27/user1-mail-04-admin-grant-ui.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Master plan: `docs/plans/2026-10-27/user1-mail-master.md` (identity `commit:d52463bb0c530b6ab5ac83c27cd34c321b5b651f`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Branch: `fast/user1-mail-master`
- Base / product boundary: `5c104aa301505e9db8a84e1649d60cbf290f3f00` (child 03 Code head)
- Depends on: 01, 02, 03 (verified). You are child 4 of 5.
- Read the child plan and master plan before editing. The child plan contains the exact header DOM and state table — copy it verbatim.

## Backend interfaces available from child 01

- `GET /api/mail/mailbox/conversations/{contactId}/read-access/user1` → 200 `{username:"user1",contactId,canRead}`.
- `PUT` same path → 200 `{...,canRead:true}` (idempotent grant).
- `DELETE` same path → 200 `{...,canRead:false}` (idempotent revoke).
- Admin-only: anonymous 401, non-admin 403 `ADMIN_REQUIRED`; missing expert 404. Send the session cookie exactly like the existing `hostApi()`; never put actor/role/user into the body.

## Master constraints for this child

- Master I-1: permission/identity comes from the server session; never from DOM/query.
- Master I-2: no grant row = no body; the UI reflects server state, never optimistic truth.
- Master I-3: reader stays in the child 03 read-only module and must make no grant request.
- Master I-4: admin existing header actions and all existing click paths stay intact; the grant control only calls the 01 endpoints and never touches follow/suspend/mail/draft.
- Master I-5: order 01→05; this step still keeps user1 login closed. Do not change AuthController.
- Scope: modify ONLY the 3 files below. No new CSS, no inline styles, no new files beyond the one test. Any required 4th file is a PLAN_CONFLICT.

## Authorized files (exactly these 3)

1. `src/main/resources/static/mailbox-chat.js`
2. `src/main/resources/static/index.html` (version bump only; preserve child 03's script registration/order)
3. `src/test/js/mailboxMailReadAccess.test.js` (new)

Do NOT modify `mailbox-readonly.js`, `app.js`, CSS, or any other test.

## Child invariants (verbatim from the plan)

- I-1: render this control only when `instance.user` is exactly `admin`; the target contactId is captured at click time; target is fixed `user1`; never read the post-request current selection; the DOM cannot supply the actor/role.
- I-2: first selection of an expert triggers a GET; disable the button while loading/writing; only the server-returned `canRead` may flip the UI; a failed GET must not default to granted/not-granted and must show the retry text; failed writes do not optimistically flip; a response whose username/contactId/canRead does not match expectations is an error state.
- I-3: every expert switch, unmount, and identity change invalidates the read generation; a response writes only when instance/user/contact/generation all match. A write may complete for the expert captured at click time but must never be painted onto a newly selected expert; returning to the old expert re-GETs.
- I-4: do not replace the original header actions or delete existing `data-action`s; the new control only calls the 01 API; reader keeps zero grant requests; all local `?v=` assets in index move together to `20261027-user1-mail-access`, preserving the 03-added `mailbox-readonly.js` immediately before `app.js`.

## Style contract S-1 (exact)

- Existing `.mc-actions` (flex/wrap/gap 8), normal `.button`, `.mc-badge`; add NO CSS.
- Append these two nodes at the END of the original actions (all material/manage/schedule buttons remain):

```html
<button class="button" type="button" data-action="mc-toggle-user1-read-access" data-contact-id="当前专家ID" disabled>授权状态加载中…</button>
<span class="mc-badge" data-role="mc-user1-read-access-status" aria-live="polite">user1：读取中</span>
```

- Fixed copy/state matrix: loading → `授权状态加载中…` / `user1：读取中` / disabled; canRead=false → `授权 user1 查看` / `user1：未授权` / enabled; canRead=true → `取消 user1 授权` / `user1：可查看` / enabled; saving → `保存中…` / keep last confirmed badge / disabled; read or write failure → `重试授权状态` / `user1：授权操作失败，请重试` / enabled and only a GET retry. Never auto-resend a failed PUT/DELETE.
- Only textContent/disabled/aria-busy change; the contact id is escaped.

## Integration points (verified at this base)

- `mailbox-chat.js` `renderHeader()` rebuilds `.mc-actions.innerHTML` on every header render; the new nodes must be included in that template and current state applied without triggering a render→GET→render loop.
- `selectExpert()` increments `instance.seq`/`instance.convEpoch`; use those (or a dedicated generation) to invalidate in-flight reads. Unmount destroys the instance; late responses must not create/update nodes.
- Use the existing `hostApi()` so the session cookie and base path behave like the rest of the module. Click dispatch is the existing delegated action handler pattern in the same file.

## Downstream interfaces later children rely on

- `index.html` keeps `mailbox-readonly.js` immediately before `app.js` and moves all local `?v=` assets to exactly `20261027-user1-mail-access`. Child 05 only reads/builds; it must not need to re-register anything.
- The reader module and app.js remain untouched, so child 03's tests and admin behavior stay valid.
- `mailbox-chat.js` must still work with no reader session and with the grant endpoint unavailable (error/retry state only, no crash).

## Required commands (run fresh; record exit codes and counts)

- `node --check src/main/resources/static/mailbox-chat.js`
- `node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxServerDrafts.test.js`
- Also run `node --test src/test/js/*.test.js` once at the end to prove no cross-file regression (baseline at this base: 1894 pass, 0 fail).

For S-1, assert the generated DOM/class/hierarchy/fixed copy from the real `mailbox-chat.js` and, where relevant, the real `index.html`; no new CSS/inline style. If a real browser is available, exercise the admin header at 1440/390 and record it; otherwise state the limitation explicitly rather than claiming browser verification.

## Output

- Commit the implementation locally as `feat(fast-p): implement 04`.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Write the full result to `docs/plans/fast/user1-mail-master/children/04/execution.md` (untracked; the controller commits it separately).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
