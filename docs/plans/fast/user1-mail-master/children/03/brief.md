# Fast-P Child Brief — 03

- Plan (exact approved contract): `docs/plans/2026-10-27/user1-mail-03-readonly-ui.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Master plan: `docs/plans/2026-10-27/user1-mail-master.md` (identity `commit:d66f54251af163f105c6244a6d06c4b28042518e`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Branch: `fast/user1-mail-master`
- Base / product boundary: `8f5b514871fdddf5e78f86bd26fe7e9575194a94` (child 02 Code head)
- Depends on: 01, 02 (verified). You are child 3 of 5.
- Read the child plan and master plan before editing. The child plan contains the exact DOM/CSS contract (S-1/S-2/S-3) — copy it verbatim where required.

## Backend interfaces available from children 01/02

- `POST /api/auth/login` body `{username,password}`; `GET /api/auth/me`; `POST /api/auth/logout` (204). login/me now return `username`, `mustChangePassword`, and derived `mailReadOnly:Boolean` (true only for exact `user1`).
- `GET /api/mail/mailbox/conversations?page=0&size=20&q=...` → `{items,total,page,size}`; for user1 the SQL already scopes items/total to granted experts only.
- `GET /api/mail/mailbox/conversations/{contactId}/messages?limit=50&before=<cursor>&accountCode=<optional>` → `{items,nextBefore,hasMore}`. `items[]` fields include `source,id,contactId,direction,accountCode,subject,body,cleanedBody,eventAt,sendStatus,processStatus,attachmentCount,firstAttachmentNames,tags,calendarAttachment,outboundAttachments`.
- Reader detail denial is HTTP 403 with `{code:"MAIL_READ_FORBIDDEN",...}`; 401 means the session is gone. Reader attachment fields are already fixed `0/[]/null/[]`.
- Do NOT call any other business endpoint. The 01 interceptor already returns 403 for them; the frontend must not request them at all.

## Master constraints for this child

- Master I-1: identity comes from session/me metadata, never from a query/username/role supplied by the page. `user1` is exact.
- Master I-2: the frontend never keeps mail bodies beyond the in-memory instance; switching expert/refresh/focus/older-page re-requests the server. 403 clears selected body and cursor.
- Master I-3: reader has no send/draft/attachment/material/note/admin tools and must not render them.
- Master I-4: admin path must not regress; do not rewrite or delete the existing MailboxChat/admin DOM contracts. The new branch sits before the old startup/navigation calls.
- Master I-5: this step keeps the live login allowlist admin-only; test with a pre-set reader session, not a real user1 login.
- Scope: modify ONLY the 7 files below. No new production behavior outside the plan; a required 8th file is a PLAN_CONFLICT.

## Authorized files (exactly these 13; 6 test-only files added by approved amendment A3/A4, commit d52463b)

1. `src/main/resources/static/mailbox-readonly.js` (new)
2. `src/main/resources/static/app.js`
3. `src/main/resources/static/index.html`
4. `src/main/resources/static/mailbox-chat.css` (append S-2 only)
5. `src/test/js/mailboxReadOnly.test.js` (new)
6. `src/test/js/authFlow.test.js`
7. `src/test/js/mailboxChatStyle.test.js`
8. `src/test/js/discoveryPapers.test.js` (asset list/count only)
9. `src/test/js/discoveryReview.test.js` (asset list/count only)
10. `src/test/js/mailboxChatBehavior.test.js` (asset list/count only)
11. `src/test/js/mailboxGroupPush.test.js` (asset list/count only)
12. `src/test/js/meetingConfirmationAssets.test.js` (asset list/count only)
13. `src/test/js/trustReplyWorkbenchSharedMount.test.js` (asset list/count only)

Do NOT modify `mailbox-chat.js`, existing CSS rules, or admin DOM ids/actions. For files 8–13, only the mechanical 18→19 asset list/count sync and insertion of `mailbox-readonly.js` immediately before `app.js` are authorized; keep every other assertion intact.

## Child invariants (verbatim from the plan)

- I-1: login/me pass `username` + `mailReadOnly` to the host; for exact `user1`, missing or non-true metadata shows the fixed error "权限信息异常，请重新登录" and must NOT enter the admin branch. Reader default `state.view=mailbox`; desktop/mobile/navigation functions only allow mailbox.
- I-2: `MailboxReadOnly` only GETs list/detail; logout is the separate auth POST. It never mounts MailboxChat/workbench/materials/contact detail; never loads drafts, sender accounts, group-push or auto-reply summaries; never writes body to localStorage/sessionStorage. Missing module shows "只读邮件视图加载失败，请刷新"; no fallback to the old module.
- I-3: current body lives only in this instance; expert switch, refresh, focus/visible, older page all re-request. 403 clears body+cursor immediately; 401 clears and returns to login; network error never restores old body. Requests carry instance/session generation; late responses after destroy/identity change must not render. Identity switch hard-reloads the page.
- I-4: display `summary` name/email plus message subject/direction/time/`cleanedBody` non-empty else `body`; all dynamic text via `textContent`/unified escape, no unsanitized HTML, no attachment/internal download links, no remote images, no auto-linked URLs. Search is `q` only.
- I-5: admin keeps original startup/navigation/chat/draft/attachment behavior. All local `?v=` assets in index use the same new version `20261027-user1-readonly`; keep existing script order, new module before `app.js`.

## Style contract (exact, from the plan)

- S-1: reuse the existing `mailbox-chat.css` classes and the exact DOM skeleton in the child plan (`.mail-chat/.mc-experts/.mc-list-tools/.mc-search-row/.mc-expert-list/.mc-person/.mc-person-main/.mc-pager/.mc-conversation/.mc-header/.mc-identity/.mc-actions/.mc-scroll/.mc-timeline/.mc-message/.mc-body/.mc-empty/.mc-error`). Fixed copy: no grant → "暂无已授权的专家邮件"; no search match → "没有匹配的已授权专家"; no selection → "请选择左侧专家"; 403 detail → "暂无权限查看该专家邮件"; network → "加载失败，请刷新重试"; loading → "加载中…"; no mail → "暂无往来邮件".
- S-2: append the plan's exact CSS block at the end of `mailbox-chat.css`, scoped by `body[data-mail-read-only="true"]`; do not modify existing rules, do not add other classes/inline styles.
- S-3: reuse the existing app shell; reader keeps only the `data-view="mailbox"` nav tab with label 收发件箱; rebuild `#mobileCoreView` to a single mailbox option; `#mailboxConversationPanel > .panel-head h2` text becomes "收发邮件 · 只读"; set the body marker; add existing `mc-refined` class to `#view-mailbox`; mount at `#mailboxList`; keep logout/current user display. Do not create a fake standalone page.

## Downstream interfaces later children rely on

- `index.html` must contain `<script src="mailbox-readonly.js?v=20261027-user1-readonly"></script>` immediately before the versioned `app.js` tag. Child 04 will only bump all `?v=` values to `20261027-user1-mail-access` and must not reorder or remove this registration.
- `app.js` must keep `window.MailboxReadOnly` optional at load: admin mode must work unchanged if the module is absent.
- `mailbox-chat.js` remains untouched so child 04 can add the admin header grant control there.

## Required commands (run fresh; record exit codes and counts)

- `node --check src/main/resources/static/mailbox-readonly.js`
- `node --check src/main/resources/static/app.js`
- `node --test src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxOutboundAttachments.test.js`
- Final: `node --test src/test/js/*.test.js` (baseline 1851 pass, 0 fail at this branch's base; no new failures allowed)

For the style/visual contract (S-1/S-2/S-3, 1440px and 390px), use real DOM/CSS assertions from the true `index.html` and CSS files. If a real browser capability is available, also exercise the actual page and record the viewport evidence; if not, state that limitation explicitly in the report instead of claiming browser verification.

## Output

- Commit the implementation locally as `feat(fast-p): implement 03`.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Write the full result to `docs/plans/fast/user1-mail-master/children/03/execution.md` (untracked; the controller commits it separately).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
