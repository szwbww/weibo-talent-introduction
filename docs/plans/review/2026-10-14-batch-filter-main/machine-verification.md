# Aggregate Machine Verification — batch-filter-main

## Epoch 1 — 2026-10-14T04:01:38Z

- Master plan: docs/plans/2026-10-14/batch-filter-main.md (sha256 cfca0276aa957e12d9e9ae7617a5dbd7a11bf8e55adf3e5a2daece3bf68af33c)
- Governing master identity: worktree sha256 cfca0276aa957e12d9e9ae7617a5dbd7a11bf8e55adf3e5a2daece3bf68af33c; recorded commit b02eeb6405b27d5d4f30a32d17019e19c855483d
- Master identity state: CONSISTENT; amendments N/A.
- Boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..50dc450b9e3d68b4cc44ad3a660d8a00936bb662
- Evidence HEAD: 6e406e22c92d9cff81df9f6453c89eac669eae77
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/batch-filter-main/repair.md (DRAFT_READY; sha256 71d5bdd391c7915a6190f9f29b9590d0d10f1de033930565e2a38194126a55be)

## Verification Result: FAIL

Plan: `docs/plans/2026-10-14/batch-filter-main.md`

Boundary valid. Master/ledger/handoff identities match. The implementation diff contains the 16 authorized product/test paths; `git diff --check` passes; CSS is unchanged. No product or test code was modified during review.

Manual acceptance: PENDING.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false` | PASS | exit 0; 340 tests; 0 failures/errors/skips |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false` | PASS | exit 0; 4 tests; 0 failures/errors/skips; MySQL 8.0.36 Testcontainer and 154 migrations |
| `node --test src/test/js/recipientPreviewDiagnostics.test.js` | PASS | 13/13 |
| `node --test src/test/js/batchEmailVerification.test.js` | PASS | 42/42 |
| `node --test src/test/js/gateTemplateFilter.test.js` | PASS | 8/8 |
| `node --test src/test/js/*.test.js` | PASS | 1671/1671 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |

Initial Maven/full-Node sandbox attempts met transient `target/` EPERM; exact reruns with permitted transient writes passed.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 / scope | PASS | Ordered commits and exact 16 product/test files match the 4/10/5 plan sets. |
| M-2 / complete shared baseline | FAIL | V-1, V-2. |
| M-3 / partitioned counts | PASS | `BatchRecipientPreflightService.kt:74-220`; retained 10/4/6/3 fixture. |
| M-4 / read-only diagnostics | PASS | Read-only repository/service paths; real-SQL IT passes. |
| M-5 / machine verification | PASS | Required commands pass; human acceptance remains pending. |
| S-M1 | PASS | `styles.css` diff zero; IDs, escaping, debounce, cache key, and Node contracts pass. |
| 01 I-1/I-2/I-3 | PASS | Region matcher and Other ES widening present; no new writes. |
| 02 I-1 | FAIL | V-1. |
| 02 I-2 | FAIL | V-2. |
| 02 I-3–I-6 | PASS | Reusable-history path, side-effect guards, bounded reads/samples, gate behavior covered. |
| 03 I-1–I-3 / S-1/S-2 | PASS | Response renders direct counts/reasons; UI tests pass. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| Child F-1 admission eligibility | RESOLVED | `9c9457c`; fresh 13-preflight-test suite passes. |
| V-1 | NEW | `ManualInitialOutreachService.kt:703-710` adds `exists email` to the required level/tag-only baseline. |
| V-2 | NEW | Full preflight occurs in preview/diagnostics (`:596`, `:625-646`, `:744-766`), not `runIntroductionFromSnapshot`; execution reuses only the template subcheck. |

### Findings

#### P1

- V-1: The diagnostics baseline silently drops selected level/tag profiles without `email`. MAIN M-2 and 02 I-1 require a level/tag-only baseline and explainable filtering; these profiles receive neither a decision/reason nor a correct diagnostic result.
- V-2: Preview, new-ES selection, and NEW retry selection do not consume one final deterministic preflight. The execution path uses selector/template pieces but never the complete `BatchRecipientPreflightService.preflight` result, violating MAIN M-2/G-2 and 02 I-2.

#### P2

- N/A.

#### Observations

- N/A beyond the RECORD_ONLY re-evaluation below.

### Evidence Boundaries

- Browser/manual A-1–A-5 not run.
- No production, SMTP, or provider validation performed.
- Controller-owned review artifact was not edited by the reviewer.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Dead `matchesDiscoveryOutreach` KDoc/predicate | M-2 | Unreachable; no mandatory violation. | No call path. |
| `tags=discovered` Other branch versus pending/identity shape | M-2 | No proven affected normal write path. | Retained as RECORD_ONLY. |
| Statusless preview error copy | 03 I-3 | 500/API error still shows `预估不可用`. | No mandatory defect. |
| Retained `门禁过滤已排除` | 03 I-2 | Required exclusion wording remains; forbidden `计入失败` absent. | No mandatory defect. |
| Legacy `详细解释不可用` plain span | S-1 | No confirmed mandatory defect. | Legacy compatibility path. |
| Aggregate `verificationPhase` unused by UI | 03 I-1 | Four mandated counts, as-of, and awaiting display remain correct. | No mandatory defect. |

## Repair Planning Result: DRAFT_READY

- Baseline: docs/plans/2026-10-14/batch-filter-main.md
- Verification: FAIL / INITIAL
- Repair artifact: docs/plans/fix/batch-filter-main/repair.md (sha256 71d5bdd391c7915a6190f9f29b9590d0d10f1de033930565e2a38194126a55be)
- Included findings: V-1, V-2.
- Excluded findings: all RECORD_ONLY items.

No implementation was performed.

## Epoch 2 — 2026-10-14T13:14:35+0800

- Master plan: docs/plans/2026-10-14/batch-filter-main.md (sha256 cfca0276aa957e12d9e9ae7617a5dbd7a11bf8e55adf3e5a2daece3bf68af33c)
- Governing master identity: worktree sha256 cfca0276aa957e12d9e9ae7617a5dbd7a11bf8e55adf3e5a2daece3bf68af33c; recorded commit b02eeb6405b27d5d4f30a32d17019e19c855483d.
- Master identity state: CONSISTENT; amendments N/A.
- Boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..e6499a22c4696a8d98ac1ffe2be0e191db5dca97
- Evidence HEAD: db3b6807cf3cfce78326c3f9bf77114516781a30
- Reviewer: /root/aggregate_rereviewer
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/batch-filter-main/repair.md (DRAFT_READY; sha256 bb1d080056783969eb766fc5e65e365de1af49f7073433a91809b837c2eb5218)

### Verification Result: FAIL

The repaired code descends from prior final code head 50dc450b9e3d68b4cc44ad3a660d8a00936bb662. Its four product/test changes are all inside the prior repair's Authorized Files. The planned 16-path boundary is exact, CSS diff is zero, and the index is clean. Manual acceptance remains PENDING.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false` | PASS | exit 0; 342 tests; 0 failures/errors/skips |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false` | PASS | exit 0; 4 tests; 0 failures/errors/skips |
| `node --test src/test/js/recipientPreviewDiagnostics.test.js` | PASS | 13/13 |
| `node --test src/test/js/batchEmailVerification.test.js` | PASS | 42/42 |
| `node --test src/test/js/gateTemplateFilter.test.js` | PASS | 8/8 |
| `node --test src/test/js/*.test.js` | PASS | 1671/1671 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |

Initial sandbox-only Maven/full-Node runs met transient `target/` EPERM; exact permitted reruns passed.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | Ordered 01→02→03 commits; scope exact; no CSS diff. |
| M-2 | FAIL | V-2 persistent: partial preflight becomes execution allow-list. |
| M-3 | PASS | Partition model and 10/4/6/3 regression retained. |
| M-4 | PASS | Preflight read-only; provider/SMTP tests remain zero. |
| M-5 | PASS | Seven required commands pass; manual remains pending. |
| S-M1 | PASS | Required IDs/cache key/escaping/debounce contracts pass; CSS unchanged. |
| 01 I-1 | PASS | `BatchExecutionModels.kt:236-243`. |
| 01 I-2 | PASS | Other ES widening: `ManualInitialOutreachService.kt:2285-2347`. |
| 01 I-3 | PASS | No write-path expansion. |
| 02 I-1 | PASS | Base no longer filters `email`; explicit `NO_RECIPIENT_ADDRESS`: `ManualInitialOutreachService.kt:717-727`, `BatchRecipientPreflightService.kt:178-210`. |
| 02 I-2 | FAIL | V-2. |
| 02 I-3 | PASS | Existing email-history/switch tests pass. |
| 02 I-4 | PASS | Read-only preflight and SQL IT pass. |
| 02 I-5 | PASS | 500 batching, bounded samples, 200 doc-ID limit, explicit partial retained. |
| 02 I-6 | PASS | Composer gate behavior retained. |
| 03 I-1 | PASS | Direct diagnostics render; 13-test suite passes. |
| 03 I-2 | PASS | Gate copy contract passes. |
| 03 I-3 | PASS | Debounce, stale-response, escaping, error tests pass. |
| 03 S-1/S-2 | PASS | DOM/IDs/cache-key contracts pass. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| Child F-1 | RESOLVED | Prior 02 repair retained; fresh suites pass. |
| V-1 | RESOLVED | Explicit no-address reason and partition regression: `BatchRecipientPreflightServiceTest.kt:385`. |
| V-2 | PERSISTENT | `scanBaseCandidates` caps at 20,000 (`ManualInitialOutreachService.kt:654-685`); execution filters all retry/ES candidates by its partial membership set (`:694-702`, `:929-942`, `:978-986`). |

### Findings

#### P1

- V-2: A target absent only because diagnostics hit the 20,000 cap is silently rejected during execution. This violates M-2/G-2 and 02 I-2's final deterministic-preflight requirement.

#### P2

- N/A.

### Evidence Boundaries

- Browser/manual A-1–A-5 not run.
- No production, SMTP, or provider validation.
- The reviewer modified no product code or aggregate review evidence.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Dead `matchesDiscoveryOutreach` | M-2 | Unreachable; no mandatory defect. | No call path. |
| `tags=discovered` coarse Other branch | M-2 | No proven affected normal write path. | Retained as RECORD_ONLY. |
| Statusless preview error copy | 03 I-3 | HTTP/API errors still show `预估不可用`. | No mandatory defect. |
| Retained `门禁过滤已排除` | 03 I-2 | Forbidden `计入失败` absent. | No mandatory defect. |
| Legacy plain `详细解释不可用` | S-1 | No confirmed S-1 violation. | Legacy compatibility path. |
| Unrendered aggregate `verificationPhase` | 03 I-1 | Four mandated counts/as-of/awaiting display remain correct. | No mandatory defect. |

### Repair Planning Result: DRAFT_READY

- Baseline: docs/plans/2026-10-14/batch-filter-main.md
- Verification: FAIL / PROGRESSING
- Repair artifact: docs/plans/fix/batch-filter-main/repair.md (sha256 bb1d080056783969eb766fc5e65e365de1af49f7073433a91809b837c2eb5218)
- Included finding: V-2.
- Excluded: V-1 resolved; all RECORD_ONLY items.

No product code was modified by the reviewer.
