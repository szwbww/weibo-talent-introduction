# Child 05 — Immutable execution brief

- Approved plan: docs/plans/2026-10-18/apollo-05-integration.md
- Plan identity: sha256:ddcb578f31c657cdfbbfef55b6b603c17abc508b0a84e1dbd5323e74cc14c714
- Approval: user invocation `/fast-p docs/plans/2026-10-18/apollo-verification-master.md` on 2026-10-18 approves the exact frozen plan bytes despite its historical draft status text.
- Master: docs/plans/2026-10-18/apollo-verification-master.md
- Normative appendix: docs/plans/2026-10-18/apollo-contracts.md (read completely)
- Audit context: docs/plans/2026-10-18/apollo-code-audit.md
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
- Branch: fast/apollo-verification-master
- Dependencies: 01,02,03,04

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

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
Use execution report schema from execute-p. Record task/invariant evidence, exact commands/exit codes/counts, smoke observations, plan hash recheck, worktree identity, product commit SHA, and remaining blockers. Commit only authorized implementation files as `feat(fast-p): implement 05`. Report path is this directory's execution.md; controller commits all evidence separately.

## Epoch2 — Approved caller-test scope amendment
- Current approved plan identity: commit:a58d457ec2921759c455b6f6edae0e6d94b85220 (supersedes original snapshot for epoch2 only).
- User “批准该计划并执行” approved the exact preceding C05-EXECUTION-CALLERS proposal; ledgerA5/A6 and plan-only commit record authority.
- Additional authorized files, solely for explicit real execution/context callable migration and related existing regression assertions:
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt`
- Total05 scope13 is the approved test-only exception to master's <=10 cap. Original production scope unchanged. Required regressions include both controller test classes.
- Product base:a0103c2ea9284c030ad0b0b10c4177474c4f4d47; fix_round=0. Completed01–04 remain closed, through04 checkpoint VALID; no product edits from05 epoch1 remain.
- Docker: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and Maven -Dapi.version=1.44; legacy optional shared-DB DiscoveryReviewRepositoryIT must stay skipped, never count skip as pass. Mandatory isolated MySQL tests must actually execute.
- Current full Node baseline1694,total/pass1653/fail41; fresh separate Node evidence required if Maven -DskipNodeTests=true. Do not repair unrelated baseline or claim historical evidence current.
- Interpretation receipt from epoch1: `run.settings另存mode/policy/limits` mandates recording configured caps; C-1/C-3 and04 contain no explicit requirement to consume historical limits instead of live safety caps. No worker/client scope is granted. Preserve exact contract rather than inventing additional requirements.
