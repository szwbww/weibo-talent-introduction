# Aggregate Machine Verification — batch-send-rhythm-and-filter

## Epoch 1 — 2026-08-12T16:59:15Z

- Master plan: docs/plans/2026-08-12/batch-send-rhythm-and-filter-00-master.md (sha256: 3878a03be2c8d51cd0c97e6d4eb06749cbe25a30c29931f23b1a1e2aba08fa14)
- Governing master identity: worktree sha256 3878a03be2c8d51cd0c97e6d4eb06749cbe25a30c29931f23b1a1e2aba08fa14; recorded identity commit a6c27bbbca02a3b018d8a16aeb11822abd905e19
- Master identity state: CONSISTENT. Amendment A1 was approved under the master test-file census rule; it authorized the omitted `MailAutomationControllerTest.kt` mechanical 02b deletion.
- Boundary: a6c27bbbca02a3b018d8a16aeb11822abd905e19..c6a02f84eba853aea5484b7ec102edddd85f5138
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/batch-send-rhythm-and-filter-00-master/repair.md — DRAFT_READY
- Product mutation: none by reviewer.

### Fresh command evidence

| Command | Exit | Counts / evidence |
|---|---:|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | Maven aggregate Surefire count unavailable in captured console; embedded JS suite 493 pass, 0 fail. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | 0 | Maven aggregate Surefire count unavailable in captured console; embedded JS suite 493 pass, 0 fail; WAR packaged. |
| `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/loadContactsFilter.test.js src/test/js/batchSendControls.test.js src/test/js/expertTagBatchFix.test.js src/test/js/batchManualExecutionLog.test.js` | 0 | 23 suites; 104 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | 0 | No output. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | 1 test; 0 failures, 1 error. `IllegalStateException: Docker is required for Flyway migration tests`; Testcontainers could not reach `/var/run/docker.sock` or `/Users/lukai/.docker/run/docker.sock`. |
| `git diff --check a6c27bbbca02a3b018d8a16aeb11822abd905e19..c6a02f84eba853aea5484b7ec102edddd85f5138` | 1 | Only child-evidence trailing blank EOF lines; no product-code whitespace defect. |

### Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | V92 removes the scheduled-config `dailyCap` gate; account limits stop only the current execution. |
| M-2 | PASS | `ManualInitialOutreachService.kt:212-216,502-506` enforces `roundsPerRun` from the execution snapshot. |
| M-3 | PASS | `ExpertSearchService.kt:104-116` and `BatchExecutionModels.kt:69-74` preserve multi-region union and retry parity. |
| M-4 | PASS | `app.js:3909,10275,13447-13473` displays Chinese labels while retaining English domain values; focused JS gates pass. |
| M-5 | PASS | `BatchExecutionModels.kt:56-63` and `ExpertSearchService.kt:53-65` handle `UNCLASSIFIED` on ES and retry paths. |
| M-6 | PASS | Controller `:88-89`, service `:399-429`, and repository aggregate query implement cron preview plus next/last times. |
| M-7 | PASS | `app.js:13126-13127` renders the merged execution-time list column. |
| G-1 | PASS | Region values remain English through API/DB/ES; Chinese is display-only. |
| G-2 | PASS | Per-run rounds and account capacity paths remain server-side hard gates. |
| G-3 | PASS | Shared discipline filtering and retry special case preserve `UNCLASSIFIED` parity. |
| G-4 | PASS | Execution loops consume `snapshot.roundsPerRun` and `snapshot.regions`. |
| G-5 | PASS | No scheduler scope change; scheduler regression suite passes. |
| Flyway V91–V93 application | BLOCKED | Mandatory integration test cannot run until Docker is reachable. |

### Findings

| ID | State | Severity | Evidence |
|---|---|---|---|
| V-1 | NEW | P1 | `deepCloneConfig()` at `app.js:13739-13754` omits `roundsPerRun`, while the source summary reads it at `app.js:14049`; configured-source manual confirmation renders `轮次: undefined`. |
| B-1 | BLOCKED | mandatory evidence | Flyway V91–V93 integration evidence unavailable: Docker daemon unreachable. |
| O-1 | PERSISTENT / RECORD_ONLY | N/A | Legacy derived budget reports `ROUNDS_PER_RUN_REACHED`; send volume and IDLE mapping unchanged. |
| O-2 | RESOLVED | N/A | Fresh Maven gate executed the embedded JS suite successfully. |
| R-1..R-4 | PERSISTENT / RECORD_ONLY | N/A | Child-evidence EOF, explicit retained legacy codes, and prior scope/evidence notes; no product violation. |

The review-p result is FAIL / INITIAL. A confirmed mandatory violation (V-1) takes precedence over otherwise missing mandatory evidence; B-1 remains blocked matrix evidence and is included in the repair plan's required verification.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1: legacy stop reason changes to `ROUNDS_PER_RUN_REACHED` | M-2, G-2 | RECORD_ONLY; no mandatory violation | Volume unchanged; COMPLETED-to-IDLE mapping intact. |
| 01 O-2: Maven executes JS suite | Required regression gate | Resolved | Fresh Maven run: 493 JS pass, 0 fail. |
| 02a R-1 / 02b R-1 / 03 R-1 / 04a R-1 / 04b R-2 / 05 R-1 | Diff hygiene | RECORD_ONLY | EOF blank lines are child evidence only, outside product boundary. |
| 02a R-2 / R-3 | M-1 retained legacy behavior | RECORD_ONLY | Explicit `DAILY_CAP_*` annotator codes are zero-diff legacy compatibility, not scheduled-task gates. |
| 02b R-2 / R-3; 03 R-3; 04a R-2 / R-3; 04b R-1 / R-4; 05 R-2 | Aggregate boundary / evidence interpretation | RECORD_ONLY | Fresh combined review found no mandatory product violation from these notes. |
| 02b R-4 / 03 R-2 | Flyway V92/V93 integration | BLOCKED | Fresh mandatory Flyway integration command failed solely because Docker is unreachable. |
| 04b R-3: manual confirmation shows `轮次: undefined` | 04b A-4, M-2 | Promoted to V-1 P1 | `deepCloneConfig()` drops `roundsPerRun`; repair plan prepared. |

## Epoch 2 — 2026-08-13T01:19:18Z

- Master plan: docs/plans/2026-08-12/batch-send-rhythm-and-filter-00-master.md (sha256: 3878a03be2c8d51cd0c97e6d4eb06749cbe25a30c29931f23b1a1e2aba08fa14)
- Governing master identity: worktree sha256 3878a03be2c8d51cd0c97e6d4eb06749cbe25a30c29931f23b1a1e2aba08fa14; recorded commit a6c27bbbca02a3b018d8a16aeb11822abd905e19
- Master identity state: CONSISTENT; approved amendment A1 retained.
- Boundary: a6c27bbbca02a3b018d8a16aeb11822abd905e19..fc136629fc9645334f71a3024c2b6fa96c909dee
- Repair boundary: c6a02f84eba853aea5484b7ec102edddd85f5138..fc136629fc9645334f71a3024c2b6fa96c909dee
- Repair evidence: DURABLE_HANDOFF; repair `docs/plans/fix/batch-send-rhythm-and-filter-00-master/repair.md` sha256 dc965d5aa146074a2d39e2216c0817cc20485d5948d5f8b9263b3648f66d3956; human approval `$execute-p` (2026-08-13); executor Main.
- Reviewer: /root/aggregate_rereviewer
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: N/A; V-1 resolved.
- Product mutation: none by reviewer.

### Fresh command evidence

| Command | Exit | Counts / evidence |
|---|---:|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | 0 | Embedded JS: 496 pass, 0 fail. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn clean package` | 0 | Surefire 2378 run, 0 failures, 0 errors, 4 skipped; WAR built; embedded JS 496 pass, 0 fail. |
| `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/loadContactsFilter.test.js src/test/js/batchSendControls.test.js src/test/js/expertTagBatchFix.test.js src/test/js/batchManualExecutionLog.test.js` | 0 | 23 suites, 107 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | 0 | No output. |
| Plan-specific static checks | 0 | Round quotas contain only round size, remaining estimate, account capacity; one allowed direct `disciplineCategory` term; one `REGION_LABELS`; `index.html` has no `dailyCap`; legacy KV has exactly 3 uses. |
| `git diff --check a6c27bbbca02a3b018d8a16aeb11822abd905e19..fc136629fc9645334f71a3024c2b6fa96c909dee -- src` | 0 | Source-only diff clean. |
| Flyway/MySQL IT | HUMAN-SKIPPED | Explicit user instruction `跳过 MYSQL IT` (2026-08-13); not a blocker. |

### Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | No scheduled config daily-cap operand in introduction/material loops; account-capacity outcomes remain. |
| M-2 | PASS | Snapshot rounds field and both loop caps; exhausted rounds complete then map to IDLE. |
| M-3 | PASS | Multi-region union and retry region parity paths verified. |
| M-4 | PASS | Chinese display labels retain English API/DB/ES values; JS payload checks pass. |
| M-5 | PASS | `UNCLASSIFIED` ES, retry, UI, and config whitelist paths verified. |
| M-6 | PASS | Cron preview plus next/last execution times verified across controller, service, repository, and view. |
| M-7 | PASS | Merged execution-time list column verified. |
| G-1 | PASS | Chinese remains display-only. |
| G-2 | PASS | Server-side rounds plus account capacity remain hard per-run gates. |
| G-3 | PASS | `UNCLASSIFIED` semantics are aligned across config, ES, and retry. |
| G-4 | PASS | Runtime consumes snapshot rounds/regions; no `config.roundsPerRun` loop read. |
| G-5 | PASS | Scheduler regression remains green; no contrary scheduler delta. |
| V91–V93 migration source | PASS | Versions/SQL semantics and no `${...}` verified; runtime MySQL application HUMAN-SKIPPED. |
| Repair scope | PASS | Repair delta contains only authorized `app.js` and JS test; clean worktree. |

### Finding lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | `deepCloneConfig()` preserves `roundsPerRun` with default 1; independent draft defaults 1; confirmation reads it; three regression tests pass. |
| B-1 | HUMAN-SKIPPED / RECORD_ONLY | MySQL/Flyway runtime evidence intentionally not collected by explicit user instruction. |
| O-1 | PERSISTENT / RECORD_ONLY | Legacy compatibility stop-reason mapping does not reintroduce a scheduled daily-cap gate. |
| R-1..R-4 | PERSISTENT / RECORD_ONLY | Pre-existing fast child brief EOF blank lines only; source diff clean. |

No P1/P2 findings. `review-p` / `verify-p` result: PASS. Repair planning: N/A. No product code was modified by the reviewer.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 legacy derived rounds note | M-2, G-2 | RECORD_ONLY | Actual volume/status behavior verified. |
| 02a legacy `DAILY_CAP_*` notes | M-1 | RECORD_ONLY | Compatibility codes remain; scheduled task gate is removed. |
| 02b/03 migration IT notes | Migration verification | HUMAN-SKIPPED | Explicit user instruction; no MySQL runtime evidence claimed. |
| 04b `轮次: undefined` | 04b A-4 | RESOLVED | V-1 repair and fresh regression tests pass. |
| All child EOF/scope/evidence notes | Aggregate boundary hygiene | RECORD_ONLY | No product violation. |
