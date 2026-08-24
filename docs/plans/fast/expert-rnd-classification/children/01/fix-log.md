# Fix Log — Child 01 (expert-rnd-classification-core)

## Epoch 2 — Round 1/3

### Findings (A1-authorized guard pin refreshes)

- F-1: `ExpertIndexServiceTest.kt` RAW per-field PUT count pin. `assertEquals(32, singleFieldPuts, ...)` fails because `orcid_info_raw.json` now declares 33 fields (T3-mandated `expertClassification`). Refresh 32 -> 33 and the stale comment "32 in orcid_info_raw.json" -> 33.
- F-2: `OperatorStatusWriteSeamGuardTest.kt` EXCLUDED_NOISE_SITES. `NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 431, "operatorStatus = source.nullableText")` is stale — line moved to 445 (T3 imports/classification parsing shifted it; context unchanged, verified at line 445). Refresh 431 -> 445.

### Before

- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt:170`: `assertEquals(32, singleFieldPuts, "RAW batch failure must degrade to per-field PUTs for every declared field")` — expected 32, actual 33.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:61`: `NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 431, "operatorStatus = source.nullableText")` — actual hit at 445, exclusion missed.
- Full regression at epoch-1 commit a8cf172: `Tests run: 2776, Failures: 2, Errors: 0, Skipped: 4` (these two guard tests), BUILD FAILURE.

### Fix commit

- `773527c7ed2ac65d4ae92d0233be82ab7417b1ef` — `fix(fast-p): repair 01 round 1` (2 files, +5/-4; parent bfe38e9 = A1 resume commit; implementation commit a8cf172 untouched).

### Authorized files changed (A1, brief items 10-11; zero assertion-semantics change)

- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` — F-1: pin 32 -> 33 (count + comment).
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — F-2: EXCLUDED_NOISE_SITES pin 431 -> 445 (context `operatorStatus = source.nullableText` unchanged); maintenance-note comment extended with A1 rationale.

### Commands

| Command | Result | Evidence |
|---|---|---|
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexServiceTest | PASS | Tests run: 8, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest | PASS | Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | PASS | Tests run: 2776, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| git diff --check | PASS | exit 0, clean |

### Result

- FIXED (round 1/3; no remaining concerns). All required commands freshly passed this invocation; full regression green.

### Notes

- Plan identity (epoch 2): plan file amended by A1 (commit 10ec2d4f6); SHA-256 `c1f15678c3e450ce5bd09ace84b091e19a1c3815ac58bb733d2dec06efbee742` (recomputed via plan_identity.py; epoch 1 hash 0f7c8c5f superseded).
- Worktree identity unchanged: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification@fast/expert-rnd-classification@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-rnd-classification`.
- Implementation boundary now: `c004a18d..a8cf172` (epoch 1, 9 files) + `a8cf172..773527c` (fix round 1, 2 files).
- docs/plans/fast/ evidence excluded from both commits; controller commits evidence separately.

- Fix commit: 773527c7ed2ac65d4ae92d0233be82ab7417b1ef
