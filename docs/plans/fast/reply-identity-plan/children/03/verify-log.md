# Child 03 Verification Log

Awaiting independent light verifier.

## Epoch 1 / Attempt 1

Timestamp: 2026-10-20T17:39:06Z (2026-10-21T01:39:06+08:00)

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 03 — docs/plans/2026-10-21/reply-identity-03-global-settings.md
Boundary: 75b399d6fbb6cc6b8cb859eef984524fc1342c12..d7908cd713600afebe3b44c9a83e668f737ca919
Verifier: Identity03Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 75b399d6fbb6cc6b8cb859eef984524fc1342c12 d7908cd713600afebe3b44c9a83e668f737ca919` identifies exactly the five authorized product/test files: ReplySnippetRepository.kt, ReplySnippetService.kt, ReplySnippetController.kt, ReplySnippetServiceTest.kt, ReplyIdentityDefaultsIntegrationTest.kt. Additional brief.md/ledger.md changes are controller orchestration evidence, not product/test expansion. Boundary ancestry command exited 0; inspected HEAD equals the reviewed head. |
| Plan and invariants | PASS | I-1/I-4: service lines 31–32 and 69–99 implement one-query snapshot, valid main-content candidates, deterministic selection/diagnostics and all required length-prefixed UTF-8 SHA-256 fields. I-2: repository lines 8–9 provide the exact ordered lock query; service lines 34–66 and 253–356 acquire that lock first within REPEATABLE_READ transactions for new/legacy writers, compare the fingerprint, validate both selections before saving, and perform no HTTP/ES/SMTP/draft/mail writes. I-3: service lines 40–64 validate placeholders/existing variant contents, then copy only content/default/timestamp fields without replacing variants. Fresh real-MySQL tests and retained unit tests directly exercise the acceptance scenarios detailed below. Existing isolated runtime smoke evidence is recorded at execution.md:59–71; it is not represented as a fresh verifier smoke run. No frontend product file changed, so frontend visual proof is not applicable to this child. |
| Required commands | PASS | Both plan-required commands ran freshly after implementation inspection, in the target worktree on JDK11, without exclusions or repairs. Scoped command exited 0: 34 tests, 0 failures/errors/skips, including 7 actual MySQL tests and 27 unit tests (artifact://810:462–485,2882–2885). Fresh full command exited 1: 5291 tests, 0 failures, 19 errors, 22 skipped; the complete error set matches the unrelated recorded baseline (artifact://812). This gate passes the required execution/baseline comparison, not a claim that the full suite is green. |
| Downstream interfaces | PASS | Controller lines 24–40 expose GET/PUT `/api/reply-snippets/identity-defaults` and local HTTP409/code `REPLY_IDENTITY_DEFAULTS_CHANGED`; service lines 483–504 exactly supply nullable `{id,name,content}` salutation/signature, the two option arrays, baseFingerprint, diagnostics, and request `{baseFingerprint,salutation:{id,content},signature:{id,content}}`, with the specified Chinese conflict message. Public getIdentityDefaults() supplies child 01's single snapshot; fields and PUT semantics match child 02's settings contract. HTTP integration test lines 225–255 mounts actual AuthWebConfig and GlobalExceptionHandler and verifies two sessions, 401/400/409/200. |

Evidence paths above are under `src/main/kotlin/com/weibo/talentintroduction/reply/` or `src/test/kotlin/com/weibo/talentintroduction/reply/service/`, unless stated otherwise.

#### Direct acceptance evidence

- `ReplyIdentityDefaultsIntegrationTest.kt:80–115`: non-hardcoded IDs 101/102/201/301, next legacy/default-selectable readers, unchanged explicit alternative, full variant-row snapshots, preserved metadata/other type, and an isolated schema containing only snippet/variant tables, so unintended draft/mail writes would fail.
- `ReplyIdentityDefaultsIntegrationTest.kt:117–147`: by-ID template reread after editing main content, unchanged other-ID content, empty/duplicate/unknown-placeholder/oversize/invalid-ID/type rejection, and a real MySQL trigger forcing the second selected-row save to fail with full rollback.
- `ReplyIdentityDefaultsIntegrationTest.kt:149–223`: concurrent same-fingerprint PUT has one winner/one conflict; every legacy writer serializes behind the collection lock; a concurrent GET sees the coherent precommit snapshot; a preceding legacy change causes conflict; empty-table concurrent creates allow only complete success or a recognized complete deadlock victim.
- `ReplySnippetServiceTest.kt:54–114`: explicit legacy mock lock setup, missing/multiple-default and deterministic fingerprint checks, and validation before any save. The fresh complete unit class also passes retained CRUD/frame/variant tests.
- Approved plan identity freshly confirmed by `shasum -a 256 docs/plans/2026-10-21/reply-identity-03-global-settings.md`: `a47dc08ffaee7311503a82356490d42af4dadb0f2bbefe231782d33e92d80786`, matching the brief.

#### Fresh required command receipts

Working directory for both commands: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-reply-identity-plan`.

1. `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=ReplySnippetServiceTest,ReplyIdentityDefaultsIntegrationTest test`
   - Exit 0, BUILD SUCCESS; 34 tests, 0 failures, 0 errors, 0 skipped.
   - MySQL 8.0.36 container started at an isolated localhost port; integration class explicitly reports Java 11.0.15. Integration: 7/0/0/0; unit: 27/0/0/0.
   - Finished 2026-10-21T01:33:13+08:00; elapsed 02:49; complete output artifact://810.
2. `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
   - Exit 1, BUILD FAILURE; 5291 tests, 0 failures, 19 errors, 22 skipped.
   - All 19 errors are `ExpertContactLocationServiceTest.<init>:38` with `ZoneRulesException: Unknown time-zone ID: America/Coyhaique`, matching baseline artifact://770 (5288 tests, 0 failures, 19 errors, 21 skipped).
   - mysqlIt is intentionally not enabled in this default full-suite command; its skipped integration entry is not treated as MySQL verification. The preceding fresh scoped command provides actual non-skipped MySQL evidence.
   - Finished 2026-10-21T01:39:02+08:00; elapsed 05:44; complete output artifact://812.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 — Fresh full-suite exit 1 is entirely the unchanged unrelated JDK11 tzdb baseline: 19 ExpertContactLocationServiceTest constructor errors for America/Coyhaique, 0 failures. Baseline artifact://770 and fresh artifact://812 agree on the error class/site/cause. No exclusions, unrelated investigation, configuration changes, or repairs were made; this does not block child completion under the approved brief.

### Required Action
- COMPLETE_CHILD
