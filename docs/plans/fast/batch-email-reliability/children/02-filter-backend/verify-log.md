## Epoch 1 — Attempt 1

## Light Verification: LIGHT_PASS
Child: 02-filter-backend — `docs/plans/2026-09-26/batch-email-02-filter-backend.md` (`commit:38ba555b4147970ee77569e71f863955e2c4a2b5`)
Boundary: `cabd3f09d7120b6edbb8e309756e25050a1c1fd6..e799ec41b5f7213b21dcf939e3089769ad6c78b5`
Verifier: RerunChild02Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status` across the stated boundary: exactly the brief's ten authorized product/test files; intervening child-01 brief/execution/fix/verification and ledger evidence files are not product/test changes. Only V142 added as migration. |
| Plan and invariants | PASS | Config entity/view/create/update and snapshot/scope defaults and propagation: `BatchSendTaskConfig.kt:38-143`, `BatchExecutionModels.kt:39-42,85-88,184-188,374-378`; create/update/legacy/enable/delete read/write preservation: `BatchSendTaskConfigService.kt:65-169,173-183,205-233,385-408`. `V142__add_exclude_verified_unavailable_emails.sql:1-2` adds BOOLEAN NOT NULL DEFAULT FALSE; actual V141→V142 MySQL old-row/default/not-null and true write/read assertions: `FlywayMigrationIntegrationTest.kt:77-116`. Read-only historical lookup, normalized addresses, ≤500-email batches, fixed Beijing-time boundary, off-path `_count` vs on-path scroll/filtered counts: `ManualInitialOutreachService.kt:480-512,564-588,1597-1649`, child-01 helper `BatchEmailVerificationService.kt:76-95`; current introduction profile email and material contact.expertEmail with preserved gates: `ManualInitialOutreachService.kt:1273-1285,1558-1585`. Original raw page controls exhaustion/offset before dedup/filter; fully filtered page continues and cancel stops further fetches with CANCELLED finalization: `OutreachTargetIterator.kt:26-62`, `ManualInitialOutreachService.kt:602-608,1080-1094`. Preview and actual-send regressions: `ManualInitialOutreachServiceTest.kt:237-404`; iterator regressions: `OutreachTargetIteratorTest.kt:161-243`. |
| Required commands | PASS | Fresh JDK11 `mvn -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest test`: exit 0, 266 run/0 failed/0 errors/0 skipped (`artifact://1203`). Fresh JDK11 `mvn -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test`: exit 0, 33 run/0 failed/0 errors/0 skipped; Testcontainers started MySQL 8.0.36 and Flyway migrated V141→V142 (`artifact://1205`). `git diff --check`: exit 0; bounded committed diff `git diff --check cabd3f09..e799ec41`: exit 0. Prior baseline Docker client API 1.32 versus daemon minimum 1.40 (`ledger.md:22`) is resolved for this run by docker-java `-Dapi.version=1.40`; Docker server 29.4.0/API 1.54 was connected. No skip substituted for migration. |
| Downstream interfaces | PASS | Child 03 requires the same `BatchExecutionModels.kt` and `ManualInitialOutreachService.kt` seams to add deferred failure handling; new field remains independent of `emailVerificationEnabled` and existing result/verification control paths remain present (`BatchExecutionModels.kt:39-42,374-378`; `ManualInitialOutreachService.kt:555-563,582-589`). Child 04's config/view/snapshot and preview count interface is present (`BatchSendTaskConfig.kt:74-77,109-112,140-143`; `ManualInitialOutreachService.kt:480-512,1853-1858`). |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
