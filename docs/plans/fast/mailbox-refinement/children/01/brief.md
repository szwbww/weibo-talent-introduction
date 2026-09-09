# fast-p child 01 brief — 收发件箱查询、邮件标签投影与主题兼容

- Master: docs/plans/2026-09-09/00-mailbox-refinement-master.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/01-mailbox-refinement-data.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Shared audit (part of the contract): docs/plans/2026-09-09/mailbox-refinement-audit.md
- Dependencies: none (first child). Downstream consumer: child 02 frontend.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement (branch fast/mailbox-refinement)
- Child base SHA: af25bf54df2bc70dd0fe9e3254b246a49395219c
- Execution report: docs/plans/fast/mailbox-refinement/children/01/execution.md
- Fix log: docs/plans/fast/mailbox-refinement/children/01/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 9; modify ONLY these)

1. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSubjectDecoder.kt (NEW)
6. src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt
9. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSubjectDecoderTest.kt (NEW)

Known fact: `MailboxConversationRepositorySqlCompatTest` is a second class inside file #6 (`MailboxConversationRepositoryIT.kt`), source-level SQL-shape regression that runs under plain `mvn test` — extend it in place when the plan requires SQL-shape assertions; do not create a new file.

## Work to implement (from child plan T1..T4)

- T1 query sorting/filtering: page/explain share one private ORDER BY helper with contract SQL from plan Invariant I-1 (`pending first; NULL-latest-reply last; latest_reply_at DESC; expert_contact_id DESC` for 全部 only; 关注/待处理 use inbound-latest DESC + stable id). latest_reply_at is a LOCAL SQL projection `MAX(CASE WHEN u.source='INBOUND_PROCESSING' THEN u.event_at END)` — no table column, no new response field. Keep latest_event_at mapping for existing rows. New optional params recipientEmail/keyword (trim→null, ≤255, overlong → 400); subject stays subject-only; keyword matches subject OR cleaned_body OR body inside EXISTS only; message-level conditions are ANDed inside one message EXISTS; OR groups fully parenthesized then ANDed with q/followed (fix audit X2 membership OR-bypass). Date start inclusive / end next-day exclusive; start>end 400. NO ROW_NUMBER/OVER/CTE (MySQL 5.7). No index/migration this round.
- T2 current-window tags: inject existing InboundMailTagService; after fetching a page, extract real INBOUND_PROCESSING ids and call listTagsBatch exactly once (empty set → no call). Message DTO gains `tags: List<TagView> = emptyList()`; only INBOUND_PROCESSING filled; OUTBOUND stays empty — never map via source_inbound_id.
- T3 MIME subject: NEW pure function MailSubjectDecoder.decode(String?):String? — null/plain unchanged; valid folding → unfold once then MimeUtility.decodeText once; no HTML unescape; no decode-as-HTML; no loop; unknown charset/broken input → return original, never throw into receive/conversation reads. Apply at ImapMailReceiveService fetchEnvelopeHeaders Subject read (the only new-inbound behavioral difference) AND conversation service latestMessage.subject / timeline.subject (incl. historical OUTBOUND rows) — read-compat only, never UPDATE old rows, no getContent/attachment access added.
- T4 current-page expert tags: summary DTO gains `expertTags: List<String>? = null`, fully separate from timeline.tags. From the current SQL page contactIds: ExpertContactRepository.findAllById → currentIndexLevel/orcidId; group by level, dedupe non-empty ORCIDs, ExpertSearchService.searchByOrcidIds(orcidIds, level) — max 1 call per non-empty level (≤3 per page), zero calls when page empty. Map back by (level, orcidId), fill in ORIGINAL SQL row order. tags trimmed/deduped keeping ES order; no tags → []; no ORCID / invalid level / missing profile / per-level ES exception → null for that row (never fake []), no cross-level guessing; per-level failure must not 500 the page nor disturb SQL order/pagination; log via existing logger. No ES writes/discovery/promotion; no new state tables or caches. ControllerTest: controlled ExpertSearchService stub/mock — verify 20 same-level experts → 1 call; across 3 levels → ≤3; no results → 0; order stays SQL; mail tags vs expertTags isolated; one level throwing → only that group null.
- Preserve: source authority / account scope / counts and cursor / follow + old waitingReply param compatibility / attachments metadata-only / every existing processing and outbound business writer. GET conversations stays read-only.

## Environment

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (JDK 11 mandatory).
- Isolated MySQL for the mysqlIt gate: container `mailbox-refinement-mysql` (mysql:8.0.36, root/root) listening on 127.0.0.1:3306, database `talent_introduction` inside the container. Test datasource default in src/test/resources/application.yml is jdbc:mysql://localhost:3306/talent_introduction (DB_URL-overridable). Business/dev MySQL is closed at baseline; confirm before running the IT that the 3306 listener is the container (e.g. `SELECT @@hostname, VERSION()` shows the container) and record the check in execution.md. Never run the IT against a real business database.
- Do not run the FlywayMigrationIntegrationTest docker-java path (env-blocked; not required here).

## Required commands (run all freshly; exact output + exit codes in execution.md)

1. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailSubjectDecoderTest,ImapMailReceiveServiceTest,MailboxConversationControllerTest,MailboxConversationRepositorySqlCompatTest
2. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT
   (plan: IT setUp/cleanup writes/deletes data; only the isolated container DB above is acceptable. If no usable isolated MySQL: mark unverified explicitly — never present H2/mock as MySQL proof.)
3. Full suite: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test (fresh; record counts)

## Downstream interfaces child 02 will consume (must match exactly)

- listMessages response items: `tags: List<TagView>` (empty for OUTBOUND) — child 02 renders directly, NO per-message /thread requests.
- listConversations summaries: `expertTags: List<String>?` — null = unavailable (do not fake []), [] = read and none. Display maps names via frontend label mapping; unknown raw values pass through.
- Query contract: followed / pendingOnly booleans; no arbitrary orderBy param; server-side sorting only; recipientEmail / keyword as above.
- waitingReply param and DTO retained compat (not used by new UI).
- Sorting semantics must be exactly the master R1 matrix (I-1) so child 02 makes zero client-side sort calls.

## Constraints

- No DB migration, no ES mapping/writer/field change, no new tables, no schema edits.
- Do not modify files outside the 9-file whitelist. A compile/tests proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- Kotlin Spring Data JDBC patterns of the repo; follow existing conventions in the touched files; no new cache framework, no mocks as MySQL proof.
- Commit implementation locally as: feat(fast-p): implement 01
- Exclude fast-p evidence (docs/plans/fast/**) from the commit; the controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
