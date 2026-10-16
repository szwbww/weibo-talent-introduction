# Child 08-selection — Execution Report

- Result: READY_FOR_VERIFICATION
- Plan: docs/plans/2026-10-13/discovery-person-08-selection.md (sha256 aed785ba70933fd10ef73ee9a3934c5714d195894e54f69e2b15386919869916)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
- Branch: fast/2026-10-13-discovery-identity-recovery-master
- Pre-execution HEAD: 0550691134d76f7c4ef7c3366964bd60cc22e456
- Executor: Impl08 (execute-p)

## Commands

| # | Command | Result |
|---|---|---|
| C1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` | PASS (exit 0), BUILD SUCCESS |
| C1 (JS gate, same run) | exec-maven-plugin `node --test` | tests 1677 / pass 1677 / fail 0 / skipped 0 |

Per-class surefire counts (final fresh run):

- DiscoveryPersonRecipientServiceTest: 10 run, 0 fail, 0 error
- BatchRecipientSelectionServiceTest: 9 run, 0 fail, 0 error
- ManualInitialOutreachServiceTest: 184 run, 0 fail, 0 error
- InitialOutreachServiceTest: 21 run, 0 fail, 0 error
- OutreachTargetIteratorTest: 12 run, 0 fail, 0 error
- Total: 236 run, 0 fail, 0 error

## Change summary

1. **New `DiscoveryPersonRecipientService`** (shared read-only person-selection layer):
   - `select(scope, profiles, onPerEmailSelection, preferredDocIds) -> PersonRecipientSelection` with per-person targets `{personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}` and `selectedDocIds` (one per person, downstream-compatible with the old `includedDocIds` filter).
   - Groups candidates by personId via 04's `DiscoveryPersonRepository.findPersonIdsByDocIds`; on first encounter loads the person's full member set (04 relations) plus their current ES profiles (`findByDocumentIds` across scope funnel levels), then calls the existing per-email `BatchRecipientSelectionService.select` once per person group.
   - Per-email qualification = selector-included AND email non-blank AND not suppressed (`EmailSuppressionService`). Selection ordering: person display default email only if qualified, else `emailVerifiedLevel` desc (null last), then docId asc.
   - Person-level protections across the whole valid group: any member MANUAL_REJECTED / IDENTITY_CHANGED, or any member orcid with a bound `ExpertContact`, excludes the whole person (cannot bypass via another member). Retry `preferredDocIds` pins the original address; heading it ineligible excludes instead of switching.
   - `selectAdmission(...)` reuses `BatchRecipientSelectionService.admissionOnlyScope()` for the legacy path.
   - Dependency direction: person service → email selector (no mutual injection). Non-person records (no 04 mapping) fall back to exact per-email behavior, preserving out-of-scope results.
   - No writes: repository reads + ES `_mget` only; no campaign/contact/member/outreach rows.

2. **`BatchRecipientSelectionService`**: extracted `companion.admissionOnlyScope()` (public) so the person layer and legacy path consume the identical admission-only scope; `selectAdmission` now delegates to it. Per-email determinations and precise rejection reasons unchanged.

3. **`OutreachTargetIterator`**: `seenOrcids` renamed `seenKeys` and a caller-provided `dedupeKey: (ExpertProfile) -> String` extension point added (default preserves the prior normalized-orcid behavior; non-person records stay on their original identity). Documented that the pre-review marking is raw-page dedupe only and must not be used for person-level "processed" (plan warning about mark-then-review order); person-level collapse is done by the selection layer.

4. **`ManualInitialOutreachService`**: added `discoveryPersonRecipientService` (default wraps the injected `batchRecipientSelectionService`). Estimation (`countEsTargets`), ES target page filtering (`selectIncluded`) and NEW retry (`buildRetryableTargets`, passing `preferredDocIds` = original attempt docIds) now use the person-selection layer; `onPerEmailSelection` still feeds the admission preview accumulator. Material reminders keep the per-email selector unchanged.

5. **`InitialOutreachService`**: `fetchSendableCandidates` admission filtering moves to `discoveryPersonRecipientService.selectAdmission(...).selectedDocIds`.

## Tests added

- `DiscoveryPersonRecipientServiceTest` (new, 10): cross-page members → one qualified address; default member filtered then second qualified chosen; manual HOLD blocks whole person; already-contacted blocks whole person; all-suppressed excluded; scope-driven member loading; safe-retry original address; retry preferred ineligible; non-person per-email parity (no ES/suppression/contact reads); no writes + per-person reason hits.
- `BatchRecipientSelectionServiceTest`: admission-only scope ignores INTRODUCTION expert type (UNCLASSIFIED admitted).
- `ManualInitialOutreachServiceTest`: person-aware `countBySnapshot` → same-person 3 members counted once (estimate == target), ordinary expert unaffected, no member loading when all in batch.
- `InitialOutreachServiceTest`: person-aware `sendInitialBatch` → one candidate/one address per person, exactly one contact saved.
- `OutreachTargetIteratorTest`: caller-provided person dedupe key collapses same-person ES members; existing tests updated for `seenKeys`.

## Deviations / decisions

- The iterator `dedupeKey` extension point is added but the ES-page path deliberately does not pass a person key: per the child plan's own warning ("人物 seen 不得沿用先标记后审核的顺序"), using a person key in the iterator's pre-review marking would drop the qualified member when it appears on a later page. Person-level collapse is instead realized through `selectedDocIds` (deterministic single selection), which yields the same "one target per person across pages" result. `dedupeKey` remains available for 09's target-set reuse.
- Person-level "already contacted" protection uses bound `ExpertContact` (the existing ES-path proxy for history-sent/bound). Full mail_record/mail_send_attempt history protection is 09's scope (09 I-3) and was not duplicated here.
- Non-person (no 04 mapping) records intentionally bypass the suppression/contact qualification so out-of-scope and existing behaviors are byte-for-byte preserved.

## Unresolved items

- None blocking. 09 must re-verify the selection (person revision + selected address eligibility) before send, per its own invariants.

## Files (commit: exactly these 10)

1. src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt (new)
2. src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt
3. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
4. src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt
5. src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt
6. src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt
8. src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt
9. src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt
10. src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt
