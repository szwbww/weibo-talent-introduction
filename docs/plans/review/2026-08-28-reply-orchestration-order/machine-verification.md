# Aggregate Machine Verification — 10-reply-orchestration-order

## Epoch 1 — 2026-08-29T02:40:04Z

- Master plan: docs/plans/2026-08-28/10-reply-orchestration-order.md (sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be)
- Governing master identity: sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be; recorded commit 5a90e3e53e5fe8b40059b3090f086d6b36a09a01
- Master identity state: CONSISTENT
- Boundary: de228e17cc0134a7c11dea7cbf82054e8d249f99..7f8b28d2f09c0df7551703d8037c2b521b189152
- Reviewer: /root/aggregate_reviewer
- Result: BLOCKED
- Convergence: INITIAL / BLOCKED
- Repair artifact/result: docs/plans/fix/10-reply-orchestration-order/repair.md — NOT_RUN (blocked route; artifact absent)

### Identity and boundary

| Item | Result | Evidence |
|---|---|---|
| Branch | PASS | `fast/2026-08-28-reply-orchestration-order` |
| Evidence HEAD | PASS | `30d03bea82744ce878f6e945bb3e01d567a335ef` |
| Master identity | PASS | SHA-256 `31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be` |
| Recorded master commit | PASS | `5a90e3e53e5fe8b40059b3090f086d6b36a09a01` |
| Code boundary | PASS | `de228e17...` is ancestor of `7f8b28d...` |
| Evidence ancestry | PASS | `7f8b28d...` is ancestor of HEAD |
| Child order | PASS | c1→c2→c3→c4→c5→c6 matches the ordered plans |
| Amendments | PASS | A1/A2/A3 each have HUMAN approval records |
| Deferred scope | PASS | Plan 17 and knowledge writes were not executed |
| Working tree | PASS_WITH_NOTE | Only controller-owned `docs/plans/review/.../` was untracked; no product/test file was dirty |

### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; BUILD SUCCESS; 3005 tests, 0 failures, 0 errors, 5 skipped; about 03:05 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; BUILD SUCCESS; about 02:50; WAR successful; embedded JS 765/765 |
| `node --test src/test/js/*.test.js` | PASS | 765 tests, 765 pass, 0 fail; 121 suites |
| `git diff --check` | PASS | exit 0; no output |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | BLOCKED | exit 1; 1 test, 1 error |

Flyway blocker:

```text
DOCKER_HOST unix:///Users/lukai/.docker/run/docker.sock is not listening
NoSuchFileException (/var/run/docker.sock)
Could not find a valid Docker environment
IllegalStateException: Docker is required for Flyway migration tests
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
```

### Stable findings

#### V-1 — P1 — NEW

Step 02/03 authoritative paragraph drafts do not enter final `/assemble`.

Evidence:

- `trust-reply-workbench.js:1338-1350` assemble payload contains only source, versions, fact selections, frame, and lockedItems.
- `paragraphDraft`, pinned paragraphs, operatorFacts, edits/merges/moves/rearrange text are not submitted.
- `trust-reply-workbench.js:1535-1555` submits those fields only to `/rearrange`.
- `TrustReplyWorkbenchController.kt:191-217` has no paragraph seam in the assemble DTO conversion.
- `TrustReplyWorkbenchService.kt:1810-1818` still re-closes/re-composes from item versions.

Impact: topic adjustment, paragraph editing/locking/merging/moving/rearranging change only preview; clicking assemble discards them. Plan 15 observable outcome 3 and A-3/A-4/A-5/A-7 fail. Fully manually locked paragraphs cannot enter final text verbatim and in sequence.

#### V-2 — P1 — NEW

Archive callers still pre-filter the plan 16 allowed set.

Evidence:

- `UnsupportedAnswerIndexService.kt:746-775,792-799` accepts four handling values × two generation kinds, and optional operatorInstruction.
- `AiTrainingEvaluationService.kt:88-96` still allows only `AI_GENERATED`.
- `PendingMailOperationService.kt:720-725` still requires `ANSWER_FROM_OPERATOR_INPUT`, `AI_GENERATED`, and non-empty operatorInstruction.

Impact: `SAFE_TEMPLATE`, the other three handling values, and live empty operatorInstruction cannot reach the widened validator. Plan 16 I-4 and the samples required by A-1/A-3 are not implemented at existing trigger points.

#### V-3 — P1 — NEW

`finalParagraphText` is not the closed final paragraph.

Evidence:

- `UnsupportedAnswerIndexService.kt:58-63` explicitly notes assemble lacks a per-topic closed-paragraph seam.
- `UnsupportedAnswerIndexService.kt:733-736` assigns `finalParagraphText = version.answerText`.
- This violates plan 12 IP-4 (per-item answerText must remain separate from final closed paragraphs) and plan 16 T-4 (channel A must inject final paragraph phrasing, tone, and transition samples).

Impact: if default-off channel A is enabled, it samples per-item answers rather than final paragraphs; topic transitions and whole-letter orchestration wording cannot become plan-required samples.

### Master contract matrix

| Contract | Result | Evidence/notes |
|---|---|---|
| G-1 frozen rules immutable | PASS | Frozen ID/body guards and real fixtures retained |
| G-2 id↔reply_subject evidence boundary | PASS | V109/tests retain canonical boundary |
| G-3 migration preserves runtime changes | BLOCKED | Static guards pass; Docker migration runtime evidence unavailable |
| G-4 controlled fact exact-set trigger | PASS | Controlled groups/migration guard tests pass |
| G-5 coverage key and intent paired | PASS | Parity tests pass |
| G-6 catalog append only | PASS | Diff/tests pass |
| G-7 requestKey excludes op id | PASS | Four-input hash unchanged |
| c1 I-1 frozen-row dual guard | PASS | Both V109 UPDATEs contain frozen-ID guard |
| c1 I-2 new-rule keys intent referenced | PASS | Parity guard passes |
| c1 I-3 not a controlled exact group | PASS | Migration test passes |
| c1 I-4 empty coverage valid | PASS | Sensitivity repair writes `''` |
| c1 I-5 controlled body unchanged | PASS | No reply/answer-body UPDATE |
| c1 I-6 categories exist | PASS | Four category_code values parse |
| c1 runtime migration | BLOCKED | Flyway Docker evidence unavailable |
| c2 I-1 claims input | PASS | Closer input is item versions/claims |
| c2 I-2 sourceRuleIds set dedupe | PASS | Deterministic closer tests |
| c2 I-3 within-topic order | PASS | Tests pass |
| c2 I-4 one authorized CTA | PASS | Reused action detection |
| c2 I-5 frozen CTA treatment | PASS | ID 1/21 real fixtures |
| c2 I-6 all-locked escape hatch | PASS | Item-version path retained |
| c2 I-7 close after validation and before frame | PASS | Service `:1802-1818` |
| c3 I-1 per-item local lock | PASS | JS authority gate |
| c3 I-2 per-item persistEach | PASS | runItemSequence Applies-to changes use PATCH |
| c3 I-3 scoped guards | PASS_WITH_NOTE | Three explicit references; equivalent assembly guard retained |
| c3 I-4 optimistic-lock convergence | PASS | Service/JS tests |
| c3 I-5 explicit start analysis | PASS | `autoBootstrap:false` tests |
| c3 S-1 start-analysis button | PASS | Existing style reused |
| c3 S-2 unanalyzed placeholder | PASS | CSS/DOM contracts |
| c3 S-3 local overlay | PASS | Existing overlay styles unchanged |
| c4 I-1 grouping order input | PASS | paragraphPlan validation |
| c4 I-2 source closure | PASS | Unknown facts rejected |
| c4 I-3 required exactly once | PASS | 0/2 occurrences rejected |
| c4 I-4 controlled/frozen verbatim | PASS | Canonical constants/fixtures |
| c4 I-5 actionText single channel | PASS | Paragraph action rejected |
| c4 I-6 gap attaches to topic | PASS | Paragraph count matches plan |
| c4 I-7 six deterministic validations | PASS | Validation codes/tests |
| c4 I-8 failure falls back to closer | PASS | Fallback tests |
| c5 I-1 op independent ID/hash | PASS | op id excluded from requestKey |
| c5 I-2 op verbatim slot | FAIL | V-1: rearrange preview only; final assemble drops it |
| c5 I-3 pinned item-level version | PASS | Rearrange payload uses item evidence version |
| c5 I-4 high-frequency interactions not persisted | PASS | JS tests |
| c5 I-5 fact-set derived view | PASS | No second body source |
| c5 I-6 per-question coverage view unchanged | PASS | JS tests |
| c5 S-1 three-step tabs | PASS | DOM tests |
| c5 S-2 fact-set table | PASS | CSS/DOM tests |
| c5 S-3 paragraph card/locked state | PASS | CSS/DOM tests |
| c5 final assemble semantics | FAIL | V-1 |
| c6 I-1 index only provides phrasing | PASS | Not a grounded fact source; source closure retained |
| c6 I-2 strict mapping evolution | PASS | 26 fields + PUT mapping patch |
| c6 I-3 exact topic keyword filter | PASS | Query only topic/sourceMode |
| c6 I-4 approval gate retained, shape widened | FAIL | V-2 |
| c6 I-5 status means conversion state | PASS | sourceMode/status decoupled; queue activation exists |
| c6 I-6 empty operatorInstruction display | PASS | Parser/list tests |
| c6 T-4 final-paragraph phrasing sample | FAIL | V-3 |
| c6 T-5 pending-fact queue | PASS | Aggregation, draft prefill, post-save activation |
| c1-c6 manual checklists | PENDING | No human UI/real ES acceptance evidence |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1 parity baseline had 5 tests | c1 parity test count | RECORD_ONLY | Pre-existing count is not a deviation |
| c2 O-1 only fast evidence dirty | clean product/test state | RECORD_ONLY | Current untracked files are controller review evidence only |
| c3 O-1 1-Hz timing flake | c3 regression evidence | RECORD_ONLY | Fresh full test did not reproduce |
| c3 O-2 hasRequestMutationPending 3 not 4 | c3 global-entry guards | RECORD_ONLY | Equivalent per-item assembly guard exists |
| c3 O-3 toggleResolve/persistDecisionUnlock remain PUT snapshot | c3 I-2 scope | RECORD_ONLY | Outside explicit runItemSequence Applies-to scope |
| c4 O-1 truncated G2 prefix negative fixture | c4 verbatim safety | RECORD_ONLY | Expectation uses catalog constant |
| c5 O-1 absent GroundedContentPlannerTest | c5 focused test availability | RECORD_ONLY | Focused fallback is factual |
| c5 O-2 no standalone requestKey-with-op test | c5 I-1 | RECORD_ONLY | Byte-unchanged hash and JS op-id evidence suffice; distinct from V-1 |
| baseline JS note | master regression gate | RESOLVED | Standalone authority gate passed 765/765 |
| c6 child F-1/F-2 | c6 I-5 | RESOLVED | Independent re-review confirms closure |

### Repair route

- Review route: BLOCKED
- repair-p eligibility: no
- Repair result: NOT_RUN
- Repair path: docs/plans/fix/10-reply-orchestration-order/repair.md (absent)
- Reason: Docker/Flyway mandatory evidence is unavailable; this BLOCKED route must not create a repair artifact.

No product code, tests, plans, or review evidence were modified by the reviewer. Nothing was staged or committed by the reviewer.

## Epoch 2 — 2026-08-29T03:04:29Z

- Master plan: docs/plans/2026-08-28/10-reply-orchestration-order.md (sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be)
- Governing master identity: sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be; recorded commit 5a90e3e53e5fe8b40059b3090f086d6b36a09a01
- Master identity state: CONSISTENT
- Boundary: de228e17cc0134a7c11dea7cbf82054e8d249f99..7f8b28d2f09c0df7551703d8037c2b521b189152
- Reviewer: /root/aggregate_reviewer_epoch2
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/10-reply-orchestration-order/repair.md — DRAFT_READY

### Human exception

The user authorized this epoch only: `忽略 Flyway IT 继续` (2026-08-29). Therefore `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` was `HUMAN_EXCEPTION / NOT_RUN`; it is non-blocking only for epoch 2 and does not waive repair execution verification.

### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; BUILD SUCCESS; 3005 tests; 0 failures; 0 errors; 5 skipped; 03:08 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; BUILD SUCCESS; WAR created; embedded JS 765/765; 02:59 |
| `node --test src/test/js/*.test.js` | PASS | 765 tests; 765 pass; 0 fail; 121 suites |
| `git diff --check` | PASS | exit 0; no output |
| Flyway migration IT | HUMAN_EXCEPTION / NOT_RUN | exact user authority above |

### Finding lineage

| ID | Severity | Epoch 1 | Epoch 2 | Evidence |
|---|---:|---|---|---|
| V-1 | P1 | NEW | PERSISTENT | Final `/assemble` payload/DTO omits authoritative step-03 paragraphs; service re-closes item versions. |
| V-2 | P1 | NEW | PERSISTENT | Training/live callers still pre-filter validator-approved archive shapes. |
| V-3 | P1 | NEW | PERSISTENT | `finalParagraphText` remains `version.answerText`, not final closed paragraph text. |

#### V-1 — P1 — PERSISTENT

- `trust-reply-workbench.js:1338-1350`: assemble payload omits paragraph draft, pins, and operator facts.
- `trust-reply-workbench.js:1535-1555`: those fields enter `/rearrange` only.
- `TrustReplyWorkbenchController.kt:191-217`: assemble conversion has no paragraph seam.
- `TrustReplyWorkbenchService.kt:1810-1818`: final assembly re-closes/re-composes from versions.

Impact: edited/merged/moved/rearranged/pinned/operator-fact preview is discarded by final assembly; plan 15 I-2 and final-assemble semantics fail.

#### V-2 — P1 — PERSISTENT

- `UnsupportedAnswerIndexService.kt:746-775` accepts four handling values × two generation kinds, with optional instruction.
- `AiTrainingEvaluationService.kt:88-97` still excludes `SAFE_TEMPLATE`.
- `PendingMailOperationService.kt:720-725` still restricts handling, generation kind, and requires non-empty instruction.

Impact: plan 16 I-4 allow-listed shapes cannot reach canonical validation.

#### V-3 — P1 — PERSISTENT

- `UnsupportedAnswerIndexService.kt:58-63` notes the final-paragraph seam is missing.
- `UnsupportedAnswerIndexService.kt:733-737` assigns `finalParagraphText = version.answerText`.

Impact: plan 12 IP-4 and plan 16 T-4 fail; the index stores per-item answer wording rather than closed paragraph wording/transitions.

### Master contract matrix

| Contract | Result | Evidence/notes |
|---|---|---|
| G-1 frozen rules immutable | PASS | Frozen ID/body guards and fixtures retained |
| G-2 id↔reply_subject boundary | PASS | V109/tests retain canonical boundary |
| G-3 migration preserves runtime changes | HUMAN_EXCEPTION / NOT_RUN | Static guards pass; Flyway exception is epoch-specific |
| G-4 controlled-fact exact set | PASS | Groups/migration guards pass |
| G-5 coverage key and intent paired | PASS | Parity tests pass |
| G-6 catalog append only | PASS | Diff/tests pass |
| G-7 requestKey excludes op ID | PASS | Four-input hash unchanged |
| c1 I-1..I-6 | PASS except runtime migration | V109 guards, parity, category parsing, and body scope pass; runtime IT is HUMAN_EXCEPTION / NOT_RUN |
| c2 I-1..I-7 | PASS | Claims, dedupe, ordering, CTA, frozen fixtures, locked escape hatch, and assembly ordering pass |
| c3 I-1..I-5; S-1..S-3 | PASS (I-3 PASS_WITH_NOTE) | Scoped PATCH/guard behavior and JS contracts pass; equivalent assembly guard accounts for noted ref count |
| c4 I-1..I-8 | PASS | Paragraph order/source closure/exact-once/verbatim/action/gap/validation/fallback pass |
| c5 I-1 | PASS | op ID excluded from requestKey |
| c5 I-2 | FAIL | V-1 |
| c5 I-3..I-6; S-1..S-3 | PASS | Evidence-version, local interactions, derived fact view, coverage view, and three-step UI contracts pass |
| c5 final assemble semantics | FAIL | V-1 |
| c6 I-1..I-3 | PASS | Phrasing-only closure, strict mapping, exact topic query pass |
| c6 I-4 | FAIL | V-2 |
| c6 I-5..I-6; T-5 | PASS | Conversion status, empty instruction display, pending-fact queue pass |
| c6 T-4 | FAIL | V-3 |
| c1-c6 manual acceptance | PENDING | No human UI/real ES evidence supplied |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Result | Evidence |
|---|---|---|
| c1 O-1 parity baseline had five tests | RECORD_ONLY | Pre-existing count; no deviation |
| c2 O-1 evidence-only dirty state | RECORD_ONLY | Only controller ledger plus permitted repair artifact; no product/test dirt |
| c3 O-1 1-Hz flake | RECORD_ONLY | Fresh full suite did not reproduce |
| c3 O-2 mutation-pending reference count | RECORD_ONLY | Equivalent assembly guard exists |
| c3 O-3 full-PUT snapshot paths | RECORD_ONLY | Outside scoped PATCH requirement |
| c4 O-1 truncated G2 negative fixture | RECORD_ONLY | Assertion uses catalog constant |
| c5 O-1 absent GroundedContentPlannerTest | RECORD_ONLY | Focused fallback remains factual |
| c5 O-2 no standalone op/requestKey test | RECORD_ONLY | Hash unchanged; separate JS evidence exists |
| baseline JS note | RESOLVED | Fresh standalone gate 765/765 |
| c6 F-1/F-2 | RESOLVED | Prior closure independently confirmed |
| Flyway runtime gate | HUMAN_EXCEPTION / NOT_RUN | Explicit epoch-2 authority; not permanent waiver |

### Repair planning

- Verification result: FAIL / PROGRESSING
- repair-p result: DRAFT_READY
- Repair artifact: docs/plans/fix/10-reply-orchestration-order/repair.md (sha256 e0704ff0b89546a557531cad63d8dc0b032582958930fdc9e2f59e09c1ed753b)
- Included: V-1, V-2, V-3. Excluded: Flyway exception and RECORD_ONLY entries.
- Tasks: R-1 carries authoritative step-03 paragraphs through assemble/archive and resolves V-1/V-3. R-2 aligns both archive callers with the exact approved eligibility set and resolves V-2.
- Product commit subject: `fix(reply-orchestration): preserve final paragraphs and archive eligibility`.
- Evidence commit subject: `docs(review-fast-p): record repair execution`.

The reviewer made no product/test/review-evidence changes, staged nothing, and committed nothing.

## Epoch 3 — 2026-08-29T11:29:34Z

- Master plan: docs/plans/2026-08-28/10-reply-orchestration-order.md (sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be)
- Governing master identity: sha256 31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be; recorded commit 5a90e3e53e5fe8b40059b3090f086d6b36a09a01
- Master identity state: CONSISTENT
- Boundary: original de228e17cc0134a7c11dea7cbf82054e8d249f99..7f8b28d2f09c0df7551703d8037c2b521b189152; post-repair 7f8b28d2f09c0df7551703d8037c2b521b189152..6793ff948515e541969f76388e0af5bde1fd2f3a
- Reviewer: /root/aggregate_reviewer_epoch3
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/10-reply-orchestration-order/repair.md — DRAFT_READY (sha256 436406ef90623635bd3342b4ac809a6050af8ad75e84fc2a4ecdf64c1ea9a522)

### Identity and repair lineage

| Item | Result | Evidence |
|---|---|---|
| Branch/worktree | PASS | Exact selected fast-p worktree |
| Master/recorded identity | PASS | Governing SHA above; commit `5a90e3e...`; invoked identity SAME |
| Ancestry | PASS | Base → original candidate → `6793ff9` repair candidate → evidence HEAD |
| Amendments | PASS | A1/A2/A3 recorded; A4 test authority in durable repair handoff |
| Repair scope | PASS | Six authorized product files, six planned tests, and two A4 tests only |
| Worktree/index | PASS_WITH_NOTE | Only controller ledger and repair-p's exact repair artifact modified; no product/test dirt |

### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; BUILD SUCCESS; 3014 tests, 0 failures, 0 errors, 5 skipped; embedded JS 766/766 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; BUILD SUCCESS; WAR built; 02:56; embedded JS 766/766 |
| `node --test src/test/js/*.test.js` | PASS | 766 tests, 766 pass, 0 fail; 121 suites |
| `git diff --check` | PASS | exit 0; no output, including after repair artifact update |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | BLOCKED | exit 1; 1 test, 1 error; Docker socket unavailable; `/var/run/docker.sock` absent; `Docker is required for Flyway migration tests` |

No previous Flyway skip was treated as a re-review waiver. Confirmed P1 defects route this review as FAIL / PROGRESSING; Flyway evidence remains unavailable.

### Contract matrix

| Contract | Result | Evidence |
|---|---|---|
| G-1 / G-2 / G-4 / G-5 / G-6 / G-7 | PASS | Frozen/migration scope untouched; controlled, parity, append-only, and hash contracts hold |
| G-3 and c1 runtime migration | BLOCKED | Static guards unchanged; Docker migration evidence unavailable |
| c1 I-1..I-6 | PASS | V109 unchanged; parity/static coverage remains green |
| c2 I-1..I-7 | PASS | Closer path, dedupe/order/CTA/frozen/all-locked/order contracts pass |
| c3 I-1..I-5, S-1..S-3 | PASS (I-3 PASS_WITH_NOTE) | Scoped persistence/guard and JS/DOM contracts pass |
| c4 I-1 / I-2 / I-4..I-8 | PASS | Grouping/source closure/verbatim/action/gap/six checks/fallback pass |
| c4 I-3 exactly once | FAIL for `op<n>` final assembly | V-1; `f*`/`x*` pass |
| c5 I-1 / I-3..I-6 / S-1..S-3 | PASS | Hash isolation, version evidence, local UI, fact and coverage contracts pass |
| c5 I-2 and final assemble semantics | FAIL | V-1 |
| c6 I-1..I-6 and T-5 | PASS | V-2 resolved; mapping/filter/status/display/queue contracts pass |
| c6 T-4 | FAIL | V-3 |
| c1-c6 manual acceptance | PENDING | Human UI/real ES evidence unavailable |

### Finding lineage

| Finding | Epoch 1 | Epoch 2 | Epoch 3 | Result |
|---|---|---|---|---|
| V-1 | NEW | PERSISTENT | PERSISTENT | `op<n>` authoritative draft fails identity closure before composition |
| V-2 | NEW | PERSISTENT | RESOLVED | Both callers delegate to canonical four-handling × two-generation predicate |
| V-3 | NEW | PERSISTENT | PERSISTENT | Operator-directed final paragraph cannot reach mapping/archive because V-1 fails first |

#### V-1 — P1 — PERSISTENT

`trust-reply-workbench.js:1357-1368` submits authoritative `op<n>` paragraph IDs and matching `operatorFacts`. `TrustReplyWorkbenchService.kt:1949-1957` reconstructs required IDs only from locked versions (`f*`/`x*`) and compares before operator facts are included at `:1968`. A claims-empty operator-directed version becomes `x<n>`, while the canonical step-03 draft uses `op<n>`; valid operator-fact drafts therefore return `TRUST_REPLY_FINAL_PARAGRAPHS_INVALID` before composition.

#### V-3 — P1 — PERSISTENT

Operator facts are mapped only after V-1's failing closure check (`TrustReplyWorkbenchService.kt:1986`). `UnsupportedAnswerIndexService.kt:780-784` correctly fails closed on missing mapping, so plan-required final wording cannot be archived for the primary operator-directed path.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Result | Evidence |
|---|---|---|
| c1 parity baseline count; c2 evidence-only state; c3 flake/reference/full-PUT notes; c4 negative fixture; c5 absent focused planner/no standalone hash test | RECORD_ONLY | No mandatory violation; fresh full suites and existing contract evidence support prior routing |
| Baseline JS authority note; c6 F-1/F-2 | RESOLVED | Standalone JS gate 766/766 and prior closure remains valid |
| Flyway runtime | BLOCKED | Mandatory Docker evidence unavailable; not product repair scope |

### Repair planning

- review-p result: FAIL / PROGRESSING; repair-p result: DRAFT_READY.
- Included: V-1 and V-3 only. V-2 is resolved; Flyway and RECORD_ONLY entries are excluded.
- Root cause: operator-fact `op<n>` IDs are excluded from exact-once closure until after the equality comparison.
- Authorized files: `TrustReplyWorkbenchService.kt`, `TrustReplyWorkbenchServiceTest.kt`, and `trustReplyWorkbenchThreeStep.test.js`.
- Product commit subject: `fix(reply-orchestration): preserve operator facts in final assembly`.
- Evidence commit subject: `docs(review-fast-p): record repair execution`.

The reviewer made no product/test/report changes, staged nothing, and committed nothing. `repair-p` updated only the exact repair artifact to include the required fully resolved Review-Fast-P execution handoff.

## Epoch 4 — 2026-08-29T12:12:24Z

- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (sha256 `31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be`)
- Governing identity: recorded commit `5a90e3e53e5fe8b40059b3090f086d6b36a09a01`; invoked identity SAME; `CONSISTENT`.
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..0d45505d68261c14f3866e3f440b2ea08195f1de`.
- Reviewer: `01a04d62-0fd8-7000-b4da-0f186741d8c2`.
- Result: FAIL. Convergence: PROGRESSING. Manual acceptance: PENDING.
- Repair artifact/result: `docs/plans/fix/10-reply-orchestration-order/repair.md` — DRAFT_READY (sha256 `a511b739a3a7e983787a29ac819f7a11530baf5b8b7eaefe706df3eb6ac634c8`).

### Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | 3022 tests, 0 failures, 0 errors, 5 skipped |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | BUILD SUCCESS |
| `node --test src/test/js/*.test.js` | PASS | 766 pass, 0 fail |
| `git diff --check` | PASS | exit 0, no output |
| Flyway migration IT | HUMAN_EXCEPTION / NOT_RUN | Current user explicitly instructed: ignore Flyway IT; non-blocking for epoch 4 only |

### Master contract matrix

| Requirement | Verdict | Evidence |
|---|---|---|
| G-1..G-7 | PASS | Frozen migration guards, controlled-fact safety, parity, append-only catalog, and four-input request hash retain their contracts |
| c1 I-1..I-6 | PASS | V109/guard/parity evidence remains green |
| c2 I-1..I-7 | PASS | Closer, dedupe/order/CTA, frozen fixtures, and locked fallback pass |
| c3 I-1..I-5; S-1..S-3 | PASS | Scoped persistence/guards and JS/DOM regression gates pass |
| c4 I-1..I-8 | PASS | Source closure, verbatim slots, exact-once, validations, and fallback pass |
| c5 I-1 | FAIL | V-4: server accepts noncanonical operator-fact IDs |
| c5 I-2..I-6; S-1..S-3 | PASS | Valid `op<n>` path, final assemble flow, UI and non-persistent rearrangement pass |
| c6 I-1..I-6; T-4/T-5 | PASS | Final-paragraph mapping and archive eligibility contracts remain closed |
| A1/A2/A3 | PASS | Final cumulative diff stays within approved amendment scope |

### Finding lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Valid `op<n>` facts bind, compose, and map to final paragraphs |
| V-2 | RESOLVED | Archive callers use canonical eligibility |
| V-3 | RESOLVED | Archive takes validated final paragraph, not `answerText` fallback |
| V-4 | NEW P1 | `TrustReplyWorkbenchService.kt:1960` rejects only blank/duplicate IDs; arbitrary `external-1` can enter required facts at `:1990` and pass generic rearrangement validation at `:2008` when its body matches an operator-owned version |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Result | Evidence |
|---|---|---|
| c1 parity count; c2 evidence state; c3 flake/reference/full-PUT; c4 negative fixture; c5 hash-test note; JS gate baseline | RECORD_ONLY | None proves a current mandatory violation |
| V-4 | P1 | New server-side source-closure defect; not a waiver/observation |

### Repair planning

- `review-p`: FAIL / PROGRESSING. `repair-p`: DRAFT_READY.
- V-4 only. Authorized files: `TrustReplyWorkbenchService.kt`, `TrustReplyWorkbenchServiceTest.kt`.
- Required repair: reject every `operatorFacts[].id` other than positive-decimal `op<n>` before identity closure; add malformed-ID regression and retain valid `op1` coverage.
- Handoff requires one product commit `fix(reply-orchestration): enforce operator fact IDs` and one evidence commit `docs(review-fast-p): record repair execution`.

No product code was modified by this reviewer.
