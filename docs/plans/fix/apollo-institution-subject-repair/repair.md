# Repair Plan: apollo-institution-subject-repair

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/2026-10-19/apollo-institution-subject-repair.md`

Baseline identity: sha256 `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397`; amendments N/A. The master explicitly includes the ordered country child, `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/2026-10-19/discovery-institution-country-supplement.md`, sha256 `3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67`.

Verification report: the `aggregate/master` verify-p report returned by the independent aggregate reviewer on 2026-10-20; controller report destination `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/review/apollo-institution-subject-repair/machine-verification.md`.

Verification result: FAIL; convergence INITIAL. No earlier aggregate repair or approved amendment exists. Child light-review RECORD_ONLY observations are evidence, not approved waivers of the baseline.

Implementation boundary: `fcd6719ecd03645284c3df0f8a6b95bf4686ba95..0953b2f3570725bf1de5cc138422220c1731a48d`; evidence HEAD `1b7e1a84233dd781a62b492276b83c6fc96632cc` on branch `fast/apollo-institution-subject-repair`.

Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair`. Every relative path below is anchored to this exact worktree.

## Objective

Restore the approved institution identity boundaries, per-record read tolerance, Worker error dispositions, and country supplement selection/cancellation/bounded-receipt behavior. Repair only the eight independently reproduced mandatory violations; preserve the working registration, legacy, admission, budget, and projection paths.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Institution I-2; T-1 steps 4/5 | `DiscoveryInstitutionNormalizer.kt:374-395,412-422` treats any alphabetic tail followed by a country as an address. Both `University of California, Los Angeles, USA` and `University of California, Berkeley, USA` become `University of California`; actual Rules returns APOLLO_UNCLEAR across those campuses. |
| V-2 | P1 | Institution T-1 standard-ID/explicit-alias contract; T-2 shared comparisons | `DiscoveryInstitutionNormalizer.kt:232-242` retains only institution names in derived records; `DiscoveryVerificationRules.kt:111-118` uses those records without original ID/alias metadata. ORCID ROR:1 and Apollo ROR:2 at the same name change from legacy PENDING to APOLLO_UNCLEAR. The metadata-aware compare overload is not used by this runtime branch. |
| V-3 | P1 | Country I-1; T-1 full-name equality; acceptance I-1 | `DiscoveryCountrySupplementService.kt:245-252,313-315,442` reuses the subject canonicalizer, which removes punctuation. A profile at `Example University-A` receives US from the different complete name `Example University A`, despite the contract permitting only NFKC, whitespace, and case normalization. |
| V-4 | P1 | Institution I-5; T-3 per-item damaged-context tolerance | `DiscoveryVerificationQueryService.kt:103,131-141` deserializes evidence before entering its controlled catch. `institutionNormalization.inputs:null` causes MissingKotlinParameterException to escape `record`, and records GET fails instead of retaining the row with institutionSubject missing. |
| V-5 | P1 | Country I-5; T-2/T-3 cancellation propagation | `DiscoveryCountrySupplementService.kt:138-146,296-300,462-469` recognizes only interruption; CancellationException becomes ERROR/HTTP_ERROR. Registration's catch at `DiscoveryVerificationRegistrationService.kt:123-132` would also wrap a rethrown cancellation as ordinary registration failure. The discovery loops then continue. |
| V-6 | P1 | Country T-1 observation limit; I-4/I-5 skip without blocking collection | `DiscoveryCountrySupplementService.kt:149-150,176` changes the reason to EVIDENCE_TOO_LARGE but retains an observation whose institution exceeds 2000 characters. Registration correctly rejects it at `DiscoveryVerificationRegistrationService.kt:193-194`, so a supposed SKIPPED supplement instead prevents registration. A 2003-character institution reproduced the rejection. |
| V-7 | P1 | Institution T-2 invalid-protocol VERIFICATION_FAILED; T-3 lawful parser-drift EVIDENCE_CONFLICT | `DiscoveryVerificationWorker.kt:86-95` combines all normalization validation failures into the parser-drift fallback. An INST_SUBJECT_V9 DECIDE payload produces a COMPLETED/PENDING commit with EVIDENCE_CONFLICT instead of the required ERROR/VERIFICATION_FAILED. This is child 01 RECORD_ONLY O-1, re-evaluated against the distinct triggering conditions. |
| V-8 | P1 | Country T-1 same-institution/same-country deduplication; I-1/O-2 truthful selection reason | `DiscoveryCountrySupplementService.kt:256-260,343-350` compares the number of distinct countries with the number of raw records. Identical OpenAlex US duplicates and two valid current ORCID GB employments both become INVALID_COUNTRY. This is child 02 RECORD_ONLY O-1, also reproduced on OpenAlex. |

All eight are introduced or behaviorally exposed within the reviewed implementation boundary. The probes establish conditional behavior, not observed online incidence. V-2 applies when the original caller/persisted search contains standard IDs or explicit aliases; the current Apollo HTTP client ordinarily emits names only, but the approved contract explicitly preserves those metadata semantics.

## Findings Excluded

| Finding | Reason |
|---|---|
| Prior child 02 F-1 original-token signing | Original JATS/OPENALEX/ORCID token prefix is now retained at `DiscoveryCountrySupplementService.kt:155-161`; fresh required tests cover the original trigger. No redundant repair. |
| Prior child 02 F-2 Spring wiring | The service is registered at `DiscoveryCountrySupplementService.kt:101,109-111`; the existing container wiring test passed freshly. |
| Prior child 02 F-3 InterruptedException trigger | The exact interruption trigger now escapes registration and retains the interrupt flag. V-5 concerns the still-unhandled CancellationException case and does not undo that fix. |
| Real-cohort effect measurement and human acceptance | Institution identity impact and country effectiveness remain NOT_EVALUATED without complete real frozen evidence. The baseline permits this explicit limit. Institution A-1..A-6 and country A-1..A-5 remain human work; no synthetic result may be promoted to a real success rate. |
| Docker default API incompatibility | The first unconfigured institution IT run failed because API 1.32 was below the daemon's minimum 1.40. Both IT suites then passed using the existing documented OrbStack socket and `-Dapi.version=1.40`. No repository/build change is required. |
| Optional cleanup, extra platform support, live rollout/backfill | Outside the confirmed findings and approved task. |

## Unchanged Contract

- Apply institution repairs before the country repairs, serially in this worktree. The file list below is a subset of the union of the two approved child whitelists, not an amendment of either child.
- Preserve original institution/employment, source facts, provider responses, bound identities, name/ORCID constraints, qualification trust levels, and APOLLO_UNCLEAR admission through the existing Gate/Commit.
- Preserve legacy null/absent institutionNormalization semantics, old APOLLO_SEARCH URI/page/queryIndex, old DECIDE behavior, request budgets, pagination completeness, lease CAS, and evidence retention.
- Keep new normalization revalidation strict in Rules/Gate. Query tolerance cannot grant admission or rewrite saved outcomes.
- Country can change only once before the first new reservation freezes, together with its original-source token. Keep existing-country values verbatim, protection checks before HTTP, frozen RESERVED recovery, source/fact checks, concurrent winner consumption, and both discovery consumers' preparedProfile path.
- Country receipts remain optional COUNTRY_V1 audit metadata, outside qualification evidence and Apollo attempt/request counters. Maintain one request maximum, dedicated 2s/3s transport limits, 2 MiB body bound, one concurrent request, rolling 10/minute and 100/hour limits, immediate budget skips, and 429 pauses.
- No new table, migration, ES field, JSON protocol member, policy version, business state, provider, framework, dictionary, fuzzy matching, LLM, scheduled job, or UI/CSS change. Do not modify Repository, Commit, ES writer, sampling implementation/configuration, or HTTP clients outside the authorized country service.
- Do not deploy, restart a live task, backfill, replay live items, rewrite historical snapshots, clear receipts/admissions, alter quotas, or change plan/knowledge/ledger/review artifacts during implementation.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt` | V-1 conservative extraction; V-2 only if its existing comparison helper must be used/corrected without protocol expansion. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | V-2 preserve source-bound IDs/aliases in the actual contextual runtime comparison. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | V-4 contain malformed new-context deserialization per record. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | V-7 distinguish invalid saved protocol from lawful parser-input drift. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementService.kt` | V-3 strict complete-name comparison; V-5 cancellation; V-6 bounded skipped receipt; V-8 value-level deduplication. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | V-5 propagate cancellation without ordinary registration-failure wrapping; preserve all freeze/protection logic. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt` | V-1 discriminating campus/address regressions in the existing test class. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | V-1/V-2 actual contextual Rules regressions. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` | V-4/V-7 real SQL read/failure-disposition regressions. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementServiceTest.kt` | V-3/V-5/V-6/V-8 focused service regressions. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt` | V-5 registration cancellation and V-6 oversized-evidence continuation regressions. |

No other implementation, test, resource, build, config, or documentation file is authorized by this repair. Reuse existing fixture helpers; no new fixture file is required.

## Repair Tasks

### R-1: Preserve campus and unproven location qualifiers

- Resolves: V-1.
- Root cause: alphabetic text before a recognized country is treated as proven address data.
- Files: Normalizer, NormalizerTest, RulesTest from Authorized Files.
- Change: retain a complete campus subject when it can be established; otherwise return UNRESOLVED/AMBIGUOUS. Do not drop a qualifier merely because it contains letters and precedes a country. Keep the mandatory Gachon/Seoul minimum examples and explain removed fragments using the declared rules.
- Regression test: LA/USA and Berkeley/USA must never compare MATCH or cause cross-campus APOLLO_UNCLEAR; `Department of Physics, Example University, Overseas Campus, USA` must not silently become the parent institution. Exercise actual contextual Rules as well as extraction.
- Existing verification: commands 1 and 2 below.
- Must not change: original strings, identity/qualification requirements, query budgets, minimum accepted fixtures.
- Prohibited: a new gazetteer, alias expansion, substring matching, provider calls, or invented institution names.

### R-2: Preserve original institution metadata in contextual comparisons

- Resolves: V-2.
- Root cause: derived name records replace source-bound standard IDs/aliases in the matching path.
- Files: Rules, existing Normalizer comparison helper only if needed, RulesTest.
- Change: use the original VerificationInstitution/evidence metadata together with validated subjects. Same-namespace ID conflicts cannot match by name; proven same IDs and explicit source-bound aliases retain the approved semantics. Do not manufacture IDs/aliases or add persistence members.
- Regression test: with an otherwise valid identity and complete search, ORCID ROR:1 versus Apollo ROR:2 at the same name must not match; include the existing approved same-ID/explicit-alias positive behavior through the contextual Rules path, not only the helper overload.
- Existing verification: commands 1 and 2.
- Must not change: name/ORCID uniqueness, legacy comparison behavior, context recomputation, Gate consistency.
- Prohibited: accepting saved subjects as authority or solving this by removing ID-bearing inputs.

### R-3: Contain malformed normalization in read-only per-item rendering

- Resolves: V-4.
- Root cause: the payload is deserialized before the per-item exception boundary.
- Files: QueryService, VerificationFlowIT.
- Change: contain malformed institutionNormalization shapes during per-item parsing/rendering so records retain their saved state/outcome and report institutionSubject missing. Keep Gate parsing strict and do not silently rewrite evidence or outcomes. Do not turn unrelated database/snapshot failures into fabricated empty success.
- Regression test: persist an otherwise valid completed item with `institutionNormalization.inputs:null`; records GET must return that row with institutionSubject missing, the saved outcome unchanged, and no revision/attempt writes. Gate must still reject that evidence.
- Existing verification: commands 1, 2, 3, and 4.
- Must not change: GET read-only behavior, DTO output contract, per-item saved authority.
- Prohibited: broad list omission, fake admission, or swallowing SQL errors.

### R-4: Separate invalid protocol errors from lawful parser drift

- Resolves: V-7 (child 01 RECORD_ONLY O-1).
- Root cause: one catch converts all normalization validation failures into the drift disposition.
- Files: Worker, VerificationFlowIT.
- Change: unknown versions and corrupted saved context must use the existing ERROR/VERIFICATION_FAILED path and retain the evidence. Preserve PENDING/EVIDENCE_CONFLICT only for the specifically approved lawful parser-rebuild input drift; no HTTP or silent context upgrade in that case.
- Regression test: persisted INST_SUBJECT_V9 in DECIDE becomes ERROR with VERIFICATION_FAILED and retains normalization; a valid saved context whose lawful parser rebuild changes the frozen facts remains PENDING/EVIDENCE_CONFLICT without requests. These paired triggers distinguish the two existing contract branches.
- Existing verification: commands 1, 2, 3, and 4.
- Must not change: legacy null-context behavior, failure payload retention, request counters, lease/CAS, admission safeguards.
- Prohibited: new business states, exception-message heuristics as protocol authority, or automatic rebuilding of damaged saved context.

### R-5: Use strict complete-name equality for country selection

- Resolves: V-3.
- Root cause: the country selector imports punctuation-removing subject comparison semantics.
- Files: CountrySupplementService, CountrySupplementServiceTest.
- Change: compare complete institution names using only NFKC, whitespace collapse, and case normalization, while retaining the required RESOLVED/boundary checks. Apply the same conservative comparison to OpenAlex and ORCID selection and conflict/dedup handling. Keep subject matching's separate approved normalization behavior unchanged.
- Regression test: `Example University-A` versus `Example University A` must not fill or fall through a known OpenAlex conflict to another source; whitespace/case/NFKC-only equivalents may still fill when all other evidence is valid.
- Existing verification: commands 3 and 4.
- Must not change: author/ORCID binding, existing-country preservation, source/token semantics.
- Prohibited: removing punctuation, country guessing, fuzzy matching, or global changes to subject canonicalization merely to fix country matching.

### R-6: Preserve cancellation through supplementation and registration

- Resolves: V-5.
- Root cause: cancellation recognition handles interruption only; both service and registration ordinary-failure catches otherwise consume cancellation.
- Files: CountrySupplementService, RegistrationService, CountrySupplementServiceTest, CountryRegistrationTest.
- Change: preserve CancellationException and wrapped cancellation through both boundaries with their original cancellation semantics. Preserve existing InterruptedException/interrupt-flag behavior. A cancelled discovery must not return an ERROR receipt and continue collecting.
- Regression test: inject CancellationException during the bounded request; supplement and reserve must propagate cancellation, reserve must not insert an item, and the existing interruption and timeout tests must retain their distinct behavior.
- Existing verification: commands 3 and 4.
- Must not change: ordinary timeout/HTTP failures continuing the original collection flow, permit release in finally, failure accounting.
- Prohibited: converting cancellation to a normal registration failure, returning a successful profile after cancellation, or changing global discovery exception policy.

### R-7: Make oversized-evidence skips valid registration inputs

- Resolves: V-6.
- Root cause: the service chooses SKIPPED but retains an over-limit observation rejected by its downstream validator.
- Files: CountrySupplementService, CountrySupplementServiceTest, CountryRegistrationTest.
- Change: decide EVIDENCE_TOO_LARGE before any fill, retain the original profile/token, and produce an audit receipt within every approved bound. Omit over-limit observation text or otherwise use the already permitted bounded audit fields; do not truncate input and then continue country selection. Keep registration's bounds strict.
- Regression test: one matching OpenAlex institution over 2000 characters yields SKIPPED/EVIDENCE_TOO_LARGE, null country, original token, bounded observations, and a successfully frozen registration rather than DiscoveryRegistrationException. Keep the existing >20 observations and >2 MiB response cases passing.
- Existing verification: commands 3 and 4.
- Must not change: 20-observation/2000-character limits, signature rules, final-profile equality, SQL failure propagation.
- Prohibited: raising/removing limits, weakening validation, or treating a SQL failure as a supplement skip.

### R-8: Deduplicate valid countries before deciding ambiguity

- Resolves: V-8 (child 02 RECORD_ONLY O-1 and the same defect on OpenAlex).
- Root cause: distinct-country count is incorrectly compared to raw observation count.
- Files: CountrySupplementService, CountrySupplementServiceTest.
- Change: validate each country's presence/recognized code, then decide from distinct valid country values for the already bound complete institution. Identical duplicates must not become INVALID_COUNTRY. Continue to reject actual multiple countries, invalid/missing countries, inconsistent authors, and different institutions.
- Regression test: identical bound OpenAlex US duplicates and two valid current ORCID GB entries fill US/GB with 0/1 requests respectively; actual US/GB disagreement remains a conflict and missing/invalid codes never fill.
- Existing verification: commands 3 and 4.
- Must not change: one-request maximum, no cross-source conflict laundering, source provenance, receipt bounds, qualification isolation.
- Prohibited: first-country selection or discarding a conflicting observation to manufacture unanimity.

## Verification Commands

Use JDK 11 and a single build target. Both IT commands use the existing documented OrbStack/Docker API compatibility configuration; do not change repository config to obtain it.

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`
2. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`
4. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test`

Before this repair, the fresh aggregate runs passed 69/49/83/47 JVM tests respectively, each with zero failures/errors/skips and 1724 passing JS tests. Passing those suites did not cover the eight discriminating triggers above. The initial unconfigured command 2 failed with three environment initialization errors and was superseded by its configured successful run; do not suppress that history in reports.

## Completion Criteria

- Every V-1..V-8 trigger has the required corrected observable behavior and a discriminating regression in the named existing suite; do not alter a test oracle to preserve the current violating behavior.
- Rules, Gate, Worker, QueryService, registration, and RAW/candidate consumption continue to satisfy the full original two-child contract matrix; no ID/alias, cancellation, or error-state requirement is silently downgraded.
- All four commands pass freshly after the final implementation with no skipped required SQL tests. Re-run the full matrix before requesting independent verification.
- Changed files remain inside the exact Authorized Files list. No new persistent field/state/schema, unrelated cleanup, live data mutation, deployment, history rewrite, or additional repair artifact exists.
- Real-cohort impact remains NOT_EVALUATED unless complete authorized frozen evidence is supplied. The eleven original human acceptance items remain PENDING; this repair does not perform or replace them.
- Stop after the authorized repair execution report and independent verification handoff. Do not merge, deploy, start another repair round, or amend the baseline automatically.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.

After approval, run `execute-p` with this exact file. A later human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fix/apollo-institution-subject-repair/repair.md` approves this artifact's current contents and only its Authorized Files.

## Review-Fast-P Execution Handoff

Selected review worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair`.

Selected branch: `fast/apollo-institution-subject-repair`.

Master/review slug: `apollo-institution-subject-repair`.

Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fix/apollo-institution-subject-repair/repair.md`.

Execution handoff: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/review/apollo-institution-subject-repair/repair-execution.md`.

Return-to-review entry: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fast/apollo-institution-subject-repair/human-review-handoff.md`.

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fix/apollo-institution-subject-repair/repair.md` invocation authorizes:

1. Only the eleven product/test Authorized Files and the required verification commands in this plan. Bind the execution to this repair artifact's actual SHA256 and the selected worktree/branch; keep prior product code head `0953b2f3570725bf1de5cc138422220c1731a48d` separate from any later documentation-only HEAD.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only changed Authorized Files, with subject `fix(review-fast-p): repair apollo institution subject contracts`. Verify the resulting commit is HEAD of the selected worktree and reachable from `fast/apollo-institution-subject-repair`; record its actual SHA as the post-repair code head.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/review/apollo-institution-subject-repair/repair-execution.md` using the Review-Fast-P Repair Execution format. Record the exact human approval source, repair path and actual SHA256, prior and committed post-repair code SHAs, evidence parent before the handoff commit, changed product/test files and their authorization, exact fresh commands/results/counts, deviations, executor identity when exposed, and clean worktree/index evidence. Preserve all prior epochs append-only; do not invent unavailable metadata or a future commit SHA.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record apollo institution subject repair execution`. Record the resulting actual evidence HEAD separately from the product code head. Do not write a commit's own SHA into itself or create another evidence commit solely to do so. After this commit, verify the selected worktree/index are clean and report both product and evidence SHAs before emitting `READY_FOR_VERIFICATION`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the human invocation requests that return. After `READY_FOR_VERIFICATION`, resume `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fast/apollo-institution-subject-repair/human-review-handoff.md` in the selected review worktree, using the committed `repair-execution.md`. The review controller must acquire a fresh independent reviewer after the product commit, preserve V-1 through V-8 lineage, and rerun the full aggregate matrix and all required commands against the committed candidate. Do not ask the user to relay executor metadata. The executor must not self-certify machine PASS or perform human acceptance.

This section is the sole control-plane exception to the earlier restriction on unlisted documentation files: the repair executor may append and commit only the exact `repair-execution.md` named above. It does not expand the eleven product/test Authorized Files. All other review evidence remains read-only to the repair executor; subsequent controller writes follow the separately authorized review-fast-p workflow.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan. On BLOCKED or PLAN_CONFLICT, preserve evidence and return the named blocker; do not claim READY_FOR_VERIFICATION, create success commits, or start an unauthorized repair round.

One human action approving execution, the two specified local commits, the durable handoff, and automatic return to review is:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fix/apollo-institution-subject-repair/repair.md
After READY_FOR_VERIFICATION, resume $review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fast/apollo-institution-subject-repair/human-review-handoff.md in /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair using the committed /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/review/apollo-institution-subject-repair/repair-execution.md. Do not ask me to relay executor metadata.
```
