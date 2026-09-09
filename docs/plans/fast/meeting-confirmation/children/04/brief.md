# fast-p child 04 brief — 人工回复会议弹窗与可搜索时区

- Master: docs/plans/2026-09-09/00-meeting-confirmation-master.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/04-meeting-confirmation-frontend.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Shared audit/contract docs (part of the contract): docs/plans/2026-09-09/meeting-confirmation-audit.md; evidence: docs/plans/2026-09-09/meeting-confirmation-evidence/ (S-1 CSS must byte-match `meeting-confirmation.target.css`; frozen `frontend-before.md` = current source baseline excerpt; `frontend-latest-paths.txt`; `timezone-aliases.target.js`)
- Dependencies: 01 (LIGHT_PASS), 02 (LIGHT_PASS), 03 (LIGHT_PASS_WITH_NOTES incl. A1). Downstream consumer: child 05 assets (registers resources + real-browser activation).
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation (branch fast/meeting-confirmation)
- Child base SHA: a275366c1d29951814443d34bdab8b49da634232 (child 03 terminal code head)
- Execution report: docs/plans/fast/meeting-confirmation/children/04/execution.md
- Fix log: docs/plans/fast/meeting-confirmation/children/04/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 7; modify ONLY these)

1. src/main/resources/static/meeting-confirmation.js (NEW — plain IIFE, window.MailboxMeeting + Node module.exports test entry, no npm/build deps)
2. src/main/resources/static/meeting-confirmation.css (NEW — byte-for-byte copy of plan S-1 CSS; must equal evidence meeting-confirmation.target.css)
3. src/main/resources/static/mailbox-chat.js
4. src/main/resources/static/app.js
5. src/test/js/meetingConfirmation.test.js (NEW)
6. src/test/js/meetingConfirmationIntegration.test.js (NEW)
7. src/test/js/meetingConfirmationStyle.test.js (NEW)

## Work to implement (from child plan T1..T5 + Invariants I-1..I-7; S-1..S-5 are the only visual contract — verbatim, no redesign)

- T1: standalone component meeting-confirmation.js with `MailboxMeeting.create({api, contextPath, onApply, onStatus})` and controller methods open/close/dispose + pure exports filterZones/normalizeMeetingText/sanitizeDraftHtml/planMeetingInsertion (exact signatures in plan). app.js:1478 JSON prefix already exists; app.js:14414 first mountOptions gains contextPath (later mounts keep existing value); mailbox-chat instance keeps options.contextPath (never window.contextPath guess) and passes to component. No window.MailboxMeeting → manualComposeHtml renders no trigger; nothing calls undefined objects.
- T2: dialog lifecycle/state machine per S-2 DOM contract (dialog appended to document.body, showModal; one instance one dialog; Esc/close/cancel close; backdrop does not close; restore focus on close; target switch disposes without stealing focus). open captures owner/targetKey/editorRevision AFTER saveDraftFromInputs; parallel options/zones guarded by sequence; 300ms debounce POST preview only when complete+valid; stale responses never overwrite; template selection incl. custom + restore; zone search I-5 semantics (NFKC, aliases/offset normalization, ArrowUp/Down cycling, Enter explicit selection only, Esc/Tab restore selected, no-result never submits free text as zone; date change reloads offsets without changing zone id); Blob download lifecycle (createObjectURL, revoke on change/close/dispose, keep until actual click completes).
- T3: draft transaction: readManualValues/saveDraftFromInputs carry meeting deep copy (never drop via old save); snapshot fields input/preview/blockHtml/blockText/state(ready|stale)/revision; apply/edit/remove semantics incl. replace-mode QA clearing, hand-edited block stale detection (block count + normalize(innerText) vs baseline), remove keeps正文; draft restore via sanitizeDraftHtml whitelist (template.content traversal; div/p/br/b/strong/i/em/u/ul/ol/li/a/span only; strip script/style/iframe/object; unwrap others; a href http/https/mailto + target/rel fixed; keep only meeting-body-block class + data-meeting-block; no regex HTML cleanup); adoptAssembly clears meeting/Blob first; retarget marks meeting stale, preserves input, closes dialog, re-pulls options on edit.
- T4: send lock: meeting send requires state ready + current block text match; capture draftsMap/owner/key/revision/processingId/requestBody BEFORE async; module-level inFlight per owner+targetKey; payload adds meeting.input + previewAttachmentSha256 = preview.attachment.sha256 only (senderAccountCode null / QA / RAG / safety adapters untouched); sent success deletes draft only when captured revision matches, always releases inFlight; failure/cancel/UNKNOWN keep draft, release lock, restore inputs; no-meeting path unchanged. Historical sent card S-5 + host `mcHostDownloadCalendar(relativePath, filename)` in app.js (validate route + filename; fetch binary with handleAuthResponse; error via hostShowStatus; blob + temp `<a hidden download>` click then remove + revoke after 1s; no window.open). S-3 trigger/card/badge/note DOM, data-state ready/stale/sending.
- T5: resource lifecycle: teardownConversationSubViews/selectExpert/account-filter change/unmount close+dispose+abort in-flight; remove document events and dialog; revoke view Blobs; drafts cache never cleared; same-target refresh replaces only needed nodes. Tests mount the real component and dispatch events (not source includes).
- I-1 absolute: confirm writes draft+editor only; no manual-rich-reply / confirmMeeting call; cancel never writes. I-2: ownerKey = full session cache key; inner key contactId:processingId:accountCode; no expert-name global map; late responses dropped after switch/unmount; send captures old draftsMap/key/revision. I-4: all rendered values (text, dates/weekday/offset, china time, ICS, sha) come from the 01 response — no local timezone math, no preview MeetingCore; payload carries meeting+previewAttachmentSha256. I-6: no element.style writes; external text textContent/escape; restored html only from this-page capture + sanitizer; no fetch interception/mock arrays/rev classes; CSS file independent, existing CSS untouched (mailboxChatStyle exact tests must not conflict).
- Keep old UI working with zero meeting code present until 05 registers the assets (tests may inject the component).

## Environment

- Frontend JS tests run with Node built-in test runner (node --test), repo convention per K-js-tests-run-via-exec-plugin; node --check for syntax.
- Do NOT run full mvn test until all three JS suites pass; final full mvn test runs the node suites via exec plugin too (baseline: seed 3234/0/9 + node 766; child-03 head 3310/0/10 + node 766).
- CSS byte-equality target: docs/plans/2026-09-09/meeting-confirmation-evidence/meeting-confirmation.target.css (read via ABSOLUTE path).
- Baseline source excerpts are evidence files (frontend-before.md / frontend-latest-paths.txt) captured at the audit revision == child base tree; if a listed anchor line differs materially, stop and report PLAN_CONFLICT instead of adapting silently.

## Required commands (run all freshly; exact command/exit/counts in execution.md; unverifiable → NOT_RUN)

1. node --check src/main/resources/static/meeting-confirmation.js && node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js
2. node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/meetingConfirmationStyle.test.js
3. node --test src/test/js/*.test.js (full JS suite; expect 766 + new ≥ baseline count, 0 fail)
4. Full: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test (fresh; node suites run in test phase too; expect 3310+ Java / 0 fail / ~10 skipped)

## Downstream interfaces child 05 assets will consume (must match plan exactly)

- meeting-confirmation.js exposes window.MailboxMeeting (IIFE) and meeting-confirmation.css exists as standalone file; mailbox-chat.js/app.js reference them only via the guarded component existence + contextPath mountOptions flow; 05 adds the <script>/<link> resource nodes + cache-key bumps + registration (its S-1 lists exact registration lines). Component/CSS must function when loaded with NO other changes (05's own file list covers index.html registration only).
- meeting-confirmation.js module.exports test entry must not break browser IIFE loading.

## Constraints

- Only the 7-file whitelist. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- No new DB columns/tables, no backend/API changes (01..03 APIs consumed as-is), no new cache framework, no changes to existing CSS files or index.html (that is child 05).
- S-1..S-5 verbatim: do not redesign, add classes, inline styles, or replicate preview nav/mock scripts. 04 验收标准 S-1..S-5 must each be evidenced by the style test suite (byte-compare + DOM class whitelist + no-inline checks).
- Commit implementation locally as: feat(fast-p): implement 04
- Exclude docs/plans/fast/** from the implementation commit; controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
