# Child 06 — Immutable execution brief

- Approved plan: docs/plans/2026-10-18/apollo-06-reporting.md
- Plan identity: sha256:43a207d15adfa9e488780fd70ce8656a15e1bd50aa121984f01efff244cdb9de
- Approval: user invocation `/fast-p docs/plans/2026-10-18/apollo-verification-master.md` on 2026-10-18 approves the exact frozen plan bytes despite its historical draft status text.
- Master: docs/plans/2026-10-18/apollo-verification-master.md
- Normative appendix: docs/plans/2026-10-18/apollo-contracts.md (read completely)
- Audit context: docs/plans/2026-10-18/apollo-code-audit.md
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
- Branch: fast/apollo-verification-master
- Dependencies: 01,02,03,04,05

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`
- `src/test/resources/discovery/apollo-verification-manual-fixture.sql`

## Contract
Read the exact child plan completely, its normative appendix, and master invariants. Implement every explicit requirement, acceptance scenario, and downstream seam; do not narrow the contract. Previous children are already committed in this worktree when dispatched. Product base will be supplied in dispatch; evidence commits are not product boundaries. No modification of plans, unlisted files, or later children. If scope/behavior is missing or conflicting, report PLAN_CONFLICT with exact evidence and leave unrelated files untouched.

Use execute-p with target worktree bound to this worktree. The fast-p loop replaces full verify-p/review-p with a fresh independent four-gate verifier; do not invoke full verification or fix-v inside this loop. One writer only. Skip build/lint/tests/formatters mid-flight; run required commands once after implementation is complete. Run all child-required tests with JDK11 plus C-7 conditions; no Docker skip counts as PASS. Exercise changed behavior through an actual runtime or throwaway driver in addition to tests, and remove throwaway scaffolds. Required regression commands from child and appendix remain mandatory. Baseline missing future test classes is expected, not a product failure.

## Global constraints / preflight
- No push, merge, rebase, amend, reset, worktree deletion, or deployment. Local child implementation commit is authorized.
- Features remain disabled by default; no real Apollo/ORCID requests, no credentials needed for offline implementation. Actual account limits/credentials remain rollout prerequisites; configured limits are application caps, not provider guarantees.
- Current execution start is 3efc192faf043badc1ef27bef124a71d3120c4c5; audited base e7c3422c176be971d823e2b82b1bca63ebb04741 differs only in app.js/index.html/styles.css for the manual recipient-count UI. Child 07 explicitly requires preserving that concurrent change. No backend baseline drift. V158 free at preflight.
- Docker OrbStack initially stopped; controller started it successfully, engine 29.8.2. Use isolated MySQL Testcontainers only. Never touch production/shared databases.
- JDK11: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Prefix Maven with env JAVA_HOME accordingly; ambient Maven otherwise uses Java25.
- Product/test changed files must be exactly within Authorized Files. Evidence execution report is separately allowed; exclude all fast-p artifacts from product commits.
- No extra retry, telemetry, fallback, abstraction, or unrelated cleanup. No contact/mail modifications or sending.
- For child-specific required fixtures and permanent tests, approved fixture/test requirements override ordinary preference for throwaway tests. Documentation updates are recorded in execution evidence; no unlisted docs authorized.
- On missing prerequisite, perform all reachable authorized work and report exact missing prerequisite; never fabricate command results.

## Required outputs
Use execution report schema from execute-p. Record task/invariant evidence, exact commands/exit codes/counts, smoke observations, plan hash recheck, worktree identity, product commit SHA, and remaining blockers. Commit only authorized implementation files as `feat(fast-p): implement 06`. Report path is this directory's execution.md; controller commits all evidence separately.

## Epoch2 — Approved CONFIG_AUTH seam
- Current approved child/master identity: commit:f9435ee70c585a158b3a3414c3bf549c35c7044b; A7/A8 record user instruction “批准该计划并执行 后续也都授权 执行完所有子计划”.
- Add existing `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` and `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` solely for read-only current auth-latch accessor and tests. Total06 scope9; no changed worker policy/state/table/reset/retry/historical inference.
- Existing06 commitbcc031775d112e1d67f20441e1c321d4b6a08967 retained; complete remaining auth capability using existing query/controller. Product base remains1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848. No independent verifier has run for06; do not call prior236 executor passes acceptance.
- Fresh final06/C-7 commands and actual HTTP/MySQL smoke including 401/403→capabilities CONFIG_AUTH without side effects required. JDK11; DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock; Maven -Dapi.version=1.44.
- Full Node baseline1694/1653pass/41fail; no unrelated fixes. Existing53 ExpertDiscoveryServiceTest fixture errors independently statically attributed in05; optional shared-DB ITs not pass or authorized real DB access. Mandatory new reporting IT must actually execute.
- If a further necessary direct dependency/caller/test file is identified, report its precise evidence to controller before editing. User advance approval permits controller to record concrete amendment without another user prompt; it does not permit unrecorded expansion/new business behavior. Original01–05 closed; do not replay them.
