# Child 06 — execution

## Epoch 1 — PLAN_CONFLICT / consumer-scope prerequisite (2026-10-30)

Executor: ExpertAccess06Impl. Approval: current child06 invocation and brief epoch1 standing authorization. No product/test mutation, staging, commit, build, test, lint, formatter, or runtime smoke performed. All implementation remains pending; this is not READY_FOR_VERIFICATION or PASS.

### Identity and preserved state
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-06-pending-query.md`.
- Plan SHA-256: `3d6d492cdb0f2adeb3ba1cd1b52c5a118d9f85b623bd26fc06d3aa0120bc6ecf`; execution ID = canonical path + `@` + this SHA. NEW epoch1.
- `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md` exited 0 and returned the canonical path above, SHA above, size 13710.
- `git show b8625c35f461816397b34d167493d62205e5f1d3:docs/plans/2026-10-29/expert-access-06-pending-query.md | shasum -a 256` exited 0: `3d6d492cdb0f2adeb3ba1cd1b52c5a118d9f85b623bd26fc06d3aa0120bc6ecf  -`. Thus current plan bytes match the brief's approved commit identity, not its superseded opening hash.
- Worktree helper command: `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, exit 0.
- Target root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Before HEAD `12354c00e3808e766d270ddb7b24a162837e9103`; before product code SHA per brief `e4c5d83a580228a92f1dd2c427538829651a40a6`. No post-execution product commit. Initial `git status --short` exit 0 showed only controller-owned brief.md and ledger.md modified; preserved.
- Read complete current child plan, brief, master (including 301–452), and execute-p before source. No LSP tool is mounted; exact constructor/type/import/direct-method/lineguard searches were used instead.

### Proven prerequisite: bounded fixture authorization before product mutation

The authorized product change is feasible, but the exact twelve-file list excludes necessary existing consumers. Request controller-only plan/brief/ledger amendment adding these paths, without broadening product scope:

1. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` — migrate the real-JDBC owner fixtures and obsolete global-pending expectations, preserving session isolation, idempotency, validation, disabled-real-account visibility and zero-write assertions.
2. `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` — migrate Mockito invocation/matcher arity for explicit owner repository arguments; preserve account/query/paging behavior and add no alternate query seam.
3. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — line-number-only correction of the existing UnmatchedInboundMailController noise sites after required session plumbing, keeping exact path/context strings.

#### Exact source/consumer receipts

Search tool regex `MailboxConversationRepository\(|MailboxConversationService\(|MailboxSuspensionService\(|UnmatchedInboundMailService\(|pendingBadge\(|suspensionStatesByContacts\(|pendingReviewOnly|EXCLUDED_NOISE_SITES` over `src` found the production constructors, direct service constructors in OutboundAttachmentFlowTest:240, MailReviewQueryIT:71, MailboxReadAccessProjectionTest:78 and UnmatchedInboundMailServiceTest:33, and the guard below. No new constructor dependency is proposed; these constructors need no compatibility shim.

Search tool regex `findManualReviewQueue|countManualReviewQueue|findUnmatchedManualReviewQueue|countUnmatchedManualReviewQueue|countManualReviewByAccounts|countGroupedByReasonTypeForAccounts|listManualReviewQueue` over `src` returned:
- production repository declarations: InboundMailProcessingRepository:161,176,197,214,229,238;
- service caller: UnmatchedInboundMailService:31,51,53,56,62,71,72;
- controller caller: UnmatchedInboundMailController:103;
- repository fixture calls: InboundMailProcessingRepositoryTest:176,180,192,196,197,200,203,205;
- service fixture calls/stubs: UnmatchedInboundMailServiceTest:105,108,110,111,115,121,129,132,135,138,142,153,161,164,166,167,170.
The repository fixture uses real Flyway (@EnabledIfSystemProperty migrationIt at :22; datasource/Flyway setup :53–58), so its existing default-admin rows remain compatible with the expressly permitted internal default-admin calls. It needs fresh execution with migrationIt, not an invented replacement fixture.

Read exact service fixture snippets:
```kotlin
// UnmatchedInboundMailServiceTest:120–121
Mockito.verify(inboundMailProcessingRepository, Mockito.never())
    .findUnmatchedManualReviewQueue(Mockito.anyList(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())
// :152–153
Mockito.verify(inboundMailProcessingRepository, Mockito.never())
    .findManualReviewQueue(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())
```
Adding the required actor argument to these repository methods requires matcher migration: even a permitted Kotlin default `admin` becomes an additional raw invocation argument alongside the existing matchers. Leaving these calls unchanged would violate Mockito's all-arguments matcher contract. This is source-proven dependency, not an observed test failure; no test was run to reconfirm.

Search tool regex `listConversations\(|ConversationFilter\(|MailboxConversationController\(|MailboxSuspensionService|UnmatchedInboundMailController\(|MailboxConversationRepository|UnmatchedInboundMailService` over `src/test/kotlin` also found MailboxConversationControllerTest imports real AuthWebConfig/Repository/Service/SuspensionService (:124–135). The controller fixture is not a mock-only HTTP consumer.

Search tool regex `pending|suspension|op1|username|SESSION_USERNAME` over MailboxConversationControllerTest and exact ranged reads established:
```kotlin
// :703–709
get("/api/mail/mailbox/conversations?pendingOnly=true").session(sessionOf("op1"))
assertEquals(1, pending["total"].asInt())
assertEquals(3L, pending["items"][0]["contactId"].asLong())
// :1615–1622
get(badge).session(sessionOf("op1")) // expects manualReviewTotal 1
put("/api/mail/mailbox/conversations/1/suspension").session(sessionOf("op1"))
get(badge).session(sessionOf("op1")) // expects 0
get(badge).session(sessionOf("op2")) // expects 1 from the same pending row
// :1688–1694
get("/api/mail/mailbox/conversations/1/suspension").session(sessionOf("op2"))
assertEquals(1L, op2Body["suspensionPendingCount"].asLong())
// insertProcessing :2051–2055 / insertProcessingRow :2077–2081
INSERT INTO inbound_mail_processing
    (sender_account_code, uid_validity, imap_uid, message_id, from_email,
     subject, body, cleaned_body, received_at, process_status, process_reason,
     expert_contact_id)
VALUES ('acc-a', 1, ?, ?, 'expert@example.org', ?, 'body', 'cleaned', ?, ?, 'QA_AUTO_REPLIED', ?)
```
These insert seams omit pending_owner_username, hence use the inherited admin default. With required owner=session SQL, op1 cannot both own that row and expose it as op2 pending. This consumer must be migrated; retaining its global count expectations would contradict I-7. Preserve the test's stable session/validation/no-side-effect coverage rather than weakening production SQL.

Exact lineguard read (`OperatorStatusWriteSeamGuardTest:44–100`) yielded:
```kotlin
NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 233, "operatorStatus = request.operatorStatus"),
NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 1160, "operatorStatus = operatorStatus"),
```
Exact controller read :94–111 shows list currently ends `query: String?` and forwards `query = query` with no HttpServletRequest/session actor. The plan mandates explicit session plumbing. Its normal signature/forwarding additions precede both pinned lines; line padding or collapsed unrelated statements to preserve incidental line numbers is not a maintainable alternative. Existing guard self-check :183–190 enforces exact path/line/context.

Policy/guard audit: MailReadOnlyPolicy:56–88 has list/messages but no pendingBadge; ExpertMailPermissionGuard:39 explicitly defers personal pending/suspension to child06. Activation remains pending until owner SQL and FULL target validation land. Searches of auth tests found suspension denial at AuthInterceptorTest:476–480 and AuthFlowIntegrationTest:650; those READ-denial contracts remain valid and need no weakening.

### Commands and acceptance status
- Preflight identity/worktree/provenance/status commands above: exit 0.
- All required Maven/JS/SQL EXPLAIN/runtime commands: NOT_RUN. Target tests/skips: not measured; no PASS claim.
- No implementation/test files changed, no SMTP/ES/file service started, no temporary runtime scaffolds created.
- Downstream interfaces remain untouched: expertScope ALL/USER1_FULL/PENDING_REVIEW and optional admin-only filterCounts.user1FullExpertCount / pendingReviewDraftCount are still pending; pendingBadge remains separate mail units. Do not expose staged endpoints prematurely.
- Smallest next action: controller records bounded fixture scope amendment/new immutable identity, then redispatch execute-p. Current twelve-file identity is held read-only for product work. No unavailable-information or infrastructure blocker is claimed.

### Consolidated prerequisite addendum — exhaustive handler consumer

Controller requested full exact-handler-set audit before amendment. Search tool regex `allowed|Allowed|handler|Handler|expected|actual|setOf|pendingBadge|suspension` against `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` returned the exhaustive test at :504, real controller reflection loop :508–518, policy call :514, set accumulation :515 and equality assertion :522–595. Exact read :504–595 shows the current MailboxConversationController portion:
```kotlin
"MailboxConversationController#conversationManualRichReply:POST",
"MailboxConversationController#dismissReplied:PUT",
"MailboxConversationController#follow:PUT",
"MailboxConversationController#list:GET",
"MailboxConversationController#messages:GET",
"MailboxConversationController#setProgressStatus:PUT",
"MailboxConversationController#unfollow:DELETE",
```
The expected list is compared to `allowed.toList()` after scanning real @RestController declared methods and every mapped HTTP verb through MailReadOnlyPolicy. Child06's required exact activation adds pendingBadge GET, suspension GET, suspend PUT, updateSuspensionReason PATCH, resumeSuspension DELETE. Therefore a fourth necessary existing fixture path is:
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` — extend the exhaustive set with exactly these completed routes, while preserving wrong-method/default-deny/global/NONE/READ/cross-resource guarantees. Existing READ suspension rejection requests :476–480 stay forbidden through the FULL resource guard; do not delete them as obsolete.

Final consolidated amendment request is the three paths above **plus AuthInterceptorTest.kt**. No product scope/dependency addition is needed for filter counts: exact read MailboxConversationService:47–58 confirms its existing non-null repository dependency, and MailboxConversationRepository:26–28 already owns NamedParameterJdbcTemplate. Its :231–250 implements SQL-backed reviewPendingTotal and :808–813 reuses reviewPendingPredicate for SQL membership. Implement unfiltered admin filterCounts in that existing authorized repository seam, reusing reviewPendingPredicate/real-account scope rather than introducing another service dependency, constructor, nullable default, or second convention. Current constructors/import fixtures therefore require no new dependency wiring. The prior direct-constructor/Spring import receipts remain applicable.

Final identity/worktree helpers were freshly rerun before this addendum (both exit 0): unchanged plan SHA `3d6d492cdb0f2adeb3ba1cd1b52c5a118d9f85b623bd26fc06d3aa0120bc6ecf`, root/branch/Git directory as above, HEAD `12354c00e3808e766d270ddb7b24a162837e9103`. Product remains unmodified. Tests/runtime remain NOT_RUN.

## Epoch 2 — PLAN_CONFLICT / badge wire-name prerequisite (2026-10-30)

Executor: ExpertAccess06Resume. Approval: current invocation, epoch2 brief and standing authorization; exact approved plan commit `099ac62fc1cddae8d31ec5f0bee90f5c22acec95`. This is a NEW immutable-identity execution, not completion of epoch1. Partial authorized implementation/test edits exist; no gates or product commit yet. This is not READY_FOR_VERIFICATION or PASS.

### Identity, provenance and held state
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-06-pending-query.md`.
- Plan SHA-256: `02dab4d4130b08bcc723aecf61c252b008c3498609230aef0350c79d7b2d8433`; size 15414 bytes. Execution ID = canonical path + `@` + that SHA.
- Target root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Before/current HEAD `099ac62fc1cddae8d31ec5f0bee90f5c22acec95`; pre-execution product SHA `e4c5d83a580228a92f1dd2c427538829651a40a6`; post-execution product SHA N/A (uncommitted partial edits). No staging or commit.
- Read execute-p, complete brief, exact child and complete master before source; inspected predecessor owner interfaces and existing consumer receipts. No LSP mounted. Used exact constructor/direct-method/Spring-import/lineguard grep and ranged reads; reused existing JDBC/review predicates without new constructor dependencies.
- Initial `git status --short && git diff --stat` exited 0: only controller-owned brief.md, execution.md and ledger.md dirty. Preserved them; appended only this execution section.
- Last `git diff --stat` exited 0: 19 paths (16 authorized product/test paths plus the three pre-existing controller artifacts), +566/-71 before this appended section. Product/test work is partial and unverified: owner pending flags/SQL, expertScope/admin filterCounts, explicit session queue actor, owner suspension, exact route activation, and migrated/new fixtures. No other product/test paths edited.

### Proven prerequisite and controller adjudication
The current plan/master/downstream08 name the existing badge total `pendingBadge.total`. The real existing HTTP DTO and existing app consumer instead use **manualReviewTotal**. Adding `total` as an alias would violate the no-shim contract; a field cutover would require unlisted frontend/caller edits. Controller was notified before any such mutation.

Exact current DTO read, `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt:35-38`:
```kotlin
data class MailboxPendingBadge(
    val manualReviewTotal: Long,
    val countsByReasonType: Map<String, Long>
)
```
Exact existing consumer read, `src/main/resources/static/app.js:17169-17177`:
```javascript
let unmatchedBadgeRefreshSeq = 0;
async function refreshUnmatchedBadge() {
    const seq = ++unmatchedBadgeRefreshSeq;
    try {
        const data = await api("/api/mail/mailbox/conversations/pending-badge");
        if (seq !== unmatchedBadgeRefreshSeq) return;
        updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
    } catch (_) {
    }
```

Full grep-tool invocation:
```json
{"pattern":"pendingBadge|manualReviewTotal|pending-badge|pendingBadge\\.total","path":"/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main/resources/static/mailbox-chat.js;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main/resources/static/app.js;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-08-admin-controls.md;/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt","case":true,"gitignore":true,"skip":null}
```
Complete matching output (no mailbox-chat.js hits):
```text
src/main/resources/static/app.js#0A74
17172:    try {
17173:        const data = await api("/api/mail/mailbox/conversations/pending-badge");
17174:        if (seq !== unmatchedBadgeRefreshSeq) return;
17175:        updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
17176:    } catch (_) {
17177:    }
17178:}

src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt#A7CD
35:data class MailboxPendingBadge(
36:    val manualReviewTotal: Long,
37:    val countsByReasonType: Map<String, Long>
38:)
39:
121:    /** 导航角标统计邮件数，未关联邮件保留；不改变原待匹配队列的数据或分页。 */
122:    fun pendingBadge(username: String): MailboxPendingBadge {
123:        require(username.isNotBlank()) { "未登录" }
124:        val grouped = jdbcTemplate.query(
125:            """

docs/plans/2026-10-29/expert-access-08-admin-controls.md#0A99
23:### Invariant I-7: 计数按处理人、数量单位明确
24:- Rule: 我的待处理=MANUAL_REVIEW且pending_owner_username=session，排除SIMULATOR_NOOP/不存在账号，保留禁用真实邮箱；user1再受可读/FULL准入约束。图标角标复用当前账号pendingBadge.total（邮件条数，含既有挂起排除），不受列表搜索/分页影响；0隐藏，失败显示不可用而非0。待处理tab数字仍为专家数，待审核是SUBMITTED草稿数。count/page/explain共用条件，All保留历史邮件。监控、来信汇总不变。
25:- Applies to: 会话聚合/排序/分页、挂起、旧队列、顶栏及更多图标
26:- Violation consequence: 列表与角标不一致或user1邮件仍进admin待处理
27:- 来源: K-mailbox-inbound-source-authority
99:2. FILTER_FIELDS最前新增expertScope项，mount时只插入一个节点；原七个字段迁移/还原逻辑保留。ALL清除scope；USER1_FULL/PENDING_REVIEW映射06参数，应用后page=0；Reset将scope回ALL并按原规则保留tab/q；原旧reviewOnly状态与新scope只有一个authority（I-9/I-11、S-3）。
100:3. 更多图标新增S-3角标，数据复用当前session pendingBadge.total，由app的既有成功刷新路径通知当前host；首次mount主动读取，mark/reopen/挂起/撤权/刷新后重读；不要先开popover才取数。停止显示原筛选数badge，原filter-summary仍可读已选条件。总数与当前页无关；请求失败角标不可冒充0（I-7/I-9/I-10）。
101:4. 两个scope选项counts来自06.filterCounts；pending-review仍右侧同专家/同编辑器，不弹草稿列表。逐条待审标记、翻译小文字按钮、批准后确认规则沿原review05。不再生成mailboxReviewPendingBtn或刷新旁下拉（I-11/S-3）。
102:5. app/index移除旧顶部review按钮生成/绑定路径（若前置计划按本修正已不生成，则不造再删）；保留审核模块加载。更新旧按钮契约测试，不弱化成仅检索新文本；样式只新增S-2/S-3，既有mc-icon/mc-filter-count规则不改（I-9/S-2/S-3）。
103:
```

Controller decision received under standing authority: preserve authoritative `manualReviewTotal`, add no `total` alias and perform no DTO/frontend cutover for a planning typo. Mail count semantics remain own un-suspended mail rows; no final UI scope reduction. HOLD further product mutation while controller makes plan-only naming corrections across affected approved plans/brief/downstream identities. No product mutation occurred after that hold. The unfinished new InboundPendingOwnerIT badge JSON read currently says `"total"` and must be reconciled to the newly approved field in the next epoch; it has not been executed or represented as passing.

### Exact commands and execution status
- `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master` — exit 0 before edits; identity/root/branch/HEAD as above.
- `git status --short && git diff --stat` — exit 0 at preflight; preserved controller changes.
- `git diff --stat && python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master` — exit 0 at hold; unchanged plan SHA/root/branch/HEAD. Raw command output `artifact://2540`.
- All required Maven/JS/build/EXPLAIN/runtime commands: **NOT_RUN**. Counts/skips unmeasured. No independent verification or PASS.
- No disposable runtime services/scaffolds started; no production DB/ES/SMTP/files touched; nothing to clean.
- Downstream intended interfaces remain expertScope ALL/USER1_FULL/PENDING_REVIEW, optional admin-only filterCounts.user1FullExpertCount (experts) / pendingReviewDraftCount (drafts), and the existing pending-badge manualReviewTotal (mail), subject to controller's immutable naming amendment.
- Smallest next action: controller records naming-only plan/master/08/brief correction and redispatches with new identity. Reconcile existing sixteen-file partial diff, finish fixtures and all fresh required gates/runtime proof, then product-only commit `feat(fast-p): implement 06`. Do not replay prior child checkpoints or claim this partial epoch complete.

## Epoch 3 — PLAN_CONFLICT / review SQL-budget fixture prerequisite (2026-10-30)

Executor: ExpertAccess06ResumeE3. Approval: current invocation and epoch3 brief, approved plan commit `e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6`. NEW immutable-identity epoch; sixteen authorized partial product/test paths inherited from epoch2 and preserved, not discarded or treated as unrelated user changes. No staging or product commit. Not READY_FOR_VERIFICATION or PASS.

### Identity and partial implementation
- Canonical plan `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-06-pending-query.md`; SHA-256 `b6c165cfa080ee284ce4b04c1d8d5f7197c3630a44d01a6602274b1c48e05afc`; size 15755 bytes. Execution ID = canonical path + `@` + that SHA.
- Target root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`, common directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. Worktree ID = root + `@fast/expert-access-master@` + Git directory.
- Before HEAD `e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6`; before product SHA `e4c5d83a580228a92f1dd2c427538829651a40a6`; after product SHA N/A. Controller-owned brief/execution/ledger edits preserved. Read complete execute-p, latest brief, child, master and mail design; inspected actual authorized partial diff against product base and direct constructor/type/method consumers (no LSP mounted).
- Authorized reconciliation in InboundPendingOwnerIT: corrected badge JSON access to existing `manualReviewTotal`, supplied missing existing `document.service.ExpertMaterialService` import, strengthened FULL/admin suspension matrix to require 200, and used a real distinct ungranted contact for cross-resource 403 (nonexistent target correctly produced 404). Captured second row ID explicitly; no assumed generated-ID adjacency.
- Existing partial SQL/filterCounts/session/route changes remain inside the original sixteen-file authorization. No other product/test mutation.

### Complete prerequisite evidence — additional existing query-shape consumer

Required additional path: `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`. No mutation made to it.

Grep-tool invocation:
```json
{"pattern":"count|queries|select|assert|EnabledIf","path":"/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt","case":true,"gitignore":true,"skip":null}
```
Relevant exact output:
```text
30:@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
221:    fun `size100 uses constant batched queries and readers never query review summaries`() {
229:        assertEquals(100, result.items.size)
230:        assertEquals(100L, result.reviewPendingTotal)
231:        assertEquals(singleContactQueryCount, adminSql.size, "Page growth must not introduce per-contact queries")
232:        assertEquals(2, adminSql.count { it.contains("mailbox_review_draft") })
233:        assertTrue(adminSql.none { it.contains("d.html_body") || it.contains("d.text_body") })
237:        assertEquals(2L, reader.total)
238:        assertEquals(0L, reader.reviewPendingTotal)
239:        assertTrue(reader.items.all { it.reviewPendingCount == 0L })
240:        assertTrue(statements.none { it.contains("mailbox_review_draft") })
242:        assertThrows(MailReadAccessDeniedException::class.java) { list(user = "user1") }
243:        assertTrue(statements.isEmpty())
```
Exact ranged read `MailReviewQueryIT.kt:221-243` confirms this is actual execution through the real JDBC repository/service with a statement-recording DataSource, not a string-only assertion.

Current authorized call chain from exact reads: `MailboxConversationService.kt:138-142` computes admin-only `repository.filterCounts(activeCodes)` before filtered/empty-page branches; `MailboxConversationRepository.kt:255-269` computes the writable-expert count and reuses `reviewPendingTotal("admin", ConversationFilter(allRealAccountCodes))` for unfiltered draft units. Existing `MailboxConversationService.kt:171` still computes the original filter-dependent `reviewPendingTotal`, and `:190-191` computes batched visible-page review counts. The new global toolbar statistic is not the filtered legacy total or page projection: q/account/direction/date/ordinary filters/page can make the latter zero while toolbar counts remain nonzero, as the authorized controller fixture asserts at :1026-1033. The SQL counter therefore sees an additional bounded review-table statement, making the old exact-two pin incidental and incompatible with the new required statistic. No product behavior should be removed or fake-counted to keep that pin; no new dependency, duplicate same-stat query, or replacement exact-three pin is requested.

Consolidating grep over `src/test/kotlin/com/weibo/talentintroduction/mail` and `.../auth` with pattern `mailbox_review_draft|reviewPendingTotal|filterCounts|sqlCount|statements\.size|adminSql|queryCount` returned the controller fixture, MailReviewQueryIT, repository IT, and review workflow fixtures. The exact review-SQL query-count pin above is the newly identified incompatible consumer; workflow/state/count assertions remain unchanged.

Controller decision under standing authorization: add only this #17 fixture to a plan-only amendment/new epoch4; retire the exact-two incidental pin, preserve constant page-size batching, no body SQL and reader-zero-review-SQL assertions, do not re-pin three. HOLD further source mutation pending the new immutable identity. This is implementation-stage scope reconciliation, not an independent-verifier repair round.

### Exact command history and current evidence

All commands executed in the target root.
- `python /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py ... && ...` — exit 127 (`python` unavailable); no mutation. Retried with installed `python3`.
- `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master && git status --short` — exit 0; identity above, staged0/unstaged19 (sixteen inherited product/test paths plus controller artifacts), artifact `2545`.
- `git diff e4c5d83a580228a92f1dd2c427538829651a40a6 -- src/main/kotlin/com/weibo/talentintroduction/mail src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt src/test/kotlin/com/weibo/talentintroduction/mail src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — exit 0, initial reconciliation only; sixteen product/test paths, artifact `2549`.
- Created owned disposable MySQL container with `docker -H unix:///Users/lukai/.orbstack/run/docker.sock run --detach --name expert-access-06-e3-mysql -e MYSQL_ROOT_PASSWORD=child06 -e MYSQL_DATABASE=talent_introduction -p 127.0.0.1:33416:3306 mysql:8.0 --default-authentication-plugin=mysql_native_password` — exit 0.
- Exact owner gate command (executed three times, with source corrections between attempts):
```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33416/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child06 ES_BASE_URL=http://127.0.0.1:33916 mvn test -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,InboundPendingOwnerIT -DmysqlIt=true -DargLine=-Dapi.version=1.41
```
- Attempt1 `bg_279`, artifact `2553`: exit1 at test compilation, missing ExpertMaterialService import; no tests run. Fixed only authorized erroring fixture import.
- Attempt2 `bg_280`, artifact `2556` (full output `2554`): exit1, 60 tests / 1 failure / 0 errors / 0 skipped. Failure is fixture's nonexistent-target 403 assumption (actual stable404), not a production permission bypass. Fixed fixture to use a persisted different ungranted expert, retaining exact403 cross-resource contract.
- Attempt3 `bg_281`: in flight when this prerequisite was recorded; append actual result before handing off. No claim of passing it.
- Remaining mandatory consumer/monitoring/auth/review/migration/full Maven/JS/runtime commands: NOT_RUN at this hold. No real application/login/JDBC runtime smoke yet; no browser acceptance claim (06 permits equivalent API actions for future UI).
- Prepared owned loopback ES observer on33916 and SMTP connection observer on33917 in Eval; no application launched and no send performed. Initial attempt to import Python smtpd failed (module unavailable); retained already-started ES observer and implemented a connection/command observation server, not an SMTP-delivery proof. Owned `/tmp/expert-access-06-epoch3-runtime/attachments` created; no production service touched. Cleanup/handoff must be recorded.
- No staging, product commit, push, merge, reset, deployment or independent review. Required final commands must run fresh under the new identity after the final authorized source state; earlier partial/failed runs cannot satisfy READY_FOR_VERIFICATION.

### Epoch3 in-flight result and final hold receipt
- Attempt3 `bg_281`, full output `artifact://2558`, failure receipt `artifact://2560`: exit1, 60 tests / 1 failure / 0 errors / 0 skipped. The strengthened NONE/READ/FULL/real-cross-expert route matrix completed; failure occurred afterward in new InboundPendingOwnerIT:158-159, which expected403 for POST to a suspension resource with no POST handler and observed500. The actual controller declares only GET/PUT/DELETE on suspension and PATCH on suspension/reason. [INFERENCE] This exercises inherited unmapped-method/global-error behavior, not a rejected HandlerMethod's security gate. No production error-handler repair is authorized or proposed. Epoch4 should use a real existing unlisted handler for default-deny proof and retain AuthInterceptorTest's exact method/wrong-verb matrix; do not re-pin the unmapped500 as a security success. No source change was made after HOLD.
- Fresh identity/worktree recheck `bg_282`, `artifact://2559`, command identical to the preflight helper pair without status: exit0; plan SHA remains `b6c165cfa080ee284ce4b04c1d8d5f7197c3630a44d01a6602274b1c48e05afc`, target root/branch/Git directory unchanged, HEAD `e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6`.
- There is no successful final owner gate or READY_FOR_VERIFICATION claim in epoch3. The next authorized immutable epoch must finish the remaining fixture and rerun all required gates/runtime freshly.
- Owned-resource cleanup completed before handoff: Eval closed ES33916 and SMTP33917 observers (recorded events `[]`) and removed the empty `/tmp/expert-access-06-epoch3-runtime/attachments` and parent directory. `docker -H unix:///Users/lukai/.orbstack/run/docker.sock rm --force expert-access-06-e3-mysql` exited0 and removed only this epoch's disposable container. No runtime process or temporary script remains owned by this epoch. Existing unrelated containers/services were untouched.
- Controller adjudication received after cleanup: the unmapped POST/global method-error→500 is existing RECORD_ONLY outside feature (prior02 O-1/03 observations). Epoch4 must retire only the erroneous unmapped-method assertion; do not alter global error mapping or register an unrelated controller merely to replace it. Preserve the real NONE/READ/FULL/cross-resource matrix and existing pure-policy wrong-verb/default-deny tests. Real production runtime may exercise an already-mapped forbidden global handler. This supersedes the fixture-registration suggestion above without reducing the security acceptance.

## Epoch 4 — PLAN_CONFLICT / inherited monitoring fixture prerequisite (2026-10-30)

Executor: ExpertAccess06ResumeE4. Approval: current invocation, epoch4 brief, standing authorization and approved plan commit `477ec60cfce0d60589bde5831053a75af98af9f5`. NEW immutable-identity epoch. Preserved/reconciled the sixteen authorized partial product/test paths from epochs2/3; no completed-child replay, no staging or product commit. Not READY_FOR_VERIFICATION or PASS.

### Identity and implementation state
- Canonical plan `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-06-pending-query.md`; SHA-256 `94c6608a738f629b27df3b6879d056a5c3aa0f2ceb7ae4185b2d3001f4d31466`, size16863. Execution ID = canonical path + `@` + this SHA.
- Worktree root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`; Worktree ID = root + `@fast/expert-access-master@` + Git directory.
- Before HEAD `477ec60cfce0d60589bde5831053a75af98af9f5`; pre-execution product SHA `e4c5d83a580228a92f1dd2c427538829651a40a6`; post-execution product SHA N/A. Preflight helper pair and `git status --short && git diff --stat` exited0 (`artifact://2567`), staged0/unstaged19. Controller-owned brief/execution/ledger preserved.
- Read execute-p, complete current brief/child/master, predecessor execution/interfaces and mail design before source. No LSP mounted; exact method/type/constructor/import/lineguard receipts from prior audits were reconciled against actual partial diff (`artifact://2569`) and current fixture reads/searches.
- Only epoch4 source changes: removed MailReviewQueryIT's exact-two review-SQL pin (not replaced with a three pin; constant page batching/no-body/reader-zero remain); removed the erroneous new InboundPendingOwnerIT unmapped-POST403 assertion. All seventeen authorized product/test paths now contain pending child06 changes. Other partial SQL/session/scope/owner/guard/fixture changes were retained.

### Fresh required commands and observed results

Every repository command used the exact target root. Common Maven environment below is literal:
```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33416/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child06 ES_BASE_URL=http://127.0.0.1:33916
```
- Disposable DB creation: `docker -H unix:///Users/lukai/.orbstack/run/docker.sock run --detach --name expert-access-06-e4-mysql -e MYSQL_ROOT_PASSWORD=child06 -e MYSQL_DATABASE=talent_introduction -p 127.0.0.1:33416:3306 mysql:8.0 --default-authentication-plugin=mysql_native_password` — exit0, owned container `a21daaf137914886f6cb261a4c16c18f8ad6345c176eafda343bd48bfbb7e6bc`.
- Common env + `mvn test -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,InboundPendingOwnerIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` — exit0, 60 tests/0fail/0error/0skip (41 repository,11 suspension,8 owner); `bg_283`, `artifact://2574`. Bundled JS1961/1961 pass,0fail/0skip and both configured JS syntax checks succeeded.
- Common env + `mvn test -Dtest=MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,UnmatchedInboundMailServiceTest,OperatorStatusWriteSeamGuardTest,AuthInterceptorTest,InboundMailProcessingRepositoryTest,MailReviewQueryIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` — exit0,101 tests/0fail/0error/0skip; `bg_284`, `artifact://2577`. Counts respectively46,6,15,1,19,9,5; bundled JS1961pass/0fail/0skip plus syntax checks succeeded. Observed `REVIEW_QUERY_SIZE100 rows=100 sqlCount=13 reviewSqlCount=3` is evidence, not a replacement exact-count assertion.
- Common env + `mvn test -Dtest=MailRecordRepositoryMonitoringIT,MailMonitoringServiceTest,MailMonitoringControllerPostmasterTest,InboundMailSummaryControllerTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` — **exit1**,220 tests/0fail/5errors/0skip; `bg_285`, full saved output `artifact://2578`, final report `artifact://2579` (very large output middle truncated by capture; permanent surefire reports retained). Monitoring repository15tests/5errors; other suites10,5,2,102,21,9,10,46 all0fail/0error/0skip. JS exec phase did not run in this failed command. No rerun solely to reconfirm this failure.
- Real owner EXPLAIN in `artifact://2574:757-759`: admin/user1 count/page fixtures both ran; direct owner predicate `possible_keys=idx_inbound_mail_processing_status,fk_inbound_mail_processing_contact,idx_inbound_pending_owner`, selected `key=idx_inbound_pending_owner`, `type=ref`, `ref=const,const,const`, `rows=1`, `Extra=Using index`. Combined-page plan uses stable SQL predicates, fixtures assert page IDs3,2,1 and owner counts, disabled real accounts/simulator/suspension/provided/READ semantics.

### Proven bounded prerequisite — unchanged monitoring test collation matrix

Additional necessary path: `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt`. **No mutation made to this unlisted file or to MailRecordRepository.kt.**

Failure receipt `target/surefire-reports/com.weibo.talentintroduction.monitoring.repository.MailRecordRepositoryMonitoringIT.txt:4-5,74`:
```text
Tests run: 15, Failures: 0, Errors: 5, Skipped: 0
listMailbox works when outbound and inbound text columns have different collations
SQL state [HY000]; error code [1267]; Illegal mix of collations
(utf8mb4_bin,NONE) and (utf8mb4_0900_ai_ci,COERCIBLE) for operation 'like'
```
Other failing methods: pending expert pagination (:272), expert filter counts (:433), expert aggregation (:222), pending expert filters (:353); all same1267, not owner SQL. The first test's DDL persists across transactional test rollback.

Exact own-container setup (fixture:40-44,63-67):
```kotlin
private val mysql = KotlinMySqlContainer("mysql:8.0.36")
    .withDatabaseName("talent_introduction")
    .withUsername("test")
    .withPassword("test")
registry.add("spring.datasource.url", mysql::getJdbcUrl)
registry.add("spring.datasource.username", mysql::getUsername)
registry.add("spring.datasource.password", mysql::getPassword)
```
Thus external disposable datasource settings do not correct this fixture's schema defaults. It creates a fresh MySQL8.0.36 default0900 schema. Migration V6:1-2 says `ALTER TABLE mail_record ADD COLUMN cleaned_body LONGTEXT AFTER body;`; V10:108-110 adds `inbound_mail_processing.cleaned_body TEXT DEFAULT NULL AFTER body`, neither specifies collation.

Exact intentional cross-source fixture (:144-147):
```kotlin
jdbcTemplate.execute("ALTER TABLE mail_record MODIFY subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
jdbcTemplate.execute("ALTER TABLE mail_record MODIFY body LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
```
`cleaned_body` remains at inherited schema collation, unlike its corresponding body. Existing MailRecordRepository projections normalize UNION columns (`:508,:538`), but its original WHERE predicates (`:524,:557` and expert-page/count equivalents `:652,:676`) evaluate `COALESCE(cleaned_body,body) LIKE CONCAT('%', :keyword, '%')` first, including when keyword is null. Mixed implicit collations produce derivationNONE, so changing only a connection collation is not a genuine correction.

Diagnostic executed only in owned disposable runtime schema, not by rerunning the reported failing test:
```sql
CREATE TEMPORARY TABLE collation_diag (
  cleaned_body TEXT COLLATE utf8mb4_0900_ai_ci,
  body TEXT COLLATE utf8mb4_general_ci);
INSERT INTO collation_diag VALUES(NULL,'body');
SELECT COLLATION(COALESCE(cleaned_body,body)),
       COERCIBILITY(COALESCE(cleaned_body,body)) FROM collation_diag;
-- observed utf8mb4_bin, 1
SET NAMES utf8mb4 COLLATE utf8mb4_bin;
SELECT COLLATION(CONCAT('%', NULL, '%')),
       COALESCE(cleaned_body,body) LIKE CONCAT('%', NULL, '%') FROM collation_diag;
-- observed ERROR1267, (utf8mb4_bin,NONE) vs (utf8mb4_bin,COERCIBLE).
```
No session/schema/source/config workaround was applied to the required test. Diagnostic runtime DB was deliberately created unicode_ci and production migrations gave both body/cleaned_body unicode_ci there; server was8.0.46/default0900, not the fixture's separate8.0.36. This is an explicitly distinguished diagnostic, not fabricated observation of the stopped Testcontainer's live variables.

Exact unchanged-byte provenance command, exit0:
```sh
git hash-object src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt
git rev-parse e4c5d83a580228a92f1dd2c427538829651a40a6:src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt e4c5d83a580228a92f1dd2c427538829651a40a6:src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt
# each returned identical pair:
# 6e6ad47150a07a76a8723f92122b5902cfc1d468
# 7ce411573b55e5b92dda398c47815f3c56f90379
```
Controller adjudication received: smallest bounded #18 test-fixture-only amendment is justified. Align mail_record.cleaned_body with existing outbound unicode_ci body and inbound_mail_processing.cleaned_body with existing inbound general_ci body in that intentional cross-source fixture; preserve all assertions/dataset and outbound-vs-inbound differences. No production query/migration repair, suppression, skips or broad monitoring hygiene. Hold further product/test mutation for plan-only commit/new epoch5. This report preserves the failed220/5error run; no gatingPASS.

### Reachable runtime (in flight at prerequisite append)
- Fresh `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-06-epoch4-runtime/classpath.txt` exited0. Launched real `TalentIntroductionApplicationKt` from dependency classpath + target/classes only, never expanded WAR or target/test-classes; service `expert-access-06-e4-app`, PID59291, ready on127.0.0.1:33816 (`artifact://2581`).
- Actual production AuthWebConfig/API/JDBC uses separate owned `expert_access_06_runtime` DB on33416, authenticates real admin/user1 login cookies and real admin password change. AUTH_ENABLED=true; queue/scheduling/discovery disabled, ES observer33916 and SMTP connection observer33917 loopback only; attachment root `/tmp/expert-access-06-epoch4-runtime/attachments`.
- Runtime SQL fixtures provide deterministic two-owner mail data (not a claim of runtime IMAP ingestion; both actual production new sinks are exercised by fresh InboundPendingOwnerIT). Actual API grant preserved historicaladmin3; user1 rows3 in disabled real account. Both badges3, pending expert totalsadmin2/user1=1, adminAlpha All timeline5. Unfiltered toolbar1FULLexpert/5SUBMITTEDdrafts survives q/account/direction/keyword/page-empty combination. Scope ALL/USER1_FULL/PENDING_REVIEW real totals2/1/2; user1 filterCounts absent.
- Actual READ/NONE/cross-resource four-verb suspension requests403; admin-only scope/read-access management and existing mapped `/api/mail/unmatched-inbound` handler denied403 for user1. Twelve403 requests had unchanged mail/suspension/processing DB snapshot, zero incremental ES/SMTP observations and no attachment files. Invalid admin enum400. No erroneous unmapped-method probe.
- Personal suspension succeedsFULL, hides only user1 badge3→0 and pendingexperts1→0, retains adminbadge3; suspended-only1, PATCHreason and DELETEresume succeed; resumedbadge3. GrantFULL to Beta with onlyadminpending leads genuine409 on user1 suspend. Admin legacy queue ignores forged actor/username and remains3 personal mails. More runtime/cleanup/final identity evidence follows in an append-only addendum.

### Epoch4 terminal runtime, cleanup and immutable hold receipt

- Actual production runtime completed **67 HTTP requests** using real cookie logins, AuthWebConfig, controllers/services/transactions and JDBC. Captured terminal Eval receipt includes every method/path/status, final DB truth and exact runtime environment; no synthetic screenshot, mock runtime service bean or test-classes contamination. Successful read paths made20 loopback ES observer calls; the denied matrix had0 incremental ES calls. SMTP connection/command observations remained`[]` throughout; attachment directory remained empty. This proves no outbound SMTP connection/file creation and zero returned file bytes in these exercised routes, not uninstrumented claims about every OS file-read syscall.
- Completion lifecycle: user1 POST`/api/mail/unmatched-inbound/9606/mark-resolved`200 ignored forged bodyadmin and returned `resolvedBy=user1`; user1badge3→2/admin3. Expert attention remainedtrue because other pending existed. Actual admin PUTgradeREAD200 reclaimed the two unfinished user1 items: user1badge0/admin5, personalpendingexperts0, READ timeline still5messages. Completed row retained user1 until actual admin POSTcancel-resolved200, then becameadmin and adminbadge6; user1 cancel whileREAD403. Actual regrantFULL200 left all sixadmin items unchanged, user1badge0. Global `/api/inbound-summary/mails?from=2026-10-29T00:00:00&to=2026-10-30T00:00:00` returned6 both before/after completion/downgrade/reopen; submitted draft count remained5. No SMTP.
- Actual concurrent HTTP lifecycle: inserted one deterministic new user1-owned fixture row afterFULL (not runtime IMAP claim), synchronized two client threads via barrier, then admin PUTgradeREAD raced user1 PUTsuspension. Observed grade200/suspend409 (current owner count rechecked after contact lock), final MANUAL_REVIEW owner user1 count0, adminbadge7/user1badge0 and user1pendingexperts0; subsequent READ suspensionGET403. No retry or source workaround. Permanent InboundPendingOwnerIT additionally proves both true creation sinks, contact-lock interleavings, narrow updates/reopen and rollback under the fresh60-test gate.
- Final runtime DB receipt: `SELECT pending_owner_username,COUNT(*) ... WHERE process_status='MANUAL_REVIEW' GROUP BY pending_owner_username` returned`admin 7`; SUBMITTEDdrafts5; mail_record count0. Fixture data is disposable and no production service was used.
- Owned-resource cleanup completed: `proc://expert-access-06-e4-app/kill` stopped only owned PID59291 service; Eval shut down/closed ES33916 and SMTP33917 observers, removed empty attachment directory, generated classpath.txt and `/tmp/expert-access-06-epoch4-runtime`. `docker -H unix:///Users/lukai/.orbstack/run/docker.sock rm --force expert-access-06-e4-mysql` exited0 and removed only this epoch's disposable database container. No remaining owned service/scaffold.
- Final combined command `docker -H unix:///Users/lukai/.orbstack/run/docker.sock rm --force expert-access-06-e4-mysql && python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-06-pending-query.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master && git diff --check` exited0 (`artifact://2593`): unchanged SHA`94c6608a738f629b27df3b6879d056a5c3aa0f2ceb7ae4185b2d3001f4d31466`, root/branch/Git directories and HEAD`477ec60cfce0d60589bde5831053a75af98af9f5`; diff whitespace check clean.

| Current child requirement | State at hold | Evidence |
|---|---|---|
| owner pending flag/count/page/explain, All timeline | IMPLEMENTED/uncommitted | real41repository tests; owner index/page EXPLAIN; real runtime pages/timeline |
| owner suspension/badge/legacy queue and currentFULL | IMPLEMENTED/uncommitted | real11suspension+8owner tests;67request production runtime |
| strict admin expertScope and SQL toolbar expert/draft units | IMPLEMENTED/uncommitted | repository+46controller tests; real unfiltered empty-page/scope requests |
| exact five staged handlers + NONE/READ/cross-resource/admin/zeroeffects | IMPLEMENTED/uncommitted |19auth tests, real AuthWebConfig owner matrix and runtime12denials; default-deny mappedhandler |
| oldconsumer/lineguard/review query-shape regressions | IMPLEMENTED/uncommitted |101-test gate0skip; no replacement query-count pin |
| mandatory monitoring/global regression command exit0 | BLOCKED | unchanged monitoring fixture SQL1267,220/5errors/0skip; bounded #18 prerequisite above |
| product-only commit and final READY_FOR_VERIFICATION | BLOCKED | no staging/commit before missing scope authority/newidentity; failed command not waived |

- Limits: browser1440/390 and future UI controls are NOT_RUN in child06; approved child plan explicitly permits equivalent same-session API actions until07/08 final UI. No IMAP or actual SMTP send was claimed by runtime; permanent tests execute both actual sinks and original automated consumers. Whole-master unrestricted suites/final independent review are controller-owned, not claimed as complete here. No independent PASS or verify-p/review-p/repair-p was run.
- Downstream interface remains existing `pendingBadge.manualReviewTotal` mail units; list `expertScope=ALL|USER1_FULL|PENDING_REVIEW`; admin-only optional `filterCounts.user1FullExpertCount` expert units and `pendingReviewDraftCount` draft units on empty/nonempty pages. No alias, nullable/default security dependency, prefixallow or new storage field.
- **Terminal outcome: PLAN_CONFLICT.** Proven smallest missing authority is controller plan-only amendment adding the single monitoring test fixture for the two corresponding cleaned_body ALTERs, followed by immutable epoch5 redispatch. Preserve17partial product/test paths and all failed/fresh receipts; no staging/product commit yet. Fresh required owner/consumer commands and runtime succeeded under epoch4, but the failed monitoring gate forbids READY_FOR_VERIFICATION. Next epoch must execute all current required commands freshly after its final source state and actual runtime, then product-only`feat(fast-p): implement 06` and evidence handoff.

## Epoch 5 — READY_FOR_VERIFICATION (2026-10-30)

Executor `ExpertAccess06ResumeE5`; NEW immutable identity epoch, current invocation and epoch5 brief/standing human authorization. This is execution evidence, **not independent PASS**.

### Identity, provenance and completed scope
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-06-pending-query.md`; SHA256 `cb29ebd9ff569ba251108c38b36de95a38dec35c118cafd2c16e8719dd56631d`,17969bytes. EXECUTION_ID is canonical path@that SHA. Approved identity commit `8284ae04acaed6f7222774c8a4c6ac522f6690f0`.
- Target root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`; git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. WORKTREE_ID=root@branch@git-dir.
- Before HEAD `8284ae04acaed6f7222774c8a4c6ac522f6690f0`; pre-execution product SHA `e4c5d83a580228a92f1dd2c427538829651a40a6`. Post-execution product SHA / evidence HEAD `b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`, subject `feat(fast-p): implement 06`. Boundary base..post includes all child06 product changes; no evidence files in product commit.
- Read execute-p, complete current child/master/brief, predecessor05 downstream evidence and06 epoch4 evidence, mail design and actual inherited diff. Preserved/reconciled seventeen authorized dirty product/test paths, without replaying completed children. No LSP available; prior constructor/import/lineguard receipts plus current exact queue consumer grep and implementation reads establish direct consumers. Current grep pattern `findManualReviewQueue\(|countManualReviewQueue\(|findUnmatchedManualReviewQueue\(|countUnmatchedManualReviewQueue\(|countManualReviewByAccounts\(|countGroupedByReasonTypeForAccounts\(` over target `src` shows repository declarations, UnmatchedInboundMailService calls and repository/service fixtures; all service query calls supply actor, old internal defaults remain admin as explicitly approved.
- Epoch5's additional mutation is exactly two `cleaned_body` ALTER statements beside existing body ALTERs in `MailRecordRepositoryMonitoringIT`: outbound LONGTEXT unicode_ci; inbound TEXT general_ci. All assertions/dataset and intentional cross-source collation difference preserved. No production MailRecordRepository, migration, connection workaround or configuration changes.
- Product commit contains exactly plan-authorized18 paths,388insertions/68deletions. Controller-owned brief/ledger/prior reports preserved; only this execution append additionally written.

| Requirement | State | Current evidence |
|---|---|---|
| owner flag/count/page/explain, complete All timeline | IMPLEMENTED | fresh41 repository tests, owner EXPLAIN, real pages/timeline |
| personal badge/suspension/legacy list/count/reason totals | IMPLEMENTED | fresh11 suspension+8 owner tests, authenticated runtime lifecycle |
| strict admin expertScope + SQL unfiltered expert/draft counts | IMPLEMENTED | repository/controller/review SQL behavior, empty-page runtime |
| five exact owner routes, READ/NONE/cross-resource/admin safety | IMPLEMENTED | real AuthWebConfig IT,19auth tests,12runtime403 zeroeffects |
| narrow updates/reopen/current-owner and expert attention | IMPLEMENTED/inherited05 preserved | fresh owner races/rollback tests and real grade/completion/reopen race |
| original monitoring/summary/automation/auth/migration regressions | IMPLEMENTED | fresh220 tests,0fail/0error/0skip including15monitoring |
| required product commit/evidence | IMPLEMENTED | exact18-path product commit above, append-only current report |

### Fresh commands and exit/count evidence

All shell repository commands used exact target root. Common literal gate prefix:
```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33416/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child06 ES_BASE_URL=http://127.0.0.1:33916
```
Sequential commands after final source mutation:
1. Prefix + `mvn test -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,InboundPendingOwnerIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` — exit0,60tests/0fail/0error/0skip, suites41/11/8. `bg_286`, `artifact://2609`; bundled JS1961pass/0fail/0skip and both configured syntax checks succeeded.
2. Prefix + `mvn test -Dtest=MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,UnmatchedInboundMailServiceTest,OperatorStatusWriteSeamGuardTest,AuthInterceptorTest,InboundMailProcessingRepositoryTest,MailReviewQueryIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` — exit0,101tests/0fail/0error/0skip; suites46/6/15/1/19/9/5. `bg_287`, `artifact://2611`; JS1961pass/0fail/0skip and syntax checks succeeded. Large-page review SQL count3 is observed evidence only, no replacement exact-count pin.
3. Prefix + `mvn test -Dtest=MailRecordRepositoryMonitoringIT,MailMonitoringServiceTest,MailMonitoringControllerPostmasterTest,InboundMailSummaryControllerTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` — exit0,220tests/0fail/0error/0skip; suites15/10/5/2/102/21/9/10/46 confirmed from fresh surefire reports. `bg_288`, `artifact://2612`/`artifact://2613`; very large captured output middle partially truncated, permanent surefire counts individually read. JS1961pass/0fail/0skip and syntax checks succeeded. Prior epoch4 failure retained above, not erased or recounted as pass.
4. `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-06-epoch5-runtime/classpath.txt` — exit0 (`artifact://2614`), fresh dependency classpath for runtime.
- Real `artifact://2609:757-759` EXPLAIN ran admin and user1 combined plans; direct owner predicate selected `idx_inbound_pending_owner`,type=ref,ref=const,const,const,rows1,Using index. Count/page owner IDs, same-date stable sorting and simulator/disabled/suspension/provided semantics covered by actual SQL.
- Preflight helper first used unavailable `python` and exited127; corrected once to installed `python3`, both identity helpers exit0. No test failure this epoch.

### Fresh actual changed-path runtime
- Created owned disposable container with `docker -H unix:///Users/lukai/.orbstack/run/docker.sock run --detach --name expert-access-06-e5-mysql -e MYSQL_ROOT_PASSWORD=child06 -e MYSQL_DATABASE=talent_introduction -p 127.0.0.1:33416:3306 mysql:8.0 --default-authentication-plugin=mysql_native_password` — exit0, id `d8e3d76338a890ef91642f6efc9b906cf3b79690397ad549462ac007ac12d4ed`. Monitoring IT independently creates its own mysql8.0.36 fixture; no external JDBC override assumed.
- Recovered predecessor runtime procedure from its persisted tool-call source and executed freshly against epoch5 DB/process. Real production `TalentIntroductionApplicationKt`, dependency cp+target/classes only, no testclasses/WAR; owned service `expert-access-06-e5-app`,PID82994,ready127.0.0.1:33816 (`artifact://2615`). Real AuthWebConfig/controllers/services/transactions/JDBC, actual cookie admin/user1 login and admin password change.
- Separate disposable `expert_access_06_runtime` database unicode_ci, JDBC33416/serverTimezone=Asia/Shanghai. AUTH_ENABLED=true; MAIL_QUEUE_ENABLED=false,MAIL_SCHEDULING_ENABLED=false,EXPERT_DISCOVERY_ENABLED=false,EXPERT_DISCOVERY_CRON=-,EXPERT_DISCOVERY_PIPELINE_ENABLED=false; ES_BASE_URL=http://127.0.0.1:33916; attachment root `/tmp/expert-access-06-epoch5-runtime/attachments`. Loopback ES observer33916/SMTP connection observer33917 only. No production external access.
- All67actual HTTP requests/statuses and final SQL emitted by fresh Eval receipt. Initial oldadmin3 remains afterFULL; deterministic user1fixture3 in disabled real account yields badgesadmin3/user3 and pendingexpertsadmin2/user1. Admin Alpha timeline5. ALL/USER1_FULL/PENDING_REVIEW totals2/1/2; empty filtered page retains toolbar1FULLexpert/5SUBMITTEDdrafts; user1filterCounts absent. Fixtures do not claim runtime IMAP ingestion; permanent owner tests execute both true creation sinks/de-duplication.
- Twelve403 requests for READ/NONE cross-resource four suspension verbs, restricted scopes, mapped unmatched endpoint and grant endpoint left DB snapshot identical,0incremental ES/SMTP observations and empty attachment directory. Invalid enum400. Forged actor/username queue query stilladmin3. No unsupported-method403 assumption or global error repair.
- FULL personal PUTsuspension200: userbadge3→0, pendingexperts1→0, adminbadge3unchanged; suspendedOnly1; PATCHreason/DELETEresume200 restores3. FULL on Beta with onlyadminpending gives409. Completion ignores bodyadmin and persists resolvedBy=user1; userbadge3→2, global attentiontrue. GradeREAD reclaims two unfinished rows (admin5/user0), READtimeline5; adminreopen adds1(admin6), READreopen403. RegrantFULL leaves existingadmin ownership unchanged. Global inbound summary6 before/after, submitteddrafts5.
- Barrier-synchronized actual HTTP gradeREAD vs usersuspend observed200/409, finaladminpending7/user0 and READsuspensionGET403. Final SQL: MANUAL_REVIEW owners `admin 7`; SUBMITTEDdrafts5; mail_record0. ES observer20calls total, deniedmatrix0incremental; SMTPevents[]; attachmentfiles[]. This proves exercised connection/response/filecreation boundaries, not every uninstrumented OS read syscall. No synthetic screenshots or SMTPsend claim.
- Cleanup: stopped owned service PID82994 with proc kill; shut down/closed both loopback observers; removed empty attachment directory/classpath/runtime temp directory. `docker ... rm --force expert-access-06-e5-mysql` exit0 removed owned DB only. No owned scaffolds/services remain.

### Commit, freshness, downstream and limits
- Before staging and commit, worktree helper ran with exact `--expect-root`, `--expect-branch`, `--expect-git-dir`, all matched. `git add --` exact18 plan table paths only, exit0. `git commit -m 'feat(fast-p): implement 06'` exit0; `git rev-parse HEAD` returned product SHA above; `git merge-base --is-ancestor HEAD fast/expert-access-master` exit0 (`artifact://2622`).
- Final precommit identity recheck unchangedSHA, correctroot/branch/HEAD and `git diff --check` exit0 (`artifact://2619`). Required commands all fresh after final source state; historical results baseline only. No independent review/verify/repair skill invoked.
- Downstream binding: `expertScope=ALL|USER1_FULL|PENDING_REVIEW`; admin optional `filterCounts.user1FullExpertCount` expertunits and `pendingReviewDraftCount` draftunits even empty page; existing `pendingBadge.manualReviewTotal` mailunits. No alias/securitydefaultshim/prefixallow/newstoredfield.
- Browser1440/390 and future07/08UI controls NOT_RUN here; child plan explicitly permits equivalent same-session API actions until final08UI. Whole-master unrestricted Maven/final independent review remain controller gates, not claimed complete. Existing inherited foreign-anchor/unmapped-method500 mapping outside scope, not reconfirmed/repaired.
- Remaining blocker: none identified within current18-file child06 scope. **READY_FOR_VERIFICATION**, not PASS; controller should perform fresh independent light verification before07.

## Controller terminal checkpoint — 2026-10-30T15:11:37.429Z
- Terminal independent verdict: LIGHT_PASS; exact report retained in verify-log.md.
- Final child code head: b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
