# Child Brief c10 — 10 开放全文回退与漏斗准确性

## Identity

- Child ID: **c10**; approved plan: `docs/plans/2026-09-21/10-fulltext-yield.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c9 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c9 `Code head`); c1–c9 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c10/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperMetadata.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/EmailExtractionOutcome.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClient.kt` | production |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClientTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. No new ES field and no mapping change — only in-memory DTOs and the existing JSON statistics. Never edit an applied migration. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 回退有界**: try in the order PMC XML, primary OA PDF, other de-duplicated OA PDFs, DOI → Unpaywall open locations; at most 3 fulltext addresses per paper in total, a 90-second total per-paper deadline, and each URL attempted only once. Download public http(s) links only; never bypass a paywall or a CAPTCHA.
- **I-2 身份不漂移**: each open version only extracts the current paper's author emails and does not weaken c5's trusted-identity rules; without reliable attribution no academic ID is attached.
- **I-3 统计分层**: `EmailExtractionOutcome` gains a nullable `fulltextObtained` boolean (null = older adapter did not declare it, derive compatibly) plus `downloadFailureCategory`. HTML fetched but email-less counts as content obtained and is reported separately as `NO_EMAIL_IN_HTML`; explicitly state that HTML is not guaranteed to be the paper's full text. The 919 download failures must be bucketed by HTTP 403/404/429/5xx, TLS, timeout and invalid content.
- **I-4 兼容边界**: keep the 10 MB size limit and the default parse of the first 2 pages; do not blindly raise concurrency, PDF size or enable patent requests. Every new field on the shared result type must be defaulted so EuropePMC / CORE / arXiv / Crossref constructions keep compiling and their statistics keep their current meaning.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged (EuropePMC / PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-3: real identity and real document location only — a fallback version must not multiply an ambiguous authorship into trusted academic data.
- Master I-1: the per-paper deadline and attempt cap are their own constraints and must be reported as such, not as source exhaustion.

## Available outputs from earlier children

- c1: `OpenAlexRequestPolicy` — fulltext accounting exists at the policy boundary (`fulltextDownloadCount`), and c1's verifier recorded as O-1 that no production writer is wired yet; the OpenAlex fulltext download path is in this child's scope, so wire the accounting where the download actually happens instead of leaving a dead counter.
- c2: checkpoint/stop-reason vocabulary (`WINDOW_LIMIT`, `TIME_BUDGET`, …) and the per-source statistics aggregation.
- c5: trusted identity rules for author/email matching — do not relax them while adding fallback versions.
- c9: `SourceStats` funnel fields and the per-source summary that these new categories must appear in.

## Downstream interfaces later children consume (keep these exact)

- `PaperMetadata` gains a candidate-URL list defaulting to `emptyList()`; existing constructions elsewhere stay valid unchanged.
- `EmailExtractionOutcome` keeps its current fields and adds only defaulted ones; `ExpertDiscoveryService.consumeOutcome` must derive `fulltextObtained` per source compatibly (null → current behaviour).
- Per-paper accounting: one paper with two attempts and a successful second one counts `papersSearched += 1`, `fulltextObtained += 1`, `downloadAttempts += 2` — the funnel must never count one paper as two.
- The download-failure categories must be stable low-cardinality strings usable in the task `details_json` summary.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c10`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
