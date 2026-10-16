# Child 04-registry — Execution Report

Result: **READY_FOR_VERIFICATION**

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- Start HEAD: `92bf3cb1321be1067596c0615f9dbf1b74b9eacf`
- Plan (authoritative): `docs/plans/2026-10-13/discovery-person-04-registry.md` (A11-revised)
- Epoch: RESUME of epoch 1 attempt 2 (prior attempt left 6 untracked files; inspected, corrected and completed).
- Executor: Impl04b

## Commands (all run freshly, JDK 11)

Docker prefix used verbatim on this host (OrbStack socket; `docker --version` = 29.4.0):

- **C1** `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test`
  → exit 0, `BUILD SUCCESS`. `DiscoveryPersonRepositoryTest`: **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0**.
- **C2** `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test`
  → exit 0, `BUILD SUCCESS`. `DiscoveryPersonServiceTest`: **Tests run: 17, Failures: 0, Errors: 0, Skipped: 0**.
- **C3** `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
  → exit 0, `BUILD SUCCESS`. `FlywayMigrationIntegrationTest`: **Tests run: 45, Failures: 0, Errors: 0, Skipped: 0**.

JS gate inside every `mvn test` run: **tests 1657 / pass 1657 / fail 0** (≥1657 baseline, unchanged).

Intermediate (non-final, for traceability):
- C2 first run: 17 run / 1 fail (`MULTIPLE_AUTHOR_KEYS` false positive) → fixed (see below).
- C1 first run: 6 run / 1 error (deadlock loser surfaced, test only caught `DuplicateKeyException`) → fixed.
- C3 first two runs: 45 run / 1 fail each (pre-existing V154 assertions stale vs V155) → fixed.

## Changed files (7 authorized)

1. `src/main/resources/db/migration/V156__create_discovery_person.sql` (new) — three tables only:
   `discovery_person(person_id CHAR(36) PK, primary_doc_id, revision, created_at, updated_at)`,
   `discovery_person_member(expert_doc_id PK utf8mb4_bin, person_id FK, normalized_email (index only, nullable, non-unique), identity_fingerprint CHAR(64), evidence_json, timestamps)`,
   `discovery_person_author_key(author_key PK utf8mb4_bin, person_id FK)`. No edits to applied migrations; no ES/contact schema changes.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt` (new) — main-record/member/author-key DTOs, snapshot inputs, result enum `CREATED/ATTACHED/UNCHANGED/NEEDS_REVIEW/SCOPE_CONFLICT/STALE`, and `DiscoveryPersonIdentity` (author-key + fingerprint single source of truth, distinct-per-kind, normalized-name/email, layer-consistent).
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt` (new) — explicit `@Transactional` writes, ordered locking (sorted author keys → person row), unique constraints as final line; batch `docId→personId`, members/author-keys, revision, fingerprint reads.
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt` (new) — server-verified snapshot → outcome; first representative = min docId on create (never silently changed on attach); conflict → re-read + re-verify loop; no scope migration; no callers registered yet (default off).
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt` (new) — 17 tests (real `ExpertIndexWriterService` + HTTP double, in-memory repository).
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt` (new) — 6 tests on real MySQL via Testcontainers (`@DataJdbcTest`+`Replace.NONE`+`MySQLContainer`+`@DynamicPropertySource`+`-DmysqlIt=true`, same bootstrap as `MailOpenTrackingRepositoryIT`): batch reads, uniqueness/partial-root, attach revision, concurrent first save → exactly one root, rollback leaves no empty root, member evidence queryable.
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` (modified, A11) — 31 full-migration latest-version assertions `155`→`156`; new `V156 creates the three discovery person tables without touching business rows` test (existence + key column/constraint/index contracts + existing-row invariance).

Nothing under `docs/plans/fast/**` is committed (ledger left untouched).

## Deviations from plan text (all inside authorized files)

- **D1 (service): distinct-per-kind author-key conflict.** The first attempt compared raw *counts* of ORCID/OpenAlex keys across the member group; three members sharing one ORCID key wrongly triggered `MULTIPLE_AUTHOR_KEYS`. Corrected to compare **distinct** keys per kind (I-2「每类作者键不冲突」= at most one distinct key of each kind).
- **D2 (service): concurrency conflict signal widened.** Real MySQL concurrency for the same new author key can surface as `DeadlockLoserDataAccessException` (a `PessimisticLockingFailureException`), not only `DuplicateKeyException`. `write(...)` now also treats `PessimisticLockingFailureException` as a "this transaction wrote nothing" signal and re-reads/re-verifies, per I-2 (never treat conflict as success). Repository test worker similarly catches `DataAccessException`.
- **D3 (A11 file, pre-existing stale assertions): V154 migration test corrected to the V155 reality.** `V155__create_expert_meeting_record.sql` (present at base) `ALTER`s `expert_meeting_video` adding `meeting_record_id` + FK `fk_video_meeting_owner`. The V154 test still asserted 10 columns / 0 foreign keys, so C3 was **red at base** independent of V156. Corrected to: 11 columns (incl. `meeting_record_id`) and exactly one FK (`fk_video_meeting_owner`, plus a `<>` guard excluding any other FK / contact FK). No V156 behavior is asserted by these lines; the change is required to make the A11-required C3 command exit 0.

## Invariant coverage

- I-1: one docId ⇒ one person (member PK); 3 emails → 1 person / 3 members; re-run adds 0; representative fixed at min docId; no admission/email-verification truth copied (email is index-only, nullable, non-unique).
- I-2: only current-name/email-bound credential ORCID/OpenAlex keys occupy unique author keys; distinct-per-kind + layer identity consistency + shared-key connectivity gate grouping; weak records register as singletons with no author key; two existing roots bridged → `NEEDS_REVIEW` (no auto-merge); ordered locking + unique constraint; conflict → re-read + re-verify, never success-on-conflict.
- I-3: server re-verifies discovery tag / real docId / fingerprint; fingerprint change ⇒ `STALE`; out-of-scope email/alias/author-key ⇒ `SCOPE_CONFLICT` (no migration of out-of-scope objects); no write for `NEEDS_REVIEW`/`SCOPE_CONFLICT`/`STALE`.

## Unresolved items

- None blocking. No production actions; no push/merge/rebase. 05/10 remain the only future registration callers; default switch stays off.
