# Fast-P Ledger — master: docs/plans/2026-09-21/00-discovery-enrichment-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-09-21/00-discovery-enrichment-master.md (commit 831e6604cf97e7acba005d8f00827659b49ce010)
- Amendments: N/A
- Master base: f0c41271fc56d7455e14d28a71d563a5341dfdeb
- Branch: fast/2026-09-21-discovery-enrichment-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-21T02:39:30Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- Approval basis: explicit `$fast-p docs/plans/2026-09-21/00-discovery-enrichment-master.md` invocation (2026-09-21), which authorizes one worktree, one local branch, and local commits for this run. The master plan and its 10 child plans were untracked on `main` at run start.
- MASTER_BASE_SHA `f0c41271fc56d7455e14d28a71d563a5341dfdeb` = `main` HEAD at run start; branch `fast/2026-09-21-discovery-enrichment-master` created there in a dedicated worktree.
- Plans were seeded on the branch as plan-only commit `831e6604cf97e7acba005d8f00827659b49ce010` (`docs/plans/2026-09-21/*.md` plus the referenced `docs/investigations/2026-09-21-deep-discovery.md`); seeding is not an amendment. Master and all 10 child plan identities = `commit:831e6604cf97e7acba005d8f00827659b49ce010`.
- Child order and dependencies follow the master plan's change table: c1 none; c2 c1; c3 c2; c4 c2,c3; c5 c1; c6 c1,c5; c7 c6; c8 c2,c5,c6,c7; c9 c1,c2,c3,c4,c8; c10 c2,c5. Execution was the master-declared serial order c1→c10 (one writer at a time; the shared `ExpertDiscoveryService` forbids concurrent modification).
- Baseline commands at seed commit `831e6604cf97e7acba005d8f00827659b49ce010`: JS `node --test src/test/js/*.test.js` exit 0 (`tests 1035, pass 1035, fail 0`); full `mvn test` with JAVA_HOME zulu-11 in a detached scratch worktree at the same commit exit 0, `BUILD SUCCESS`, `Tests run: 3504, Failures: 0, Errors: 0, Skipped: 13`.
- Environment notes: Docker runs under OrbStack; container-backed tests need `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` and `-Dapi.version=1.40` (docker-java default API 1.32 is below OrbStack's minimum). Two pre-existing red tests affect required commands: the Flyway IT's `V124 allows material attached promotion audit trigger` (fk_eap_contact) and the container-backed ITs under the bare command; both were reproduced at child bases by the verifiers and in the controller baseline (which ran with migration ITs skipped).
- All ten children reached a terminal light verdict with zero automatic fix rounds; no plan amendment was requested or approved during the run. No whole-system verification was performed.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| c1 | docs/plans/2026-09-21/01-openalex-auth-budget.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | none | 1 | LIGHT_PASS_WITH_NOTES | f0c41271fc56d7455e14d28a71d563a5341dfdeb | 147dc953a194a27ea73b7934a8e2bc334beca655 | 0 | — | 147dc953a194a27ea73b7934a8e2bc334beca655 | a8a7be6f795e1ad2464984675360ffec3f3290c2 | OpenAlex Bearer auth, shared request policy/budget |
| c2 | docs/plans/2026-09-21/02-discovery-checkpoint.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1 | 1 | LIGHT_PASS_WITH_NOTES | 147dc953a194a27ea73b7934a8e2bc334beca655 | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | 0 | — | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | 69bdb7dcec2663df605f726279f0cefec5f6e685 | Checkpoint safety + honest task status |
| c3 | docs/plans/2026-09-21/03-crossref-arxiv.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2 | 1 | LIGHT_PASS_WITH_NOTES | 468df56bf69b4b2f9afc7b4f38ad4791d8331240 | fba6173efd061aef73ebbce1854f83decb52a84f | 0 | — | fba6173efd061aef73ebbce1854f83decb52a84f | 915e542b8841c7496844cced0722e9453293eeeb | Crossref single-encoding + arXiv HTTPS/error detection |
| c4 | docs/plans/2026-09-21/04-core-orcid-scope.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c3 | 1 | LIGHT_PASS_WITH_NOTES | fba6173efd061aef73ebbce1854f83decb52a84f | 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b | 0 | — | 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b | 5ed8de19c84b68d42f42b1e621a705dbd08f35c1 | CORE offset pagination, ORCID raw-record paging, RND scope |
| c5 | docs/plans/2026-09-21/05-author-identity.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1 | 1 | LIGHT_PASS | 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b | 1ba685217a166628968a798450ff203191ead79c | 0 | — | 1ba685217a166628968a798450ff203191ead79c | 04cd01039f71e157ab5c25bafe959551a4f2e3c3 | Trusted author identity (externalIds.openAlexAuthorId) |
| c6 | docs/plans/2026-09-21/06-targeted-enrichment.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1,c5 | 1 | LIGHT_PASS_WITH_NOTES | 1ba685217a166628968a798450ff203191ead79c | 16647117f7f7f7e7d1a66f35524900f0ea431e0d | 0 | — | 16647117f7f7f7e7d1a66f35524900f0ea431e0d | 65429bfc829ef5828d37ab6a072c5c6fba2f088c | Targeted enrichment core + three-layer result contract |
| c7 | docs/plans/2026-09-21/07-enrichment-job-store.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c6 | 1 | LIGHT_PASS_WITH_NOTES | 16647117f7f7f7e7d1a66f35524900f0ea431e0d | 449fb48f264872402b40f14ef4b22f98a521e480 | 0 | — | 449fb48f264872402b40f14ef4b22f98a521e480 | 72a7bfd29bd265b7e1f6b230de381cb951d958ce | Resumable enrichment job store (V131) |
| c8 | docs/plans/2026-09-21/08-auto-enrichment.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c5,c6,c7 | 1 | LIGHT_PASS_WITH_NOTES | 449fb48f264872402b40f14ef4b22f98a521e480 | 8873dc96cd2799a49ee1bd055d5366c9e3a78ae0 | 0 | — | 8873dc96cd2799a49ee1bd055d5366c9e3a78ae0 | e946feecb31f6dff040cd2b1a7f446ebd6305804 | Auto enqueue + bounded worker + manual compensation |
| c9 | docs/plans/2026-09-21/09-discovery-throughput.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c1,c2,c3,c4,c8 | 1 | LIGHT_PASS_WITH_NOTES | 8873dc96cd2799a49ee1bd055d5366c9e3a78ae0 | f81f71f30ee6a9749aeba3556a0527b8ccf05901 | 0 | — | f81f71f30ee6a9749aeba3556a0527b8ccf05901 | 912742739a868fd140368c545d17a7b2c4ae017b | Fair per-source quotas, run caps, time budget, rollout runbook |
| c10 | docs/plans/2026-09-21/10-fulltext-yield.md | commit:831e6604cf97e7acba005d8f00827659b49ce010 | c2,c5 | 1 | LIGHT_PASS_WITH_NOTES | f81f71f30ee6a9749aeba3556a0527b8ccf05901 | e12c3471f89a9bc333ef8e7777703214abe29d5e | 0 | — | e12c3471f89a9bc333ef8e7777703214abe29d5e | afcca16f33bd892fa0f69d5609ad46395f122d4e | Bounded open-fulltext fallback + funnel accuracy |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
