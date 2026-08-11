# Child 02b Execution Report — mailto 退订通道生效

- Plan: docs/plans/2026-08-11/unsubscribe-02b-mailto-channel.md
- Plan SHA-256: 39b7a52c84c8c865fb3dc4f662b424a5bf862f7325b41cd8d589702fdc6647d2
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure
- Branch: fast/unsubscribe-closure
- Implementation commit: `cfe8936` (feat(fast-p): implement 02b) — exactly the 4 authorized files, +81/−13
- Base (child 02 code head): f09f8c314951279aaabd025d31d4e045d2928aa6
- Agent identity: Impl02b (task subagent of fast-p controller)
- Result: READY_FOR_VERIFICATION

## Changes

1. `EmailSuppressionService.kt` — extracted `containsUnsubscribePhrase(text)` verbatim from `looksLikeUnsubscribe`; `looksLikeUnsubscribe(body)` kept as public entry delegating to it (behavior unchanged, `UNSUBSCRIBE_PHRASES` 9-item list untouched); added `private subjectRequestsUnsubscribe` (exact-equality `in SUBJECT_UNSUBSCRIBE_PHRASES = setOf("unsubscribe", "退订", "取消订阅")` after trim + lowercase ROOT) and `detectUnsubscribeSource(subject, body)` (subject-first → `MAILTO`, else body → `INBOUND_REPLY`, else `null`). Top-level `RecipientSuppressedException` untouched (still at file end).
2. `AutoMailReplyService.kt` — `captureUnsubscribeIfPresent(senderEmail, subject, cleanedBody)` now uses `detectUnsubscribeSource` and suppresses with `MAILTO`/"mailto unsubscribe" or `INBOUND_REPLY`/"inbound reply unsubscribe"; all 3 call sites (lines 138, 197, 310) pass `received.subject`.
3. `EmailSuppressionServiceTest.kt` — 5 new cases: `detectUnsubscribeSource returns MAILTO for exact unsubscribe subject`, `detectUnsubscribeSource prefers subject over body`, `detectUnsubscribeSource falls back to body with INBOUND_REPLY`, `detectUnsubscribeSource rejects subject that merely contains the phrase`, `detectUnsubscribeSource returns null when neither matches`. Existing `looksLikeUnsubscribe detects unsubscribe phrases` preserved unchanged.
4. `AutoMailReplyServiceTest.kt` — new case `mailto unsubscribe mail with empty body is suppressed with MAILTO source` (subject "unsubscribe", empty body → `suppress("expert@example.com", MAILTO, "mailto unsubscribe")` once).

## Invariant verification (grep)

- I-1: `grep -n "SUBJECT_UNSUBSCRIBE_PHRASES"` shows `s in SUBJECT_UNSUBSCRIBE_PHRASES` (set equality, no `contains`) at EmailSuppressionService.kt:84; set = `setOf("unsubscribe", "退订", "取消订阅")` at :109.
- I-2: `grep -rn "SuppressionSource.MAILTO" src/main/kotlin` → 2 hits (EmailSuppressionService.kt:89, AutoMailReplyService.kt:842) — MAILTO no longer a dead enum.
- I-3: `grep -n "captureUnsubscribeIfPresent"` → 4 hits (3 calls + 1 def); all 3 call sites contain `received.subject`.

## Commands (all run in worktree with JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)

| Command | Exit code | Result |
|---|---|---|
| `mvn test -Dtest=EmailSuppressionServiceTest,AutoMailReplyServiceTest` | 0 | Tests run: 57, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| `mvn test` (full regression) | 0 | Tests run: 2296, Failures: 0, Errors: 0, Skipped: 4 — BUILD SUCCESS |
| `mvn clean package` | 0 | BUILD SUCCESS (full suite re-run inside) |
| `git diff --check` | 0 | clean |

Focused-class runs individually equivalent to the combined run above (both classes, 57 tests total: 41 AutoMailReplyServiceTest + 16 EmailSuppressionServiceTest).

Test count delta vs baseline 2290: +5 EmailSuppressionServiceTest + 1 AutoMailReplyServiceTest = 2296.

## Deviations

- None. Plan implemented verbatim; no files outside the authorized 4 touched. `docs/plans/fast/` left untracked/uncommitted (controller evidence).

## Notes

- Initial run of the new AutoMailReplyServiceTest case failed on `Mockito.any()` returning null under Mockito 2 (Kotlin non-null parameter NPE, cascading Mockito state corruption into 2 unrelated tests). Fixed by removing the redundant matcher-based verify — the exact-arg verify already asserts exactly-once `MAILTO` suppression. Re-run: 0 failures/errors.
