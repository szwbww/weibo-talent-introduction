# Child 02-country-supplement brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair
- Branch: fast/apollo-institution-subject-repair
- Exact approved child plan: docs/plans/2026-10-19/discovery-institution-country-supplement.md
- Plan sha256: 3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67
- Entry/master plan: docs/plans/2026-10-19/apollo-institution-subject-repair.md (read its ordering, 2026-10-19 supplement, and invariants).
- Child Base (product boundary): 1e2ae74981ccb8b6866b135222b2a24540ea09b8 (child 01 terminal code head). The preceding afbc806 commit is docs-only.
- Prior child output: child 01-institution-subject is LIGHT_PASS_WITH_NOTES with zero repairs (evidence 274ab5c). Read docs/plans/fast/apollo-institution-subject-repair/children/01-institution-subject/execution.md and inspect the implemented `DiscoveryInstitutionNormalizer` API before T-0. For this child, consume the normalizer read-only; do not modify it (not authorized).
- Authorized Files: exactly the 8 paths in the child plan's 变更文件清单 (4 main, 3 test classes, 1 test resource). No other product, test, config, migration, ES mapping, frontend, plan, or build file may change. Do not modify `DiscoveryInstitutionNormalizer.kt`, Rules/Gate/QueryService/Worker, Repository, Gate, Commit, or ES writers.
- T-0 gate: verify the two predecessor stages are materially complete; re-read RegistrationService reserve, both ExpertDiscoveryService reserve call sites, snapshot consumers, DiscoveryIdentity, CountryContinentMapping, QualificationEvidenceParser, and the actual normalizer API. Record the current `country`/token/fact-fingerprint contracts and update the plan's attachment hashes only if line/naming drift requires it; any behavior or whitelist change is PLAN_CONFLICT.
- Required commands, invariants I-1..I-6, T-1..T-4, acceptance criteria: the exact child plan. Do not weaken any invariant.
- Key contracts: single deployment switch default false (`countrySupplementEnabled`, bound to `talent-introduction.discovery-verification.country-supplement-enabled`; do not edit application.yml); supplement only after protections/independent checks and before the first frozen snapshot; exactly one bounded ORCID employments request when OpenAlex has no usable country; no new SQL writer/table/ES field; persist only `snapshot_json.countrySupplement` with COUNTRY_V1; retries/concurrency return the winner's frozen profile; no deployment/restart/live backfill/UI.
- No push, merge, rebase, squash, amend, or history rewrite.
- Use execute-p. Run every required command fresh after implementation. SKIP is not PASS; Docker/MySQL FlowIT is required. Use JDK11 at /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home and runtime-only DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock with -Dapi.version=1.40.
- Commit the implementation locally as: feat(fast-p): implement 02-country-supplement
- Exclude fast-p reports/logs from the implementation commit; the controller commits evidence separately.
- Write the full execution report (T-0 receipts, commands with exit codes/counts, fixture/FlowIT outputs, real runtime proof, deviations) to docs/plans/fast/apollo-institution-subject-repair/children/02-country-supplement/execution.md.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
