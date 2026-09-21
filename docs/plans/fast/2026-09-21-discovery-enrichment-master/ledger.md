# Fast-P Ledger — master: docs/plans/2026-09-21/00-discovery-enrichment-master.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-21/00-discovery-enrichment-master.md (commit 831e6604cf97e7acba005d8f00827659b49ce010)
- Amendments: N/A
- Master base: f0c41271fc56d7455e14d28a71d563a5341dfdeb
- Branch: fast/2026-09-21-discovery-enrichment-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-21T02:39:30Z
- Current child: c1
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- Approval basis: explicit `$fast-p docs/plans/2026-09-21/00-discovery-enrichment-master.md` invocation (2026-09-21), which authorizes one worktree, one local branch, and local commits for this run. The master plan and its 10 child plans were untracked on `main` at run start.
- MASTER_BASE_SHA `f0c41271fc56d7455e14d28a71d563a5341dfdeb` = `main` HEAD at run start; branch `fast/2026-09-21-discovery-enrichment-master` created there in a dedicated worktree.
- Plans were seeded on the branch as plan-only commit `831e6604cf97e7acba005d8f00827659b49ce010` (`docs/plans/2026-09-21/*.md` + the referenced `docs/investigations/2026-09-21-deep-discovery.md`), which is not an amendment. Master and all 10 child plan identities = `commit:831e6604cf97e7acba005d8f00827659b49ce010`.
- Child order and dependencies per the master plan change table: c1 (01-openalex-auth-budget) none; c2 (02-discovery-checkpoint) c1; c3 (03-crossref-arxiv) c2; c4 (04-core-orcid-scope) c2,c3; c5 (05-author-identity) c1; c6 (06-targeted-enrichment) c1,c5; c7 (07-enrichment-job-store) c6; c8 (08-auto-enrichment) c2,c5,c6,c7; c9 (09-discovery-throughput) c1,c2,c3,c4,c8; c10 (10-fulltext-yield) c2,c5. Execution is the master-declared serial order c1→c10 (one writer at a time; the shared `ExpertDiscoveryService` forbids concurrent modification).
- Baseline command results at seed commit `831e6604`: JS suite `node --test src/test/js/*.test.js` exit 0 (`tests 1035, pass 1035, fail 0`); full `mvn test` with JAVA_HOME zulu-11 in a detached scratch worktree at the same commit exit 0, `BUILD SUCCESS`, `Tests run: 3504, Failures: 0, Errors: 0, Skipped: 13` (pre-existing skips).
- Docker daemon available (`docker info` OK), JDK 11 (`zulu-11`) available, so the child 07 Flyway/MySQL integration command (`-DmigrationIt=true`) is runnable.
- Unrelated user working-tree changes exist only in the primary checkout (`tools/contactout-visible-export/**`, `docs/knowledge/**`, `docs/releases.json`, untracked `scripts/*.py`, `docs/investigations/`, `docs/plans/2026-09-21/`); this worktree is clean apart from the seeded plans.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| c1 | docs/plans/2026-09-21/01-openalex-auth-budget.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | none | 1 | LIGHT_PASS_WITH_NOTES | f0c41271fc56d7455e14d28a71d563a5341dfdeb | 147dc953a194a27ea73b7934a8e2bc334beca655 | 0 | — | 147dc953a194a27ea73b7934a8e2bc334beca655 | a8a7be6f795e1ad2464984675360ffec3f3290c2 | OpenAlex Bearer auth + shared request policy/budget; O-1 (fulltext counter has no production writer yet, deferred to c10) |
| c2 | docs/plans/2026-09-21/02-discovery-checkpoint.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1 | 1 | LIGHT_PASS_WITH_NOTES | 147dc953a194a27ea73b7934a8e2bc334beca655 | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | 0 | — | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | 69bdb7dcec2663df605f726279f0cefec5f6e685 | Checkpoint safety + honest task status; O-1 (taskFinalStatus non-null means expert-level fault counters alone cannot flip a zero-output run to FAILED) |
| c3 | docs/plans/2026-09-21/03-crossref-arxiv.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2 | 1 | LIGHT_VERIFYING | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | fba6173efd061aef73ebbce1854f83decb52a84f | 0 | — | fba6173efd061aef73ebbce1854f83decb52a84f | — | Crossref encoding + arXiv HTTPS |
| c4 | docs/plans/2026-09-21/04-core-orcid-scope.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c3 | 1 | PENDING | — | — | 0 | — | — | — | CORE offset pagination, ORCID paging, RND scope |
| c5 | docs/plans/2026-09-21/05-author-identity.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1 | 1 | PENDING | — | — | 0 | — | — | — | Trusted author identity |
| c6 | docs/plans/2026-09-21/06-targeted-enrichment.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1,c5 | 1 | PENDING | — | — | 0 | — | — | — | Targeted enrichment core + three-layer contract |
| c7 | docs/plans/2026-09-21/07-enrichment-job-store.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c6 | 1 | PENDING | — | — | 0 | — | — | — | Resumable enrichment job store (V131) |
| c8 | docs/plans/2026-09-21/08-auto-enrichment.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c5,c6,c7 | 1 | PENDING | — | — | 0 | — | — | — | Auto enqueue + worker |
| c9 | docs/plans/2026-09-21/09-discovery-throughput.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1,c2,c3,c4,c8 | 1 | PENDING | — | — | 0 | — | — | — | Fair quotas and staged expansion |
| c10 | docs/plans/2026-09-21/10-fulltext-yield.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c5 | 1 | PENDING | — | — | 0 | — | — | — | Fulltext fallback + funnel accuracy |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
