# Aggregate Machine Verification — shared inbox master

## Epoch 1 — 2026-09-23

- Master plan: `docs/plans/2026-09-23/00-shared-inbox-main.md` (sha256 `0b7066e0ef0273aa71c348368ce75248362962c3346f9e3287a04418d5615666`)
- Governing master identity: worktree sha256 `0b7066e0ef0273aa71c348368ce75248362962c3346f9e3287a04418d5615666`; commit `8de18f69a63d1683515dd00c1b46fbddbd644094`
- Master identity state: CONSISTENT; A2 applies M-5's 02 11-file exception, approved HUMAN option A at 2026-09-23 18:58 +0800.
- Boundary: `9237d6f573335d1624217cbc5501f68a6f52b97b..1cff8f650cfa27ca506246380e0e56a77a20a4f9`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: N/A; `repair-p` returned BLOCKED / PLAN_AMENDMENT_REQUIRED.

## Verification Result: FAIL

Plan: `docs/plans/2026-09-23/00-shared-inbox-main.md`

Implementation boundary: `9237d6f..1cff8f6`; evidence head `bce2ae6`

Convergence: INITIAL

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1121 pass, 0 fail |
| Required Maven unit selection | BLOCKED | Fresh Surefire: 221 run, 0 fail/error; wrapper lost exact Maven exit code |
| Required Flyway IT | BLOCKED | Fresh run could not finish under shared build contention; stopped after required source/test compilation to release resources |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 / 01 config | PASS | `MailSenderAccountService.kt:48-72,103-176`; V134 |
| M-2 physical UID | PASS | `AutoMailReplyService.kt:136-166,179-226`; V134 |
| M-3 routing/filter/bounce | FAIL | V-1 below |
| M-4 production gate | PASS, manual PENDING | runbook §§2-10; c4 evidence |
| M-5 serial scope/A1/A2 | PASS | identities, ancestry, authorized diff validated |
| M-6 historical probes | PASS, manual PENDING | runbook §6-10; c4 evidence |
| 01 UI/API/DDL | PASS | `index.html:1798-1816`; controller/domain/V134 |
| 02 owner polling/routing | PASS | `AutoMailReplyService.kt:833-975,1402-1459` |
| 03 probe handling | PASS | `SelfCheckProbeDetector.kt:18-44`; `AutoMailReplyService.kt:123-135` |
| 04 migration evidence | PASS, manual PENDING | `docs/runbooks/repair-lukai-shared-inbox.md:31-222` |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `BounceBackfillService.kt:28-39`; `BounceCollectionService.kt:98-103,176-208` |

### Findings

#### P1

- **V-1 — historical backfill can overwrite its known logical account.** 03 I-1 requires `BounceBackfillService` to retain its passed logical account. It calls generic `ingest` with `row.senderAccountCode`, but `ingest` always substitutes a unique same-group OUTBOUND candidate. A historical row attributed to `Alias` whose referenced OUTBOUND row is `Owner` becomes `Owner`. Existing test only covers no candidate: `BounceCollectionServiceTest.kt:380-398`.

#### P2

- N/A.

#### Observations

- c1 screenshot artifact absent; UI manual acceptance remains pending.
- Nullable `MailSenderAccountService` fallback in `BounceCollectionService` remains unasserted.
- c4 pre-existing orphan tags and production hygiene items remain outside scope.

### Evidence Boundaries

- Exact Maven exits unavailable; Flyway IT incomplete. This does not supersede confirmed V-1.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 screenshot artifact absent | Manual UI acceptance | PENDING | reviewer observation; c1 verification evidence |
| c3 nullable `MailSenderAccountService` fallback unasserted | M-3 bounce attribution | Observation | reviewer observation; `BounceCollectionService.kt` |
| c4 orphan tags and production hygiene | Outside MAIN scope | Observation | reviewer observation; c4 evidence |

### Next Action

- FAIL + INITIAL. `repair-p` found V-1 needs a plan amendment before a repair plan may be drafted.

## Repair Planning Result: BLOCKED

Baseline plan: `docs/plans/2026-09-23/00-shared-inbox-main.md`

Verification result: FAIL / INITIAL

Repair artifact: N/A

### Included Findings

- V-1.

### Required Human Decision

Approve an amendment to phase 03 and MAIN M-5 allowing:

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt`

Repair: mark the backfill caller as known-logical attribution, preserve that code regardless of an OUTBOUND candidate, and add the contradictory-owner regression.

No product code was modified.

## Epoch 2 — 2026-09-23

- Master plan: `docs/plans/2026-09-23/00-shared-inbox-main.md` (sha256 `0b7066e0ef0273aa71c348368ce75248362962c3346f9e3287a04418d5615666`)
- Governing master identity: commit `8de18f69a63d1683515dd00c1b46fbddbd644094`; identity state `CONSISTENT`.
- Amendment: A3, explicitly approved by the user in this task; phase 03/MAIN M-5 authorize `BounceBackfillService.kt` and `BounceBackfillServiceTest.kt` only for V-1.
- Boundary: `9237d6f573335d1624217cbc5501f68a6f52b97b..1cff8f650cfa27ca506246380e0e56a77a20a4f9`
- Reviewer: `/root/amendment_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/00-shared-inbox-main/repair.md` — DRAFT_READY.

## Verification Result: FAIL

Manual acceptance: PENDING.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1121 pass, 0 fail |
| Required eight-class Maven suite | PASS | exit 0; 221 tests, 0 errors/failures/skips |
| `FlywayMigrationIntegrationTest` with OrbStack/API 1.40 | PASS | exit 0; 27 tests, 0 errors/failures/skips |

The first sandbox Maven attempt failed reading `target/classes/application.yml`; the identical permitted rerun passed. No final command evidence is unavailable.

### Contract Matrix

| Contract | Verdict | Evidence |
|---|---|---|
| M-1 / 01 configuration | PASS | source/tests and selected suite |
| M-2 physical UID | PASS | source/tests and selected suite |
| M-3 routing, filtering, bounce | FAIL | V-1 |
| M-4 production gate | PASS, manual PENDING | c4 runbook/evidence |
| M-5 serial scope, A1/A2/A3 | PASS | ordered commits and authorized diff scope |
| M-6 historical probes | PASS, manual PENDING | c4 runbook/evidence |
| 01 UI/API/DDL | PASS | JS, Maven, Flyway fresh evidence |
| 02 owner polling/routing | PASS | selected suite |
| 03 bounce/probe | FAIL | V-1 |
| 04 migration evidence | PASS, manual PENDING | Flyway 27/27; c4 evidence |

### Finding Lineage

| Finding | Prior | Current | Evidence |
|---|---|---|---|
| V-1 | NEW | PERSISTENT | `03-shared-inbox-bounce.md:17,90`; `BounceBackfillService.kt:32-39`; `BounceCollectionService.kt:98-103,176-208` |

### P1

- V-1: historical backfill supplies known `row.senderAccountCode`, then shared ingest overwrites it with a contradictory unique same-group OUTBOUND account. Existing backfill tests omit the conflict regression.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 screenshot artifact absent | Manual UI acceptance | PENDING | c1 evidence |
| c3 nullable `MailSenderAccountService` fallback unasserted | M-3 bounce attribution | Observation | source inspected |
| c4 orphan tags/production hygiene | Outside MAIN scope | Observation | c4 evidence |

## Repair Planning Result: DRAFT_READY

- Included finding: V-1.
- Repair artifact: `docs/plans/fix/00-shared-inbox-main/repair.md`.
- No product code was modified.
