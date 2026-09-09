# fast-p child 02 brief — 收发件箱布局与交互修复

- Master: docs/plans/2026-09-09/00-mailbox-refinement-master.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/02-mailbox-refinement-frontend.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Shared audit + verbatim baseline (part of the contract): docs/plans/2026-09-09/mailbox-refinement-audit.md; docs/plans/2026-09-09/mailbox-refinement-evidence/frontend-before.md; docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css (S-6 complete CSS contract)
- Dependencies: child 01 (done, LIGHT_PASS). Base already carries 01's backend: message `tags: List<TagView>` inline (OUTBOUND empty), summaries `expertTags: List<String>?`, recipientEmail/keyword query params, server-side sort only. Downstream consumer: child 03 (asset cache keys, must NOT be touched in this child).
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement (branch fast/mailbox-refinement)
- Child base SHA: cf257779ae420ab4c745b20aa4de6e6942a66b18
- Execution report: docs/plans/fast/mailbox-refinement/children/02/execution.md
- Fix log: docs/plans/fast/mailbox-refinement/children/02/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment and repo traps; the child plan file is the authority on requirements.

## Authorized files (exactly 7; modify ONLY these)

1. src/main/resources/static/index.html
2. src/main/resources/static/app.js
3. src/main/resources/static/mailbox-chat.js
4. src/main/resources/static/mailbox-chat.css
5. src/test/js/mailboxChatBehavior.test.js
6. src/test/js/mailboxChatStyle.test.js
7. src/test/js/mailboxInboundTags.test.js

NOT authorized: styles.css, expert-materials.js/css, trust-reply-workbench.js, any other JS test file, mailbox-chat.css cache-key value change (child 03 owns it), backend Kotlin, migrations. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT.

## Work to implement (child plan T1..T5 mapped to S-1..S-7)

- T1/S-1/S-2 host/filter/list: keep unique mailboxFilter* ids; move field group into the ⋯ popover; tabs 全部/关注/待处理 only (no waitingReply param ever sent; 全部 sends neither followed nor pendingOnly; 关注 sends followed=true; 待处理 pendingOnly=true); filter changes draft until 应用/Enter; reset clears advanced filters keeping tab/q; date-invalid local error; dynamic account options preserved; task-drill-down still restores the old toolbar; markResolved refetches current page + summary, falls back to last valid page on empty; component tab params never overridden by app-level onlyPending after first user interaction.
- T2/S-3/S-4 management/translation/tags: management overlay PORTALed under body in its own .mail-chat.mc-overlay-root (production .panel has backdrop-filter — fixed positioning must not nest inside it); cancel of status/level = zero requests; save only changed fields via the two existing endpoints with per-endpoint result collection + re-read, partial failure surfaced honestly; tag editor reuses shared renderMailboxExpertTagEditor/mutateExpertTag/fetchExpertTagsFromEs (immediate-save, clear hint text, missing-profile unavailable message); inbound-tag modal gains an optional host adapter {inboundId, source, contactId, onTagsChanged} — new chat target uses adapter, old 来信汇总/详情 branches unchanged when no adapter; POST response tags displayed directly; DELETE by tagId with silent window re-check; never generate duplicate fixed ids on one page; remove old renderExtrasBlock/thread-per-message loading; translation = mc action on existing POST /api/translate, cache key includes message source:id + body, escapeText output, no global btn-translate double binding.
- T3/S-5/S-6 cache/scroll/async: bounded module Map cache (key sessionUser/contactId/accountScope; ≤10 sessions LRU; ≤500 messages/session then drop cache and locate latest; no localStorage bodies); save on scroll/selectExpert/unmount/loadOlder/quiet-refresh; restore anchors with fallback scrollTop, 0 valid; first-visit no-cache → last message top ≈8px (constrained to scroll range), never the editor bottom; quiet refresh keeps loaded window and merges by source:id (server state wins for same keys); every async callback captures session/contact/account/request seq; loadOlder gets seq guard + busy flag; anchor restore deviation ≤2px without full-page scrollIntoView; latest button does not mark processed or send.
- T4 automated checks within the three test files: Style reads the REAL index.html source (getElementById stubs always return elements — add source-text presence assertions for new ids, per K-dom-stub-tests-hide-dangling-refs); CSS byte-compare with S-6 (evidence/mailbox-chat.target.css) verbatim as the contract; Behavior covers slow-response-vs-switch races (A late must not write into B), ≥60-message history, all/pending switching, filter clearing, same-numeric-id different-source tags, draft node not replaced.
- T5/S-7 expert-tag row: renderPerson per S-7; summary.expertTags only; display names via existing expertTagLabels mapping (window-exposed), unknown raw values escaped; container one line flex:1/min-width:0/nowrap/ellipsis, counts flex:none, native title = full complete names list, no JS width measurement/tooltip/mouseenter ES reads; [] renders nothing, null renders one grey line 标签暂不可用 (title same); NEVER fall back to mail tags; remove waiting badge + waiting branches entirely (tabs/server sort unchanged); after expert-tag add/delete, silent refresh of summary + current row/header; late responses guarded by contactId.

## Style contract (S-6) — non-negotiable

mailbox-chat.css final content must be the S-6 full text verbatim (same content in evidence/mailbox-chat.target.css). Do not cherry-pick rules; do not add classes/inline styles/global rules; reuse production shared classes (.button/.panel/.inbound-tag-chip/.expert-tag…) by descendant scoping under .mail-chat only. Do not copy preview mock body/topnav/fetch. If a real-browser or test finding shows the contract CSS is wrong, STOP and report (style contract changes need a plan amendment).

## Repo traps already verified by the controller

- window.operatorStatusOptions / window.indexLevelOptions are explicitly assigned in app.js:666/:673 — S-3 option sources resolve in a real classic-script browser. Keep that exposure intact (mailbox-chat.js must read the window props, never hardcode option lists).
- Cache keys in index.html stay at v=20260907-material-chat during this child (7 fixed-value JS tests assert the old literal; child 03 flips them). Do NOT bump keys here.
- .mc-* classes are used only by mailbox-chat.js/css; app.js mounts at #mailboxList (S-1 host ids #mailboxLegacyToolbar / #mailboxConversationPanel are NEW — they must exist in index.html and get source-text assertions in tests).
- Do not change styles.css or the shared workbench/materials internals.
- Full-suite JS must stay green: 02 may only adjust the 3 whitelisted test files; if unrelated JS tests break on your DOM changes, your change is probably at fault — fix within the whitelist, do not touch other test files.

## Required commands (run all freshly; exact output + exit codes in execution.md)

1. node --check src/main/resources/static/mailbox-chat.js
2. node --check src/main/resources/static/app.js
3. node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxInboundTags.test.js
4. node --test src/test/js/*.test.js  (full JS gate; baseline 733 pass)
5. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test (full suite incl. exec-plugin node run; baseline BUILD SUCCESS, JVM 3218 run/0 fail/9 skipped at master base — child 01 raised JVM counts to 3234)

Real-browser viewport screenshot acceptance (A-10/A-11 style, CSS-vs-browser checks) belongs to the deferred human acceptance per master plan; do not fabricate browser evidence. Machine CSS byte-comparison + DOM assertions are this child's proof.

## Constraints

- Do not modify files outside the 7-file whitelist; do not touch child 03's cache keys; no new frontend framework/test dependency; no mock data/fake endpoints — all data paths call the real 01 APIs.
- Commit implementation locally as: feat(fast-p): implement 02
- Exclude fast-p evidence (docs/plans/fast/**) from the commit; the controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
