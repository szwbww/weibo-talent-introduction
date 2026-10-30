# Child 07 — execution

## Epoch 1 — PLAN_CONFLICT / direct stylesheet consumer prerequisite

### Identity and boundary
- Executor: `ExpertAccess07Impl`; execution epoch: NEW.
- Approval basis: current child dispatch, epoch-1 brief and standing human authorization; inherited DRAFT header is not interpreted as a withdrawal of approval.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md`.
- Plan SHA-256: `a33017f940c5865cf34354b4f80e644562e51d8e84478591f43d11d992c37d72`.
- Execution ID: the canonical plan path above plus `@a33017f940c5865cf34354b4f80e644562e51d8e84478591f43d11d992c37d72`.
- Approved immutable identity `commit:e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6` contains those same plan bytes. The brief's initial, pre-dispatch SHA is historical; its epoch-1 commit identity governs.
- Governing master: `8284ae04acaed6f7222774c8a4c6ac522f6690f0`, as dispatched.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`.
- Branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Pre-execution product SHA: `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`.
- Before/after HEAD: `00be03eb8c56f98f58fbf79ffee66aa80b6e332f` / unchanged.
- Post-execution product SHA: unchanged; no product edits or product commit.
- Initial dirty paths were controller-owned `children/07/brief.md` and `ledger.md`; preserved. This append is the worker's only repository mutation.
- Authorized product/test scope remains the exact original list in the epoch-1 brief. No unlisted implementation/test file has been edited.

### Prerequisite and controller decision

The direct stylesheet contract consumer `src/test/js/mailboxChatStyle.test.js` is outside the current authorized list. Its inherited reader tests make historical EOF placement part of their assertion and inspect the suffix by byte length. The new S-4 delegated-conversation rules must be introduced without treating that incidental historical EOF position as a permanent product constraint.

Controller decision received during this preflight: **do not preserve CSS EOF solely to satisfy the incidental ordering pin**. Standing authority permits a bounded fixture-only amendment after receipt completion. Controller owns the committed plan/brief/ledger amendment and new execution identity; this worker remains read-only for product/test files until that identity is dispatched.

Requested additional authorized file:

`src/test/js/mailboxChatStyle.test.js`

Bounded change:
- Retire the old reader `endsWith(s2Block.trimEnd())` assertion at lines 490–494 and its EOF wording.
- Retire the suffix-position extraction at line 504; do not replace either with a new last-block/order/line-count pin.
- Preserve the inherited reader CSS block byte-for-byte, its reader-scoped selectors and hiding behavior, the existing scope checks on that block, and all stable mailbox CSS/DOM/cache-key/no-inline-style/no-body-storage/admin checks.
- Cover the actual delegated S-4 rules, DOM and READ/FULL separation behavior through the already-authorized workspace/reader/chat tests. Do not expand styles, UI layout, endpoints or product scope.
- Fresh added-file gate after final implementation: `node --test src/test/js/mailboxChatStyle.test.js`; it also remains included in the required full JS suite and Maven-bound JS gate.

This is static preflight evidence, **not an observed failing test**. No test was run to manufacture or reconfirm a failure.

### Exact direct-consumer receipts

No LSP/reference tool is mounted in this worker's tool inventory; exact symbol/path searches and source reads were used.

#### R1 — EOF assertions being retired

Exact grep-tool pattern:
`endsWith\(s2Block|cssSource\.slice\(cssSource\.trimEnd|reply-drafts/1|for \(const path of|allows exact reader draft|S-2 全文`

Paths:
`src/test/js/mailboxChatStyle.test.js;src/test/js/authFlow.test.js`

Relevant complete matched stylesheet output:
```text
mailboxChatStyle.test.js:492: cssSource.trimEnd().endsWith(s2Block.trimEnd()),
mailboxChatStyle.test.js:493: "S-2 全文必须是 mailbox-chat.css 的最后一段，且不得改写"
mailboxChatStyle.test.js:504: const appended = cssSource.slice(cssSource.trimEnd().length - s2Block.trimEnd().length);
```

Read `mailboxChatStyle.test.js:449-588` established test intent and surrounding stable checks:
- Lines 490–500 assert old EOF plus the inherited reader-scoped selectors.
- Lines 503–515 derive the suffix by byte length, then inspect its selector scope and preserve the admin toolbar rule.
- Lines 517–532 preserve defined classes, no inline styles/remote attachment output/body persistence in the READ renderer.
- Lines 543–587 preserve script ordering/dynamic cache identity and real index shell nodes.
- Read `mailboxChatStyle.test.js:151-204` separately confirms the general mailbox-scoped CSS/class/no-inline-style checks accept a declared `.mail-chat` delegated class and need no scope relaxation.
- Read `mailbox-chat.css:133-223` confirms the inherited reader CSS is the current EOF block. Existing rules are not authorized to change.

#### R2 — cache-key consumers

Exact grep-tool pattern: `20261030-expert-access-01`

Path: `src/test`

Complete output:
```text
No matches found
```

Source search for `MailboxReadOnly|delegated-conversation|mountExpertManualProfile|isMailReadOnlyRequestAllowed|MailboxChat\.mount|202610|mailbox-readonly\.js` across `src/main/resources/static;src/test/js` and subsequent targeted reads established dynamic asset consumers in `mailboxChatStyle`, `mailboxMailReadAccess`, `mailboxGroupPush`, `mailboxChatBehavior`, `contactTimingStyle`, `discoveryReview`, `expertMeetingVideo` and `trustReplyWorkbenchSharedMount`. Index is the version-key source. Preserve the current asset sequence and same-key contract; there is no current-key literal fixture requiring authorization.

#### R3 — do not mistake an obsolete draft URL for a required API-test amendment

Exact grep-tool pattern:
`GET.*detail|MailReplyDraftController|reply-drafts|fun detail|@GetMapping`

Paths:
`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReplyDraftController.kt;src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`

Matched route evidence:
```text
MailReplyDraftController.kt:12: @RequestMapping("/api/mail/mailbox/drafts")
MailReplyDraftController.kt:14: @GetMapping("/target")
MailReplyDraftController.kt:23: @GetMapping("/{id}")
MailReplyDraftController.kt:24: fun detail(request: HttpServletRequest, @PathVariable id: Long)
MailReplyDraftController.kt:27: @GetMapping
MailReplyDraftController.kt:30: @GetMapping("/summaries")
MailReadOnlyPolicy.kt:80: AllowedHandler("GET", MailReplyDraftController::class.java, "target"),
MailReadOnlyPolicy.kt:81: AllowedHandler("PUT", MailReplyDraftController::class.java, "save"),
MailReadOnlyPolicy.kt:82: AllowedHandler("GET", MailReplyDraftController::class.java, "detail"),
MailReadOnlyPolicy.kt:83: AllowedHandler("DELETE", MailReplyDraftController::class.java, "discard"),
MailReadOnlyPolicy.kt:84: AllowedHandler("GET", MailReplyDraftController::class.java, "list"),
MailReadOnlyPolicy.kt:85: AllowedHandler("GET", MailReplyDraftController::class.java, "summaries"),
```

The R1 grep also matched:
```text
authFlow.test.js:1020: it("allows exact reader draft method/path pairs and rejects forbidden capabilities before fetch", async () => {
authFlow.test.js:1043: for (const path of [base + "/0", base + "/01", base + "/-1", base + "/x", base + "/1/",
authFlow.test.js:1045: "/api/mail/mailbox/reply-drafts/1"]) {
```

Read `authFlow.test.js:988-1056`: this rejected `/reply-drafts/1` is not the actual personal-draft controller URL. Do not weaken that stable malformed/unlisted-path check or request unrelated edits to it. Actual `/drafts` routes must be added through the exact frontend method/path contract and tested behaviorally in the authorized workspace test.

#### R4 — existing reader-review fixture shares the authorized response helper

Exact grep-tool pattern:
`require\(["']\./mailboxReadOnly|mailbox-readonly\.js|loadModule\(|detailResponse\(`

Path: `src/test/js`

Relevant output:
```text
mailReviewReader.test.js:29: if (url.includes("/messages")) return detailResponse([...]);
mailboxReadOnly.test.js:15: const modulePath = path.join(ROOT, "mailbox-readonly.js");
mailboxReadOnly.test.js:267: function detailResponse(items, extra = {}) {
```

Read `mailReviewReader.test.js:1-55` shows it evaluates the helper prefix from `mailboxReadOnly.test.js`, including `detailResponse`. Read `mailboxReadOnly.test.js:255-273` shows the helper currently omits accessLevel. The already-authorized helper can provide an explicit READ response and support explicit test overrides; production must not infer FULL/default security authority from a missing field. No separate review-reader fixture edit is established as necessary.

#### R5 — profile wrapper must be included, not bypassed

Exact grep-tool pattern:
`createExpertManualProfileSession|expertManualProfileShellTemplate|mountExpertManualProfile\(`

Paths: `src/main/resources/static;src/test/js`

Relevant source matches:
```text
app.js:11679: function expertManualProfileShellTemplate() {
app.js:11699: function createExpertManualProfileSession(host, context) {
app.js:12141: function mountExpertManualProfile(rootEl, context) {
app.js:12148: const session = createExpertManualProfileSession(host, context || {});
app.js:12150: host.innerHTML = expertManualProfileShellTemplate();
app.js:13931: mountExpertManualProfile(contactDetail, {
app.js:14577: mountExpertManualProfile(contactDetail, {
contact-details.js:11: expertManualProfileShellTemplate=function(){
```

Read `contact-details.js:10-25,68-81` proves production overrides both the shell and mount/jump/unmount wrappers: the default shell adds meeting records/contact-info; mount invokes reorganizeContactPanel/ExpertMeetingRecords; unmount globally unmounts meeting records. The delegated marker must bypass those side effects as well as the shell/video/history mount, while leaving default admin behavior unchanged. `app.js:11626-11780,12141-12170` contains the shared field/editor/session code to reuse. These source files are already authorized.

Relevant test matches from the same search:
```text
expertContactSharedNote.test.js:811: const shell = sandbox.expertManualProfileShellTemplate();
expertContactSharedNote.test.js:868: const shell = harness.sandbox.expertManualProfileShellTemplate();
expertMeetingVideo.test.js:1028: assert.ok(extractFunction("expertManualProfileShellTemplate").includes("expertMeetingVideoPanelTemplate"),
expertMeetingVideo.test.js:1030: assert.ok(extractFunction("mountExpertManualProfile").includes("mountExpertMeetingVideos"),
```

Read `expertMeetingVideo.test.js:1018-1035` and `expertManualProfile.test.js:948-955`: inherited source pins concern existing app detail hosts/video guarded callsites. The approved new profile container belongs in the MailboxChat management overlay, not a new whole-contact page; no additional app host literal or video callsite is required. Preserve existing default-admin shell behavior; no extra fixture amendment is established here.

#### R6 — other existing host contracts to preserve

Exact grep-tool pattern:
`contact-details\.js|initContactDetailsLayout|expertManualProfileShellTemplate|mcHostOpenExpertDetail|mcHostOpenMaterials|mcHostOpenMeetingSchedule|isMailReadOnlyRequestAllowed`

Path: `src/test/js`

Relevant matches/read boundaries:
```text
contactDetailsCache.test.js:2: const source=fs.readFileSync(...'contact-details.js'...);
expertMaterialsShared.test.js:1827: for (const name of ['mountExpertMaterialsInline', 'mcHostOpenMaterials']) {
mailboxCalendarIntegration.test.js:99: ["mcHostGetMeetingSummaries", "mcHostOpenMeetingSchedule", "mcHostMeetingScheduleChanged"]
mailboxChatBehavior.test.js:3479: describe("mcHostOpenMaterials app 适配器（drawer/store 契约）", () => {
```

Read `app.js:26954-27022,27258-27344`: existing create-dialog unconditionally calls loadMeetingCalendarContacts and reads form contactId; delegated invocation must bind the existing form to its current expert and skip the global contact list. Existing default-admin path remains intact. Source is already authorized.

Exact material lifecycle pattern:
`function (mount|unmount|unmountHostsIn|destroy|release)|stores\.|storeByContact|\.delete\(|global.ExpertMaterials|Object.freeze|dispose|abort|cache|upload`

Path: `src/main/resources/static/expert-materials.js`

Relevant output:
```text
expert-materials.js:207: stores.delete(store.contactId);
expert-materials.js:221: function disposeView(view) {
expert-materials.js:233: if (store.views.size === 0) {
expert-materials.js:234: dropStore(store);
expert-materials.js:1685: function unmountHostsIn(rootEl) {
expert-materials.js:1691: return view.host === rootEl || (...rootEl.contains(view.host));
```

Existing host-based teardown releases the material view/store; use it from the authorized app/chat lifecycle rather than introducing an unlisted material-component change. General source/test searches found existing material binary adapters and shared-store teardown behavior, which must remain stable.

### Task status / downstream handoff

| Requirement | Status | Evidence / next authorized implementation |
|---|---|---|
| I-2/I-4/I-8 exact frontend routes/current-target send | PENDING | Predecessor backend exact guards exist; no permission prefix/default shim authorized. Actual fresh route/resource runtime matrix still required. |
| I-3/S-1 READ tags | PENDING reconciliation | Existing reader text-only tag renderer identified; preserve and extend behavioral tests, not grant controls. |
| I-7/S-5 pending experts vs mail units | PENDING | Reader list/total currently unfiltered; implement pendingOnly server query and expert count, preserve manualReviewTotal wire. |
| I-10/S-4 delegated conversation | PENDING | Existing full MailboxChat bootstrap/selection/draft globals audited; require fixed contact/no list probe/global preload, target-scoped flush and epoch teardown. |
| I-11 review separation | PENDING | Existing READ review module/APPROVED lock identified; FULL new manual draft must remain separate. |
| S-4 CSS consumer retirement | CONFLICT pending new plan identity | Unlisted mailboxChatStyle fixture and controller decision above. |
| Actual backend/browser proof | NOT_RUN | Must run freshly after final product implementation, real login/API/JDBC and Chromium 1440/390. |

### Commands actually executed

All shell commands below used the exact target worktree as cwd.

1. `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master && git status --short` — exit 0; plan hash/root/branch/HEAD/git-dir and initial dirty paths recorded above.
2. `git show e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6:docs/plans/2026-10-29/expert-access-07-user-workspace.md | shasum -a 256 && git merge-base --is-ancestor b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc HEAD` — exit 0; output `a33017f940c5865cf34354b4f80e644562e51d8e84478591f43d11d992c37d72  -`; product base is reachable.
3. `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master` — exit 0; same plan hash/worktree identity/HEAD.

No builds/tests/linters/formatters/smoke runs executed; test counts/skips are NOT_RUN, not zero-skip PASS. No Docker/JDBC/ES/SMTP/browser service or throwaway scaffold created. No staging/commit/push/merge/rewrite/deploy.

### Freshness, limits and next action
- Plan identity rechecked: YES, unchanged at last identity command.
- Worktree identity rechecked: YES.
- Required final commands run: NO; source remains unimplemented for this identity.
- Historical predecessor evidence used only as baseline: YES; no inherited test/runtime count claimed as fresh.
- This report is not READY_FOR_VERIFICATION and not PASS.
- Remaining prerequisite: controller commits the bounded stylesheet-consumer amendment and supplies the new exact plan identity/brief. Resume only against those new bytes; do not silently transfer this epoch's approval or evidence.

## Epoch 2 — PLAN_CONFLICT / remaining inherited header implementation pin

### Identity and preserved partial state
- Executor: `ExpertAccess07ResumeE2`; exact brief: `children/07/brief.md`, epoch 2.
- Approved child: `docs/plans/2026-10-29/expert-access-07-user-workspace.md`.
- Approved plan commit / before HEAD / current after HEAD: `a2799c7211fcd95210bfa340aff9529221dd8b6d`.
- Child bytes: SHA-256 `73887e16768fe7cc289f6ba9f0c21694f87c607e5940ff4fbb43403c7667cd3c`, size `24610`.
- Product base: `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Identity helpers rechecked this same plan hash/size, root, branch and git directory after the failed gate; no plan-byte change was observed.
- No product commit or staging occurred. No push, merge, rewrite, deployment, other worktree or subagent was used.
- Preserve all partial product/test work in the exact eleven authorized paths: `src/main/resources/static/{mailbox-readonly.js,mailbox-chat.js,app.js,contact-details.js,mailbox-chat.css,index.html}` and `src/test/js/{mailboxReadOnly.test.js,mailboxChatBehavior.test.js,expertManualProfile.test.js,expertAccessWorkspace.test.js,mailboxChatStyle.test.js}`. `expertAccessWorkspace.test.js` is new/untracked, hence absent from `git diff --name-only`; it must not be omitted from a later product commit.
- Inherited controller changes to brief/ledger/execution were preserved. This append is execution evidence only; controller owns amendments to approval/brief/ledger.

### Partial implementation and binding interfaces
- Reader now uses explicit detail `accessLevel` to choose READ versus the fixed-contact delegated MailboxChat host; missing/READ/NONE authority cannot mount a writable editor. It retains the original READ text/tag and review-draft path.
- Delegated mode reuses the existing right-pane rich editor, current-user/contact server drafts, safety/send adapters and immutable review-history renderer without administrator list/bootstrap/filter/workbench/template/default-settings consumers. Header identity omits email/account lines and name does not fall back to email.
- Reader personal pending chips use the server expert `total` from an independent pending-only request; card pending counts remain mail counts; search/filter reset the page.
- `app.js` now gates explicit anchored method/path pairs for the already guarded scoped endpoints. Actual drafts use `/api/mail/mailbox/drafts`; obsolete `/reply-drafts` remains denied.
- Native profile fields/CAS are mounted through the existing host/shell wrappers with delegated context/current-owner guards; delegated mount and unmount skip whole-page video/navigation/layout/history/meeting-record consumers. Materials/calendar remain existing fixed-contact adapters.
- Inherited reader CSS bytes remain unchanged; only the two exact S-4 delegated rules were appended. Index asset versions changed together without resource-order changes.
- Existing default-admin editor/header behavior is retained in a separate explicit header template branch. This is not evidence of browser/JDBC/SMTP behavior; those checks have not run.
- Source audit of calendar submission and complete actual browser/lifecycle/runtime acceptance is still required on resume; no completeness claim is made for the partial implementation.

### Fresh commands actually exercised
1. Initial identity:
   `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master && git status --short`
   Exit `0`; same identity recorded above; initial controller-only documentation dirt was retained.
2. Initial focused behavioral gate:
   `node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js`
   Exit `1`: tests `232`, pass `229`, fail `3`, skipped `0`, cancelled `0`, todo `0`. Failures were the new integrated workspace fixture's missing `URL` global, not a production response or gate bypass. The new fixture now supplies `URL`; no production fallback was introduced.
3. Fresh sequential gate command after that fixture repair:
   `node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js && node --test src/test/js/mailboxChatStyle.test.js && node --check src/main/resources/static/mailbox-readonly.js && node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js && node --check src/main/resources/static/contact-details.js && node --test src/test/js/*.test.js`
   Overall exit `1`; output `artifact://2666`.
   - Focused behavioral gate: tests `232`, pass `232`, fail `0`, skipped `0`, cancelled `0`, todo `0`.
   - Stylesheet/direct-source gate: tests `47`, pass `46`, fail `1`, skipped `0`, cancelled `0`, todo `0`.
   - Failed assertion: `mailboxChatStyle.test.js:343` / assertion line `345`, `identity 层级：名称→邮箱账号→时间槽`, requiring an unconditional source-template `<h2>…</h2><p>…</p><span class="mailbox-reply-detail" data-role="last-reply-time">`.
   - Because `&&` stopped at that failure, none of the four syntax checks or the full Node suite in this command executed.
   - After the observed failure, the product header was expressed as explicit delegated/admin branches, retaining default-admin source markup and delegated omission. This later product edit was NOT retested: its literal-count incompatibility below was found statically, not claimed as another observed failure.
4. Boundary identity:
   `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master && git diff --name-only`
   Exit `0`; same identity; only the ten tracked authorized product/test paths plus preserved controller documents were reported.

### Complete bounded amendment batch — exact evidence
The current authorization permits `mailboxChatStyle.test.js` changes only for the inherited stylesheet EOF/suffix pin. Its separate header implementation pin is outside that authorization. Controller decision received: retire the incidental global source/header count pin, preserve actual default-admin DOM order and unique slot, and preserve delegated absence of identity email/account lines. No edit to that newly requested test section was made.

Exact search through product/header consumer paths:
`grep(pattern='data-role="last-reply-time"|identity 层级|建槽 1 处|S-2 详情', path='src/main/resources/static/mailbox-chat.js;src/test/js/mailboxChatStyle.test.js')`
Observed output:
```text
mailbox-chat.js:3688 const slot = body && body.querySelector ? body.querySelector('[data-role="last-reply-time"]') : null;
mailbox-chat.js:3704 ? `<h2>${escapeText(summary.name || "未填写姓名")}</h2><span class="mailbox-reply-detail" data-role="last-reply-time">…</span><span class="calendar-summary" data-role="meeting-summary"></span>`
mailbox-chat.js:3705 : `<h2>${escapeText(summary.name || summary.email || "-")}</h2><p>${escapeText(conversationSummaryInfo() || "-")}</p><span class="mailbox-reply-detail" data-role="last-reply-time">…</span><span class="calendar-summary" data-role="meeting-summary"></span>`;
mailboxChatStyle.test.js:343 it("S-2 详情：identity 顺序 h2→p→时间槽→排期；只在 renderHeader 建槽", …
mailboxChatStyle.test.js:347 /<h2>[^<]*<\/h2><p>[^<]*<\/p><span class="mailbox-reply-detail" data-role="last-reply-time">/,
mailboxChatStyle.test.js:348 "identity 层级：名称→邮箱账号→时间槽"
mailboxChatStyle.test.js:350 assert.ok(hdr.indexOf('data-role="last-reply-time"') < hdr.indexOf('data-role="meeting-summary"'), "时间槽在排期摘要之前");
mailboxChatStyle.test.js:351 assert.strictEqual((chatSource.match(/data-role="last-reply-time"/g) || []).length, 2, "建槽 1 处 + 局部刷新选择器 1 处");
```
The displayed ellipses abbreviate the template body only; the exact literal occurrences are at the cited lines. Search tool output contains their full text. Current source has separate mutually exclusive delegated/admin builders and the shared refresh selector. Therefore the source-literal count assertion is incompatible with this valid layout [INFERENCE]; no failing-gate rerun was used to reconfirm it.

Completed remaining stylesheet audit: read all `mailboxChatStyle.test.js` sections through line `585`. Existing rule-byte/scoping checks, class whitelist, no-inline-style, reader resource/body persistence restrictions, script registration, resource relative order, original reader selector block/count, mobile/default-admin skeleton and scoped styles remain valid requirements, not requests to weaken them. The already approved EOF change is the only other stylesheet amendment. No additional same-cause change was identified in this inspected file beyond the bounded header block `343–353`.

Direct consumer search:
`grep(pattern='last-reply-time|mc-identity|renderHeader\(|renderHeader\b|mc-delegated-conversation|mailbox-chat\.css|mailboxReadOnly|isMailReadOnlyRequestAllowed|delegated-conversation', path='src/test/js', skip=0)` and the same search with `skip=20` to exhaust the paginated output.
Observed behavioral safeguards already present:
```text
mailboxChatBehavior.test.js:5923–5926 default-admin .mc-identity children must be ["H2","P","SPAN","SPAN"], third child is time slot, fourth is meeting-summary.
mailboxChatBehavior.test.js:6173 exactly one actual [data-role="last-reply-time"] after header/list rebuild.
mailboxChatBehavior.test.js:6215 / 6257 no stale/unmatched detail time slot.
mailboxChatBehavior.test.js:6901–6902 delegated .mc-identity text excludes a@example.edu and acc1.
expertAccessWorkspace.test.js:85 integrated reader/delegated identity excludes a@example.edu.
```
These are production-module DOM/behavior tests, not source-occurrence replacements. Fresh focused run above exercised the behavioral files before the final explicit-header branch edit; resume must run them fresh again. Add an explicit delegated absence-of-`P`/one-actual-time-slot assertion in the already authorized behavioral file rather than replacing source count `2` with a new incidental count.

The same search identified `authFlow.test.js:988–1056` as the exact API-guard consumer; it was read through its logout boundary at `1078`. It tests precise review-draft routes, global sender/settings denial, malformed IDs and obsolete `/reply-drafts/1` denial. Those pins remain valid; no amendment to that file is requested. Wrapper consumer lookup/read identified `contactDetailsCache.test.js` as the unchanged `cachedRead` extractor; the inspected `contactHeadLayout.test.js` and `contactMeetingRecords.test.js` assertions concern unchanged admin/caching/video behavior. A first narrower guessed wrapper test path did not exist; the subsequent filename glob resolved the actual consumers rather than treating that empty result as evidence.

### Limits and next action
- Exact bounded request: authorize retirement of `mailboxChatStyle.test.js:343–353`'s inherited global source-header/count pin while retaining default-admin actual DOM order/slot uniqueness and delegated no-email/account-line behavior. No new authorized file is requested; no broad stylesheet weakening or new source/order/count pin is requested.
- All eleven partial files remain in place; no product commit. This report is `PLAN_CONFLICT`, not `READY_FOR_VERIFICATION` and not `PASS`.
- Full Node suite, syntax checks, backend/auth/SQL/MySQL/Flyway matrices, actual authenticated JDBC/API runtime, safe SMTP, 1440/390 browser acceptance, A5/A6/A7/A12/A14, lifecycle/race proof and owned-service cleanup have NOT executed in this epoch. Do not inherit previous-child counts or claim this partial run as acceptance.
- No services/scaffolds were started. There is nothing owned to stop at this boundary.
- Controller must commit the bounded approval/brief amendment and supply a new identity before mutation of the newly requested fixture area. Resume against the preserved partial state, finish reachable implementation/static audits, run every final required gate fresh with explicit opt-in flags and zero target skips, produce the product-only commit, then append final evidence.

## Epoch 3 — PLAN_CONFLICT / complete remaining direct-consumer fixture batch

### Identity and preserved implementation boundary
- Executor: `ExpertAccess07ResumeE3`; approval basis: current invocation, epoch3 brief and standing human authorization. NEW immutable identity epoch; prior results used only as baseline.
- Canonical child: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md`; SHA256 `dbe8f22f2c2d48f0e8dfbcc68217f0025b08bdce92ef4249ba88d316b7155a10`,25247bytes. EXECUTION_ID = canonical child path@that SHA.
- Approved child/master identity commit / before HEAD / after HEAD: `967c6b0a47d963fd2d968a94ccf635caa5842e51`. Pre-execution product SHA remains `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`; post-execution code SHA N/A (no product commit), Evidence HEAD unchanged.
- Target worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. WORKTREE_ID=root@branch@git-dir.
- Read execute-p, entire current child/master, epoch3 brief, predecessor06/07 evidence and mail design. Preserved/reconciled the eleven inherited authorized partial paths; `expertAccessWorkspace.test.js` remains new/untracked and must be included in a later product-only commit. Controller-owned brief/ledger/evidence dirt was preserved.
- Initial `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-07-user-workspace.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master` exited0 (`artifact://2670`), exact identity above. `git status --short && git diff --stat` exited0 (`artifact://2672`); only authorized partial product/test files and preserved controller documents.
- No LSP mounted. Current exact delegated/calendar/type/direct-method searches and inherited complete consumer receipts were used; current partial adapter diff read from `artifact://2673`.

### Completed authorized work before hold
- Retired the now-authorized `mailboxChatStyle.test.js` global single-header/source-occurrence pin, without replacing it with a new count or branch pin; retained Beijing-tail presence, all CSS/resource/security checks and existing real default-admin DOM order/slot tests.
- Added delegated actual-DOM checks in `mailboxChatBehavior.test.js`: no identity `P` and exactly one actual last-reply-time node. This complements existing no-email/no-account and default-admin H2/P/time/calendar behavior.
- Completed the explicitly outstanding fixed-contact calendar/form audit. The existing form was already bound before opening; delegated create skips global contact loading and `meetingCalendarDialogReadPayload()` ignores stale global contact-select values, using the captured fixed contact.
- Closed lifecycle gaps within authorized `app.js`: delegated dialog captures current-owner predicate and a distinct dialog object; late save/cancel callbacks cannot close or refresh a replacement context, and cancellation's version-fetch continuation cannot dispatch its subsequent write after revoke/unmount. Admin default path retains its existing form/adapters and behavior. No new classes, interfaces, retries, stores or security defaults.
- Added three permanent behavioral regressions in authorized `expertAccessWorkspace.test.js`: actual extracted form submission uses contact1 despite hidden stale select999 and zero global contacts load; late save leaves replacement contact2 open with no stale refresh; revoke during cancel-version fetch produces zero cancellation writes. The form/picker IDs are additionally asserted present in real index source.
- All eleven partial product/test paths remain preserved; no staging/commit/push/merge/rewrite/deploy, no other worktree/subagents, and no runtime services/scaffolds started.

### Fresh sequential commands and observed results
Exact command, target cwd above:
```sh
node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js && node --test src/test/js/mailboxChatStyle.test.js && node --check src/main/resources/static/mailbox-readonly.js && node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js && node --check src/main/resources/static/contact-details.js && node --test src/test/js/*.test.js
```
- Overall exit1, `artifact://2674`. Focused235tests/235pass/0fail/0skip/0cancel/0todo; style47tests/47pass/0fail/0skip/0cancel/0todo. All four syntax checks completed successfully (the `&&` sequence reached the full suite).
- Full suite1976tests/1972pass/4fail/0skip/0cancel/0todo. Exact count receipt: grep `ℹ (tests|pass|fail|skipped|cancelled|todo)|not ok|✖ failing` over `artifact://2674` returned focused lines308–314,style383–389,full3082–3088. No re-run to reconfirm known failures.
- Failures are existing unlisted direct-consumer source pins, below. Backend/auth/SQL/MySQL/Flyway, actual cookie/JDBC/API runtime, real SMTP,1440/390 browser acceptance A5/A6/A7/A12/A14 and runtime lifecycle races remain NOT_RUN this epoch. No predecessor results recounted as fresh acceptance; no READY/PASS claim.
- Boundary command: same plan helper, worktree helper with `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`, then `git diff --check` (`artifact://2675`). Both helpers matched exact unchanged identity and HEAD; overall exit2 solely for existing execution.md:311 blank line at EOF, now consumed as the separator by this append. No product whitespace error reported. No gate rerun solely for that evidence whitespace.

### Proven prerequisite — three unlisted existing consumer fixtures
No mutation was made to any requested file. Complete known batch from the fresh full suite:

1. `src/test/js/mailReviewReader.test.js:66` in test at56:
```js
assert.ok(!/MailboxChat|sender-accounts|\/translate|\/approve|\/send|attachment/i.test(moduleSource.replace(/\/\*[\s\S]*?\*\//g, "")));
```
Observed failure: falsy assertion. Current approved reader must reference `global.MailboxChat` at848/852 to reuse FULL right-pane editor; global source prohibition is obsolete. Preserve READ actual no-FULL-mount/no-send/no-attachment behavior, storage prohibition, exact review DOM/classes, sanitizer, reviewer states/CAS/late-response/logout tests. Retire only the global MailboxChat implementation ban in favor of actual READ capability assertions; do not relax denied API/storage/approval behavior.

2. `src/test/js/mailboxCalendarIntegration.test.js:82,106` in tests at79/105:
```js
assert.strictEqual(countOccurrences(chatSource, skeleton), 2, "列表卡片与头部各一处骨架");
assert.strictEqual(countOccurrences(chatSource, 'addEventListener("meeting-calendar-changed"'), 1);
assert.strictEqual(countOccurrences(chatSource, 'removeEventListener("meeting-calendar-changed"'), 1);
```
Observed first two failures: actual3 versus expected2 skeleton literals; actual2 versus expected1 listener literals. The remove-listener assertion and subsequent notify assertion were not reached in the failing test, not separately claimed failures. Product source contains mutually exclusive delegated/admin header branches and delegated/admin attach paths, each with a single actual header/subscription, sharing existing unmount removal. Retire incidental global source counts only, do not repin3/2 or alter product to satisfy count defaults. Preserve literal skeleton/CSS/three header-action contracts, real admin/delegated one-active-instance behavior, current-owner broadcasts and unmount symmetry.

3. `src/test/js/mailboxReplyIdentity.test.js:505` in test at504:
```js
assert.ok(chat.includes('${followUpButton}\n                        <button class="button" type="button" data-action="mc-complete-reply-identity">补全称呼与签名</button>'));
```
Observed failure: falsy source-adjacency assertion. Approved delegated mode omits global identity completion/default-setting controls atmailbox-chat.js6260–6262; default-admin actual toolbar retains button placement. Retire this incidental unconditional source adjacency, not actual admin toolbar order/identity behavior/shared dialog/CSS/cache contracts. No new source adjacency/count pin.

### Exact complete consumer receipt and preserved safeguards
Search performed:
```text
grep pattern='MailboxChat|sender-accounts|/translate|/approve|/send|attachment|moduleSource|calendar-summary|meeting-calendar-changed|mc-complete-reply-identity|reply-identity-default-summary|mc-reply-identity-settings|followUpButton'
paths=src/test/js/{mailReviewReader.test.js,mailboxCalendarIntegration.test.js,mailboxReplyIdentity.test.js}
```
Observed relevant output:
```text
mailReviewReader:65 storage prohibition;66 global capability regex.
mailboxCalendarIntegration:79–85 skeleton/CSS;106–108 global source counts;
820 actual mount;848 calendar event dispatch;889 remount;957 unmount;961 no post-unmount reads.
mailboxReplyIdentity:98–102 actual toolbar children/order;85/151 actual completion action;
120/140 actual draft flush;155–169 edit/contact/unmount/logout stale-inspection matrix;
504–512 frozen source/DOM/CSS/cache test, only505 conflicts.
```
Read full reader remainder120–299: all four review states, immutableAPPROVED, history/comments, CAS/input preservation,401/403 clearing, late context callbacks, sanitizer and registration checks remain valid. Read calendar actual behavioral853–964: summaries batch/current epoch, failed lookup≠zero, current expert actions via adapter, broadcasts preserve timeline/draft node, exactly one extra read for active owner, and no post-unmount read. Read identity actual behavioral94–115: completion is last actual tool and directly after actual follow-up when present; real button preserves rich editor nodes. Those assertions passed in the fresh full run and must remain.

Production exact search:
```text
grep pattern='identity.innerHTML = delegated|mc-complete-reply-identity|addEventListener\("meeting-calendar-changed"|removeEventListener\("meeting-calendar-changed"|calendar-summary" data-role="meeting-summary"|global.MailboxChat'
paths=src/main/resources/static/{mailbox-chat.js,mailbox-readonly.js}
```
Observed:
```text
mailbox-chat2188 list calendar skeleton;
3703–3705 mutually exclusive delegated/admin identity templates, each one calendar slot;
6260 delegated omission of completion button;6262 omission of global defaults;
8137/9911 unchanged completion DOM/action consumers;
10422 common listener removal;10463 delegated attach;10485 default-admin attach.
mailbox-readonly848 dependency check;852 FULL mount via delegated-conversation.
```
No newly requested source change beyond this complete three-fixture batch is evidenced. The full suite's other1972 passing tests do not establish unexercised runtime acceptance.

### Controller decision, status and next action
- Controller adjudicated all three existing consumer additions appropriate under standing authority; retire incidental assertions only, retain READ never-mount-FULL behavioral guard, admin actual toolbar/calendar subscription/lifecycle/one-active-instance behavior, no replacement count/adjacency pins. Controller will commit14-file master/child authorization and dispatch a new immutable identity.
- Current task states: eleven-file shared-editor/scoped-host/calendar/profile/pending/lifecycle implementation remains IMPLEMENTED/uncommitted partial; focused/style/syntax gates fresh succeeded; full regression gate BLOCKED by unlisted fixtures; backend/runtime/browser/SMTP acceptance PENDING/NOT_RUN; product commit BLOCKED.
- Terminal outcome **PLAN_CONFLICT**, not READY_FOR_VERIFICATION/PASS. Exact missing prerequisite: committed bounded authorization of the three paths above plus new brief/identity before further product/test mutation. No unavailable-information claim; file contents and exact failures were exhausted through tools.
- Preserve eleven partial paths (including new untracked workspace test), all historical failure receipts and this append. After new identity: modify only authorized obsolete fixture pins with behavioral safeguards, finish all required fresh commands/backend+browser+SMTP acceptance, commit product/tests only as `feat(fast-p): implement 07`, append exact evidence. No service cleanup needed at this hold because none were started.

## Epoch 4 — PLAN_CONFLICT / bounded projection fixture prerequisite

### Identity and preserved implementation
- Executor: `ExpertAccess07ResumeE4`; NEW execution against canonical plan `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md`, SHA-256 `b6c3ef0c4c847c4ce1301ccb5c98856c764bba18fbc66d152d705eaaf83b4d87`, 26618 bytes. Approval/brief commit `5e62c14b617d45b65847068b657ae27e5250e133`; product base `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`.
- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Before/after HEAD both `5e62c14b617d45b65847068b657ae27e5250e133`; no staging/product commit/evidence commit. Parent explicitly requires the projection file remain read-only until a committed new identity. All fourteen authorized dirty product/test paths, including untracked `expertAccessWorkspace.test.js`, remain preserved.
- Initial identity receipt artifact2678; final helpers freshly rerun with expected root/branch/Git-dir, artifact2793, identical identity/HEAD. The first final helper attempt incorrectly passed `skill://` directly to Python (exit2); resolved skill file path `/Users/lukai/.agents/skills/execute-p/scripts/` and both canonical helper commands exited0. No identity change.
- Retired only obsolete global MailboxChat denial, global calendar skeleton/subscription count pins, and unconditional toolbar adjacency. Existing READ behavior, actual admin toolbar, exact CSS, one-active calendar listener and unmount assertions remain.
- Actual browser exposed and this epoch fixed two authorized host defects: successful expert-tag mutation then incorrectly attempted admin global reloads; hidden required empty calendar `contactId` select blocked native submit. The shared tag helper now skips only explicit delegated global reloads, preserving original DOM-derived admin filter arguments; delegated tag callbacks reject unmounted contexts and release loading. Delegated calendar disables the hidden select and restores it for default admin. Added real-helper/delegated-consumer/admin-refresh/late-dialog/native-select regressions in the authorized workspace fixture.
- Exact tag consumers audited using grep `mutateExpertTag|loadContacts\(|loadExpertTagOptions\(` over app/chat/tests: app definition8640, direct admin add/remove calls16730/16749 (pre-edit numbering), chat host calls6809/6837, direct extracted helper `mailboxInboundTags.test.js:89/91`, and stubs in mailboxChatBehavior/mailboxOutboundAttachments/mailboxSuspension/meetingConfirmationIntegration. No unlisted mutation, nullable security shim, prefix permission allowance, new production abstraction or backend change.

### Fresh commands and failures
All commands ran in the exact worktree. JDK was `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home`; Docker host `unix:///Users/lukai/.orbstack/run/docker.sock`.

Final frontend command, exit0, artifact2775:
```bash
node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js &&
node --test src/test/js/mailboxChatStyle.test.js &&
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/mailboxReplyIdentity.test.js &&
node --check src/main/resources/static/mailbox-readonly.js &&
node --check src/main/resources/static/mailbox-chat.js &&
node --check src/main/resources/static/app.js &&
node --check src/main/resources/static/contact-details.js &&
node --test src/test/js/*.test.js
```
Fresh focused239/239, style47/47, added-fixture97/97, full1980/1980; every group zero failures/cancellations/skips/todos. Four syntax checks exit0 through the successful chain. Exact grep receipt against artifact2775 `ℹ (tests|pass|fail|skipped|cancelled)` gives lines312–318,387–393,518–524,3221–3227.
- Earlier baseline frontend chain artifact2681 passed focused235/style47/fixture97/full1976. New-regression first run failed focused238/239 due only cross-realm empty-array assertion; fixed to zero length. Next artifact2772 passed focused239/style47/fixture97 but full1979/1980 exposed my accidental alteration of original tag helper DOM filter dependency to `state`; restored original arguments, not an unlisted fixture change. Final successful chain above ran after all source/test edits.

Accidentally broader aggregate command (master final scope, not child07 gate155), exit1, artifact2683/2687:
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn test -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```
Observed5963 tests,3 failures,24 errors,11 skips. Not PASS; not rerun wholesale.
- `MailboxReadAccessProjectionTest` eight tests/one error: real admin list reaches `MailboxConversationService:140–142 repository.filterCounts(activeCodes)`, unstubbed nonnull Pair is null, NPE at fixture291. Detailed prerequisite below.
- `DiscoveryPersonOutreachGuardServiceTest`: assertions986 (`admissionReads >=4`) and1011 (sent expected1, actual0) failed,39 tests/two failures/four skips. Exact fixture1020–1157 stubs `countExperts`, `searchExpertsFiltered`, `scrollExpertsFiltered`, `findByDocumentIdsStrict`, but actual demand reader1918–1924 calls unstubbed `openCandidateCursor(...).nextPage(size)`. [INFERENCE] null cursor is wrapped by readCandidates1879–1886 into source failure before live admission/SMTP; observed system-out only logs starts702/701 at XML80/91. No claim that the guarded live-send behavior passed, and no unrelated repair.
- `ExpertInboundNotificationServiceTest:171` failed global assertion that no alive thread anywhere has name `expert-inbound-notification`; its own stop/release assertion170 passed. Source94–115 starts that fixed name for every service instance. [INFERENCE] cached Spring application worker instances elsewhere in the aggregate can satisfy this global predicate despite the tested instance stopping. Observed same-name global assertion failure is retained; not claimed proven harmless or repaired.
- `OperatorActionLogRepositoryTest` three context errors: Flyway V2 unresolved `${senderEmail}` in fixture configuration.
- RAG fact-admin ten and knowledge-base ten context errors: `BeanDefinitionOverrideException` for `attachmentTransferWorker` from `WorkerLifecycleTestConfig`. These plus projection account for24 errors. No production test-suppression/config changes.

Fresh targeted backend command, exit1,263 tests/zero failures/three errors/zero skips, artifact2710 and snapshot2770:
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarControllerMysqlTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```
Only errors: calendar MySQL fixture setup cleanup552 `DELETE FROM expert_contact` conflicts with residual `inbound_mail_processing` FK in shared external schema; XML error73/842/851, constraint `fk_inbound_mail_processing_contact`. Other targeted tests260 passed with zero skips. Not a clean aggregate pass. Remedied environment, not source: created fresh disposable schema `expert_access_07_calendar` and ran calendar alone:
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/expert_access_07_calendar?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn resources:resources surefire:test -Dtest=MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```
Exit0, three tests/zero failures/errors/skips,170 migrations toV171, artifact2781. Existing compiled Kotlin fixtures unchanged; resource phase copied final frontend files for actual browser replay. This isolated correction does not erase original command failure.
- Runtime classpath command `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-07-epoch4-runtime/classpath.txt` exit0, artifact2688. Production Java launched with `target/classes` plus126 dependency entries only, no testclasses/expanded WAR.

### Actual production runtime observed, not test substitution
- Owned disposable MySQL8 container `expert-access-07-e4-mysql` port33417, separate runtime DB `expert_access_07_runtime`, timezone Asia/Shanghai; loopback ES33918, safe SMTP33919, production app33817 Java11.0.33 `AuthWebConfig`/actual API/JDBC/Flyway170 migrations throughV171. Production app service `expert-access-07-e4-app`, PID59708 startup artifact2690.
- Actual admin login/password change and user1 login; actual admin grants contact9701FULL,9702READ,9703NONE; real HTTP cookie clients plus independently logged-in production browser user1. Fixtures use disabled logical accountfixture07 (per existing manual-send scope), contacts/inbound/mail_record/manual uploads/document rows/three ownership-source attachments and a foreign attachment. No production service contacted.
- Real browser Chromium150 (not a fabricated1440/390 screenshot),1440×1000 and390×844. user1 login shows only mailbox/logout and no global admin bootstrap. Actual FULL existing shared rich editor sends via `/api/mail/unmatched-inbound/9712/manual-rich-reply`: initial422 safety requires actual confirmation, then200, one SMTP DATA816bytes to `expert1@example.test`; JDBC mail_record SENT and `SEND_MANUAL_RICH_REPLY` actoruser1. No review submission required for new manual reply.
- Existing materials drawer downloaded exact original26-byte payloads for MAIL_RECORD9771, INBOUND_PROCESSING9772, MANUAL_UPLOAD9773; browser download bytes compared directly. Existing manual upload form201 added a document; no SMTP side effect.
- Existing delegated profile form displayed four groups/22fields without video/global history bootstrap; actual identity edit PATCH200 saved talentCodeWORKSPACE07/chineseName, version1, updatedByuser1. Valid full-group stale expectedVersion0 API returned409 PROFILE_CONFLICT. Initial incomplete-group attempt400 was validation, not a CAS proof.
- Repaired expert tag UI actual add200; loading false, final tag visible, no global contact/tag-options queries. Removal was clicked, but its response/final tag state was not separately captured and is not claimed runtime proof. Initial pre-fix add200 followed by frontend-rejected global refresh/stuck loading is the actual defect this epoch repaired, not hidden.
- Repaired calendar original mobile form fixedcontact9701: hidden select disabled, no global chooser; actual create201, edit200, cancel200. JDBC final event1/contact9701 CANCELLED, updated note and actual cancel reason. No schedule notification send: SMTP remained one. Initial native-select blockage produced no POST and actual invalid-element receipt showed required empty enabled SELECT; fixed rather than bypassed via direct API.
- READ Beta real UI has no live manual editor; review DRAFT/SUBMITTED/RETURNED/APPROVED fixtures created/submitted/returned/approved through actual authenticated API with observed context token. Actual READ UI opens all four: DRAFT/RETURNED editable, SUBMITTED/APPROVED not; no user approve/send controls. Upgrade BetaFULL mounts separate manual editor; JDBC review rows unchanged, SMTP remains one; downgrade READ plus actual focus listener removes writable DOM synchronously before requests and does not restore it. Logout removed all visible/delegated editors, open dialogs and management overlay.
- Personal-pending runtime changed two mails of the same FULL expert to user1; server list returned pendingCount2 with total one expert, actual UI chip `待处理1` and row `2条待处理`; other admin-owned/NONE experts excluded.
- Real HTTP denial matrix18×403: READ/NONE conversation and inbound rich sends, cross-contact attachment/source mismatches, READ/NONE expert tags, foreign mail tags, NONE profile, NONE/mixed calendar queries, mapped global sender-account/task/unmatched APIs and user grant mutation. Snapshot counts mail_record/send_attempt/attachment/profile/calendar/tag unchanged, zero incremental ES requests and SMTP DATA. Foreign-anchor inherited500 and unsupported-verb/no-handler500 were not reused as default-deny proofs.
- HTTP observer final43 calls:21×200,1×204,18×403,1×404 (initial wrong guessed grant URL),1×400 (incomplete CAS body),1×409. Browser requests are separate. ES observer48 requests; SMTP DATA exactly one.
- Actual images: desktop profile `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15bab4884dacf7f6.webp`; mobile workspace `.../omp-sshots-15bab65c1eacf7f7.webp`; mobile profile `.../omp-sshots-15bab66185ecf7f8.webp`; repaired mobile schedule `.../omp-sshots-15bab89ae32cf7f9.webp`. Actual document scrollWidth equals1440/390 respectively; mobile profile width356 within390. Browser errors only blocked external Google-font requests from explicit loopback allowlist; no observed JS page errors.
- Limits: full actual acceptance is **not claimed complete**. Remaining final-identity browser race/delayed-response/visibility/current-owner/calendar CAS/mixed-resource/admin-baseline matrices and all acceptance combinations require fresh completion after the authorized projection prerequisite. Permanent tests are not labeled runtime proof. User's final priority was to finish current step, clean up and return.
- Cleanup completed: real logout, managed browser released; app stopped through proc service kill; ES/SMTP servers shutdown/closed; Docker owned container removed exit0; runtime directory and owned download directory removed. No owned runtime jobs/services/scaffolds remain. Generated screenshot evidence above retained.

### Complete bounded prerequisite receipt / controller disposition
- Unlisted exact file `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt`, SHA-256 `47e7fc609ef75c8303b9ed943a622bfc702aed5393b41b4a2f81fba6cf548907`; last introducing commit `6c0981eb4e148e9789c3af49e9d2dca9739c2508` (child02). `git diff b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc --` this file, MailboxConversationService, outreach test, notification test was empty; failure is not from a current07 edit.
- Fixture274–300 constructs real service with mock repository, stubs count/page, calls admin list291 and asserts FULL/READ/NONE permissions/projection. Missing prerequisite is **only** required nonnull `repository.filterCounts(activeCodes)` Pair stub so existing meaningful assertions execute. No nullable/default production workaround and no forwarding/mock-echo count assertion.
- Exact comprehensive test receipt: grep `listConversations\(|filterCounts\(` over `src/test/kotlin` returned AuthInterceptorTest373 (mock service), MailReviewQueryIT126 (real service/repository), MailboxConversationRepositoryIT158 (`assertEquals(2L to 2L, repository.filterCounts(allAccounts))`, real SQL), MailboxReadAccessProjectionTest291 (real service/mock repository). No other matches. Thus real count behavior already has independent JDBC coverage; do not add a stub-value assertion as a substitute.
- Controller accepted bounded fixture-only authorization, requires committed new plan/brief identity before mutation, no completed-child replay. Projection file remains untouched. Other broad aggregate failures above are observations, not unrelated child07 repair tasks.
- Status **PLAN_CONFLICT**, not READY_FOR_VERIFICATION or PASS; no product commit because current authorization excludes the required existing direct-consumer fixture. Source/tests within fourteen authorized paths implemented and freshly frontend-gated; backend/runtime evidence and known limits retained. Next authorized epoch: preserve partial paths, add only controller-approved Pair stub, finish fresh final-identity commands and remaining actual acceptance, product-only commit `feat(fast-p): implement 07`, then independent light verification/review.

## Epoch 5 — READY_FOR_VERIFICATION (execution evidence, not PASS)

### Identity and product boundary
- Executor: ExpertAccess07ResumeE5; approval: current standing all-child authorization and epoch5 brief. NEW epoch for the newly approved fifteen-file identity; prior partial changes preserved, not replayed as completed-child work.
- Plan canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-07-user-workspace.md`; SHA-256 `a96241f8124c20ee5e9fbcfa6df7c64bb72f04bb3b37f292346eed66d1c98303` (27853 bytes). Execution ID is that canonical path followed by `@` and that SHA.
- Target root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. WORKTREE_ID is root@branch@git-dir.
- Product base `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`; pre-execution HEAD `b65a7436b49995b882ff71e08f7b271c1ecf00e8`; post-execution code SHA / evidence HEAD `f9db55399b0f77c378bbcfd8f69a714ade4c4b9f`, subject `feat(fast-p): implement 07`. No separate evidence commit. Product diff is base..post-code SHA; commit itself contains only the exact fifteen authorized product/test files, 910 additions/118 deletions. Controller-owned brief/ledger and this execution report remain unstaged.
- Exact skill identity helpers ran at entry, before staging, before commit and final handoff; final SHA unchanged. `git merge-base --is-ancestor HEAD fast/expert-access-master` exit0; commit is target-worktree HEAD. Final `git diff --check` exit0. No push, merge, deployment, other worktree or subagent.

### Implementation and permanent consumer regressions
| Requirement | Implementation / permanent evidence |
|---|---|
| I-2/I-10/S-4 | Existing independent MailboxReadOnly list retains READ rendering; fresh FULL response mounts only the existing MailboxChat conversation. Fixed contact and authenticated user; exact verb/path frontend capability table, no prefix allowance or nullable security shim. Shared source and lifecycle behavior exercised by mailboxReadOnly, mailboxChatBehavior and new expertAccessWorkspace fixtures. |
| I-3/S-1 | ES and QA/CUSTOM labels escaped; READ has no tag mutation controls. FULL current-expert management reuses existing tag editor; delegated add/remove does not run admin contact/global tag refresh. Permanent actual-host behavior protects both delegated and admin paths. |
| I-4/I-8/I-13 | Original rich composer, safety confirmation, manual-draft transport, materials drawer and source-owner download paths retained; manual uploads, direct send and profile editing remain contact-scoped. Existing shared editor, calendar and reply identity regressions retained. |
| I-7/S-5 | Personal pending chip uses server expert total; row pendingCount remains mail units, independently of review-draft counts. Owner/suspension query behavior uses predecessor06 interfaces unchanged. |
| I-11 | READ four-state review editor retained; FULL review history is immutable and separate from its new manual editor. APPROVED cannot be edited/deleted; no automatic send on approval or upgrade. |
| S-4 profile/calendar | Reused four-group/22-field profile templates and existing version save; no video/history/reorganizer in delegated context. Original calendar form fixed to current contact, its hidden required chooser disabled only while delegated and restored for admin. Permanent DOM/form behavior covers this native-submit defect. |
| Exact fixture amendment | MailboxReadAccessProjectionTest adds only `filterCounts(listOf(ACCOUNT_CODE, OTHER_ACCOUNT_CODE)).thenReturn(0L to 0L)` after existing count stub. Original grade/order/tag/pagination assertions remain unchanged; no stub-value echo assertion. Exact test-tree consumer search and real JDBC count evidence are retained in epoch4 §prerequisite; fresh fixture now executes successfully. |

Changed paths, all authorized: `src/main/resources/static/{mailbox-readonly.js,mailbox-chat.js,app.js,contact-details.js,mailbox-chat.css,index.html}`; `src/test/js/{mailboxReadOnly.test.js,mailboxChatBehavior.test.js,expertManualProfile.test.js,expertAccessWorkspace.test.js,mailboxChatStyle.test.js,mailReviewReader.test.js,mailboxCalendarIntegration.test.js,mailboxReplyIdentity.test.js}`; `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt`. New workspace fixture is committed. CSS adds only the two approved S-4 rules; index cache triad remains unified. Only obsolete interim global-denial/source-layout pins retired; stable READ/admin/grade/security assertions retained. This canonical execution section records the permanent change; no unlisted docs/changelog file created.

### Fresh sequential required command evidence
All commands ran in the exact target worktree after the final source/fixture state; no parallel build/test storm. Java home below is existing11.0.33, never installed11.0.15.
```bash
node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js &&
node --test src/test/js/mailboxChatStyle.test.js &&
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/mailboxReplyIdentity.test.js &&
node --check src/main/resources/static/mailbox-readonly.js &&
node --check src/main/resources/static/mailbox-chat.js &&
node --check src/main/resources/static/app.js &&
node --check src/main/resources/static/contact-details.js &&
node --test src/test/js/*.test.js
```
Exit0, artifact2799: focused239, style47, shared fixture97, full1980; all zero failures/cancellations/skips/todos. Four syntax checks exit0.
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn test -Dtest=MailboxReadAccessProjectionTest
```
Exit0, artifact2808:8 tests,0 failures/errors/skips; compiles final production/test sources.
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```
Retained failure: killed at900s deadline, artifact2809/wait2811, not PASS. AuthInterceptor19/AuthFlow10 passed, but cached Spring attachment-worker contexts kept polling an already stopped per-class container and emitted repeated connection-refused logs; later schema migration still active at deadline. No production workaround/test weakening. Corrected execution isolation, not source:
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn surefire:test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```
Exit0, artifact2814:260 tests,0 failures/errors/skips;13:02. Fresh final compiled classes, one fork at a time with context lifetime bounded to its class. Both opt-in flags explicit; real AuthFlow migration gate enabled.
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/expert_access_07_calendar?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn resources:resources surefire:test -Dtest=MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 &&
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-07-e5-classpath.txt
```
Exit0, artifact2816: independent pristine calendar schema,170 migrations throughV171,3 tests/zero failures/errors/skips. Runtime classpath126 dependencies plus target/classes only; no expanded WAR/testclasses.
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn surefire:test -Dtest=AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,PendingMailOperationServiceTest,UnmatchedInboundMailServiceTest,OutboundAttachmentFlowTest,CalendarAttachmentIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```
Retained invocation error exit1, artifact2865:204 tests,0 failures,6 CalendarAttachment context errors,0 skips; the other198 pass. This invocation omitted the external datasource environment; shared real-JDBC fixture attempted unavailable default DB. Exact corrected failing class:
```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33417/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child07 ES_BASE_URL=http://127.0.0.1:33918 mvn surefire:test -Dtest=CalendarAttachmentIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```
Exit0, artifact2877:6 tests/zero failures/errors/skips, no source mutation. Epoch4 broad all-opt-in5963-test failure remains recorded, not rerun to confirm and not silently repaired; unrestricted aggregate gate remains controller's final whole-master gate.

### Fresh actual production runtime — A-5/A-6/A-7/A-11/A-12/A-14 and downstream contracts
- Owned MySQL8.0.36 container `expert-access-07-e5-mysql` ID7d5081843b859930621b5e35e7bea1ab0a6b024dcf514806a93ac07ddf2c3324, loopback33417, runtime DB `expert_access_07_runtime` with Asia/Shanghai JDBC. Separate main/calendar test schemas; no production DB. Loopback ES33918, safe SMTP33919, app33817 Java11.0.33 service `expert-access-07-e5-app`, PID12640, actual AuthWebConfig/JDBC/Flyway170 migrations toV171, startup artifact2818. Mail queue/schedulers disabled. Existing epoch4 tool recipe reused with this epoch's disposable names; production classpath contains target/classes+126 dependencies, never testclasses.
- Actual admin password change/login, independent HTTP admin/user1 cookie sessions, and production browser UI login. Runtime fixtures: FULL9701, READ9702, NONE9703; disabled real senderfixture07 retained; inbound9711/9712/9721/9731, outgoing9741, QA/CUSTOM/escaped malicious mail tags, original26-byte attachments9771(MAIL_RECORD),9772(INBOUND_PROCESSING),9773(MANUAL_UPLOAD), foreign9774; safe loopback ES profile fixtures. SQL seeds use utf8mb4.
- Real Chromium150 desktop1440×1000 and mobile390×844, not fabricated screenshots. Desktop FULL exactly one original shared editor, long expert name escaped, no email/account identity line, only mailbox/logout tabs. Fresh full-session API audit includes only login, scoped conversation list/messages, current notes/suspension/timing/contact/draft/profile/review/calendar-summary/manual-profile; no sender-account/config/task/global contact/tag options preload on mount/focus/visible. Browser page-error entries empty at final capture.
- Existing user1 rich editor UI sends9712: initial422 safety, actual confirmation click then200; SMTP DATA889bytes and SENT9742, audit `SEND_MANUAL_RICH_REPLY` actoruser1. Actual admin original composer similarly422→confirmation→200, SMTP849bytes/SENT9743, existing admin auditconsole retained. A separate new manual request after upgrading BetaFULL sends9721 once, SMTP840bytes/SENT9744; identical requestId replay returns same messageId with delta exactly one SMTP DATA. Body spoof operatorNameadmin still auditsuser1. Old APPROVED review4 remains APPROVED and immutable; PUT/DELETE409. Downgrade BetaREAD then identical manual send403, no fourth SMTP. Final SMTP total exactly3: two user1 sends, one explicit admin baseline send; no management/review/permission action sent automatically.
- Real materials drawer anchors fetch download/preview of all three source-owner originals200, exact26-byte original payloads. Original upload form201 creates document/attachment9775; upload itself no SMTP. Mobile drawer fits390. Foreign source/path mismatches403 without bytes; mixed transfer/ZIP reject with existing400 resource mismatch before any effects. READ downgrade actual current9701 link403 and all message attachmentCount0/names[]/outboundAttachments[].
- Existing profile UI PATCH200 saves WORKSPACE07E5 / 测试专家, version1, updatedByuser1; admin reads same snapshot. Valid full identity group staleVersion0 returns409 PROFILE_CONFLICT; foreign expertId403. Existing desktop and mobile form reused; mobile dialogx8,width374 within390, no video/history/email field. Four existing groups/22 fields remain in shared templates. Actual expert tag add200 and remove200 both responses captured, final ES tags restored to 新发现/瑞典; no admin reloads after mutation.
- Native calendar desktop create201/edit200/cancel200 and mobile create201/edit200/cancel200 through original form, fixed contact9701, hidden required selectdisabled, no global chooser. Real concurrent admin edit makes original open user form save409; reopen with fresh timestamp edits200. SQL final events1/2 CANCELLED with user notes; foreign event3 remains ACTIVE. Foreign schedule GET/PUT/cancel403 and mixed summary403, counts/SMTP/ES unchanged. Native datetime fill helper initially typed invalid values, checkValidityfalse/no POST; corrected input values then actual form submit—not a source fix or bypass of native required controls.
- READ real UI opens DRAFT1/RETURNED2/SUBMITTED3/APPROVED4: contenteditable true/true/false/false respectively. Submit/return/approve observed through real authenticated API/context token; submitted comments/events and withdrawal exercised. FULL upgrade opens old APPROVED in read-only history, returning manual selects a separate initially empty editor. Direct new manual after upgrade above never uses approved HTML. Approval/upgrade/comments/withdrawal did not send. READ QA薪资/CUSTOM补充材料 and ES labels visible, malicious label stays text (zero img[src=x] DOM); zero add/remove tag controls. Missing profile yields “标签暂不可用”, restored [] yields no placeholder/tag nodes; long label at390 has scrollWidth390.
- Actual current-owner SQL: two user1 MANUAL_REVIEW mails of9701 give personal pending total1 expert/row pendingCount2, UI chip待处理1/row2条待处理; user mail badge2/admin2 independently. Original inline suspension UI saves reason, user badge0/admin2 unaffected; resume restores. Grant downgrade moves unfinished rows toadmin, manually resolved row reopened after downgrade returnsadmin; upgradeFULL does not steal historicaladmin rows. FULL authority can resolve a historicaladmin-owned row: initial experimental expectation403 was incorrect, actual200 matches master ownership-as-query/count boundary, not an added owner-based mutation restriction.
- Denial snapshot matrix: READ/NONE direct rich sends, cross-source files, READ/NONE tag mutations, foreign profile, NONE/mixed calendar, mapped forbidden sender-account/task/unmatched routes and grant mutation all403; mixed material member validation400. Counts for mail_record/send_attempt/attachments/profile/calendar/tags/operator log unchanged with zero incremental ES/SMTP. Foreign draft created during temporaryREAD, revoked, then GET/PUT403. Wrong initial tag body used expertId instead of requiredorcidId and got400 binding; corrected exact DTO exercised403. NONE PUT rejected400 by canonical contract; revoke uses DELETE.
- Delayed actual authorized A message response held at transport while selecting B: releasing real backend response leaves B only, one B editor. Separate old FULL A response held, server downgrade, focus obtains freshREAD; released oldFULL cannot restore editor. Delayed explicit manual-save response: backend persisted200, response held, server downgrade, focus destroys editor, actual late200 released; no restored private draft/editor. Focus while profile portal open synchronously yields0 editors/0 dialogs/0 profile sessions before refetch. Visibilitychange handler in real page likewise immediately clears materials portal/editor and reauthorizesREAD.
- Real logout204 clears all editors/dialogs/private body; held-request logout probes left actual logged-out page with no private body, then real relogin after serverNONE omits A. Transport interception of logout encountered handled/canceled-request cleanup errors/timeouts; do not claim a clean forced late200 delivery for logout. Independent permanent late-callback tests cover forced callbacks; actual browser observed cancellation/logout cleanup. Chromium shared headless `bringToFront()` did not produce native hidden transitions (visibilityState stayedvisible), so visible-handler exercise uses explicit document visibilitychange dispatch, not a fabricated OS background transition. These are instrumentation limits, not claims of native window-manager coverage.
- Live stale-row selection after canonical revoke receives403 and shows original “暂无权限查看该专家邮件”;0 editor/dialog/private body, no fake success. Refresh/relogin leaves revoked A absent. Actual admin baseline retains old mailbox/list/filter/materials/composer; original seven filter fields observed at1440/390, manual saved draft sent and material links intact. Child08's new180/34/430/18px permission controls/one-Apply final dropdown are not yet implemented by07: permission changes here use same-session API equivalence as plan§251 allows; final08 actual controls remain downstream acceptance, not newly claimed UI.
- Runtime fixture/operator mistakes retained: first uploadFile matched hidden original outbound attachment input rather than drawer form; created only a runtime draft attachment, explicitly discarded via existing UI before race proof. Several guessed selector/text waits timed out after already successful responses, so outcomes above rely on actual HTTP/JDBC/SMTP, not those text waits. No source workaround for fixture/selector mistakes. Initial profile editor stays open with “已保存” normally; explicit Close used. Earlier foreign-anchor500 and unmapped wrong-verb500 not rerun or repaired.
- Actual screenshots (prefix `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/`): desktop profile `omp-sshots-15bac222d2636cbc.webp`; mobile calendar `omp-sshots-15bac2bf4b91cc34.webp`; mobile materials `omp-sshots-15bac2c89691cc35.webp`; READ APPROVED `omp-sshots-15bac3763a11cc36.webp`; FULL separate manual `omp-sshots-15bac39e4851cc37.webp`; original admin filters desktop/mobile `omp-sshots-15bac52c30a88be2.webp` / `omp-sshots-15bac53957688be3.webp`; admin rich composer `omp-sshots-15bac55603a88be4.webp`; missing tags `omp-sshots-15bac5fb5ef887b3.webp`; mobile long READ tags `omp-sshots-15bac62808b887b4.webp`; mobile saved profile `omp-sshots-15bac658c6b887b5.webp`; live403 `omp-sshots-15bac67a91b887b6.webp`. These are real captured images, with stated viewport measured at capture.
- Cleanup complete: owned app stopped via proc kill; both managed tabs released, browser tabs empty; ES/SMTP shutdown/server_close; owned Docker removed exit0; runtime args/mail payloads/attachments directory and classpath temp removed. Original captured SMTP sizes889/849/840 retained above, real screenshots remain tool artifacts. No owned service/scaffold remains.

### Handoff
- READY_FOR_VERIFICATION is not PASS or independent verification. No known remaining child07 product failure; bounded current-identity commands and reachable product commit recorded. Failed commands/instrumentation limits above retained, not erased.
- Binding downstream interfaces unchanged: independent user list; FULL shared conversation only; current message accessLevel authoritative; pending expert/mail/draft units distinct; admin original mode preserved. No child08 controls introduced early.
- Next action: controller resumes ordered child08, its final control/browser/native-background acceptance and unrestricted aggregate gates, then independent light verification/whole-master review-fast-p. Do not rerun completed children or broaden07 to unrelated aggregate defects.

## Controller terminal checkpoint — 2026-10-30T18:53:17.171Z
- Terminal independent verdict: LIGHT_PASS_WITH_NOTES; exact report retained in verify-log.md.
- Final child code head: be6437501d88a3eb283ede009eedf5cbca16b117
- Actual repair rounds: 1; fix commits: be6437501d88a3eb283ede009eedf5cbca16b117.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
