# Aggregate Machine Verification — wecom-inbound-notification

## Epoch 1 — 2026-10-08T06:48:58Z

- Master plan: docs/plans/2026-10-06/wecom-inbound-notification.md (sha256 22efdecd3d001c8b7f04b30512e2fd644a3487ad19b6feacd5dda035656ee1da)
- Governing master identity: worktree sha256 22efdecd3d001c8b7f04b30512e2fd644a3487ad19b6feacd5dda035656ee1da; recorded commit 4826cbe111310284cf13bfe7fa395bd9e2e122ad; CONSISTENT
- Boundary: 235681497c226066fa0174a2d79bc82863a1e91a..b5452b4ff487766dd69c0ef4b3996dd2b484f1ef
- Reviewer: /root/aggregate_reviewer
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=… mvn test -Dtest=ExpertInboundNotificationServiceTest,ExpertInboundNotificationControllerTest,AutoMailReplyServiceTest` | PASS | Exit 0; 104 JVM tests, 0 failures/errors/skips; JDK 11.0.15. |
| `JAVA_HOME=… PATH=… DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 mvn test -Dtest=ExpertInboundNotificationRepositoryIT -DmysqlIt=true` | PASS | Exit 0; Docker 29.4.0; MySQL 8.0.36; 13 tests, 0 failures/errors/skips; V150 applied; local-only HTTP smoke passed. The initial sandbox attempt lacked Docker-socket permission; required elevated rerun passed. |
| `node --test src/test/js/mailboxGroupPush.test.js` | PASS | Exit 0; 17/17 pass. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 1,520 pass, 286 suites, 0 failures/skips. |
| `git diff --check` | PASS | Exit 0; no output. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-M1 | PASS | V150 singleton setting/outbox; repository locks/reads DB; UI only adopts GET responses: `ExpertInboundNotificationRepository.kt:44-95`, `app.js:18030-53`. |
| I-M2 | PASS | Separate setting/service; no auto-reply-setting writes. Receipt hook is additive: `AutoMailReplyService.kt:153-167`; 104 JVM tests pass. |
| I-M3 | PASS | Frontend calls settings endpoint only; server-only env property: `app.js:18042-70`, `application.yml:117-118`; tests use mocks/loopback, no real credential/group request. |
| I-M4 | PASS | Ordered commits `74ead84` backend before `b5452b4` UI; backend default is disabled; child records terminal. |
| B-I1 | PASS | Default false/generation 0, idempotent setting mutation, DB errors propagate: migration `V150:1-38`, repository `44-68`, JVM/IT tests. |
| B-I2 | PASS | Ordinary receipt only; skip/simulator/duplicate/backfill exclusions: `AutoMailReplyService.kt:153-167,927,1002`; service reads physical processing identity `73-90`. |
| B-I3 | PASS | Dedicated nested savepoint and outer-transaction requirement: repository `64-68`; real rollback/visibility/failed-enqueue tests passed in MySQL IT. |
| B-I4 | PASS | Singleton-first setting/generation gates, cancellation, pre-HTTP authorization: repository `44-61,105-144`; MySQL concurrency tests pass. |
| B-I5 | PASS | Physical unique key, lease token fencing, terminal states, bounded retry/cleanup: migration `24-38`; repository `105-189`; MySQL IT passes. |
| B-I6 | PASS | Dedicated single-thread lifecycle worker, 3s/5s timeout, persisted lease/cooldown: service `94-137`; MySQL two-worker/slow-HTTP tests pass. |
| B-I7 | PASS | Session endpoint, strict boolean, sanitized errors, safe HTTPS Webhook validation: controller `21-57`; service `53-70,199-207`; controller tests pass. |
| B-I8 | PASS | Text-only, bounded cleaned Unicode payload and response-code validation: service `143-203`; focused tests plus local HTTP MySQL smoke pass. |
| UI-I1 | PASS | GET-only authoritative load, refresh/visibility re-read: `app.js:18030-53,18105-07`; 17 focused tests pass. |
| UI-I2 | PASS | Explicit boolean PUT, save lock, reconciliation/unknown state, request sequence: `app.js:18056-94`; focused tests pass. |
| UI-I3 | PASS | No auto-reply/bulk/contact calls; one-time listeners; leave/logout invalidation: `app.js:5033,18102-07,18450`; focused tests pass. |
| UI-I4/S-1/S-2 | PASS | Switch DOM/accessibility `index.html:801-805`; exact scoped CSS/responsive rule `styles.css:12810-21`; cache-key and full JS tests pass. |
| Scope | PASS | All 14 changed `src/main`/`src/test` paths match child 01+02 authorized lists. |
| Manual A-M1/A-M2 | PENDING | Deployment/test-group/mobile/native-browser checks remain human-only. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| N/A | N/A | No prior aggregate report/finding lineage. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- Historical `base..final_code_head` whitespace inspection flags only blank EOF lines in fast-p child evidence artifacts, not product/test paths. Current required `git diff --check` passes.

### Evidence Boundaries

- No machine blocker.
- Real deployment, authorized group send, physical mobile/native foreground behavior remain manual acceptance only.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 native OS foreground evidence limitation | UI-I1 / I-M1 authoritative server refresh | PASS, manual native foreground remains pending | `app.js:18105-07` registers `visibilitychange` and re-GETs only when visible; fresh focused test verifies hidden→0 GET and visible→1 GET. No permanent waiver; actual OS foreground remains manual acceptance. |

### Review-P Result

## Verification Result: PASS

Plan: `docs/plans/2026-10-06/wecom-inbound-notification.md`  
Implementation boundary: `235681497c226066fa0174a2d79bc82863a1e91a..b5452b4ff487766dd69c0ef4b3996dd2b484f1ef`  
Convergence: `INITIAL`  
Manual acceptance: `PENDING`

Repair planning: N/A. No repair artifact created. No product code was modified.
