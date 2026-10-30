# Child 04a — execution

## Epoch 1 — pre-edit prerequisite / PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-04a-profile-tags.md`
- SHA-256 before and after preflight: `0ffa45716aa98398dceab5a11f581d5630b870e73541babd99fbb2b2af52e4aa` (12145 bytes).
- Execution ID: canonical plan path above + `@0ffa45716aa98398dceab5a11f581d5630b870e73541babd99fbb2b2af52e4aa`; NEW invocation, epoch 1.
- Approval basis: current epoch-1 brief and standing user authorization; executor `ExpertAccess04aImpl`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`.
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Common git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Pre-execution / unchanged product SHA: `d5093a2509427526784254aba7dbe98ff2a9b1d9`.
- Before / after preflight HEAD: `2c613429f0f61169f4f4648989eb1e245dcc92f6` (`docs(fast-p): checkpoint 03`).
- No 04a implementation or product commit; evidence HEAD remains the above existing HEAD until controller evidence commit. Product boundary remains empty.
- Initial dirty files were controller-owned `children/04a/brief.md` and `ledger.md`; preserved.

### Prerequisite: exact additional fixture paths

The current child authorizes five files. Required nonnullable controller security dependencies, session-aware handler signatures, precise handler activation, and the existing line-number scanner need these six additional test files. No additional product behavior/file is requested:

1. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` — direct constructor wiring and direct addTag/removeTag/getExpertProfile calls must supply the real handler session context; retain ES document-id resolution, fallback and exception assertions.
2. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt` — selected-controller MVC slice must provide newly required nonnullable security dependencies; retain original admin/automatic-task contracts.
3. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt` — both direct constructor and MVC slice dependency wiring; preserve independent absent/blank-session rejection and version/error/admin contracts.
4. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt` — selected-controller MVC slice dependency wiring for actual processing/tag ownership; preserve existing summary/date/statistics contracts.
5. `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` — MVC includes InboundMailSummaryController; required dependencies must be wired. Its exhaustive exact-handler allowlist must include only completed 04a methods. Retire obsolete blanket tag-write denial wording, while retaining actual NONE/READ/cross-resource denial, admin behavior and no-business-side-effect assertions; no incidental source-wording pins.
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — update only ExpertIndexController noise-site line numbers after authorized controller insertions; preserve exact paths/snippets and scanner rules.

Controller must first record bounded 5→11 fixture-only scope and corresponding master I-14 exception, add each fixture's fresh required gates, commit plan-only amendments, then dispatch the new immutable execution identity. No nullable/default security dependency, setter injection bypass, test removal or product widening is authorized by this prerequisite.

### Consumer investigation receipts

Language tooling probe `read("xd://lsp")` returned: `No such tool: xd://lsp`; fallback used exact type, constructor, Spring/import and direct-method receipts before any product edit.

Receipt A — `functions.grep`:

```text
pattern: \b(ExpertIndexController|ExpertManualProfileController|InboundMailSummaryController)\b
path: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin
case: true; gitignore: true; skip: null
```

Complete matching-line output (context-only rows omitted, line numbers unchanged):

```text
src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt
7:import com.weibo.talentintroduction.mail.controller.InboundMailSummaryController
65:        AllowedHandler("GET", InboundMailSummaryController::class.java, "tagOptions"),
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt
40:class ExpertIndexController(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt
42:class ExpertManualProfileController(private val service: ExpertManualProfileService) {
src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertManualProfile.kt
233: * 01 私有业务异常：只由 `ExpertManualProfileController` 的局部 `@ExceptionHandler` 映射为固定
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt
25:class InboundMailSummaryController(
src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
14:import com.weibo.talentintroduction.mail.controller.InboundMailSummaryController
96:        InboundMailSummaryController::class,
548:                "InboundMailSummaryController#tagOptions:GET",
src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
79:        NoiseSite("com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt", 80, "operatorStatus = operatorStatus"),
82:        NoiseSite("com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt", 477, "operatorStatus = operatorStatus ?: expert.operatorStatus"),
84:        NoiseSite("com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt", 108, "operatorStatus = contact?.operatorStatus ?: expert.operatorStatus"),
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt
36:@WebMvcTest(ExpertIndexController::class)
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt
53:    private val controller = ExpertIndexController(
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt
49:@WebMvcTest(controllers = [ExpertManualProfileController::class])
302:        val controller = ExpertManualProfileController(service)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt
21:@WebMvcTest(InboundMailSummaryController::class)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
2088:    /** 真实落库 CUSTOM 标签行（写路径由既有 InboundMailSummaryController/服务覆盖）。 */
```

The final match is a comment, not a constructor/import/Spring consumer; no MailboxConversationControllerTest modification requested.

Receipt B — `functions.grep`:

```text
pattern: @WebMvcTest|@Import|import .*Expert(MailPermissionGuard|MailReadAccessService|ProfileIdentityResolver)|import .*InboundMailTagRepository|import .*NamedParameterJdbcTemplate|private lateinit var (resourceGuard|service|authService|expertSearchService|expertContactRepository|inboundMailProcessingRepository|inboundMailTagService|mailRecordRepository)|val controller = Expert|private val controller = Expert
path: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
case: true; gitignore: true; skip: null
```

Complete matching-line output:

```text
ExpertIndexControllerTest.kt
53:    private val controller = ExpertIndexController(
ExpertIndexControllerMvcTest.kt
36:@WebMvcTest(ExpertIndexController::class)
43:    private lateinit var expertSearchService: ExpertSearchService
46:    private lateinit var expertContactRepository: ExpertContactRepository
ExpertManualProfileControllerTest.kt
49:@WebMvcTest(controllers = [ExpertManualProfileController::class])
50:@Import(AuthWebConfig::class, ExpertManualProfileControllerTest.KotlinObjectMapperConfig::class)
54:    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
63:    @MockBean private lateinit var service: ExpertManualProfileService
64:    @MockBean private lateinit var authService: AuthService
302:        val controller = ExpertManualProfileController(service)
InboundMailSummaryControllerTest.kt
21:@WebMvcTest(InboundMailSummaryController::class)
28:    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository
31:    private lateinit var inboundMailTagService: InboundMailTagService
34:    private lateinit var expertContactRepository: ExpertContactRepository
37:    private lateinit var mailRecordRepository: MailRecordRepository
AuthInterceptorTest.kt
84:@WebMvcTest(
100:@Import(AuthWebConfig::class, AuthInterceptorTest.KotlinObjectMapperConfig::class)
104:    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
133:    private lateinit var authService: AuthService
178:    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository
181:    private lateinit var inboundMailTagService: InboundMailTagService
193:    private lateinit var expertContactRepository: ExpertContactRepository
196:    private lateinit var mailRecordRepository: MailRecordRepository
```

Receipt C — direct method source reads:

- `ExpertIndexControllerTest.kt:53-57`: constructs ExpertIndexController with existing services and named personQueryService; no new required security dependency.
- `ExpertIndexControllerTest.kt:346-352`: `controller.addTag(ExpertTagMutationRequest(orcidId = "orcid-1", tag = "承诺回复材料", level = ExpertIndexLevel.CANDIDATE))`.
- `ExpertIndexControllerTest.kt:375-381`: equivalent `controller.removeTag(...)`.
- `ExpertIndexControllerTest.kt:475,487,500`: direct `controller.getExpertProfile("orcid-1"/"orcid-absent"/"orcid-err", ExpertIndexLevel.CANDIDATE)`.
- `ExpertManualProfileControllerTest.kt:302-309`: `ExpertManualProfileController(service)` then direct get/patch with absent/blank session; expects 401 and no service interaction.
- MVC dependency blocks read: ExpertIndexControllerMvcTest 36–67; InboundMailSummaryControllerTest 21–37; ExpertManualProfileControllerTest 49–64; AuthInterceptorTest 84–104/178–196. Existing guard mocks in the latter two do not provide missing identity/tag-query dependencies.

Receipt D — exact policy/line-guard consumers:

- `AuthInterceptorTest.kt:508-584` scans real RestControllers and asserts the entire sorted allowed-handler set; current set includes only InboundMailSummaryController#tagOptions among 04a methods (line548). Activating the approved eight guarded handlers (ExpertIndex addTag/removeTag/getExpertProfile, manual-profile get/patch, inbound addTag/autoApplyTags/deleteTag) necessarily changes this exact set.
- `AuthInterceptorTest.kt:420-462` sends `POST /api/inbound-summary/mails/1/tags` with `{"tag":"x"}`, expects blanket403 and verifies zero processing/tag-service interaction. Retain meaningful permission denial/no-side-effect coverage rather than retaining an obsolete global route-denial assumption once the method is precisely opened.
- `OperatorStatusWriteSeamGuardTest.kt:79,82,84` contains exact ExpertIndexController noise pins at80/477/108, quoted in Receipt A.
- `OperatorStatusWriteSeamGuardTest.kt:139-155` scans all main Kotlin source lines and excludes only exact noise-site path/line/context.
- `OperatorStatusWriteSeamGuardTest.kt:182-190` separately fails stale noise-site line/context. Thus normal import/constructor/guard insertion in the authorized index controller requires line-only fixture adjustment, not scanner weakening or source layout hacks.

Read-only downstream facts: ExpertIdNormalizer normalizes trim + EMAIL suffix lowercase / other keys uppercase; ExpertProfileIdentityResolver.requireKnown may accept an existing profile row before checking contact/ES, so existence alone cannot authorize a user1 profile. Existing guard requireFull uses current per-contact access and does not grant shared-identity contacts. Keep all these constraints in the new epoch.

### Commands, counters, runtime and limitations

| Command/tool | Actual result |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-04a-profile-tags.md` | Exit0, twice; exact same canonical path/hash/size above |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-04a-profile-tags.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master` | Exit0; expected root/branch/git-dir and HEAD above |
| Same worktree helper plus `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master` | Exit0; same identity before handoff/hold |
| `git status --short` | Exit0; initial controller-owned dirty brief/ledger only |
| `git log -4 --format='%H %s' && git diff --name-only d5093a2509427526784254aba7dbe98ff2a9b1d9..HEAD` | Exit0; intervening03 evidence-only commits, no product differences |
| Required Maven/JDK11/mysqlIt/migrationIt/JS/build commands | NOT_RUN: pre-edit prerequisite; no passed/failed/skipped test counts claimed |
| Actual authenticated API/JDBC/controlledES runtime smoke | NOT_RUN: no implementation or runtime services started |

- Product files changed:0; product commits:0; implementation/test tasks remain PENDING/BLOCKED by immutable scope. No test/formatter/build execution, no baseline-failure rerun, no scaffolds/services to clean.
- Only this authorized execution evidence append was written; controller-owned plan/brief/ledger remain untouched.
- Existing child03 verification is historical context only, not current04a evidence.
- Full UI/calendar/send/review clauses remain downstream where outside04a; none are waived, claimed executed or newly implemented.
- Required next action: controller plan-only amendment for exact six fixtures and master I-14, then new-epoch execution using updated plan identity. Per controller instruction, executor holds read-only after this report rather than mutating product or self-approving the amendment.

## Epoch 2 — READY_FOR_VERIFICATION

This is execution evidence, not an independent PASS or completed human acceptance.

### Identity and provenance

- Plan canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-04a-profile-tags.md`.
- Approval identity: `commit:c3afa57cfe2b448470b8968ac7cc47e24963bbc9`; actual SHA-256 before and after execution: `2f2c40c18aa1e625fda490de27f82224603e41b25b5a246c37dca34fcb6cbdbf`, 14340 bytes.
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-04a-profile-tags.md@2f2c40c18aa1e625fda490de27f82224603e41b25b5a246c37dca34fcb6cbdbf`; NEW identity / epoch 2. Epoch-1 receipts are historical prerequisite evidence, not current test evidence.
- Approval basis: epoch-2 brief and recorded standing human authorization. Executor: `ExpertAccess04aResume`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch: `fast/expert-access-master`.
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Pre-execution product SHA: `d5093a2509427526784254aba7dbe98ff2a9b1d9`; pre-execution HEAD: `c3afa57cfe2b448470b8968ac7cc47e24963bbc9`.
- Post-execution product SHA / current Evidence HEAD: `020bda37127f92187589e9d8f10e3bb3cbccb187`, subject `feat(fast-p): implement 04a`. Controller owns any later evidence-only commit.
- Product boundary: `d5093a2509427526784254aba7dbe98ff2a9b1d9..020bda37127f92187589e9d8f10e3bb3cbccb187`; this product commit contains exactly the authorized eleven product/test paths, no plans/reports/artifacts.
- Controller-owned dirty brief, ledger and pre-existing epoch-1 report text were preserved. Only this epoch-2 report append is an executor evidence edit.

### Implemented requirements and downstream contract

| Requirement | Status | Implementation / exercised evidence |
|---|---|---|
| I-2 / I-4 expert identity + actual current layer | IMPLEMENTED | Index tag add/remove/profile and manual-profile get/patch read the session, query real FULL grants joined to current contact layer, and compare using existing `ExpertIdNormalizer`. No ES lookup/writer runs before reader authorization. Same canonical identity on another contact does not add a grant to that mailbox. |
| I-3 inbound tags | IMPLEMENTED | Add/auto read actual processing ownership; delete resolves tag→processing→contact; `ExpertMailPermissionGuard.requireFull` uses current access. Null-contact writes deny. Creation audit prefers session over forged body operator. Existing background tag service remains session-independent. |
| Manual-profile 403 / CAS | IMPLEMENTED | Dedicated local permission handler returns 403 rather than catch-all500. Existing identity resolver, grouped field validation and expectedVersion transaction/CAS remain authoritative. Real MySQL test and real HTTP/JDBC show version0→1, old version409, audit user1, admin readback. |
| Exact staged admission | IMPLEMENTED | Opened only the guarded handler/verb entries; existing tagOptions remains allowed. Global search/reindex/summary content endpoints remain denied. Exhaustive AuthInterceptor handler-set regression passes. |
| Behavioral regressions | IMPLEMENTED | New `ExpertScopedOperationsIT` has five real-MySQL/AuthWebConfig/MVC tests, including READ/NONE/cross-resource, normalized shared identity, current layer, live revocation, QA/CUSTOM tags, forged operator, unbound processing, background/admin and version conflict. |
| I-8 / I-11 / I-13 inherited boundaries | PRESERVED | Original calendar controller/service/send and review IT gates pass; no send/draft/calendar services or frontend files changed. New scope does not grant SMTP, attachment, global management or approval authority. Cross-phase UI/full workflow acceptance remains at its approved downstream stage, not claimed complete here. |

Fresh consumer audit before product edits used the same exact-type fallback as epoch1 (LSP was already proved unavailable there): `functions.grep`, pattern `\b(ExpertIndexController|ExpertManualProfileController|InboundMailSummaryController)\b`, paths `src/main;src/test/kotlin` under this worktree, case-sensitive. Output named the same controller declarations, policy import/tagOptions, AuthInterceptorTest import/MVC/exact table, index direct constructor, index MVC slice, manual-profile MVC/direct constructor, inbound-summary MVC, and three lineguard pins recorded in epoch1; the mailbox conversation mention remained a comment. No additional consumer file was needed. Original mandatory security dependencies are nonnullable; no nullable/default security shim was introduced.

Fresh precise-admission receipt (`functions.grep`; path `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`; pattern `AllowedHandler\("(GET|POST|PATCH|DELETE)", (ExpertIndexController|ExpertManualProfileController|InboundMailSummaryController)::class.java`):

```text
67: AllowedHandler("GET", InboundMailSummaryController::class.java, "tagOptions"),
68: AllowedHandler("POST", InboundMailSummaryController::class.java, "addTag"),
69: AllowedHandler("POST", InboundMailSummaryController::class.java, "autoApplyTags"),
70: AllowedHandler("DELETE", InboundMailSummaryController::class.java, "deleteTag"),
71: AllowedHandler("POST", ExpertIndexController::class.java, "addTag"),
72: AllowedHandler("POST", ExpertIndexController::class.java, "removeTag"),
73: AllowedHandler("GET", ExpertIndexController::class.java, "getExpertProfile"),
74: AllowedHandler("GET", ExpertManualProfileController::class.java, "get"),
75: AllowedHandler("PATCH", ExpertManualProfileController::class.java, "patch"),
```

The pre-edit policy receipt had only old tagOptions among those rows. Existing explicit resource-handler routes and unrelated reader handlers were accumulated, not replaced. AuthInterceptorTest retires the interim globally denied tag-write request; real ownership denials/zero writes are covered by the new IT, and the exhaustive precise-handler contract remains. Lineguard changes are only the original index-controller pin line numbers `80→86`, `477→502`, `108→114`, with unchanged paths/snippets/scanner.

### Exact machine commands and results

All Maven commands below ran sequentially in the target worktree, after source writing stopped for each attempt. Environment prefix used on test commands:

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
```

1. Initial command (exit1; `artifact://2319`):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertScopedOperationsIT,ExpertManualProfileControllerTest,ExpertManualProfileServiceTest,MeetingCalendarControllerTest,MeetingCalendarServiceTest,MailReviewDraftIT,ExpertIndexControllerTest,ExpertIndexControllerMvcTest,InboundMailSummaryControllerTest,AuthInterceptorTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

Observed 114 JVM tests, 0 failures, 5 errors, 0 skipped. The new IT fixture omitted required `inbound_mail_processing.process_reason`; all five errors were the seed INSERT, not permission behavior. Added that existing required column/value; no schema/product change or weakened assertion.

2. Second command (exit1; `artifact://2323`):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertScopedOperationsIT,ExpertManualProfileControllerTest,ExpertManualProfileServiceTest,MeetingCalendarControllerTest,MeetingCalendarServiceTest,MeetingCalendarSendIntegrationTest,MailReviewDraftIT,ExpertIndexControllerTest,ExpertIndexControllerMvcTest,InboundMailSummaryControllerTest,AuthInterceptorTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

Observed 121 JVM tests, 0 failures, 5 errors, 0 skipped. Same-identity sibling fixture hit existing unique `(campaign_id,orcid_id)`; placed sibling in a distinct existing campaign, preserving shared-normalized-identity/other-email denial coverage. Also corrected the new profile fixture to supply the existing identity group's full field set (talentCode/chineseName/birthDateText/documentNumber/sourceCountry), as required by production validation. No production behavior changed during fixture corrections.

3. Final exact scoped gate (exit0; `artifact://2326`; 2026-10-30 16:16:40 +0800):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertScopedOperationsIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

JVM: 5 passed, 0 failed/errors, 0 skipped. Real MySQL8.0.36 Testcontainers; actual Flyway migration chain applied through V170 (169 applied migrations). Maven's bound frontend gate: 1961 tests / 352 suites, 1961 passed, 0 failed/cancelled/skipped/todo; app and task-modal-runtime syntax checks exit0.

4. Final original regressions (exit0; `artifact://2328`; 2026-10-30 16:21:31 +0800):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertManualProfileControllerTest,ExpertManualProfileServiceTest,MeetingCalendarControllerTest,MeetingCalendarServiceTest,MeetingCalendarSendIntegrationTest,MailReviewDraftIT,ExpertIndexControllerTest,ExpertIndexControllerMvcTest,InboundMailSummaryControllerTest,AuthInterceptorTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

JVM: 116 passed, 0 failed/errors, 0 skipped. Per-class counts: AuthInterceptor19, InboundMailSummaryController2, MailReviewDraftIT12, MeetingCalendarSendIntegration7, OperatorStatusWriteSeamGuard1, MeetingCalendarController6, MeetingCalendarService18, ExpertIndexControllerMvc5, ExpertManualProfileController9, ExpertIndexController20, ExpertManualProfileService17. Bound frontend gate again 1961 passed / 0 failed / 0 skipped plus both syntax checks exit0. Relevant opt-in properties explicitly enabled; no target test skip counted as passing.

5. Runtime classpath (exit0; no WAR/testclasses used):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert04a-epoch2.classpath
```

6. Execution identity/scope/provenance commands (each exit0):

```sh
python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-04a-profile-tags.md
python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-04a-profile-tags.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master
git diff --check
git merge-base --is-ancestor HEAD fast/expert-access-master
git show --format=fuller --stat HEAD
git diff --exit-code -- src/main src/test
git diff --cached --exit-code
```

Identity helper ran at entry and final, and immediately before exact-path git add and before git commit. Product/test working tree and index are clean at handoff; control-plane files intentionally remain unstaged for controller. Commit command was `git commit -m 'feat(fast-p): implement 04a'`; only the eleven authorized source/test paths were passed to `git add`.

### Actual changed-path runtime, beyond tests

This was the genuine application main from `target/classes` with runtime dependencies, production AuthService/AuthWebConfig/controllers/JDBC/services and actual HTTP login cookies. Only ES was a controlled loopback provider; it was not a real Elasticsearch cluster, and is not represented as one. No production database, provider, SMTP or IMAP was accessed.

Runtime setup commands:

```sh
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker run --detach --rm --name expert04a-epoch2-mysql -e MYSQL_ROOT_PASSWORD=scoped-fixture -e MYSQL_DATABASE=scoped_runtime -p 127.0.0.1:33474:3306 mysql:8.0.36
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker exec expert04a-epoch2-mysql mysql -uroot -pscoped-fixture -e 'SELECT VERSION(), DATABASE();' scoped_runtime
```

Both exit0, readiness query returned MySQL8.0.36/scoped_runtime. Eval started an actual `ThreadingHTTPServer(('127.0.0.1',19474), Expert04ES)` with in-memory tags, recorded search/update calls and mutation semantics. Application service `expert04a-epoch2-app` ran actual `java` binary above with `-cp cp04` where `cp04 = target_worktree + '/target/classes:' + Path('/tmp/expert04a-epoch2.classpath').read_text().strip()`, main `com.weibo.talentintroduction.TalentIntroductionApplicationKt`, and these exact application arguments:

```text
--server.address=127.0.0.1
--server.port=18474
--spring.datasource.url=jdbc:mysql://127.0.0.1:33474/scoped_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
--spring.datasource.username=root
--spring.datasource.password=scoped-fixture
--spring.flyway.placeholder-replacement=false
--talent-introduction.elasticsearch.base-url=http://127.0.0.1:19474
--talent-introduction.elasticsearch.username=fixture
--talent-introduction.elasticsearch.password=fixture
--talent-introduction.expert-discovery.enabled=false
--talent-introduction.scheduling.enabled=false
--talent-introduction.mail-queue.enabled=false
--talent-introduction.mail-attachment-storage.base-path=/tmp/expert04a-epoch2-attachments
```

Service reported ready PID26427, actual Java11.0.33 and application startup on 18474 (`artifact://2330`). Disposable Java source-launch JDBC fixture was executed with `[java04,'-cp',cp04,'/tmp/Expert04RuntimeSeed.java','seed']` (exit0). It used BCrypt to set fixture passwords, seeded contacts7401 FULL /7402 READ /7403 NONE, and contact7404 same normalized identity as7401 in a different campaign/other email without grant. Processing rows belonged to their real contacts. JDBC before requests showed zero profile/tag/outbound rows.

Actual Python urllib clients used cookie jars, JSON HTTP requests and asserted every expected status. Observed:

- Real `POST /api/auth/login` for user1/admin:200, `mailReadOnly=true/false` respectively; real `/api/auth/me`:200 for both.
- Initial denial matrix: nineteen requests; READ/NONE expert tag add/remove/profile/manual get/patch, READ/NONE/sibling mail-tag add/auto, wrong RAW layer, global experts/summary content all403; ES call delta0. JDBC snapshot still had no manual profile/tag rows and outbound_count0.
- FULL runtime-a: ES tag add200, get200 contained runtime-tag, remove200, get200 no runtime-tag. Controlled provider observed exactly six real HTTP calls: candidate `_search`, `_update/doc-runtime-a`, `_search`, `_search`, `_update/doc-runtime-a`, `_search`; original expert tags `新发现` / `瑞典` remained.
- FULL manual-profile normalized `" RUNTIME-A "` get200 version0; patch200 version1; same expectedVersion patch409; admin get at RAW200 read back the same canonical RUNTIME-A profile and talentCode runtime0042.
- FULL mail custom add200 despite forged body operator admin; persisted created_by=user1. Admin could add foreign tag200. Reader foreign tag delete403; own delete200. Own auto-tag200 and tagOptions200. A retained custom tag demonstrated live downgrade denial without deleting audit evidence.
- Existing READ conversation messages200 retained foreign-admin CUSTOM label, accessLevel READ, attachmentCount0, firstAttachmentNames empty, calendarAttachment null, outboundAttachments empty.
- JDBC fixture `downgrade` invocation exit0 changed7401 to READ while preserving the actual HTTP session. All eight newly activated handlers then returned403 on fresh requests for7401; ES delta0.
- Anonymous real profile request401; real user1 PUT to `/api/mail/mailbox/conversations/7401/read-access/user1`403; admin GET of that endpoint200 still READ. An earlier probe of non-handler `/api/mail/expert-read-access/7401` also403 is counted only as default-denial evidence, not as grant-handler coverage.
- Total observed HTTP requests51: 19×200, 1×401, 30×403, 1×409.

Final independent JDBC source-launch snapshot exit0, after downgrade denials:

```text
SELECT expert_key,version,updated_by,talent_code FROM expert_manual_profile
expert_key=RUNTIME-A | version=1 | updated_by=user1 | talent_code=runtime0042
SELECT id,inbound_processing_id,tag_type,label,created_by FROM inbound_mail_tag
id=2 | inbound_processing_id=7402 | tag_type=CUSTOM | label=foreign-admin | created_by=admin
id=3 | inbound_processing_id=7401 | tag_type=CUSTOM | label=保留审计 | created_by=user1
SELECT expert_contact_id,access_level FROM expert_mail_read_access
expert_contact_id=7401 | access_level=READ
expert_contact_id=7402 | access_level=READ
SELECT COUNT(*) AS outbound_count FROM mail_record WHERE direction='OUTBOUND'
outbound_count=0
```

Runtime harness correction disclosed: initial READ-projection assertion used nonexistent JSON key `attachments` and raised Python KeyError after its HTTP200. Read the actual response keys and corrected the assertion to existing attachmentCount/firstAttachmentNames/calendarAttachment/outboundAttachments, without repeating the already-completed tag mutation or changing product. This was instrumentation, not a hidden product failure.

Cleanup completed: stopped owned app through `proc://expert04a-epoch2-app/kill`; shutdown/closed loopback ES thread (confirmed stopped); `docker stop expert04a-epoch2-mysql` exit0 removed the `--rm` container; deleted owned temporary Java seed and classpath files. No runtime scaffold or fixture entered the product commit.

### Changed files, freshness and limits

- `ExpertIndexController.kt`: nonnullable JDBC dependency and session/current-layer/normalized-identity FULL admission for assigned operations.
- `ExpertManualProfileController.kt`: same real identity/layer admission plus explicit403; existing CAS/service retained.
- `InboundMailSummaryController.kt`: real tag/processing ownership and session audit.
- `MailReadOnlyPolicy.kt`: precise completed handler activation.
- `ExpertScopedOperationsIT.kt`: real behavioral regressions, reusable by04b.
- `ExpertIndexControllerTest.kt`, `ExpertIndexControllerMvcTest.kt`, `ExpertManualProfileControllerTest.kt`, `InboundMailSummaryControllerTest.kt`: mandatory constructor/session/MVC wiring, original business/error tests retained.
- `AuthInterceptorTest.kt`: mandatory tag repository wiring, exact new handler set and retirement of obsolete blanket tag-denial pin.
- `OperatorStatusWriteSeamGuardTest.kt`: original noise-site line numbers only.

Plan SHA unchanged: YES. Worktree root/branch/git-dir unchanged: YES. Product commit is target HEAD and target-branch reachable: YES. Required child gates ran freshly after final source changes: YES. Source/test/index clean: YES. Only authorized control-plane dirty files remain. Historical evidence is baseline only: YES. No new plan amendment, schema, global permission, permissive shim, source security bypass, push, merge, rewrite or deployment.

Master final UI checks (including1440/390 browser acceptance), full-master all-test/migration suites and cross-phase human workflows are not claimed executed by this backend child; their approved final-stage ownership remains unchanged. The child-required original calendar/review regressions and bound JS suite were actually run as above. Human acceptance remains PENDING. No independent verify-p/review-p/repair-p was performed or PASS claimed.

Remaining current04a blocker: none. Next action: controller dispatches the independent light verifier for product `020bda37127f92187589e9d8f10e3bb3cbccb187`, then proceeds only under the master workflow.

## Controller terminal checkpoint — 2026-10-30T08:41:22.183Z
- Terminal independent verdict: LIGHT_PASS; exact report retained in verify-log.md.
- Final child code head: 020bda37127f92187589e9d8f10e3bb3cbccb187
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
