# Child 02 verification log

## Light Verification: LIGHT_PASS
Child: 02 / docs/plans/2026-10-22/batch-demand-02-cursors.md
Boundary: 756e8acaccc4675759152b4973e6b451a58f0a00..fb50f5e18f74a6df42eac3ec86c9a1560808b948
Verifier: Demand02LightVerifier epoch1 attempt1 2026-10-22T21:15:46+08:00

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --stat fb50f5e` contains exactly the seven authorized files (ExpertSearchService.kt, ExpertSearchCursor.kt, ExpertContactRepository.kt, ExpertSearchServiceTest.kt, ExpertSearchCursorTest.kt, ExpertContactCursorRepositoryIT.kt, OperatorStatusWriteSeamGuardTest.kt), 857 insertions and 4 deletions; the other boundary changes are child01 evidence docs/ledger, not product or test files. No product delta in `git status`. |
| Plan and invariants | PASS | I-1: `ExpertSearchCursor.kt:39-83` advances from the complete raw-hit `sort`, appends `_shard_doc` at `ExpertSearchService.kt:765`, sends no `from` and `track_total_hits=false`; I-2: 5m keep_alive and 60s size=0/match_none renewal at `ExpertSearchCursor.kt:83-121`, 3s connect/20s read private client at `ExpertSearchService.kt:47-56`, controlled `ExpertSearchUnavailableException` on timeout, shard failure, HTTP error and PIT expiry at `ExpertSearchCursor.kt:92-132`; I-3: lazy layer open, close-before-next, idempotent close and cancellation at `ExpertSearchCursor.kt:39-89,134-155`, active registry plus `@PreDestroy` shutdown at `ExpertSearchService.kt:39,781-791`; I-4: campaign MAX(id) and NEW keyset with paired nullable cursor and 1..500 validation at `ExpertContactRepository.kt:38-39,155-194`; old search/scroll/repository APIs remain (product diff has no deletions of existing declarations). The mock cursor tests cover gated request shape, raw-page advance despite filtered profiles, six renewal ticks, renewal failure, partial/malformed/nonadvancing fail-closed, lock serialization, cancellation and shutdown; the service tests cover frozen filters, auth/converter/interceptor preservation, sort shapes and the 20s read timeout; the MySQL IT covers same-second keyset, state change, high-id exclusion and zero writes. |
| Required commands | PASS | All three ran freshly at HEAD `fb50f5e`. (1) `mvn test -Dtest=ExpertSearchServiceTest,ExpertSearchCursorTest,OperatorStatusWriteSeamGuardTest` BUILD SUCCESS: JVM 105 run = 104 passed + 1 intentionally gated ES skip (96 Service + 8 Cursor + 1 Guard), 0 failures/errors; Node 1792 passed. (2) `mvn test -Dtest=ExpertContactCursorRepositoryIT -DmysqlIt=true` BUILD SUCCESS: JVM 3 passed, 0 skipped, real MySQL 8.0.36; test asserts 501 NEW rows read as first page 500 + tail 1, new id 502 excluded by the frozen upper bound, and row snapshots unchanged; Node 1792 passed. (3) `mvn test -Dtest=ExpertSearchCursorTest -DesIt=true` BUILD SUCCESS: JVM 8 passed, 0 skipped, real ES 7.14.2; smoke `documents=11001 pages=24 searchRequests=24 countRequests=0` and `earlyStopSearchRequests=3 releasedPits=2 openContexts=0`; Node 1792 passed. Child01 baseline 370 JVM + 1792 Node passed; no baseline failures and the gated skip is covered by command 3. |
| Downstream interfaces | PASS | `ExpertSearchService.openCandidateCursor(level, filters, pageSize)` and the multi-level overload at `ExpertSearchService.kt:742-749`; `ExpertSearchCursor.nextPage(): Page(profiles, exhausted)` at `ExpertSearchCursor.kt:27,39`; `ExpertContactRepository.findNewRetryUpperId` and `findNewRetryPage(campaignId, upperId, afterUpdatedAt, afterId, limit)` at `ExpertContactRepository.kt:39,156-177`; strict `findByDocumentIdsStrict` and all old search/scroll/repository read APIs remain available for 04/05. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
