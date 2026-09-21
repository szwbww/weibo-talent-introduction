# Child Brief c3 — 03 Crossref 与 arXiv 入口修复

## Identity

- Child ID: **c3**; approved plan: `docs/plans/2026-09-21/03-crossref-arxiv.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c2 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c2 `Code head`); c1 and c2 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c3/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/config/ArxivProperties.kt` | typed config |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | shared Crossref topic contract |
| `src/main/resources/application.yml` | env vars + defaults |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration. Cursor persistence belongs to c2's checkpoint layer — do not add database state to these adapters. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 编码==一次**: Crossref query/filter/cursor/mailto are each encoded exactly once and the final URI object is passed to `RestTemplate` — never a pre-encoded `String` through the String overload. Applies to the first page, continuation pages and keyword queries. Test the decoded parameters at the HTTP boundary, not only a mocked `getForObject(String)`.
- **I-2 HTTPS 与错误识别**: arXiv defaults to HTTPS; the official `http://` entry from legacy config is normalized to `https://`. HTTP 301 with an empty body, non-Atom payloads, Atom error entries and parse errors all fail explicitly — none may be reported as a normal zero-result page.
- **I-3 领域与现有入口**: keep arXiv `RND_TARGET` classification, manual keyword search and year filtering; keep Crossref manual-query behaviour. Wire the Crossref catalogue R&D topic queries in this child (so the intermediate build never crawls all fields unconditionally); c4 does the unified shard rotation. Keyword input from the operator always wins over the catalogue default.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged (EuropePMC/PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-3: real identity only — never treat a name/initial as a trusted academic identity (c5 tightens the weak matches; do not widen them here).
- `application.yml` default values override the typed `@ConfigurationProperties` defaults: when changing an arXiv default you must change both places, or the yml wins silently.

## Available outputs from earlier children

- c2 (complete): `discovery/service/DiscoveryCheckpointCodec.kt` and `SourceRunOutcome(resumeCursor, exhausted, stopReason)` own cursor durability. An arXiv page whose entries are all filtered out by publication year must still return the `nextCursor` derived from the raw entry count, so the c2 loop keeps paging; a genuine parse/HTTP failure must be a failure, not exhaustion.
- c1 (complete): OpenAlex-only auth/budget. Crossref and arXiv must not carry the OpenAlex bearer token (they use the generic `RestTemplate`).

## Downstream interfaces later children consume (keep these exact)

- `SubjectScopeCatalog.crossrefQueries(scope)` (per the plan's Task 1) is the single catalogue source for Crossref R&D topics; c4 adds the CORE topic queries and the rotating shard selection to the same catalogue — keep the API additive and do not encode shard state in the adapters.
- Keep every existing public method signature of `CrossrefDataSource` and `ArxivDataSource` that `ExpertDiscoveryService` already calls; `PaperSearchResult` is unchanged.
- Keep the RND_TARGET discipline list and the excluded-source behaviour untouched (EuropePMC / PMC OA exclusion lives elsewhere; do not re-enable them).

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c3`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
