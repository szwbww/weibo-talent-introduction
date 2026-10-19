# Child 02-country-supplement fix log

## Epoch 1 — Round 1/3

**Findings**
- F-1 (I-2 / T-3.3, P1): the final profile was re-signed with the supplement source (`effective.source`) instead of the original institution-evidence token prefix, so a JATS-signed profile that received a fill failed the freeze/registration source check.
- F-2 (T-2, P1): the supplement service had no Spring stereotype/bean, so the optional `DiscoveryVerificationRegistrationService.countrySupplement` dependency was null in the running container and `countrySupplementEnabled` was inert.
- F-3 (I-5, P2 explicit invariant): `DiscoveryVerificationRegistrationService.reserve` wrapped `InterruptedException` into `DiscoveryRegistrationException`, which the discovery loops catch and continue, swallowing cancellation.

**Before**: `842cd66980660a94b2e34baab4aaf6ce98260953`; focused 80 JVM + 1,724 JS pass, Docker 46 tests pass; JATS-original fill and container wiring were not covered.

- Fix commit: 0953b2f3570725bf1de5cc138422220c1731a48d

**Authorized files changed**
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementService.kt` — `@Service`; re-sign with `profile.institutionEvidence.substringBefore(':')`; receipt `selectedSource` still records the supplement source.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — `InterruptedException` and recognized cancellation rethrow instead of becoming `DiscoveryRegistrationException`.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt` — JATS-original fill/registration test, Spring container wiring test, interrupt-propagation test.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt` — JATS snapshot/RAW country and token-source consistency test.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementServiceTest.kt` — asserts every FILLED case retains the original token prefix.
- `src/test/resources/discovery/institution-country-cases.json` — `JATS_ORCID_MATCH` synthetic case (22 cases total).

**Commands**
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` → PASS, 83 JVM tests, 0 failures/errors/skips; node suite in the same build PASS.
- `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test` → PASS, 47 tests (CountrySupplementFlowIT 8 + VerificationFlowIT 27 + RepositoryIT 12), 0 skipped.
- Runtime smoke after the fix: `… mvn -q exec:exec … DiscoveryCountrySupplementFlowIT --country-smoke` → PASS, `snapshotCountry==rawCountry` for OPENALEX/US (0 supplement HTTP) and ORCID/GB (1 supplement HTTP), `validInstitutionEvidence=true`.

**Result**: FIXED

**Notes**
- The JATS path is proven at both levels: the service test asserts the FILLED token prefix equals the original JATS prefix, the registration test captures the frozen snapshot (`JATS:` token, `selectedSource=ORCID`, country GB), and the FlowIT asserts snapshot and RAW have the same country/token for `EMAIL-jats-doc`.
- The container wiring test registers the production `@Service` classes in an `AnnotationConfigApplicationContext` with the registration dependencies as singletons and asserts the injected `countrySupplement` field is non-null; `application.yml` was not edited and `countrySupplementEnabled` remains default `false`.
- The interrupt test proves an interruption during the bounded request escapes `reserve` as `InterruptedException` (not `DiscoveryRegistrationException`) with the thread interrupt flag set.
- No plan, ledger, verify-log, application.yml, Repository/Gate/Commit/QueryService/Worker/Normalizer, or other unauthorized file was changed; fix-log and reports are outside the fix commit.
