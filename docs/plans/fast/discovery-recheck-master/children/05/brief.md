# Child05 execution brief

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Exact approved plan: docs/plans/2026-10-24/discovery-recheck-05-history.md
- Approved identity: sha256:1e269d2e8ba0bee59ff0442e6e10b3e750fb9323d9dc4e666a8f5dfd80b6870a
- Product base: 6884eb7f31b1763dbb14422e91dfd63dc022fb94
- Depends on04 terminal LIGHT_PASS_WITH_NOTES. Both full ten-child artifact checkpoint modes VALID through04; clean checkpoint HEAD11a9cedf0074f831bc59f7816818355e30310c92.
- Read full exact child plan and its linked master/evidence before implementation. Use execute-p immutable plan/worktree gates. Master invocation authorizes local implementation commit, not unapproved plan edits.

## Authorized Files
Only these nine product/test files; fast-p report files separately controller-owned:
- src/main/resources/db/migration/V161__discovery_recheck_history.sql (new; plan allows only semantics-preserving version increment if161 actually occupied; document evidence before choosing)
- src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerificationContractTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt
Any extra required file, changed behavior beyond approved plan, or conflicting authority => PLAN_CONFLICT, preserve state, do not silently widen. Prior A1/A2/A3/A4 exceptions concern01/03 only.

## Required plan/invariants
- Storage/compatibility only, no production recheck start endpoint yet. Add only run.recheck_json and item.review_epoch columns to existing shared stores; independent step table as exact plan schema. New migration only, never edit applied migration. Preserve V158 data IDs/request_count/owner/evidence and ordinary null/epoch0 semantics.
- Four-column identity key(docId,identityHash,policyVersion,review_epoch). Ordinary two lookups and insert enforce0; recheck epoch=real owner execution ID>0. Two rounds independent, round retry same item. No fake policy versions/reset/move/old evidence overwrite.
- Business repository/managed reads include all ordinary epoch0 includingAUDIT; epoch>0 only actually admitted ENFORCE with nonnull admission_revision. Uncommitted queued/PENDING/AUDIT rechecks never displace prior admitted business state. Commit can supply explicit staged item only for its authoritative recomputation; only committed new result becomes business fact. Separate full history reads.
- Recheck schemaVersion1 spec immutable(actor/requestKey/scope/evidenceMode/evaluatedAt/code+parser versions); control only run.revision CAS. Valid PREPARING→READY→RUNNING→COMPLETED with documented failure/interruption/cancellation states. READY requires frozen scope hash; cannot claim early. Stale CAS and second spec rewrite rejected as approved.
- Independent eight step slots step_no1..8; unique(item_id,step_no,attempt_no); terminal attempts immutable, retry appends. Nullable hash/time rules per state. CANCELLED item outcome=null, completedAt nonnull, admissionRevision=null; never cancel an already committed item or clear committed admission.
- Ordinary recovery/listReserved and old CLI requeue affect epoch0 only. Recheck claimDue/renewLease/reserveAttempt/saveOutcome/readReserved honor input_closed and RUNNING control; cancellation/interruption prohibits new admission, while retrying already committed projection stays allowed. Keep run→item lock order and no network transaction. Reporting cancellation disjoint from queued/errors/autoAdmitted, ordinary summaries unchanged.
- Preserve prior queue/proof identity compatibility, protected/manual/contact/SBIR/APPLICATION paths, sender/outreach/mail behavior. Do not implement later child06–10 entrypoints or pipeline early.

## Required commands and runtime evidence
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest -DmysqlIt=true
- Required fresh actual MySQL IT cannot be skipped or replaced with mocked SQL. Observe old-data migration preservation, normal/recheck identity isolation, concurrent CAS/claims, business-vs-history reads, eight-step/terminal retry contracts and cancellation/projection behavior, as all named child acceptance requires.
- Run an independent disposable runtime smoke outside JUnit through actual migrated SQL/domain/repository/Gate/Query changed path; exercise ordinary item and two recheck epochs, frozen control and cancellation/read isolation. Fixture HTTP only where needed, no production queries/writes or paid provider costs. Tests alone are not smoke proof.
- Run build/lint/tests only after implementation complete; final prescribed command after last product/test edit. Remove temporary scripts/classes outside repo; report exact command/exit/count/skip/runtime boundaries. No full-system reviewer or verification skill inside fast-p loop.

## Prior outputs / downstream interfaces
- 04 rule3 frozen for new registration; Worker supports stored0..3, rejects4; old missing settings default1. Rules optional evaluatedAt affects both age and trace; later07 will wire end-to-end. Qualification RULE_VERSION3 cache key independent; analysisVersion2 and old first/bodySHA immutable.
- 03 layoutRULE_VERSION20261024; DiscoveryIdentity.VERSION20260925 and EXTRACTION_VERSION20261004 unchanged. Do not invalidate/re-sign old evidence by another version bump.
- Independent04 fresh62 JVM/1827JS with zero skips and actual isolated MySQL/extractor/Rules/version runtime passed. Existing warnings/harness limits only RECORD_ONLY; not repair tasks. No private full-batch or production bridge acceptance claimed.
- 06–08 consume the new compatible domain/repository/control/step contracts sequentially; preserve ordinary API/JSON defaults, Gate commit authority, and historical access rather than exposing a second workflow.

## Commit / evidence
- Explicit local implementation authority: feat(fast-p): implement 05. Stage only authorized product/test files, exclude all fast-p logs/ledger/brief. Controller commits verification evidence separately.
- Append full execute-p result to docs/plans/fast/discovery-recheck-master/children/05/execution.md; include exact immutable identity/base/head/commands/limits. Child logs append-only.
- No plan/ledger/other-worktree edits, unapproved test files, unrelated cleanup, push/merge/rebase/amend/reset or later-child investigation. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, exact commit SHA, command summary, report path.
