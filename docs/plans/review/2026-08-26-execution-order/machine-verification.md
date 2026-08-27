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

## Epoch 2 — 2026-08-27

- Master plan: docs/plans/2026-08-26/00-execution-order.md (sha256 01c95b5dde2180ce0beaac0b08361a8c11dae0bd8929389e2058914d76dd4da8)
- Governing master identity: sha256 01c95b5dde2180ce0beaac0b08361a8c11dae0bd8929389e2058914d76dd4da8; recorded commit ee0749d3beedea7e26f4bf4e097b3d33a1684b7d.
- Master identity state: CONSISTENT. A1 remains recorded and applies only to `TrustReplyWorkbenchServiceTest.kt` and `trustReplyWorkbenchSharedMount.test.js`.
- Boundary: f2935072c819a9167e75220a6a959b0769462fde..7ce95dba4b01d559ce580cc964564cc648c292a4
- Reviewer: 01a040fe-131e-7d82-b6c3-c05beac25a55 (Mencius)
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/00-execution-order/repair.md — DRAFT_READY (sha256 ceea69e6f79b9876c8901e4c279cbdcbb533ba8f4b598f54a96eea29a73ec33b)

## Verification Result: FAIL

Preflight: PASS. Selected worktree/branch, governing master identity, A1 authority, durable repair handoff, ancestry, and V-1 repair scope match. The V-1 repair delta is limited to its two authorized files.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | Exit 0; 2873 tests, 0 failures, 0 errors, 4 skipped. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | Exit 0; 2873/0/0/4; WAR built. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 735 pass, 0 fail. |
| `git diff --check` | PASS | Exit 0; silent. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 boundary/identity | PASS | Discovery SELECTED; branch/head/ancestry valid. |
| M-2 child order | PASS | c1 → c2 → c3 terminal; repair descends from prior code head. |
| M-3 scope/A1 | PASS | Aggregate and repair product/test deltas authorized; A1 files individually valid. |
| 01-I-1 | PASS | Retrieval affects facts only; `intents = intentCoverages` remains. |
| 01-I-2 | PASS | Matrix/legacy paths pass empty retrieval IDs. |
| 01-I-3 | PASS | Strict keyword facts remain first; retrieved facts append. |
| 01-I-4 | PASS | V-1 resolved: validation map uses truncated `promptPool`. |
| 01-I-5 | PASS | Selected retrieved facts enter `sendQaRuleIds`. |
| 01-I-6 | PASS | Unsupported-with-facts becomes PARTIAL only. |
| 01-I-7 | PASS | Deterministic cache key; `temperature = 0.0`. |
| 01-I-8 | FAIL | Retriever failure branches lack retriever-level classified `warn`; V-2. |
| 01-I-9 | PASS | Per-request cap and visible truncation. |
| 02-I-1..I-5 | PASS | Status cap, intent stability, unavailable-enumerator behavior, catalog ordering, parity closure. |
| 03-I-1..I-7 | PASS | Operator edit protection, dedupe, one CTA, safe text, 500 cap, rendered preview, state-render tabs. |
| 03-S-1/S-2 | PASS | Mandated CSS and three-tab/shared-`pre` structure preserved. |
| A1: `TrustReplyWorkbenchServiceTest.kt` | PASS | Wording updates remain within amendment authority. |
| A1: `trustReplyWorkbenchSharedMount.test.js` | PASS | Page-nav-scoped tab assertions preserve prior navigation contract. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | `QaFactRetriever.kt` validates from exact truncated prompt pool; regression test present. |
| V-2 | NEW | DISABLED/CLIENT_ABSENT/EMPTY_RESPONSE/PARSE_ERROR/ALL_REJECTED lack classified retriever `warn`; TRANSPORT_ERROR warning lacks normalized outcome. |

### Findings

#### P1

- V-2 — 01 I-8 requires each `QaFactRetriever.retrieve(...)` failure outcome to fail open and emit one classified `log.warn`. Caller-level `[FACT_RETRIEVAL]` logs do not satisfy this retriever-scoped requirement. Authorized repair scope: `QaFactRetriever.kt` and `QaFactRetrieverTest.kt`.

#### P2

- N/A

### Observations

- c1/c2/c3 worktree-identity helper deviations are process-only; target identity independently matches.
- c1 additive retrieval statistics, omitted intermediate, disabled workbench route, valid `[]`, and historical test-count correction do not violate master rules.
- c2 reachability/comma notes are non-functional; c3 A1 wording/tab assertion forms remain authorized and behavior-preserving.

### Fast-P RECORD_ONLY Re-evaluation

| Source | Master requirement | Result | Evidence |
|---|---|---|---|
| c1/c2/c3 identity notes | Worktree/boundary integrity | Unrelated | Registered worktree, branch, and boundary independently match. |
| c1 retrieval shape/route notes | 01-I-1/I-4/I-8 | V-2 promoted | Missing retriever-level classified warnings only. |
| c1 baseline-count note | Fresh command evidence | Unrelated | Epoch 2 commands establish 2873/0/0/4. |
| c2 notes | 02-I-2/I-4/I-5 | PASS | No mandatory breach. |
| c3 A1/test-form notes | A1, 03-I-6/S-2 | PASS | Authorized and behavior-preserving. |

### Evidence Boundaries

- Manual acceptance remains pending.
- No live LLM, delivery, operator matrix, or browser acceptance was performed.

## Repair Planning Result: DRAFT_READY

- Included finding: V-2.
- Excluded: V-1 resolved; RECORD_ONLY entries non-blocking.
- Repair artifact: docs/plans/fix/00-execution-order/repair.md.
- No product code, tests, review evidence, stage, or commits were modified by the reviewer. Only the permitted repair artifact was updated.
