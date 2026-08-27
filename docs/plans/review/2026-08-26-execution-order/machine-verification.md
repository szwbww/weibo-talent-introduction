# Aggregate Machine Verification — 2026-08-26 execution order

## Epoch 1 — 2026-08-27

- Master plan: docs/plans/2026-08-26/00-execution-order.md (sha256 01c95b5dde2180ce0beaac0b08361a8c11dae0bd8929389e2058914d76dd4da8)
- Governing master identity: sha256 01c95b5dde2180ce0beaac0b08361a8c11dae0bd8929389e2058914d76dd4da8; recorded commit ee0749d3beedea7e26f4bf4e097b3d33a1684b7d
- Master identity state: CONSISTENT; A1 applies only to `TrustReplyWorkbenchServiceTest.kt` and `trustReplyWorkbenchSharedMount.test.js` under the recorded 03 rule and human approval.
- Boundary: f2935072c819a9167e75220a6a959b0769462fde..cb30230970d12e649e9faac2835335345daac793
- Reviewer: 01a040d3-201f-7730-8782-ac18de6a38db (Ampere)
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/00-execution-order/repair.md — DRAFT_READY

## Verification Result: FAIL

Plan: `docs/plans/2026-08-26/00-execution-order.md`

Implementation boundary: `f2935072c819a9167e75220a6a959b0769462fde..cb30230970d12e649e9faac2835335345daac793`

Manual acceptance: PENDING

Identity: master SHA-256 matches `01c95b5d…4da8`; recorded identity `ee0749d…`; A1 commit `dc5c11e…`; code head is ancestor of evidence head `0511e635…`.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | Fresh surefire XML aggregate: 2872 tests, 0 failures, 0 errors, 4 skipped. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | Fresh clean/package run reached test aggregate 2872/0/0/4 and produced `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 735 tests, 735 pass, 0 fail, 0 skipped. |
| `git diff --check` | PASS | Exit 0; silent. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | Boundary ancestry valid; branch/worktree match ledger. |
| M-2 | PASS | c1→c2→c3 order and final code head match ledger/handoff. |
| M-3 | PASS | A1 authorizes only the two repair test files; combined product diff stays within c1/c2/c3/A1 scope. |
| 01-I-1 | PASS | `QaFactSelectionService.kt:542-544, 629-641` keeps intents based on strict candidates; retrieval only augments facts. |
| 01-I-2 | PASS | Five call sites explicitly pass retrieval IDs only on auto paths; matrix/legacy pass empty lists. |
| 01-I-3 | PASS | `QaFactSelectionService.kt:542-544, 599-602` preserves keyword facts first, then retrieved facts. |
| 01-I-4 | FAIL | `QaFactRetriever.kt:71` truncates `promptPool`; `:119` validates against full `pool`. A valid rule excluded from the prompt can be accepted. |
| 01-I-5 | PASS | `QaFactSelectionService.kt:653-664` appends each request’s final fact IDs into send IDs. |
| 01-I-6 | PASS | `QaFactSelectionService.kt:608-623` applies UNSUPPORTED-with-facts → PARTIAL, without GROUNDED escalation. |
| 01-I-7 | PASS | `QaFactRetriever.kt:67-89, 286-300` uses deterministic cache key and explicit temperature `0.0`. |
| 01-I-8 | PASS | Fail-open outcomes retained; actual AUTO/WORKBENCH callers log unavailable retrieval outcomes at `warn` in `QaFactSelectionService.kt:115-129,232-245`. |
| 01-I-9 | PASS | `QaFactRetriever.kt:168-181` caps per request and warns with count/IDs. |
| 02-I-1 | PASS | `QaFactSelectionService.kt:614-623` caps recognized-but-incomplete requests at PARTIAL. |
| 02-I-2 | PASS | Intent aliases/keys untouched; only `application.required_materials` alternative coverage added. |
| 02-I-3 | PASS | Enumerator cap requires `available`; unavailable preserves natural status. |
| 02-I-4 | PASS | `QaCoverageKeyCatalog.kt:112-117` appends new keys after prior final entry. |
| 02-I-5 | PASS | `QaCoverageKeyIntentParityTest.kt` verifies bidirectional catalog/intent closure and explicit exceptions. |
| 03-I-1 | PASS | Auto-run instruction write block remains guarded by operator-edit state. |
| 03-I-2 | PASS | `TrustReplyWorkbenchService.kt:2130-2164` performs ordered cross-item name de-duplication. |
| 03-I-3 | PASS | Last UNSUPPORTED item alone receives the action suffix. |
| 03-I-4 | PASS | Existing unsafe-name/body-fragment filters preserved; revised literals satisfy tests. |
| 03-I-5 | PASS | Budget calculated from actual action/non-action suffix. |
| 03-I-6 | PASS | Rendered preview defaults to rendered text with raw fallback; raw/local remain reachable. |
| 03-I-7 | PASS | Preview tab dispatch uses state plus `render()`; no preview `querySelector`. |
| 03-S-1 | PASS | Six CSS blocks match approved contract; existing assembly/page-nav/page-tab blocks unchanged. |
| 03-S-2 | PASS | Shared `<pre class="pre">` roles retained; preview markup has no inline style. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `QaFactRetriever.kt:71,119,147` violates 01 I-4 prompt-pool authority. |
| Prior findings | N/A | No prior aggregate report or repair epoch. |

### Findings

#### P1

- V-1 — 01 I-4 requires every accepted rule ID to be in the exact `promptPool` sent to the model. `promptPool = pool.take(maxRulesInPrompt)` at `QaFactRetriever.kt:71`, but `poolById` is created from untruncated `pool` at `:119`. A hallucinated ID for an enabled valid rule omitted by truncation is accepted at `:147-159`, allowing unprompted fact content into a reply.

#### P2

- N/A

#### Observations

- c1 O-1 / c2 O-1 / c3 O-3: manual worktree identity replication is process-only; identity independently matches.
- c1 O-2: additive retrieval outcome/stat fields support required observable logs; no contract breach.
- c1 O-3: omitted dead `candidateRules` local is behaviorally equivalent to the required union.
- c1 O-4: workbench invokes the retriever and relies on its disabled guard; this preserves A-3’s required disabled outcome log.
- c1 O-5: parsed `[]` means model returned no facts, not a failed invocation.
- c1 O-6: fresh aggregate count is 2872; historical 2847 baseline count remains record-quality only.
- c2 O-2 / O-3: timeline reachability test methodology and required trailing comma are valid.
- c3 O-1: all 13 mechanically affected wording assertions are within A1 scope.
- c3 O-2: page-nav-scoped tab assertions preserve the two-page navigation contract.
- c3 O-4: template-generated preview tab markup and scoped no-inline-style assertion preserve required behavior.

### Evidence Boundaries

- Manual acceptance A-1 through A-6 for plans 01/02 and A-1 through A-7 for plan 03 remains PENDING.
- No live LLM, mail delivery, operator matrix, or browser manual acceptance was performed.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-08-26/00-execution-order.md`

Verification result: FAIL / INITIAL

Repair artifact: `docs/plans/fix/00-execution-order/repair.md`

### Included Findings

- V-1

### Excluded Findings

- Failure logging — caller-level classified unavailable logs already use `warn`; no confirmed I-8 violation.

### Required Human Decision

- Approve the repair plan.

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1 / c2 O-1 / c3 O-3 | Worktree identity evidence | Unrelated | Registered worktree, branch, and boundary independently match. |
| c1 O-2 through O-5 | 01 invariants I-1, I-4, I-8 | PASS | Each additive or caller-routing deviation preserves the named master requirement. |
| c1 O-6 | Fresh verification evidence | Unrelated | Fresh aggregate suite establishes 2872/0/0/4; historical baseline count is not a product defect. |
| c2 O-2 / O-3 | 02 I-2, I-4, I-5 | PASS | Coverage reachability method and syntax-only comma preserve the contract. |
| c3 O-1 / O-2 / O-4 | A1 and 03 I-6/S-2 | PASS | Amendment-authorized tests and equivalent template assertions preserve required behavior. |
