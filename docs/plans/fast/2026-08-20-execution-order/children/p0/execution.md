# P0 Execution Report — SSE 生成错误码透出 + 工作台状态强制重置入口

- Executor: `P0Implementer`
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P0-sse-error-code-and-state-reset.md`
- Plan SHA-256: `68f80155354cdbe2d7ae6f9d5a53e283a5aa08977dabbef965a8e7f4f67538a9` (unchanged pre/post execution)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P0-sse-error-code-and-state-reset.md@68f80155354cdbe2d7ae6f9d5a53e283a5aa08977dabbef965a8e7f4f67538a9`
- Execution epoch: NEW
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Target branch: `fast/2026-08-20-execution-order`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order@fast/2026-08-20-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Child base SHA: `66e1036d5e5d9d33f2b59655f20063ed90fa9015`
- Pre-execution HEAD: `15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8` (plan seed commit)
- **Implementation commit: `8ea1e241b5703e967da9861847663e67e5eb3bdc` (`feat(fast-p): implement P0`)** — HEAD of target branch, 9 files, 386 insertions / 15 deletions
- Result: **READY_FOR_VERIFICATION**

## Changes per authorized file (exactly 9)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` | +`import org.slf4j.LoggerFactory`; +`private val logger` (line 30); worker 3rd catch `catch (_: Exception)` → `catch (ex: Exception)` binding `ex`, `code = (ex as? TrustReplyWorkbenchException)?.code ?: CODE_GENERATION_FAILED`, `logger.warn("AI reply generation failed: generationId={}, code={}", generationId, code, ex)`, payload `{generationId, code, message}` (lines 82-87); +`const val CODE_GENERATION_FAILED = "AI_REPLY_GENERATION_FAILED"` in companion (line 136). `catch (_: AiReplyGenerationCancelledException)` (:80-81), `catch (_: RejectedExecutionException)` (:92), `sendLocked` catch (:304) unchanged. |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | Same-shape catch change (:530-535) with fully-qualified `com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException` / `...AiReplyGenerationCoordinator.CODE_GENERATION_FAILED`; +`private val logger = org.slf4j.LoggerFactory.getLogger(...)` (:87). **Net-zero line diff (6+/6−)** to preserve `OperatorStatusWriteSeamGuardTest` pinned noise-site lines 203/1099 (see Deviations). |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | +`deleteBySource(sourceType, sourceId)` (lines 101-113): `DELETE FROM trust_reply_workbench_state WHERE source_type = :sourceType AND source_id = :sourceId`, returns Int, no `pruneExpired`, no `throwStateConflict`. Existing `delete/save/load/pruneExpired/decodePayload` untouched. |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | +`resetState(source)` after `deleteState` (lines 593-604): `require(source.sourceId > 0)`, `stateStore.deleteBySource(source.sourceType.name, source.sourceId)`, returns `TrustReplySavedState("DELETED", 0)`; deliberately does NOT call `resolveSource`. `deleteState` untouched. |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | +`@PostMapping("/state/reset")` endpoint (lines 114-115) → `workbenchService.resetState(request.source.toDomain())`; +`data class TrustReplyResetStateHttpRequest(val source: TrustReplySourceHttpRequest)` (lines 321-323) — source only, no version field. Existing `@DeleteMapping("/state")` untouched. |
| 6 | `src/main/resources/static/trust-reply-workbench.js` | B-1: `WORKBENCH_ERROR_TEXT` 16-entry code→中文 table after `COVERAGE_LABELS` (lines 36-53). B-2: `errorText(error, fallback)` helper (lines 116-121); per-item generation failure sets `request.error = errorText(error, "单项生成失败，可重试")` (line 1077). C-4: `renderShell(message, allowRecovery)` + `recoveryZone` (`trust-reply-item-actions` + `button secondary` + `data-action="reset-workbench-state"`, gated on `allowRecovery && !state.readOnly`, line 2001); bootstrap catch passes `true` (line 731); `resetWorkbenchState()` handler (lines 866-878: readOnly guard, confirm text「重置会清空本封信已锁定的全部回答，且不可撤销。确认继续？」, `POST /state/reset`, `state.savedStateVersion = 0`, `state.requests = []`, `await bootstrap()`, failure → `renderShell(..., true)`); onClick dispatch `if (action === "reset-workbench-state") void resetWorkbenchState();` (line 2333). `errorFromStream`/`errorFromResponse`/`isStaleError`/`isFrameStaleError` bodies zero-diff. |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt` | +3 tests (D-1) capturing SSE events via reflective `ResponseBodyEmitter.initialize` + `java.lang.reflect.Proxy` handler (`SseCapture` helper, package-private Handler workaround): business code surfaces `TRUST_REPLY_EVIDENCE_STALE`; unknown exception → `AI_REPLY_GENERATION_FAILED` + payload contains no `xyz`/class name; cancellation emits `cancelled`, never `error`. |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | +3 tests (D-2): `resetState` calls `deleteBySource` and never `delete(...)`; `resetState` never resolves source (repo stub throws, still succeeds, `findById` never called); `deleteState` regression still calls `stateStore.delete(..., expectedStateVersion)`. |
| 9 | `src/test/js/trustReplyWorkbench.test.js` | +3 tests (D-3): SSE error `{code, message}` renders mapped Chinese (not raw message); bootstrap-failure shell offers `data-action="reset-workbench-state"` with no inline `style=`; successful bootstrap never renders the reset button. |

## Per-invariant evidence

- **I-1** (payload `{generationId, code, message}`; known business code only; fixed generic message; no leak):
  - `AiReplyGenerationCoordinator.kt:82-87`, `UnmatchedInboundMailController.kt:530-535` — both `mapOf("generationId" ..., "code" to code, "message" to "AI generation failed")`.
  - Acceptance grep: `grep -rn "AI generation failed" src/main` → exactly these 2 sites, both mapOfs contain `"code" to`.
  - D-1 `business exception surfaces its code in the error event` → payload `code == "TRUST_REPLY_EVIDENCE_STALE"`, keys `{generationId, code, message}`.
  - D-1 `unknown exception surfaces the fixed code and no exception text` → `code == "AI_REPLY_GENERATION_FAILED"`, serialized payload contains no `xyz` / `IllegalStateException` / `db password`.
- **I-2** (both catches bind the exception and log WARN with generationId + code + Throwable):
  - Coordinator: `logger.warn(...)` at :85 (declaration :30); `catch (_: Exception)` remaining in coordinator = **1** (`sendLocked` :304, sanctioned). Cancellation catch (:80-81) untouched, `RejectedExecutionException` (:92) untouched.
  - Mail controller: `logger.warn(...)` at :533 (declaration :87). Its own pre-existing `catch (_: Exception)` at :756 (GenerationControl client-disconnect path, identical role to the sanctioned coordinator `sendLocked` catch; present at HEAD, unchanged — see Deviations #4).
- **I-3** (code→中文 at render time; parse functions untouched):
  - `git diff HEAD -- src/main/resources/static/trust-reply-workbench.js | grep -cE "errorFromStream|errorFromResponse|isStaleError|isFrameStaleError"` → **0**.
  - `WORKBENCH_ERROR_TEXT` (:36-53) consulted by `errorText` (:116-121), used at per-item failure `request.error = errorText(...)` (:1077); fallback order 中文 > `error.message` > fallback string.
  - D-3 `error event code renders the mapped chinese text` → renders「AI 未能产出可用的回答，请重试或换一种处理方式。」, not `AI generation failed` (uses code `TRUST_REPLY_ITEM_GENERATION_FAILED`, see Deviations #1).
- **I-4** (reset only in bootstrap-failure UI + second confirmation + independent server entry):
  - `renderShell(` call sites: :717 loading (1-arg), :731 bootstrap catch (2-arg `true`), :877 resetWorkbenchState failure (2-arg `true`), :2498 initial mount (1-arg), def :2000. Recovery zone emitted only when `allowRecovery && !state.readOnly` (:2001).
  - Confirm text at :867:「重置会清空本封信已锁定的全部回答，且不可撤销。确认继续？」.
  - Independent endpoint: `POST /state/reset` (controller:114-115) + `TrustReplyResetStateHttpRequest` with `source` only (controller:321-323); `grep expectedStateVersion` in controller → hits only at :112 (deleteState), :200/:318/:327 (existing save/delete requests) — none in the reset endpoint/body.
  - D-3 `bootstrap failure shell offers the reset button` / `successful bootstrap never renders the reset button` pass.
- **I-5** (scope = one row; idempotent; never TRUST_REPLY_STATE_CONFLICT; no other tables):
  - `deleteBySource` SQL touches only `trust_reply_workbench_state`, `WHERE` only `source_type`/`source_id`; body contains no `pruneExpired`/`throwStateConflict` (state store:101-113).
  - `resetState` never calls `resolveSource` (service:593-604).
  - D-2 `resetState deletes the row by source without a version` (verifies `deleteBySource` called, `delete(...)` never) and `resetState never resolves the source` (repository throws but reset succeeds, `findById` never called) pass.
- **I-6** (full `bootstrap()` without `preserveVersions`, savedStateVersion zeroed, no pre-failure state reuse):
  - `resetWorkbenchState` (:868-878): `state.savedStateVersion = 0; state.requests = []; await bootstrap();` — `bootstrap()` bare call (no `preserveVersions`), so `requestFactSelections` = null → server auto-match fallback.
- **S-1**: `git diff HEAD -- src/main/resources/static/styles.css` empty; recovery DOM verbatim contract (`trust-reply-item-actions` + `button secondary` + `data-action="reset-workbench-state"`); `grep -c 'style="' trust-reply-workbench.js` still **1** (pre-existing progress bar).
- **S-2**: item-error diff is a pure string change (`request.error = errorText(error, ...)`); no new class or `data-role`.
- **IP-4**: `grep -n "renderShell("` → 5 matches (1 def :2000 + 4 calls :717/:731/:877/:2498); truthy recovery only at :731 and :877 (see Deviations #3).

## Commands (final implementation state, all run fresh)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='AiReplyGenerationCoordinatorTest#business exception surfaces its code in the error event'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... -Dtest='AiReplyGenerationCoordinatorTest#unknown exception surfaces the fixed code and no exception text'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... -Dtest='AiReplyGenerationCoordinatorTest#cancellation still emits cancelled and never error'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... -Dtest='TrustReplyWorkbenchServiceTest#resetState deletes the row by source without a version'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... -Dtest='TrustReplyWorkbenchServiceTest#resetState never resolves the source'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... -Dtest='TrustReplyWorkbenchServiceTest#deleteState still enforces the expected version'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... mvn test -Dtest=AiReplyGenerationCoordinatorTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchControllerTest` | 0 | Tests run: 88, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `... mvn test` (full suite incl. exec-plugin JS run) | 0 | Tests run: 2636, Failures: 0, Errors: 0, Skipped: 4 — BUILD SUCCESS |
| `... mvn test -Dtest=OperatorStatusWriteSeamGuardTest` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `node --test src/test/js/trustReplyWorkbench.test.js` | 0 | tests 19, pass 19, fail 0 |
| `node --test src/test/js/*.test.js` | 0 | tests 673, pass 673, fail 0 |
| `node --check src/main/resources/static/app.js` | 0 | silent |
| `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | silent |
| `git diff --check` | 0 | silent |

## Scope check (A-9)

`git diff --name-only 66e1036d5e5d9d33f2b59655f20063ed90fa9015..HEAD`:

```
docs/plans/2026-08-20/00-execution-order.md
docs/plans/2026-08-20/P0-sse-error-code-and-state-reset.md
docs/plans/2026-08-20/P1-fact-binding-drop-not-fatal.md
docs/plans/2026-08-20/P2a-bound-vs-evidence-split.md
docs/plans/2026-08-20/P2b-bound-facts-into-prompt.md
src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
src/main/resources/static/trust-reply-workbench.js
src/test/js/trustReplyWorkbench.test.js
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt
```

- The 5 `docs/plans/2026-08-20/*` files come from the pre-existing plan-seed commit `15dbf44` (in the base..HEAD range by design, per brief note). The implementation commit `8ea1e24` itself contains **exactly the 9 authorized paths** (`git show --stat 8ea1e24`: 9 files, 386+/15−).
- Forbidden files check: `git diff --name-only base..HEAD | grep -cE "styles.css|GlobalExceptionHandler.kt|app.js"` → **0**.

## Deviations (all forced by unmodifiable repo constraints; documented for verify-p)

1. **D-3 test 1 uses code `TRUST_REPLY_ITEM_GENERATION_FAILED` instead of the plan's stated `TRUST_REPLY_EVIDENCE_STALE`.** `TRUST_REPLY_EVIDENCE_STALE` is intercepted by the pre-existing `isStaleError()` (which the plan mandates not to touch and which IP-1 acknowledges will now "命中"), so that code never reaches the item-error `request.error` render. The pre-existing shared-mount test `src/test/js/trustReplyWorkbenchSharedMount.test.js:1374` (NOT authorized) asserts the stale path keeps `error.message` (`/TRUST_REPLY_SOURCE_STALE|来源或事实已变化/`), which forced B-2 to be applied strictly to the `request.error` assignment only. The D-3 test's name and assertion semantics (mapped Chinese rendered, raw `AI generation failed` absent) are preserved.
2. **Mail controller catch is compact and uses fully-qualified names (no new imports) to keep a net-zero line diff (6+/6−).** `OperatorStatusWriteSeamGuardTest` pins `EXCLUDED_NOISE_SITES` line numbers 203/1099 in `UnmatchedInboundMailController.kt` and self-checks that every exclusion still exactly hits; any added line fails the full suite. The transformation is behaviorally identical (same `code` resolution, WARN log with generationId/code/Throwable, same `{generationId, code, message}` payload).
3. **`renderShell(` grep count is 5 (1 definition + 4 calls), not the plan's predicted "4 行".** Plan counted 3 existing calls + 1 new call in `resetWorkbenchState` but the definition line also matches the pattern. Constraint honored: only the bootstrap catch (:731) and `resetWorkbenchState` failure (:877) pass a truthy recovery flag; :717 and :2498 stay single-arg.
4. **Mail controller retains a pre-existing `catch (_: Exception)` at :756** (its private `GenerationControl` client-disconnect path — the exact analog of the coordinator's sanctioned `sendLocked` catch). It exists at HEAD and is untouched; the plan's I-2 acceptance "UnmatchedInboundMailController 只剩 0 处" was based on an audit that did not cover the mail controller's own `GenerationControl`.
5. **Coordinator catch retains the I-1/I-2 comments while the mail controller's does not** (line-count constraint above); the two catch bodies are otherwise identical in structure, payload and logging.

## Iteration notes (all resolved in final state; commands re-run fresh after final state)

- A truncated read-paste briefly corrupted one `renderShell` line with a literal `…`; restored from `git show HEAD` and verified with `node --check` + full JS suite.
- Initial SSE capture used the package-private `ResponseBodyEmitter.initialize(Handler)` directly; Kotlin compiler rejected it, so capture now uses `java.lang.reflect.Proxy` + `getDeclaredMethod("initialize")` (`SseCapture` in the test). Event-name prefix parsed at the first newline (`event:<name>\ndata:` is flushed as one String entry by `SseEventBuilderImpl`).
- First full `mvn test` run failed on `OperatorStatusWriteSeamGuardTest` (mail-controller line shift 203→207 / 1099→1103); fixed via the net-zero diff described in Deviation #2. Full suite re-run green: 2636, 0/0.

## Freshness

- Plan identity rechecked: YES (SHA-256 unchanged `68f80155...`)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged; HEAD now `8ea1e24`)
- Reported commit reachable from target branch: YES (`8ea1e24` is HEAD of `fast/2026-08-20-execution-order`)
- Required commands run this invocation: YES (all after final implementation state)
- Historical evidence used only as baseline: YES

## Remaining blocker

- None.

## Next action

- READY_FOR_VERIFICATION → run `verify-p` for plan `P0-sse-error-code-and-state-reset.md` at commit `8ea1e241b5703e967da9861847663e67e5eb3bdc`.
