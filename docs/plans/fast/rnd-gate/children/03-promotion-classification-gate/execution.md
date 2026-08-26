# Execution Report — 03-promotion-classification-gate

- Executor: Impl03PromoGate (execute-p)
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/fast/rnd-gate/children/03-promotion-classification-gate/brief.md`
- Plan SHA-256: `7c2b25eeb19246ce45990c7056c0e862d6b4ede648f71fa51c17f3f16c58762c`
- Execution ID: `.../brief.md@7c2b25ee…`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate` @ branch `fast/rnd-gate` @ git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-rnd-gate`
- Pre-execution product code SHA: `05ad78be88861136400b0ad4b42033fe50812295` (child 02 code head); pre-execution HEAD: `30add44f`
- Commit: `b2fdf028d16b1669c9c3f481fb5b94abd77d4e60` — `feat(fast-p): implement 03-promotion-classification-gate` (6 files, +300/−4; parent 30add44)
- Result: **READY_FOR_VERIFICATION**

## Per-file changes

1. `src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt`
   - Added `val promotionGateEnabled: Boolean = false` directly after `incrementalEnabled` (I3-5/M-6 switch).
   - Extended class KDoc: the switch controls ONLY rejection of `SERVICE_ONLY`/`OUT_OF_SCOPE` in fast promotion; it does NOT control the classification write (I3-5).

2. `src/main/resources/application.yml`
   - `talent-introduction.expert-classification` section: added `promotion-gate-enabled: ${EXPERT_CLASSIFICATION_PROMOTION_GATE_ENABLED:false}` (default OFF per M-6/A3-1).

3. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`
   - `private fun classificationNode` → `fun classificationNode` (I3-4); body verbatim, still the single definition in `src/main/kotlin` (callers: backfill `:248`, promotion `:276`).

4. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt`
   - Constructor: appended two deps at END of the parameter list — `expertClassificationService: ExpertClassificationService` and `expertClassificationProperties: ExpertClassificationProperties` — **with default values** (see Deviations, D-1). `revalidateCandidates` untouched.
   - `promoteEligibleRawExperts` loop: inserted the classification gate AFTER the `evaluateEligibility` block and BEFORE the existence check (I3-6), computing `val classification = expertClassificationService.classify(profile)` fresh (I3-3, no `profile.expertClassification` read); rejects only when `promotionGateEnabled && type in {SERVICE_ONLY, OUT_OF_SCOPE}` (I3-1 wide gate), incrementing `stats.filtered` and `filterReasons.merge("CLASSIFICATION:${type.name}", 1){a,b->a+b}` then `continue`; comments carry I3-3/I3-1/I3-5 and the I3-2 `enrichExistingExperts` line-number reference (`ExpertDiscoveryService.kt:845-877`, `:850`/`:877` — verified against current tree).
   - `promoteRawToCandidate(profile: ExpertProfile, classification: ExpertClassification)`: added 4th key `put("expertClassification", expertIndexWriterService.classificationNode(classification))` inside the apply block; the other field copies and the 3 existing overlay keys untouched (I3-4/I3-5, write not gated). Call site updated to `promoteRawToCandidate(profile, classification)`.

5. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` — added mock-based gate suite (10 original tests untouched). Coverage (all 9 plan cases):
   - gate on + SERVICE_ONLY → not promoted, `filtered=1`, `filterReasons["CLASSIFICATION:SERVICE_ONLY"]==1`
   - gate on + OUT_OF_SCOPE → same with `CLASSIFICATION:OUT_OF_SCOPE`
   - gate on + UNKNOWN → promoted (I3-1 core)
   - gate on + PRODUCTION_RND / ACADEMIC_RND / HYBRID_RND → all promoted, zero `CLASSIFICATION:` keys
   - gate off + SERVICE_ONLY → promoted, no `CLASSIFICATION:` keys (I3-5)
   - gate off → written doc still contains `expertClassification` (non-null, asserted via captured doc; I3-5)
   - Robert Bosch GmbH + lastPublicationYear=2024 RAW profile → real `ExpertClassificationService` classifies `UNKNOWN` and gate on promotes it (I3-1 regression anchor)
   - promoted doc: `email`/`employment`/`institution` verbatim from rawDoc, `tags` = raw + `auto_promoted`, key set = rawDoc keys + `{candidateValidatedAt, updatedAt, tags, expertClassification}` only
   - eligibility failure (empty ORCID + requireOrcid) → filtered with `MISSING_ORCID`, `verifyNoInteractions(classificationService)` (I3-6)
   - Mockito/Kotlin null-safety handled via repo-established patterns (`eqValue`, `captureValue` elvis helpers, concrete-instance stubs, existing `ScrollExpertsMockHelper`).

6. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` — added `classificationNode output matches backfill node shape (I3-4)`: calls `service.classificationNode(...)` and asserts the 9 keys (`type`/`sendable`/`productionScore`/`researchScore`/`positiveEvidence`/`negativeEvidence`/`version`/`sourceFingerprint`/`classifiedAt`) with the same values/format as the existing backfill NDJSON assertions (`yyyy-MM-dd HH:mm:ss` via the writer's own `dateFormatter`).

## Commands (all with JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home, cwd = worktree root)

| Command | Exit | Result |
|---|---|---|
| `mvn test` (full suite) | 0 | Tests run: 2863, Failures: 0, Errors: 0, Skipped: 4 |
| `mvn test -Dtest=ExpertRevalidationServiceTest` | 0 | Tests run: 19, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test -Dtest=ExpertIndexWriterServiceTest` | 0 | Tests run: 30, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test -Dtest=ExpertClassificationServiceTest` | 0 | Tests run: 29, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test -Dtest=ExpertRevalidationServiceBehaviorTest` | 0 | Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn clean package` | 0 | Tests run: 2863, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| `git diff --check` | 0 | no output |
| `git diff src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 0 | empty (plan acceptance) |

## Acceptance greps

- I3-1: gate condition is an explicit two-type enumeration (`type == ExpertType.SERVICE_ONLY || type == ExpertType.OUT_OF_SCOPE`); no `!classification.sendable`-style rejection anywhere in `src/main/kotlin`.
- I3-2: I3-1 wide-gate assertion (tests 3/7) + gate comment preserves `enrichExistingExperts` CANDIDATE-only line refs (`ExpertDiscoveryService.kt:845-877`, `:850`/`:877`).
- I3-3: zero code reads of `profile.expertClassification` / `.expertClassification?.type` in `ExpertRevalidationService.kt` (only the I3-3 comment mentions the field).
- I3-4: `fun classificationNode` single definition in `src/main/kotlin`; writer test asserts output shape equals the backfill path.
- I3-5: tests 5/6 — gate off rejects nothing but the write still carries `expertClassification`.
- I3-6: test 9 — eligibility failure never calls `classify`.

## Deviations

- **D-1 (constructor defaults)**: the two new constructor dependencies were appended with default values (`= ExpertClassificationService()`, `= ExpertClassificationProperties()`). The plan specifies appending the two deps at the end "避免打断既有位置参数构造的测试" (to avoid breaking existing positional-argument-constructed tests). `ExpertRevalidationServiceBehaviorTest` (NOT in the authorized file set, its command must pass) constructs the service with exactly 6 positional args at two sites (`:26`, `:231`); required params would break its compilation. Kotlin defaults are the only mechanism that both (a) keeps those positional constructions compiling and (b) keeps production behavior correct: empirically verified with this exact Spring 5.3.31/Boot 2.7.18 stack via a temporary context probe (removed before commit) that Spring injects the real beans when present (so `EXPERT_CLASSIFICATION_PROMOTION_GATE_ENABLED` binds to the `ExpertClassificationProperties` bean — M-6 intact) and uses the defaults only when beans are absent (so the gate is off and classification works in the positional-arg tests). The plan's gate code uses both deps unconditionally, matching non-null defaults.
- No other deviations. No unlisted files modified; `docs/plans/fast/` left uncommitted for the controller; the temporary `ScratchKotlinDefaultsProbeTest.kt` (diagnostic only) and its stale compiled artifacts were removed before the final runs and commit.

## Notes

- `mvn clean package` also ran the 4 skipped (Docker-gated) tests as skipped — consistent with baseline.
- The pre-existing `OperatorStatusWriteSeamGuardTest` passes (1, 0, 0); no pinned lines shifted by this child's edits (pinned files are outside the authorized set).
