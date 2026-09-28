# Repair Execution Handoff — academic enrichment root updatedAt (V-1)

- Approval source: HUMAN `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/fix/discovered-institution-repair-00-master/repair.md` (2026-09-28; the plan's "Review-Fast-P Execution Handoff" section authorizes this execution verbatim)
- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/fix/discovered-institution-repair-00-master/repair.md`
- Repair identity (sha256): `5c02f818d2acbc0e37ddae696e0383af5f2c6d2e39d4f6933f03fa0e7540ad95` (unchanged before and after execution)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master` (branch `fast/2026-09-28-discovered-institution-repair-00-master`)
- Worktree identity: `root=/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master @ branch=fast/2026-09-28-discovered-institution-repair-00-master @ git-dir=/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Pre-repair code SHA: `70e6144065335beee72dbd22a84e4bb975a68928` (fast-p product head; execution-start HEAD was `6cdfd6794aadebd56282daf3de64f4cba463426e`, the aggregate machine-verification evidence commit)
- Post-repair code SHA: `5096e0618f7ecdc2224b9b472effd812d5b96828` (product commit `fix(discovery): preserve root updatedAt during academic enrichment`)
- Executor: Main (fast-p controller session), `execute-p` contract

## Changed files (authorized only)

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — `updateExpertAcademicFields` no longer puts root `updatedAt` into the partial-update document (the key was the V-1 root cause). `enrichedAt`, `enrichmentSource`, every non-null academic field, the discovery identity script guard, classification recomputation and the per-layer HEAD/`_update` behavior are unchanged; a comment records why the timestamp must not advance.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — new discriminating regression test `enrichExistingExperts never writes root updatedAt in any layer update payload (V-1, I-3)`: runs academic enrichment with a supplied `hIndex`/`disciplineCategory`, captures every `/_update/` payload and asserts each one lacks `updatedAt`, `institutionType` and `institutionEvidence`, while still carrying `enrichedAt`, `hIndex` and `disciplineCategory`.

## Commands (all fresh in this invocation, post-change)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=ExpertDiscoveryServiceTest` | 0 | Tests run: 172, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=…/zulu-11.jdk mvn test` | 0 | BUILD SUCCESS; surefire 4263 tests, 0 failures, 0 errors, 13 skipped (269 classes); test-phase Node 1202 tests / 1202 pass / 0 fail |
| `git diff --check` | 0 | silent |

Pre-change red evidence for the same required command: `mvn test -Dtest=ExpertDiscoveryServiceTest` → exit 1, `Tests run: 172, Failures: 1`, sole failure `enrichExistingExperts never writes root updatedAt in any layer update payload (V-1, I-3)` with `补全不得写根级 updatedAt：{updatedAt=2026-09-28 21:13:43, enrichedAt=…, enrichmentSource=OPENALEX, hIndex=10, citationCount=100, worksCount=5, disciplineCategory=STEM, expertClassification=…}`.

## V-1 resolution evidence

- The captured pre-fix payload contained `updatedAt=2026-09-28 21:13:43` alongside `enrichedAt`; the post-fix payload for the same scenario contains `enrichedAt` and the academic fields but no `updatedAt` key, for every layer that received an `_update` (RAW and candidate under the recorded test stubs).
- I-3 preserved: no `institutionType` and no `institutionEvidence` key in any enrichment payload; discovery identity guard, classification recomputation, per-layer 404/FAILED semantics and promotion behavior untouched.
- Head count: 4263 = 4262 (pre-repair full-suite count) + 1 new regression test; zero new failures/errors.

## Deviations

- The pre-commit `worktree_identity.py` check was invoked with an incorrect `--expect-git-dir` value (`<worktree>/.git/worktrees/…` instead of the real `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`), and its non-zero exit was masked by the shell pipeline that followed. The product commit was therefore created without a passing pre-commit gate check. Immediately afterwards the gate was re-run with the correct expectation (exit 0, identity above) and the commit was proven to be `HEAD` of the target branch and reachable from it. Zero product impact; no history rewrite was performed.

## Clean-state evidence

- `git status --porcelain` after the product commit: empty (only this handoff and its docs-only evidence commit follow).

## Next action

- `READY_FOR_VERIFICATION` — aggregate re-review decides compliance; no push, merge, PR or further repair round was performed.
