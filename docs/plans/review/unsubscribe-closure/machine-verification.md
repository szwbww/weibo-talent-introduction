# Aggregate Machine Verification — unsubscribe-closure

## Epoch 1 — 2026-08-11T23:28:27+0800

- Master plan: `docs/plans/2026-08-11/unsubscribe-closure-master.md` (sha256 `ec9a06e8b23f4a7eeca03bad32fae1c900e980588a23c603a44c78d3cb8b89f8`)
- Governing master identity: worktree sha256 `ec9a06e8b23f4a7eeca03bad32fae1c900e980588a23c603a44c78d3cb8b89f8`; recorded `commit 16c476b`
- Master identity state: CONSISTENT
- Boundary: `8e8ddfcd6c02c754de3e50b3c02004a2900e5be5..cfe8936c2dcf049672ebaca036430aeabcc1cc7d`
- Reviewer: `/root/aggregate_reviewer`
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

## Verification Result: PASS

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| JDK/Maven | PASS | Zulu 11.0.15; Maven 3.9.11 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeBodyLinkMigrationTest` | PASS | exit 0; 6 tests, 0 failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailVariableServiceTest` | PASS | exit 0; 40 tests, 0 failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SmtpMailDeliveryServiceTest` | PASS | exit 0; 24 tests, 0 failures/errors |
| Remaining targeted-class union | PASS | exit 0; 195 tests, 0 failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 2296 Maven tests, 0 failures/errors; Node 485/0; BUILD SUCCESS |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; 2296 Maven tests, 0 failures/errors; WAR generated; BUILD SUCCESS |
| `git diff --check 8e8ddfcd6c02c754de3e50b3c02004a2900e5be5..cfe8936c2dcf049672ebaca036430aeabcc1cc7d` | PASS | exit 0 |
| Flyway Docker integration test | N/A | Plan 01 marks it optional and Docker-only. |
| Single-method diagnostic commands | N/A | Failure-localization only; no failure observed. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| C-01 | PASS | `V87__append_unsubscribe_line_to_cold_outreach_templates.sql:7-16`: only `mail_compose_template_block`; `CONCAT` and `NOT LIKE`; INTRODUCTION/MATERIAL_REMINDER only. |
| C-02 | PASS | `src/main/resources/application.yml:8-13`: `placeholder-replacement: false`. |
| C-03 | PASS | `MailVariableServiceTest:582-613`: enabled URL plus no literal `${unsubscribeUrl}` in both configuration states. |
| C-04 | PASS | `SmtpMailDeliveryService.kt:17-24`: fail-closed gate before `getSender`; all seven `mailDeliveryService.send` paths cross it. |
| C-05 | PASS | `EmailSuppressionService.kt:120-125`: `RecipientSuppressedException : IllegalStateException`; `GlobalExceptionHandler.kt:18-20`: HTTP 400 mapping. |
| C-06 | PASS | `PendingMailOperationService.kt:254-262`: block happens before `prepareAndClaim`. |
| C-07 | PASS | `IntroductionMailComposer.kt:69-80`: default false; `ManualExpertMailService.kt:178-255`: manual-single-send override; `ExpertContactManagementController.kt:326-339`: batch retains default. |
| C-08 | PASS | `EmailSuppressionService.kt:74-109`: normalized exact subject set and priority; `AutoMailReplyService.kt:138,197,310,837-843`: three paths pass subject. |
| C-09 | PASS | Implementations touch only authorized child 01/02/02b files; master Plan 03/04/05 remain unimplemented. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| O-1 | RECORD_ONLY | Plan 02's two-hit grep wording conflicts with mandatory SMTP read; actual three hits satisfy the governing override contract. |
| O-2 | RECORD_ONLY | Plan 02b wiring test stubs detection; independent `EmailSuppressionServiceTest` covers exact-subject behavior. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- O-1 and O-2 remain RECORD_ONLY; neither violates the master contract.

### Evidence Boundaries

- No machine-verification blocker.
- Docker Flyway integration is explicitly optional.
- Gmail/client/pre-production checks remain manual and pending.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 02 O-1 | Explicit manual-single-send override, default false elsewhere | RECORD_ONLY | Required SMTP read produces the third `allowSuppressedRecipient` hit; no behavioral mismatch. |
| Child 02b O-1 | Exact subject detection and MAILTO source capture | RECORD_ONLY | Isolation stub is not the only coverage; `EmailSuppressionServiceTest` proves actual detection. |

Repair planning: N/A. No product code was modified.
