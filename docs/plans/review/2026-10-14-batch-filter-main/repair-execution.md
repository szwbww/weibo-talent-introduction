# Repair Execution — batch-filter-main

## Approval and identity

- Approval source: human-originated invocation `$execute-p docs/plans/fix/batch-filter-main/repair.md` (this invocation) — the exact approval path named by the plan's `## Review-Fast-P Execution Handoff`.
- Plan: `docs/plans/fix/batch-filter-main/repair.md`
- Plan SHA-256: `71d5bdd391c7915a6190f9f29b9590d0d10f1de033930565e2a38194126a55be` (identical to the hash the aggregate review recorded for the reviewed draft)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main/docs/plans/fix/batch-filter-main/repair.md@sha256:71d5bdd391c7915a6190f9f29b9590d0d10f1de033930565e2a38194126a55be`
- Execution epoch: NEW (no prior execution artifact names this identity)
- Executor: omp controller session (single writer; the invocation did not request subagent isolation)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Target branch: `fast/2026-10-14-batch-filter-main`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main@fast/2026-10-14-batch-filter-main@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Pre-execution code SHA: `50dc450b9e3d68b4cc44ad3a660d8a00936bb662` (pre-repair HEAD of the branch lineage: `683a71aaf510515de4eb352651a106a362108741`)
- Post-execution code SHA: `e6499a22c4696a8d98ac1ffe2be0e191db5dca97`
- Evidence HEAD: recorded by the docs-only commit that contains this file

## Repair identity and tasks

- Task R-1 (resolves V-1 and V-2). Status: IMPLEMENTED.
- Implementation boundary: `683a71aaf510515de4eb352651a106a362108741..e6499a22c4696a8d98ac1ffe2be0e191db5dca97` (single product commit `fix(batch-filter): align recipient preflight`).

### Changed files (all four are on the plan's Authorized Files list)

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | V-1: the level/tag base scan no longer carries `exists email`, so email-less members stay in the diagnostic baseline. V-2: `runIntroductionFromSnapshot` consumes one lazily-computed preflight eligibility set for NEW-retry selection and every ES page, keeping all send-time rechecks; the legacy progress estimate (`allRetryableTargets.size + esEstimate`) is unchanged. |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt` | V-1: `RecipientProtectionReasonKeys.NO_RECIPIENT_ADDRESS` (`PROTECTION_NO_RECIPIENT_ADDRESS`) expresses the deterministic no-usable-address exclusion; `preflight` folds it into `protectionReasonKeys` (and therefore into `reasonKeys` / `eligible` / `excludedUnique` / `reasonHits`) while reusing one normalized address for the suppression and verification-phase checks. |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt` | New regression: a base member with blank/null address is `eligible=false`, keeps its explicit `NO_RECIPIENT_ADDRESS` reason, stays in `baseline`, and partitions counts correctly. |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | New regression: preview and per-docId diagnostics retain and explain one address-less and one suppressed target (`3/2/1`, reasons `NO_RECIPIENT_ADDRESS` + `SUPPRESSED`), while the run selects only the eligible target — `sent=1`, `failed=0`, `skipped=0`, one SMTP call. |

No other file, migration, ES mapping/writer change, admission policy, live send, backfill, UI or person-merge logic was touched. `git diff --check` is clean.

## Commands (all run freshly in this invocation, after the final implementation state)

| # | Command | Result |
|---|---|---|
| 1 | `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false` | PASS — exit 0; 342 tests, 0 failures/errors/skips (preflight 14, manual outreach 190, selection 14, email verification 40, expert search 75, country mapping 9); bound JS gate 1671 pass / 0 fail |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false` | PASS — exit 0; 4 tests, 0 failures; real testcontainers MySQL 8.0.36 started (not skipped) |
| 3 | `node --test src/test/js/recipientPreviewDiagnostics.test.js` | PASS — exit 0; 13/13 |
| 4 | `node --test src/test/js/batchEmailVerification.test.js` | PASS — exit 0; 42/42 |
| 5 | `node --test src/test/js/gateTemplateFilter.test.js` | PASS — exit 0; 8/8 |
| 6 | `node --test src/test/js/*.test.js` | PASS — exit 0; 1671 tests, 1671 pass, 0 fail |
| 7 | `node --check src/main/resources/static/app.js` | PASS — exit 0 |

## Deviations

- None. No plan requirement was reinterpreted; no authorized file was skipped; no unauthorized file was added.
- Execution notes (not deviations): the preflight eligibility set is computed lazily so that cancelled/empty runs do not pay for an extra base scan, and it is still computed at most once per run and shared by NEW-retry selection and every ES page. The legacy progress estimate keeps its pre-repair formula so the recorded preview/execution `totalSendable`↔`total` parity contract is preserved; runtime skips from protections that appear after the preflight remain possible and are still recorded at send time.

## Clean-state evidence

- Product commit `e6499a22c4696a8d98ac1ffe2be0e191db5dca97` (`fix(batch-filter): align recipient preflight`) stages exactly the four authorized files and is `HEAD` of `fast/2026-10-14-batch-filter-main` before this evidence commit.
- The worktree identity was re-checked with `--expect-root/--expect-branch/--expect-git-dir` immediately before staging and before the commit.
- This docs-only commit (`docs(review-fast-p): record repair execution`) contains only this file.
