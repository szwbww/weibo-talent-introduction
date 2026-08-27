# Repair Execution Handoff — prompt-pool authority (V-1)

- Approval source: HUMAN `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fix/00-execution-order/repair.md` (2026-08-27; the plan's "Review-Fast-P Execution Handoff" section authorizes this execution verbatim)
- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fix/00-execution-order/repair.md`
- Repair identity (sha256): `3672c34b40d77efee1ca8fc15b44675cfaf230d37c4a1e28121538e92ac87d05`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order` (branch `fast/2026-08-26-execution-order`)
- Worktree identity: `root=/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order @ branch=fast/2026-08-26-execution-order @ git-dir=/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-26-execution-order` — script `worktree_identity.py` crashes on stale `/sessions` worktree entries (FileNotFoundError); gate replicated manually with the script's exact git commands before/after/at commit
- Pre-repair code SHA: `cb30230970d12e649e9faac2835335345daac793` (product head)
- Post-repair code SHA: `7ce95dba4b01d559ce580cc964564cc648c292a4` (product commit `fix(fast-p): enforce prompt-pool authority`)
- Executor: fast-p controller (Main), execute-p contract

## Changed files (authorized only)

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt` — `poolById` built from `promptPool` (the exact truncated list passed to `buildUserContent`) instead of the untruncated `pool`; comment documents the V-1 root cause and the 01 I-4 prompt-pool authority rule. Prompt truncation, cache key/fingerprint, model call, temperature 0.0, result/error shape, and ordering unchanged.
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt` — new regression test `rejects an id that is absent from the truncated prompt pool`: `maxRulesInPrompt = 1`, model returns in-pool id 10 + out-of-prompt id 11; asserts 11 is rejected `reason=not_in_pool`, `byRequestIndex == {1: [10]}`, accepted=1 rejected=1, and that the user prompt contains rule 10 but not rule 11.

## Commands (all fresh, post-change)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=QaFactRetrieverTest` | 0 | Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=QaFactSelectionRetrievalTest` | 0 | Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=…/zulu-11.jdk mvn test` | 0 | Tests run: 2873, Failures: 0, Errors: 0, Skipped: 4 (pre-existing @Disabled) |
| `JAVA_HOME=…/zulu-11.jdk mvn clean package` | 0 | BUILD SUCCESS |
| `node --test src/test/js/*.test.js` | 0 | 735 pass, fail 0 |
| `git diff --check` | 0 | silent |

Head count 2873 = prior 2872 + 1 new regression test; zero failures/errors.

## Deviations

- Worktree identity script unavailable (stale `/sessions` worktree entries crash `resolve(strict=True)`); replicated gate manually with the script's exact commands and re-verified after the commit: root/branch/git-dir/HEAD all correct, `7ce95db` is HEAD of the branch and reachable from it. Zero product impact.

## Clean-state evidence

- `git status --porcelain` after the product commit: only `docs/plans/**` evidence files pending (this handoff + its evidence commit), no product/test/index dirt.

## Next action

- `READY_FOR_VERIFICATION` — independent verification (review-p / review-fast-p) decides compliance.
