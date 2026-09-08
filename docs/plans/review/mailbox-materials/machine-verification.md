# Aggregate Machine Verification — mailbox-materials

## Epoch 1 — 2026-09-08

- Master plan: `docs/plans/2026-09-07/00-mailbox-materials-master.md` (sha256 `2bbfc191ad4a905e5a42dcb34b76bb490661abf243b280764452b5483fdc4044`)
- Governing master identity: worktree sha256 `2bbfc191ad4a905e5a42dcb34b76bb490661abf243b280764452b5483fdc4044`; recorded `commit a61ecb543668532317bebd7864286352dc3359c7`
- Master identity state: CONSISTENT; amendments A1 and A2 are recorded and human-approved in the fast-p ledger.
- Boundary: `8a0c5360e25e875e52800d17797a7b1ea4bd452c..009d9bab431c75184b00659905f80dcde91e3166`
- Reviewer: /root/aggregate_reviewer
- Result: BLOCKED
- Convergence: BLOCKED
- Repair artifact/result: N/A

### Verification Result

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/00-mailbox-materials-master.md`  
Implementation boundary: `8a0c5360e25e875e52800d17797a7b1ea4bd452c..009d9bab431c75184b00659905f80dcde91e3166`; evidence HEAD `91359402f948be0538c579c74404a5ba0a6209b2`; ancestry PASS.  
Contract hashes PASS: master `2bbfc…4044`; ledger `e0e356…d84d`; handoff `036c11…a61`.  
Manual acceptance: PENDING.

### Fresh Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | Exit 0; JS 730/730 pass; Maven BUILD SUCCESS. |
| `node --check src/main/resources/static/expert-materials.js` | PASS | Exit 0. |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | Exit 0. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 730 pass, 0 fail, 136 suites. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test` | BLOCKED | Exit 1; Testcontainers Docker API client 1.32, OrbStack minimum 1.40; `Docker is required for Flyway migration tests`; 1 error. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test` | N/A | Not run after mandatory Flyway gate blocked. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=ImapMetadataFetchIT,AttachmentTransferWorkerIT test` | N/A | Not run after mandatory Flyway gate blocked. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Boundary/identity | PASS | Supplied SHA identities and ancestry verified. |
| G-1..G-6, I-1..I-11 | BLOCKED | Mandatory aggregate integration evidence incomplete because the Flyway gate cannot start. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW BLOCKED | Mandatory Flyway gate cannot reach its test due Docker API incompatibility. |

### Evidence Boundary

- The exact Flyway command cannot run in this environment. The known compatible Docker override is not part of the master-mandated exact command.
- The MySQL integration gates were intentionally not assessed after the mandatory blocking gate.
- No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1..O-4 | G-1..G-4 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 02 O-1..O-2 | G-2, G-3 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 03 O-1..O-4 | G-2, G-3 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 04 O-1..O-3 | G-1, G-3 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 05 O-1..O-4 | G-2, G-3 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 06 O-1..O-4 | G-1, G-2, G-4 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 07 O-1..O-5 | G-1, G-5 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 08 O-1..O-5 | G-2, G-4, G-6 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 09 O-1..O-4 | G-2, G-4 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 10 O-1..O-4 | G-5, G-6 | N/A | Re-evaluation stopped at mandatory V-1 gate. |
| 11 R1..R3 | G-1..G-6 | N/A | Re-evaluation stopped at mandatory V-1 gate. |

Repair planning: N/A. Next action: provide an environment where the exact Flyway command can use Docker API >=1.40, then rerun the complete aggregate verification.

## Epoch 2 — 2026-09-08

- Master plan: `docs/plans/2026-09-07/00-mailbox-materials-master.md` (sha256 `2bbfc191ad4a905e5a42dcb34b76bb490661abf243b280764452b5483fdc4044`)
- Governing master identity: worktree sha256 `2bbfc191ad4a905e5a42dcb34b76bb490661abf243b280764452b5483fdc4044`; recorded `commit a61ecb543668532317bebd7864286352dc3359c7`
- Master identity state: CONSISTENT. A1/A2 are recorded and human-approved. A3 is the exact 2026-09-08 user-approved waiver of only the Flyway command after “忽略flyway 继续”; no product requirement, invariant, migration behavior, manual item, or other command is waived.
- Boundary: `8a0c5360e25e875e52800d17797a7b1ea4bd452c..009d9bab431c75184b00659905f80dcde91e3166`
- Reviewer: /root/aggregate_reviewer_epoch2
- Result: FAIL
- Convergence: DIVERGING
- Repair artifact/result: N/A

### Fresh Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | Exit 0; Maven BUILD SUCCESS; Node 730/730 pass, 136 suites. |
| `node --check src/main/resources/static/expert-materials.js` | PASS | Exit 0. |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | Exit 0. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 730 pass, 0 fail, 136 suites. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test` | N/A | A3 command-only waiver; Testcontainers Docker API 1.32 vs OrbStack minimum 1.40. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test` | PASS | Exit 0; real MySQL; 12 run, 0 fail/error/skip; Flyway validates 120 migrations and schema V121. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=ImapMetadataFetchIT,AttachmentTransferWorkerIT test` | PASS | Exit 0; real MySQL schema V121; 21 run, 0 fail/error/skip: ImapMetadataFetchIT 9, AttachmentTransferWorkerIT 12. |

The first sandboxed MySQL attempt could not write `target/classes`; fresh reruns passed with transient target-output permission.

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Boundary, identity, authorized aggregate scope | PASS | Hash/ancestry valid; product/test changes are in the union of 01–11 authorized files plus A1/A2 additions; `git diff --check` reports only controller-doc whitespace and a pre-existing test EOF blank line. |
| Requirement 1: conversations, outbound-only/waiting/follow | PASS | MailboxConversationRepositoryIT 12/12; controller/service/repository present. |
| Requirement 2: mark processed only; retained mail/metadata view and edit capability | PASS | mailboxChatBehavior included in 730 green Node tests; action is gated by inbound/manual-review. |
| Requirement 3: real navigation, settings, workbench/manual reply, existing controls | FAIL | V-2: status/level controls render empty and cannot submit in real classic-script browser. |
| Requirement 4: metadata-only reply check; no attachment binary fetch | PASS | ImapMetadataFetchIT 9/9 and complete gate 21/21. |
| Requirement 5: shared material UI/state/download service; explicit retrieval only | PASS | ExpertMaterialService, expert-materials.js, and full suites green. |
| Requirement 6: paging/search/filter/cross-page/retry/source/legacy | PASS | Material service/unit/UI tests and integration gates pass. |
| Requirement 7: picker acquire-then-analyze; no implicit download/OCR claim | PASS | ExpertDocumentAnalysisService and expertMaterialAnalysisFlow pass. |
| G-1 | PASS | Real-ID grouping and inbound authority: MailboxConversationRepositoryIT 12/12; schema V121. |
| G-2 | PASS | ImapMetadataFetchIT 9/9; transfer IT 12/12. |
| G-3 | PASS | V120/V121 and IMAP/transfer lifecycle gates pass. |
| G-4 | PASS | Shared expert-materials component/store and material/analysis UI tests pass. |
| G-5 | FAIL | V-2 breaks required status/level settings operation in mandatory host. |
| G-6 | PASS, machine / PENDING, manual | Node contracts pass; A-1/A-8 viewport/browser checks remain pending. |
| Persistence/API/performance/error contracts | PASS | V118–V121 reaches V121; MySQL/IMAP/worker gates pass. |
| Must-not-change behavior | PASS | Full Maven/Node and targeted regressions pass. |
| Explicit non-goals | PASS | No disallowed architecture/product expansion in boundary. |
| I-1..I-9 | PASS | Aggregate tests and child runtime evidence. |
| I-10 mailbox chat frontend | FAIL | V-2 violates I-5/S-3 status/level selection operation. |
| I-11 cache/enablement/rollback | PASS, machine / PENDING, manual | Seven same-key assets ordered in index.html; metadataOnly default true. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED by authority | A3 waives only the environment-blocked Flyway command; migration/product behavior is not waived. |
| V-2 | NEW P1 | `mailbox-chat.js:765-766` reads `global.operatorStatusOptions` and `global.indexLevelOptions`; `app.js:656,665` declares top-level `const`, not `window` properties. |

### P1 — V-2

`mailbox-chat.js:765-766` calls `optionsFromCatalog(global.operatorStatusOptions, …)` and `global.indexLevelOptions`. `app.js:656-670` declares both with top-level `const`; they are global-lexical, not `window` properties in a classic script. `optionsFromCatalog` (`mailbox-chat.js:794-805`) turns undefined into an empty array, so each selector has zero options. `saveSettings` (`mailbox-chat.js:1402-1448`) finds no changed non-empty status/level and makes no POST. This violates requirement 3, G-5, and I-10 I-5/S-3. Existing Node tests mirror the empty catalog state and do not assert populated options.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1..O-4 | G-1..G-4 | Non-blocking: actual-size semantics and historical evidence/default observations remain contract-compliant. |
| 02 O-1..O-2 | G-2/G-3 | Non-blocking: compile-time caps meet values; Surefire accounting is stale. |
| 03 O-1..O-4 | G-2/G-3 | Non-blocking: executable zero-fetch enforcement remains; residual leaf/body-read risks unconfirmed. |
| 04 O-1..O-3 | G-1/G-3/G-4 | Non-blocking: confirm/UIDVALIDITY/owner-bind behavior verified. |
| 05 O-1..O-4 | G-2/G-3 | Non-blocking: evidence-count, direct-DMARC-test, and watchdog-maintenance observations. |
| 06 O-1..O-4 | G-1/G-2/G-4 | Non-blocking: deliberate in-memory metadata projection; later 07 MySQL gate supplies SQL evidence. |
| 07 O-1..O-5 | G-1/G-5 | Non-blocking: dead delegate/evidence count/institution/opt-in MySQL observations; real MySQL gate passes. |
| 08 O-1..O-5 | G-2/G-4/G-6 | Non-blocking: range/controller/display/environment observations. |
| 09 O-1..O-4 | G-2/G-4 | Non-blocking: polling/elvis/evidence observations; backend remains fail-closed. |
| 10 O-1 | G-5/I-5/S-3 | PROMOTED to V-2 P1. |
| 10 O-2..O-4 | I-5/test hygiene/depth | Non-blocking: debug line, unfiltered drawer limitation, DOM test-depth note. |
| 11 R1..R3 | G-1..G-6 | Non-blocking: docs boundary, stale XML exclusion, deliberate negative cache-key assertion. |

### Evidence Boundary

- A3 waives only the stated Flyway invocation.
- A-1 through A-8 real browser/viewport/IMAP-protocol/manual-send acceptance remain PENDING.
- No product/test code was modified; only controller-owned review evidence is modified.

Repair planning: N/A. Result is FAIL / DIVERGING. V-2 needs human adjudication and explicit repair authority; no repair plan is created automatically.
