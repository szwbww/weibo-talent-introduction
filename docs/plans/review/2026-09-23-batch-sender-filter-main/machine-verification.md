# Aggregate Machine Verification — batch sender filter

## Epoch 1 — 2026-09-23T14:07:13Z

- Master plan: docs/plans/2026-09-23/00-batch-sender-filter-main.md (worktree sha256 5d23d9dab0e2f7fdf8541a393c62f7171aee7d510e2d619f23665d6dababa62e)
- Governing master identity: recorded commit a58ce98be8bb828899dd69c7b8a0282cce35eeb1; AMENDMENT_RECORDED A4, M-5 / G-0, moving this run to V135 without rebase, HUMAN:2026-09-23 选择「本组改用 V135（推荐）」
- Boundary: 9237d6f573335d1624217cbc5501f68a6f52b97b..75cc1714611ac085341cf372d28c057bb332796d
- Reviewer: /root/aggregate_reviewer_epoch1
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/batchSenderFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0; 97 pass / 0 fail |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1137 tests, 227 suites, 0 fail |
| `JAVA_HOME=<zulu-11> mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,ManualInitialOutreachServiceTest test` | PASS | exit 0; 227 tests, 0 failures/errors; fresh XML timestamps 21:54 |
| `DOCKER_HOST=<orbstack> JAVA_HOME=<zulu-11> mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | RECORD_ONLY | exit 1; 27 tests, 25 pass, 1 historical failure, 1 historical error; V135 migration/backfill and fresh-V135 cases pass |

### Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | c1 `248c30a`, then c2 `75cc1714`; both terminal `LIGHT_PASS_WITH_NOTES`; no separate release action. |
| M-2 / I-1 | PASS | V135 adds/backfills non-null JSON; DTO/config/view/snapshot use `senderAccountCodes`; parser rejects malformed JSON; UI sends logical `accountCode` arrays. |
| M-3 / I-3 | PASS | `ManualInitialOutreachService.kt` excludes a bound ORCID before selection/bind/SMTP for retry, ES, and material paths; bound-reason tests pass. |
| M-4 / I-2 / I-4 | PASS | Selected-set gates constrain candidates, capacity, self-check, and selection; empty selection retains legacy behavior; preview/execution reuse target constructors. |
| M-5 / I-5 | PASS | This branch adds V135; parallel worktree has V134; A4–A6 authorize the gap; eleven resource keys use `20260923-batch-sender-filter`. |
| Frontend I-1 | PASS | Account API uses `accountCode`; labels include `senderEmail · accountCode`; historical disabled values remain visible. |
| Frontend I-2 | PASS | Config, echo, editor preview, manual inheritance/diff/preview/execution carry `senderAccountCodes`; independent manual default is `[]`. |
| Frontend I-3 / S-1 / S-2 | PASS | Real-DOM tests pass; both picker IDs/registrations/bindings occur once; no `styles.css` diff or inline styles. |
| Scope | PASS | Product/test delta matches backend plan's 10 files plus frontend plan's 3 files; later commits are evidence only. |
| Manual A-1–A-4 | PENDING | UI/API/SMTP test-environment checks require human acceptance. |

### Finding lineage

| ID | State | Result |
|---|---|---|
| V-1 | NEW — Observation | Flyway V124 fixture FK and V131 history-count residuals predate this feature and lie outside authorized test-target changes; all V135-specific cases pass. |
| V-2 | NEW — Observation | Blank binding-value construction/send nuance affects prohibited dirty data only. |
| V-3 | NEW — Observation | KV-only fallback lacks whitelist propagation; normal config launch uses the persisted full snapshot. |
| V-4 | NEW — Observation | Nullable non-Spring service construction bypasses code-existence validation; production Spring wiring injects it. |
| V-5 | NEW — Observation | Existing manual-picker fallback-key behavior remains unchanged. |
| V-6 | NEW — Observation | UI reader does not dedupe; toggle UI plus backend normalization leave no reachable duplicate path. |
| V-7 | NEW — Observation | c2 literal boundary includes orchestrator evidence, not product changes. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1 historical Flyway residuals | M-5 migration verification | RECORD_ONLY; no V135 failure | fresh 27-test Flyway run |
| c1 O-2 blank binding semantics | M-3 | RECORD_ONLY; prohibited dirty data | construction/send inspection |
| c1 O-3 KV-only fallback | M-1/M-2 | RECORD_ONLY; outside config-row/manual-snapshot scope | launch-path inspection |
| c1 O-4 nullable service | M-2 | RECORD_ONLY; production injection present | service construction inspection |
| c2 O-1 fallback draft key | frontend I-2 | RECORD_ONLY; existing behavior retained | app.js diff |
| c2 O-2 UI dedupe | M-2 | RECORD_ONLY; backend normalizes | UI/backend inspection |
| c2 O-3 evidence docs in boundary | Scope | RECORD_ONLY; no product delta | Git boundary inspection |

No product code was modified by the aggregate reviewer. Repair planning: N/A.
