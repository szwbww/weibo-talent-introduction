# 02-status-api — execution report

Recorded by: Implementer02 (fast-p child 02 implementer)
Date: 2026-09-18 (Asia/Shanghai)

## Execution result: PLAN_CONFLICT

- Plan (canonical): `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request/docs/plans/2026-09-17/02-material-request-status-api.md`
- Plan SHA-256: `58df2a2a97675b425aac96b2a219c664848294ccfa1f7a06681f6e39aa702024`
- Authoritative child brief: `docs/plans/fast/material-request/children/02-status-api/brief.md`, SHA-256 `2f136b89d67ad71d940c8615dadeb98b3a645682c6b8754a95c3052dddfa98ec`
- Master plan: `docs/plans/2026-09-17/00-material-request-master.md`, SHA-256 `81b9c1171e70cab9328626ad23aaf34072853f7a7a1fdb076f7f08316e7fb03d`
- Execution ID: `docs/plans/2026-09-17/02-material-request-status-api.md@58df2a2a97675b425aac96b2a219c664848294ccfa1f7a06681f6e39aa702024`
- Execution epoch: NEW
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request`
- Target branch: `fast/material-request`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request@fast/material-request@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-material-request`
- Pre-execution code SHA (child base): `535f76f3bcbee243ae5bef09686bbe57921075e7`
- Pre-execution HEAD (child 01 evidence commit): `236872237950c53da0081e611dd4ad26d87bd2d2`
- Post-execution code SHA (implementation commit): `5aaa801af8f79de5b429215df64d3163649188f3`
- Evidence HEAD: N/A (this report is the separate evidence artifact; it is intentionally **not** part of the implementation commit)
- Implementation boundary: `535f76f..5aaa801` (six authorized files only; `docs/plans/fast/**` left uncommitted for the controller)

### Why PLAN_CONFLICT

All child-02 work in the six authorized files is implemented, and the new API is verified live end-to-end. However, the child cannot go green inside its authorized scope: adding the two material-request endpoints to `ExpertContactManagementController.kt` shifts a **line-pinned** exclusion in an **unauthorized** test file:

- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:69` pins
  `NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 564, "operatorStatus = operatorStatus")`.
- After child 02, that fragment is at **line 578** and line 564 is now `    ExpertContactResponse(`.
- Result (observed, `ExpertContactManagementControllerTest`-style scoped run below): 79 tests, **1 failure** — `OperatorStatusWriteSeamGuardTest > operator_status write sites exactly match whitelist`, message `排除名单已失效`, observed violation line `com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:578: operatorStatus = operatorStatus,`.
- The file is outside the six authorized files and the brief forbids touching it; the only fix is the standard line-number-only pin update (564 → 578, path/context unchanged) required by the repo rule `K-line-number-guard-breaks-on-any-insertion`. Precedent: commit `c7a0323` updated this exact pin inside the previous material-status plan.
- Main (parent agent) was asked over `hub` and answered **no** — an out-of-list file needs a plan amendment approved by the human. Implementation was therefore committed as-is and the child is reported `PLAN_CONFLICT` with a precise amendment request.

Required amendment (one line, no fragment change):

```diff
-        NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 564, "operatorStatus = operatorStatus"),
+        NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 578, "operatorStatus = operatorStatus"),
```

No other file in the repository is affected: the guard test's `ALLOWED_WRITE_SITES` closure still holds (the change introduces no new `operatorStatus` write site; the violation is only the stale line pin).

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 V128 code-domain widening (I-1, I-2) | IMPLEMENTED | `src/main/resources/db/migration/V128__add_material_request_codes.sql` | Live MySQL 8.0.36 applied 127 migrations to `v128`; `SHOW CREATE TABLE` shows the 12-code CHECK, two-valued status CHECK and unchanged unique key; `V128 …` test added to `FlywayMigrationIntegrationTest.kt` |
| T2 five-item catalogue + state API (I-1…I-4) | IMPLEMENTED | `ExpertMaterialService.kt`, `ExpertContactManagementController.kt` | New enum/DTO/read/write + shared `persistStatus`; MockMvc route tests; live HTTP run |
| T3 targeted verification (I-1…I-4) | IMPLEMENTED (1 command group unverified) | three test files | `ExpertMaterialRequestServiceTest` 10/10, `ExpertContactManagementControllerTest` 10/10; migration-IT group could not start (environment) — see Commands |
| Guard-closure regression of the repo | CONFLICT | `OperatorStatusWriteSeamGuardTest.kt` (not authorized) | 1 failure, see above |

## Commands

All Java commands run with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` in the target worktree.

| # | Command (exact) | Result | Evidence |
|---|---|---|---|
| 1 | `mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest` | **PASS** | exit 0; `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` (`ExpertContactManagementControllerTest` 10, `ExpertMaterialRequestServiceTest` 10); `BUILD SUCCESS`; the same lifecycle also ran the JS gate bound in `pom.xml:186-232` → `node --test src/test/js/*.test.js` **pass 990 / fail 0** |
| 2 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **未验证 (DID NOT RUN)** | attempt A (no env var): hung >20 min inside `org.testcontainers.dockerclient.UnixSocketClientProviderStrategy` after `Loaded … will try it first`; killed by the 2400 s job deadline, no `Tests run:` line. attempt B (`DOCKER_API_VERSION=1.44`, prescribed by the brief): exit 1, `Tests run: 1, Failures: 0, Errors: 1`, `java.lang.IllegalStateException: Docker is required for Flyway migration tests`; all three strategies failed with `BadRequestException (Status 400: {"message":"client version 1.32 is too old. Minimum supported API version is 1.40, please upgrade your client to a newer version"})`. attempt C (`DOCKER_API_VERSION=1.41`): same 400. OrbStack Docker is 29.4.0 / API 1.54; testcontainers 1.19.8 + docker-java 3.3.x probes at API 1.32. Docker itself works on this machine (`docker exec` round-trips in <1 s; the live smoke run migrated a MySQL 8.0.36 container) |
| 3 | `git diff --check` | **PASS** | exit 0, no output |
| 4 | `mvn -q -o test-compile` | **PASS** | exit 0, no diagnostics for changed sources |
| 5 | `mvn test -Dtest=OperatorStatusWriteSeamGuardTest,MailVariableServiceTest,RagProcessContextResolverTest,ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest -DskipNodeTests=true` | **FAIL (1/79)** | `Tests run: 79, Failures: 1, Errors: 0`; the only failure is the guard line pin. Legacy/RAG isolation all green: `MailVariableServiceTest` 54/54, `RagProcessContextResolverTest` 4/4, new service 10/10, new routes 10/10 |
| 6 | Live smoke: `java -cp target/classes:$(mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/fast02-cp.txt -q) com.weibo.talentintroduction.TalentIntroductionApplicationKt` + curl sequence | **PASS** | see “Live HTTP contract record”; Flyway `Successfully applied 127 migrations … now at version v128`; Tomcat `18080`; `Started … in 11.432 seconds` |

Attempt 2 stop rule: after attempt C the intervention was stopped on the parent agent's instruction (“the Docker/OrbStack daemon is wedged … record the migration group as 未验证”), and this report does not claim it as passing.

Launch note for command 6 (verification rig only, no repo file touched): `flyway-mysql` is **test-scoped** in `pom.xml:66-70`, so `mvn spring-boot:run` cannot reach MySQL 8 (`org.flywaydb.core.api.FlywayException: Unsupported Database: MySQL 8.0`). The smoke run therefore used the compiled classes plus the test-scope classpath against a throwaway `mysql:8.0.36` container `fast02-mysql` published on `127.0.0.1:13306` with `DB_URL=…&allowPublicKeyRetrieval=true`, `DB_USERNAME=root`, `DB_PASSWORD=root`, `AUTH_ENABLED=false`, `SERVER_PORT=18080`.

## Live HTTP contract record (required for child 03)

Rig: MySQL 8.0.36 (`fast02-mysql`, port 13306) seeded with `expert_contact.id = 1` (campaign `SIMULATOR`); app on `http://127.0.0.1:18080`; base `B=http://127.0.0.1:18080/api/expert-contacts/1`. The container is left running (with that seeded contact) so verification can reproduce this record; remove it with `docker rm -f fast02-mysql`.

### Routes and verbs

| Route | Verb | Response |
|---|---|---|
| `/api/expert-contacts/{contactId}/material-requests` | `GET` | `200 application/json`, array of 5 objects |
| `/api/expert-contacts/{contactId}/material-requests/{code}` | `PUT` | `200 application/json`, array of 5 objects |
| `/api/expert-contacts/{contactId}/materials` | `GET` | unchanged document paging object (owned by `document/controller/ExpertMaterialController.kt`) |
| `/api/expert-contacts/{contactId}/materials/{materialCode}` | `PUT` | unchanged legacy 7-item array |

### Response shape and field order

Element shape is exactly `[{code,label,status,requestText}]`; in the observed JSON each object serialises its keys in that order (`code`, then `label`, then `status`, then `requestText`). Accepted `PUT` body: `{"status":"PENDING"|"PROVIDED"|"DECLINED"}` (same shape as the legacy `UpdateExpertMaterialStatusRequest`; no other body fields are read).

### Observed `GET /api/expert-contacts/1/material-requests` → `HTTP 200`, 601 bytes, verbatim

```json
[{"code":"REQ_PUBLICATIONS","label":"代表性论文","status":"PENDING","requestText":"Copies of your representative publications"},{"code":"REQ_PROJECTS","label":"科研项目","status":"PENDING","requestText":"Supporting documents for research projects"},{"code":"REQ_PATENTS","label":"专利","status":"PENDING","requestText":"Patent certificates"},{"code":"REQ_AWARDS","label":"荣誉奖项","status":"PENDING","requestText":"Certificates of honors and awards"},{"code":"REQ_DEGREES","label":"学位","status":"PENDING","requestText":"Bachelor’s, master’s, and doctoral degree certificates"}]
```

### Five codes in order, with the verbatim `requestText` copied from the response above

| # | `code` | `label` | `requestText` (copied from the observed response; #5 uses U+2019, not `'`) |
|---|---|---|---|
| 1 | `REQ_PUBLICATIONS` | `代表性论文` | `Copies of your representative publications` |
| 2 | `REQ_PROJECTS` | `科研项目` | `Supporting documents for research projects` |
| 3 | `REQ_PATENTS` | `专利` | `Patent certificates` |
| 4 | `REQ_AWARDS` | `荣誉奖项` | `Certificates of honors and awards` |
| 5 | `REQ_DEGREES` | `学位` | `Bachelor’s, master’s, and doctoral degree certificates` |

### Three-state walk (observed)

1. `PUT /material-requests/REQ_AWARDS` body `{"status":"DECLINED"}` → `HTTP 200`; response array has `REQ_AWARDS` = `"DECLINED"`, other four `"PENDING"`.
2. `GET /material-requests` → `HTTP 200`; `REQ_AWARDS` still `"DECLINED"` (persisted).
3. `PUT /material-requests/REQ_AWARDS` body `{"status":"PENDING"}` → `HTTP 200`; `REQ_AWARDS` back to `"PENDING"`.
4. DB after the walk (`docker exec fast02-mysql mysql …`): `PENDING` left no row; only persisted rows were `CV PROVIDED` and `REQ_DEGREES PROVIDED` (2 rows), i.e. `PENDING` = missing row.

### Cross-checks observed in the same session

- `GET /api/expert-contacts/1/materials?page=0&size=10` → `HTTP 200` `{"items":[],"total":0,"page":0,"size":10,"summary":{"total":0,"stored":0,"metadataOnly":0,"active":0,"failed":0,"sourceUnavailable":0,"defaultAnalysisAttachmentIds":[]}}` — the document paging endpoint is untouched (I-4).
- Legacy `PUT /api/expert-contacts/1/materials/CV` body `{"status":"PROVIDED"}` → `HTTP 200` `[{"code":"CV","label":"简历","status":"PROVIDED"},{"code":"PASSPORT","label":"护照","status":"PENDING"},{"code":"DEGREE","label":"学位","status":"PENDING"},{"code":"EMPLOYMENT","label":"工作","status":"PENDING"},{"code":"PUBLICATIONS","label":"出版","status":"PENDING"},{"code":"PATENTS","label":"专利","status":"PENDING"},{"code":"RESEARCH","label":"研究","status":"PENDING"}]` — legacy 7-item catalogue and shape unchanged.
- New `PUT /material-requests/REQ_DEGREES` `{"status":"PROVIDED"}` → `HTTP 200`; the following `GET /material-requests` shows `REQ_DEGREES` = `"PROVIDED"` and **no** `CV` entry; a legacy `PUT /materials/CV` in the same state still returns the 7-item array with `CV=PROVIDED` (new and legacy rows never infer from each other, I-1).
- Invalid input: `PUT /material-requests/REQ_UNKNOWN` → `HTTP 400` `{"code":"BAD_REQUEST","message":"Unknown material request code: REQ_UNKNOWN","detail":"Bad Request"}`; `PUT /material-requests/REQ_AWARDS` body `{"status":"DONE"}` → `HTTP 400` `{"code":"BAD_REQUEST","message":"Unknown material status: DONE","detail":"Bad Request"}` (rejected before any write).
- `SHOW CREATE TABLE expert_material_status` after `v128`:
  `CONSTRAINT chk_expert_material_code CHECK ((material_code in (_utf8mb4'CV',_utf8mb4'PASSPORT',_utf8mb4'DEGREE',_utf8mb4'EMPLOYMENT',_utf8mb4'PUBLICATIONS',_utf8mb4'PATENTS',_utf8mb4'RESEARCH',_utf8mb4'REQ_PUBLICATIONS',_utf8mb4'REQ_PROJECTS',_utf8mb4'REQ_PATENTS',_utf8mb4'REQ_AWARDS',_utf8mb4'REQ_DEGREES')))`, `chk_expert_material_status CHECK ((material_status in (_utf8mb4'PROVIDED',_utf8mb4'DECLINED')))`, `UNIQUE KEY uk_expert_material_contact_code (expert_contact_id,material_code)` — i.e. V128 ran on a real MySQL 8.0.36 and only widened the code domain.

## Changed Files (implementation commit `5aaa801`, exactly the six authorized files)

- `src/main/resources/db/migration/V128__add_material_request_codes.sql` (new) — drops and re-adds `chk_expert_material_code` with the 7 legacy + 5 `REQ_*` codes; no data DML, no touch to the unique key or the status CHECK.
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt` — adds `ExpertMaterialRequestCode` (5 entries, fixed order, label + English `requestText`) and `ExpertMaterialRequestItem(code,label,status,requestText)`; adds `listMaterialRequests` (read-only) and `updateMaterialRequestStatus`; extracts the existing save/delete branch into one shared `persistStatus` used by both catalogues (no forked persistence logic).
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` — adds `GET /{contactId}/material-requests` and `PUT /{contactId}/material-requests/{code}` (+1 import); legacy non-routed `listMaterials` helper and the legacy `PUT /{contactId}/materials/{materialCode}` untouched.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialRequestServiceTest.kt` (new) — 10 tests: order/labels/status/verbatim English text, read-only GET, stored-row resolution, insert/update/delete transitions, unknown code/status rejected pre-write, cross-catalogue code rejection, missing contact, and a stateful cross-module isolation test (legacy 7-item list + `renderPendingMaterials` + `RagProcessContextResolver` `CV → RECEIVED` after a `REQ_*` write).
- `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` — +3 MockMvc `standaloneSetup(controller)` tests: GET route maps and serialises the exact 5-item JSON, PUT route maps body/path and returns the updated array, and `GET /{contactId}/materials` stays unmapped on this controller (the ambiguity cause of I-4).
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — new `V128 …` test (12 codes acceptable, clause contains all 12, out-of-domain code rejected, `PENDING` still rejected by the status CHECK, unique key still rejects a duplicate, zero rows in before/after) and the 14 `targetSchemaVersion` assertions plus the `fresh database migrates through V128` name moved from `127` to `128`.

## Deviations

1. **PLAN_CONFLICT** — the guard-test line pin (see above) needs an authorized 7th file.
2. **`FlywayMigrationIntegrationTest.kt`: 127 → 128** — mechanical consequence of adding the highest migration `V128`; semantics of the assertions (`migrate to latest`) unchanged, one test name renamed `…through V127` → `…through V128`. No assertion was weakened or deleted.
3. **Migration-IT group unverified** — environment (testcontainers/docker-java probe at API 1.32 vs OrbStack Docker 29.4.0 requiring ≥1.40), not a code failure; `DOCKER_API_VERSION` retries recorded above. Supplementary real-MySQL evidence for V128 exists (live app migrated to `v128` and the final CHECK clause was inspected), but it is **not** the `-DmigrationIt=true` group and must not be reported as that group passing.
4. **`mvn spring-boot:run` cannot serve this app against MySQL 8** — pre-existing repo condition (`flyway-mysql` is test-scoped in `pom.xml`), worked around for the smoke run only.

## Freshness

- Plan identity rechecked: YES (hashes above, read from disk in this invocation)
- Worktree identity rechecked: YES (root/branch/git-dir printed immediately before the commit)
- Reported commit reachable from target branch: YES (`5aaa801` is `HEAD` of `fast/material-request` in the target worktree)
- Required commands run this invocation: YES for commands 1 and 3; command 2 attempted three times and recorded 未验证
- Historical evidence used only as baseline: YES
- Implementation commit contains only the six authorized files: YES (`git show --stat 5aaa801` → 6 files); `docs/plans/fast/**` intentionally uncommitted

## Remaining concerns

1. Guard pin amendment (`OperatorStatusWriteSeamGuardTest.kt:564` → `578`) is required before any `mvn test` on this branch can be green.
2. The migration-IT gate stays 未验证 until testcontainers can talk to this machine's Docker (needs a client that speaks API ≥ 1.40 — e.g. a testcontainers/docker-java bump or an `api.version` setting); the pom is outside child 02's scope.
3. Human acceptance A-1…A-4 need a deployed environment: `mvn spring-boot:run` cannot start against MySQL 8 as the repo stands (`flyway-mysql` test-scoped).
4. Child 03 must consume the recorded `requestText` values verbatim (note U+2019 in #5) and must not re-derive them.
5. Cleanup: throwaway artifacts live outside the repo — container `fast02-mysql`, `/tmp/fast02-run.sh`, `/tmp/fast02-cp.txt`, `/tmp/fast02-*.log`.

## Next Action

- PLAN_CONFLICT → obtain the human's approval to amend child 02 by adding `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` (line-number-only pin 564 → 578) and to record the migration-IT group as 未验证 for this environment; then re-run `verify-p`.

---

# Epoch 2

Recorded by: Implementer02b (fast-p child 02 implementer, execution epoch 2)
Date: 2026-09-18 (Asia/Shanghai)

- Worktree / branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request` @ `fast/material-request`
- Parent authority: amendment **A1** approved by the human at `HUMAN:2026-09-18T09:12+08:00` (recorded in the plan's `## 修正记录` and in the amended brief's 7-file list)
- Epoch-1 implementation commit `5aaa801af8f79de5b429215df64d3163649188f3` was **not** rewritten, reverted or amended; the whole epoch-1 report above is preserved verbatim (this file is append-only).
- Epoch-2 evidence HEAD before the commit: `3fcccc8341fe3965c20a4eaead1ad99965228e51` (docs amendment A1)

## Epoch 2 result: PLAN_CONFLICT

Applied exactly one change (the A1 pin correction). The guard group, the scoped group, the full suite and `git diff --check` are all green. The migration-IT group — which epoch 1 could not even start — **now starts** and consequently exposes **1 failure + 1 error**:

- the **failure is inside child 02's own new test** (`V128 …` in authorized file #6): an assertion that contradicts how MySQL 8.0.36 renders `information_schema.CHECK_CONSTRAINTS.CHECK_CLAUSE` (backslash-escaped literals). The V128 implementation itself is not at fault (behaviourally re-verified below);
- the **error is pre-existing** and is *not* caused by child 02: it reproduces unchanged at `child_base_sha` `535f76f3bcbee243ae5bef09686bbe57921075e7` in a scratch clone (proof below).

Because a required command fails, the child cannot be declared `READY_FOR_VERIFICATION`. Repairing the assertion needs an authorization decision (this epoch was scoped to the A1 pin only, and "do not change any other file"), so the epoch returns `PLAN_CONFLICT` with a one-line amendment request in "Next Action".

## A1 correction — pinned site before/after

| | value |
|---|---|
| Pin before (blob of `5aaa801`, `OperatorStatusWriteSeamGuardTest.kt:69`) | `NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 564, "operatorStatus = operatorStatus"),` |
| Pin after (this epoch, same line 69) | `NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 578, "operatorStatus = operatorStatus"),` |
| Actual site in `ExpertContactManagementController.kt` | `578:        operatorStatus = operatorStatus,` (`grep -n "operatorStatus = operatorStatus"`) |

Evidence commands and output:

```text
$ git show 5aaa801:src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | sed -n '67,70p'
        // 专家联系人列表响应 DTO 构造：查询参数回显到 DTO
        // （plan 01 新增 materials GET/PUT 端点与 UpdateExpertMaterialStatusRequest DTO 使 :549 偏移至 :564）
        NoiseSite("…/campaign/controller/ExpertContactManagementController.kt", 564, "operatorStatus = operatorStatus"),

$ sed -n '67,70p' src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
        // 专家联系人列表响应 DTO 构造：查询参数回显到 DTO
        // （plan 01 新增 materials GET/PUT 端点与 UpdateExpertMaterialStatusRequest DTO 使 :549 偏移至 :564）
        NoiseSite("…/campaign/controller/ExpertContactManagementController.kt", 578, "operatorStatus = operatorStatus"),

$ grep -n "operatorStatus = operatorStatus" src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt
578:        operatorStatus = operatorStatus,

$ git diff -- src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
-        NoiseSite("…/campaign/controller/ExpertContactManagementController.kt", 564, "operatorStatus = operatorStatus"),
+        NoiseSite("…/campaign/controller/ExpertContactManagementController.kt", 578, "operatorStatus = operatorStatus"),
```

The diff is **one line**, one insertion / one deletion. Path string, fragment text `operatorStatus = operatorStatus`, the surrounding comment and every other `NoiseSite` are untouched; no assertion was added, removed, reordered or weakened. The pin now equals the observed site, which is why the previously red guard test is green (command 1).

## Commands (exact, JDK 11)

`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` → `openjdk 11.0.15 (Zulu11.56+19-CA)`, verified with `$JAVA_HOME/bin/java -version`. Run in the target worktree.

| # | Command | Exit | Result | Counts / timing |
|---|---|---|---|---|
| 1 | `mvn test -Dtest=OperatorStatusWriteSeamGuardTest` | **0** | **PASS** (`BUILD SUCCESS`) | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` — `OperatorStatusWriteSeamGuardTest` 1/1; `Total time: 02:06 min` (finished `2026-09-18T09:15:55+08:00`); log `/tmp/e2_guard.log` |
| 2 | `mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest` | **0** | **PASS** (`BUILD SUCCESS`) | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` (`ExpertContactManagementControllerTest` 10, `ExpertMaterialRequestServiceTest` 10); `Total time: 01:50 min` (finished `09:17:50`); log `/tmp/e2_scoped.log` |
| 3 | `mvn test` | **0** | **PASS** (`BUILD SUCCESS`) | `Tests run: 3465, Failures: 0, Errors: 0, Skipped: 13`; `OperatorStatusWriteSeamGuardTest` 1/1 green inside the full run; `FlywayMigrationIntegrationTest` reported `Skipped: 1` (opt-in `migrationIt` not set in this invocation); exec-plugin JS gate `node --test src/test/js/*.test.js` → `tests 990 / pass 990 / fail 0`; `Total time: 02:53 min` (finished `09:20:50`); log `/tmp/e2_full.log` |
| 4 | `git diff --check` | **0** | **PASS** | no output (no whitespace/conflict errors) |
| 5 | see below (migration group retry) | **1** | **FAIL** (`BUILD FAILURE`) | `Tests run: 24, Failures: 1, Errors: 1, Skipped: 0`; `Total time: 05:12 min` (finished `09:26:18`); log `/tmp/e2_migration_it.log` |

## Command 5 — migration group retry (epoch-1 Docker blocker solved, two failures revealed)

### Exact command and pins

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
export DOCKER_API_VERSION=1.44          # verified: `env | grep DOCKER` → DOCKER_API_VERSION=1.44
export MAVEN_OPTS=-Dapi.version=1.44
mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44
```

This is the single bounded retry. `Docker server 29.4.0 / client 29.4.0` (OrbStack), `env | grep DOCKER` printed only the pinned `DOCKER_API_VERSION=1.44`.

**Why the epoch-1 400-error is gone:** testcontainers 1.19.8 (pinned in `pom.xml:85`) shades docker-java; the constant pool of `org/testcontainers/shaded/com/github/dockerjava/core/DefaultDockerClientConfig` contains exactly the keys `DOCKER_HOST`, `DOCKER_TLS_VERIFY`, `DOCKER_CERT_PATH`, `DOCKER_CONFIG`, `DOCKER_CONTEXT` plus the system-property / docker-properties key **`api.version`** — there is **no `DOCKER_API_VERSION` env key**, which is why epoch 1's env-var-only attempts still negotiated client API 1.32 and got `Status 400 … client version 1.32 is too old`. The pin must be the `api.version` system property (surefire forwards `-D` user properties to the forked JVM — the same mechanism that makes `-DmigrationIt=true` enable this opt-in class). With it:

```text
[INFO] Running com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest
tc.testcontainers/ryuk:0.7.0 - Container testcontainers/ryuk:0.7.0 started in PT0.609414S
tc.mysql:8.0.36 - Creating container for image: mysql:8.0.36
tc.mysql:8.0.36 - Container mysql:8.0.36 is starting: 3c0fbe2544cca0dd4b77df30b8bc5939027adc9446397c3aafbc28e2f168698d
tc.mysql:8.0.36 - Waiting for database connection to become available at jdbc:mysql://localhost:32769/talent_introduction using query 'SELECT 1'
```

The group therefore **ran** (24 tests over ~200 s of real migrations on a real MySQL 8.0.36 container) — it is *not* reported as 未验证, and it is *not* passing:

```text
[ERROR] Tests run: 24, Failures: 1, Errors: 1, Skipped: 0, Time elapsed: 200.909 s <<< FAILURE!
[ERROR] Failures:
[ERROR]   FlywayMigrationIntegrationTest.V128 widens the material code check to twelve codes without touching stored rows:196 clause dropped legacy CV ==> expected: <true> but was: <false>
[ERROR] Errors:
[ERROR]   FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger:297->execute:1280 » SQLIntegrityConstraintViolation
[ERROR] Tests run: 24, Failures: 1, Errors: 1, Skipped: 0
```

### Failure A — child 02's own new `V128` test assertion (root cause measured, not inferred)

`FlywayMigrationIntegrationTest.kt:190-196` reads `CHECK_CLAUSE` from `information_schema.check_constraints` and asserts `clause.contains("'$code'")`. MySQL 8.0.36 renders that column with a charset introducer **and backslash-escaped literals**, so the substring `'CV'` does not exist even though `CV` does. Reproduced on a fresh `mysql:8.0.36` with the same DDL sequence as V111 + V128 (`docker run … mysql:8.0.36`, verbatim output of `SELECT CHECK_CLAUSE FROM information_schema.check_constraints WHERE constraint_name='chk_expert_material_code'`):

```text
(`material_code` in (_latin1\'CV\',_latin1\'PASSPORT\',_latin1\'DEGREE\',_latin1\'EMPLOYMENT\',_latin1\'PUBLICATIONS\',_latin1\'PATENTS\',_latin1\'RESEARCH\',_latin1\'REQ_PUBLICATIONS\',_latin1\'REQ_PROJECTS\',_latin1\'REQ_PATENTS\',_latin1\'REQ_AWARDS\',_latin1\'REQ_DEGREES\'))
```

(`_latin1` because that constraint was created on a latin1 session; a Flyway/JDBC-created clause renders `_utf8mb4\'…\'` — the epoch-1 smoke's `SHOW CREATE TABLE` prints the *unescaped* `_utf8mb4'CV'` form because it is a different renderer, which is exactly why this assertion looked correct during epoch-1 offline review. Both introducers carry escaped quotes, so `contains("'CV'")` is false either way — and the JDBC path is the one that actually observed `false`.)

The implementation is **not** at fault — on that same 12-code constraint (MySQL 8.0.36):

| Probe | Observed |
|---|---|
| insert all 7 legacy + all 5 `REQ_*` codes | accepted → `COUNT(*) = 12` |
| `material_code='REQ_UNKNOWN'` | rejected, `ERROR 3819: Check constraint 'chk_expert_material_code' is violated` |
| `material_status='PENDING'` | rejected, `ERROR 3819: Check constraint 'chk_expert_material_status' is violated` |
| duplicate `(1,'REQ_DEGREES')` | rejected, `ERROR 1062: Duplicate entry '1-REQ_DEGREES' for key 'expert_material_status.uk_expert_material_contact_code'` |

So V128 widens the domain exactly as designed (I-1/I-2) and the two-valued status CHECK + unique key are intact; only the clause-text assertion is defective. Because assertions run in order, the behavioural checks that follow it (12 inserts, three rejections, `COUNT(*) = 12`) never executed.

**Minimal repair (NOT applied — needs authorization):** make the clause-text check escape-agnostic, e.g.

```kotlin
val normalizedClause = clause.replace("\\'", "'")   // I_S escapes literals with backslashes
legacyCodes.forEach { code -> assertTrue(normalizedClause.contains("'$code'"), "clause dropped legacy $code") }
requestCodes.forEach { code -> assertTrue(normalizedClause.contains("'$code'"), "clause missing $code") }
```

(equivalently: drop the text assertion in favour of the already-present behavioural inserts, which are the stronger oracle). Authorized file #6 already covers this file, but the epoch-2 mandate was "apply ONLY the A1 line-number correction", so no edit was made.

### Error B — pre-existing defect, proven not caused by child 02

```text
java.sql.SQLIntegrityConstraintViolationException: Cannot add or update a child row: a foreign key constraint fails
  (`talent_introduction`.`expert_application_promotion`, CONSTRAINT `fk_eap_contact` FOREIGN KEY (`expert_contact_id`) REFERENCES `expert_contact` (`id`))
    at …FlywayMigrationIntegrationTest.execute(FlywayMigrationIntegrationTest.kt:1280)
    at …FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger(FlywayMigrationIntegrationTest.kt:297)
```

The test does `flyway.clean()` → `flyway.migrate()` (no base seed) and then inserts `expert_contact_id = 1`; `fk_eap_contact` comes from `V15__add_mail_monitoring_columns_and_promotion_audit.sql:42-44`, and after `clean()` no contact `1` exists. Child 02's only change to that test is `"127"` → `"128"` in the `targetSchemaVersion` expectation (full diff reviewed); the INSERT, the FK and the migration set are untouched.

Proof by running the same class at `child_base_sha` in a scratch clone outside the repo (no worktree created/moved/deleted, nothing pushed/merged/rewritten):

```bash
rm -rf /tmp/e2-base-check
git clone -q --no-hardlinks /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request /tmp/e2-base-check
cd /tmp/e2-base-check && git checkout -q --detach 535f76f3bcbee243ae5bef09686bbe57921075e7
JAVA_HOME=…/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 MAVEN_OPTS=-Dapi.version=1.44 \
  mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44
```

```text
[ERROR] Tests run: 23, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 226.447 s <<< FAILURE!
[ERROR]   FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger:233->execute:1216 » SQLIntegrityConstraintViolation
[INFO] BUILD FAILURE   (Total time: 05:19 min, exit 1, finished 2026-09-18T09:33:28+08:00)
```

Base = **23 tests, 0 failures, 1 error, same test, same FK constraint** ⇒ error B is a latent repo-level defect of the opt-in migration group (it had never been runnable on this machine before) and is outside child 02's authorized scope. At HEAD the same run adds only child 02's new `V128` test (24 tests, failure A).

## Commit

- Commit: **`71e5874`** — `feat(fast-p): implement 02-status-api`, created with `git -c user.name=omp -c user.email=omp@local commit`.
- Content: **one file** — `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` (`1 insertion(+), 1 deletion(-)`).
- The other six authorized files are byte-identical to epoch 1's `5aaa801` (`git diff 5aaa801..HEAD --stat` shows only the guard file plus the controller's docs commit; `git show --stat 71e5874` shows exactly the one file). The child's authorized seven-file set therefore spans `5aaa801` (six files) + `71e5874` (guard pin, authorized by A1).
- `docs/plans/fast/**` deliberately **excluded** from the implementation commit (still modified in the worktree: `brief.md`, `execution.md`, `ledger.md`) — the controller commits the evidence separately.
- No other file was added, modified or deleted by this epoch; `git status --porcelain` after the commit lists only the three `docs/plans/fast/**` files.

## Interface record for child 03 — unchanged

Epoch 2 touched **no** `src/main` byte and no route (`git diff 5aaa801..HEAD` = guard test only), so the epoch-1 record stands as the observed contract: `GET /api/expert-contacts/{contactId}/material-requests` → `200`, 5-element array with field order `[{code,label,status,requestText}]`; `PUT …/material-requests/{code}` body `{"status":"PENDING"|"PROVIDED"|"DECLINED"}` → `200`, same array; codes in order `REQ_PUBLICATIONS, REQ_PROJECTS, REQ_PATENTS, REQ_AWARDS, REQ_DEGREES`; labels `代表性论文、科研项目、专利、荣誉奖项、学位`; five English `requestText` values verbatim (note U+2019 in #5); legacy `GET /materials` still returns the `ExpertMaterialPage` object and legacy `PUT /materials/{materialCode}` still returns the 7-item array. No re-observation was required or performed in epoch 2.

## Remaining concerns (epoch 2)

1. **Failure A must be repaired** before the migration group can go green: the `V128 …` test's clause-text assertion contradicts MySQL's `information_schema.CHECK_CLAUSE` escaping. One-line fix proposed above; needs authorization (this epoch was scoped to A1 only).
2. **Error B (`V124 allows material attached promotion audit trigger`) is pre-existing** (reproduced at `535f76f`, unchanged by child 02) and needs an owner decision — e.g. seed the base contact (`migrateToV23AndSeedBase()`) before the insert, or insert the contact row inline. Out of child 02's authorized scope; must not be attributed to child 02.
3. **Docker/testcontainers runbook:** on this machine the migration group is only runnable with `-Dapi.version=1.44` (system property, *not* `DOCKER_API_VERSION`); the oracle for that is the shaded `DefaultDockerClientConfig` constant pool. Without it the group is unstartable (epoch-1 evidence).
4. Guard pin is now green and exactly aligned with `ExpertContactManagementController.kt:578`; any future insertion in that controller shifts it again (K-line-number-guard-breaks-on-any-insertion).
5. Epoch-1's concerns 3 (human acceptance A-1…A-4 need a deployed environment) and 4 (child 03 must consume the recorded `requestText` verbatim) remain open unchanged.
6. Scratch artifacts from this epoch live outside the repo: logs `/tmp/e2_guard.log`, `/tmp/e2_scoped.log`, `/tmp/e2_full.log`, `/tmp/e2_migration_it.log`, `/tmp/e2_base_migration_it.log`, base clone `/tmp/e2-base-check` (removed after use), throwaway container `e2-checks` (removed after use); epoch 1's `fast02-mysql` container is left as documented above.

## Next Action (epoch 2)

- **PLAN_CONFLICT.** Request the human/controller to authorize, by amending child 02's change list:
  1. the one-line **test-assertion repair** in authorized file #6 `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` (normalize `CHECK_CLAUSE` before the `contains` checks, or rely on the behavioural inserts) — child 02's own new test, no production-code change; and
  2. the disposition of the **pre-existing** `V124` FK error (fix it in a repo-level/other child's scope, or record it as a known-red opt-in-group test).
- After 1, re-run `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` (expect `Failures: 0` and only the pre-existing `V124` error) plus `mvn test -Dtest=OperatorStatusWriteSeamGuardTest`; then hand the child to `verify-p`.

---

# Epoch 3

Recorded by: Implementer02c (fast-p child 02 implementer, execution epoch 3 / human-arbitrated repair round 1)
Date: 2026-09-18 (Asia/Shanghai)

- Worktree / branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request` @ `fast/material-request`
- Base of this epoch: pause commit `24f2dde2df36ea35e2f14630ab83386f50ec479d` (child code head `71e587477e0b2defb9775042ae8e9cee03365321`)
- Authority: human arbitration 2026-09-18 (this session), following verifier `LightVerifier02`'s **PAUSE** on gate 3 — the behavioural-probe option was chosen over the `CHECK_CLAUSE` normalization option. Recorded in `fix-log.md` → `## Epoch 3 — Round 1/3`.
- Epochs 1 and 2 are preserved verbatim above (this file is append-only).

## Epoch 3 result: FIXED

Applied exactly the arbitrated correction to child 02's own migration test: the two `clause.contains("'$code'")` loops and the `information_schema.CHECK_CLAUSE` query that only they used are deleted; every other assertion in that test is byte-identical. The migration-IT group now reports `Failures: 0` with only the pre-existing `V124` FK error remaining, the full suite is BUILD SUCCESS, and `git diff --check` is clean.

## Change — one authorized file, six deleted lines

File: `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` (authorized file #6), test `V128 widens the material code check to twelve codes without touching stored rows`.

```diff
@@ -189,12 +189,6 @@ class FlywayMigrationIntegrationTest {
             val requestCodes =
                 listOf("REQ_PUBLICATIONS", "REQ_PROJECTS", "REQ_PATENTS", "REQ_AWARDS", "REQ_DEGREES")
-            val clause = connection.queryString(
-                "SELECT CHECK_CLAUSE FROM information_schema.check_constraints " +
-                    "WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_expert_material_code'"
-            )
-            legacyCodes.forEach { code -> assertTrue(clause.contains("'$code'"), "clause dropped legacy $code") }
-            requestCodes.forEach { code -> assertTrue(clause.contains("'$code'"), "clause missing $code") }

             // 旧 7 代码与新 5 代码都可写（V128 只扩大域，不删旧代码）。
             legacyCodes.forEach { code ->
```

- `git diff --stat` = `1 file changed, 6 deletions(-)`; no insertion.
- Retained unchanged: `legacyCodes` / `requestCodes` (still drive the insert loops), the `Connection.queryString` helper (used by ~30 other assertions of the same file), and every other assertion of the test — `migrateToV23AndSeedBase()`, `targetSchemaVersion == "128"`, pre-insert `COUNT(*) == 0`, the `chk_expert_material_code` / `chk_expert_material_status` / `uk_expert_material_contact_code` existence checks, the 7 + 5 inserts, `COUNT(*) == 12`, the `REQ_UNKNOWN` / `PENDING` / duplicate-row rejections, and the closing `COUNT(*) == 12`.
- Rationale (arbitrated): MySQL renders `CHECK_CLAUSE` with a charset introducer and backslash-escaped literals (`_utf8mb4\'CV\'`), so the substring `'CV'` is absent from a clause that does contain `CV` — the assertion tested MySQL's renderer, not the constraint domain. The behavioural probes that remain prove the same domain on a real MySQL 8.0.36: all 12 required codes accepted, `REQ_UNKNOWN` rejected, `PENDING` rejected, duplicate `(1,'REQ_DEGREES')` rejected, `COUNT(*) == 12` before and after — the stronger oracle, and exactly what T3/验收标准 ("V128 CHECK 实施") demands.

## Commands (JDK 11)

`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` → `openjdk 11.0.15 (Zulu11.56+19-CA)`, confirmed with `$JAVA_HOME/bin/java -version` in this epoch.

| # | Command (exact) | Exit | Result | Counts / timing |
|---|---|---|---|---|
| 1 | `MAVEN_OPTS=-Dapi.version=1.44 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **1** | **environment red — group did NOT start** | `Tests run: 1, Failures: 0, Errors: 1`, `IllegalStateException: Docker is required for Flyway migration tests`; all strategies `Status 400: client version 1.32 is too old … minimum 1.40`; `Total time: 01:39 min`; log `/tmp/e3_migration_it.log` |
| 1b | `MAVEN_OPTS=-Dapi.version=1.44 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` | **1** | **target state reached**: only the pre-existing V124 error | `Tests run: 24, Failures: 0, Errors: 1, Skipped: 0`; container `mysql:8.0.36` started, Flyway `Successfully applied 104 migrations … now at version v128`; `V128 …` testcase present with no failure/error child (`time="9.155"`); sole red `V124 allows material attached promotion audit trigger:291->execute:1274 » SQLIntegrityConstraintViolation … fk_eap_contact`; `Total time: 04:58 min`; log `/tmp/e3_migration_it_retry.log` |
| 2 | `mvn test` | **0** | **PASS** (`BUILD SUCCESS`) | `Tests run: 3465, Failures: 0, Errors: 0, Skipped: 13`; `OperatorStatusWriteSeamGuardTest` 1/1; `FlywayMigrationIntegrationTest` skipped (opt-in flag absent); JS gate `node --test src/test/js/*.test.js` → `tests 990 / pass 990 / fail 0`; `Total time: 03:12 min` (finished `2026-09-18T10:11:09+08:00`); log `/tmp/e3_full.log` |
| 3 | `git diff --check` | **0** | **PASS** | no output (no whitespace/conflict errors) |

### Command 1 — why the env-only form still cannot start the group

`MAVEN_OPTS` sets system properties on the **Maven** JVM only; surefire's forked test JVM does not inherit them (it does inherit `-D` user properties passed on the Maven command line). Testcontainers 1.19.8's shaded docker-java reads `api.version` from the system property / docker-properties key, not from any `DOCKER_API_VERSION` env key (verifier record **O-2**; epoch 2's constant-pool oracle). Hence attempt 1 negotiated client API 1.32 and every strategy failed the server's ≥1.40 minimum, while attempt 1b — identical plus `-Dapi.version=1.44` — started the container and ran all 24 tests. The working invocation is the epoch-2/verifier form; the env-var-only form is recorded here so the runbook gap stays visible.

### Command 1b — the two reds, separated

- **Child 02's `V128 …` test: now GREEN.** Epoch 2's `Failures: 1` (line 196 `clause dropped legacy CV ==> expected: <true> but was: <false>`) is gone: `Failures: 0`, and the surefire XML `<testcase name="V128 widens the material code check to twelve codes without touching stored rows" … time="9.155"/>` carries no `<failure>`/`<error>` child. The behavioural probes that previously never executed (the failing assertion preceded them) now run and pass.
- **`V124 allows material attached promotion audit trigger`: pre-existing, out of scope.** `SQLIntegrityConstraintViolationException … CONSTRAINT fk_eap_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact (id)` at `:291->execute:1274`. Reproduced untouched at `child_base_sha` `535f76f` (23 tests, 0 failures, **1 error, same test, same FK** — epoch-2 scratch-clone run, independently re-reproduced by `LightVerifier02`). This epoch's change cannot reach it: the only edit is six deleted lines inside a different test method, and V124's INSERT / FK / migration set are untouched. It was neither repaired nor attributed to child 02. Note the `:297`/`:233` line drift is itself an artifact of the intra-file line numbering (V124 sits *after* the V128 test), not of V124's content.

## Commit

- Commit: **`4a91a6135c031cc60fd8091b1fe8ae61f77c4a2b`** — `fix(fast-p): repair 02-status-api round 1`, created with `git -c user.name=omp -c user.email=omp@local commit`.
- Content: **one file** — `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` (`1 file changed, 6 deletions(-)`).
- Parent: `24f2dde` (pause). No history rewritten, nothing pushed, merged, rebased, amended, squashed or deleted.
- `docs/plans/fast/**` deliberately **excluded** from the implementation commit (controller commits evidence separately).
- The child's authorized seven-file set now spans `5aaa801` (six files) + `71e5874` (A1 guard pin) + `4a91a61` (this repair); `git diff --name-only 535f76f..4a91a61 -- src` is exactly the authorized 7-path list. `git status --porcelain` after the commit listed only the two `docs/plans/fast/**` evidence files.

## Interface record for child 03 — unchanged

Epoch 3 touched no `src/main` byte and no route, no DTO and no catalogue string; the epoch-1 live contract record stands verbatim: `GET /api/expert-contacts/{contactId}/material-requests` → `200`, 5-element array, field order `[{code,label,status,requestText}]`; `PUT …/material-requests/{code}` body `{"status":"PENDING"|"PROVIDED"|"DECLINED"}` → `200`, same array; codes in order `REQ_PUBLICATIONS, REQ_PROJECTS, REQ_PATENTS, REQ_AWARDS, REQ_DEGREES`; labels `代表性论文、科研项目、专利、荣誉奖项、学位`; the five English `requestText` values verbatim (U+2019 in #5); legacy `GET /materials` still returns the `ExpertMaterialPage` object and legacy `PUT /materials/{materialCode}` still the 7-item array.

## Remaining concerns (epoch 3)

1. **Pre-existing `V124` FK error still reds the opt-in migration group repo-wide.** Reproduced at `child_base_sha`; it had never been runnable on this machine before child 02. Needs an owner decision outside child 02's scope (e.g. seed the base contact before the insert). Must not be attributed to child 02; `-DmigrationIt=true` cannot be fully green until it is owned.
2. **Runbook gap (O-2):** the migration group is startable only with `-Dapi.version=1.44` as a **Maven command-line** system property (not `DOCKER_API_VERSION`, not `MAVEN_OPTS` alone). Any future runbook/brief line prescribing the env-var form should be corrected.
3. **O-3 stale comment (not fixed):** `OperatorStatusWriteSeamGuardTest.kt:67-68` still says `使 :549 偏移至 :564` while the A1 pin is 578. Documentation-only drift; A1 authorized exactly the number, and this epoch's mandate was the test assertion only.
4. Epoch-1/2 concerns unchanged and still open: human acceptance A-1…A-4 need a deployed environment (`mvn spring-boot:run` cannot serve MySQL 8 as the repo stands — `flyway-mysql` is test-scoped); child 03 must consume the recorded `requestText` verbatim (U+2019).
5. Scratch artifacts outside the repo: logs `/tmp/e3_migration_it.log`, `/tmp/e3_migration_it_retry.log`, `/tmp/e3_full.log`; testcontainers MySQL/ryuk containers are reaped by ryuk; epoch-1's `fast02-mysql` container remains as documented above.

## Next Action (epoch 3)

- **FIXED.** Hand back to `verify-p` (round 1 re-verification): the in-scope red is repaired, `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` reports `Failures: 0` with only the pre-existing `V124` error, `mvn test` is BUILD SUCCESS (3465/0/0/13), and `git diff --check` is clean.
