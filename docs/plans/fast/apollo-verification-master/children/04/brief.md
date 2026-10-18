# Child 04 — Immutable execution brief

- Approved plan: docs/plans/2026-10-18/apollo-04-worker.md
- Plan identity: sha256:8507e21e882539d54f908732dc58095129f9a3f2538f3b4c8b8a003926fa487a
- Approval: user invocation `/fast-p docs/plans/2026-10-18/apollo-verification-master.md` on 2026-10-18 approves the exact frozen plan bytes despite its historical draft status text.
- Master: docs/plans/2026-10-18/apollo-verification-master.md
- Normative appendix: docs/plans/2026-10-18/apollo-contracts.md (read completely)
- Audit context: docs/plans/2026-10-18/apollo-code-audit.md
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
- Branch: fast/apollo-verification-master
- Dependencies: 01,02,03

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`

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
Use execution report schema from execute-p. Record task/invariant evidence, exact commands/exit codes/counts, smoke observations, plan hash recheck, worktree identity, product commit SHA, and remaining blockers. Commit only authorized implementation files as `feat(fast-p): implement 04`. Report path is this directory's execution.md; controller commits all evidence separately.

## Epoch 2 — Approved ORCID amendment (supersedes Epoch1 identity/scope)
- Exact plan identity: commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16
- Exact plan SHA-256: 9b8d742b37fefd332cf9799db5f479c9b24653f5e3ce945d6b01727591e25819
- Exact master identity: commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16
- Human approval: “批准该计划并执行”, recorded2026-10-18T21:06:49+08:00; ledgerA3/A4.
- Authorized Files: original seven entries above plus `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClient.kt` and `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClientTest.kt` (nine total; exact amended plan table is authoritative).
- Add per-endpoint completion and persisted-result/cursor resume to the existing client. Worker persists successful response/attempt/progress before allowing next HTTP; restart skips completed endpoints. Persistence failure prevents subsequent request. Preserve binding, allowlist, permits, cumulative caps and dedicated HTTP. No duplicate ORCID/HTTP implementation.
- Product base:98c7b78fde54f69b72f175b94b2ab0eed25d86ab. Prior01–03 completed; child04 Epoch1 made no product changes. Controller epoch2 is a new execution identity; append report after history.
- All other invariants, required commands, smoke/worktree binding and product-only commit rules above remain applicable. Run client regression plus worker/commit/real-MySQL FlowIT and planned related regressions; no fake Docker PASS.
