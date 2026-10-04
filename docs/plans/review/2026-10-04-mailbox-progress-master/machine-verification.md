# Aggregate Machine Verification — mailbox-progress-master

## Epoch 1 — 2026-10-05

- Master plan: docs/plans/2026-10-04/mailbox-progress-master.md (sha256 38c25a2e3417a37f76c143791e3c24f472072bfb78c75d9e3104090fe960e315)
- Governing master identity: sha256 38c25a2e3417a37f76c143791e3c24f472072bfb78c75d9e3104090fe960e315; recorded commit 9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4; CONSISTENT; amendments N/A
- Boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..61d630b080266220c078cb38e64bf7f542141e01
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/mailbox-progress-master/repair.md (DRAFT_READY; sha256 b846d272701a44e6449a2d24a833de191b950ea093b3b4cb0e7585fc5fbec28c)

## Verification Result: FAIL

Plan: `docs/plans/2026-10-04/mailbox-progress-master.md`
Identity: SHA256 `38c25a2e3417a37f76c143791e3c24f472072bfb78c75d9e3104090fe960e315`; recorded/invoked commit `9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`; CONSISTENT.
Implementation boundary: `e28e53fd898edd62905a0d45a6bf90396b18b1bf..61d630b080266220c078cb38e64bf7f542141e01`
Evidence HEAD: `1b971f964aff53f745c3e9a2b5b29c1cc15b68da`
Convergence: INITIAL
Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `DB_URL=... DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DmysqlIt=true -DskipNodeTests=true -Dtest=MailboxConversationControllerTest,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT` | BLOCKED | Sandbox exit 1 before tests: `target/classes/application.yml (Operation not permitted)`. Escalation rejected: `localhost/talent_introduction` root DB isolation could not be independently established. Counts N/A. |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DmigrationIt=true -Dapi.version=1.40 -DskipNodeTests=true -Dtest=FlywayMigrationIntegrationTest` | BLOCKED | Reached Kotlin compile only; two bounded attempts timed out and were terminated before Surefire. `FlywayMigrationIntegrationTest` report remains stale at `00:12`, `1/0/0/1`; no fresh count/exit. |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | Exit 0. |
| `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mobileCoreNavigation.test.js` | PASS | Exit 0; 272 pass, 0 fail. |
| `node --test src/test/js/*.test.js` | PASS | First sandbox run blocked writing transient fixture; elevated fresh rerun exit 0; 1453 pass, 0 fail. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | BLOCKED | Timed out during Kotlin compile before Surefire; terminated; no fresh count/exit. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| R-1 | PASS | Status words/order: `mailbox-chat.js:75-84` defines NONE/FOLLOWING/PROVIDED and fixed menu wording/order. |
| R-2 | FAIL | Mandatory long-name title absent: `mailbox-chat.js:1630` renders `<strong>` without `title`; 02 S-2 and master A-4 require full name retained as title. |
| R-3 | PASS | `mailbox-chat.js:80-83` has required labels and no `进入跟进`/`进入提供`. |
| R-4 | PASS; geometry PENDING | Seven-chip source `mailbox-chat.js:58-66`; preserved wrap `styles.css:12648`; real-width browser validation is manual. |
| I-1 | BLOCKED | Source conforms at `V149__add_expert_follow_progress_status.sql` and `ExpertFollowService.kt:84-137`; fresh MySQL/Flyway gates unavailable. |
| I-2 | BLOCKED | Source conforms: `MailboxConversationRepository.kt:189-200`, `:685-700`, `:721-748`; fresh MySQL gate unavailable. |
| I-3 | FAIL | Write/refresh guards at `mailbox-chat.js:2513-2528,4918-4958`; required card-name hover behavior fails as V-1. |
| Legacy mailbox behavior preserved | PASS | Focused Node suite 272/272; no out-of-scope product files. |
| S-1–S-4 | PASS; manual geometry PENDING | Contract block `styles.css:12653-12673`; 11 unified keys in `index.html`; focused tests pass. |
| Prohibited scope/non-goals | PASS | Boundary has exact 10 backend plus 6 frontend authorized files; `git diff --check` clean; no V148 migration, new table, ES, or resource added. |
| Required machine evidence | BLOCKED | Two DB gates and full Maven lack fresh terminal results. |
| Manual A-1–A-7 | PENDING | No isolated deployed/browser acceptance evidence. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `mailbox-chat.js:1630` lacks the required escaped full-name `title`; focused tests do not assert it. |

### Findings

#### P1

- V-1: Long expert names cannot reveal their full value on hover. 02 S-2 explicitly requires `title` retention; master A-4 requires it. Smallest implicated scope: card-name render plus one behavior regression test.

#### P2

- N/A.

#### Observations

- Child 01 O-1: current tree has V149 and no V148. Release sequencing for the parallel V148/V149 migrations remains a human release-coordination check.
- Child 02 O-1: `MailboxConversationRepositoryIT` Surefire XML is stale (`00:12`, `errors=1`); no fresh Maven run reached Surefire.

### Evidence Boundaries

- Fresh MySQL integration evidence unavailable: safety approval rejected unverified `localhost` root DB mutation.
- Fresh Flyway and full Maven evidence unavailable: both remained in Kotlin compile until bounded termination.
- Browser geometry/persistence acceptance remains human-only.

### Repair Planning Result: DRAFT_READY

- Baseline plan: `docs/plans/2026-10-04/mailbox-progress-master.md`
- Verification result: FAIL / INITIAL
- Repair artifact: `docs/plans/fix/mailbox-progress-master/repair.md`
- SHA256: `b846d272701a44e6449a2d24a833de191b950ea093b3b4cb0e7585fc5fbec28c`
- Included: V-1. Excluded: blocked command evidence and RECORD_ONLY items.
- Authorized files: `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxChatBehavior.test.js`.
- One-approval Review-Fast-P execution handoff included; product subject `fix(mailbox-progress): restore card-name title`; evidence subject `docs(review-fast-p): record repair execution`.

No implementation was performed. No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1: V148/V149 coordination | Master migration-number coordination | RECORD_ONLY; human release coordination remains | Current boundary adds V149 only; no V148 migration in scope. |
| 02 O-1: stale Surefire XML | Fresh required test evidence | BLOCKED, not waived | XML stale; fresh Maven/Flyway evidence did not reach Surefire. |

## Epoch 2 — 2026-10-05

- Master plan: docs/plans/2026-10-04/mailbox-progress-master.md (sha256 38c25a2e3417a37f76c143791e3c24f472072bfb78c75d9e3104090fe960e315)
- Governing master identity: sha256 38c25a2e3417a37f76c143791e3c24f472072bfb78c75d9e3104090fe960e315; recorded commit 9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4; CONSISTENT; amendments N/A
- Boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..cc37073675eedf8da87ca4f3856c0d5a10a45b0a
- Evidence boundary: cc37073675eedf8da87ca4f3856c0d5a10a45b0a..e7f4d8ba214394d2152447df4bf8067d0451d89d (repair-execution.md only)
- Reviewer: /root/aggregate_rereviewer
- Result: BLOCKED
- Convergence: BLOCKED
- Repair artifact/result: docs/plans/fix/mailbox-progress-master/repair.md (EXECUTED; sha256 b846d272701a44e6449a2d24a833de191b950ea093b3b4cb0e7585fc5fbec28c)

### Command Evidence

| Command | Result | Fresh evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| Focused six-file Node suite | PASS | exit 0; 273 pass, 0 fail |
| `node --test src/test/js/*.test.js` | PASS | sandbox first blocked transient `target` write; elevated fresh rerun exit 0; 1454 tests, 279 suites, 1454 pass, 0 fail |
| Required Flyway command, exact | BLOCKED | Fresh report: 1 test, 0 fail, 1 error: Docker is required for Flyway migration tests. Harness detached before terminal exit capture. |
| Flyway rerun with reachable OrbStack socket | BLOCKED | Bounded during Kotlin compilation; terminated; no fresh Surefire result. |
| Required MySQL command, isolated DB | BLOCKED | Disposable `mailbox_progress_review_20261005_0125`, then dropped. Controller 41/0/0; Repository 30/0/1; Suspension 10/0/0; aggregate 81/0/1. Only error is unchanged base `MailboxConversationRepositoryIT.kt:226`: `dismissed_at` has no default. Harness detached before terminal exit capture. |
| `JAVA_HOME=... mvn test` | BLOCKED | Not started: bounded Maven/Flyway compilation consumed the verification window; no fresh terminal result. |
| `git diff --check e28e53f..e7f4d8b` | PASS | exit 0 |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| R-1 tabs/order | PASS | `mailbox-chat.js:60-67`; focused Node suite |
| R-2 card three-state control | PASS | `mailbox-chat.js:1589-1644,4919-4964`; focused Node suite |
| R-3 exact menu words/no “进入” | PASS | `mailbox-chat.js:80-85`; style/behavior tests |
| R-4 seven tabs/counts/wrap | PASS; geometry PENDING | `mailbox-chat.js:60-67`, `styles.css:12647-12648`; browser widths are manual |
| I-1 single DB fact/migration | BLOCKED | V149 and `ExpertFollowService.kt:84-137` conform; Flyway success unavailable |
| I-2 ownership, filters, pagination, replied exclusion | BLOCKED | Repository `:189-200,685-727` conforms; MySQL suite has unchanged unrelated error |
| I-3 UI state-transition behavior | PASS | `mailbox-chat.js:2514-2529,4919-4964`; 273 focused tests pass |
| S-1 tabs/wrapping | PASS; geometry PENDING | `styles.css:12647-12648`; Node styles pass |
| S-2 menu/card/title/CSS | PASS | Title at `mailbox-chat.js:1631`; regression `mailboxChatBehavior.test.js:2894-2904`; CSS `styles.css:12653-12673` |
| S-3 no detail duplicate/legacy flows | PASS | Focused behavior/style tests pass |
| S-4 resource/cache/CSS boundaries | PASS | 11 unified `index.html` keys; full Node suite passes |
| Prohibited scope/non-goals | PASS | Exactly 16 product/test paths; no V148/new table/ES/resource |
| Mandatory aggregate gates | BLOCKED | Flyway and full Maven lack completed fresh terminal evidence |
| Manual A-1–A-7 | PENDING | Requires deployed isolated/browser acceptance |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Escaped full-name `title` at `mailbox-chat.js:1631`; special-character long-name regression passes |
| New P1/P2 | N/A | None confirmed |

### Observations

- Child 01 O-1 remains `RECORD_ONLY`: branch has V149 only; isolated review DB avoided shared-schema interference. Human V148/V149 release coordination remains required.
- Child 02 O-1: stale Surefire XML is superseded. Fresh XML reproduces the unchanged pre-base `dismissed_at` error at `MailboxConversationRepositoryIT.kt:226`; not attributed to this plan.
- Historical full-Maven failures were not reused.

### Repair Result

- Repair: `docs/plans/fix/mailbox-progress-master/repair.md` (sha256 `b846d272701a44e6449a2d24a833de191b950ea093b3b4cb0e7585fc5fbec28c`).
- Approved `$execute-p` execution: product `cc37073675eedf8da87ca4f3856c0d5a10a45b0a`; evidence `e7f4d8ba214394d2152447df4bf8067d0451d89d`.
- V-1 resolved. No new repair plan.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1: V148/V149 coordination | Migration-number coordination | RECORD_ONLY | V149 only; human release coordination required. |
| 02 O-1: stale Surefire XML | Fresh required test evidence | BLOCKED | Fresh XML has the unchanged pre-base `dismissed_at` error. |

No product code, tests, review evidence, index, branch, staging, or commits were modified by the reviewer.
