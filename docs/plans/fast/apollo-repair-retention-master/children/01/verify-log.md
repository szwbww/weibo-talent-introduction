# Child 01 — Verification log

## Light Verification: LIGHT_FAIL
Child: 01 — docs/plans/2026-10-21/apollo-repair-01-error-retention.md
Boundary: 962fcfe924ad7562a4ea917d174180df82ac3b5c..dc1f01c55164016288c491cb5f9580eeaa5ac968
Verifier: ApolloVerify01

Epoch: 1; attempt: 1; timestamp: 2026-10-21T12:37:40+08:00. Independent inspection and fresh command execution in the assigned worktree; plan/evidence-only seed excluded from product authorization.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff <base>..<head> -- src/main src/test` reports precisely the seven source/test files authorized at child plan:52–58: ApolloPeopleSearchClient.kt, VerificationDiagnostics.kt, DiscoveryVerificationWorker.kt, DiscoveryVerificationQueryService.kt and their three named test files. Boundary name listing additionally contains controller plan/workflow evidence, excluded as instructed. No schema/frontend/ES product change. |
| Plan and invariants | FAIL | I-1 classification has direct evidence at VerificationDiagnostics.kt:49–63 and ApolloPeopleSearchClientTest.kt:103–127. Bounded reads/hash scope are at ApolloPeopleSearchClient.kt:76–102 and HTTP test:147–172; success shape/hash and nullable historical diagnostic are retained at Worker:155–162 and Query tests:126–147. HTTP→Worker→real MySQL→item detail and one-request accounting are exercised at WorkerTest:159–209. However I-2's mandatory token/key/password redaction is violated for quoted assignments inside a whitelisted message: F-01 below. |
| Required commands | PASS | Fresh exact `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test`, exit 0, BUILD SUCCESS, 31 tests / 0 failures / 0 errors / 0 skipped; finished 2026-10-21T12:37:35+08:00 (artifact://33). Environment: JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home, its bin prepended to PATH; DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock; JAVA_TOOL_OPTIONS=-Dapi.version=1.44. Fresh `java -version` and `docker info` also exited 0 (Zulu 11.0.32.1; OrbStack server 29.8.2). Surefire XML mtimes: HTTP 12:37:30+08:00 (12 tests), Worker 12:37:30+08:00 (11), Query 12:37:06+08:00 (8), each failures/errors/skips 0. Matches implementer's final 31/0/0/0 baseline; earlier recorded assertion/API-version failures are not reproduced or treated as baseline product failures. MySQL scenario is not skipped and explicitly fails when Docker unavailable. |
| Downstream interfaces | PASS | Existing Result subclasses are preserved with default-null diagnostic (ApolloPeopleSearchClient.kt:30–35); success DTOs remain unchanged (:37–48), nullable attempt summary field is added (DiscoveryVerificationQueryService.kt:225–228). Fixed schemaVersion=1/kind=PROVIDER_ERROR DTO and explicit error result codes exist (VerificationDiagnostics.kt:9–24,49–63); item-only projection uses the whitelist reader (QueryService.kt:106–110). Existing completeAttemptAndProgress lease/CAS seam persists diagnostics with NULL non-success response_sha256 (Worker:155–162). E-6/E-7 read completely; no repository, ReplayTool, admission, ES or sample-store format/writer modifications. |

### AUTO_FIX
- F-01 — Child plan I-2 and implementation step 2 require message key/token/password values to be redacted before persistence/API exposure. `VerificationDiagnostics.kt:33` requires `:`/`=` immediately after the unquoted key (allowing whitespace only), so the closing quote in a quoted key prevents a match. For a valid provider error body `{"message":"invalid parameter {\"token\":\"private-token\"}"}`, error extraction (:72) yields `invalid parameter {"token":"private-token"}` and sanitize (:35–37) leaves the secret intact; the email/phone/Bearer/query patterns do not match this input either. A fresh JDK 11 JShell reproduction of the exact secret regex returned `invalid parameter {"token":"private-token"}` unchanged (exit 0); this is an explicit invariant violation, not a general security audit finding. Worker serializes this diagnostic (:156–161), and the detail reader applies the same ineffective sanitizer (:83–87). Smallest authorized correction: extend the existing secret-assignment matcher to redact quoted key assignments as well as existing unquoted forms, and add a regression case in authorized ApolloPeopleSearchClientTest.kt showing quoted token/key/password message values absent from serialized diagnostics and re-read API projection. Keep the current whitelist, bounded fields and classification behavior; no new files/interfaces required. Verifier made no product/test edits.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

## Light Verification: LIGHT_PASS
Child: 01 — docs/plans/2026-10-21/apollo-repair-01-error-retention.md
Boundary: 962fcfe924ad7562a4ea917d174180df82ac3b5c..4c2339e13d90d06735dd00edeaaefeede845ed7c
Verifier: ApolloReverify01R1

Epoch: 1; attempt: 2; repair round: 1; timestamp: 2026-10-21T12:48:07+08:00. Fresh independent four-gate reinspection and command execution in the assigned worktree. Historical report above is preserved; seed plan/evidence-only files are excluded from product authorization. F-01 is CLOSED by the evidence below. Human acceptance remains deferred.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only 962fcfe924ad7562a4ea917d174180df82ac3b5c..4c2339e13d90d06735dd00edeaaefeede845ed7c -- src/main src/test` returned the seven files authorized at child plan:52–58: ApolloPeopleSearchClient.kt, VerificationDiagnostics.kt, DiscoveryVerificationWorker.kt, DiscoveryVerificationQueryService.kt, ApolloPeopleSearchClientTest.kt, DiscoveryVerificationWorkerTest.kt and DiscoveryVerificationQueryServiceTest.kt (artifact://48). Full-boundary diff inspection distinguishes controller plan/workflow evidence from product files (artifact://46). Repair diff changes only the authorized diagnostic matcher and HTTP regression test; no schema, frontend or ES product change. |
| Plan and invariants | PASS | F-01 CLOSED: VerificationDiagnostics.kt:33 now accepts a closing single/double quote after the secret key before `:`/`=`; existing sanitizer remains shared by extraction (:70–76) and detail reading (:83–87). ApolloPeopleSearchClientTest.kt:147–174 exercises the exact prior `invalid parameter {"token":"private-token"}` example, quoted api_key/password, single-quoted mixed-case assignments, serialized diagnostics, reread projection and historical unsanitized message projection; all secret values must be absent. Fresh HTTP tests pass. I-1 has direct status/provider/phrase evidence at Diagnostics:49–63 and HTTPTest:103–127. I-2's bounded 16,385-byte read and fixed projection are at ApolloPeopleSearchClient:76–81 and Diagnostics:9–24,65–76; privacy/bounded hash tests are at HTTPTest:130–145,176–186. I-3 preserves original structural-success hash/unknown size (HTTP client:183–186; HTTPTest:189–202), NULL non-success response_sha256 (Worker:155–162), historical NULL/success-shaped diagnostic absence (QueryTest:126–147), and unchanged successful response serialization branch (Worker:156). HTTP→Worker→real MySQL→item detail with three 422 codes, one request/attempt, sanitized persistence and unchanged historical result/count is exercised at WorkerTest:159–209. E-6/E-7 were read completely; their lease/CAS and store boundaries remain unchanged. |
| Required commands | PASS | Fresh exact `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test` completed exit 0 / BUILD SUCCESS, 32 tests, 0 failures, 0 errors, 0 skipped; finished 2026-10-21T12:47:52+08:00 (artifact://50). Fresh Surefire XML counts: HTTP 13, Worker 11, Query 8; mtimes respectively 12:47:47, 12:47:47, 12:47:24 +08:00. Real MySQL scenario is mandatory and did not skip. Matches round-1 repair baseline 32/0/0/0; original implementation baseline 31/0/0/0 gains the quoted-secret regression test. Recorded initial Docker API/assertion failures remain historical and were not reproduced. Maven also completed its configured frontend checks (1733 JS tests passed, zero failures/skips). |
| Downstream interfaces | PASS | Existing Result subclasses retain optional/default-null diagnostic (ApolloPeopleSearchClient.kt:30–35); successful candidate/search DTOs remain unchanged (:37–48). VerificationDiagnostic retains schemaVersion=1, kind=PROVIDER_ERROR and the fixed whitelist (VerificationDiagnostics.kt:9–24); explicit APOLLO_CREDITS_EXHAUSTED/APOLLO_HTTP_422_UNCLASSIFIED codes remain (:53–58). VerificationAttemptSummary.diagnostic is optional/default NULL (QueryService.kt:226–228), exposed through item-only whitelist decoding (:108–110). Worker uses the existing completeAttemptAndProgress seam, serializes the existing success response unchanged and leaves failure response_sha256 NULL (:155–162). Repair introduces no interface change. Repository, ReplayTool, admission, ES and sample-store formats/writers are untouched in this boundary; no later-child implementation was inspected. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
