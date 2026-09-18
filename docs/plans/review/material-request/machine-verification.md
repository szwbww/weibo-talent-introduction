# Aggregate Machine Verification — material-request

## Epoch 1 — 2026-09-18T13:54:40+0800

- Master plan: `docs/plans/2026-09-17/00-material-request-master.md` (sha256 `81b9c1171e70cab9328626ad23aaf34072853f7a7a1fdb076f7f08316e7fb03d`)
- Governing master identity: recorded commit `44136f5dc7bbf94f429afa72c55242c5137d7f4f`; invoked identity `SAME`; state `CONSISTENT`.
- Boundary: `7f7b3a821f09d4255dc735c1e7b96eacf9c32164..75628fb9ae6201e7aa2c26f3dcb880dde8faa9ab`
- Reviewer: `/root/aggregate_reviewer`
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

## Verification Result: PASS

Plan: `docs/plans/2026-09-17/00-material-request-master.md`  
Phase: aggregate/master  
Identity: SHA-256 `81b9c1171e70cab9328626ad23aaf34072853f7a7a1fdb076f7f08316e7fb03d`; recorded/invoked identity `SAME`; state `CONSISTENT`.  
Implementation boundary: `7f7b3a821f09d4255dc735c1e7b96eacf9c32164..75628fb9ae6201e7aa2c26f3dcb880dde8faa9ab` only; evidence HEAD `e7960c7bc2971d51001722262689d9bcfca34806` excluded from code review.  
Convergence: INITIAL  
Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| 01 exact nine-file `node --test …` command | PASS | exit 0; 100 pass, 0 fail |
| 03 targeted `node --test contactHeadLayout … mailboxOutboundAttachments` | PASS | exit 0; 104 pass, 0 fail |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1003 pass, 0 fail |
| `mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest` under JDK 11 | PASS | exit 0; 20 tests, 0 failures/errors |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` under JDK 11 | PASS with unrelated recorded observation | exit 1; 24 tests, 0 failures, 1 pre-existing V124 FK error. Both V128 cases passed; Docker 29.4/Testcontainers MySQL ran successfully. |
| `git diff --check` | PASS | exit 0 |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 independent five-item catalog | PASS | `ExpertMaterialService.kt:50-70,140-152`; service test and UI suite verify fixed order, old seven-item path remains `:109-120`. |
| I-2 sparse tri-state / confirmation boundary | PASS | Shared persistence delete/save path `ExpertMaterialService.kt:161-220`; `materialRequestIntegration.test.js` verifies only PENDING selectable, zero status PUT/send, draft persistence. |
| I-3 API and upload-list isolation | PASS | New routes at `ExpertContactManagementController.kt:258-268`; old GET mapping remains retired at `:245-247`; controller MockMvc test verifies `/materials` is not shadowed. |
| I-4 server text, textual DOM insertion, identity isolation | PASS | Server text catalog `ExpertMaterialService.kt:50-70`; dialog GET/identity checks `mailbox-chat.js:4267-4422`; append uses text nodes `:4439-4450`; targeted XSS/late-response/meeting tests pass. |
| I-5 cache-key contract | PASS | Eleven unchanged-order references share `20260918-material-request-ui` in `index.html:11-15,2110-2115`; exact cache-fixture suite passes. |
| Migration safety | PASS | `V128__add_material_request_codes.sql:15-23` expands only the code CHECK. Fresh V128 and 12-code behavioral migration cases passed. |
| Scope / amendments A1-A2 | PASS | Combined diff contains only approved child files, plans, and execution artifacts; no unexpected product path. |
| Required regressions | PASS | Full JS suite passes; meeting, attachment, manual-send, RAG, legacy-material, and upload pagination paths are covered by targeted/full suites. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 duplicate `materialRequestTriggerHtml()` declaration | NEW P2 | Byte-identical declarations at `mailbox-chat.js:2791-2793` and `4227-4229`; runtime tests pass. |

### Findings

#### P1

- N/A

#### P2

- V-1: Remove the dead duplicate `materialRequestTriggerHtml()` declaration. No observed behavior impact; both declarations are identical.

#### Observations

- Record-only 01/O-1: current cache key is lowercase/hyphen-only and the nine-fixture exact command passes. Charset limitation remains non-blocking.
- Record-only 01/O-2: stale `20260917-calendar-layout-align` plan prose remains documentation drift; live key is `20260918-material-request-ui`.
- Record-only 02/O-1: fresh migration run reproduced only the known V124 `fk_eap_contact` error; it is outside `base..final_code_head` product changes. V128-specific tests passed.
- Record-only 02/O-2: `-Dapi.version=1.44` was required and supplied; Testcontainers connected to Docker successfully.
- Record-only 02/O-3/O-4/O-5: stale guard comment, repeated assertion literals, and historical live-boot evidence remain non-blocking; fresh route/static evidence and MockMvc route tests pass.
- Record-only 03/O-1 is now V-1 above. 03/O-2 remains deployed-environment/manual acceptance work, not a machine-verification failure.

### Evidence Boundaries

- Deployment-only manual acceptance A-1 through A-4 remains pending.
- The V124 migration error is verified pre-existing/accepted record-only evidence, not an introduced master-contract violation.

### Next Action

- Perform pending human acceptance or finish the branch.

Repair planning: N/A

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01/O-1 | I-5 | PASS / non-blocking charset limit | Current key and exact nine-fixture suite pass. |
| 01/O-2 | I-5 | Observation | Stale plan prose; live key verified independently. |
| 02/O-1 | Migration safety | Observation | Fresh run reproduced pre-existing V124 only; V128 cases pass. |
| 02/O-2 | Migration safety | PASS | `-Dapi.version=1.44` enabled Testcontainers. |
| 02/O-3/O-4/O-5 | I-1/I-3 | Observation | Documentation/assertion/history only; fresh behavioral evidence passes. |
| 03/O-1 | I-4 | P2 V-1 | Duplicate declaration has no observed behavior impact. |
| 03/O-2 | A-1 through A-4 | PENDING | Deployment/manual gate remains human-only. |
