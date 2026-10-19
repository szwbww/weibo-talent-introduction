# Child 07 — Immutable execution brief

- Approved plan: docs/plans/2026-10-18/apollo-07-frontend.md
- Plan identity: sha256:efce42d320a64915768fc6277257fab928090d2d7b64c0a40b6503d88d773f4d
- Approval: user invocation `/fast-p docs/plans/2026-10-18/apollo-verification-master.md` on 2026-10-18 approves the exact frozen plan bytes despite its historical draft status text.
- Master: docs/plans/2026-10-18/apollo-verification-master.md
- Normative appendix: docs/plans/2026-10-18/apollo-contracts.md (read completely)
- Audit context: docs/plans/2026-10-18/apollo-code-audit.md
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
- Branch: fast/apollo-verification-master
- Dependencies: 01,02,03,04,05,06

## Authorized Files
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`
- `src/test/js/discoveryVerification.test.js`
- `src/test/js/discoveryContinuousRun.test.js`
- `src/test/js/discoveryScheduleSetting.test.js`
- `src/test/js/discoveryReview.test.js`

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
Use execution report schema from execute-p. Record task/invariant evidence, exact commands/exit codes/counts, smoke observations, plan hash recheck, worktree identity, product commit SHA, and remaining blockers. Commit only authorized implementation files as `feat(fast-p): implement 07`. Report path is this directory's execution.md; controller commits all evidence separately.

## Current execution handoff
- Master identity commit:f9435ee70c585a158b3a3414c3bf549c35c7044b; original07 plan snapshot remains unchanged until a separately recorded concrete amendment.
- Through06 clean checkpoint VALID at2ddf49514057f4a274441bd74c1a72c46a231715; product basecd105de17d7100466543675727a68fe1370008cb. All prior children closed.
- Existing06 real API handoff: Apollo participation filters108/45/5/18 vs admitted40; itemdetail typedapolloPersonId andapolloStages (enteredAt/searchedAt/candidateFoundAt/matchedAt), current auth CONFIG_AUTH capability. Inspect current DTOs, don't infer shape from demo.
- User instruction “批准该计划并执行 后续也都授权 执行完所有子计划” preauthorizes necessary direct dependency/caller/test scope amendments, not unrecorded modifications or new business goals. Send exact missing-file evidence to controller first; it records concrete plan-only amendment and ledger authority, without a routine user pause. FrozenCSS/DOM and existing feature invariants remain.
- Save actual browser proof under children/07/browser if retained in repository; controller commits those separately from product. Browser UI may use isolated fixture transport but must be actualindex/app, not frozenpreview; report transport/production integration limits.
- Mandatory fresh Node four discovery files including newly created discoveryVerification, full suite and app syntax; current baseline1694/1653/41. Other literal cache-key contract tests may require mechanical migration; report exact files before editing, don't claim additional failures baseline without evidence.

## Approved necessary S-5 batch read seam
- Current07/master identity commit:d0c1e1a74fc337c93cba9df1db35b67732abb0b4 supersedes original07snapshot under A9/A10 and user's recorded advance authorization.
- Additional authorized existing files: discovery/service/DiscoveryVerificationQueryService.kt, discovery/repository/DiscoveryVerificationRepository.kt under src/main/kotlin/com/weibo/talentintroduction; corresponding src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt and discovery/repository/DiscoveryVerificationReportingIT.kt. Exact full paths in amended plan; total07scope11, approved necessary read-dependency exception.
- Add batches item cumulativeProfiles across all prior/equal activated batches independent of current page; updatedAt=MAX activated-item updated_at in batch; complete deduplicated reason counts, stable unknown codes. Existing same RR snapshot, counts/suggested/runCounts/API shapes and all writer logic unchanged. No unbounded frontend paging to manufacture missing facts.
- Product base unchangedcd105de17d7100466543675727a68fe1370008cb; epoch1/fix0; no06replay. Fresh amended batch SQL/DTO regressions plus required frontend/browser proof.

## Approved obsolete CSS position-test retirement
- Current07/master identity commit:00c0c9e75926ec9a8a9487fccf40d35d6275df3f supersedes prior scope under A11/A12.
- Add exact `src/test/js/expertManualProfile.test.js` and `src/test/js/expertMeetingVideo.test.js` solely to delete obsolete old-CSS-at-EOF source-position assertions and any consequently unused constants, not re-pin an incidental implementation constraint. Preserve all other behavior checks. Total07scope13; no further production files.
- Authority: same user's advance approval, concrete mismatch observed in actualfullNode1712/1669/43 versusexisting41. Final required Node must freshly exercise corrected state, no unrelated baseline repair.
