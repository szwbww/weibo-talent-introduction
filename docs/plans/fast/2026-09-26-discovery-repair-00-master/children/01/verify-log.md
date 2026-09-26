## Light Verification: LIGHT_FAIL
Child: 01 — `docs/plans/2026-09-26/discovery-repair-01-page-replay.md`
Boundary: `b8789cb6062d9110218c08ce8099dba8dddd73e4..56f153df3f42d9ab5149b986c621ccf784184539`
Verifier: PageReplayVerifierFresh

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Implementation diff changes only `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` and `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`, exactly the child plan's two authorized files. |
| Plan and invariants | FAIL | I-1/I-2 dedup failure handling is evidenced by both page loops (`ExpertDiscoveryService.kt:724-831`, `:969-1112`) and new replay/cursor/ORCID tests (`ExpertDiscoveryServiceTest.kt:895-1068`). However, at `ExpertDiscoveryService.kt:1001-1004`, the ORCID per-author global limit check no longer refreshes `stats.totalAuthors` after each author is counted. Since `DiscoveryStats.refreshGlobalCounts()` derives `totalAuthors` from `bySource.authorsExtracted` (`discovery/domain/DiscoveryStats.kt:34-37`) and this path increments `sourceStats.authorsExtracted` inside that loop, multiple authors in one record can pass a stale count and exceed `maxAuthorsPerRun`; this changes the existing limit prohibited by I-4/M3. |
| Required commands | PASS | Fresh equivalent focused command `JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest` — exit 0; 229 tests, 0 failures, 0 errors, 0 skipped. Recorded pre-implementation baseline: exit 0; 226 tests, 0 failures, 0 errors, 0 skipped. |
| Downstream interfaces | PASS | The new stop reason is only the existing string-contract constant `DEDUP_INCOMPLETE` (`ExpertDiscoveryService.kt:3135`); existing `ACTIVE` checkpoint/cursor, pending-work, and terminal-status read contracts remain used by the existing checkpoint/terminal paths (`ExpertDiscoveryService.kt:402-403, 473-485, 3290-3292`). Child 02's synchronous retry work can continue from the retained source cursor without a schema or interface change. |

### AUTO_FIX
- F-1 — Child I-4/M3 requires preserving the existing run author limit. Restore `stats.refreshGlobalCounts()` immediately before the per-author `stats.totalAuthors` check at `ExpertDiscoveryService.kt:1001`; that file is authorized and the pre-patch code establishes this exact correction.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

---

## Light Verification: LIGHT_PASS
Child: 01 — `docs/plans/2026-09-26/discovery-repair-01-page-replay.md`
Boundary: `b8789cb6062d9110218c08ce8099dba8dddd73e4..29db24e66b8cb98eceb782812da34d1acbd6da06`
Verifier: PageReplayReVerifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Product boundary changes only the two authorized child files: `ExpertDiscoveryService.kt` and `ExpertDiscoveryServiceTest.kt`. The automatic fix commit `29db24e66b8cb98eceb782812da34d1acbd6da06` changes only the authorized service file, adding the single-line count refresh. |
| Plan and invariants | PASS | F-1 is resolved: `ExpertDiscoveryService.kt:1000-1003` calls `stats.refreshGlobalCounts()` immediately before checking `stats.totalAuthors >= maxAuthorsPerRun` for each ORCID author. That refresh observes the prior iteration's `authorsExtracted` increment, preserving the per-run global author cap. Both synchronous page loops track dedup-error deltas and only advance after complete processing; failure keeps the entering cursor. The tests cover the cursor combinations, ORCID dedup failure, and replay behavior (`ExpertDiscoveryServiceTest.kt:895-1067`). No broadened scope is present in the fix commit. |
| Required commands | PASS | Freshly ran `JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest` from the worktree root — exit 0; 229 tests run, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`. |
| Downstream interfaces | PASS | `DEDUP_INCOMPLETE` remains only a string stop-reason value; the existing `ACTIVE` checkpoint and retained cursor/pending-work contracts are unchanged. Child 02's retry plan consumes the retained cursor and requires no added schema or interface (`discovery-repair-02-search-retry.md`, `ExpertDiscoveryService.kt:1049-1110`). The ORCID author-limit restoration does not alter this interface. |

### Finding Lineage
| Finding | State | Evidence |
|---|---|---|
| F-1 | RESOLVED | The ORCID per-author loop refreshes global author counts immediately before its limit comparison (`ExpertDiscoveryService.kt:1000-1003`); the automatic fix commit adds only this restoration. |

### Required Action
- COMPLETE_CHILD