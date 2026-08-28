## Light Verification: LIGHT_PASS_WITH_NOTES
Child: c1 · plan docs/plans/2026-08-28/11-fact-supply.md
Boundary: 5a90e3e53e5fe8b40059b3090f086d6b36a09a01..97e414658b1fe9196271f607cf763853c04d5098
Verifier: C1Ver

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized files only | PASS | `git diff --stat 5a90e3e..97e4146` → exactly the 3 authorized files: `src/main/resources/db/migration/V109__qa_fact_supply_and_controlled_key_repair.sql` (new, +148), `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaFactSupplyMigrationTest.kt` (new, +126), `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` (+62). `git diff --stat ... -- src/main/kotlin` empty (zero production Kotlin changes). HEAD=97e4146, branch `fast/2026-08-28-reply-orchestration-order`; only untracked path is `docs/plans/fast/**` (fast-p evidence, committed by controller separately — expected). |
| 2. Requirements & invariants (T-1/T-2/T-3, I-1..I-6) | PASS | T-1: plan's T-1 SQL fence vs committed V109 — byte-for-byte identical (diff empty after trailing-newline strip); 2 UPDATEs + 5 INSERTs exactly as written. T-2: all 7 assertions present in `QaFactSupplyMigrationTest` (frozen-id guard ×2 w/ literal count 2; WHERE NOT EXISTS ×5; `updated_at = updated_at` ×2; exact `AND coverage_keys = 'confidentiality.materials'` guard; 4 IP keywords × 2 NOT LIKE = 8; no `reply_body =`/`answer_body =`; 5 coverage literals + controlled-group exclusion). T-3: 4th test `every intent reachable coverage key is owned by at least one migration seeded rule` present; `knownUnownedKeys = setOf("publication.authorship")` with stated reason. I-1: `grep -F -c "AND id NOT IN (1, 3, 21, 24)"` == 2, `grep -c "^UPDATE qa_rule"` == 2, both statement bodies carry guard. I-2: all 5 rules use only intent-referenced keys (`enterprise.project_types`, `finance.compensation_structure`, `role.deliverables`, `confidentiality.research`, `work.time_commitment,work.advisory_duration`); 4th parity test enforces ownership side. I-3: assertion 7 — none of the 5 literals equals a controlled group. I-4: repair sets `coverage_keys = ''` with exact guard (assertion 4). I-5: keyword-only UPDATE on `Pre-contract IP boundary`, no body assignment anywhere (assertion 6). I-6: V109 uses only category codes {COMMUNICATION_AND_OTHER, FUNDING_AND_TIMELINE, ROLE_AND_WORKSTYLE, TRUST_AND_COMPLIANCE} ⊆ the 7 existing (V38:5-11, V41:7). G-3: `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject =` ×5, `updated_at = updated_at` ×2. No priority/body/catalog changes. |
| 3. Required commands fresh vs baseline | PASS | All with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Focused: `mvn test -Dtest=QaFactSupplyMigrationTest,QaCoverageKeyIntentParityTest` exit 0; surefire `QaFactSupplyMigrationTest` 7/0/0/0, `QaCoverageKeyIntentParityTest` 6/0/0/0. Full: `mvn test` → BUILD SUCCESS, `Tests run: 2960, Failures: 0, Errors: 0, Skipped: 5` (baseline 2952 + 8 new tests = 2960; Skipped 5 unchanged). Build: `mvn clean package` → BUILD SUCCESS, `Tests run: 2960, Failures: 0, Errors: 0, Skipped: 5`. Hygiene: `git diff --check 5a90e3e..97e4146` exit 0. Node suite also ran during focused `mvn test`: 755 pass (baseline 755). |
| 4. Downstream interfaces | PASS | V109 version allocation correct: max prior migration V108, exactly one V109 file, no V110 present (reserved for plan 17, not part of this run). c2 (12-letter-closer) consumes only migration-level data (5 new facts + deadlock repair + IP keywords) — no code interface required. |

### AUTO_FIX (F-ids or N/A)
N/A — no four-gate violation found.

### RECORD_ONLY (O-ids or N/A)
- O-1 (informational): plan T-3 text states `QaCoverageKeyIntentParityTest` "已有 3 个测试"; at seed commit 5a90e3e the file already contained 5 tests (the 3 described plus 2 added by other plans, e.g. `normalizeAndValidate keeps existing key order...`). c1's diff adds exactly the planned 4th test + `knownUnownedKeys` + extraction helper — no deviation from the plan's change list. No action needed.

### Required Action
- COMPLETE_CHILD
