# Repair Execution — mail-reliability V-1

- Approval source: human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/fix/00-main-plan-mail-reliability/repair.md` (2026-08-07).
- Repair identity: `docs/plans/fix/00-main-plan-mail-reliability/repair.md` @ sha256 `e9f8778a7751fb7523e9cdf9e8060cf93c2aed5e7f799878e87b2f3b46c11c78` — unchanged across execution (rechecked after implementation).
- Worktree identity: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability@fast/mail-reliability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/mail-reliability` — unchanged.
- Finding: V-1 (aggregate/master epoch 1) — decorative tag-fetch rejection aborts four detail renderers (`showExpertDetail`, `loadContactDetail`, `showMailDetail` read-only branch, `showUnmatchedDetail`).
- Pre-execution code SHA: `9185bba` (HEAD at start; product code `ef7e471`).
- Post-execution code SHA: `c3e6d00`.
- Evidence HEAD: `605a34f` (next commit below).

## Changed Files (authorized only)

- `src/main/resources/static/app.js` (+40 -8) — at the four renderer call sites only: catch a rejected `fetchExpertTagsFromEs` request, `showStatus(error.message, "error")`, continue with `{ found: false, tags: [] }` so the S-1 editor renders and the panel completes. `fetchExpertTagsFromEs` itself unchanged (still rethrows); `handleContactAction` guard, `data-level` sourcing, S-1/S-2 output untouched; no CSS, no backend, no migration.
- `src/test/js/expertProfileAbsence.test.js` (+235) — new V-1 regression suite: brace-aware `extractFunction` harness (stack-based tokenizer for nested template literals), DOM-stubbed `createRendererSandbox`, four tests asserting panel HTML populated + S-1 notice + `showStatus(error)` for each renderer. Existing suites (incl. the `fetchExpertTagsFromEs` rejects-in-isolation unit test) untouched.

## Commands (all run fresh in this invocation, JDK 11 zulu-11)

| Command | Exit | Result |
|---|---|---|
| `node --test src/test/js/expertProfileAbsence.test.js` | 0 | 11 pass / 0 fail |
| `node --test src/test/js/expertTagBatchFix.test.js` | 0 | 33 pass / 0 fail |
| `node --check src/main/resources/static/app.js` | 0 | no output |
| `JAVA_HOME=… mvn test -Dtest=ExpertIndexControllerTest` | 0 | Tests run: 18, Failures: 0, Errors: 0 |
| `JAVA_HOME=… mvn test` | 0 | Kotlin 2187/0/0/4-skipped; JS 470 pass / 0 fail; BUILD SUCCESS |
| `JAVA_HOME=… mvn clean package` | 0 | BUILD SUCCESS (2187/0/0/4) |
| `git diff --check` | 0 | silent |

## Discrimination Evidence

Stashed the pre-fix `app.js` and re-ran the suite: exactly the 4 new V-1 tests failed (7 existing passed); with the fix all 11 pass.

## Deviations

None (two harness stubs `loadEmailAliases` / `mountLiveTrustReply` added during test bring-up — part of the authorized test file).

## Executor

Main session (controller), human-invoked `$execute-p`.

## Clean-State Evidence

At start: worktree clean except the 2 authorized files (modified). After product commit: clean. After evidence commit: clean.

## Commits

- `c3e6d00` — `fix(mail-reliability): degrade tag-load failures in detail panels` (2 authorized files, product + test)
- Next: `docs(review-fast-p): record mail-reliability repair execution` (this handoff only)
