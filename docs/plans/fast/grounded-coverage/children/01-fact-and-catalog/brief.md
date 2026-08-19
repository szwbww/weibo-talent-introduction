# Child Brief — 01-fact-and-catalog (P1)

## Approved contract
- Plan: `docs/plans/2026-08-19/01-fact-and-catalog.md` (plan identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)
- Read the plan file in full. It is the complete approved contract; this brief only adds global constraints and downstream contracts.
- Master plan: `docs/plans/2026-08-19/00-grounded-coverage-master.md` (identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)

## Global constraints
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage` (branch `fast/grounded-coverage`)
- Child base SHA: `af1723f37021328f8ffa61261504727e514fbb4b`
- JDK 11 required: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` — bare `mvn` fails to build.
- Use skill `execute-p` against the child plan.
- Commit the implementation locally as `feat(fast-p): implement 01-fact-and-catalog`.
- Do NOT commit fast-p reports/logs (docs/plans/fast/**) in the implementation commit; controller commits evidence separately.
- No push, no merge, no rebase, no amend, no history rewrite. One commit for implementation.
- Do not review later children, repair unrelated behavior, or add files outside Authorized Files.

## Authorized files (exact, from plan 变更文件清单)
1. `src/main/resources/db/migration/V105__add_programme_identity_facts.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` (modify)
3. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` (modify)
4. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` (modify)
5. `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` (modify)
6. `src/test/kotlin/com/weibo/talentintroduction/qa/service/ProgrammeIdentityFactsMigrationTest.kt` (new)

## Required commands (all must run; JDK11)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='AiReplyIntentCatalogTest,QaFactSelectionServiceTest,ProgrammeIdentityFactsMigrationTest'`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full regression gate)
- `git diff --check`
- Pass criteria: exit 0, `Tests run: N, Failures: 0, Errors: 0`.

## Key invariants (from plan; full set in plan)
- I-1 new coverage keys must be referenced by an intent (both directions guarded by test).
- I-2 same keyword must satisfy letter-substring (QaFactKeywordMatcher.normalize) AND alias-substring (AiReplyIntentCatalog.canonicalize). No keyword may contain `programme`.
- I-3 new intents `requiresProfile = false` (default).
- I-4 new rules' coverage_keys written in V105 INSERT columns.
- I-5 id=6/id=18 keyword appends must be CONCAT + NOT LIKE guarded + `updated_at = updated_at`; new INSERTs guarded by `WHERE NOT EXISTS`.
- I-6 id=6 must NOT gain `partner companies`/`partner company`/`collaboration with` keywords.
- I-7 tests must use the verbatim letter from plan top.
- I-8 do not touch `TrustReplyWorkbenchService.bootstrap` STALE path (file is not authorized).
- N6: `git status --porcelain src/main/resources/db/migration` must show only the new V105 file.

## Downstream interfaces (consumed by later children)
- After P1, `AiReplyIntentCatalog.definitions` gains `programme.name`, `governance.sponsor`, `collaboration.form`; `QaCoverageKeyCatalog` gains `programme.name`, `governance.sponsor_level`; V105 exists with 4 statements.
- P2 (02-unrecognized-request-detection) depends on these exact intents matching the orthopaedic letter 5/5.

## Verification contract
- After READY_FOR_VERIFICATION, a fresh verifier audits the four gates. Keep your execution report at:
  `docs/plans/fast/grounded-coverage/children/01-fact-and-catalog/execution.md`
- Report shape: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
