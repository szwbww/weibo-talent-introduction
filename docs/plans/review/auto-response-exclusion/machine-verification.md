# Aggregate Machine Verification — auto-response-exclusion

## Epoch 1 — 2026-10-21T00:27:48+08:00

- Master plan: `docs/plans/2026-10-20/auto-response-exclusion.md` (governing SHA-256 `01004d3ded06a9ac2fcf17ac642a27643445064beb0f342f283b646cc04e7625`; recorded commit `a73ebf38df69b25839e7926bc09f66541574ccb8`)
- Governing master identity: `AMENDMENT_RECORDED`; A1, rule `变更文件清单 / 配套文案计划总量`, reason `缓存键 bump 使两个固定旧 slug 的正则测试必须同改，经用户批准将前端授权文件由 5 扩至 7`, approval `HUMAN:批准扩展授权 (2026-10-20T23:43:31+08:00)`.
- Invoked master identity: SHA-256 `9d77540b671b0cef36f02ea72862215b9f50d928b34e4577540f66f6f1e1472c`.
- Amendment scope: aggregate 15→17 files, companion 5→7 files; retroactively authorized reviewed files are `src/test/js/discoveryVerification.test.js` and `src/test/js/discoveryReview.test.js`, limited to cache-key regex slug assertions.
- Boundary: `5f1e99d4fe024d698759c16ae5e2c9ba40554648..aee499beaaea8c282e6f453d0cecbe863724df01`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/auto-response-exclusion/repair.md` — DRAFT_READY

### Review-P Output

#### Fresh commands

| Command | Result |
|---|---|
| JDK 11 `mvn test` | PASS — 5306 tests, 0 failures, 0 errors, 21 skipped |
| JDK 11 `mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT` | BASELINE_ONLY — 46 tests, 0 failures, 1 known error: `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives`; `dismissed_at` has no default. `MailboxSuspensionServiceIT`: 11/0/0. No candidate failure. |
| `python3 -m py_compile scripts/reclassify-auto-responses.py` | PASS |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS |
| `node --check src/main/resources/static/app.js` | PASS |
| `node --test src/test/js/*.test.js` | PASS — 1733 pass, 0 fail |
| `git diff --check 5f1e99d4fe024d698759c16ae5e2c9ba40554648..aee499beaaea8c282e6f453d0cecbe863724df01` | PASS |

#### Master contract matrix

| Contract | Result | Evidence |
|---|---|---|
| Amendment authority/scope | PASS | A1 recorded; exact 17 product/test paths; two added companion tests limited to slug assertions. |
| I-1 OR rules/header-only input | PASS | Detector only evaluates `Auto-Submitted`/5-minute proof after first-reply gate; no subject/body classifier input. |
| I-2 real time/account/outbound window | PASS | Actual IMAP received date reliable flag; same account successful outbound anchor; `[0,300]` seconds. |
| I-3 restricted state write | PASS | New row only: `MANUAL_REVIEW` → `PROCESSED`, correct reason/type, `resolved_*` untouched. |
| I-4 original flow/outcome/metrics | PASS | Classifier runs after core path in transaction; no early bypass; changed rows return only excluded outcome. |
| I-5 protected/safety branches unchanged | FAIL — V-1 | Historical script can select and update protected fallback rows. Online path passes. |
| I-6 historical audit/idempotence/rollback | PASS | Manifest snapshot, conditional apply, post-write snapshot, and guarded rollback present. |
| I-7 first-reply serialization/history | PASS | Contact `FOR UPDATE`; current locked history reads; no post-save firstness query. |
| T4 historical candidate scope | FAIL — V-1 | Scan does not restrict protected fallback reasons before proof. |
| Companion I-1 data unchanged | PASS | Old tag/filter values, request params, actions, classes retained. |
| Companion I-2 copy complete | PASS | Visible text/ARIA render as `待确认`; backend/tag data remains `待匹配`. |
| Companion I-3 cache-key update | PASS | Versioned resources use `20261020-auto-response-confirmation`; old slug only remains in negative assertion. |
| Companion S-1 structure/style unchanged | PASS | No class/layout/action/filter-key rewrite. |
| Mandatory manual checks | PENDING | Main A-1…A-8; companion A-1…A-3. |

#### Findings

##### V-1 — P1 — NEW — REPAIRABLE

Master I-5/T4 violation. `scripts/reclassify-auto-responses.py:110-117` selects every expert-bound `MANUAL_REVIEW` row without excluding `BODY_TRUNCATED`, `LEGACY_UID_UNVERIFIABLE`, `RECIPIENT_UNRESOLVED`, `CONTACT_NOT_FOUND`, or `INTRODUCTION_NOT_SENT`. `scripts/reclassify-auto-responses.py:374-410` re-fetches, proves, and updates a matching manifest row with no protected-reason guard. A protected historical row can become `PROCESSED/AUTO_RESPONSE_*`, violating mandatory unchanged-safety behavior.

##### V-2 — P2 — NEW — RECORD_ONLY

`InboundAutoResponseDetector.kt:153-156` says identical duplicate headers are accepted; `:161-169` correctly rejects every duplicate. Stale KDoc only; excluded from repair.

##### V-3 — P2 — NEW — RECORD_ONLY

`mailboxChatBehavior.test.js:6849-6853` asserts global literal occurrence count `MAILBOX_TAG_BADGE_LABELS === 2`. Brittle test-maintenance concern; no functional breach; excluded from repair.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-1, history script safety filtering | I-5/T4 protected fallback branches remain unchanged | Promoted to V-1 P1 | Scan and apply lack protected-reason guards. |
| Child 01 O-2, stale KDoc | I-1 header rule documentation | RECORD_ONLY V-2 | Runtime behavior rejects duplicate headers correctly. |
| Child 02 O-1, literal occurrence count | Companion test-maintenance only | RECORD_ONLY V-3 | No functional/master-contract breach. |

### Repair-P Output

- Baseline: `docs/plans/2026-10-20/auto-response-exclusion.md`
- Verification: `FAIL / INITIAL`
- Included: V-1
- Excluded: V-2, V-3
- Result: DRAFT_READY
- Repair artifact: `docs/plans/fix/auto-response-exclusion/repair.md`
- Authorized files: `scripts/reclassify-auto-responses.py`; `scripts/test_reclassify_auto_responses.py`.
- Required handoff: explicit `$execute-p docs/plans/fix/auto-response-exclusion/repair.md`; product commit subject `fix(mail): preserve protected historical auto-response rows`; repair-evidence subject `docs(review-fast-p): record repair execution`.

No product code was modified by aggregate review.

## Epoch 2 — 2026-10-21T09:32:00+08:00

- Master plan: `docs/plans/2026-10-20/auto-response-exclusion.md` (governing SHA-256 `01004d3ded06a9ac2fcf17ac642a27643445064beb0f342f283b646cc04e7625`; recorded commit `a73ebf38df69b25839e7926bc09f66541574ccb8`)
- Governing master identity: `AMENDMENT_RECORDED`; A1, rule `变更文件清单 / 配套文案计划总量`, reason `缓存键 bump 使两个固定旧 slug 的正则测试必须同改，经用户批准将前端授权文件由 5 扩至 7`, approval `HUMAN:批准扩展授权 (2026-10-20T23:43:31+08:00)`.
- Invoked master identity: SHA-256 `9d77540b671b0cef36f02ea72862215b9f50d928b34e4577540f66f6f1e1472c`.
- Amendment scope: aggregate 15→17 files, companion 5→7 files; retroactively authorized reviewed files are `src/test/js/discoveryVerification.test.js` and `src/test/js/discoveryReview.test.js`, limited to cache-key regex slug assertions.
- Boundary: `5f1e99d4fe024d698759c16ae5e2c9ba40554648..de77c293063d0b502a8ac9c99ceeb7d4340101f2`
- Repair lineage: `aee499beaaea8c282e6f453d0cecbe863724df01..de77c293063d0b502a8ac9c99ceeb7d4340101f2`; `DURABLE_HANDOFF`; human approval `$execute-p docs/plans/fix/auto-response-exclusion/repair.md`; only `scripts/reclassify-auto-responses.py` and `scripts/test_reclassify_auto_responses.py` changed.
- Reviewer: `/root/aggregate_rereviewer`
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: `docs/plans/fix/auto-response-exclusion/repair.md` — executed; no new repair plan.

### Review-P Output

#### Fresh commands

| Command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH mvn test` | PASS | exit 0; `BUILD SUCCESS`. |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT` | BASELINE_ONLY | 46 tests, 0 failures; one recorded `dismissed_at` no-default error in `MailboxConversationRepositoryIT.kt:241`; suspension IT passes. |
| `python3 -m unittest scripts/test_reclassify_auto_responses.py` | PASS | exit 0; 3 tests. |
| `python3 -m py_compile scripts/reclassify-auto-responses.py` | PASS | exit 0. |
| `node --check src/main/resources/static/mailbox-chat.js` and `node --check src/main/resources/static/app.js` | PASS | both exit 0. |
| `node --test src/test/js/*.test.js` | PASS | 1733 pass, 0 fail. |
| `git diff --check 5f1e99d4fe024d698759c16ae5e2c9ba40554648..de77c293063d0b502a8ac9c99ceeb7d4340101f2` | PASS | clean. |

The first direct Maven/Node attempts were denied only because the sandbox prevented writes to `target/`; fresh reruns with the required local verification environment supplied the passing evidence above.

#### Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| Authority/boundary/scope | PASS | Master SHA, A1 authority, and fast-p ledger/handoff hashes match; 18 cumulative product/test paths equal the master 17 plus the authorized repair regression test. |
| Repair delta scope | PASS | `aee499b..de77c29` changes only `scripts/reclassify-auto-responses.py` and `scripts/test_reclassify_auto_responses.py`. |
| I-1 | PASS | Detector preserves header-first OR logic and duplicate-header regression coverage. |
| I-2 | PASS | Reliable IMAP date, same-account SENT anchor, and inclusive 0–300-second tests. |
| I-3 | PASS | New `MANUAL_REVIEW` row only; four-field write; excluded outcome. |
| I-4 / X1 / X5 | PASS | Core flow completes before reclassification; batch count excludes the new outcome; mailbox/suspension tests pass. |
| I-5 / X4 | PASS | Online safety branches precede detector; historical protected-reason guards at `scripts/reclassify-auto-responses.py:300-306,404-405`. |
| I-6 | PASS | Dry-run, conditional apply, snapshot rollback; focused repair tests pass. |
| I-7 / X7 | PASS | Contact lock/current reads; real concurrent commit/rollback tests. |
| Companion I-1 / I-2 | PASS | Old filter/API/tag keys retained; display and ARIA copy map to `待确认`. |
| Companion I-3 / S-1 | PASS | One cache key; no CSS/class/action change. |
| Manual A-1–A-8, companion A-1–A-3 | PENDING | Human acceptance only. |

#### Finding lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | All five protected reasons skip before proof and block crafted/stale apply; focused tests cover scan and apply. |
| V-2 | PERSISTENT P2 | `InboundAutoResponseDetector.kt:155` KDoc remains stale; runtime correctly rejects all duplicates. |
| V-3 | PERSISTENT P2 | `mailboxChatBehavior.test.js:6850` literal occurrence-count assertion remains brittle. |

#### Findings

##### P1

N/A.

##### P2

- V-2: stale KDoc only.
- V-3: brittle test-maintenance assertion only.

#### Evidence boundaries

- Manual acceptance is pending.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-1, history-script safety filtering | I-5/T4 protected fallback branches remain unchanged | RESOLVED as V-1 | Guards now skip all five protected reasons in scan and apply. |
| Child 01 O-2, stale KDoc | I-1 header rule documentation | RECORD_ONLY V-2 | Runtime behavior remains correct; comment-only P2. |
| Child 02 O-1, literal occurrence count | Companion test maintenance | RECORD_ONLY V-3 | No functional/master-contract breach. |

Repair planning: N/A.

No product code was modified by aggregate review.
