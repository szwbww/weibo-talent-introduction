# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: f0c41271fc56d7455e14d28a71d563a5341dfdeb
- Current/final code head: e12c3471f89a9bc333ef8e7777703214abe29d5e
- Branch/worktree: fast/2026-09-21-discovery-enrichment-master / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master

## Child Status

| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---:|---|
| c1 | LIGHT_PASS_WITH_NOTES | f0c41271fc56d7455e14d28a71d563a5341dfdeb..147dc953a194a27ea73b7934a8e2bc334beca655 | 0 | a8a7be6f795e1ad2464984675360ffec3f3290c2 |
| c2 | LIGHT_PASS_WITH_NOTES | 147dc953a194a27ea73b7934a8e2bc334beca655..468df56bf69b4b2f9afc7b4f38ad4791d8331240 | 0 | 69bdb7dcec2663df605f726279f0cefec5f6e685 |
| c3 | LIGHT_PASS_WITH_NOTES | 468df56bf69b4b2f9afc7b4f38ad4791d8331240..fba6173efd061aef73ebbce1854f83decb52a84f | 0 | 915e542b8841c7496844cced0722e9453293eeeb |
| c4 | LIGHT_PASS_WITH_NOTES | fba6173efd061aef73ebbce1854f83decb52a84f..985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b | 0 | 5ed8de19c84b68d42f42b1e621a705dbd08f35c1 |
| c5 | LIGHT_PASS | 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b..1ba685217a166628968a798450ff203191ead79c | 0 | 04cd01039f71e157ab5c25bafe959551a4f2e3c3 |
| c6 | LIGHT_PASS_WITH_NOTES | 1ba685217a166628968a798450ff203191ead79c..16647117f7f7f7e7d1a66f35524900f0ea431e0d | 0 | 65429bfc829ef5828d37ab6a072c5c6fba2f088c |
| c7 | LIGHT_PASS_WITH_NOTES | 16647117f7f7f7e7d1a66f35524900f0ea431e0d..449fb48f264872402b40f14ef4b22f98a521e480 | 0 | 72a7bfd29bd265b7e1f6b230de381cb951d958ce |
| c8 | LIGHT_PASS_WITH_NOTES | 449fb48f264872402b40f14ef4b22f98a521e480..8873dc96cd2799a49ee1bd055d5366c9e3a78ae0 | 0 | e946feecb31f6dff040cd2b1a7f446ebd6305804 |
| c9 | LIGHT_PASS_WITH_NOTES | 8873dc96cd2799a49ee1bd055d5366c9e3a78ae0..f81f71f30ee6a9749aeba3556a0527b8ccf05901 | 0 | 912742739a868fd140368c545d17a7b2c4ae017b |
| c10 | LIGHT_PASS_WITH_NOTES | f81f71f30ee6a9749aeba3556a0527b8ccf05901..e12c3471f89a9bc333ef8e7777703214abe29d5e | 0 | afcca16f33bd892fa0f69d5609ad46395f122d4e |

## RECORD_ONLY Index

| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1: I-3's "list calls and fulltext downloads are counted separately" exists only at the policy boundary (`fulltextDownloadCount()` OpenAlexRequestPolicy.kt:99, incremented when `X-RateLimit-Credits-Used >= 100` at :155-157, covered by `list calls and fulltext downloads are counted and charged separ | c1 | recorded in children/c1/verify-log.md | children/c1/verify-log.md |
| O-1: because `TaskExecutionService` now adopts the always-non-null `DiscoveryResult.taskFinalStatus` (TaskExecutionService.kt:135-143) instead of deriving from counts, expert-level fault counters no longer influence the recorded terminal status: dedup lookups failing for every candidate leave `sourc | c2 | recorded in children/c2/verify-log.md | children/c2/verify-log.md |
| O-1: the arXiv request URL is still a pre-encoded `String` handed to RestTemplate's template overload (`ArxivDataSource.kt:41` `URLEncoder.encode(it)` into `:53-62` `exchange(url, ...)`), so for any non-ASCII keyword the wire carries a second encoding: a jshell probe of RestTemplate's default handle | c3 | recorded in children/c3/verify-log.md | children/c3/verify-log.md |
| O-2: `src/test/resources/application.yml:120` still defaults `arxiv.base-url` to `${ARXIV_BASE_URL:http://export.arxiv.org/api}` while production `application.yml:224` and `ArxivProperties.kt:11` are https; that file is outside the brief's 8-file list, so it cannot be repaired under this child, and  | c3 | recorded in children/c3/verify-log.md | children/c3/verify-log.md |
| O-3: Crossref encoding departs from the literal Task-1 snippet in the approved plan — `CrossrefDataSource.kt:63-70,84` encode each value once with `URLEncoder` and declare it encoded via `build(true)`, instead of `.build().encode().toUri()`. Spring's `encode()` leaves a literal `+` in a query value  | c3 | recorded in children/c3/verify-log.md | children/c3/verify-log.md |
| O-1: the Crossref clause of I-3 is unmet in its literal form and has no code or test evidence. Plan 04 I-3 states "Crossref 针对目录主题轮换检索并保留来源/主题审计", `docs/plans/2026-09-21/03-crossref-arxiv.md:32` defers "04 再统一分片轮换", and the brief's I-3 states "Crossref rotates over catalogue topics and keeps per-sou | c4 | recorded in children/c4/verify-log.md | children/c4/verify-log.md |
| O-2: CORE operator keywords keep AND semantics while the plan's Task-1 snippet shows `terms.joinToString(" OR ")`: `CoreDataSource.kt:131-136` applies OR only to the catalogue topic group and `AND` to `criteria.keywords` (asserted by `CoreDataSourceTest` `lets operator keywords win and keeps their A | c4 | recorded in children/c4/verify-log.md | children/c4/verify-log.md |
| O-3: ORCID now has two entry points — `searchOrcidPage` (paging) and `searchOrcidRecords` (record view, `OrcidDataSource.kt:130` delegating to the page call) — because `ExpertDiscoveryService.tryGetEmailFromOrcid` (`:1504`) and the out-of-list `DiscoveryMockHelper.java:180-184` consume the record vi | c4 | recorded in children/c4/verify-log.md | children/c4/verify-log.md |
| O-4: `sourceStats.apiRequests` is incremented before the call (`ExpertDiscoveryService.kt:497` generic loop, `:738` ORCID loop), so the explicit skip path (`OrcidDataSource.kt:85-89`) reports `apiRequests=1` with zero HTTP requests; the pre-patch blank-query skip incremented the counter at the same  | c4 | recorded in children/c4/verify-log.md | children/c4/verify-log.md |
| N/A | c5 | recorded in children/c5/verify-log.md | children/c5/verify-log.md |
| O-1: the I-4-mandated single-gate extraction reorders the pre-existing `promoteEligibleRawExperts` sequence from eligibility -> classification -> CANDIDATE existence -> email to eligibility -> classification -> email -> CANDIDATE existence (ExpertRevalidationService.kt:267-292 vs the removed block i | c6 | recorded in children/c6/verify-log.md | children/c6/verify-log.md |
| O-1: pre-existing red inside the required command - `FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger` (FlywayMigrationIntegrationTest.kt:603-614) inserts `expert_contact_id = 1` after `clean()+migrate()` with no seeded `expert_contact` row, so it hits `CONSTRAINT | c7 | recorded in children/c7/verify-log.md | children/c7/verify-log.md |
| O-2: the schedule columns are naive `DATETIME(3)` and `Deferred.resetAt` is persisted as `LocalDateTime.ofInstant(resetAt, ZoneId.systemDefault())` (Service.kt:127-130, mirrored by the IT assertion), so the store's clock convention is the JVM default zone: c8 must call `claimDue`/`renewLease` with ` | c7 | recorded in children/c7/verify-log.md | children/c7/verify-log.md |
| O-1: The automatic batch's `task_execution.result_summary` is `AutoEnrichmentBatchResult`, whose JSON keys are `claimed/succeeded/pending/unmatched/failed/...`, but the shared `EXPERT_ENRICHMENT` summary rule reads `enriched` (`TaskExecutionSummaryExtractor.kt:96-104` and `:160-168`), so an automati | c8 | recorded in children/c8/verify-log.md | children/c8/verify-log.md |
| O-2: Idle ticks still persist one progress-log row each: `tryStartWithToken` writes the log on every successful start (`TaskProgressStore.kt:145-155`) at `Worker.kt:50`, before `runBatch` returns for an empty claim (`Worker.kt:82-85`), so the claim at `Worker.kt:19-20` / execution.md deviation 5 tha | c8 | recorded in children/c8/verify-log.md | children/c8/verify-log.md |
| O-1: `src/main/resources/application.yml:248` (`PMC_OA_API_KEY:6c4864…`) and `:259` (`CORE_API_KEY:ZoMR12…`) still hold literal API-key *values* as placeholder defaults, and the c9 hunk edits the same CORE/PMC-OA blocks. Byte-identical at base 8873dc9, so this is pre-existing and outside this child' | c9 | recorded in children/c9/verify-log.md | children/c9/verify-log.md |
| O-1, I-1 "总计每篇最多3个全文地址" is implemented as at-most-3 **URLs**; the PMC XML step is an extra pre-chain fetch, so a PMC paper can make 1 XML + up to 3 URL attempts (OpenAlexDataSource.kt:86-87 and :119) — the plan's own sketch (`take(3)` over the URL list, XML handled before the loop) supports this rea | c10 | recorded in children/c10/verify-log.md | children/c10/verify-log.md |
| O-2, the brief's `downloadAttempts += 2` has no own `details_json` key: attempts are folded into the existing `apiRequests` counter, which also carries the page-search request and the Unpaywall lookup (`OpenAlexDataSource.kt:129-131`, `ExpertDiscoveryService.kt:1157-1159`; the test asserts apiReques | c10 | recorded in children/c10/verify-log.md | children/c10/verify-log.md |
| O-3, the plan Task checklists' "write the failing test first and run it red" was not performed (execution.md Deviations): the new tests reference fields/parameters that do not exist at base, so they cannot compile against pre-change code; the gaps they pin are the ones the plan's own 现状审计 records an | c10 | recorded in children/c10/verify-log.md | children/c10/verify-log.md |
| O-4, the shared 90-second deadline bounds the URL download attempts only: the PMC XML fetch (`OpenAlexDataSource.kt:100`; `EuropePmcDataSource` is not an authorized file) and the Unpaywall lookup (`:129-131`, no deadline argument) run outside it, bounded by their own HTTP timeouts; matches the plan  | c10 | recorded in children/c10/verify-log.md | children/c10/verify-log.md |

## High-attention items for this review

- c4 O-1: plan 04 I-3's "Crossref 针对目录主题轮换检索并保留来源/主题审计" is not implemented — all six catalogue topics are joined into one `query.bibliographic` per Crossref call. Plan 03 defers rotation to plan 04; the verifier judged the approved bytes to determine no unique repair, so it is recorded, not fixed.
- c9 O-1: `src/main/resources/application.yml:248` (`PMC_OA_API_KEY`) and `:259` (`CORE_API_KEY`) still carry literal API-key default values. This pre-dates the run and the c9 hunk edits the same CORE/PMC-OA configuration blocks, so a human should decide whether that is acceptable or a secret that must be rotated and removed.
- c10 O-2/O-4: the per-paper download-attempt counter and the 90-second deadline cover only the URL download legs, not the PMC XML fetch or the Unpaywall lookup; the "at most 3 fulltext addresses" cap is counted as URLs with the PMC XML step outside it.
- c1/c7/c8/c10 environmental notes: the container-backed integration tests need `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` plus `-Dapi.version=1.40`, and `FlywayMigrationIntegrationTest.V124` is red at every tested revision (pre-existing, base-reproduced). The full `mvn test` integration gate was NOT run for this workflow.
- c8/c9/c10 O items and the c4/c7 observations are display/telemetry and convention notes; none of them changed the four-gate verdicts.

## Pause/Resume

- Reason: N/A
- Resume from: N/A

No whole-system verification was performed.
