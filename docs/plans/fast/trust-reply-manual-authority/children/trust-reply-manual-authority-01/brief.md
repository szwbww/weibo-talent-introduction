# Fast-P Child Brief — trust-reply-manual-authority-01

- Plan (exact approved contract): `docs/plans/2026-08-24/01-mail-request-extraction-correctness.md` (approved identity `commit:8dc7c96` in branch `fast/trust-reply-manual-authority`)
- Master plan: `docs/plans/2026-08-24/00-trust-reply-manual-authority-master.md`
- Depends on: none. Execution order: this is child 1 of 4; the worktree is at master base 99cef49 + plan-seeding commit.
- Base: `99cef49a37f79b409504e89cd5cd942370966c39`

## Scope and master constraints (apply to this child)

- Master I-1: 01 must complete and verify before 02 starts. Do NOT implement any 02 fact-matrix semantics (no new fact fields, no intentMatched/intentMismatch splits).
- Master I-6: auto/legacy/null-assembly paths must keep current strict matching and degraded-warning behavior.
- Master I-7: modify ONLY the files in the child plan's `## 变更文件清单` (6 files). Any needed new file/field/API → stop and report PLAN_CONFLICT; do not extend scope.
- Master I-8: run the child's directed tests and `git diff --check`; record exact commands and exit codes.
- Master I-9: this is R1. On failure, stop; repair stays inside this child's authorized files.

## Authorized files (from plan 01)

| File | Change |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt` | tighten bullet marker regex to require whitespace after marker |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | fix MatchedIntentSpan coordinate comment |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | rebase local intent spans to absolute coords before claimed() |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt` | sanitized live fixture + marker boundary + offset regression |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | nonzero request offset claimed/unrecognized regression |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | saved-state STALE regression on requestKey drift |

No DB schema, API, frontend, CSS changes.

## Child-plan invariants (01 I-1..I-5)

- I-1: `ExtractedRequest.startOffset/endOffset` keep pointing into the original full text; CRLF/CR/soft-newline/order/dedup contracts unchanged.
- I-2: bullets only for explicit markers `- `, `* `, `• `, `1. `, `1) `; `*Name*`, `*Title*`, `-not a list` are NOT bullets; indented continuation rules unchanged.
- I-3: `matchIntentsWithSpans` returns ranges local to the passed requestText; before `claimed()` rebase by adding `RequestUnit.startOffset` to make absolute.
- I-4: ask enumeration stays a shadow signal — only `unrecognizedAsks`/`unrecognizedAskCount`/`enumeratorClaimed`/logs may change; status/intents/factRuleIds/sendQaRuleIds/promptRuleIds unchanged.
- I-5: old saved matrix that no longer maps to new request set → existing bootstrap fallback (default selection + `savedState.status=STALE`), never guess-rebind; explicit dirty matrix still `TRUST_REPLY_FACT_SELECTION_INVALID`.

## Required commands (must run fresh, record exit codes)

- `mvn -q -Dtest=QaRequestExtractorTest,QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest test` (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)
- `git diff --check`

## Downstream interfaces (later children depend on these)

- `QaFactSelectionService.kt` is shared with child 02 (matrix fact authority). 01 must deliver the absolute-coordinate claiming fix without introducing 02's fact fields.
- `QaFactSelectionServiceTest.kt` and `TrustReplyWorkbenchServiceTest.kt` are shared with 02; 02 must NOT delete 01's regression fixtures — keep them meaningful and passing.
- requestKey stability: 01 changes the extracted request set (5 signature bullets disappear) → old payloads must fall back to STALE per I-5.
- Do not modify `TrustReplyWorkbenchStateStore`, schema, or bootstrap semantics.

## Procedure

1. Read the exact plan file above; use `execute-p` against it.
2. Implement the three stages (bullet marker, absolute-coordinate claiming, STALE regression) with tests.
3. Run every required command; record command, exit code, counts in the execution report.
4. Commit locally ONLY your 6 authorized files as: `feat(fast-p): implement trust-reply-manual-authority-01`
5. Append the full result (stages, evidence, commands) to `docs/plans/fast/trust-reply-manual-authority/children/trust-reply-manual-authority-01/execution.md` (append-only; exclude this file from the implementation commit).
6. Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
7. Do not review later children, repair unrelated behavior, push, merge, amend, squash, or rewrite history. Skip formatters/linters and the full project test suite (the verifier/controller runs the combined gates later).
