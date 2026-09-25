## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 — docs/plans/2026-09-25/mail-open-tracking-01-storage-api.md
Boundary: f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..76de1ab3a3257a2b2e292f6c894296d7eb8c80dd
Verifier: RecoveryStorageVerifier
Attempt: Epoch 1 — Attempt 1, 2026-09-25T20:39:16+0800; implementation c39198962a728e620bf0648e71021c49601778d2, F-01/F-02 fix 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd. Plan-only commits 7bfd699 and ab2dda0 precede the product boundary.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status ab2dda0f86c52d8bd9570d994fa895f087931df3 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd` lists exactly the ten child-authorized product/test paths. `git log --ancestry-path` confirms seed, amendment, implementation, fix in order; fix changes only repository and its IT. |
| Plan and invariants | PASS | V141 migration:1–15 and MailRecord.kt nullable field; repository:47–86 switch, REQUIRES_NEW reservation, atomic conditional signal; repository:88–160 successful-outbound snapshot, explicit parameterized filters and aggregate; service:38–118 HTTPS validation, recipient/token rules, Shanghai date range and read-only REPEATABLE_READ snapshot; controller:20–61 protected API/public GET/explicit HEAD. F-01: repository:84 compares `BINARY ... = BINARY 'true'`, IT:132–136 covers uppercase `TRUE`. F-02: IT:81–102 exercises Spring-proxied reservation against uncommitted outer contact, independent connection visibility and outer rollback. MySQL assertions are source evidence only, not executed proof. |
| Required commands | PASS | Fresh JDK11 `mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true`: exit 0, 7 run/0 failures/0 errors/0 skipped (`artifact://404`). Fresh JDK11 `mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true`: exit 1, two `@BeforeAll` Docker-unavailable errors, zero assertion failures; **MySQL test bodies NOT_RUN, not PASS** (`artifact://403`). `docker info` exit 1: OrbStack socket absent. Matches execution.md/fix-log.md baseline infrastructure failure; no behavioral comparison possible for MySQL. |
| Downstream interfaces | PASS | 01→03 `MailOpenTrackingService.reserve(recipient)` returns independently committed id/token/HTTPS URL or null (service:26,49–64; repository:60–76); 03 must isolate reservation failures at SMTP boundary. `MailRecord.openTrackingId` nullable unique FK for later successful-save propagation (migration:11–15). 01→04 settings `{enabled,configured,baseUrl}`, snapshot `{records,totalCount,summary}`, detail without token/body, GET signal and side-effect-free HEAD (repository:12–43, service:25, controller:20–61). No sending integration is required in child 01. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-01: Docker is unavailable (`docker info` exit 1; Testcontainers fails before either integration test body). The required command was attempted freshly and classified NOT_RUN, not successful MySQL migration/transaction/concurrency verification. Environment restoration and a fresh Docker-backed rerun are required before claiming that behavioral gate or whole-system acceptance; no authorized source correction follows from this infrastructure failure.

### Required Action
- COMPLETE_CHILD
