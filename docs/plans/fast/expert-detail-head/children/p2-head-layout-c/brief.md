# P2 Child Brief — p2-head-layout-c

## Contract
- Plan (complete approved contract, read fully before implementing): `docs/plans/2026-08-14/expert-detail-head-p2-head-layout-c.md` (AMENDED A2: pass-criteria test counts corrected; T1-T11 unchanged)
- Master (shared invariants + shared verification commands): `docs/plans/2026-08-14/expert-detail-head-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head`
- Branch: `fast/expert-detail-head`
- Child base SHA: `111180741ec46bea796e81a60e513769d2de534c` (P1 terminal Code head; P1 implementation already present and verified LIGHT_PASS_WITH_NOTES)

## Global constraints (master plan)
- M-1: authoritative sender account = DB binding; `send-manual-mail` keeps `senderAccountCode: null` (I-1); never read `#senderBindingSelect` value into the send payload.
- M-3: `renderExpertTagEditor` default (no `layout` param) output byte-identical; mailbox views (`renderMailboxExpertTagEditor`) unchanged.
- M-4: JS gate is `node --test <file>`; `verify.sh` is NOT a gate.
- JDK: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for mvn; bare `mvn` fails.

## Authorized files (ONLY these 4 may change)
1. `src/main/resources/static/app.js`
2. `src/main/resources/static/styles.css`
3. `src/test/js/expertProfileAbsence.test.js`
4. `src/test/js/contactHeadLayout.test.js` (**new file**)

## Key invariants (implement exactly, per plan I-1..I-10)
- I-1: `send-manual-mail` branch keeps `senderAccountCode: null` in request body.
- I-2: `updateSenderBindingDirtyState()` sets all three (note.hidden, sendBtn.disabled, pill data-dirty) from one function; dirty = `select.value !== (select.dataset.original || "")`.
- I-3: `data-original` written as `contact.boundSenderAccountCode || ""` at render; sole dirty-check source; NO `selectedIndex` inference.
- I-4: fold state `state.contactHeadExpanded` (init false), survives expert switch; `#contactHeadMoreRow` hidden + `#contactHeadMoreToggle` aria-expanded from it.
- I-5: `renderExpertTagEditor(tags, orcidId, level, editorId = "expertTagEditor", profileMissing = false, layout = "section")` — `layout !== "inline"` output byte-identical to before (S1/S2_EXPECTED tests pass).
- I-6: inline root has `class` containing `expert-tag-editor`, `id="${editorId}"`, `data-orcid`, `data-level`, `data-layout="inline"`.
- I-7: `updateExpertTagEditor` re-reads `editor.dataset.layout` and passes it back (default section); no layout loss after add/remove tag.
- I-8: `.expert-tag-editor.is-inline.tag-editor-loading { min-height: 0; }` exactly once.
- I-9: unbound → gray dot `.sender-binding-dot.is-unbound` + text `未绑定`.
- I-10: `data-panel="mail-preview"` appears exactly 2x; `data-panel="template"` count equal; four sub-tab keys/order unchanged.
- Zero-diff must-NOT-change: `renderMailboxExpertTagEditor` calls (`app.js:9031/9600`), `updateSaveButtonState` (`:8799-8824`), list item tag chips (`:4734/:4744`), `#contactHeadActions` container in index.html, ES read via orcid+level.

## Style contract (verbatim from plan S-1..S-9; append-only under `/* === 专家详情头部 C 布局 === */`)
- All new CSS appended at end of `styles.css` under the new comment block; NEVER insert into existing rule blocks.
- `.dropdown`/`.dropdown-menu`/`.expert-tag` variants/`.inbound-tag-editor-chips`/`.mail-expert-overview .expert-tag-editor`/`.contact-head-status-row`/`.contact-head-mail-row` existing blocks NOT modified.
- Delete `styles.css:1556-1567` (`.metadata-card-value .sender-binding-editor` dead rules) and `app.js` Sender Binding Card block (with comment) `:7061-7076`.
- S-8 skeleton: `contact-head-main-row` + `contact-head-divider` + `sender-binding` pill/pop (ids `senderBindingToggle`, `senderBindingPop`, `senderBindingSelect`, `senderBindingDirtyNote`, `sendManualMailBtn`, `contactHeadMoreRow`, `contactHeadMoreToggle`, `manualMailOption`); status row hidden by default; `contact-head-mail-row` must NOT appear in app.js anymore.
- If `styles.css` lacks `.button[disabled]`, append `.contact-head-actions .button[disabled] { opacity: 0.5; cursor: not-allowed; }` in the new block (note in PR).
- Inline editor: `layout === "inline"` → `expert-tag-editor is-inline` root (S-7); chips >3 → hidden on 4th+ + `expert-tag-more-btn` `+N`; empty tags → `暂无标签`; `profileMissing === true` → `expert-tag-nodoc` pill `ES 无画像`, NO data-action attrs.
- NO inline `style="` in app.js (existing hit set unchanged); no new classes beyond S-1..S-6.

## Implementation steps (plan T1..T11)
T1 state `contactHeadExpanded: false` (after `mailSendOptions: [],`); T2 rewrite `#contactHeadActions` template (S-8) + move select-fill block up + call `updateSenderBindingDirtyState()` after fill; T3 delete Sender Binding Card + dead CSS; T4 `updateSenderBindingDirtyState()` + change-delegate branch for `senderBindingSelect` (existing 3-id branches untouched); T5 popup open/close listeners on `#contactHeadActions` + document (pattern `app.js:11405-11424`); T6 `toggle-contact-head-more` branch before `select-expert`; T7 `renderExpertTagEditor` inline early-return (WARNING: no `)` inside default param list — test file extracts fn source by regex); T8 `updateExpertTagEditor` passes layout; T9 `expert-tags-expand` branch; T10 two name-row sites (loadContactDetail + showExpertDetail) inline editor + remove standalone row; T11 tests: +1 describe/4 cases + CSS-assert in expertProfileAbsence.test.js; NEW contactHeadLayout.test.js with 9 cases per plan; `senderBindingDisplay.test.js` NOT modified.

## Required commands (run ALL, record command + exit code + counts)
```bash
node --test src/test/js/contactHeadLayout.test.js      # new file: 9 cases, # fail 0
node --test src/test/js/expertProfileAbsence.test.js   # 11 existing + 4 new, # fail 0
node --test src/test/js/contactHeadLayout.test.js src/test/js/expertProfileAbsence.test.js src/test/js/senderBindingDisplay.test.js src/test/js/expertMailPreviewTab.test.js   # # fail 0; counts per amended A2 (see plan; use actual output)
node --check src/main/resources/static/app.js          # exit 0, no output
node --test src/test/js/*.test.js                      # all JS, # fail 0
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   # full regression, node --test record present
git diff --check                                       # no output
```
Note: after P1, `expertMailPreviewTab.test.js` has 13 tests. P2's pass criteria defers to actual run output ("以实跑输出为准，不得倒推").

## Downstream interfaces (nothing after P2; handoff contract)
- P1 preview behavior must remain: `renderExpertMailPreview` untouched by P2; A-9 cross-path works (rebind → re-render shows new signature).
- M-3 mailbox tag editor output byte-identical.

## Deliverable
- Commit implementation locally ONLY (product code + tests), excluding `docs/plans/fast/`:
  `feat(fast-p): implement p2-head-layout-c`
- Append full execution record to `docs/plans/fast/expert-detail-head/children/p2-head-layout-c/execution.md` (do not commit).
- Return ONLY: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, implementation commit SHA, command summary, report path.
- Do NOT push, merge, amend, rewrite history, or touch files outside the 4 authorized.
