# Child brief — 02-status-api

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request`
- Branch: `fast/material-request`
- `child_base_sha`: `535f76f3bcbee243ae5bef09686bbe57921075e7` (child 01 terminal code head)
- Approved plan (complete contract): `docs/plans/2026-09-17/02-material-request-status-api.md`
- Master plan cross-plan constraints: `docs/plans/2026-09-17/00-material-request-master.md`
- Execution report to write: `docs/plans/fast/material-request/children/02-status-api/execution.md`

## Authorized files (exactly these seven; #7 added by amendment A1)

1. `src/main/resources/db/migration/V128__add_material_request_codes.sql`
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialRequestServiceTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — A1: line-number-only correction of the pinned site `ExpertContactManagementController.kt` 564 → 578 inside `EXCLUDED_NOISE_SITES` (path and fragment text unchanged; no new assertion, no behavioural change).

Do not modify `MailVariableService.kt`, `RagProcessContextResolver.kt`, `document/controller/ExpertMaterialController.kt`, any static resource, any frontend test, or any other file. Do not touch the fast-p ledger or other children's artifacts.

## Verified current-state facts (re-confirm, then rely on the plan)

- Highest existing migration is `V127__add_outbound_attachments_snapshot.sql`; `V111__create_expert_material_status.sql` defines `chk_expert_material_code` (7 legacy codes), `chk_expert_material_status` (`PROVIDED`,`DECLINED`), `uk_expert_material_contact_code`.
- `ExpertMaterialService.ExpertMaterialCode` is one enum of 7 legacy entries with `label`/`requestText`; `listMaterials` and `renderPendingMaterials` iterate `ExpertMaterialCode.entries` and must keep doing so.
- `ExpertContactManagementController.kt:~245` keeps the legacy `listMaterials(contactId)` as a non-routed method (its `@GetMapping` was removed to avoid the Ambiguous mapping with `document/controller/ExpertMaterialController.kt`); the legacy `PUT /{contactId}/materials/{materialCode}` stays.

## Invariants (from the approved plan)

- I-1: new catalogue codes are exactly `REQ_PUBLICATIONS, REQ_PROJECTS, REQ_PATENTS, REQ_AWARDS, REQ_DEGREES` in that order; legacy 7 codes, rows, `${pendingExpertMaterials}` and the RAG `CV` read are untouched. Never infer new state from old rows or vice versa.
- I-2: missing row = `PENDING`; `PROVIDED`/`DECLINED` persist one row each; setting `PENDING` deletes the row. The status CHECK stays two-valued and the unique key stays `(expert_contact_id, material_code)`.
- I-3: the five English `requestText` strings are defined once, server-side, and returned by GET; GET is read-only (no writes, no file scanning, no mail).
- I-4: new routes are `/api/expert-contacts/{contactId}/material-requests` and `/{code}`; `GET /materials` keeps returning the document page object. Application context must start with no Ambiguous mapping.

## Interface record required for child 03

In the execution report, record the exact observed contract: both routes and verbs, the response array shape `[{code,label,status,requestText}]` with its field order, the five codes in order, the five Chinese labels (代表性论文、科研项目、专利、荣誉奖项、学位), the accepted `PUT` body, and the five English `requestText` values copied verbatim from the running response (not from the plan prose).

## Required commands

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest
mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
git diff --check
```

Baseline at `child_base_sha`: `mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest` fails with "no matching tests" (classes did not exist); the `migrationIt` group did not run before child 02. Docker 29.4.0 is available; `FlywayMigrationIntegrationTest` starts its own MySQL testcontainer. If the bundled docker-java client fails API negotiation, retry with `DOCKER_API_VERSION=1.44` and record it. A skipped/unrun migration group must be reported as 未验证, never as passing.

## Commit

Commit only the six authorized files locally as:

```
feat(fast-p): implement 02-status-api
```

Exclude `docs/plans/fast/**` reports from that commit; the controller commits evidence separately.
