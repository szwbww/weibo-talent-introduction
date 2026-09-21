# Child Brief c4 — 04 CORE、ORCID 分页与研发范围

## Identity

- Child ID: **c4**; approved plan: `docs/plans/2026-09-21/04-core-orcid-scope.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c3 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c3 `Code head`); c1–c3 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c4/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | data contract |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration and do not add columns to `discovery_source_cursor` — the CORE/ORCID cursors ride inside the existing `cursor_value` envelope owned by c2's codec. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CoreDataSourceTest,OrcidDataSourceTest,CrossrefDataSourceTest,SubjectScopeCatalogTest,ExpertDiscoveryServiceTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 稳定进度不用瞬时 scroll**: CORE switches to the measured `offset`/`limit` protocol; the cursor stores the query shard and offset and never a `searchId`/`scrollId`. A full page advances by the raw returned count; an empty page or the real last page ends the shard. Never claim offset gives snapshot consistency on a moving source.
- **I-2 ORCID 按原始记录翻页**: add a search-result wrapper returning `rawRecordCount`, `nextCursor` and the records that carry public emails. A whole page with no public email must not terminate the search or freeze the offset. When the scheduled keyword is empty, use the scope topic seeds; an operator-supplied keyword always wins.
- **I-3 统一研发意图**: the topic catalogue keeps the six R&D topic classes; CORE queries use explicit parenthesised `OR`; Crossref rotates over catalogue topics and keeps per-source/topic audit; ORCID seeds use public research keyword fields and never treat company names or affiliation addresses as nationality. Source keywords are search constraints only — never claim they equal OpenAlex classifications. EuropePMC / PMC OA stay excluded exactly as today.
- **I-4 边界停止可见**: when a CORE offset reaches the verified vendor window, stop that shard and record `WINDOW_LIMIT`; never rename `searchId` into a cursor and never increment offsets without bound. Every request keeps its timeout/cancel/quota handling.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged (EuropePMC/PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-2 (先持久化再推进) applies to the new CORE/ORCID cursors: only a fully consumed page persists its next cursor.

## Available outputs from earlier children

- c2: `DiscoveryCheckpointCodec` owns the `SOURCE:v2:<24-char hash>` key and the versioned `ACTIVE`/`EXHAUSTED` cursor envelope, and `nonPersistableCursorSources` currently still excludes `CORE`; completing CORE persistence here is exactly the intended follow-up — reuse the codec, do not add a parallel encoding.
- c3: `SubjectScopeCatalog.crossrefQueries(scope)` already exists and Crossref honours operator keywords; add the CORE topics and the shard rotation to the same catalogue instead of a second topic list.
- c1: OpenAlex-only auth/budget. CORE, ORCID and Crossref use the generic `RestTemplate` and must never carry the OpenAlex bearer token.

## Downstream interfaces later children consume (keep these exact)

- Cursor envelope shape (`ACTIVE`/`EXHAUSTED`, offset/shard/topic/year payload) is what c9's fair-quota run reads to decide the next page and to resume after a stop.
- Source stop reasons (`WINDOW_LIMIT`, exhausted, budget/time) feed the shared terminal-status decision function from c2 and the summary that c9 reports — keep the strings explicit.
- `SubjectScopeCatalog` stays the single topic SSOT (six R&D classes) that c9's per-source quotas and c10's extraction paths rely on; keep its API additive.
- Do not change `PaperSearchResult` semantics or the ORCID record shape used by the discovery loop beyond the additive page wrapper.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c4`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
