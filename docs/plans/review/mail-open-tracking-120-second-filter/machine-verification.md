# Aggregate Machine Verification — mail-open-tracking-120-second-filter

## Epoch 1 — 2026-09-28T11:03:00+08:00

- Master plan: `docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md` (sha256 `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`)
- Governing master identity: worktree sha256 `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`; recorded commit `3237f07e694565bda5e0e2d6a453fc654d695014`
- Master identity state: CONSISTENT; amendments: N/A
- Boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..e0b002076b92d3e1e543040fb5640585fc2869aa`
- Evidence head: `fabfbd87fac219fbefb5fd67133ecaaedcd0b3e0`
- Reviewer: `/root/aggregate_reviewer` (fresh, dispatched after final code commit; distinct from recorded fast-p roles `Impl01Backend`, `Verify01Backend`, `Impl02Ui`, `Verify02Ui`)
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A
- Product modification by reviewer: none

### Fresh command evidence

| Command | Exit | Result |
|---|---:|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -B -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | Real MySQL IT: 6 run, 0 failures/errors/skips; report timestamp `2026-09-28 10:59:14 +0800`. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest,SmtpMailDeliveryServiceTest test` | 0 | 47 run (4 + 3 + 40), 0 failures/errors/skips; report timestamp `2026-09-28 11:02:56 +0800`. |
| `node --test src/test/js/mailOpenTracking.test.js` | 0 | 11 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | 0 | Syntax pass. |
| Product `git diff --check` | 0 | No whitespace errors. |

The master describes full `mvn test` as conditional (“按需要”), so it is not a required gate.

### Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 | PASS | One strict `last_open_at > DATE_ADD(sent_at, INTERVAL 120 SECOND)` predicate drives summary, filters, row and detail; fresh MySQL IT covers 119.999999s, 120s and 120.000001s. |
| I-2 | PASS | `recordSignal` stays unchanged; GET writes and HEAD does not. IT proves early-only remains `NO_SIGNAL` through time passage and a second `recordSignal(+121s)` flips it. |
| I-3 | PASS | Raw `first_open_at`/`last_open_at` mappings are unchanged; IT verifies early/later values and UI names them “首次图片请求”/“最近图片请求”. |
| I-4 | PASS | Base outbound/SENT/sent-at constraints and summary independence from status/keyword remain; IT covers untracked, failed, inbound, orphan, account/date/paging and zero denominator. |
| I-5 | PASS | Exact explanatory copy, status labels and metrics are present in `index.html`/`app.js`; stale labels are absent. |
| S-1 | PASS | Static DOM retains IDs/classes and nine columns; all 11 versioned assets use `20260928-mail-open-120s`. |
| S-2 | PASS | `OPENED` renders `badge info`; `NO_SIGNAL` renders `badge warn`; CSS diff is zero; dynamic metrics/detail labels are exact. |
| Scope/non-goals | PASS | Product/test delta is exactly the five authorized files; no migration, CSS, send-chain, controller or service change. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| `01-backend/O-1` | I-1/I-3 shared 120-second predicate | PASS / `RO-B-1` | `NO_SIGNAL` is the approved explicit complement of `QUALIFIED`, using the same `CUTOFF`; no violation. |
| `01-backend/O-2` | Required verification evidence | PASS / `RO-B-2` | Bound Maven exec runs repository-wide Node suite; baseline-consistent cost only. |
| `02-ui/O-1` | Authorized cumulative scope | PASS / `RO-U-1` | Range contains fast-p evidence commits, but implementation commit `e0b0020` changes only its three authorized UI files. |

### Findings

No P1/P2 findings. No prior aggregate finding lineage. A-1..A-6 remain human-only checks on an independent clean acceptance DB; machine PASS does not mark them passed.
