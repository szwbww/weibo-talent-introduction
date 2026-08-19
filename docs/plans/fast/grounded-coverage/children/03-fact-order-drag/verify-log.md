## Light Verification: LIGHT_PASS
Child: 03-fact-order-drag (docs/plans/2026-08-19/03-fact-order-drag.md)
Boundary: 1df49e8e37f24d4040e27e1d78052a8645253e2b..52f3a90b496b52b329bacf0d45b16902e45346a4
Verifier: Verify03FactDrag

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Implementation commit d977980..52f3a90 changes exactly the 3 authorized files: styles.css (+49), trust-reply-workbench.js (+166/-4), trustReplyWorkbench.test.js (+358). `git diff 1df49e8..52f3a90 -- app.js` empty (N5). styles.css diff is a single insertion-only hunk `@@ -7818,6 +7818,55 @@` — zero deletions; region 7720-7819 appears only as unchanged context (7818-7819), 6566-6605 absent (N4). JS hunks confined to planned regions: ~119 reorderFactIds (B-1), ~214 pendingFocusFactId state (B-4), ~1379-1492 moveFact + drag handlers (B-2/B-3), ~1517 keydown grip branch (B-4), ~1566/1609 chip template + hint (B-5/S-1/S-3), ~1716 render() focus restore, ~2184 listener registration, namespace export. |
| Plan and invariants | PASS | I-1: moveFact body (`trust-reply-workbench.js:1382-1390`) submits only via `changeRequestFacts`; `grep -n "request.factRuleIds ="` shows exactly 1 write point at :1354 inside changeRequestFacts (others are constructor/payload spreads, pre-existing); serializeRequestFactSelections still 5 call sites (:618/635/655/675/1035), none added; test "moveFact only commits through changeRequestFacts" asserts body has changeRequestFacts and no serializeRequestFactSelections/requestJson/fetch; no-op test asserts zero changeRequestFacts (zero fetch + zero confirm). I-2: `grep -nE 'factRuleIds.*(sort|reverse)'` empty (exit 1); render maps `request.factRuleIds` directly with no sort; payload-order tests assert `payload.factRuleIds` deep-equals rendered order (`[2,1,3]` keyboard test, `"2,3,1"` drag test). I-3: grip template carries `draggable="true"`, `tabindex="0"`, `role="button"`, `aria-label="拖动或用左右方向键调整「…」的顺序"`, `aria-describedby` (:1571-1574); keydown ArrowLeft/ArrowRight branch with preventDefault → onGripArrowKey → moveFact (:1520-1527); focus restored via state.pendingFocusFactId consumed once in render() (:1719-1730); test asserts focus back on same fact. I-4: execution.md 阶段 A records spike PASS in real Chromium (A-1.1 grip dragstart not stolen by button, A-1.2 dataTransfer round-trip, A-1.3 wrap-row midline computation, IP-3 no delete on drop over ×); implementation = drag on grip only + keyboard, matches recorded conclusion. S-1: chip template attr set/order matches plan skeleton (chip: class→data-fact-id→data-request-key→title; grip: class→data-role→draggable→tabindex→role→aria-label→aria-describedby→[aria-disabled]); `grep -c "style="` = 1, unchanged from baseline (no inline styles); no extra classes. S-2: programmatic diff plan block vs styles.css block → identical 48 lines / 8 rules, appended immediately after :disabled rule (line 7819). S-3: hint `<span class="trust-reply-fact-grip-hint" id="{instanceId}-fact-grip-hint-{requestKey}">拖动 ⋮⋮ 或用 ← → 调整顺序</span>` placed after count inside fact-head; id instance+request derived (no cross-summary collision). |
| Required commands | PASS | Fresh, JDK11: `node --test src/test/js/trustReplyWorkbench.test.js` → exit 0, tests 16, pass 16, fail 0. Fresh: `git diff --check` → exit 0, no output. Full-suite claim verified via artifacts: surefire XML aggregate = tests 2589, failures 0, errors 0, skipped 4 (matches implementer); full node suite `node --test src/test/js/*.test.js` freshly run → pass 658, fail 0 (matches implementer's 658); surefire report mtime 17:11:42 < commit 52f3a90 (17:12:52), consistent with tests run on final state pre-commit. Full `mvn test` not rerun (optional; XML + fresh node suite verify the claim). |
| Downstream interfaces | PASS | None — P3 is the last child; brief declares "Downstream interfaces: None". No backend/product file in diff other than the 3 authorized; backend zero changes confirmed by diff stat. |

### AUTO_FIX
- N/A — no four-gate violation found; the 6 deviations recorded in execution.md (spike on standalone Chromium page, host-level listener delegation, reorderFactIds on frozen namespace, data-role="fact-chip-list" additive attribute, self-drop no-op short-circuit, headless vertical-drag cancel artifact) are all within-scope faithful readings of the plan (B-1/B-3/N4/C-1) or documented additive choices; none requires correction.

### RECORD_ONLY
- N/A — nothing outside the light gate surfaced; all deviations are plan-contemplated and documented in execution.md.

### Required Action
- COMPLETE_CHILD

### Finalization note (2026-08-19, controller)

Evidence re-recorded in the terminal evidence commit; verifier report content unchanged since signature.
