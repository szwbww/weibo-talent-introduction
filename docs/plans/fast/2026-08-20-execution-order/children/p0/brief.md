# Fast-P Child Brief — P0

- Plan (authoritative contract): `docs/plans/2026-08-20/P0-sse-error-code-and-state-reset.md` (commit:15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8)
- Child base SHA: `66e1036d5e5d9d33f2b59655f20063ed90fa9015`
- Depends on: none
- Downstream consumers: P1 (`P1-fact-binding-drop-not-fatal.md`), P2a, P2b

## Global constraints (from `docs/plans/2026-08-20/00-execution-order.md`)

1. **Order authority**: `00-execution-order.md` wins on any conflict with this plan's prose.
2. **Symbol-first relocation**: plan line numbers were taken from the 07:34 UTC worktree and may be stale. Locate every change point by symbol/function name (`generateOperatorDirectedAnswer`, `validateLockedItem`, etc.), use line numbers only as cross-check. Symbol not found = real problem; line mismatch = expected drift.
3. **JDK 11 mandatory**: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` — bare `mvn` fails to build.
4. **Verification command authority**: full suite / build / JS tests / syntax checks / whitespace hygiene are defined ONLY in `00-execution-order.md` 「验证命令」. This plan's `## 验证命令` lists only this child's new/updated test classes and single-test runs.
5. **需求方已拍板决策** (do not re-litigate): manual binding never changes item status; outbound audit records only true evidence. Applies across the whole Line B.
6. **No out-of-scope work**: `GlobalExceptionHandler.kt`, `app.js` other SSE consumers, and `styles.css` are explicitly out of scope for this child. No new CSS classes, no inline styles, `styles.css` stays untouched.

## Authorized files (exactly 9 — nothing else may change)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` | add logger, change third catch, add one `const val` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | same-shape catch change |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | add `deleteBySource` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | add `resetState` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | add endpoint + request body data class |
| 6 | `src/main/resources/static/trust-reply-workbench.js` | error-code copy table, render by code, failure-screen reset button + handler |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt` | add 3 test cases (D-1) |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | add 3 test cases (D-2) |
| 9 | `src/test/js/trustReplyWorkbench.test.js` | add 3 test cases (D-3) |

## Invariants (exact text in plan §关键不变量; acceptance greps in §验收标准)

- **I-1** error event payload is exactly `{generationId, code, message}`; `TrustReplyWorkbenchException` → real code with fixed generic message; anything else → `code = "AI_REPLY_GENERATION_FAILED"` with current message; never leak `ex.message`, class name, stack.
- **I-2** both catches log WARN with `generationId`, resolved code, and the exception object (Throwable param). `catch (_: Exception)` must not remain (except the one sanctioned location in `sendLocked`).
- **I-3** `errorFromStream()` untouched (it already reads `data.code || data.errorCode`); add a code→中文 map consulted at render time; fallback order 中文 > code message > existing fallback. Do not break `isStaleError()`/`isFrameStaleError()`.
- **I-4** reset button appears ONLY in the bootstrap-failure UI; normal loaded workbench must NOT show it; second confirmation dialog wording must state it clears all locked answers for this letter.
- **I-5** `resetState` deletes `trust_reply_workbench_state` rows by `(source_type, source_id)` only; 0 or 1 rows deleted = success (idempotent); never throws `TRUST_REPLY_STATE_CONFLICT`; never touches `qa_rule`, `reply_snippet`, `mail_record`, `inbound_mail_processing`, or any ES index.
- **I-6** after reset, frontend runs a full `bootstrap()` WITHOUT `preserveVersions` and zeroes `state.savedStateVersion`; never renders pre-failure in-memory state.

Style contract S-1/S-2: reuse `class="button secondary"` (no new class, no inline style); item error copy is pure string replacement, no DOM/CSS change.

## Required commands (from this plan's `## 验证命令` + shared authority)

Run plan §验证命令 exactly: the three D-1 single tests, three D-2 single tests, D-3 `node --test src/test/js/trustReplyWorkbench.test.js`, plus the shared suite (full `mvn test`, `node --test src/test/js/*.test.js`, `node --check` on app.js and trust-reply-workbench.js, `git diff --check`) from `00-execution-order.md`. Pass criteria: Maven exit 0 with `Tests run: N, Failures: 0, Errors: 0`; node `# fail 0`; `node --check` silent; `git diff --check` silent.

## Scope check (plan A-9)

`git diff --name-only 66e1036d5e5d9d33f2b59655f20063ed90fa9015..HEAD` must output exactly the 9 authorized paths. `styles.css`, `GlobalExceptionHandler.kt`, `app.js` must NOT appear. (Use the base range because this worktree already contains the seeded plan commit.)

## Downstream interfaces (must be preserved exactly)

- P1 A-1 reads the 422 response body `code` field (`TRUST_REPLY_FACT_SELECTION_INVALID`) — existing contract, unchanged.
- P1 A-5 needs the bootstrap-failure reset button from P0 to clean up test state.
- Error payload shape `{generationId, code, message}` is consumed by P2a/P2b-era frontend.
- Success-path SSE events (`ready`/`progress`/`heartbeat`/`result`/`cancelled`) payloads and ordering must be byte-identical to before (plan What must NOT change #1).
- Cancellation semantics unchanged: `catch (_: AiReplyGenerationCancelledException)` still emits `cancelled`, no error log.
