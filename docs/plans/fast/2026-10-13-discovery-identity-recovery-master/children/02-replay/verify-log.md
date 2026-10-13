# Child 02-replay — Verify Log

## Light Verification: LIGHT_PASS
Child: 02-replay (docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md, A10-revised, 11 files)
Boundary: bc294d2e991e58346ce456f4e5eeb7525a2b5771..abdf9f4645958e1ef7ef7c44963b51c4bc5898d4
Verifier: Verify02-2

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status bc294d2..abdf9f4` → src/tools touched only by commit `abdf9f4` (`git show --name-only abdf9f4` = exactly the 11 amended-plan files: `OpenAlexRequestPolicy.kt`, `DiscoveryIdentityRecoveryController.kt`, `DiscoveryIdentityRecoveryService.kt`, `DiscoveryIdentityRepairService.kt`, `OpenAlexDataSource.kt`, `ExpertIndexWriterService.kt`, +3 tests, `tools/discovery-identity-recovery/replay.py`, `test_replay.py`). All other commits in range (`25c7455`,`58a9e51`,`75d979b`,`bc84bac`,`0aba5e5`,`41f3b94`) are docs-only. |
| Plan and invariants | PASS | I-1: sources only from server `externalIds` (`DiscoveryIdentityRecoveryService.kt:340-352` `resolveLocator`, PMC→JATS / DOI·PMID→OpenAlex exact), controller accepts only `docId`+2 hashes (`DiscoveryIdentityRecoveryController.kt:12-22`); old extraction not reused (re-extracts each call, `:363-398`). I-2: full risk set incl. academic keys `:43-46`, alias contact `:180`, existing proof/APPLICATION/author-id conflict `:151-157`, never overwrites filled names (`DiscoveryIdentityRepairService.kt:216-224`); identity hash recomputed via `reviewService.recordAutomaticConclusion` after repair (`RecoveryService.kt:118-131`). I-3: preview zero-write (read-only `readDiscoveryDocument`), 1 docId/call, `snapshotHash`/`proposalHash` (`:461-476`) → STALE/409 (`Controller.kt:62`), per-layer CAS `if_seq_no/if_primary_term` (`ExpertIndexWriterService.kt:986-1006`), PARTIAL never refreshes admission (`:143-146`). I-4: precise read via existing `getJson`/`reserve` (`OpenAlexDataSource.kt:77-84,344-347`) declaring DISCOVERY+SINGLETON; 429 rethrown `:356-359`; script JSONL/0700/0600/no-auth-headers (`replay.py:76-105,183-200`). A10: `SINGLETON_WORK_PATH` `/works/(W…|pmid:…|doi:…)` → SINGLETON(0) at `OpenAlexRequestPolicy.kt:1015-1018,1117`; LIST/SEARCH/CONTENT branches untouched (9-line add; `/works?` list/search still LIST/SEARCH `:1019-1020`). Zero-credit + SINGLETON-declaration proven end-to-end in `OpenAlexDataSourceTest.kt:2287-2310` (`policy.snapshot().confirmedSpentCredits==0` via real `reserve`→`validateOperationTarget`). |
| Required commands | PASS | C1 `mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` exit 0 — 156 tests/0F/0E (RecoveryServiceTest 16, ControllerTest 7, OpenAlexDataSourceTest 89, OpenAlexRequestPolicyTest 44); JS gate 1657/1657/0. C2 `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` exit 0 — 294 tests/0F/0E (ExpertDiscoveryServiceTest 184, DiscoveryPipelineServiceTest 48, DiscoveryIdentityRepairServiceTest 13, ExpertIndexWriterServiceTest 49); JS gate 1657/1657/0. C3 `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` exit 0 — `Ran 9 tests … OK`. Baseline deltas additive only: OpenAlexDataSourceTest 85→89 (+4), OpenAlexRequestPolicyTest 44→44 (unchanged, execute-only); JS 1657/1657/0 matches. No new failures. |
| Downstream interfaces | PASS | `DiscoveryIdentityRecoveryService.preview(docId): RecoveryPreview` / `apply(docId, snapshotHash, proposalHash): RecoveryApplyResult` + result types (`RecoveryPreview`/`RecoveryApplyResult`/`RecoveryStatus`/`RecoveryApplyStatus`, `RecoveryService.kt:497-547`) match brief §下游接口. Restricted repair mode `repairEmptyNameProof` has exactly one caller (recovery service; `grep` shows no automatic-incremental use) → matches "自动增量入口不启用". `OpenAlexDataSource.fetchWorkByDoi/fetchWorkByPmid` + shared `parseWork` (single author-parse rule, `:283-321`) → matches brief. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

