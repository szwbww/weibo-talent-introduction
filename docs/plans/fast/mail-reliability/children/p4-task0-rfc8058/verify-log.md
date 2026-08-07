# Verification Log — p4-task0-rfc8058

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: p4-task0-rfc8058 (plan authority: `docs/plans/2026-08-06/material-reminder-02-headers-personalization.md`, 阶段 0 任务 0 J-7 + 阶段 D 任务 8 J-7 用例 only)
Boundary: 92a678b..f2916674 (single child commit `f291667 feat(fast-p): implement p4-task0-rfc8058`; parent of f2916674 == 92a678b, verified)
Verifier: VerP4Task0

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | `git diff --stat 92a678b..f2916674` = 2 files, +26 -2, only `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` (1 line: `List=One-Click` → `List-Unsubscribe=One-Click`) and `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` (+25 -1: one expected-value fixup in existing test + 1 new J-7 test case). `git show f2916674 --name-only` = exactly those 2 files. No migration, no other files, no frontend/knowledge files. Working tree clean of staged/unstaged changes (only untracked `docs/plans/fast/mail-reliability/`, the report area). |
| 2. Plan and invariants | PASS | Service line reads verbatim `message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")` (J-7). New test `list unsubscribe post header value is exactly RFC 8058 postarg` asserts EQUALITY, not contains: `assertEquals("List-Unsubscribe=One-Click", message.getHeader("List-Unsubscribe-Post").single())`; same case asserts `List-Unsubscribe` verbatim as `"<${enabledTokenService.unsubscribeUrl(mail.to)}>, <mailto:test@example.com?subject=unsubscribe>"` (https then mailto, order preserved). Service diff touches only the List-Unsubscribe-Post line — `:49-52` and the `List-Unsubscribe` addHeader line byte-unchanged. Grep across both files: zero matches for `List=One-Click`, `mailType`, `MATERIAL_REMINDER`, `Gmail`/`gmail` (no mailType logic; no Gmail-button criterion). `git diff 92a678b..f2916674` shows no other hunks. |
| 3. Required commands | PASS | (JDK 11, cwd = worktree) `mvn test -Dtest=SmtpMailDeliveryServiceTest`: exit 0, `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` (surefire report, 16 existing + 1 new). Full `mvn test`: exit 0; surefire totals across 172 report files = tests 2159, failures 0, errors 0, skipped 4 (matches implementer claim); JS suite `tests 459, pass 459, fail 0` (matches baseline d911bd6). `git diff --check`: silent, exit 0. |
| 4. Downstream interfaces | N/A | M-1: P1/P2/P3 declare zero changes to `SmtpMailDeliveryService.kt`; no downstream interface changes required by this child. Note recorded, nothing to verify. |

### AUTO_FIX
N/A

### RECORD_ONLY
- Existing test `send adds List-Unsubscribe headers when token service enabled` expected-value fixup: `assertEquals("List=One-Click", …)` → `assertEquals("List-Unsubscribe=One-Click", …)` in `SmtpMailDeliveryServiceTest.kt:148`. Judged CONSISTENT with J-7 — the old literal was the plan-defect value (J-7 修正记录: `unsubscribe-suppression-02` wrote the wrong value; code was its faithful implementation), and the brief explicitly authorizes the forced fixup. Test name/condition/semantics unchanged; the assertion now matches the mandated RFC 8058 §5 ABNF value.

### Required Action: COMPLETE_CHILD
