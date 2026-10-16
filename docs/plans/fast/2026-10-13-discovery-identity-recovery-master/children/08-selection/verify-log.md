# Child 08-selection — Verify Log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 08-selection (docs/plans/2026-10-13/discovery-person-08-selection.md)
Boundary: 2fae99a551ea656604c46a19f1f046eb3ad6593e..4ee251f7c90bea7cf556c5536f8bb522c5cebc09
Verifier: Verify08

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 2fae99a..4ee251f` = exactly the 10 authorized product/test files (DiscoveryPersonRecipientService.kt, BatchRecipientSelectionService.kt, ManualInitialOutreachService.kt, InitialOutreachService.kt, OutreachTargetIterator.kt + 5 corresponding tests) plus docs/plans/fast/** bookkeeping (07-ui execution/fix/verify-log, 08 brief/execution/fix/verify-log, ledger.md). No extra product/test files. |
| Plan and invariants | PASS | I-1: `DiscoveryPersonRecipientService.kt:118-215` runs per-member `batchRecipientSelectionService.select` first, then picks one qualified address; hard protections (MANUAL_REJECTED/IDENTITY_CHANGED at :168-175) and bound-contact protection (:178-183) checked over the whole member group so another member cannot bypass. I-2: `allMemberDocIds`/`loadMemberProfiles` (:244-262) batch-load on first encounter; deterministic order default→emailVerifiedLevel desc→docId asc (:277-289); retry `preferredDocIds` pinned (:186-198). I-3: estimation (`countEsTargets`, ManualInitialOutreachService.kt:1917-1926) and execution (`selectIncluded` via run-path :718 / :1968-1979) both call `discoveryPersonRecipientService.select`; service is read-only (no campaign/contact/member/outreach writes; asserted in DiscoveryPersonRecipientServiceTest `selection performs no writes`). Material reminder path still uses `batchRecipientSelectionService.select` (ManualInitialOutreachService.kt:1847) unchanged. Five test areas present: cross-page single selection, default-unqualified-second-qualified, HOLD/contacted protection, scope-driven member loading, retry dedupe/pin, old/new admission parity. |
| Required commands | PASS | C1 fresh: `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` → exit 0, BUILD SUCCESS. Counts: 10 / 9 / 184 / 21 / 12 = 236 run, 0 failures, 0 errors (expected 236). JS gate in same run: tests 1677 / pass 1677 / fail 0 (≥1677, 0 failures). No new failures. |
| Downstream interfaces | PASS | Selection result `PersonRecipientTarget {personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}` (DiscoveryPersonRecipientService.kt:18-25). `OutreachTargetIterator` caller-provided `dedupeKey: (ExpertProfile) -> String` added with orcid default (OutreachTargetIterator.kt:19-22,52-56). Estimation/execution/retry share one `select`/`selectedDocIds` semantics; `personKeyOf` extension point exposed for 09. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (P3, cosmetic): `DiscoveryPersonRecipientService.kt:50` KDoc above `const val ALREADY_CONTACTED` reads "人物有人工拒绝/身份冲突等硬保护，整组不可首发" — copy-paste mismatch; describes the hard-protection reasons, not ALREADY_CONTACTED (bound-contact). Comment-only, no behavioral impact; misleading to future readers.

### Required Action
- COMPLETE_CHILD
