# Execution Report — 03-fact-order-drag (P3)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/03-fact-order-drag.md`
- Plan SHA-256: `4a062685f043e574171f49ad73d584ec832e2839d9c073613eb5ea927c678616`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/03-fact-order-drag.md@4a062685f043e574171f49ad73d584ec832e2839d9c073613eb5ea927c678616`
- Execution epoch: NEW
- Approval basis: current invocation (fast-p child brief + approved child plan; plan identity gate passed at start and rechecked at handoff, hash unchanged)
- Executor: `Impl03FactDrag`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage`
- Target branch: `fast/grounded-coverage`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage@fast/grounded-coverage@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-grounded-coverage`
- Pre-execution code SHA: `1df49e8e37f24d4040e27e1d78052a8645253e2b` (child 02 terminal code head = P3 base; HEAD also carried controller evidence commits `7955162`/`ec68a4d`/`d977980` — docs only, untouched)
- Post-execution code SHA (implementation commit): `52f3a90b496b52b329bacf0d45b16902e45346a4`
- Evidence HEAD: N/A (controller commits fast-p evidence separately; docs/plans/fast/** left uncommitted)
- Implementation boundary: `1df49e8e37f24d4040e27e1d78052a8645253e2b..52f3a90b496b52b329bacf0d45b16902e45346a4`

---

## 阶段 A — Drag spike (I-4): PASS, no degradation

Spike executed in **real Chromium** (omp browser tool, headless) against a standalone page replicating the exact chip DOM under test: `.fact-chip` (`inline-flex`, contains a `<button>`) → `.fact-grip` (`data-role="fact-grip"`, `draggable="true"`) inside a `flex-wrap: wrap` container. Real pointer input via `page.mouse.*` plus synthetic `DragEvent` with a real `DataTransfer` for the cross-row drop (the browser engine's own event/DataTransfer/hit-test path — the part jsdom cannot provide).

| Spike check | Result (evidence) |
|---|---|
| A-1.1 dragstart on the grip, not stolen by the chip's `<button>` | **PASS** — real mouse drag from grip fired `dragstart OK id=1 from=SPAN.fact-grip` (target = the grip SPAN, never the button); repeated across 4 real drags |
| A-1.2 dragover + `preventDefault()` → drop receives `dataTransfer.getData()` | **PASS** — real same-row drag: `drop OK data=1 ontoChip=3 mark=after` (dataTransfer round-trip); cross-row synthetic `DragEvent`: `drop OK data=4 ontoChip=5 mark=before` |
| A-1.3 drop-target computation (chip midline left/right) under `flex-wrap: wrap` | **PASS** — layout wrapped into 3 rows (chips 1-2 / 3 / 4-5); real pointer drag from row 1 to row 3 computed `chip=5 rectLeft=160 rectRight=275 clientX=181 => BEFORE` (correct: 181 < mid 217.5); synthetic cross-row `dragover x=188 => BEFORE` correct |
| IP-3: drop over the `×` button must not click/delete | **PASS** — real drag ended with pointer inside button 2's rect (`x=356` in `347-367`): `dragend`, **no** `CLICK on remove button` logged; drop events never synthesize a click; app `drop` handler additionally `preventDefault()`+`stopPropagation()` |

**Recorded headless-Chromium artifact (not a capability failure):** `page.mouse.up()` after a long **vertical** real drag terminates the native DnD session with `dragend` and no `drop` (known headless `Input.dispatchMouseEvent` DnD limitation); the same-row real drag committed the drop correctly. Failure mode is a harmless **cancel** (marks cleaned by `dragend`, no state change, no accidental delete) and real (headed) browsers do not exhibit it. Cross-row drop delivery was therefore verified with the engine's own `DragEvent`/`DataTransfer`/`elementFromPoint` machinery.

**Conclusion: PASS — full drag implementation + keyboard path (no degradation).** The implementation below matches this recorded conclusion. Note on method: the spike ran before any workbench modification (spike-first per I-4), so it used an equivalent standalone DOM rather than the unmodified app; the shipped handlers were then exercised end-to-end in the vm-harness unit tests.

---

## 阶段 B — Implementation (per file)

| # | File | Change |
|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | Top-level pure `reorderFactIds(ids, fromId, toIndex)` (B-1; insertion index in the post-removal array, clamped to `[0, ids.length-1]`, length-conservation assertion; exposed on the frozen namespace as `TrustReplyWorkbench.reorderFactIds` for the plan-mandated unit tests). `moveFact(requestKey, factId, toIndex)` (B-2) — goes **only** through `changeRequestFacts` (I-1), short-circuits when order unchanged, disabled-guard at entry (same pending flags as `addFact`/`removeFact`). Delegated drag handlers on the host (bubbles from the chip list; B-3): `onDragStart` (grip-only via `data-role="fact-grip"` + `dataset.role` guard, sets `dataTransfer` + `data-dragging`), `onDragOver` (preventDefault, chip-midline before/after marks, clears other chips' marks), `onDrop` (preventDefault + stopPropagation for IP-3, `getData("text/plain")`, self-drop no-op, target-index math with post-removal insertion), `onDragEnd` (clears all marks). Keyboard (I-3, B-4): `onKeydown` grip branch for `ArrowLeft`/`ArrowRight` → `onGripArrowKey` (`toIndex = currentIndex ∓ 1`, sets `state.pendingFocusFactId`); `render()` restores focus to the same fact's grip (flag kept until found). `renderFactSection`: S-1 chip template (grip with `draggable` omitted + `aria-disabled="true"` when `factActionsDisabled`), S-3 hint line after the count, `data-role="fact-chip-list"` on the existing container, `gripHintId` derived from instanceId+requestKey; **no sorting of `factRuleIds`** (I-2). Four new delegated listeners registered for non-readOnly hosts. |
| 2 | `src/main/resources/static/styles.css` | S-2 block of 8 rules appended verbatim immediately after the `:disabled` rule (line 7819): `.trust-reply-fact-grip` (+`:hover`, `:focus-visible`, `[aria-disabled="true"]`), `.trust-reply-fact-chip[data-dragging="true"]`, `[data-drop-before="true"]` (box-shadow left), `[data-drop-after="true"]` (box-shadow right), `.trust-reply-fact-grip-hint`. Zero edits inside 7720-7819 or 6566-6605 (verified: the diff is a single insertion-only hunk at 7818+). |
| 3 | `src/test/js/trustReplyWorkbench.test.js` | C-1: source-text assertions (grip skeleton attrs incl. `tabindex="0"`/`aria-label`, `data-role="fact-grip"`, `trust-reply-fact-grip`, hint line, styles.css `.trust-reply-fact-grip {` + drop-mark selectors — K-dom-stub-tests-hide-dangling-refs; `style=` count stays 1; no `factRuleIds.*sort|reverse`); `reorderFactIds` pure cases (前移/后移/首位/末位/缺失 id/越界钳制/长度守恒/空输入); keyboard reorder via `changeRequestFacts` + payload-order + focus-restore assertions (I-2/I-3); dragstart/dragover/drop reorder with mark cleanup; no-op reorder → zero `changeRequestFacts` (zero confirm + zero fetch); disabled state ignores dragstart/ArrowLeft; `moveFact` body contains `changeRequestFacts` and no direct `serializeRequestFactSelections`/`requestJson`/`fetch` (I-1). Runtime tests use a small `vm` sandbox harness in the same file (FakeElement/FakeDocument, stateful bootstrap fetch that echoes the latest payload so the server-canonical matrix check stays consistent). |

## 阶段 C — Regression

All commands below ran freshly in this invocation on the final implementation state, JDK 11 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`), without `-DskipNodeTests`:

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=… mvn test` (full regression gate, brief-required) | PASS | exit 0; `BUILD SUCCESS`; surefire `Tests run: 2589, Failures: 0, Errors: 0, Skipped: 4` (4 opt-in/condition-gated, incl. Docker-gated `FlywayMigrationIntegrationTest`); exec-maven-plugin node-test: `tests 658, pass 658, fail 0` |
| `JAVA_HOME=… mvn clean package` (plan 验证命令 build) | PASS | exit 0; `BUILD SUCCESS`; identical counts (2589/0/0, node 658/658); WAR assembled |
| `node --test src/test/js/trustReplyWorkbench.test.js` (brief-required, focused) | PASS | exit 0; `tests 16, pass 16, fail 0` (9 existing + 7 new P3) |
| `git diff --check` (brief-required) | PASS | exit 0, no output |

## Verification evidence (invariants / gates)

- **I-1** (reorder only through `changeRequestFacts`): source test `moveFact only commits through changeRequestFacts` green (`moveFact` body has `changeRequestFacts`, no `serializeRequestFactSelections`/`requestJson`/`fetch`); runtime no-op test asserts zero confirm **and** zero new fetch (changeRequestFacts always bootstraps, so zero fetch ⇒ zero calls); `grep -n "request.factRuleIds ="` still shows exactly one assignment point inside `changeRequestFacts` (plus two constructor spreads and one payload spread, all pre-existing).
- **I-2** (order contract): `grep -nE "factRuleIds.*(sort|reverse)" src/main/resources/static/trust-reply-workbench.js` → **empty**; runtime test asserts post-move bootstrap payload `factRuleIds` equals the rendered chip order bit-for-bit (`[2,1,3]`).
- **I-3** (keyboard equivalence): ArrowLeft/ArrowRight tests green (chip moves one slot, focus restored to the same fact's grip via `pendingFocusFactId`); source asserts `tabindex="0"` and `aria-label` on the grip template.
- **I-4** (spike-first): 阶段 A above — PASS, implementation matches.
- **S-1/S-3** (template): landed chip/grip/button attribute sets and order match the plan skeleton (grip: class → data-role → draggable → tabindex → role → aria-label → aria-describedby → [aria-disabled]; chip gains `data-request-key`, conditional `title` and button `disabled` preserved for N1); hint `<span class="trust-reply-fact-grip-hint" id="…">拖动 ⋮⋮ 或用 ← → 调整顺序</span>` inserted after the count. `grep -c "style=" trust-reply-workbench.js` = **1** (unchanged from baseline, no inline styles).
- **S-2** (styles verbatim): programmatic comparison `FILE BLOCK == PLAN BLOCK` → **True** (48 lines, 8 rules, appended after the `:disabled` block at line 7819); `git diff styles.css` is a single insertion-only hunk `@@ -7818,6 +7818,55 @@` — zero deleted lines, zero edits inside 7720-7819.
- **N4/N5**: `git diff src/main/resources/static/app.js` → **empty**; styles.css diff contains no hunk touching 6566-6605.
- **Regression**: full `mvn test` + `mvn clean package` green (above).

## Deviations

- **Spike host**: 阶段 A ran on a standalone Chromium page replicating the exact chip DOM instead of the unmodified app (the spike must precede any implementation, so the app had no drag support yet); the shipped handlers were then covered end-to-end by the vm-harness unit tests. Spike conclusion (PASS) unchanged.
- **Listener delegation point**: drag/keyboard listeners are delegated on the persistent `host` element (the repo's established pattern — every listener in this file is host-delegated) rather than bound to each re-created `.trust-reply-fact-chip-list`; handlers verify via `closest('[data-role="fact-chip-list"]')` that the event originated in a chip list. Functionally identical delegation, re-render-safe.
- **`reorderFactIds` exposure**: added to the frozen `TrustReplyWorkbench` namespace (`Object.freeze({ mount, reorderFactIds })`) so the plan-mandated pure-function unit tests can reach it; purely additive — `app.js` and existing tests only consume `mount`/`unmount`.
- **`data-role="fact-chip-list"`**: added to the existing chip-list container div (delegation-by-data-role, repo convention; not a class, not inside 7720-7819).
- **Self-drop no-op**: dropping a chip onto its own before/after mark short-circuits in `onDrop` (standard DnD UX; prevents a self "drop-after" from shifting the chip past its neighbor). Keyboard no-ops at the head/tail already fall out of the `toIndex` clamp + unchanged-order short-circuit.
- **Headless artifact**: recorded in 阶段 A; affects only the automation input path, not shipped behavior.

## Freshness

- Plan identity rechecked: YES (hash `4a062685…` unchanged at handoff)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged; `--expect-*` flags passed)
- Reported commits reachable from target branch: YES — `52f3a90` is `HEAD` of `fast/grounded-coverage`, parent `d977980` (evidence HEAD), base `1df49e8` ancestor
- Required commands run this invocation: YES (all four, on final state)
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` against the four gates (I-1..I-4, S-1..S-3, N4/N5, regression).

---

### Finalization note (2026-08-19, controller)

Evidence re-recorded in the terminal evidence commit to include all four child artifacts (brief/execution/verify-log/fix-log). No content change beyond this note; fix_round=0.
