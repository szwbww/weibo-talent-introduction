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

## Epoch 3 — 2026-09-20T09:33:40Z

- Master plan: `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md` (sha256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5`)
- Governing master identity: sha256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5`; recorded commit `d2a7f65ecbc46b5165863dfcab94ae5972f50605`
- Master identity state: CONSISTENT
- Approved amendment: A-01, `docs/plans/2026-09-20/00-manual-expert-material-upload-main-amendment-01.md` (commit `dd173172c1d92a6c371bcc4f41780447a27e7bbd`); master I-8 migration command only; human approval `批准` on 2026-09-20
- Boundary: `d2a7f65ecbc46b5165863dfcab94ae5972f50605..5f4967b8d5f94663266c095e6a2e9ec69f570505`
- Reviewer: `/root/aggregate_reviewer_amended`
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: N/A; repair-p returned NO_ACTION

### Required Commands — Fresh

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test` | exit 1; RECORD_ONLY Node gate | Selected Surefire: 107 tests, 0 failures/errors/skips; Maven fails only at node-test: 1035 tests, 1034 pass, one baseline calendar failure. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -DargLine="-Dapi.version=1.43" -DskipNodeTests=true -Dtest='FlywayMigrationIntegrationTest#V130*' test` | exit 0 | A-01 compatibility command; `FlywayMigrationIntegrationTest#V130*`: 1/1 pass; migrated to V130. |
| `node --check src/main/resources/static/expert-materials.js` | exit 0 | PASS. |
| `node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js` | exit 0 | 39/39 pass. |
| `node --test src/test/js/*.test.js` | exit 1; RECORD_ONLY | 1034/1035 pass; sole failure `meetingCalendar.test.js:188`, predating the frontend child base. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | exit 1; RECORD_ONLY Node gate | Surefire: 3503 tests, 0 failures/errors, 13 skipped; only failing Maven step is the same node-test result. |
| `git diff --check` | exit 0 | PASS. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 sequential execution | PASS | Backend then frontend terminal child order is recorded. |
| I-2 HTTP contract | PASS | `ExpertMaterialController.kt:81-97`; `expert-materials.js:491-498`; focused Node PASS. |
| I-3 authoritative GET/store contract | PASS | `ExpertMaterialService.kt:429-443`; `expert-materials.js:664-668`; focused Node PASS. |
| I-4 exact 104857600-byte boundary | PASS | `ManualExpertMaterialUploadService.kt:167-197,224-229`; `application.yml:14-25`; directed Java PASS. |
| I-5 owner/mail isolation/path safety | PASS | V130 SQL:24-67; atomic write `ManualExpertMaterialUploadService.kt:102-145`; owner/read scope `ExpertMaterialService.kt:78-119,343,429-443`; V130 and directed Java PASS. |
| I-6 scope boundary | PASS | Exactly 16 product/test paths: backend 10 plus frontend 6 allowlists; later changes are plans/review evidence only. |
| I-7 cache/release/rollback | PASS static; manual pending | 11 unified cache keys; focused cache/SharePoint tests PASS; release drill stays A-8. |
| I-8 combined gates | PASS | Fresh gates completed; sole known baseline calendar red is RECORD_ONLY. |
| I-A1 environment compatibility replacement | PASS | Exact A-01 V130 command exit 0; 1/1 V130 test passes. |
| I-A2 non-expansion of amendment | PASS | Only migration command replaced; all other gates ran; master A-1 through A-8 remain pending. |

### Cross-Child Assessment

| Boundary | Verdict | Evidence |
|---|---|---|
| Backend → frontend ordering | PASS | Frontend base follows backend implementation head. |
| Endpoint/part/status | PASS | Backend/frontend agree on endpoint, multipart `file`, 201, and `headers:{}`. |
| POST → authoritative GET | PASS | Successful upload refreshes with GET only; no direct material-row insertion. |
| Source DTO → UI source filter/label | PASS | Manual source resolves as `MANUAL_UPLOAD:contactId`, labeled `手动上传`. |
| Capacity/old outbound isolation | PASS | Streamed 100 MiB limit and old outbound directed tests pass. |
| Ownership/security/read path | PASS | V130 migration and directed Java tests cover the owner/write/read chain. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | A-01 Docker API 1.43 command completed V130 successfully. |
| O-1 aggregate/calendar | PERSISTENT / RECORD_ONLY | 1034/1035 Node; present at frontend child base. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| backend O-1 cache-key Node red | I-8 | RECORD_ONLY | Original cache-key failures resolved; only unrelated calendar baseline remains. |
| backend O-2 V124 migration | I-5, I-8 | RECORD_ONLY | A-01 narrows the migration gate to V130 only. |
| backend O-3 Docker API 1.32/1.40 | I-8 | Historical, superseded | A-01 API 1.43 command passes. |
| backend O-4 no live/full-context smoke | I-5 | RECORD_ONLY | Manual E2E remains pending. |
| backend O-5 no move-failure injection | I-2 | RECORD_ONLY | Not mandatory; cleanup path and target flow tests pass. |
| frontend O-1 CSS placement | I-6 | RECORD_ONLY | Allowlist is respected; focused CSS tests pass; no authorized non-regressive change. |
| frontend O-2 calendar CSS red | I-8 | PERSISTENT / RECORD_ONLY | Present before frontend child boundary. |
| frontend O-3 additive stubs | I-3 | RECORD_ONLY | Focused 39/39 suite; no weakened assertion evidenced. |

### Findings

#### P1

- N/A

#### P2

- N/A

### Evidence Boundaries

- Manual A-1 through A-8 remain pending.
- The full Node/Maven nonzero result is the known pre-existing `meetingCalendar.test.js:188` calendar CSS assertion; it is not in this product boundary.

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

## Epoch 2 — 2026-09-20T09:04:35Z

- Master plan: `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md` (sha256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5`)
- Governing master identity: sha256 `402676caa56035706601113d0b690d6d5b5d29617aef0d01cabbfb328379b4a5`; recorded commit `d2a7f65ecbc46b5165863dfcab94ae5972f50605`
- Master identity state: CONSISTENT; invoked identity SAME; amendments N/A
- Boundary: `d2a7f65ecbc46b5165863dfcab94ae5972f50605..5f4967b8d5f94663266c095e6a2e9ec69f570505`
- Reviewer: `/root/aggregate_reviewer`
- Result: BLOCKED
- Convergence: BLOCKED
- Repair artifact/result: N/A; repair-p is not eligible

### Required Commands — Fresh

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test` | exit 1; RECORD_ONLY Node gate | Selected Surefire: 107 tests, 0 failures/errors/skips. Maven fails only at `node-test`: 1035 tests, 1034 pass, 1 baseline calendar failure. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | exit 1; BLOCKED | 1 test, 0 failures, 1 error; Testcontainers Docker client API 1.32 is below engine minimum 1.40. |
| `node --check src/main/resources/static/expert-materials.js` | exit 0 | PASS. |
| `node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js` | exit 0 | 39 tests, 39 pass, 0 fail. |
| `node --test src/test/js/*.test.js` | exit 1; RECORD_ONLY | 1035 tests, 1034 pass, 1 fail: `meetingCalendar.test.js:188`. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | exit 1; RECORD_ONLY Node gate | Surefire: 3503 tests, 0 failures/errors, 13 skipped; only failing Maven step `node-test`, same 1034/1035 Node result. |
| `git diff --check` | exit 0 | PASS. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 sequential execution | PASS | Ledger records backend `d2a7f65..80beb2b`, then frontend `80beb2b..5f4967b`; both terminal LIGHT_PASS_WITH_NOTES. |
| I-2 HTTP contract | PASS | `ExpertMaterialController.kt:81-105`; frontend FormData and `headers:{}` are covered by focused Node 39/39. |
| I-3 authoritative GET/store contract | PASS | `ExpertMaterialService.kt:803-857`; frontend success refreshes via GET only; focused Node passes. |
| I-4 exact 104857600-byte boundary | PASS | `ManualExpertMaterialUploadService.kt:177-229`; target Java reports include 13 flow tests green; old outbound suite has 28 green. |
| I-5 owner/mail isolation/path safety | PASS static/unit; migration runtime BLOCKED | V130 lines 24-67; upload transaction `ManualExpertMaterialUploadService.kt:102-145`; scoped resolver/source path in `ExpertMaterialService.kt:107-120,803-857`. |
| I-6 scope boundary | PASS | Exactly 10 backend plus 6 frontend allowlisted product/test files; no extra product path. |
| I-7 cache/release/rollback | PASS static; manual pending | 11 cache keys unified; release/rollback remains A-8 manual. |
| I-8 combined gates | BLOCKED | Focused gates and diff check pass; migration IT has no Docker-compatible environment evidence. Full Node/Maven red is established unrelated baseline and remains RECORD_ONLY. |

### Cross-Child Assessment

| Boundary | Verdict | Evidence |
|---|---|---|
| Backend → frontend ordering | PASS | Frontend base is backend implementation head `80beb2b`. |
| Endpoint/part/status | PASS | Backend and frontend match `POST /api/expert-contacts/{contactId}/materials/uploads`, multipart `file`, HTTP 201, and empty JSON-header override. |
| POST → authoritative GET | PASS | Successful upload calls `fetchPage`; no direct material-row insertion. |
| Source DTO → UI source filter/label | PASS | One `MANUAL_UPLOAD:contactId` source, labeled `手动上传`. |
| Capacity/old outbound isolation | PASS | Streamed 100 MiB limit and old outbound tests have fresh directed Java evidence. |
| Ownership/security/read path | PASS static/unit; migration runtime BLOCKED | Owner/write/read chains align; migration IT cannot reach Docker. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | PERSISTENT / BLOCKED, narrowed | Epoch 1 had no Maven completions. Targeted/full Maven now complete and Java-green; migration remains blocked by Docker API incompatibility. |
| O-1 | PERSISTENT / RECORD_ONLY | Full Node remains 1034/1035; `meetingCalendar.test.js:188` predates frontend child base. |
| backend O-2 | BLOCKED evidence / RECORD_ONLY | Exact migration command stops before V124 on Docker API mismatch. |
| backend O-3 | PERSISTENT / RECORD_ONLY | Fresh Testcontainers failure: client API 1.32, engine minimum 1.40. |
| backend O-4, O-5 | RECORD_ONLY | Full-context smoke/manual gap; move-failure injection is not mandatory. |
| frontend O-1 | RECORD_ONLY | CSS placement has no authorized non-regressive repair; focused CSS tests pass. |
| frontend O-2 | PERSISTENT / RECORD_ONLY | Fresh full Node repeats baseline calendar assertion. |
| frontend O-3 | RECORD_ONLY | Additive stubs; focused suite 39/39 passes. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- Full Node/Maven failures are solely the pre-existing calendar CSS assertion, not this boundary.
- Required migration IT cannot run on this host: Testcontainers/docker-java API `1.32` is below Docker engine minimum `1.40`.

### Evidence Boundaries

- No fresh migration verification of V130 against Docker/MySQL.
- Manual A-1 through A-8 remain pending.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| backend O-1 cache-key Node red | I-8 | RECORD_ONLY | Full Node fresh: 1034/1035; remaining calendar failure already existed at frontend base; 16 original cache-key failures resolved. |
| backend O-2 V124 migration IT | I-5, I-8 | BLOCKED evidence / RECORD_ONLY | Exact migration command fails before V124 on Docker API mismatch. |
| backend O-3 Docker API incompatibility | I-8 | PERSISTENT / RECORD_ONLY | Fresh Testcontainers error: client API 1.32, engine minimum 1.40. |
| backend O-4 no live/full-context smoke | I-5 | RECORD_ONLY | Static/runtime unit paths inspected; A-1 manual E2E remains pending. |
| backend O-5 no move-failure injection | I-2 | RECORD_ONLY | Not an enumerated mandatory injection; temp cleanup/static ordering inspected; target flow suite green. |
| frontend O-1 CSS physical insertion position | I-6 | RECORD_ONLY | Focused CSS tests pass; product boundary is within child allowlists; no authorized non-regressive repair. |
| frontend O-2 calendar CSS red | I-8 | PERSISTENT / RECORD_ONLY | Fresh full Node reproduces `meetingCalendar.test.js:188` baseline failure. |
| frontend O-3 additive test stubs | I-3 | RECORD_ONLY | Focused shared/style/SharePoint suite: 39/39 pass; no weakened assertion evidenced. |

### Next Action

- Provide a Docker/Testcontainers-compatible environment, rerun the exact migration command, then rerun aggregate `verify-p`.

No product code was modified.
