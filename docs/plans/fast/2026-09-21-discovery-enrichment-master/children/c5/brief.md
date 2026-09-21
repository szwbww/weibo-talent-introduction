# Child Brief c5 — 05 保留可信作者身份

## Identity

- Child ID: **c5**; approved plan: `docs/plans/2026-09-21/05-author-identity.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c4 code head recorded in the ledger (`docs/plans/fast/2026-09-21-…/ledger.md`, child c4 `Code head`); c1–c4 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c5/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration, never add an ES mapping field (the new sub-key rides inside the existing `externalIds` object, which is `enabled: false`). Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,CoreDataSourceTest,ExpertDiscoveryServiceTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 作者ID不是专家主键**: add a nullable DTO field `openAlexAuthorId`, normalized to `A` + digits, finally stored inside the existing `externalIds.openAlexAuthorId`; ES `_id` and the historical meaning of `orcidId` stay exactly as they are. Two DTOs gain the nullable defaulted field; keep constructor compatibility.
- **I-2 弱匹配不能带学术身份**: the existing first-initial/name-loose matches in `PdfEmailExtractor` and `CoreDataSource` must not bind an author ID or ORCID. Accept only original structured email attribution, a unique full-name combination hit, or a unique author with a unique email; ambiguous results keep the email but carry no author identity. PMC extraction results may only attach an OpenAlex ID through an exact ORCID match.
- **I-3 外部ID仅源读取**: `externalIds` is `enabled: false` — it can only be read from `_source`, never queried with `exists`/`term` on its sub-fields. Writing merges and preserves `doi`/`pmid`/`orcid` and the other import IDs; never guess when there is no reliable link, and never replace the whole `externalIds` object.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged (EuropePMC/PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-3: OpenAlex ID, ORCID and the ES `_id` are stored and used separately; an `externalIds` sub-key may only be read from `_source`.

## Available outputs from earlier children

- c1: the shared OpenAlex auth/budget (`OpenAlexRequestPolicy`, `OpenAlexBudgetDeferredException`) — the new author-ID writes change no request path.
- c2: checkpoint safety; do not weaken it while adding identity propagation (a page still advances only after RAW is persisted).
- c3/c4: Crossref/arXiv/CORE/ORCID adapters and the catalogue topics.

## Downstream interfaces later children consume (keep these exact)

- `externalIds["openAlexAuthorId"]` written for newly discovered experts is the trusted identity c6 reads first (`OpenAlexAuthorId` before `ORCID`) — the key name and the `A` + digits normalization are a cross-child contract. EMAIL-* experts keep their `EMAIL-*` `_id`; the value is never used as a document key.
- Keep the existing `PaperAuthor` / `AuthorEmail` constructor call sites compiling (nullable defaulted fields only, additive).
- Keep `ExpertDiscoveryService`'s existing writes to `toIndexMap` / `buildExternalIds` semantics: add the sub-key without dropping other keys; three-layer promotion still copies the whole `_source`.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c5`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
