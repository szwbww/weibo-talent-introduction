# Child 02b Verification Log — unsubscribe-closure fast-p run

## Light Verification: LIGHT_PASS
Child: 02b and plan path docs/plans/2026-08-11/unsubscribe-02b-mailto-channel.md
Boundary: f09f8c314951279aaabd025d31d4e045d2928aa6..cfe8936c2dcf049672ebaca036430aeabcc1cc7d
Verifier: Verify02b

### Four Gates

|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-only f09f8c3..cfe8936` lists 8 paths: 4 fast-p evidence docs under `docs/plans/fast/unsubscribe-closure/children/02/` (controller evidence commit `c295cbc docs(fast-p): record 02 light verification`, fast-p infrastructure, not implementer scope — same pattern accepted for sibling 02) plus 4 product files. The implementation commit `cfe8936 feat(fast-p): implement 02b` alone (`git diff cfe8936^ cfe8936`, +81 −13) touches exactly the plan's 变更文件清单 4 files: `EmailSuppressionService.kt`, `AutoMailReplyService.kt`, `EmailSuppressionServiceTest.kt`, `AutoMailReplyServiceTest.kt`. |
|Plan and invariants|PASS|I-1: `EmailSuppressionService.kt:82-85` `subjectRequestsUnsubscribe` uses set membership `s in SUBJECT_UNSUBSCRIBE_PHRASES` after `trim()` + `lowercase(Locale.ROOT)` — exact equality, no `contains` on subject; set at `:109` = `setOf("unsubscribe", "退订", "取消订阅")`. Test `detectUnsubscribeSource rejects subject that merely contains the phrase` covers the plan's 3 subjects (`Re: unsubscribe policy question`, `Question about unsubscribe`, `关于退订的问题`, bodies phrase-free → `null`). I-2: `detectUnsubscribeSource` (`:88-92`) subject-first → `MAILTO`, else body → `INBOUND_REPLY`, else `null`; `grep -rn "SuppressionSource.MAILTO" src/main/kotlin` → 2 hits (`EmailSuppressionService.kt:89`, `AutoMailReplyService.kt:842`), MAILTO no longer a dead enum; test `prefers subject over body` (subject exact + body phrase → MAILTO). I-3: `grep -n "captureUnsubscribeIfPresent" AutoMailReplyService.kt` → 4 hits (3 calls `:138` `:197` `:310` + def `:837`); all 3 call sites pass `received.subject` as 2nd arg. What-must-NOT-change: `UNSUBSCRIBE_PHRASES` 9-item list (`:96-105`) byte-identical to base (diff of the two revisions contains zero changed phrase tokens); `looksLikeUnsubscribe` (`:74`) delegates to `containsUnsubscribePhrase` with the old body verbatim — public behavior unchanged, `looksLikeUnsubscribe detects unsubscribe phrases` test preserved (test diffs are pure additions, +36/+16, 0 deletions); `InboundIntentClassifier` zero diff; `RecipientSuppressedException` zero diff, still at file end `:122-126`; `suppress` idempotency untouched. Tests: 5 new `EmailSuppressionServiceTest` cases (MAILTO exact subject / prefers subject / falls back to body / rejects contains-only / neither matches) and 1 new `AutoMailReplyServiceTest` case `mailto unsubscribe mail with empty body is suppressed with MAILTO source` (subject `unsubscribe`, body `""`, asserts `suppress("expert@example.com", MAILTO, "mailto unsubscribe")` once) — all assert the plan's acceptance items. |
|Required commands|PASS|All run fresh by this verifier in the worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` (zulu 11.0.15 confirmed): `mvn test -Dtest=EmailSuppressionServiceTest,AutoMailReplyServiceTest` → exit 0, surefire `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0` (41 AutoMailReplyServiceTest + 16 EmailSuppressionServiceTest), BUILD SUCCESS; `mvn test` (full regression) → exit 0, `Tests run: 2296, Failures: 0, Errors: 0, Skipped: 4`, BUILD SUCCESS — baseline 2290 + 6 new tests, no baseline failures; `mvn clean package` → exit 0, `Tests run: 2296, Failures: 0, Errors: 0, Skipped: 4`, BUILD SUCCESS; `git diff --check` → exit 0 (clean). All counts match the execution report. |
|Downstream interfaces|N/A|02b is the last child in this run — no later child consumes its API. `docs/plans/fast/unsubscribe-closure/children/` contains only 01, 02, 02b; brief states "No later children in this run". No downstream interface to verify. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (test-isolation pattern, not a defect): The new `AutoMailReplyServiceTest` case stubs `emailSuppressionService.detectUnsubscribeSource("unsubscribe", "")` to return `MAILTO`, so the wiring assertion (suppress called once with `MAILTO`/"mailto unsubscribe") does not itself exercise the real subject→MAILTO detection — that logic is verified in `EmailSuppressionServiceTest` (5 new cases). Execution report documents an initial Mockito-2 `Mockito.any()` NPE in this case, fixed by dropping the redundant matcher-based verify; the exact-arg verify still asserts exactly-once MAILTO suppression, satisfying the plan's acceptance. No gate violation.

### Required Action
- COMPLETE_CHILD
