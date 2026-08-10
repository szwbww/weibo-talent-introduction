# p1 light verification log


## Light Verification: LIGHT_PASS_WITH_NOTES
Child: p1 (sender-binding-01-schema-and-establish); plan docs/plans/2026-08-10/sender-binding-01-schema-and-establish.md
Boundary: e6662677cc715421566006bbb90e3d47a75302b6..d957683635a304d7b2f7611053250546f720e638
Verifier: P1Verifier (fresh, read-only)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Boundary diff = 13 files: 10 authorized files (V85 SQL, ExpertContact.kt, ExpertContactRepository.kt, SenderAccountBindingService.kt, InitialOutreachService.kt, ManualInitialOutreachService.kt, SenderAccountBindingServiceTest.kt, InitialOutreachServiceTest.kt, ManualInitialOutreachServiceTest.kt, BatchSendTaskRuntimeIntegrationTest.kt) + knowledge write-back K-expert-contact-two-write-sites.md (append-only, M-7) + 2 plan files which are the in-boundary A1/A2 amendment commits (96c8113, 76778b7). BatchSendTaskRuntimeIntegrationTest.kt diff = 1 import + 1 trailing ctor mock arg only (A2 compile fix, no assertion changes). No product/test file outside the authorized list touched. |
| Plan and invariants | PASS | I-1: grep `ExpertContact(` campaign/service = exactly 2 sites (InitialOutreachService.kt:55, ManualInitialOutreachService.kt:579), both pass boundSenderAccountCode/senderAccountBoundAt; zero `copy(boundSenderAccountCode` in diff. I-2: binding written in the create-row save before send (InitialOutreachService.kt:52-64; ManualInitialOutreachService.kt:576-588 new-branch only); no binding write near txHelper.recordSuccess. I-3: V85 both columns NULL, no NOT NULL/DEFAULT ''; backfill WHERE ec.bound_sender_account_code IS NULL (idempotent); ExpertContact.kt:25-26 String?/LocalDateTime? = null. I-4: ExpertContactRepository.kt:68-77 updateBindingById @Modifying @Query SET only 2 cols; test verifies updateBindingById and never save(). I-5: V85 excludes 'SIMULATOR_NOOP' (line 24); bindingFieldsFor require + resolveForSend SIMULATOR branch. I-6: boundSenderAccountCode read only in SenderAccountBindingService.kt:27 (plus field decl). I-7: gate matrix exactly per brief (requireAvailable SenderAccountBindingService.kt:40-53; manual=true stops after enabled/non-simulator). G-2/G-3 hold. M-3: resolveForSend(contact, manual: Boolean) with NO ignoreWarmup (verified no occurrence in file); tests call manual= named arg -> P2 default-param compatible. M-4: MailSenderAccountServiceTest/ManualExpertMailServiceTest/MeetingScheduleServiceTest zero-change (name-only diff grep: none). M-6: V84 was max migration, V85 correct. |
| Required commands | PASS | (1) full `mvn test` (JDK11, -B): exit 0, BUILD SUCCESS, surefire aggregate Tests run: 2249, Failures: 0, Errors: 0, Skipped: 4 (180 report XMLs; baseline 2236 + 13 new = 2249); node 479 pass/0 fail/85 suites. (2) `mvn test -Dtest=SenderAccountBindingServiceTest`: exit 0, Tests run: 10, Failures: 0, Errors: 0. (3) `-Dtest=InitialOutreachServiceTest+ManualInitialOutreachServiceTest`: FAILS as brief predicted — surefire 2.22.2 "No tests were executed!" exit 1; equivalent comma run `-Dtest=InitialOutreachServiceTest,ManualInitialOutreachServiceTest`: exit 0, Tests run: 50 (42+8), Failures: 0, Errors: 0, BUILD SUCCESS. (4) `mvn clean package`: exit 0, BUILD SUCCESS, aggregate 2249/0/0/4, WAR 46MB produced. (5) `git diff --check` base..head and at HEAD: exit 0. No new failures vs baseline. |
| Downstream interfaces | PASS | bindingFieldsFor(accountCode: String, now: LocalDateTime): Pair<String, LocalDateTime> (SenderAccountBindingService.kt:13-19); resolveForSend(contact, manual: Boolean): MailSenderAccount (line 24, no ignoreWarmup); bindIfAbsent(contactId, accountCode, now) (line 35-38); SenderAccountNotBoundException(contactId) / BoundSenderAccountUnavailableException(contactId, accountCode, reason) both extend IllegalStateException (lines 55-64); updateBindingById(id, String?, LocalDateTime?): Int column-level @Modifying @Query (ExpertContactRepository.kt:68-77); V85 two NULL cols + idx_expert_contact_bound_sender; gate matrix manual=false: enabled && !autoSendPaused && todaySentCount < warmup.effectiveDailyLimit(account) (default ignoreWarmup=false, SenderWarmupService.kt:25-28) && non-simulator; manual=true: enabled && non-simulator; no fallback re-selection. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Plan-documented command `mvn test -Dtest=InitialOutreachServiceTest+ManualInitialOutreachServiceTest` is not runnable as written under surefire 2.22.2 (exit 1 "No tests were executed!"); brief pre-anticipated this and comma-separated equivalent passes 50/50 — plan doc syntax should be updated to comma form in a later edit, no code impact.
- O-2: Plan T1.3 prose names `SenderAccountBindingService.bindOnCreate` while plan T2.1 and brief specify `bindIfAbsent`; implementation follows the brief (bindIfAbsent) — stale prose name only, no functional divergence.

### Required Action
- COMPLETE_CHILD
