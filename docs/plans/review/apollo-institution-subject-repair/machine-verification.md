# Aggregate Machine Verification — apollo-institution-subject-repair

## Epoch 1 — 2026-10-20T01:55:42Z

- Master plan: `docs/plans/2026-10-19/apollo-institution-subject-repair.md` (sha256 `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397`)
- Governing master identity: worktree/recorded sha256 `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397`; invoked identity same; state `CONSISTENT`; amendments `N/A`.
- Ordered country child: `docs/plans/2026-10-19/discovery-institution-country-supplement.md` (sha256 `3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67`).
- Boundary: `fcd6719ecd03645284c3df0f8a6b95bf4686ba95..0953b2f3570725bf1de5cc138422220c1731a48d`.
- Evidence HEAD: `1b7e1a84233dd781a62b492276b83c6fc96632cc`; branch `fast/apollo-institution-subject-repair`; worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair`.
- Reviewer: `/root/aggregate_reviewer` (fresh isolated reviewer; distinct from all recorded writers/verifiers).
- Worktree resolution: `DISCOVERED_FROM_GIT_WORKTREES`; exactly one selected candidate; fast-p ledger/handoff and two child evidence bundles verified.
- Result: `FAIL`; convergence `INITIAL`; manual acceptance `PENDING`.
- Repair artifact/result: `docs/plans/fix/apollo-institution-subject-repair/repair.md` (sha256 `cfd5a2179390fb1c18470c0e2854d27b0df52b8ac37fab1bca6e443574cdab85`), `DRAFT_READY`.

## Verification Result: FAIL

Eight mandatory violations remain. No product code, review evidence, index, branch, or commit was modified by the reviewer. The combined diff contains exactly the 18 product/test/resource paths in the union of the two approved child whitelists; no schema, ES mapping, UI/CSS, provider, rollout, backfill, or unapproved product path changed.

### Commands

| ID | Command | Result | Evidence |
|---|---|---|---|
| C-1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 69 JVM, 0 failures/errors/skips; JS 1724 passed. `/private/tmp/aggregate-institution-focused.log` |
| C-2a | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | environment FAIL | exit 1; Docker Java API 1.32 below daemon minimum 1.40; 3 initialization errors, no assertion failure. `/private/tmp/aggregate-institution-it.log` |
| C-2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS | exit 0; Flow 27 + Repository 12 + Reporting 10 = 49 JVM, 0 failures/errors/skips; JS 1724 passed. `/private/tmp/aggregate-institution-it-configured.log` |
| C-3 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 83 JVM, 0 failures/errors/skips; JS 1724 passed. `/private/tmp/aggregate-country-focused.log` |
| C-4 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test` | PASS | exit 0; Country 8 + Flow 27 + Repository 12 = 47 JVM, 0 failures/errors/skips; JS 1724 passed. `/private/tmp/aggregate-country-it-configured.log` |
| D-1 | compiled aggregate probe against actual repository classes | PASS — diagnostic | exit 0; reproduced V-1..V-8 and the frozen inventory audit. `/private/tmp/aggregate-probe.log`; probe SHA256 `1aee654e05b06b3c6f6818d03bac19f2e80a5d96371f27082a0e7588736682a5` |
| D-2 | `git diff --check fcd6719ecd03645284c3df0f8a6b95bf4686ba95..0953b2f3570725bf1de5cc138422220c1731a48d -- src` | PASS | exit 0 |

C-2 supersedes only C-2a's documented Docker configuration issue. Docker inspection during review reported client/server `29.8.2/29.8.2`; the supplied client-version record had drifted. No repository configuration changed.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-ORDER, M-SCOPE, M-NONGOALS | PASS | Ledger/commit order is 01 then 02; exact union whitelist; no prohibited product scope. |
| S-O-1, S-I-2, S-I-3, S-T-1 | FAIL | V-1 campus collapse and V-2 contextual ID/alias loss. |
| S-O-2, S-I-1, S-I-6, S-I-7, S-T-0, S-T-4, S-T-5 | PASS | Original facts/fingerprint, frozen recovery, honest audit limits, predecessor recheck, required suites, and non-deployment hold. Frozen audit: SHA `d2019b7f…1150`; records 41844; with value 26134; missing 15710; identity impact `NOT_EVALUATED`. |
| S-N-1, S-N-2, S-N-3, S-N-4 | PASS | Existing identity, legacy, budget/CAS/protection, UI/CSS/statistics contracts held; no frontend writes. |
| S-I-4 | PASS | Legacy null behavior and strict Gate rejection held. |
| S-I-5, S-T-3 | FAIL | V-4 malformed per-row context escapes QueryService and can make GET fail. |
| S-T-2 | FAIL | V-7 invalid saved protocol is persisted as PENDING/EVIDENCE_CONFLICT, not VERIFICATION_FAILED. |
| C-O-1, C-I-1, C-T-1 | FAIL | V-3 over-broad complete-name equality and V-8 valid-duplicate misclassification. |
| C-O-2, C-I-4 | FAIL | V-6 downstream-invalid oversized skipped receipt; V-8 untruthful invalid-country result. |
| C-N-1, C-N-2, C-N-3, C-N-4, C-I-2, C-I-3, C-I-6, C-T-0, C-T-4 | PASS | Existing values/protections/old items/retention/UI, freeze/winner profile, honest fixed-input limits, caller/reader audit, and required SQL tests held. |
| C-I-5, C-T-2, C-T-3 | FAIL | V-5 cancellation is consumed as ordinary failure; V-6 converts a skip into registration failure. |
| S-A-1..S-A-6; C-A-1..C-A-5 | PENDING | Human-only checks were not performed. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | Country suffixes erase campus identity. |
| V-2 | NEW | Contextual Rules loses original standard-ID/alias semantics. |
| V-3 | NEW | Country full-name comparison removes punctuation. |
| V-4 | NEW | Malformed context can break records GET. |
| V-5 | NEW | CancellationException becomes ordinary supplement failure. |
| V-6 | NEW | Oversized audit receipt blocks registration. |
| V-7 | NEW; child 01 RECORD_ONLY reclassified | Invalid protocol gets the parser-drift disposition. |
| V-8 | NEW; child 02 RECORD_ONLY reclassified | Same-country duplicates become INVALID_COUNTRY. |

### Findings

#### P1

- V-1: `DiscoveryInstitutionNormalizer.kt:374-422` removes any alphabetic tail before a recognized country. `University of California, Los Angeles, USA` and `..., Berkeley, USA` both resolve to the parent; Rules can then produce `APOLLO_UNCLEAR` with the parent narrow hint. Violates S I-2/T-1 and I-3.
- V-2: `DiscoveryInstitutionNormalizer.kt:232-242` and `DiscoveryVerificationRules.kt:111-118` use name-only derived records. Probe: ORCID `ROR:1` and Apollo `ROR:2` at the same name changes from legacy `PENDING/IDENTITY_NOT_MATCHED` to contextual `APOLLO_UNCLEAR/person-1`. Violates S T-1/T-2.
- V-3: `DiscoveryCountrySupplementService.kt:245-252,313-315,442` uses punctuation-removing subject canonicalization. `Example University-A` matches `Example University A` and fills US, violating C I-1/T-1's NFKC/whitespace/case-only complete-name comparison.
- V-4: `DiscoveryVerificationQueryService.kt:103,131-141` deserializes before its per-item catch. `institutionNormalization.inputs:null` throws `MissingKotlinParameterException`, escapes `record`, and yields GET 500. Violates S I-5/T-3.
- V-5: `DiscoveryCountrySupplementService.kt:296-300,462-469` handles interruption but not `CancellationException`; registration's ordinary catch would consume a propagated cancellation too. Probe returns `ERROR/HTTP_ERROR`. Violates C I-5/T-2/T-3.
- V-6: `DiscoveryCountrySupplementService.kt:149-150,176` marks a 2003-character institution `SKIPPED/EVIDENCE_TOO_LARGE` but retains it in the observation; `DiscoveryVerificationRegistrationService.kt:193-194` correctly rejects it. A non-blocking skip becomes registration failure, violating C I-4/I-5/T-1.
- V-7: `DiscoveryVerificationWorker.kt:86-95` maps all context validation failures to `PENDING/EVIDENCE_CONFLICT`. `INST_SUBJECT_V9` in DECIDE persists `COMPLETED/PENDING`, instead of required `ERROR/VERIFICATION_FAILED`. Violates S T-2; lawful parser drift remains the separate EVIDENCE_CONFLICT case.
- V-8: `DiscoveryCountrySupplementService.kt:256-260,343-350` compares distinct country count to raw observation count. Valid duplicate OpenAlex US and ORCID GB inputs become `INVALID_COUNTRY`, contrary to C T-1/I-1/O-2.

#### P2

N/A.

#### Observations

- Real institution identity improvement and country effectiveness are `NOT_EVALUATED`; synthetic tests do not prove online success.
- No mandatory machine-evidence blocker remains after Docker recovery.
- Prior child 02 fixes for original-source token signing, Spring wiring, and `InterruptedException` remain effective. V-5 is the separate cancellation trigger.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-1 Worker invalid-context disposition | S T-2 invalid protocol must be VERIFICATION_FAILED; S T-3 parser drift may be EVIDENCE_CONFLICT | Blocking P1 V-7 | Fresh DECIDE `INST_SUBJECT_V9` probe persisted PENDING/EVIDENCE_CONFLICT. |
| Child 02 O-1 same-country duplicate conservatism | C T-1 decides after valid-country deduplication | Blocking P1 V-8 | Fresh OpenAlex and ORCID duplicate probes returned INVALID_COUNTRY. |

### Evidence Boundaries

- Required suites used isolated Docker MySQL and deterministic provider/ES doubles; no live provider/ES validation, rollout, backfill, deployment, or human acceptance ran.
- Complete real frozen identity/country cohorts were not supplied. The plans allow the explicit `NOT_EVALUATED` result; it is neither a PASS claim nor a machine blocker.
- Review covers this implementation boundary and required suites, not the entire repository test suite.

### Next Action

Verification is `FAIL/INITIAL`. `repair-p` produced one bounded `DRAFT_READY` repair artifact. No implementation was performed.

## Repair Planning Result: DRAFT_READY

- Baseline: `docs/plans/2026-10-19/apollo-institution-subject-repair.md`.
- Included findings: V-1 through V-8.
- Authorized product/test files: six original production files and five existing test files, all inside the two child whitelists.
- Excluded: prior resolved child-02 triggers, real-cohort measurement, human checks, Docker configuration change, optional cleanup, rollout/backfill.
- Repair artifact: `docs/plans/fix/apollo-institution-subject-repair/repair.md` (sha256 `cfd5a2179390fb1c18470c0e2854d27b0df52b8ac37fab1bca6e443574cdab85`).
- Required human decision: approve this exact repair plan before execution.

No product code was modified.
