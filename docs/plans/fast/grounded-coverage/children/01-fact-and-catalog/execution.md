# Execution Report — 01-fact-and-catalog (P1)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/01-fact-and-catalog.md`
- Plan SHA-256: `0d5ff0f7987b060e233e923aabf6d3b10c5b477c980409adf0c94675f50eb38e`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/01-fact-and-catalog.md@0d5ff0f7987b060e233e923aabf6d3b10c5b477c980409adf0c94675f50eb38e`
- Execution epoch: NEW
- Executor: `Impl01FactCatalog`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage`
- Target branch: `fast/grounded-coverage`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage@fast/grounded-coverage@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-grounded-coverage`
- Child product base: `af1723f37021328f8ffa61261504727e514fbb4b`
- Pre-execution HEAD (incl. controller evidence seed): `841c633030b6deb26b8e5c708144224d987ae3d6`
- Post-execution code SHA (implementation commit): `f5c09382744c0da8a537610af6145974ee1fcaf4`
- Implementation boundary: `af1723f..f5c0938` (only the 6 authorized files; exactly one commit)
- Commit subject: `feat(fast-p): implement 01-fact-and-catalog` (verified as HEAD of `fast/grounded-coverage`, ancestor of target branch)

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 阶段 A — catalog alignment (A-1/A-2/A-3/A-4) | IMPLEMENTED | `QaCoverageKeyCatalog.kt`, `AiReplyIntentCatalog.kt`, `AiReplyIntentCatalogTest.kt` | focused + full tests green; guard tests added |
| 阶段 B — V105 migration (B-1, I-2/I-4/I-5/I-6) | IMPLEMENTED | `V105__add_programme_identity_facts.sql` | `ProgrammeIdentityFactsMigrationTest` 6/6 green |
| 阶段 C — regression (C-1/C-2/C-3) | IMPLEMENTED | both test files + new migration test | focused suite green |
| Invariants I-1..I-8, N1..N6 | PRESERVED | see verification evidence below | greps + tests + diff scope |

## Changed Files (per plan 变更文件清单, exactly 6)

1. `src/main/resources/db/migration/V105__add_programme_identity_facts.sql` (new) — 4 statements:
   - S1: INSERT new fact "Programme name and public visibility", coverage `programme.name`, category `OVERVIEW`, keywords `official name,name of the scheme,what is it called`, `WHERE NOT EXISTS` guard. `reply_body`/`answer_body` = plan's verbatim body.
   - S2: INSERT new fact "Programme sponsorship and organising level", coverage `governance.sponsor_level`, category `TRUST_AND_COMPLIANCE`, keywords `government body,government institution,government agency,institution supporting,supporting body`, `WHERE NOT EXISTS` guard. `reply_body`/`answer_body` = plan's verbatim body.
   - S3: id=6 keyword append — CONCAT + three NOT LIKE guards (`form of collaboration`, `forms of collaboration`, `how the collaboration works`) + `updated_at = updated_at`.
   - S4: id=18 keyword append (IP-6 fix) — CONCAT + two NOT LIKE guards (`government body`, `institution supporting`) + `updated_at = updated_at`.
   - No `${` placeholder tokens; no keyword contains `programme`.
2. `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` (modify) — appended `programme.name` and `governance.sponsor_level` Entries at the END of the catalog list (after `confidentiality.research`), per plan A-1 note (normalizeAndValidate serializes in declaration order). Existing 29 keys untouched (N5).
3. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` (modify) — appended three `RequestIntentDefinition`s at the end of `definitions` (`programme.name`, `governance.sponsor`, `collaboration.form`) with the plan's exact keys/titles/aliases/coverage, `requiresProfile` left at default false (I-3/N2); appended one `IntentGroupTitle` (`programme.name`,`governance.sponsor` → "Programme identity and sponsorship") per A-3. Existing 20 definitions untouched (N1).
4. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` (modify) — C-1: verbatim-letter exact-5-intent test, no-`general.answer` assertion, 3 existing-fixture verbatim regression (N1), A-4 bidirectional coverage-key guard (with documented exemptions), I-2 mechanical parity test (letter-side `normalize` substring + production `scoreRuleIntentAlignment` > 0).
5. `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` (modify) — C-2: letter → 5 intents all SUPPORTED / `factRuleIds` = [1001,1002,6,8,34] / GROUNDED / N3 evidence checks (finance.arrangements → [8], ip.arrangements → [34]); I-6 negative case (`Which partner company…` never binds id=6).
6. `src/test/kotlin/com/weibo/talentintroduction/qa/service/ProgrammeIdentityFactsMigrationTest.kt` (new) — C-3 text assertions: exactly 2 `WHERE NOT EXISTS` guards, `updated_at = updated_at`, NOT LIKE guards for id=6's three words and id=18's two words, no `${`, coverage literals `programme.name` / `governance.sponsor_level`.

## Commands (all run freshly, JDK11 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`)

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest='AiReplyIntentCatalogTest,QaFactSelectionServiceTest,ProgrammeIdentityFactsMigrationTest'` | PASS | exit 0; surefire: AiReplyIntentCatalogTest tests=30 errors=0 failures=0; QaFactSelectionServiceTest tests=35 errors=0 failures=0; ProgrammeIdentityFactsMigrationTest tests=6 errors=0 failures=0; BUILD SUCCESS |
| `mvn test` (full regression) | PASS | exit 0; `Tests run: 2563, Failures: 0, Errors: 0, Skipped: 4` (4 skipped = Docker-gated migration ITs, design-default); frontend node suites pass; BUILD SUCCESS |
| `git diff --check` | PASS | exit 0 |

## Verification Evidence (invariants)

- I-1: `grep -c "verification_evidence" src/main/kotlin/.../llm/service/AiReplyIntentCatalog.kt` = 1 (was 0); A-4 bidirectional guard tests green.
- I-2: mechanical parity test green (letter-side + alias-side for `official name` / `government body` / `form of collaboration`); keyword lines in V105 contain 0 occurrences of `programme` (verified per-line; raw `grep -c 'programme' V105` = 8, see deviation D-3).
- I-3: `grep -n "requiresProfile = true"` → single hit at line 116 (`expertise.programme_fit`), unchanged from before.
- I-4: migration test asserts coverage literals inline.
- I-5: migration test asserts NOT EXISTS × 2, `updated_at = updated_at`, NOT LIKE guards.
- I-6: negative test green (id=6 never in `factRuleIds` for partner-company ask).
- I-7: both test files carry the verbatim plan-header letter, character-for-character (copied from plan raw bytes).
- I-8: `TrustReplyWorkbenchService` NOT modified (file not in authorized list; `git show --stat` of implementation commit shows no such file; STALE path untouched).
- N6: `git status --porcelain src/main/resources/db/migration` → only `?? src/main/resources/db/migration/V105__add_programme_identity_facts.sql`.
- N1/N3/N5: full `mvn test` green; definitions/catalog existing entries byte-identical (diff limited to appended lines).
- Plan identity rechecked after execution: unchanged (`0d5ff0…38e`). Worktree identity rechecked before commit: unchanged. Commit `f5c0938` verified as HEAD of `fast/grounded-coverage` and ancestor of target branch.

## Deviations

- **D-1 (A-4 exemption set)**: direction-2 guard exemption constant additionally contains `application.timeline`. Factual state: `application.timeline` exists in the catalog but is referenced by NO definition's required/alternative list (only via `matchIntents`' runtime copy for next-stages+timing asks, `AiReplyIntentCatalog.kt:342`), making it an O4-class orphan like the three plan-listed exemptions. The plan's literal exemption set (`general.answer`, `application.required_materials`, `work.relocation`) would make the mandated guard fail on current facts. Resolved per the plan's own mechanism ("豁免集必须写成显式常量并附注释"), with a comment documenting the reason; removing the exemption later turns the test red.
- **D-2 (C-2 Funding support keywords)**: the letter test's Funding support rule carries keyword `remuneration` alongside the real migrated keywords. The real Funding support keywords (`salary,subsidy,funding,compensation,advisory role compensated,is the advisory role compensated`) contain no substring of the verbatim letter, yet the plan mandates all 5 intents SUPPORTED with Funding support among `factRuleIds` on this letter (N3). `remuneration` is the letter's finance phrasing (and a `finance.arrangements` alias); constructing scenario-tailored rule keywords matches the existing convention in this test file.
- **D-3 (I-2 raw-grep acceptance)**: plan acceptance "grep -c 'programme' V105__*.sql 结果为 0" is unsatisfiable against the plan's own mandated text — `reply_subject 'Programme name and public visibility'` and the verbatim S1 body contain `programme`. Raw count = 8, all from subject/body; the actual I-2 constraint (no rule keyword contains `programme`) holds (0 in keyword lines) and is enforced by the mechanical parity test. Flagged for the verifier.
- **D-4 (C-2 pool reading)**: "三条新规则 + id=6" interpreted as the 2 new facts + id=6 (post-V105 keywords) + `Funding support` + `Pre-contract IP boundary` = 5-rule pool, matching the observable outcome's 5 bound facts and the `factRuleIds` list (新事实①②、id=6、Funding support、Pre-contract IP boundary). id=18 is deliberately excluded from this pool (its binding is exercised by the plan's A-2 acceptance, not by this letter scenario).
- **D-5**: plan's S1/S2 `<正文见下>` / `<同 reply_body>` placeholders replaced with the plan's verbatim bodies (`reply_body` = `answer_body`), as the plan text requires.

## Freshness

- Plan identity rechecked: YES (sha unchanged)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged; commit verified as branch HEAD)
- Reported commit reachable from target branch: YES
- Required commands run this invocation: YES (all three, after final implementation state)
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` (fresh verifier audits the four gates: 6-file scope, verbatim I-7 fixture, command evidence above, commit `f5c0938` subject/scope).

---

### Finalization note (2026-08-19, controller)

Evidence re-recorded in the terminal evidence commit to include all four child artifacts (brief/execution/verify-log/fix-log). No content change beyond this note; fix_round=0.
