# Child 02-country-supplement execution

## Execution Result: READY_FOR_VERIFICATION

- Plan: `docs/plans/2026-10-19/discovery-institution-country-supplement.md`
- Plan SHA-256: `3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67` (matches the brief; unchanged)
- Entry plan: `docs/plans/2026-10-19/apollo-institution-subject-repair.md` SHA-256 `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397`
- Child base (child 01 terminal code head): `1e2ae74981ccb8b6866b135222b2a24540ea09b8`
- Pre-execution HEAD: `f93cbdc` (docs-only; product tree = `1e2ae74`)
- Target worktree/branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair` / `fast/apollo-institution-subject-repair`
- Implementation commit: `842cd66980660a94b2e34baab4aaf6ce98260953` (`feat(fast-p): implement 02-country-supplement`)
- Implementation boundary: exactly the 8 authorized paths; no plan, build, migration, ES mapping, frontend, application.yml, Repository/Gate/Commit/QueryService/Worker/Normalizer change.
- Evidence: this report is intentionally outside the implementation commit; the controller commits it separately.

## T-0 执行前复核（receipts）

### Predecessor stages

- Child 01 `01-institution-subject` is `LIGHT_PASS_WITH_NOTES` with zero repairs (`docs/plans/fast/apollo-institution-subject-repair/children/01-institution-subject/execution.md`); its code head is the child base above.
- The two prior stages are material: `DiscoveryInstitutionNormalizer` exists as a pure object; Rules/Gate/QueryService consume `institutionNormalization`; Worker retention and query/narrow semantics are implemented. Nothing in those files was modified.
- `git status --porcelain` was clean before implementation; only the 8 authorized paths are in `842cd66`.

### Re-read contracts (actual code)

- Normalizer API (`DiscoveryInstitutionNormalizer.kt`): `VERSION="INST_SUBJECT_V1"`, `resolve(raw, InstitutionSource, sourceRef)`, `build/buildInputs/withCandidates`, `validate(expected, saved, includeCandidates)`, `queryInstitution`, `employmentInputs`, `candidateInputs`, `compare(...)` (two overloads), `canonical`, `sha256`; sources `PROFILE`, `ORCID_EMPLOYMENT`, `APOLLO_CANDIDATE`; `MAX_RAW_LENGTH=4096`, `ORCID_PROJECTION_LENGTH=500`. Consumed read-only by the new service.
- Parser semantics (`QualificationEvidenceParser.kt`): only `visibility=PUBLIC` (case-insensitive) facts are emitted; employment/education scope is `CURRENT` when `start <= today` and end is missing/future, `HISTORICAL` when `end <= today`, otherwise `UNKNOWN`; assertion source is `ORGANIZATION` when `source-client-id.path` is present, `SELF` only when `source-orcid.path == boundOrcid`, else `UNKNOWN`; `jsonPointer` is the exact summary pointer. The supplement calls this parser on an in-memory `OrcidEvidenceDocument` and discards the facts.
- Reserve call sites (`grep -rn "verificationRegistration?.reserve" src/main/kotlin`):
  - `ExpertDiscoveryService.kt:1463` ORCID branch (no paper)
  - `ExpertDiscoveryService.kt:1822` paper branch (passes `identity.paper`)
- Snapshot consumers (`grep -rn "readValue<VerificationSnapshot>" src/main/kotlin`): Gate `:61`, QueryService `:102`, Worker `:165/:246`. All mappers disable `FAIL_ON_UNKNOWN_PROPERTIES`, so the new root member is inert.
- Country/token/fact fingerprint (`DiscoveryIdentity.kt`): `institutionEvidence(profile, source)` is the only signer; `tokenDigestInput` order is source, proof source/hash, normalized email, names, source author ID, paper/ORCID ID, institution, **country**, institution type; `validInstitutionEvidence` recomputes from stored fields. `DiscoveryVerificationAdmissionGate.snapshot` includes `institution` and `institutionEvidence` but not `country`, so country enters the fingerprint only through the token.
- Repository (`DiscoveryVerificationRepository.kt`): `reserveItem` is the only production snapshot writer and uses `INSERT IGNORE` + `(expert_doc_id, identity_hash, policy_version)`; `checkedIdentity` checks only the identity keys. This is why the new pre-check and post-reserve winner read were required.
- Country mapping (`CountryContinentMapping.kt`): `toRegion` returns `Other` for unknown values; `countriesForRegion(North America)` is the region filter used by `ExpertSearchService.regionFilter`.
- `DiscoveryCountrySupplementService` line/naming drift: none requiring plan attachment edits. No behavior or whitelist change was found, so no PLAN_CONFLICT.

## Task Status

| Task | Status | Files |
|---|---|---|
| T-1 pure selection + COUNTRY_V1 receipt | IMPLEMENTED | `DiscoveryCountrySupplementService.kt`, `institution-country-cases.json` |
| T-2 bounded public read + switch | IMPLEMENTED | same service, `DiscoveryVerificationProperties.kt` |
| T-3 frozen registration + consumer profile | IMPLEMENTED | `DiscoveryVerificationRegistrationService.kt`, `ExpertDiscoveryService.kt` |
| T-4 verification/evidence | IMPLEMENTED | 3 test classes + fixture |
| T-5 release/runtime | NOT APPLICABLE (plan forbids deploy/live backfill) | none |

## Commands（fresh, final state `842cd66`）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS (exit 0) | 80 JVM tests, 0 failures/errors/skips; node suite run by the build |
| `node --test src/test/js/*.test.js` | PASS (exit 0) | `tests 1724`, `pass 1724`, `fail 0`, `skipped 0` |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=…zulu-11… mvn -Dapi.version=1.40 -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test` | PASS (exit 0) | 46 tests, 0 failures/errors/skips = CountrySupplementFlowIT 7 + VerificationFlowIT 27 + RepositoryIT 12; Docker/MySQL 8.0.36, isolated ES mock, Flyway v158 |
| Runtime smoke (real MySQL + real registration; `exec:exec`, classpath test): `…DiscoveryCountrySupplementFlowIT --country-smoke` | PASS (exit 0) | see the two `RUNTIME_COUNTRY_SMOKE` lines below |

Runtime smoke output:

```
RUNTIME_COUNTRY_SMOKE doc=runtime-oa receipt=FILLED/OPENALEX/US snapshotCountry=US rawCountry=US token=OPENALEX: validInstitutionEvidence=true supplementHttp=0
RUNTIME_COUNTRY_SMOKE doc=runtime-orcid receipt=FILLED/ORCID/GB snapshotCountry=GB rawCountry=GB token=ORCID:5 validInstitutionEvidence=true supplementHttp=1
```

The smoke reads the **persisted** `snapshot_json` and the **RAW mock source represented by the consumer's final profile**; both carry the same final `country` and the same `institutionEvidence` token, and `validInstitutionEvidence` is true. It is not only a unit assertion.

## Fixture and FlowIT evidence

### Fixture matrix (`src/test/resources/discovery/institution-country-cases.json`)

Synthetic and de-identified (`"synthetic": true`), fixed clock `2026-10-19T00:00:00Z`, 21 cases:

- FILLED 3: `OA_MATCH`, `ORCID_MATCH`, `ORCID_SELF_CURRENT`
- UNCHANGED 2: `OA_EXISTING_US` (US kept), `OA_EXISTING_LITERAL_UNKNOWN` (`未知` kept verbatim)
- CONFLICT 4: `OA_INSTITUTION_MISMATCH`, `OA_MULTIPLE_COUNTRIES`, `ORCID_TWO_COUNTRIES`, `ORCID_INVALID_COUNTRY`
- ERROR 2: `ORCID_500`, `ORCID_INVALID_JSON`
- SKIPPED 2: `ORCID_429`, `ORCID_OVERSIZE`
- UNRESOLVED 8: `OA_AUTHOR_ID_MISMATCH`, `OA_ORCID_CONFLICT`, `OA_NOT_OPENALEX_PAPER`, `ORCID_HISTORICAL`, `ORCID_OTHER_INSTITUTION`, `ORCID_MISSING_COUNTRY`, `ORCID_CAMPUS_MISMATCH`, `NO_BOUND_ORCID`

HTTP accounting: 9 cases make zero requests (all OpenAlex cases plus `NO_BOUND_ORCID`), 12 make exactly one `/employments` request. Buckets are mutually exclusive and sum to 21. `DiscoveryCountrySupplementServiceTest` asserts each case's status, reason, country, request count, unchanged institution/nationality, and that only `country` + token change.

Additional unit coverage in the same class: 429 `Retry-After` pause and clock-based resume; 10/minute + 100/hour rolling budgets; exactly one in-flight permit; timeout → `HTTP_ERROR`; interrupt propagation; switch-off returns the input untouched; >20 observations → `EVIDENCE_TOO_LARGE` and no fill.

### FlowIT (`DiscoveryCountrySupplementFlowIT`, real MySQL)

| Case | What it proves |
|---|---|
| OA match | OpenAlex fills `US` with **0** supplement HTTP; snapshot receipt `FILLED/OPENALEX/US`; RAW and snapshot token identical and valid; worker → `COMPLETED/APOLLO_UNCLEAR`, candidate country `US`, token equal, `autoCount=1`; `QueryService` reads the item; `ExpertSearchService.regionFilter(North America)` is emitted and maps `US` to that region |
| ORCID match | exactly **1** supplement request; `FILLED/ORCID/GB`; RAW/snapshot `GB`; worker reuses the frozen country |
| Existing/protected/switch | `US` and literal `未知` unchanged with 0 HTTP; RAW-present doc is `protected`; switch-off writes no `countrySupplement` and keeps country null |
| Retry / old RESERVED | second reserve returns the identical snapshot and fingerprint with no new HTTP; a hand-written legacy snapshot without the member is resumed unchanged and not upgraded |
| Concurrency | two concurrent reserves (OpenAlex-route US server vs ORCID-route GB server) return one distinct country, the same persisted snapshot, and both RAW writes use the winner; exactly one item |
| Failures | 429 → `RATE_LIMITED`, 500 → `HTTP_ERROR`, invalid JSON → `INVALID_RESPONSE`, timeout → `HTTP_ERROR`, budget → `BUDGET_EXHAUSTED`; all continue the original collection flow with a null country and a receipt; registration SQL failure leaves no item/RAW |

## Invariant coverage

- **I-1**: non-empty values (including the literal `未知`) are never inspected or overwritten; only a trimmed uppercase two-letter code known to `CountryContinentMapping` is accepted; author/institution ambiguity, multiple countries, missing country, mismatched full institution names, hospitals/campuses/multi-institution names all refuse; nationality is never touched.
- **I-2**: supplement runs after all protections and existing-item checks and before `reserveItem`; the final profile is re-signed with the original token source and used for eligibility, `toIndexMap`, activation/admission; no worker/commit/projection write; signature algorithm unchanged.
- **I-3**: a same-doc/identity/policy item is read before any HTTP; source mismatch or any fact other than country/institutionEvidence fails registration; old items are not upgraded; the profile returned is always the persisted winner, never the local supplement result; non-RESERVED items are `protected` (no RAW rewrite); persistence failures keep the original failure path.
- **I-4**: the member is optional; when present it must be `COUNTRY_V1` with a known status/reason, bounded observations, blank `beforeCountry` for `FILLED`, matching `afterCountry`, original-token source and a valid final token. It is not part of `VerificationSnapshot`, creates no attempt/SQL row, and does not change `request_count`.
- **I-5**: OpenAlex path performs no HTTP; ORCID issues at most one `GET /{boundOrcid}/employments`, no person/education/detail, no retry, no redirect, connect 2s/read 3s/body 2 MiB; one in-flight, 10/minute, 100/hour; denial returns immediately; each acquired permit is consumed even on failure; 429 pauses per `Retry-After` or 60s; timeouts are `HTTP_ERROR` and interrupts propagate.
- **I-6**: the synthetic fixture and FlowIT report request counts separately from Apollo; receipts hold the decision inputs. A real frozen-cohort effectiveness report is **NOT_EVALUATED** (no authorized real input was available in this worktree); no success rate is claimed.

## Deviations / limits

- `IDENTITY_UNRESOLVED` maps to audit status `UNRESOLVED` (not `CONFLICT`); this is the only status/reason pairing not explicit in the plan and is applied uniformly.
- On the >20-observation bound, the receipt keeps the first 20 observations for audit but records `EVIDENCE_TOO_LARGE` and does not fill; the decision is made before truncation.
- ORCID summaries without a country are treated as `NO_EVIDENCE`; unknown/invalid codes are `INVALID_COUNTRY`.
- The FlowIT uses synthetic fixture inputs and mocked provider/ES transports; the real 41,844-institution inventory was not used as a country source (it has no country field), per the plan.
- No deployment, restart of a live service, live backfill, push, merge, rebase, squash, amend, reset, or worktree deletion was performed.

## Remaining

- Independent `verify-p` machine verification and the human acceptance list A-1..A-5.
- The real-cohort effectiveness table (per-document original/new country and evidence) remains to be produced from authorized frozen items; until then it is `NOT_EVALUATED`.

## Post-fix execution correction

- Round 1 fix commit: 0953b2f3570725bf1de5cc138422220c1731a48d.
- Post-fix verified counts: focused 83 JVM tests (0 failures/errors/skips) plus 1,724 JS tests; Docker/MySQL 47 tests (CountrySupplementFlowIT 8 + VerificationFlowIT 27 + RepositoryIT 12), 0 skipped; runtime smoke preserved snapshot==RAW country/token for OPENALEX/US (0 supplement HTTP) and ORCID/GB (1 supplement HTTP).
- The round-0 counts above remain the initial implementation record. The fix-log and re-verification report hold the round-1 record. This note was added during deterministic evidence finalization; no product behavior changed.
