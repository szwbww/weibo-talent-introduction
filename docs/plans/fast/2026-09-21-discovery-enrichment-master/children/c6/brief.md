# Child Brief c6 — 06 定向补全与三层结果契约

## Identity

- Child ID: **c6**; approved plan: `docs/plans/2026-09-21/06-targeted-enrichment.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c5 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c5 `Code head`); c1–c5 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c6/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | production |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | pin-only test update |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration, never add an ES mapping field, never add an enrichment job table (c7 owns that). Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

**Pin-only rule for `OperatorStatusWriteSeamGuardTest.kt`:** that test pins exact line numbers of the seam sites in `ExpertSearchService.kt` (and other files). Adding or removing lines anywhere above a pinned site will trip it; you may update **only the recorded line numbers** to their new values so the same code snippet is asserted. Never relax, delete, or re-scope an assertion because the guard is inconvenient — if the seam set itself changed (a new operator-status write site), that is a design change and must come back as `PLAN_CONFLICT`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,OpenAlexDataSourceTest,ExpertRevalidationServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 身份与批次**: `enrichProfiles(profiles)` handles at most 100 distinct identities per batch; it uses the trusted `externalIds.openAlexAuthorId` first and a valid ORCID only when the former is absent; an `EMAIL-*` value is never passed as an ORCID; neither present means `NO_ID` and **no** author query is issued. Results are keyed by the real `esDocId`; an API id or ORCID is never a substitute for locating the database document.
- **I-2 三层局部更新**: `updateExpertAcademicFields` stays the only academic-field write point, doing a partial update by real `_id` on each layer that exists, skipping 404 and retrying non-404 errors; it returns per-layer results instead of a single `candidateUpdated` boolean, and it never overwrites existing values with null facts, never touches name/email/affiliation/operator status, and never creates a missing APPLICATION.
- **I-3 学术完成语义**: the base facts are hIndex / citation count / paper count / research direction / discipline / most recent publication year; the latest three paper titles are separately retryable and an empty result is not a request failure. `enrichedAt` alone never proves OpenAlex completion (ContactOut/Apollo imports write the same field). Patents stay off.
- **I-4 晋升保持门禁**: add a targeted revalidation method for RAW that reuses the current email, eligibility and classification gates; create a candidate only when neither CANDIDATE nor APPLICATION exists, never re-create a candidate for an already-applied expert, and do not auto-demote existing experts. Promotion reads the newest RAW source so a stale snapshot cannot overwrite it.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working (they must now go through the same core). M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged. M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-4: reuse `updateExpertAcademicFields` as the single academic write point; record failures per layer; RAW-only re-evaluation goes through the original gates; when APPLICATION exists no candidate is created.

## Available outputs from earlier children

- c1: `OpenAlexRequestPolicy` with `RequestKind`; the enrichment entry passes `HISTORY_ENRICHMENT` for the manual/backfill path (c8 adds `NEW_ENRICHMENT`), with release in `try/finally`. A `Permit.Deferred` / `OpenAlexBudgetDeferredException` must surface as the identifiable deferred outcome, not a retryable error.
- c5: trusted author identity — `externalIds.openAlexAuthorId` (`A` + digits) is written for newly discovered experts and is the first identity source here; ORCID second.

## Downstream interfaces later children consume (keep these exact)

- The plan's outcome type is a cross-child contract: `ProfileEnrichmentOutcome` variants `Success, Partial, Deferred, NotFound, NoId, RetryableError`. c7 (job store) persists exactly these variants as `SUCCEEDED` / partial / `RETRY_WAIT` (quota defers without consuming an attempt) / `UNMATCHED` / `FAILED`; adding or renaming a variant here breaks that mapping.
- `enrichProfiles(profiles, requestKind)` and `findByDocumentIds(level, ids)` are the entry points c7/c8 call; keep their names and shapes.
- `LayerUpdateResult` (per-layer outcome) is what c7 stores in `result_json`; keep it serializable with stable field names.
- `revalidateEnrichedRaw(docId): PromotionOutcome` returning `AlreadyPresent` when CANDIDATE or APPLICATION exists is what c8 calls after a successful enrichment; keep the name/semantics.
- Existing readers of the academic fields (`ExpertSearchService.sourceFields/toExpertProfile`, index DTOs, `CandidateEligibilityService`, `ExpertClassificationService`, batch-send filters, `AiReplyContextBuilder`) must see the same facts — never create a parallel enrichment field set.
- The existing manual entry and its three scopes must keep working, including the `TaskProgressStore` progress/token usage.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c6`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
