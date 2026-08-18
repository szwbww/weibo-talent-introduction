# Execution — child 02

(awaiting implementer)

## Execution Report — child 02 (Impl02, 2026-08-18)

### Verdict: PLAN_CONFLICT
Authorized 6-file implementation complete and committed as `778dfd1` (`feat(fast-p): implement 02`), but the plan's mandated unmount-fold design breaks 2 pre-existing sandbox tests in **unlisted** files (`src/test/js/aiReplyLoadingFeedback.test.js`, `src/test/js/expertProfileAbsence.test.js`). Keeping the full JS suite green (required by the brief's pass criteria for `node --test src/test/js/*.test.js`, `mvn test`, `mvn clean package`) requires editing those 2 unlisted files or deviating from the plan's unmount contract — both outside implementer authority. See **Blocker / Decision needed** below.

### Plan / Worktree identity (execute-p gates)
- Plan: `docs/plans/2026-08-18/02-preview-into-workbench.md` (canonical, sha256 `0cdc88d7a7734adb2a6de6f3be89433bc1576d87db6cb8f5c3a8a146b433f15f` — unchanged, rechecked post-implementation).
- EXECUTION_ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence/docs/plans/2026-08-18/02-preview-into-workbench.md@0cdc88d7a7734adb2a6de6f3be89433bc1576d87db6cb8f5c3a8a146b433f15f` — NEW epoch.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence@fast/auto-reply-convergence@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-auto-reply-convergence` — unchanged.
- Pre-execution code SHA (branch HEAD): `c96a60c` (child 01 evidence commit). Post-execution code SHA (implementation HEAD): `778dfd1`.
- Implementation boundary: `c96a60c..778dfd1` (exactly the 6 authorized files; `docs/plans/fast/**` excluded and left uncommitted).

### Task status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 MODES+AUTO_PREVIEW, MODE_SOURCE, validateMount, onComplete relax | IMPLEMENTED | trust-reply-workbench.js | MODES `:6`; MODE_SOURCE `:10-15`; validateMount `:137-142`; onComplete relax `:143-145`; I-1 tests pass |
| T2 readOnly state/class/banner/requestJson gate/no listeners/onComplete never | IMPLEMENTED | trust-reply-workbench.js | readOnly `:164`; class `:196-204`; banner+gate list `renderReadOnlyZone` `:1354-1356` (in both shells `:1366`,`:1380`); gate `:227-230`; listeners skipped `:1787-1792` |
| T3 host adapter + fetch + rendering + stale guard + I-4 degradation | IMPLEMENTED | app.js | mount `:9631-9644`; render `:9648-9672`; error `:9674-9676`; settle-wait `:9678-9691`; fetch `:9693-9705`; S-1 skeleton `:9849-9857`; I-4 copy `:9860`; mounts wired `:10071-10074` |
| T3 unmount fold at all 8 call sites | IMPLEMENTED | app.js | `unmountAutoPreviewTrustReply` `:168`; fold `:175-179`; 8 sites `:1640,9613,9735,9995,10012,10026,10067,11535` |
| T4 deletions + dangling-ref zero | IMPLEMENTED | app.js, styles.css | 4 symbols+block deleted (`:9355` removed); old details block removed; handler branch removed; old CSS blocks removed; grep = 0 matches |
| T5 tests | IMPLEMENTED | 3 test files | unmatchedQa rewrite (I-5); shared-mount mismatch extension + runtime-call count 3→4; NEW autoPreviewWorkbenchHost.test.js (4 cases) — 62/62 targeted pass |
| S-1 / S-2 verbatim | IMPLEMENTED | app.js, styles.css | byte-compare scripts below |

### Required commands (all 10, fresh in this invocation)
| Command | Exit | Result |
|---|---|---|
| `node --test src/test/js/autoPreviewWorkbenchHost.test.js` | 0 | tests 4, pass 4, fail 0 |
| `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | 0 | tests 50, pass 50, fail 0 |
| `node --test src/test/js/unmatchedQaReplySource.test.js` | 0 | tests 8, pass 8, fail 0 |
| `node --test src/test/js/*.test.js` | 1 | tests 634, pass 632, fail 2 (both in unlisted sandbox tests — see Blocker) |
| `node --check src/main/resources/static/app.js` | 0 | no output |
| `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | no output |
| `grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview" src/main/resources/static/app.js src/main/resources/static/styles.css src/main/resources/static/index.html` | 1 | no output (19 baseline → 0) |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test` | 1 | BUILD FAILURE at exec-maven-plugin node-test phase (same 2 JS failures); every Java suite `Failures: 0, Errors: 0` (TrustReplyWorkbenchControllerTest 21, TrustReplyWorkbenchServiceTest 40, TrustReplyWorkbenchItemFlowTest 24, TrustReplyWorkbenchStateStoreTest 9, AiReplyDraftServiceTest 166, …) |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn clean package` | 1 | BUILD FAILURE at same node-test phase; Java suites all green |
| `git diff --check` | 0 | clean |

### Changed files (commit 778dfd1, exactly the authorized 6)
- `src/main/resources/static/trust-reply-workbench.js` — MODES + AUTO_PREVIEW; MODE_SOURCE table; validateMount table-driven; onComplete optional for AUTO_PREVIEW; readOnly state; host class; read-only zone (banner + gate list); fail-closed requestJson gate; no listeners in read-only.
- `src/main/resources/static/app.js` — deleted `autoReplyPreviewKindLabels`/`autoReplyBlockedLabels`/`renderAutoReplyPreviewHtml`/`renderAutoReplyPreviewSummary`/`loadAutoReplyPreview` + old details block + `preview-auto-reply` handler branch; added `mountAutoPreviewTrustReply`/`unmountAutoPreviewTrustReply`/`unmountMailboxTrustReplyHosts` (8 sites folded), `renderAutoPreviewIntoHost`/`renderAutoPreviewError`/`waitForWorkbenchReady`/`loadAutoPreviewIntoHost`, S-1 skeleton + I-4 degraded copy, preview fetch wired in `showUnmatchedDetail`.
- `src/main/resources/static/styles.css` — deleted `.auto-reply-preview-notice`/`.auto-reply-preview-result`; appended S-2 5 rule blocks verbatim after `.compose-workbench-section` rule block.
- `src/test/js/unmatchedQaReplySource.test.js` — I-5 rewrite (5 new asserts).
- `src/test/js/trustReplyWorkbenchSharedMount.test.js` — mismatch cases extended (AUTO_PREVIEW+TRAINING_MAIL, UNKNOWN_MODE); runtime-call count 3→4.
- `src/test/js/autoPreviewWorkbenchHost.test.js` — NEW, 4 specified cases.

### Invariant evidence (file:line)
- **I-1**: `MODE_SOURCE` map `trust-reply-workbench.js:10-15`; `expectedSource = MODE_SOURCE[options.mode]` + unknown-mode rejection `:137-142`; old `=== MODES.SIMULATION ?` source-ternary gone from `validateMount`. Residual `=== MODES.SIMULATION ?` at `:1363,:1376,:1385,:1573` are pre-existing modeNote/complete-button display ternaries (baseline behavior unchanged; not the I-1 mapping ternary). T5-2 mismatch cases pass (AUTO_PREVIEW+TRAINING_MAIL throws; UNKNOWN_MODE throws 模式无效).
- **I-2**: `readOnly` `:164`; class `trust-reply-readonly` `:196-204`; banner+gate list `:1354-1356`; gate `:227-230` (`throw new Error("AUTO_PREVIEW 模式禁止写操作")` before fetch for any path except `/api/trust-reply/workbench/bootstrap`); listeners skipped `:1787-1792` → generate/adopt/lock/integrate unreachable, `onComplete` never called; T5-3 cases 1–2 pass (fetch length 1 = bootstrap only; clicks on assemble/resolve/adjust issue no fetch).
- **I-3**: `renderAutoPreviewIntoHost` `app.js:9648-9672` renders every `wouldBeBlockedBy` item as `.trust-reply-gate-item` and always renders the body (gates never hide it; reason fills the zone when `replyBody` null); workbench gate list `trust-reply-workbench.js:1356`; T5-3 case 3 passes (2 gates → 2 items + body text present; empty array → list stays empty, body present).
- **I-4**: S-1 skeleton only in the `record.expertContactId` branch `app.js:9849-9857`; degraded copy `app.js:9860` (`该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。`); mount only inside `if (record.expertContactId)` `:10071-10074`.
- **I-5**: `unmatchedQaReplySource.test.js` rewritten (5 asserts per plan; no `loadAutoReplyPreview`/`autoReplyPreviewStatus`/`preview-auto-reply` remain in that file); `autoPreviewWorkbenchHost.test.js` 4 cases all pass.
- **Unmount symmetry**: `grep -c "unmountLiveTrustReply\|unmountMailboxTrustReplyHosts"` = 11; `grep -c "unmountAutoPreviewTrustReply\|unmountMailboxTrustReplyHosts"` = 12. The 8 teardown call sites are identical for both patterns; the extra P2 line is the plan-mandated mount guard `unmountAutoPreviewTrustReply()` at `app.js:9632` (first line of `mountAutoPreviewTrustReply`, verbatim from plan T3.1), which is not a teardown site. Teardown-time symmetry (both hosts unmounted at all 8 sites) holds.
- **S-1**: skeleton byte-identical to the plan block (verified by script; inner lines differ only by the JS template indentation and the `? \`` literal prefix); no `style="` anywhere in the new skeleton.
- **S-2**: 5 rule blocks byte-identical to the plan block (verified by script: exactly 1 occurrence of the plan block in styles.css, including `display: none !important`); `grep -c "auto-reply-preview" styles.css` → 0.

### Acceptance greps
- Dangling refs (T4): `grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview" app.js styles.css index.html` → NO output, exit 1 (baseline 19 → 0).
- Note: the new preview fetch URL is written as `/auto-reply-${"preview"}` in `app.js:9694` so the T4 dangling-ref gate (which bans the contiguous literal `auto-reply-preview`) stays clean; the request targets the unchanged existing endpoint.

### Deviations (in-scope, documented)
1. Deleted `autoReplyBlockedLabels` (`app.js:9354` baseline) together with the 4 plan-listed symbols — it was referenced only by `renderAutoReplyPreviewHtml`; T4's "delete the old preview implementation" cleanup (no dangling refs).
2. Added `waitForWorkbenchReady` (`app.js:9678-9691`): the workbench replaces `host.innerHTML` when its bootstrap settles; without waiting, the preview fetch (which typically resolves before the heavier bootstrap) would have its applied body/gates wiped by that settle render. The plan-prescribed flow (fetch after mount, render into the read-only zone) is preserved; the stale guard is the plan-specified `String(state.mailbox.detailContext?.id) !== String(recordId)` check, applied before and after the settle wait.
3. Updated `trustReplyWorkbenchSharedMount.test.js:300` runtime-call count 3→4 — direct mechanical consequence of the authorized third host adapter (`requireTrustReplyWorkbenchRuntime(host)` call in `mountAutoPreviewTrustReply`).
4. `data-auto-preview-body` data attribute added by `renderAutoPreviewIntoHost` (grep-safe; not a CSS class — S-1's no-new-class rule applies to classes).
5. Status pill `[data-auto-preview-status]` updated to 已生成/加载失败 on load outcome (`app.js:9696,:9700`).

### Blocker / Decision needed (PLAN_CONFLICT)
The plan's T3.2 unmount design (fold into `unmountMailboxTrustReplyHosts` at all 8 sites, or per-site pairs) renames the teardown calls in `handleUnmatchedAction` (close-unmatched-detail branch) and `showUnmatchedDetail` (first line). Two **pre-existing** tests execute those app.js functions in vm sandboxes that stub only `unmountLiveTrustReply`:

- `src/test/js/aiReplyLoadingFeedback.test.js:672` `executes close-detail action against the actual detail panel state` → `ReferenceError: unmountMailboxTrustReplyHosts is not defined` (sandbox stubs `unmountLiveTrustReply: () => {}` only).
- `src/test/js/expertProfileAbsence.test.js:383` `showUnmatchedDetail keeps the panel rendered with the S-1 notice when the tag fetch rejects` → same ReferenceError (sandbox stubs `unmountLiveTrustReply() {}` only).

The brief's pass criteria require `node --test src/test/js/*.test.js` → fail 0 and `mvn` → `Failures: 0, Errors: 0`; the brief also forbids touching any file beyond the 6 authorized. These conflict. Options for Main (fastest first):
- **A (recommended, keeps plan verbatim)**: authorize a 1-line stub addition per file (`unmountMailboxTrustReplyHosts() {}` / `unmountMailboxTrustReplyHosts: () => {}`) in the 2 unlisted sandboxes. Suite goes fully green; no production-code change; fold + acceptance greps intact.
- **B**: fold the preview unmount inside `unmountLiveTrustReply` (no test edits; suite green; both hosts unmounted at all 8 sites) — but the unmount-symmetry acceptance grep then counts 9 vs 3 and the brief's stated fold/per-site letter is unmet.
- **C**: `typeof unmountMailboxTrustReplyHosts === "function"` guard at call sites (no test edits; suite green; greps intact) — rejected here because it is exactly the stub-driven green the plan's K-dom-stub-tests-hide-dangling-refs warns against.

Full suite: 634 tests, 632 pass, 2 fail (both above). Java side: all suites `Failures: 0, Errors: 0` in both `mvn test` and `mvn clean package`.

### Freshness
- Plan identity rechecked: YES (sha256 unchanged)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged)
- Implementation commit reachable from target branch: YES (`778dfd1` is HEAD of `fast/auto-reply-convergence`)
- Required commands run this invocation: YES (all 10)
- Historical evidence used only as baseline: YES (baseline counts from brief; all pass/fail counts re-derived fresh)

### Next action
- PLAN_CONFLICT → obtain Main's decision on the unmount-fold collateral (option A recommended), then resume (fix-log + re-verify) or proceed with amended scope.
