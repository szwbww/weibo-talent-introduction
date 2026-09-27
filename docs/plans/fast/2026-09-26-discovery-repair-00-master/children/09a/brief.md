# Child 09a Brief — ORCID URI semantics and source-specific cursor key

Approved exact child plan: `docs/plans/2026-09-26/discovery-repair-09a-orcid-query.md`, committed with user-approved revised master/index at `152028fb4f6adf467a5254ed3627bf84c561f6bc` (A1). Read plan and shared `discovery-repair-09-scope-audit.md`; their exact current bytes govern. This brief is preparation only until child08 is verified terminal.

## Boundary and global constraints

- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`. Conditional product base: child08 implementation `9c6d84430ec0f5f25e3fa06468ad7dcde260fe67`, only upon fresh child08 LIGHT_PASS. Master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Preserve all verified 01–08 source/identity/qualification/renderer behavior and 09 master M1–M4/I1–I5; especially child01 page replay/stop semantics and child08 truthful funnel labels. No sending config, schema, hidden gate, existing source query semantics other than ORCID, clearance of historical rows/queues, migration, production requests, or 09b–09d scope classification/admission.

## Authorized Files — exact six

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

## Explicit invariants and acceptance

- I-1: ORCID search keeps current UTF-8 URLEncoder parameter encoding but constructs a final `java.net.URI` and uses RestTemplate URI overload (not String overload which double-encodes). Actual final `q` decoded exactly once equals original `queryShards` expression; no `%253A/%2522` from preencoding. Verify with real RestTemplate + MockRestServiceServer actual URI for `keyword:"engineering"`, `keyword:"computer science"`, Chinese, literal plus/percent/quotes; start/rows independent of q. Existing lookup-by-ORCID ID retains its query language and uses URI overload. Do not change global URI handler/other sources.
- I-2: Shared `DiscoveryCheckpointCodec.sourceCanonicalCriteria(sourceName, criteria)` adds exactly `;orcidQueryEncoding=uri-v1` to original canonical string only for ORCID. Both synchronous sourceKey and `ExpertDiscoveryService.queueQueryHash` use same function. Other sources' key bytes unchanged, generic canonicalCriteria and v2 envelope unchanged. Old ORCID key not inherited by new; new first cursor topic=0/offset=0, second run resumes own offset; avoid legacy queued cursor under old key. No cursor computed from papers_processed_total; manual explicit cursor retains old semantics.
- I-3: No deleting old checkpoint/stream/job, no old payload rewriting or global job epoch/hash change. Old queued job identity checks stay intact. This only fixes query and isolates ORCID key; specialized professional candidate admission occurs in 09d.
- Generate `target/discovery-plan-acceptance/09a.json` from actual isolated URI requests, decoded q, old/new key, first/next cursors and no-delete evidence; no hard-coded success placeholders. Required tests: `OrcidDataSourceTest`, `DiscoveryCheckpointCodecTest`, `ExpertDiscoveryServiceTest` including child01 failed-page replay scenario. Run focused JDK11 Maven test command for those three classes; Maven-configured Node phase included, no project-wide suite. User plan does not prescribe a single absolute JDK path; available `/Users/lukai/.jenv/versions/zulu64-11.0.15` and Maven `/opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn`.

## Downstream 09b contract

Keep existing query/filter/cursor APIs stable outside one new source-specific criteria function. 09b adds a single `researchFieldIds` field/mapping and must see 09a product as base; no schema/field/classification work in 09a.
