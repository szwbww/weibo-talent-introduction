# Machine Verification — Manual Expert Material Upload Main

## Epoch 1 — 2026-09-20T08:37:29Z

## Verification Result: BLOCKED

Plan: `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`

Implementation boundary: `d2a7f65ecbc46b5165863dfcab94ae5972f50605..5f4967b8d5f94663266c095e6a2e9ec69f570505` (final source head); review evidence head before this report: `a49e07d5de665ed4f25e009ec1ff99b81bae50f3`.

Convergence: BLOCKED

Manual acceptance: PENDING

Identity: governing/invoked master SHA-256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5` — SAME/CONSISTENT. Fast ledger SHA-256 `de33bffeeac806c9dcedaa02ae9bd5b683feeb03dce517fdfb68bf5d58d7fb77`; handoff SHA-256 `abcddfe7fc04a2ba36513b5b034f2f6151b0dcfe0e8426caab1c691df0b4f2e9`.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/expert-materials.js` | PASS | exit 0 |
| `node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js` | PASS | exit 0; 39 pass, 0 fail |
| `node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/ragKnowledgeBasePage.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js` | PASS | exit 0; 49 pass, 0 fail |
| `node --test src/test/js/*.test.js` | FAIL (record only) | exit 1; 1035 tests, 1034 pass, 1 fail: `meetingCalendar.test.js:188` verbatim calendar S-2 CSS assertion |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test` | BLOCKED | fresh invocation reached Kotlin `compile` after resources; the execution host terminated without Maven exit code/Surefire output |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | BLOCKED | fresh invocation reached the same Kotlin `compile` point; no Maven exit code or IT result available |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | BLOCKED | fresh invocation reached the same Kotlin `compile` point; no Maven exit code or full-suite result available |
| `git diff --check` | PASS | exit 0 |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 sequential child order | PASS | Fast ledger records backend `d2a7f65..80beb2b`, then frontend `80beb2b..5f4967b`; terminal child evidence exists for both. |
| I-2 frozen HTTP contract | PASS | `ExpertMaterialController.kt:81-97` exposes POST `/uploads`, consumes only `file`, returns 201; `expert-materials.js:494-498` posts one FormData field with `headers:{}`; targeted Node suite PASS. |
| I-3 frozen GET/store contract | PASS | `ExpertMaterialService.kt:429-443` projects `MANUAL_UPLOAD/contactId/手动上传/uploadedBy`; `expert-materials.js:665-668` refreshes exclusively through `fetchPage`; targeted Node suite PASS. |
| I-4 exact 104857600-byte boundary | PASS | `ManualExpertMaterialUploadService.kt:177-190,224-229` counts streamed bytes and rejects first byte over `100*1024*1024`; `application.yml:14-25` sets 100MB/101MB; `expert-materials.js:53,465-471` prechecks the same value; targeted Node suite PASS. Java proof remains unavailable. |
| I-5 owner/mail isolation and path safety | PASS (static); Java evidence BLOCKED | V130 SQL lines 24-67 creates manual owner and three-way constraint; service lines 102-145 writes manual/attachment/document in one transaction and cleans final file on error; query service lines 107-119 and 343-391 enforce contact scope. Required Java tests could not complete. |
| I-6 strict authorized scope | PASS | Boundary has exactly 16 business paths: 10 backend-plan paths and 6 frontend-plan paths; no product path outside their union. Additional boundary paths are fast-p evidence only. |
| I-7 publication/cache/rollback contract | PASS (static); manual pending | `index.html` cache tests passed and all 11 versioned assets use `20260920-manual-material-upload`; release/rollback exercise is manual acceptance A-8. |
| I-8 combined gate | BLOCKED | `git diff --check` and focused Node gates PASS. Full Node has one recorded pre-existing red; all three required Maven invocations have no completion evidence from this execution host. |

### Cross-Child Assessment

| Boundary | Verdict | Evidence |
|---|---|---|
| Backend → frontend ordering | PASS | Ledger/handoff show frontend base is backend implementation head `80beb2b`. |
| Endpoint/part/status | PASS | Controller lines 81-97 and frontend lines 494-498 match `POST /api/expert-contacts/{contactId}/materials/uploads`, `file`, 201, multipart headers override. |
| POST → authoritative GET | PASS | Frontend lines 665-668 use `fetchPage(store,{page:0,reason:"manual-upload"})`; no direct material-row insertion found. |
| Source DTO → UI source filter/label | PASS | Query service lines 429-443 and frontend lines 949-987 use one `MANUAL_UPLOAD:contactId` source with label `手动上传`. |
| Capacity/old outbound isolation | PARTIAL | Static implementation is aligned; required Java `OutboundAttachmentServiceTest` did not produce fresh completion evidence. |
| Ownership/security/read path | PARTIAL | Static path/owner analysis is aligned; required Java flow/download/AI tests did not produce fresh completion evidence. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW / BLOCKED evidence | Required Maven command outputs are unavailable because the host ends each fresh process at Kotlin compilation without an exit code. |
| O-1 | PERSISTENT / RECORD_ONLY | Full Node failure at `meetingCalendar.test.js:188`; child evidence establishes the same assertion already failed at frontend child base `80beb2b`, before this feature's frontend change. |
| O-2..O-5 (backend), O-1..O-3 (frontend) | RECORD_ONLY | Preserved from child `verify-log.md` files; none is promoted without fresh mandatory command evidence. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- O-1: The full Node gate is not green: 1034/1035 pass. The sole failing `meetingCalendar.test.js:188` CSS assertion was already false at the frontend child base, per the handoff. It is outside this approved upload plan; it is RECORD_ONLY, not a repairable feature finding.
- Child reports contain additional RECORD_ONLY environment/test limitations (migration Docker API incompatibility, no live full-context smoke, move-failure injection absent, and CSS placement wording). They are not fresh proof of a product defect and are retained as observations only.

### Evidence Boundaries

- All required Maven commands were freshly invoked, but this host stopped each at Kotlin compile without a Maven exit status or test output. Directed behavior, migration compatibility, and full Java regression therefore lack fresh machine evidence.
- Manual A-1 through A-8 remain pending; machine verification cannot replace them.

### Next Action

- Obtain a complete fresh Maven execution environment/log for the three named commands, then rerun `verify-p`. No repair plan is eligible while verification is BLOCKED.

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| backend O-1 cache-key full-Node red | I-8 | RECORD_ONLY; original cache failures resolved, one unrelated baseline calendar failure remains | Fresh full Node: 1034/1035 pass; `meetingCalendar.test.js:188` was already false at frontend base. |
| backend O-2 migration IT V124 | I-5, I-8 | BLOCKED evidence | Fresh migration command produced no Maven exit or IT result on this host. |
| backend O-3 Docker API incompatibility | I-8 | BLOCKED evidence | Fresh migration command stopped at Kotlin compile before Docker/Testcontainers evidence. |
| backend O-4 missing live full-context smoke | I-5 | RECORD_ONLY; manual/end-to-end evidence remains pending | Static chain inspected; required Java evidence is independently BLOCKED. |
| backend O-5 move-failure injection absent | I-2 | RECORD_ONLY | Not a stated mandatory injection; static cleanup path inspected; no fresh Java command completion. |
| frontend O-1 CSS insertion position | I-6 | RECORD_ONLY | Required focused UI/cache tests pass; no product path exceeds the two child allowlists. |
| frontend O-2 calendar CSS red | I-8 | RECORD_ONLY | Same pre-existing `meetingCalendar.test.js:188` failure in fresh full Node run. |
| frontend O-3 additive test stubs | I-3 | RECORD_ONLY | Targeted shared/style/SharePoint Node suite: 39/39 pass. |
