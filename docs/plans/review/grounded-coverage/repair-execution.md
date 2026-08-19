# Repair Execution — 00-grounded-coverage-master

- Approval source: HUMAN invoked `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md` (2026-08-19). The plan's own "Human Approval" section designates this exact invocation as the approval.
- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md`
- Repair plan SHA-256: `3492c2d2615c9184f4fe673c6a95b69c84c1468d642e48092c5d904172659d7c`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md@3492c2d2615c9184f4fe673c6a95b69c84c1468d642e48092c5d904172659d7c`
- Execution epoch: NEW
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage`
- Target branch: `fast/grounded-coverage`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage@fast/grounded-coverage@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-grounded-coverage`
- Pre-execution code SHA: `8c2ec53f4e97d06acb89b81bfb5a388a9d49a566` (product code head; branch HEAD was `9a28ec00b2474ac3cad71d4887d3cc02a8b735cb`)
- Post-execution code SHA: `a7cceb2e3fdec25cecd4e3582135edefb3a5447f`
- Evidence HEAD: `docs(review-fast-p): record repair execution` commit (see below)

## Changed files (product commit `a7cceb2`, subject `fix(qa): support remuneration in funding facts`)

- `src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql` (new) — conditionally appends `remuneration` to `Funding support` keywords (`CONCAT` + `NOT LIKE '%remuneration%'` guard, `updated_at = updated_at`, targets `reply_subject = 'Funding support'` only). V3–V105 byte-for-byte unchanged (verified: repair diff vs `8c2ec53` shows only V106 under `db/migration`).
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` (modified) — fixture comment ties the Funding support `remuneration` keyword to post-V106 production state; new test `V106 conditionally appends remuneration to funding support preserving updated_at` asserts the migration targets Funding support, guards with `NOT LIKE`, and preserves `updated_at`. The V-1 regression (verbatim orthopaedic letter → five recognised intents, five SUPPORTED, five bound facts, `GROUNDED`, finance evidence = Funding support only) is unchanged and now tied to the migration.

## Commands (all fresh, JDK11 zulu-11)

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=...zulu-11... mvn test -Dtest=QaFactSelectionServiceTest` | PASS (exit 0) | surefire `QaFactSelectionServiceTest`: tests=44, failures=0, errors=0; node 658/658 |
| `JAVA_HOME=...zulu-11... mvn test` | PASS (exit 0) | surefire aggregate: tests=2590, failures=0, errors=0; node 658/658 |
| `git diff --check af1723f37021328f8ffa61261504727e514fbb4b HEAD` | PASS (exit 0) | no whitespace errors |

## Deviations

- None. Only the two Authorized Files changed in the product commit; evidence commit contains only this file.

## Clean state evidence

- `git status --porcelain` after the evidence commit: empty (clean).
- Product commit `a7cceb2e3fdec25cecd4e3582135edefb3a5447f` is HEAD's ancestor on `fast/grounded-coverage`; repair-execution docs commit is the branch tip.
- Executor identity: controller `Main` (this execution report emitted by the execute-p dispatch).
