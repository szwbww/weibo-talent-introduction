# Child 01 Pause — Epoch 1 PLAN_CONFLICT

- Child: 01 (`docs/plans/2026-09-29/bounce-alert-observability.md`)
- Plan identity: `sha256:7db733f98f985a6e83b28d7776c236a6ae7d7dcb00a59cc077041f945ef3f614`
- Epoch: 1; fix_round: 0
- Writer: agent `ImplBounce01` (execute-p), 2026-09-29
- Base: `18c79797ef87022d0fd134d7890759993e377059`
- Product commit: `ff0d1ebc52eae3812c994cb8764e04ef455455c0` (`feat(fast-p): implement 01`, 7/7 authorized files)
- Execution report: `children/01/execution.md` (uncommitted at pause)

## Required commands on `ff0d1eb`

| Command | Result |
|---|---|
| `mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test` (JDK 11) | PASS — 23 tests / 0 failures (baseline 13) |
| `node --test src/test/js/senderBindingDisplay.test.js` | PASS — 13 pass / 0 fail (baseline 8) |
| `node --check src/main/resources/static/app.js` | PASS — exit 0 |

## Why the child paused

Child 01's own three required commands pass, but landing its product change breaks the repo-wide JS suite that later children must keep green (`child 03` requires full `mvn test`). Two failures at `ff0d1eb` (`node --test src/test/js/*.test.js` → 1230 pass / 2 fail):

### F-1 (plan conflict — unauthorized file): `providerUndeliveredColumn.test.js:232-235`

- Assertion: `assert.ok(!appJsSource.includes("hardBounceCount"))` (plus the same form for `softBounceCount`) — a **global** app.js source-substring negation.
- Child 01 I-4 freezes the sender-account DTO field name `hardBounceCount` and S-1 requires `loadAccounts` to render it, so `app.js` must read that exact name (`app.js:3285-3294`). The two requirements are mutually exclusive; obfuscating the property name was rejected.
- The assertion's origin plan `docs/plans/2026-09-02/provider-undelivered-column.md` scopes invariant I-6 to the **provider-distribution chain only** (`ProviderStatRow` + `providerDistribution` + the two `app.js` consumers) and explicitly forbids a repo-wide "zero hits" acceptance (`I-6 范围限定（必读）`, lines 106-114, Phase-3 item 7).
- `src/test/js/providerUndeliveredColumn.test.js` is not in child 01's authorized list; no authorized file can repair it. → requires a plan amendment.

### F-2 (fixable inside authorized files): `taskActivityCenter.test.js:696-702`

- `taskActivityCenter.test.js` derives `CACHE_KEY` from `index.html` and asserts the current key literal appears **only** in `index.html` — not in any `src/main/resources/static/*.js` or `src/test/js/*.js`.
- The epoch-1 implementation hard-coded `20260929-bounce-alert` inside the authorized `src/test/js/senderBindingDisplay.test.js`, so the straggler check fails with `found in: .../senderBindingDisplay.test.js`.
- Repair (inside an authorized file, no amendment needed): keep asserting the uniform bump — derive the current key from `index.html` (as `taskActivityCenter.test.js` and `mailOpenTracking.test.js` already do), assert 11 versioned assets share it, assert the old key `20260929-discovery-schedule` no longer appears, and keep `task-modal-runtime.js` unversioned. Do not embed the new key literal in any test/script.

## Required amendment (needs human approval)

**A1**: add `src/test/js/providerUndeliveredColumn.test.js` to child 01's authorized files and narrow its global assertion back to the I-6 scoped intent — i.e. assert that the provider-distribution consumers (`renderMonitoringProviderDistribution`, and the `worstUndeliveredProvider` chain inside `renderMonitoringCards`) no longer reference `hardBounceCount` / `softBounceCount`, without forbidding the new sender-accounts DTO consumption mandated by child 01 I-4.

Product implementation needs no rework for A1; the same commit range keeps its behaviour.

## Resume plan (after A1 approval)

1. Commit the amended child plan 01 on its own; append amendment row A1 to the ledger (`Before`/`After` commit identities).
2. New execution epoch 2, fix_round = 0: one writer applies F-1 (narrowed assertion) + F-2 (key literal removal), runs the child's required commands **plus** the full JS suite, and commits `feat(fast-p): implement 01 epoch 2`.
3. Fresh verifier (distinct agent) runs the four-gate light verification against the epoch-2 boundary; full `mvn test` is not re-run at this gate (deferred to child 03's required command), but the JS suite is part of the evidence.

## Paused-at state

- Worktree/branch retained: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master` @ `fast/2026-09-29-bounce-repair-master`
- Product code head: `ff0d1ebc52eae3812c994cb8764e04ef455455c0`; no fix commits; no plan edits yet.
