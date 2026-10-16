# Child 04-registry — Verify Log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04-registry — docs/plans/2026-10-13/discovery-person-04-registry.md (A11-revised)
Boundary: abdf9f4645958e1ef7ef7c44963b51c4bc5898d4..de4c1a042a48ec139c95f659858e78746b6208a6
Verifier: Verify04-2

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show de4c1a0 --stat` = exactly the 7 authorized files (V156 sql, DiscoveryPerson.kt, DiscoveryPersonRepository.kt, DiscoveryPersonService.kt, DiscoveryPersonServiceTest.kt, DiscoveryPersonRepositoryTest.kt, FlywayMigrationIntegrationTest.kt); docs bookkeeping (`brief/execution/fix-log/verify-log`, `ledger.md`, plan A11 amendment in 9bf6b8e) live in separate commits. No ES/contact schema touched. |
| Plan and invariants | PASS | V156 = plan item 1 exactly (3 tables; `person_id CHAR(36)` PK, `expert_doc_id`/`author_key` PK `utf8mb4_bin`, `normalized_email` nullable index-only non-unique, `identity_fingerprint CHAR(64)`, `evidence_json`, FKs only to the 3 new tables). Enum `CREATED/ATTACHED/UNCHANGED/NEEDS_REVIEW/SCOPE_CONFLICT/STALE` and non-write of last three (DiscoveryPersonService.kt:66-125). I-2: distinct-per-kind key gate + shared-key connectivity + `BRIDGE_EXISTING_ROOTS`→NEEDS_REVIEW + ordered lock (sorted keys→person row, Repository `createPerson`/`attachToPerson`) + conflict re-read/re-verify loop (`repeat(MAX_CONFLICT_ATTEMPTS)`, `write{}`→null on `DuplicateKeyException`/`PessimisticLockingFailureException`). I-3: server re-verifies discovery tag/docId/fingerprint (`verifyMember`), `FINGERPRINT_MISMATCH`/`MEMBER_FINGERPRINT_CHANGED`→STALE, out-of-scope email/alias/author-key→SCOPE_CONFLICT with no migration. First representative = min docId on create, unchanged on attach. Rollback leaves no empty root (repo test `rolled back create leaves no empty person root`). 05/10 only future callers (`grep -rn DiscoveryPersonService|Repository src/main` excluding own module = no matches, exit 1); default off. |
| Required commands | PASS | C1 `... -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test` exit 0, Tests run 6 / Failures 0 / Errors 0 / Skipped 0; JS gate 1657/1657/0. C2 `... -Dtest=DiscoveryPersonServiceTest test` exit 0, 17/0/0/0; JS 1657/1657/0. C3 `... -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` exit 0, 45/0/0/0; JS 1657/1657/0. All matched baseline (no new failures). |
| Downstream interfaces | PASS | Registration entrypoint `register(DiscoveryPersonSnapshot): DiscoveryPersonRegistrationResult` + outcome enum (DiscoveryPersonService.kt); read queries for downstream: `findPersonIdsByDocIds` (batch docId→personId), `findMembersByPerson`, `findAuthorKeysByPerson`, `findPerson` (revision), `findMember` (fingerprint) (DiscoveryPersonRepository.kt:30-62); V156 three tables are the single storage 10 reuses. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (D1, service distinct-per-kind keys): `DiscoveryPersonService.kt:82-87` compares `allKeys.filter{startsWith(ORCID:)}.distinct()` / OPENALEX distinct and only fails when `size > 1`; a shared ORCID across members no longer raises `MULTIPLE_AUTHOR_KEYS`. Test `two different keys of the same kind are not merged` (distinct keys → NEEDS_REVIEW) + `one person with three emails...` (shared key → CREATED). Declared, necessary, within I-2 spirit.
- O-2 (D2, widened conflict signal): `write{}` (DiscoveryPersonService.kt:312-325) also catches `PessimisticLockingFailureException` (MySQL deadlock loser = `DeadlockLoserDataAccessException`) and returns null → outer loop re-reads/re-verifies; never success-on-conflict, per I-2. Matched by repo concurrency worker catching `DataAccessException`. Declared, necessary, within I-2 spirit.
- O-3 (D3, pre-existing stale V154 assertions): Base-red reasoning confirmed. `V155__create_expert_meeting_record.sql` (present at base) `ALTER`s `expert_meeting_video` adding `meeting_record_id` + FK `fk_video_meeting_owner`; base `git show abdf9f4:...FlywayMigrationIntegrationTest.kt` asserted `10L` columns (line 614) and `0L` foreign keys (lines 697-704), so C3 was already red at base independent of V156. Child corrections are minimal & necessary: 31 `targetSchemaVersion` `"155"→"156"` bumps (`grep -c '^-.*targetSchemaVersion'` = 31) plus the V156 three-table existence/contract test; the V154-scenario edits only realign to V155 reality (10→11 columns incl. `meeting_record_id`; FK count 0→1 + `foreignKeyExists(fk_video_meeting_owner)` + a `<> fk_video_meeting_owner` guard). No V156 behavior asserted by those lines. Declared, necessary, within A11 spirit.
- O-4 (P2, test-quality, non-blocking): in `DiscoveryPersonServiceTest.a unique-conflict is re-read and re-verified...`, the fake's `rejectNextCreate` flag is not exercised — the second `register` resolves to `Attach` (the shared author key is already visible to the fake), so `createPerson` is never called and the flag stays set. The test still asserts correct ATTACHED/re-read behavior, and the real unique-conflict/retry path is covered by C1 (`concurrent first saves...` on real MySQL). No production defect.

### Required Action
- COMPLETE_CHILD
