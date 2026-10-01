# Aggregate Machine Verification — Emailable 验证结果多选放行

## Epoch 1 — 2026-10-02T00:02:58+08:00

- Master plan: `docs/plans/2026-10-01/email-verification-allowlist.md` (sha256 `49d61d1ebb0babee9517010c245827d406916bf2040a2558c0717ccbc2a94b73`)
- Governing master identity: worktree sha256 `49d61d1ebb0babee9517010c245827d406916bf2040a2558c0717ccbc2a94b73`; recorded `commit 143d9caccaef16e848927dd03923f3172f1b74a1`
- Master identity state: CONSISTENT; amendments: N/A
- Boundary: `2b036ccce7e9956f6ea27420d8bc9e057a011da2..e6c36e294cb26ce36684a96a0908c305f871e4b4`
- Reviewer: `/root/aggregate_reviewer`
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

## Verification Result: PASS

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist/docs/plans/2026-10-01/email-verification-allowlist.md`

Implementation boundary: `2b036ccce7e9956f6ea27420d8bc9e057a011da2..e6c36e294cb26ce36684a96a0908c305f871e4b4`

Convergence: INITIAL

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn -B -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,TaskRetentionMigrationTest test` | PASS | exit 0; 268/0/0/0 |
| `mvn -B -DmysqlIt=true -Dapi.version=1.40 -Dtest=BatchEmailVerificationRepositoryIT test` | PASS | exit 0; 17/0/0/0 |
| `mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest test` | PASS | exit 0; 156/0/0/0 |
| `mvn -B -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0; 34/0/0/0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/batchEmailVerification.test.js` | PASS | exit 0; 41/0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1,273/0 |
| `mvn -B test` | PASS | exit 0; BUILD SUCCESS; 4,483/0/0/13; embedded JS 1,273/0 |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Plan identity / ancestry | PASS | SHA-256 `49d61d…4b73`; base→code→evidence ancestry passes; HEAD `7f14bd…`, code head `e6c36e…`. |
| Scope | PASS | 23 product/test files within ordered child allowlists; source/test diff check passes. |
| Backend I-1 snapshot whitelist | PASS | `BatchExecutionModels.kt:18-48,84-97`; null legacy, empty explicit, invalid states rejected. |
| Backend I-2 provider fact vs decision | PASS | `BatchEmailVerificationService.kt:108-176,305-350`; new/in-memory/repository reuse recompute decision. |
| Backend I-3 policy skip isolation | PASS | `BatchEmailVerificationService.kt:200-236`; `ManualInitialOutreachService.kt:760-820`; no tag/contact/SMTP, no success quota use. |
| Backend I-4 reuse / retention | PASS | Reuse predicates symmetrical at `BatchEmailVerificationRepository.kt:214-233`; retention at `TaskExecutionRepository.kt:180-191`. |
| Backend I-5 guards unchanged | PASS | Verification context only when enabled at `ManualInitialOutreachService.kt:572-582`; `markSending` predicate unchanged. |
| Config I-1 single strict column | PASS | V145 adds only nullable text column; strict parse `BatchExecutionModels.kt:515-541`. |
| Config I-2 defaults / legacy preservation | PASS | Create default and update preserve logic at `BatchSendTaskConfig.kt:128-168`, `BatchSendTaskConfigService.kt:105-155,209-248`. |
| Config I-3 snapshot isolation | PASS | `toExecutionSnapshot` copies config value at `BatchExecutionModels.kt:475-502`; scheduled/manual paths consume snapshot. |
| Config I-4 lifecycle compatibility | PASS | `setEnabled`/soft-delete copy existing entity at `BatchSendTaskConfigService.kt:158-194`; no backfill or KV change. |
| Frontend I-1/I-2 selection / toggle | PASS | Real two-panel DOM `index.html:1456-1476,1728-1752`; fixed-order read/fill/disable helpers `app.js:19079-19117`. |
| Frontend I-3 manual isolation | PASS | Snapshot-only POST at `app.js:19215-19240,19882-19896`; independent diff field at `19720-19815`. |
| Frontend I-4 skip explanation | PASS | Policy skip label/priority and `NOT_REQUIRED` rendering at `app.js:20370-20460`. |
| Frontend I-5 cache / preview | PASS | 11 unified `20261001-email-verification-allowlist` references; preview remains existing endpoint at `app.js:19267-19284`. |
| S-1/S-2/S-3 UI contract | PASS | Exact checkbox groups/CSS, no undeliverable input, escaped scope output; focused JS 41/0. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| c1 O-1 baseline count delta | RESOLVED | Fresh targeted suite 268/0; increase is c1+c2 added tests, not a failure/regression. |
| c3 O-1 stale MailboxConversationRepositoryIT report | RESOLVED | mtime `2026-09-29`; fresh required runs pass. |
| c3 slug uppercase observation | PERSISTENT | Global label styling affects `<small>` rendering; exact required S-2 CSS is present. Manual A-5 remains pending. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- Repository-wide `git diff --check` flags committed plan/baseline-log whitespace only; `src/main`/`src/test` diff check passes.
- All backend→config→snapshot→execution→audit→frontend interfaces use `emailVerificationAllowedStates` and `EMAIL_VERIFICATION_POLICY_SKIP` consistently.

### Evidence Boundaries

- Pre-production/browser/SMTP manual acceptance remains PENDING: backend A-1–A-7, config A-1–A-4, frontend A-1–A-6.
- No mandatory machine evidence missing.

### Next Action

- Perform pending human acceptance; then finish branch.

Repair planning: N/A

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1: baseline count delta | Fresh test evidence; no removed/silenced regression | PASS | Fresh targeted suite 268/0; no failures. |
| c3 O-1: stale `MailboxConversationRepositoryIT` report | Fresh required command evidence | PASS | Fresh required runs pass; report predates boundary. |
| c3 uppercase `<small>` observation | Frontend S-2 and manual A-5 visual check | PENDING HUMAN | Exact S-2 CSS passes machine review; visual acceptance remains A-5. |
