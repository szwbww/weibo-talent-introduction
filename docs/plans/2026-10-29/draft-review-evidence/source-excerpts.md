# 代码逐字证据（2026-10-29）
HEAD: b15bb069f43f89c45ee6859dba04a0baff5d774d
行号针对规划时工作树；并行变动后须重读。

## E01 — src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt:1
```text
1: package com.weibo.talentintroduction.auth.config
2: 
3: import com.weibo.talentintroduction.auth.controller.AuthController
4: import com.weibo.talentintroduction.mail.controller.MailboxConversationController
5: import org.springframework.web.method.HandlerMethod
6: import java.util.Locale
7: 
8: /**
9:  * 固定只读账号策略（I-1/I-4）。
10:  *
11:  * 本步不建设通用角色/权限系统：身份只有精确 `admin` 与精确 `user1` 两种语义，
12:  * 绝不从 query/body/前端隐藏推导。`user1` 的已认证请求默认拒绝，仅放行：
13:  * - `GET` [MailboxConversationController.list]（会话列表）；
14:  * - `GET` [MailboxConversationController.messages]（会话时间线）；
15:  * - `POST` [AuthController.logout]。
16:  *
17:  * 白名单按 `HandlerMethod.method.declaringClass + method.name + HTTP method` 精确匹配，
18:  * 绝不使用 URL 前缀放行（否则新映射会自动继承权限）。登录/`me` 仍由
19:  * [AuthWebConfig] 的排除规则处理；`OPTIONS` 沿用原拦截器行为直接放行。
20:  */
21: class MailReadAccessDeniedException(
22:     val code: String,
23:     override val message: String
24: ) : RuntimeException(message)
25: 
26: object MailReadOnlyPolicy {
27: 
28:     /** 内置只读账号：只做精确匹配，不做大小写或前缀匹配。 */
29:     const val READER_USERNAME = "user1"
30: 
31:     /** 唯一管理员账号：授权管理接口只接受该身份。 */
32:     const val ADMIN_USERNAME = "admin"
33: 
34:     /** reader 访问非白名单业务接口的默认拒绝码。 */
35:     const val READ_ONLY_FORBIDDEN = "READ_ONLY_FORBIDDEN"
36: 
37:     /** 邮件详情失权码（02 的详情准入使用；本步只固定常量与 403 映射）。 */
38:     const val MAIL_READ_FORBIDDEN = "MAIL_READ_FORBIDDEN"
39: 
40:     /** 授权管理接口非 admin 的拒绝码。 */
41:     const val ADMIN_REQUIRED = "ADMIN_REQUIRED"
42: 
43:     private data class AllowedHandler(
44:         val httpMethod: String,
45:         val declaringClass: Class<*>,
46:         val methodName: String
47:     )
48: 
49:     private val READER_ALLOWED_HANDLERS = listOf(
50:         AllowedHandler("GET", MailboxConversationController::class.java, "list"),
51:         AllowedHandler("GET", MailboxConversationController::class.java, "messages"),
52:         AllowedHandler("POST", AuthController::class.java, "logout")
53:     )
54: 
55:     fun isReader(username: String?): Boolean = username == READER_USERNAME
56: 
57:     fun isAdmin(username: String?): Boolean = username == ADMIN_USERNAME
58: 
59:     /**
60:      * 授权管理接口的唯一身份闸门：非精确 admin（含匿名/reader/其他测试身份）一律
61:      * 抛出 [ADMIN_REQUIRED] 的权限异常，由 [com.weibo.talentintroduction.common.controller.GlobalExceptionHandler]
62:      * 固定映射为 403，不落 500。
63:      */
64:     fun requireAdmin(actorUsername: String?) {
65:         if (!isAdmin(actorUsername)) {
66:             throw MailReadAccessDeniedException(ADMIN_REQUIRED, "仅管理员可管理邮件查看授权")
67:         }
68:     }
69: 
70:     /**
71:      * reader 是否可访问该 handler。非 `HandlerMethod`（如静态资源）默认拒绝；
72:      * `OPTIONS` 保持原拦截器的直接放行语义。
73:      */
74:     fun isReaderAllowed(handler: Any?, httpMethod: String?): Boolean {
75:         if ("OPTIONS".equals(httpMethod, ignoreCase = true)) {
76:             return true
77:         }
78:         val handlerMethod = handler as? HandlerMethod ?: return false
79:         val verb = httpMethod?.uppercase(Locale.ROOT) ?: return false
80:         val method = handlerMethod.method
81:         return READER_ALLOWED_HANDLERS.any { allowed ->
82:             allowed.httpMethod == verb &&
83:                 allowed.declaringClass == method.declaringClass &&
84:                 allowed.methodName == method.name
85:         }
86:     }
87: }
```

## E02 — src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt:12
```text
12:  * `expert_mail_read_access` 的唯一写者与读者（I-2/I-3）。
13:  *
14:  * 关系即状态：行存在 = 允许内置 `user1` 查看该专家邮件；不存在 = 禁止。`username`
15:  * 恒为 [MailReadOnlyPolicy.READER_USERNAME]，绝不来自请求体/查询参数；`granted_by`
16:  * 只取调用方传入的 admin session 用户名。
17:  *
18:  * 全部读写为参数化 JDBC；本服务只写 `expert_mail_read_access`，不触碰关注、挂起、
19:  * 邮件状态、联系人或 ES，也不引入权限缓存。PUT 用单条 `INSERT ... ON DUPLICATE KEY
20:  * UPDATE`（并发重复仅一行且不刷新 `created_at`）；DELETE 幂等；联系人在存在性校验
21:  * 后被并发删除时，FK 拒绝写入并统一转成 404（不泄露 SQL）。
22:  */
23: @Service
24: class ExpertMailReadAccessService(
25:     private val jdbcTemplate: NamedParameterJdbcTemplate
26: ) {
27: 
28:     /** GET：无授权行返回 false；专家不存在 404。 */
29:     fun getAccess(actorUsername: String?, expertContactId: Long): Boolean {
30:         MailReadOnlyPolicy.requireAdmin(actorUsername)
31:         requireValidContactId(expertContactId)
32:         requireContactExists(expertContactId)
33:         return grantCount(expertContactId) > 0
34:     }
35: 
36:     /** PUT：幂等授予，只保留首次 granted_by/created_at；返回 canRead=true。 */
37:     @Transactional
38:     fun grant(actorUsername: String?, expertContactId: Long): Boolean {
39:         MailReadOnlyPolicy.requireAdmin(actorUsername)
40:         requireValidContactId(expertContactId)
41:         requireContactExists(expertContactId)
42:         try {
43:             jdbcTemplate.update(
44:                 """
45:                 INSERT INTO expert_mail_read_access (username, expert_contact_id, granted_by, created_at)
46:                 VALUES (:username, :contactId, :grantedBy, :createdAt)
47:                 ON DUPLICATE KEY UPDATE expert_contact_id = expert_contact_id
48:                 """.trimIndent(),
49:                 MapSqlParameterSource()
50:                     .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
51:                     .addValue("contactId", expertContactId)
52:                     .addValue("grantedBy", actorUsername)
53:                     .addValue("createdAt", LocalDateTime.now())
54:             )
55:         } catch (ex: DataIntegrityViolationException) {
56:             // 存在性校验后联系人被并发删除：FK 拒绝，统一按专家不存在处理。
57:             throw NoSuchElementException("Expert contact not found: $expertContactId")
58:         }
59:         return true
60:     }
61: 
62:     /** DELETE：幂等撤销（无行也成功）；返回 canRead=false。 */
63:     @Transactional
64:     fun revoke(actorUsername: String?, expertContactId: Long): Boolean {
65:         MailReadOnlyPolicy.requireAdmin(actorUsername)
66:         requireValidContactId(expertContactId)
67:         requireContactExists(expertContactId)
68:         jdbcTemplate.update(
69:             """
70:             DELETE FROM expert_mail_read_access
71:              WHERE username = :username AND expert_contact_id = :contactId
72:             """.trimIndent(),
73:             MapSqlParameterSource()
74:                 .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
75:                 .addValue("contactId", expertContactId)
76:         )
77:         return false
78:     }
79: 
80:     private fun requireValidContactId(expertContactId: Long) {
81:         require(expertContactId > 0) { "expertContactId 必须为正数" }
82:     }
83: 
84:     private fun requireContactExists(expertContactId: Long) {
85:         val exists = jdbcTemplate.queryForObject(
86:             "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
87:             MapSqlParameterSource("contactId", expertContactId),
88:             Long::class.java
89:         ) ?: 0L
90:         if (exists == 0L) {
91:             throw NoSuchElementException("Expert contact not found: $expertContactId")
92:         }
93:     }
94: 
95:     private fun grantCount(expertContactId: Long): Long =
96:         jdbcTemplate.queryForObject(
97:             """
98:             SELECT COUNT(*) FROM expert_mail_read_access
99:              WHERE username = :username AND expert_contact_id = :contactId
100:             """.trimIndent(),
101:             MapSqlParameterSource()
102:                 .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
103:                 .addValue("contactId", expertContactId),
104:             Long::class.java
105:         ) ?: 0L
106: }
```

## E03 — src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql:1
```text
1: CREATE TABLE mailbox_reply_draft (
2:     id BIGINT NOT NULL AUTO_INCREMENT,
3:     username VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
4:     expert_contact_id BIGINT NOT NULL,
5:     target_kind VARCHAR(8) COLLATE utf8mb4_bin NOT NULL,
6:     inbound_processing_id BIGINT NOT NULL,
7:     account_scope VARCHAR(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
8:     version BIGINT NOT NULL,
9:     state VARCHAR(16) NOT NULL,
10:     subject VARCHAR(255) NULL,
11:     html_body LONGTEXT NULL,
12:     text_body LONGTEXT NULL,
13:     context_json LONGTEXT NULL,
14:     send_attempt_id BIGINT NULL,
15:     send_version BIGINT NULL,
16:     created_at DATETIME(3) NOT NULL,
17:     updated_at DATETIME(3) NOT NULL,
18:     PRIMARY KEY (id),
19:     UNIQUE KEY uk_mailbox_reply_draft_target (username, expert_contact_id, target_kind, inbound_processing_id, account_scope),
20:     KEY idx_mailbox_reply_draft_list (username, state, updated_at, id)
21: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## E04 — src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:20
```text
20:             r.getLong("version"), MailReplyDraftState.valueOf(r.getString("state")), r.getString("subject"), r.getString("html_body"), r.getString("text_body"), r.getString("context_json"),
21:             r.getObject("send_attempt_id")?.let { (it as Number).toLong() }, r.getObject("send_version")?.let { (it as Number).toLong() },
22:             r.getTimestamp("created_at").toLocalDateTime(), r.getTimestamp("updated_at").toLocalDateTime(),
23:             r.getBoolean("contact_exists"), r.getString("expert_name"), r.getString("expert_email"), r.getString("send_attempt_status"))
24:     }
25:     private val projection = """SELECT d.*, c.id IS NOT NULL AS contact_exists, c.expert_name, c.expert_email,
26:         a.status AS send_attempt_status FROM mailbox_reply_draft d
27:         LEFT JOIN expert_contact c ON c.id=d.expert_contact_id
28:         LEFT JOIN mail_send_attempt a ON a.id=d.send_attempt_id"""
29:     private fun targetParams(owner: String, target: MailReplyDraftTarget) = mapOf("owner" to owner, "contact" to target.contactId,
30:         "kind" to target.kind.name, "processing" to target.processingId, "scope" to target.accountScope)
31:     fun findTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.expert_contact_id=:contact AND d.target_kind=:kind AND d.inbound_processing_id=:processing AND d.account_scope=:scope", targetParams(owner, target), mapper).singleOrNull()
32:     fun findOwned(owner: String, id: Long): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.id=:id", mapOf("owner" to owner, "id" to id), mapper).singleOrNull()
33:     fun insert(owner: String, target: MailReplyDraftTarget, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Long {
34:         val params = MapSqlParameterSource(targetParams(owner, target)).addValue("subject", content.subject).addValue("html", content.html).addValue("text", content.text).addValue("context", contextJson).addValue("now", now)
35:         val key = GeneratedKeyHolder()
36:         jdbc.update("""INSERT INTO mailbox_reply_draft (username,expert_contact_id,target_kind,inbound_processing_id,account_scope,version,state,subject,html_body,text_body,context_json,created_at,updated_at)
37:             VALUES (:owner,:contact,:kind,:processing,:scope,1,'ACTIVE',:subject,:html,:text,:context,:now,:now)""", params, key)
38:         return requireNotNull(key.key).toLong()
39:     }
40:     fun save(owner: String, id: Long, version: Long, state: MailReplyDraftState, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Boolean {
41:         val resetBinding = if (state == MailReplyDraftState.ACTIVE) "" else ",send_attempt_id=NULL,send_version=NULL"
42:         return jdbc.update("""UPDATE mailbox_reply_draft SET version=version+1,state='ACTIVE',subject=:subject,html_body=:html,text_body=:text,context_json=:context,updated_at=:now$resetBinding
43:             WHERE id=:id AND username=:owner AND version=:version AND state=:state""",
44:             mapOf("owner" to owner,"id" to id,"version" to version,"state" to state.name,"subject" to content.subject,"html" to content.html,"text" to content.text,"context" to contextJson,"now" to now)) == 1
45:     }
46:     fun discard(owner: String, id: Long, version: Long, now: LocalDateTime): Boolean = jdbc.update("""UPDATE mailbox_reply_draft SET state='DISCARDED',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
47:         WHERE id=:id AND username=:owner AND version=:version AND state='ACTIVE'""", mapOf("owner" to owner,"id" to id,"version" to version,"now" to now)) == 1
48: 
49:     private fun scopeSql(scope: String?) = if (scope == null) "" else " AND d.account_scope=:scope"
50:     private fun listParams(owner: String, scope: String?, search: String?): Map<String, Any?> = mapOf("owner" to owner,"scope" to scope,
51:         "search" to ("%" + search.orEmpty().replace("!","!!").replace("%","!%").replace("_","!_") + "%"))
52:     fun list(owner: String, scope: String?, search: String?, page: Int, size: Int): MailReplyDraftList {
53:         val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope) +
54:             if (search.isNullOrEmpty()) "" else " AND (c.expert_name LIKE :search ESCAPE '!' OR c.expert_email LIKE :search ESCAPE '!' OR d.subject LIKE :search ESCAPE '!')"
55:         val params = listParams(owner,scope,search) + mapOf("limit" to size,"offset" to page.toLong()*size)
56:         val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter",params,Long::class.java) ?: 0
57:         // Do not fetch large HTML/context merely to render a list preview.
58:         val items = jdbc.query("""SELECT d.id,d.version,d.state,d.expert_contact_id,d.target_kind,d.inbound_processing_id,d.account_scope,d.subject,
59:             LEFT(d.text_body,120) AS preview,d.updated_at,c.id IS NOT NULL AS contact_exists,c.expert_name,c.expert_email
60:             FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter ORDER BY d.updated_at DESC,d.id DESC LIMIT :limit OFFSET :offset""",params,
61:             RowMapper { r, _ -> MailReplyDraftListItem(r.getLong("id"),r.getLong("version"),MailReplyDraftState.valueOf(r.getString("state")),
62:                 MailReplyDraftTarget(r.getLong("expert_contact_id"),MailReplyDraftKind.valueOf(r.getString("target_kind")),r.getLong("inbound_processing_id"),r.getString("account_scope")),
63:                 r.getString("subject"),r.getString("preview").orEmpty(),r.getTimestamp("updated_at").toLocalDateTime(),r.getBoolean("contact_exists"),r.getString("expert_name"),r.getString("expert_email")) })
64:         return MailReplyDraftList(items,total,page,size)
65:     }
66:     fun summaries(owner: String, scope: String?, contacts: List<Long>): MailReplyDraftSummaries {
67:         val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope)
68:         val params = mapOf("owner" to owner,"scope" to scope,"contacts" to contacts)
69:         val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d$filter",params,Long::class.java) ?: 0
70:         val counts = if (contacts.isEmpty()) emptyMap() else jdbc.query("SELECT d.expert_contact_id,COUNT(*) AS n FROM mailbox_reply_draft d$filter AND d.expert_contact_id IN (:contacts) GROUP BY d.expert_contact_id",params,
71:             RowMapper { r, _ -> r.getLong("expert_contact_id") to r.getLong("n") }).toMap()
72:         return MailReplyDraftSummaries(contacts.map { MailReplyDraftSummary(it,counts[it] ?: 0) },total)
73:     }
74: 
75:     /** Child 02 calls these only inside its existing sending transaction. */
76:     @Transactional(propagation = Propagation.MANDATORY)
77:     fun lockOwned(owner: String, id: Long): MailReplyDraftRow? {
78:         mandatory()
79:         // Lock only the draft row, not the optional LEFT JOIN business rows.
80:         jdbc.queryForList("SELECT id FROM mailbox_reply_draft WHERE username=:owner AND id=:id FOR UPDATE",mapOf("owner" to owner,"id" to id))
81:         return findOwned(owner,id)
82:     }
83:     @Transactional(propagation = Propagation.MANDATORY)
84:     fun bindAttempt(owner: String, id: Long, version: Long, attemptId: Long): Boolean {
85:         mandatory(); require(attemptId > 0 && version > 0)
86:         return jdbc.update("""UPDATE mailbox_reply_draft SET send_attempt_id=:attempt,send_version=:version
87:             WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE'
88:             AND ((send_attempt_id IS NULL AND send_version IS NULL) OR (send_attempt_id=:attempt AND send_version=:version))""",
89:             mapOf("owner" to owner,"id" to id,"version" to version,"attempt" to attemptId)) == 1
90:     }
91:     @Transactional(propagation = Propagation.MANDATORY)
92:     fun closeSent(owner: String, id: Long, sendVersion: Long, attemptId: Long, now: LocalDateTime): Boolean {
93:         mandatory()
94:         return jdbc.update("""UPDATE mailbox_reply_draft SET state='SENT',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
95:             WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE' AND send_attempt_id=:attempt AND send_version=:version""",
96:             mapOf("owner" to owner,"id" to id,"version" to sendVersion,"attempt" to attemptId,"now" to now)) == 1
97:     }
98:     @Transactional(propagation = Propagation.MANDATORY)
99:     fun releaseCompletedBinding(owner: String, id: Long, sendVersion: Long, attemptId: Long): Boolean {
100:         mandatory()
101:         return jdbc.update("UPDATE mailbox_reply_draft SET send_attempt_id=NULL,send_version=NULL WHERE username=:owner AND id=:id AND send_attempt_id=:attempt AND send_version=:version",
102:             mapOf("owner" to owner,"id" to id,"attempt" to attemptId,"version" to sendVersion)) == 1
103:     }
104:     private fun mandatory() { check(TransactionSynchronizationManager.isActualTransactionActive()) { "Draft send operations require an existing transaction" } }
```

## E05 — src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt:25
```text
25:     fun getTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftDetail {
26:         identity(owner); targetShape(target)
27:         val row = repository.findTarget(owner,target)
28:         if (row != null) return detail(row)
29:         validateTarget(target)
30:         return MailReplyDraftDetail(target=target)
31:     }
32:     fun get(owner: String, id: Long): MailReplyDraftDetail { identity(owner); return detail(owned(owner,id)) }
33:     // Each write is one atomic CAS statement; conflict rereads must not use an old REPEATABLE READ snapshot.
34:     fun save(owner: String, target: MailReplyDraftTarget, request: MailReplyDraftSaveRequest): MailReplyDraftDetail {
35:         identity(owner); targetShape(target)
36:         if (request.expectedVersion < 0) invalid("非法 expectedVersion")
37:         val existing = repository.findTarget(owner,target)
38:         if (existing == null) {
39:             if (request.expectedVersion != 0L || request.reopen) conflict(null)
40:         } else {
41:             if (existing.version != request.expectedVersion) conflict(existing)
42:             if (existing.state != MailReplyDraftState.ACTIVE && !request.reopen) conflict(existing, true)
43:         }
44:         validateTarget(target)
45:         val content = normalizeAttachments(owner,target,request.content,existing)
46:         val json = MailReplyDraftContextCodec.serialize(content.context)
47:         if (content.subject.codePointCount(0,content.subject.length) > 255) invalid("主题最多255字符")
48:         val bytes = sequenceOf(content.subject,content.html,content.text,json).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
49:         if (bytes > MAIL_REPLY_DRAFT_MAX_BYTES) throw MailReplyDraftException(HttpStatus.PAYLOAD_TOO_LARGE,"DRAFT_TOO_LARGE","草稿文本总量超过1MiB")
50:         val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
51:         if (existing == null) {
52:             val id = try { repository.insert(owner,target,content,json,now) } catch (e: DuplicateKeyException) { conflict(repository.findTarget(owner,target)) }
53:             return detail(owned(owner,id))
54:         }
55:         if (!repository.save(owner,existing.id,existing.version,existing.state,content,json,now)) conflict(owned(owner,existing.id))
56:         return detail(existing.copy(version=existing.version+1,state=MailReplyDraftState.ACTIVE,subject=content.subject,html=content.html,text=content.text,contextJson=json,updatedAt=now,
57:             sendAttemptId=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptId else null,
58:             sendVersion=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendVersion else null,
59:             sendAttemptStatus=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptStatus else null))
60:     }
61:     @Transactional(propagation = Propagation.REQUIRES_NEW)
62:     fun discard(owner: String, id: Long, expectedVersion: Long): MailReplyDraftDetail {
63:         identity(owner)
64:         repository.lockOwned(owner,id)
65:         val row = owned(owner,id)
66:         if (expectedVersion <= 0 || row.version != expectedVersion) conflict(row)
67:         if (row.state != MailReplyDraftState.ACTIVE) return detail(row)
68:         if (row.sendAttemptStatus == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
69:             throw MailReplyDraftException(HttpStatus.CONFLICT,"DRAFT_SEND_IN_PROGRESS","草稿正在发送，不能放弃",row.version,row.state)
70:         }
71:         val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
72:         if (!repository.discard(owner,id,expectedVersion,now)) conflict(owned(owner,id))
73:         return detail(row.copy(version=row.version+1,state=MailReplyDraftState.DISCARDED,subject=null,html=null,text=null,contextJson=null,updatedAt=now))
74:     }
75:     fun list(owner: String, search: String?, accountScope: String?, page: Int, size: Int): MailReplyDraftList {
76:         identity(owner); filterScope(accountScope)
77:         if (page < 0 || size !in 1..100) invalid("非法草稿分页")
78:         return repository.list(owner,accountScope?.takeIf { it.isNotEmpty() },search,page,size)
79:     }
80:     fun summaries(owner: String, contactIds: List<Long>, accountScope: String?): MailReplyDraftSummaries {
81:         identity(owner); filterScope(accountScope)
82:         val ids = contactIds.distinct()
83:         if (ids.size > 100 || ids.any { it <= 0 }) invalid("contactIds最多100个真实id")
84:         return repository.summaries(owner,accountScope?.takeIf { it.isNotEmpty() },ids)
85:     }
```

## E06 — src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:508
```text
508:     fun timelineMessages(
509:         contactId: Long,
510:         filter: ConversationFilter,
511:         before: ConversationKeyset?,
512:         limit: Int
513:     ): TimelinePage {
514:         val sql = """
515:             SELECT u.expert_contact_id AS expert_contact_id,
516:                    u.source AS source,
517:                    u.id AS id,
518:                    u.direction AS direction,
519:                    u.account_code AS account_code,
520:                    u.subject AS subject,
521:                    u.body AS body,
522:                    u.cleaned_body AS cleaned_body,
523:                    u.event_at AS event_at,
524:                    u.send_status AS send_status,
525:                    u.process_status AS process_status,
526:                    u.message_id AS message_id,
527:                    u.in_reply_to AS in_reply_to
528:               FROM (${rangeUnionSql(includeBody = true)}) u
529:              WHERE u.expert_contact_id = :contactId
530:                AND (:beforeTime IS NULL
531:                     OR u.event_at < :beforeTime
532:                     OR (u.event_at = :beforeTime
533:                         AND (u.source_rank < :beforeSourceRank
534:                              OR (u.source_rank = :beforeSourceRank AND u.id < :beforeId))))
535:              ORDER BY u.event_at DESC, u.source_rank DESC, u.id DESC
536:              LIMIT :fetchLimit
537:         """.trimIndent()
538:         val p = params("", filter)
539:         p.addValue("contactId", contactId)
540:         p.addValue("beforeTime", before?.beforeTime)
541:         p.addValue("beforeSourceRank", before?.beforeSourceRank)
542:         p.addValue("beforeId", before?.beforeId)
543:         p.addValue("fetchLimit", limit + 1)
544:         val rows = jdbcTemplate.query(sql, p) { rs, _ -> rs.toMessageRow() }
545:         val hasMore = rows.size > limit
546:         return TimelinePage(rows = if (hasMore) rows.take(limit) else rows, hasMore = hasMore)
547:     }
548: 
549:     // ------------------------------------------------------------------
550:     // SQL 装配
551:     // ------------------------------------------------------------------
552: 
553:     /**
554:      * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
555:      * 两个分支的文本列显式 CONVERT + COLLATE utf8mb4_unicode_ci，避免 UNION 排序/物化
556:      * 时字符集冲突。includeBody 仅 timeline 需要（summary 永不读正文列）。
557:      * 注意：本 SQL 片段同时作为 count/summary/latest 系列/accountCodes/timeline 的数据基础，
558:      * 各列位置与别名必须保持一致。
559:      */
560:     private fun rangeUnionSql(includeBody: Boolean): String {
561:         val outboundBody = if (includeBody) {
562:             """,
563:                    CONVERT(mr.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
564:                    CONVERT(mr.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
565:         } else ""
566:         val inboundBody = if (includeBody) {
567:             """,
568:                    CONVERT(imp.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
569:                    CONVERT(imp.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
570:         } else ""
571:         return """
572:             SELECT mr.expert_contact_id AS expert_contact_id,
573:                    1 AS source_rank,
574:                    CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
575:                    mr.id AS id,
576:                    CONVERT('OUTBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
577:                    CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
578:                    COALESCE(mr.sent_at, mr.created_at) AS event_at,
579:                    CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
580:                    CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
581:                    CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
582:                    CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
583:                    CONVERT(mr.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
584:                    CONVERT(mr.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
585:                    CASE WHEN mr.send_status = 'SENT' THEN 1 ELSE 0 END AS sent_flag,
586:                    CASE WHEN mr.send_status = 'FAILED' THEN 1 ELSE 0 END AS failed_flag,
587:                    0 AS received_flag,
588:                    0 AS pending_flag$outboundBody
589:               FROM mail_record mr
590:              WHERE mr.direction = 'OUTBOUND'
591:                AND mr.expert_contact_id IS NOT NULL
592:                AND mr.sender_account_code IN (:accountCodes)
593:                AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
594:             UNION ALL
595:             SELECT imp.expert_contact_id AS expert_contact_id,
596:                    2 AS source_rank,
597:                    CONVERT('INBOUND_PROCESSING' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
598:                    imp.id AS id,
599:                    CONVERT('INBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
600:                    CONVERT(imp.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
601:                    imp.received_at AS event_at,
602:                    CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
603:                    CONVERT(imp.process_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
604:                    CONVERT(imp.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
605:                    CONVERT(SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
606:                    CONVERT(imp.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
607:                    CONVERT(imp.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
608:                    0 AS sent_flag,
609:                    0 AS failed_flag,
610:                    1 AS received_flag,
611:                    CASE WHEN imp.process_status = 'MANUAL_REVIEW' THEN 1 ELSE 0 END AS pending_flag$inboundBody
612:               FROM inbound_mail_processing imp
613:              WHERE imp.expert_contact_id IS NOT NULL
614:                AND imp.sender_account_code IN (:accountCodes)
615:                AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
616:         """.trimIndent()
617:     }
618: 
```

## E07 — src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:704
```text
704:     private fun expertPredicates(username: String, filter: ConversationFilter, eligibility: MembershipEligibility): String {
705:         val clauses = mutableListOf<String>()
706:         clauses += """
707:             (:q IS NULL OR :q = ''
708:              OR ec.expert_name LIKE CONCAT('%', :q, '%')
709:              OR ec.expert_email LIKE CONCAT('%', :q, '%'))
710:         """.trimIndent()
711:         clauses += """
712:             (:followed = 0 OR EXISTS (
713:                 SELECT 1 FROM expert_follow eff
714:                  WHERE eff.username = :username
715:                    AND eff.expert_contact_id = u.expert_contact_id
716:                    AND eff.progress_status = 'FOLLOWING'))
717:         """.trimIndent()
718:         // 01 (I-4)：已提供与跟进中同层、互斥（同一行 progress_status 只能取一个值），
719:         // followed=true AND providedOnly=true 按 AND 得空集（不悄悄取其一）；参数化布尔
720:         // 开关，绝不被消息 OR 括号绕过。
721:         clauses += """
722:             (:providedOnly = 0 OR EXISTS (
723:                 SELECT 1 FROM expert_follow ef_provided
724:                  WHERE ef_provided.username = :username
725:                    AND ef_provided.expert_contact_id = u.expert_contact_id
726:                    AND ef_provided.progress_status = 'PROVIDED'))
727:         """.trimIndent()
728:         // 01 (I-4)：挂起归类与关注/待处理同层，OR 组外再 AND（q/followed 不可被绕过）。
729:         // suspendedOnly=true 用 EXISTS（有行）；待处理/已回复用 NOT EXISTS（无当前用户挂起行）。
730:         // 同传相互冲突条件按 AND 得空集，不隐藏改写参数。
731:         if (filter.suspendedOnly) {
732:             clauses += """
733:                 EXISTS (
734:                     SELECT 1 FROM expert_mailbox_suspension ems_suspended
735:                      WHERE ems_suspended.username = :username
736:                        AND ems_suspended.expert_contact_id = u.expert_contact_id)
737:             """.trimIndent()
738:         }
739:         if (filter.pendingOnly || filter.repliedOnly) {
740:             clauses += """
741:                 NOT EXISTS (
742:                     SELECT 1 FROM expert_mailbox_suspension ems_not_suspended
743:                      WHERE ems_not_suspended.username = :username
744:                        AND ems_not_suspended.expert_contact_id = u.expert_contact_id)
745:             """.trimIndent()
746:         }
747:         if (filter.repliedOnly) {
748:             clauses += """
749:                 NOT EXISTS (
750:                     SELECT 1 FROM expert_follow ef_replied
751:                      WHERE ef_replied.username = :username
752:                        AND ef_replied.expert_contact_id = u.expert_contact_id)
753:             """.trimIndent()
754:             clauses += """
755:                 EXISTS (
756:                     SELECT 1 FROM mail_record mr_replied
757:                      WHERE mr_replied.expert_contact_id = u.expert_contact_id
758:                        AND mr_replied.direction = 'OUTBOUND'
759:                        AND mr_replied.send_status = 'SENT'
760:                        AND mr_replied.sender_account_code IN (:accountCodes)
761:                        AND (:accountCode IS NULL OR mr_replied.sender_account_code = :accountCode))
762:             """.trimIndent()
763:             clauses += """
764:                 EXISTS (
765:                     SELECT 1 FROM inbound_mail_processing imp_replied
766:                      WHERE imp_replied.expert_contact_id = u.expert_contact_id
767:                        AND imp_replied.sender_account_code IN (:accountCodes)
768:                        AND (:accountCode IS NULL OR imp_replied.sender_account_code = :accountCode)
769:                        AND imp_replied.id > COALESCE((
770:                            SELECT erd.last_inbound_id FROM expert_replied_dismissal erd
771:                             WHERE erd.username = :username
772:                               AND erd.expert_contact_id = u.expert_contact_id
773:                        ), 0))
774:             """.trimIndent()
775:         }
776:         val membershipClauses = listOfNotNull(eligibility.outboundClause, eligibility.inboundClause)
777:         if (membershipClauses.isNotEmpty()) {
778:             clauses += "(\n${membershipClauses.joinToString("\n      OR ")}\n      )"
779:         }
780:         // 02 (I-1)：只读账号的成员过滤必须在所有 OR 条件之外（每条既有子句都已带括号），
781:         // 与 q/followed/provided/挂起/消息级 EXISTS 同层 AND。关系即状态：无 grant 行即
782:         // 不可见，空授权集自然得到 total=0/items=[]（绝不在应用层取全量后再过滤）。
783:         // `:username` 是固定绑定参数，绝不做字符串拼接。
784:         if (MailReadOnlyPolicy.isReader(username)) {
785:             clauses += """
786:                 EXISTS (
787:                     SELECT 1 FROM expert_mail_read_access emra
788:                      WHERE emra.username = :username
789:                        AND emra.expert_contact_id = u.expert_contact_id)
790:             """.trimIndent()
791:         }
792:         return clauses.joinToString("\n   AND ")
793:     }
794: 
795:     private fun havingClause(filter: ConversationFilter): String {
796:         val conditions = mutableListOf<String>()
797:         if (filter.waitingReply) {
798:             // I-2：全历史账号范围 receivedCount=0 且 sentCount>0（FAILED 不计为 sent）。
799:             conditions += "SUM(u.received_flag) = 0 AND SUM(u.sent_flag) > 0"
800:         }
801:         if (filter.pendingOnly) {
802:             // 待处理谓词复用 processing 现有 MANUAL_REVIEW 谓词。
803:             conditions += "SUM(u.pending_flag) > 0"
804:         }
805:         return if (conditions.isEmpty()) "" else "HAVING ${conditions.joinToString(" AND ")}"
806:     }
807: 
808:     private fun params(username: String, filter: ConversationFilter): MapSqlParameterSource =
809:         MapSqlParameterSource()
810:             .addValue("username", username)
811:             .addValue("accountCodes", filter.accountCodes)
812:             .addValue("accountCode", filter.accountCode)
813:             .addValue("q", filter.q?.takeIf { it.isNotBlank() })
814:             .addValue("followed", if (filter.followed) 1 else 0)
815:             .addValue("providedOnly", if (filter.providedOnly) 1 else 0)
816:             .addValue("startTime", filter.startTime)
817:             .addValue("endTime", filter.endTime)
818:             .addValue("subject", filter.subject?.takeIf { it.isNotBlank() })
819:             .addValue("label", filter.label?.takeIf { it.isNotBlank() })
820:             .addValue("recipientEmail", filter.recipientEmail?.takeIf { it.isNotBlank() })
821:             .addValue("keyword", filter.keyword?.takeIf { it.isNotBlank() })
822: 
823:     private fun ResultSet.toSummaryRow(): ConversationSummarySqlRow =
```

## E08 — src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:205
```text
205:                 },
206:                 materialCount = materialCounts[row.expertContactId] ?: 0L,
207:                 expertTags = expertTagsByContact[row.expertContactId],
208:                 // 01 (I-1/I-3)：逐项填真实值；未挂起行也带真实跨账号 pending 计数。
209:                 suspended = suspensionStates[row.expertContactId]?.suspended ?: false,
210:                 suspendReason = suspensionStates[row.expertContactId]?.suspendReason,
211:                 suspensionPendingCount = suspensionStates[row.expertContactId]?.suspensionPendingCount ?: 0L,
212:                 // 01 (I-1/I-4)：一次读取的 DB 真值；followed 由同一状态派生。
213:                 progressStatus = row.progressStatus
214:             )
215:         }
216:         return ConversationListResponse(items, total, pageIndex, pageSize)
217:     }
218: 
219:     // ------------------------------------------------------------------
220:     // 单专家 timeline
221:     // ------------------------------------------------------------------
222: 
223:     fun listMessages(
224:         contactId: Long,
225:         accountCode: String?,
226:         beforeCursor: String?,
227:         limit: Int,
228:         /**
229:          * 02 (I-2/I-4)：会话身份，只由 HTTP Controller 从 session 显式传入；默认 null 保持
230:          * 既有内部调用/测试兼容（null 非 reader → 管理员语义）。admission 在 Service 内，
231:          * 不新增绕行 HTTP 入口。
232:          */
233:         username: String? = null
234:     ): ConversationMessageListResponse {
235:         // 02 (I-2/I-3)：只读账号的准入先于联系人存在检查、正文/标签/附件查询。grant 缺失与
236:         // 专家不存在统一 403 MAIL_READ_FORBIDDEN（不泄露存在性）；每次请求都读 DB，
237:         // 撤销提交后开始的新请求（含更早页/刷新）必然被拒，无缓存/推送。
238:         val reader = MailReadOnlyPolicy.isReader(username)
239:         if (reader && !repository.hasReadAccess(username.orEmpty(), contactId)) {
240:             throw MailReadAccessDeniedException(
241:                 MailReadOnlyPolicy.MAIL_READ_FORBIDDEN,
242:                 "无权查看该专家的邮件"
243:             )
244:         }
245:         expertContactRepository.findById(contactId)
246:             .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
247:         val activeCodes = activeAccountCodes()
248:         val pageLimit = limit.coerceIn(1, MAX_MESSAGE_LIMIT)
249:         if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
250:             return ConversationMessageListResponse(emptyList(), null, false)
251:         }
252:         val before = decodeKeyset(beforeCursor, contactId, accountCode)
253:         val filter = ConversationFilter(accountCodes = activeCodes, accountCode = accountCode)
254:         val page = repository.timelineMessages(contactId, filter, before, pageLimit)
255: 
256:         // I-3：只把当前窗口的真实 INBOUND_PROCESSING id 交出去批量读一次标签；窗口内
257:         // 无来信（纯 OUTBOUND / 更早页）不发请求。绝不用 source_inbound_id / 数值巧合
258:         // 给 OUTBOUND 行映射标签。
259:         val processingIds = page.rows
260:             .filter { it.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING }
261:             .map { it.id }
262:         val tagsByProcessingId = if (processingIds.isEmpty()) {
263:             emptyMap()
264:         } else {
265:             inboundMailTagService.listTagsBatch(processingIds)
266:         }
267: 
268:         // 03 (I-3/I-4)：只对当前窗口的 MAIL_RECORD+OUTBOUND+SENT 行按 id 集合批量读一次
269:         // 存档快照（findAllById 恰好一次；窗口无此类行不发请求）。INBOUND 行即使与
270:         // outbound 数值 id 相同也绝不读 outbound 快照（避免 source 碰撞串附件）。
271:         // 02 (I-3)：只读账号不做任何附件投影，因此这段仅用于日历/通用附件的快照读取
272:         // 完全跳过（零交互），绝不为 reader 预读 mail_record。
273:         val outboundSentRows = if (reader) {
274:             emptyList()
275:         } else {
276:             page.rows.filter {
277:                 it.source == MailboxConversationRepository.SOURCE_MAIL_RECORD &&
278:                     it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
279:                     it.sendStatus == SEND_STATUS_SENT
280:             }
281:         }
282:         val mailRecordRowsById = if (outboundSentRows.isEmpty()) {
283:             emptyMap()
284:         } else {
285:             mailRecordRepository.findAllById(outboundSentRows.map { it.id })
286:                 .asSequence()
287:                 .filter {
288:                     it.id != null &&
289:                         it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
290:                         it.sendStatus == SEND_STATUS_SENT
291:                 }
292:                 .associateBy { requireNotNull(it.id) }
```

## E09 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:390
```text
390:     fun sendConversationManualRichReply(
391:         contactId: Long,
392:         requestId: String,
393:         accountScope: String?,
394:         anchorMailRecordId: Long? = null,
395:         subject: String,
396:         htmlBody: String,
397:         textBody: String?,
398:         operatorName: String?,
399:         safetyWarningConfirmed: Boolean = false,
400:         strongConfirmationText: String? = null,
401:         // 06 (T1/I-2)：通用附件 id 与真实会话身份。空/空身份保持既有调用点零改动。
402:         attachmentIds: List<String> = emptyList(),
403:         authenticatedUsername: String? = null,
404:         draftRef: MailReplyDraftRef? = null
405:     ): PendingMailSendResult {
406:         val contact = expertContactRepository.findById(contactId)
407:             .orElseThrow { error("Expert contact not found: $contactId") }
408:         require(subject.isNotBlank()) { "Subject is required" }
409:         require(htmlBody.isNotBlank()) { "HTML body is required" }
410:         require(subject.trim().length <= 255) { "Subject exceeds 255 characters" }
411:         require(requestId.isNotBlank()) { "requestId is required" }
412:         val canonicalRequestId = try {
413:             manualReplySendAttemptService.canonicalConversationRequestId(requestId)
414:         } catch (ex: IllegalArgumentException) {
415:             throw IllegalArgumentException("requestId must be a valid UUID", ex)
416:         }
417: 
418:         // I-4：已完成 attempt 先收敛 —— SENT 时直接返回原结果，绝不重查锚点/再次投递
419:         // （刚发出的信已成为最新成功发件也不得再次 SMTP）。
420:         // 06 (I-2)：本次或原记录任何一侧带通用附件时，先用 04 批量元数据校验归属
421:         // （同专家 + 同上传者，不要求原件仍在线），再按 filename/type/size/hash 有序语义
422:         // 比较：相同返回原 SENT；不同固定 409，绝不谎报新附件已发送、也不换 requestId。
423:         val completed = manualReplySendAttemptService.findCompletedByRequestId(
424:             contact.orcidId, canonicalRequestId
425:         )
426:         val trustedDraftAttempt = draftRef?.let {
427:             requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
428:                 authenticatedUsername.orEmpty(),it,contactId,null,accountScope,completed?.attemptId)
429:         }
430:         if (completed != null && (draftRef == null || trustedDraftAttempt == completed.attemptId)) {
431:             val record = completed.mailRecord
432:             val originalSnapshots = OutboundAttachmentSnapshotCodec.parseOrThrow(
433:                 record.outboundAttachmentsJson
434:             )
435:             val requestedSnapshots = resolveRequestedAttachmentSnapshots(
436:                 contactId = contactId,
437:                 attachmentIds = attachmentIds,
438:                 authenticatedUsername = authenticatedUsername
439:             )
440:             if ((originalSnapshots != null || requestedSnapshots.isNotEmpty()) &&
441:                 !sameOutboundAttachmentSet(originalSnapshots.orEmpty(), requestedSnapshots)
442:             ) {
443:                 throw OutboundAttachmentException.conflict(CONVERSATION_SENT_ATTACHMENTS_CHANGED)
444:             }
445:             return PendingMailSendResult(
446:                 contactId = contactId,
447:                 senderAccountCode = record.senderAccountCode ?: completed.attemptAccountCode,
448:                 mailType = "MANUAL_RICH_REPLY",
449:                 subject = record.subject ?: subject,
450:                 sendStatus = "SENT",
451:                 messageId = record.messageId ?: completed.attemptMessageId
452:             )
453:         }
454:         val validatedDraft = draftRef?.let {
455:             requireNotNull(mailReplyDraftService).validateConversationSend(authenticatedUsername.orEmpty(),it,
456:                 contactId,accountScope,canonicalRequestId,anchorMailRecordId,subject,htmlBody,textBody,attachmentIds)
457:         }
458: 
459:         // I-1：真实 SENT 出站锚点（排除空账号/模拟器）；accountScope 非空时只在该账号内找，
460:         // scope 下无成功发件不回退其他账号（I-6）。锚点查询在任何 claim/SMTP 之前。
461:         // 跟进（I-2）：显式 id 必须重读同一行并全校验，前端只作提示、绝不作为权限边界。
462:         val anchor = resolveIdentityConversationAnchor(contactId, accountScope, anchorMailRecordId)
463:         val accountCode = requireNotNull(anchor.senderAccountCode) {
464:             "Conversation anchor mail record has no sender account"
465:         }
466:         val account = mailSenderAccountService.getManualSendAccount(accountCode)
467: 
468:         // I-7：Message-ID 仅在 trim 后非空且 <=255 时进入落库 inReplyTo 与 SMTP 线程头；
469:         // 缺失/超长不取消资格 —— 线程头为空仍发送（I-3：引用链只来自真实锚点）。
470:         val anchorMessageId = anchor.messageId
471:             ?.trim()
472:             ?.takeIf { it.isNotBlank() && it.length <= 255 }
473:         val smtpReferences = if (anchorMessageId != null) {
474:             listOfNotNull(anchor.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
475:                 .joinToString(" ")
476:         } else null
477: 
478:         val source = ManualRichSendSource(
479:             contact = contact,
480:             contactId = contactId,
481:             inboundProcessingId = null,
482:             inboundRecord = null,
483:             account = account,
484:             accountCode = accountCode,
485:             persistInReplyTo = anchorMessageId,
486:             smtpInReplyTo = anchorMessageId,
487:             smtpReferences = smtpReferences,
488:             anchorMailRecordId = requireNotNull(anchor.id) { "Conversation anchor mail record has no id" },
489:             requestId = canonicalRequestId,
490:             identityAnchor = anchor
491:         )
492:         return executeManualRichSend(
493:             source = source,
494:             rawSubject = subject,
495:             htmlBody = htmlBody,
496:             textBody = textBody,
497:             operatorName = operatorName,
498:             safetyWarningConfirmed = safetyWarningConfirmed,
499:             strongConfirmationText = strongConfirmationText,
500:             evidence = ManualReplyEvidenceContext(runInboundSemanticChecks = false),
501:             attachmentIds = attachmentIds,
502:             authenticatedUsername = authenticatedUsername,
503:             draftSendRef = validatedDraft
504:         )
505:     }
506: 
507:     fun inspectReplyIdentity(request: ReplyIdentityInspectRequest): ReplyIdentityResult =
508:         replyIdentityService.inspect(resolveIdentitySource(request.contextRequest()), request.textBody, request.htmlBody)
509: 
510:     fun previewReplyIdentityDefaults(request: ReplyIdentityDefaultsPreviewRequest): ReplyIdentityDefaultsPreview =
```

## E10 — src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:260
```text
260:         val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
261:         return CompletedOutboundReply(attemptId, attempt.messageId, attempt.accountCode, record)
262:     }
263: 
264:     @Transactional(propagation = Propagation.REQUIRES_NEW)
265:     fun prepareAndClaim(payload: SendPayload): ClaimedAttempt {
266:         val draftRef = payload.draftSendRef
267:         val drafts = draftRef?.let { requireNotNull(mailReplyDraftService) { "Draft sending is not wired" } }
268:         val draftRow = draftRef?.let { requireNotNull(drafts).lockForClaim(it) }
269:         // A different requestId or changed content must not evade an unresolved delivery.
270:         if (draftRow?.sendAttemptId != null) {
271:             val bound = attemptRepository.findById(draftRow.sendAttemptId).orElseThrow {
272:                 IllegalStateException("Draft bound attempt is missing")
273:             }
274:             val blocked = when (bound.status) {
275:                 MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> ClaimResult.IN_PROGRESS
276:                 MailSendAttemptStatus.DELIVERY_UNKNOWN -> ClaimResult.UNKNOWN
277:                 else -> null
278:             }
279:             if (blocked != null) return ClaimedAttempt(requireNotNull(bound.id),bound.messageId,blocked)
280:             require(bound.status == MailSendAttemptStatus.FAILED_SAFE_TO_RETRY ||
281:                 bound.status == MailSendAttemptStatus.FAILED || bound.status == MailSendAttemptStatus.SENT) { "Draft attempt cannot be replaced" }
282:             requireNotNull(drafts).releaseBinding(draftRow)
283:         }
284:         val fingerprint = computeFingerprint(payload)
285:         val now = LocalDateTime.now()
286: 
287:         attemptRepository.insertIgnore(
288:             orcidId = payload.orcidId,
289:             mailType = fingerprint.shortKey,
290:             accountCode = payload.accountCode,
291:             messageId = fingerprint.messageId,
292:             status = MailSendAttemptStatus.PREPARED,
293:             recipient = payload.normalizedRecipient,
294:             subject = payload.subject,
295:             body = fingerprint.fullHex,
296:             contentType = FINGERPRINT_CONTENT_TYPE,
297:             createdAt = now,
298:             updatedAt = now
299:         )
300: 
301:         val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
302:             payload.orcidId, fingerprint.shortKey
303:         ) ?: throw IllegalStateException(
304:             "Mail send attempt not found after reservation: orcidId=${payload.orcidId} mailType=${fingerprint.shortKey}"
305:         )
306: 
307:         if (attempt.contentType != FINGERPRINT_CONTENT_TYPE) {
308:             throw IllegalArgumentException(
309:                 "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
310:             )
311:         }
312:         if (attempt.body != fingerprint.fullHex) {
313:             throw IllegalArgumentException(
314:                 "Mail send attempt fingerprint collision: full hash mismatch"
315:             )
316:         }
317:         if (attempt.recipient != payload.normalizedRecipient) {
318:             throw IllegalArgumentException(
319:                 "Mail send attempt fingerprint collision: recipient mismatch"
320:             )
321:         }
322: 
323:         val claim = when (attempt.status) {
324:             MailSendAttemptStatus.PREPARED -> {
325:                 val affected = attemptRepository.claimStatus(
326:                     requireNotNull(attempt.id),
327:                     MailSendAttemptStatus.PREPARED,
328:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
329:                     now
330:                 )
331:                 if (affected <= 0) {
332:                     throw IllegalStateException("CAS claim failed for attempt ${attempt.id}")
333:                 }
334:                 ClaimedAttempt(
335:                     attemptId = requireNotNull(attempt.id),
336:                     messageId = attempt.messageId,
337:                     result = ClaimResult.CLAIMED
338:                 )
339:             }
340: 
341:             MailSendAttemptStatus.SENT ->
342:                 ClaimedAttempt(
343:                     attemptId = requireNotNull(attempt.id),
344:                     messageId = attempt.messageId,
345:                     result = ClaimResult.DEDUP_SENT
346:                 )
347: 
348:             MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> {
349:                 val affected = attemptRepository.claimStatus(
350:                     requireNotNull(attempt.id),
351:                     MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
352:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
353:                     now
354:                 )
355:                 if (affected <= 0) {
356:                     throw IllegalStateException("CAS claim for safe retry failed for attempt ${attempt.id}")
357:                 }
358:                 ClaimedAttempt(
359:                     attemptId = requireNotNull(attempt.id),
360:                     messageId = attempt.messageId,
361:                     result = ClaimResult.SAFE_RETRY_CLAIMED
362:                 )
363:             }
364: 
365:             MailSendAttemptStatus.DELIVERY_IN_PROGRESS ->
366:                 ClaimedAttempt(
367:                     attemptId = requireNotNull(attempt.id),
368:                     messageId = attempt.messageId,
369:                     result = ClaimResult.IN_PROGRESS
370:                 )
371: 
372:             MailSendAttemptStatus.DELIVERY_UNKNOWN ->
373:                 ClaimedAttempt(
374:                     attemptId = requireNotNull(attempt.id),
375:                     messageId = attempt.messageId,
376:                     result = ClaimResult.UNKNOWN
377:                 )
378: 
379:             else ->
380:                 ClaimedAttempt(
381:                     attemptId = requireNotNull(attempt.id),
382:                     messageId = attempt.messageId,
383:                     result = ClaimResult.PERMANENT_FAILED
384:                 )
385:         }
386:         if (draftRef != null && (claim.result == ClaimResult.CLAIMED || claim.result == ClaimResult.SAFE_RETRY_CLAIMED || claim.result == ClaimResult.DEDUP_SENT)) {
387:             if (claim.result == ClaimResult.DEDUP_SENT) {
388:                 check(mailRecordRepository.findByMailSendAttemptId(claim.attemptId)?.sendStatus == "SENT") {
389:                     "Completed attempt has no durable SENT mail record"
390:                 }
391:             }
392:             requireNotNull(drafts).bind(draftRef,claim.attemptId)
393:             if (claim.result == ClaimResult.DEDUP_SENT) drafts.closeSent(draftRef,claim.attemptId,now)
394:         }
395:         return claim
396:     }
397: 
398:     @Transactional(propagation = Propagation.REQUIRES_NEW)
399:     fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
400:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
```

## E11 — src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:399
```text
399:     fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
400:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
401:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
402:             IllegalStateException("Mail send attempt not found: $attemptId")
403:         }
404:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
405:             "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
406:         }
407: 
408:         val now = LocalDateTime.now()
409:         val bodyText = payload.finalText.ifBlank { null }
410:         val mailBody = bodyText ?: payload.finalHtml
411:         // fast-p 02 (I-1/I-4)：与本次发送同一实例的规范快照 JSON；null 显式清空
412:         // （不沿用安全失败记录的旧附件）。
413:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
414:         // fast-p 05 (I-1/I-4)：本次发送的有序通用附件快照 JSON；空列表显式 null
415:         // （唯一 absence 形态，绝不写 []/空串，也不沿用上一次尝试的旧快照）。
416:         val outboundSnapshotJson = payload.outboundAttachments
417:             .takeIf { it.isNotEmpty() }
418:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
419:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
420: 
421:         val mailRecord = if (existingRecord != null) {
422:             existingRecord.copy(
423:                 senderAccountCode = payload.accountCode,
424:                 messageId = messageId,
425:                 inReplyTo = payload.inReplyTo,
426:                 subject = payload.subject,
427:                 body = mailBody,
428:                 matchedQaRuleId = payload.primaryRuleId,
429:                 sendStatus = "SENT",
430:                 sentAt = now,
431:                 errorSummary = null,
432:                 calendarAttachmentJson = snapshotJson,
433:                 outboundAttachmentsJson = outboundSnapshotJson
434:             )
435:         } else {
436:             MailRecord(
437:                 expertContactId = payload.contactId,
438:                 direction = "OUTBOUND",
439:                 mailType = "MANUAL_RICH_REPLY",
440:                 senderAccountCode = payload.accountCode,
441:                 triggeredBy = TriggeredBy.OPERATOR,
442:                 sourceInboundId = null,
443:                 messageId = messageId,
444:                 inReplyTo = payload.inReplyTo,
445:                 subject = payload.subject,
446:                 body = mailBody,
447:                 matchedQaRuleId = payload.primaryRuleId,
448:                 sendStatus = "SENT",
449:                 receivedAt = null,
450:                 sentAt = now,
451:                 mailSendAttemptId = attemptId,
452:                 createdAt = existingRecord?.createdAt ?: now,
453:                 calendarAttachmentJson = snapshotJson,
454:                 outboundAttachmentsJson = outboundSnapshotJson
455:             )
456:         }
457:         val savedRecord = mailRecordRepository.save(mailRecord)
458:         val mailRecordId = requireNotNull(savedRecord.id)
459: 
460:         if (payload.canonicalQaRuleIds.isNotEmpty()) {
461:             payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
462:                 mailRecordQaRuleRepository.save(
463:                     MailRecordQaRule(
464:                         mailRecordId = mailRecordId,
465:                         qaRuleId = qaRuleId,
466:                         ordinal = ordinal
467:                     )
468:                 )
469:             }
470:         }
471: 
472:         // fast-p 02 (I-2)：真实 SENT mail_record 取得 id 之后、attempt 标 SENT 之前创建
473:         // 排期 —— 与成功落库同一 REQUIRES_NEW 事务提交，二者同成功或同回滚（排期写失败
474:         // 绝不返回发送成功）。普通发送/无会议回信 meetingEvent 为 null，完全跳过。
475:         // 01 createFromSentMail 校验来源邮件（OUTBOUND/MANUAL_RICH_REPLY/SENT/带日历附件）
476:         // 与结构化输入；校验失败同样回滚整个成功事务（不静默吞掉排期写入错误）。
477:         payload.meetingEvent?.let { event ->
478:             meetingCalendarService.createFromSentMail(savedRecord, event)
479:         }
480: 
481:         attemptRepository.updateStatusAndError(
482:             id = attemptId,
483:             status = MailSendAttemptStatus.SENT,
484:             errorSummary = null,
485:             now = now
486:         )
487:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).closeSent(it,attemptId,now) }
488: 
489:         return mailRecordId
490:     }
```

## E12 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:755
```text
755:         )
756: 
757:         val payload = ManualReplySendAttemptService.SendPayload(
758:             orcidId = contact.orcidId,
759:             contactId = source.contactId,
760:             inboundProcessingId = source.inboundProcessingId,
761:             accountCode = source.accountCode,
762:             normalizedRecipient = contact.expertEmail.lowercase().trim(),
763:             subject = renderedSubject,
764:             finalText = finalTextBody,
765:             finalHtml = finalHtmlBody,
766:             inReplyTo = source.persistInReplyTo,
767:             canonicalQaRuleIds = evidence.canonicalFactIds,
768:             primaryRuleId = if (evidence.ragMode) null else evidence.canonicalFactIds.firstOrNull(),
769:             // I-3：会话回信锚点只以真实 mail_record.id 表达，绝不伪造 inbound id。
770:             sourceAnchor = if (source.inboundProcessingId == null) {
771:                 requireNotNull(source.anchorMailRecordId) {
772:                     "Conversation rich reply requires a real anchor mail record"
773:                 }.let { "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$it" }
774:             } else null,
775:             idempotencyRequestId = source.requestId,
776:             // 03 (I-3): 与 ComposedMail 共用同一 01 快照实例，绝不独立生成第二份。
777:             calendarAttachment = calendarSnapshot,
778:             // fast-p 02 (I-1)：同一次重算产出的结构化排期输入；无会议恒 null。
779:             meetingEvent = meetingCalendarInput,
780:             // 06 (I-1)：与 ComposedMail.outboundAttachments 同一文件集合导出的有序快照；
781:             // 05 的 finalize 四分支据此写 mail_record.outbound_attachments_json。
782:             outboundAttachments = attachmentFileSet.snapshots
783:         ).also { it.draftSendRef = draftSendRef }
784: 
785:         val persistInReplyTo = source.persistInReplyTo
786:         if (persistInReplyTo != null && persistInReplyTo.length > 255) {
787:             throw ResponseStatusException(
788:                 HttpStatus.UNPROCESSABLE_ENTITY,
789:                 "inReplyTo exceeds 255 characters"
790:             )
791:         }
792: 
793:         // I-5: 幂等占位（prepareAndClaim）之前必须判抑制，禁止把发送尝试烧成 DELIVERY_UNKNOWN。
794:         if (emailSuppressionService.isSuppressed(contact.expertEmail)) {
795:             throw ResponseStatusException(
796:                 HttpStatus.BAD_REQUEST,
797:                 "收件人已退订，禁止外发：${contact.expertEmail}"
798:             )
799:         }
800: 
801:         // Preserve original risk/attachment/suppression priority. Only an exact completed
802:         // non-draft inbound payload may bypass a newly introduced identity reminder.
803:         if (draftSendRef == null && source.inboundProcessingId != null) {
804:             manualReplySendAttemptService.findCompletedByPayload(payload)?.let {
805:                 return completedDraftResult(source.contactId, it)
806:             }
807:         }
808:         if (identityFindings.isNotEmpty() && !safetyWarningConfirmed) {
809:             throw ManualSendSafetyBlockedException(findings)
810:         }
811: 
812:         val claim = manualReplySendAttemptService.prepareAndClaim(payload)
813: 
814:         return when (claim.result) {
815:             ManualReplySendAttemptService.ClaimResult.CLAIMED,
816:             ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED -> {
817:                 val mail = ComposedMail(
818:                     to = contact.expertEmail,
819:                     subject = renderedSubject,
820:                     body = finalHtmlBody,
821:                     html = true,
822:                     text = finalTextBody,
823:                     messageId = claim.messageId,
824:                     // 03 (I-2/I-3): 带会议日历的新分支用真实来信 processing.messageId 作
825:                     // SMTP 线程头（inReplyTo/references 同一来源）；会议只由来信路径产生，
826:                     // 无会议时回落该路径原形态（来信 SMTP 头恒 null；会话回信 = 锚点头），
827:                     // 不因本合并顺带改变旧邮件线程形态。
828:                     inReplyTo = if (calendarSnapshot != null) source.inboundRecord?.messageId
829:                         else source.smtpInReplyTo,
830:                     references = if (calendarSnapshot != null) source.inboundRecord?.messageId
831:                         else source.smtpReferences,
832:                     calendarAttachment = calendarSnapshot,
833:                     // 06 (I-1)：同一文件集合的原件（已核尺寸/hash），SMTP 在 ICS 之后按
834:                     // 选择顺序以 multipart/mixed 携带。
835:                     outboundAttachments = attachmentFileSet.files,
836:                     isReply = true
837:                 )
838:                 val bodyPreviewText = (finalTextBody.ifBlank { mailBodyCleaner.clean(finalHtmlBody) }
839:                     .takeIf { it.isNotBlank() } ?: mailBodyCleaner.clean(finalHtmlBody)).take(500)
840: 
841:                 try {
842:                     val delivered = mailDeliveryService.send(source.account, mail)
843:                     val classification = classifyDelivery(delivered)
844: 
845:                     if (classification.isSent) {
846:                         val mailRecordId = try {
847:                             val id = manualReplySendAttemptService.finalizeSuccess(
848:                                 payload = payload,
849:                                 attemptId = claim.attemptId,
850:                                 messageId = claim.messageId
851:                             )
852:                             if (source.inboundProcessingId != null) {
853:                                 manualReplySendAttemptService.recordSendAudit(
854:                                     inboundProcessingId = source.inboundProcessingId,
855:                                     contactId = source.contactId,
856:                                     mailRecordId = id,
857:                                     canonicalFactIds = evidence.canonicalFactIds,
858:                                     carriesQa = evidence.carriesQa,
859:                                     delivered = delivered,
860:                                     sendSubject = renderedSubject,
861:                                     bodyPreviewText = bodyPreviewText,
862:                                     operatorName = operatorName,
863:                                     inboundRecord = requireNotNull(source.inboundRecord) {
864:                                         "Inbound rich reply audit requires the inbound processing record"
865:                                     },
```

## E13 — src/main/resources/static/app.js:21080
```text
21080: 
21081: // I-1/I-2：reader 状态只存在于本页内存（active/username/handle），不写任何 Storage。
21082: function mailReadOnlyState() {
21083:     if (!state.mailReadOnly) {
21084:         state.mailReadOnly = { active: false, username: null, handle: null };
21085:     }
21086:     return state.mailReadOnly;
21087: }
21088: 
21089: function isMailReadOnlyMode() {
21090:     return mailReadOnlyState().active === true;
21091: }
21092: 
21093: // I-2：已知 reader 时只允许两条会话 GET 与 auth login/me/logout，其余请求在 fetch 前拒绝。
21094: function isMailReadOnlyRequestAllowed(path, options) {
21095:     const raw = String(path ?? "");
21096:     const normalized = raw.split("?")[0];
21097:     const method = String((options && options.method) || "GET").toUpperCase();
21098:     if (method === "GET" && normalized === "/api/auth/me") return true;
21099:     if (method === "POST" && (normalized === "/api/auth/login" || normalized === "/api/auth/logout")) return true;
21100:     if (method !== "GET") return false;
21101:     if (normalized === "/api/mail/mailbox/conversations") return true;
21102:     return /^\/api\/mail\/mailbox\/conversations\/[^/]+\/messages$/.test(normalized);
21103: }
21104: 
21105: function showMailReadOnlyIdentityError() {
21106:     $("#loginOverlay").hidden = false;
21107:     $("#changePasswordOverlay").hidden = true;
21108:     const errDiv = $("#loginError");
21109:     if (errDiv) {
21110:         errDiv.textContent = MAIL_READ_ONLY_COPY.identityError;
21111:         errDiv.hidden = false;
21112:     }
21113: }
21114: 
21115: // S-3：复用既有应用壳 —— 只留「收发件箱」，重建手机下拉，标记 body 与正文面板标题。
21116: function applyMailReadOnlyShell() {
21117:     const body = document.body;
21118:     if (body && body.dataset) body.dataset.mailReadOnly = "true";
21119:     else if (body && typeof body.setAttribute === "function") body.setAttribute("data-mail-read-only", "true");
21120:     const mailboxView = document.getElementById("view-mailbox");
21121:     if (mailboxView && mailboxView.classList) mailboxView.classList.add("mc-refined");
21122:     $$(".nav-tab").forEach((tab) => {
21123:         if (!tab.classList) return;
21124:         const isMailbox = Boolean(tab.dataset) && tab.dataset.view === "mailbox";
21125:         tab.classList.toggle("active", isMailbox);
21126:     });
21127:     $$(".view").forEach((section) => {
21128:         if (!section.classList) return;
21129:         section.classList.toggle("active", section.id === "view-mailbox");
21130:     });
21131:     const panelTitle = document.querySelector("#mailboxConversationPanel > .panel-head h2");
21132:     if (panelTitle) panelTitle.textContent = "收发邮件 · 只读";
21133:     const menu = document.getElementById("mobileCoreView");
21134:     if (!menu) return;
21135:     if (typeof menu.replaceChildren === "function") menu.replaceChildren();
21136:     else menu.innerHTML = "";
21137:     if (typeof document.createElement !== "function") return;
21138:     const option = document.createElement("option");
21139:     option.value = "mailbox";
21140:     option.textContent = "收发件箱";
21141:     menu.appendChild(option);
21142:     menu.value = "mailbox";
21143: }
21144: 
21145: function renderMailReadOnlyLoadError(host, message) {
21146:     if (!host) return;
21147:     if (typeof document.createElement !== "function" || typeof host.replaceChildren !== "function") {
21148:         host.textContent = message;
21149:         return;
21150:     }
21151:     const node = document.createElement("p");
21152:     node.className = "mc-error";
21153:     node.setAttribute("role", "alert");
21154:     node.textContent = message;
21155:     host.replaceChildren(node);
21156: }
21157: 
21158: // I-2：模块缺失只显示固定错误，绝不回退到 MailboxChat/旧账号表格。
21159: function startMailReadOnlyApp(username) {
21160:     const readerState = mailReadOnlyState();
21161:     readerState.active = true;
21162:     readerState.username = username;
21163:     readerState.handle = null;
21164:     state.view = "mailbox";
21165:     applyMailReadOnlyShell();
21166:     const host = $("#mailboxList");
21167:     const module = typeof window !== "undefined" && window.MailboxReadOnly ? window.MailboxReadOnly : null;
21168:     if (!module || typeof module.mount !== "function") {
21169:         renderMailReadOnlyLoadError(host, MAIL_READ_ONLY_COPY.moduleError);
21170:         return;
21171:     }
21172:     let handle = null;
21173:     try {
21174:         handle = module.mount(host, { api, sessionUser: { username } });
21175:     } catch (error) {
21176:         handle = null;
21177:     }
21178:     if (!handle) {
21179:         renderMailReadOnlyLoadError(host, MAIL_READ_ONLY_COPY.moduleError);
21180:         return;
21181:     }
21182:     readerState.handle = handle;
21183: }
21184: 
21185: // I-3：注销/停止时立即拆模块（abort 在途请求、清正文与监听）。
```

## E14 — src/main/resources/static/mailbox-readonly.js:137
```text
137:         // ---- S-1 DOM 骨架（静态壳一次构建，动态文案全部 textContent） ----------------
138:         var root = makeNode(doc, "div", "mail-chat");
139:         root.setAttribute("data-role", "readonly-mailbox");
140: 
141:         var aside = makeNode(doc, "aside", "mc-experts");
142:         aside.setAttribute("aria-label", "已授权专家");
143: 
144:         var tools = makeNode(doc, "div", "mc-list-tools");
145:         var searchRow = makeNode(doc, "div", "mc-search-row");
146:         var searchInput = makeNode(doc, "input");
147:         searchInput.setAttribute("type", "search");
148:         searchInput.setAttribute("aria-label", "搜索已授权专家");
149:         searchInput.setAttribute("placeholder", "搜索专家姓名");
150:         var searchButton = buttonNode(doc, "search", "搜索");
151:         searchRow.appendChild(searchInput);
152:         searchRow.appendChild(searchButton);
153:         var refreshButton = buttonNode(doc, "refresh", "刷新");
154:         var note = makeNode(doc, "p", "mc-note");
155:         setText(note, "只读：仅显示管理员授权的专家邮件");
156:         tools.appendChild(searchRow);
157:         tools.appendChild(refreshButton);
158:         tools.appendChild(note);
159: 
160:         var expertList = makeNode(doc, "div", "mc-expert-list");
161: 
162:         var pager = makeNode(doc, "div", "mc-pager");
163:         var previousButton = buttonNode(doc, "previous", "上一页");
164:         var pagerLabel = makeNode(doc, "span");
165:         pagerLabel.setAttribute("aria-live", "polite");
166:         setText(pagerLabel, "第 1 页，共 0 位专家");
167:         var nextButton = buttonNode(doc, "next", "下一页");
168:         pager.appendChild(previousButton);
169:         pager.appendChild(pagerLabel);
170:         pager.appendChild(nextButton);
171: 
172:         aside.appendChild(tools);
173:         aside.appendChild(expertList);
174:         aside.appendChild(pager);
175: 
176:         var conversation = makeNode(doc, "section", "mc-conversation");
177:         conversation.setAttribute("aria-label", "往来邮件");
178:         var header = makeNode(doc, "header", "mc-header");
179:         var identity = makeNode(doc, "div", "mc-identity");
180:         var identityName = makeNode(doc, "h2");
181:         identity.appendChild(identityName);
182:         var readOnlyBadge = makeNode(doc, "span", "mc-badge");
183:         setText(readOnlyBadge, "只读");
184:         header.appendChild(identity);
185:         header.appendChild(readOnlyBadge);
186:         var scroll = makeNode(doc, "div", "mc-scroll");
187:         conversation.appendChild(header);
188:         conversation.appendChild(scroll);
189: 
190:         root.appendChild(aside);
191:         root.appendChild(conversation);
192: 
193:         // ---- 请求（AbortController + 15s 超时；signal 沿既有 api 传递） -------------
194:         function requestJson(path) {
195:             var controller = new AbortController();
196:             var timedOut = false;
197:             var timer = setTimeout(function () {
198:                 timedOut = true;
199:                 controller.abort();
200:             }, REQUEST_TIMEOUT_MS);
201:             inflight.add(controller);
202:             var settle = function () {
203:                 clearTimeout(timer);
204:                 inflight.delete(controller);
205:             };
206:             return Promise.resolve()
207:                 .then(function () {
208:                     return api(path, { method: "GET", cache: "no-store", signal: controller.signal });
209:                 })
210:                 .then(function (data) {
211:                     settle();
212:                     return data;
213:                 }, function (error) {
214:                     settle();
215:                     // 跨 realm 的 Error 不保证 instanceof，保留原始错误对象上的 status/data。
216:                     var failure = error && typeof error === "object" ? error : new Error(String(error));
217:                     failure.timedOut = timedOut;
218:                     throw failure;
219:                 });
220:         }
221: 
222:         function isStale(gen, seq, seqRef) {
```

## E15 — src/main/resources/static/mailbox-chat.js:5800
```text
5800:             const ui = meetingEnabled() && !isOutbound;
5801:             let editorContent = "";
5802:             if (draft && draft.html && String(draft.html).trim()) {
5803:                 editorContent = meetingRestoreEditorHtml(String(draft.html));
5804:                 if (!meetingLib() || typeof meetingLib().sanitizeDraftHtml !== "function") {
5805:                     draftRecord(targetKey).error = "load";
5806:                     editorContent = escapeText(editorText);
5807:                 }
5808:             } else if (editorText) {
5809:                 editorContent = escapeText(editorText);
5810:             }
5811:             const meetingTrigger = ui ? meetingTriggerHtml() : "";
5812:             // S-2：材料索取紧随会议按钮之后，仍在跟进按钮之前。
5813:             const materialTrigger = ui ? materialRequestTriggerHtml() : "";
5814:             // S-1（fast-p 01 I-1）：引用模板入口只属于有真实来信的人工回复（outbound=false），
5815:             // 不依赖会议组件；固定在材料索取之后、跟进按钮之前。
5816:             const templateReferenceTrigger = isOutbound
5817:                 ? ""
5818:                 : `<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>`;
5819:             // S-1：跟进按钮只在当前专家确有成功发件时渲染，固定紧随既有会议按钮之后，
5820:             // class 严格为 button（不新增按钮 class、不改会议按钮顺序）。
5821:             const followUpButton = Number(instance.selectedSummary && instance.selectedSummary.sentCount) > 0
5822:                 ? `<button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>`
5823:                 : "";
5824:             // S-3：草稿携带跟进锚点时，在 .mc-editor 之后、会议附件之前显示所选邮件提示。
5825:             const anchorNote = draft && draft.followUpAnchorMailRecordId != null
5826:                 ? followupAnchorNoteHtml(draft.followUpAnchorMailRecordId)
5827:                 : "";
5828:             const meetingAttachment = ui && meetingCardContainerHtml(draft && draft.meeting ? draft.meeting : null) || "";
5829:             // fast-p 07（S-2）：通用附件草稿卡放在会议附件卡之后、发送 footer 之前。
5830:             const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
5831:             return `
5832:                 <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
5833:                     <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
5834:                     <div class="mc-editor-tools">
5835:                         <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
5836:                         <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
5837:                         <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
5838:                         <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
5839:                         <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
5840:                         <input type="file" data-role="outbound-file-input" multiple hidden>
5841:                         ${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
5842:                         <button class="button" type="button" data-action="mc-complete-reply-identity">补全称呼与签名</button>
5843:                     </div>
5844:                     <div class="reply-identity-defaults"><span>全局默认</span><strong data-role="reply-identity-default-summary">全局默认：加载中…</strong><button class="reply-identity-settings-link" type="button" data-action="mc-reply-identity-settings">默认设置</button></div>
5845:                     <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
5846:                     ${anchorNote}
5847:                     ${meetingAttachment}
5848:                     ${outboundFiles}
5849:                     <div class="mc-error" data-role="draft-load-error" role="alert" hidden>草稿加载失败，请重试</div>
5850:                     <button class="button" type="button" data-action="mc-reload-draft" data-role="draft-load-retry" hidden>重试加载</button>
5851:                     <div class="mc-note" data-role="draft-target-warning" hidden></div>
5852:                     <button class="button" type="button" data-action="mc-retarget-draft" hidden>为新来信另建草稿</button>
5853:                     <button class="button" type="button" data-action="mc-open-existing-draft" hidden>打开已有草稿</button>
5854:                     <div class="mc-note" data-role="draft-send-unknown" hidden>发送结果待确认，草稿已保留，请勿重复发送</div>
5855:                     <div class="mailbox-draft-status-row">
5856:                         <span class="mailbox-draft-status" data-role="draft-save-status" data-state="dirty" role="status" aria-live="polite">尚未保存</span>
5857:                         <button class="mc-text-button" type="button" data-action="mc-retry-draft" hidden>重试保存</button>
5858:                         <button class="mc-text-button" type="button" data-action="mc-reload-draft" hidden>重新加载</button>
5859:                         <button class="button" type="button" data-action="mc-continue-draft" hidden>继续编辑并保存</button>
5860:                         <button class="button danger" type="button" data-action="mc-discard-draft">放弃草稿</button>
5861:                     </div>
5862:                     <div class="mc-compose-footer">
5863:                         <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
5864:                         ${templateFollowButton}
5865:                         <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
5866:                     </div>
5867:                 </div>
5868:             `;
5869:         }
5870: 
```

## E16 — src/main/resources/static/index.html:788
```text
788:             </div>
789: 
790:             <!-- B4 (S2b-3): 按任务执行过滤的提示条；位于既有 .toolbar 之下、不与标题栏「批量发送」按钮同行 -->
791:             <div id="mailboxExecutionFilterBar" class="toolbar" hidden>
792:                 <span class="text-muted" id="mailboxExecutionFilterText"></span>
793:                 <button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>
794:             </div>
795: 
796:             <section class="panel" id="mailboxConversationPanel">
797:                 <div class="panel-head">
798:                     <h2>已激活账号收发邮件记录</h2>
799:                     <div class="panel-head-actions">
800:                         <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
801:                         <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
802:                         <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
803:                         <button class="button" id="mailboxGroupPushBtn" type="button"
804:                                 role="switch" aria-checked="false" aria-label="企业微信群消息推送" disabled>
805:                             <span>群消息</span><span id="mailboxGroupPushState">加载中…</span>
806:                             <span class="mailbox-push-track" aria-hidden="true"></span>
807:                         </button>
808:                     </div>
809:                 </div>
810:                 <div class="mailbox-list" id="mailboxList"></div>
811:                 <div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
812:             </section>
813: 
814:             <section class="panel" id="unmatchedDetailPanel" hidden style="margin-top: 16px;">
815:                 <div class="panel-head">
```

## E17 — src/main/resources/static/mailbox-chat.css:1
```text
1: .mail-chat{display:grid;grid-template-columns:306px minmax(0,1fr);gap:16px;min-height:480px;height:calc(100dvh - 216px);color:#475569;font-size:12px;line-height:1.6}
2: .mail-chat *{box-sizing:border-box}
3: .mail-chat [hidden]{display:none!important}
4: .mail-chat :is(h2,h3,p){margin:0}
5: .mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
6: .mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
7: .mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
8: .mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
9: .mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
10: .mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
11: .mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
12: .mail-chat .mc-filter:active{background:#dbeafe}
13: .mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
14: .mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
15: .mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
16: .mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
17: .mail-chat .mc-person:hover{background:#eff5ff}
18: .mail-chat .mc-person[data-active=true]{border-color:#c2d3f2;border-left-color:#3c65cd;background:#eaf1ff}
19: .mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
20: .mail-chat .mc-person-main:active{opacity:.85}
21: .mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
22: .mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
23: .mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
24: .mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
25: .mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
26: .mail-chat .mc-follow:active{background:#fde68a}
27: .mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
28: .mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
29: .mail-chat .mc-badge{display:inline-flex;align-items:center;padding:1px 7px;border:1px solid #dce4ef;border-radius:12px;background:#f1f5f9;color:#64748b;font-size:11px;white-space:nowrap}
30: .mail-chat .mc-badge[data-tone=pending]{background:#fff7ed;border-color:#fed7aa;color:#b45309}
31: .mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
32: .mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
33: .mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
34: .mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
35: .mail-chat .mc-identity{flex:1;min-width:180px}
36: .mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
37: .mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
38: .mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
39: .mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
40: .mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
41: .mail-chat .mc-load-older{align-self:center}
42: .mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
43: .mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
44: .mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
45: .mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
46: .mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
47: .mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
48: .mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
49: .mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
50: .mail-chat .mc-mail-extras summary{cursor:pointer}
51: .mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
52: .mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
53: .mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
54: .mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
55: .mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
56: .mail-chat .mc-section-content{padding:14px;min-width:0}
57: .mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
58: .mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
59: .mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
60: .mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
61: .mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
62: .mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
63: .mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
64: .mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
65: .mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
66: .mail-chat .button{white-space:nowrap}
67: .mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
68: .mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
69: @media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
70: @media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
71: @media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
72: /* S-1..S-6: mailbox only; retain production shell. */
73: #view-mailbox.mc-refined #mailboxLegacyToolbar{display:none}
74: #view-mailbox.mc-refined #mailboxConversationPanel{overflow:visible}
75: #view-mailbox.mc-refined #mailboxList{padding:16px}
76: #view-mailbox:not(.mc-refined) .mc-filter-fields{display:contents}
77: .mail-chat .mc-experts{overflow:visible;position:relative}
78: .mail-chat .mc-search-row{position:relative;display:flex;align-items:center;gap:7px}
79: .mail-chat .mc-search-row input{flex:1;min-width:0;height:36px;min-height:36px}
80: .mail-chat .mc-icon{display:inline-flex;align-items:center;justify-content:center;flex:none;min-width:36px;height:36px;padding:0 8px;border:1px solid #d8e1ef;border-radius:8px;background:#fff;color:#6482b2;font:inherit;font-size:20px;cursor:pointer;gap:4px}
81: .mail-chat .mc-icon:hover{background:#edf3ff;border-color:#93b4ec}
82: .mail-chat .mc-icon:active{background:#dbeafe}
83: .mail-chat .mc-icon[aria-expanded=true]{background:#eaf1ff;border-color:#7396df;color:#2451b9}
84: .mail-chat .mc-icon:disabled{opacity:.45;cursor:not-allowed}
85: .mail-chat .mc-filter-count{padding:0 4px;border-radius:8px;background:#2553c5;color:#fff;font-size:10px;line-height:16px}
86: .mail-chat .mc-filters{gap:12px;flex-wrap:nowrap}
87: .mail-chat .mc-filter{border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;padding:6px 5px 10px}
88: .mail-chat .mc-filter[aria-pressed=true]{border-bottom-color:#2e59c7;background:transparent;color:#244ca9}
89: .mail-chat .mc-filter-summary{display:flex;align-items:center;gap:8px;color:#7890b3;font-size:11px}
90: .mail-chat .mc-filter-popover{position:absolute;top:44px;left:0;z-index:var(--z-dropdown);width:430px;max-width:calc(100vw - 48px);max-height:calc(100dvh - 210px);overflow:auto;background:#fff;border:1px solid #dce5f1;border-radius:12px;box-shadow:0 14px 50px #20395d26}
91: .mail-chat .mc-filter-popover header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f7;font-size:14px;color:#475d79}
92: .mail-chat .mc-filter-popover .mc-filter-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;padding:18px 20px}
93: .mail-chat .mc-field{display:flex;flex-direction:column;gap:7px;min-width:0;color:#8494aa;font-size:11px}
94: .mail-chat .mc-field-wide{grid-column:1/-1}
95: .mail-chat .mc-field input,.mail-chat .mc-field select{width:100%;min-width:0;height:34px;min-height:34px;margin:0;padding:0 9px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#5f7390;font:inherit;font-size:12px}
96: .mail-chat .mc-filter-popover footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
97: .mail-chat .mc-filter-popover footer .mc-text-button{margin-right:auto}
98: .mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
99: .mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
100: .mail-chat .mc-close:active{background:#dbeafe}
101: .mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
102: .mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
103: .mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
104: .mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
105: .mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
106: .mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
107: .mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
108: .mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
109: .mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
110: .mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
111: .mail-chat .mc-text-button:active{background:#dbeafe}
112: .mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
113: .mail-chat .mc-message{width:min(92%,820px);padding:14px 17px;border-radius:11px;box-shadow:0 2px 6px #334b7210}
114: .mail-chat .mc-message h3{font-size:12px;color:#4d617d;font-weight:600;margin-bottom:11px;line-height:1.55}
115: .mail-chat .mc-body{font-size:13px;line-height:1.55;color:#465974}
116: .mail-chat .mc-message footer{justify-content:flex-start;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
117: .mail-chat .mc-process{margin-left:auto;border:1px solid #dce4ef;border-radius:7px;padding:3px 9px;white-space:nowrap}
118: .mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
119: .mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
120: .mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
121: .mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
122: .mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
123: .mail-chat .mc-section{background:#fff;border-radius:10px}
124: .mail-chat .mc-section>summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:12px;font-weight:600;color:#506783}
125: .mail-chat .mc-section>summary::-webkit-details-marker{display:none}
126: .mail-chat .mc-section>summary::after{content:'⌄';margin-left:auto;color:#91a1b7}
127: .mail-chat .mc-section[open]>summary::after{content:'⌃'}
128: .mail-chat .mc-section>summary:hover{background:#f5f8ff}
129: .mail-chat .mc-section>summary:active{background:#edf3ff}
130: .mail-chat .mc-editor{min-height:100px;max-height:240px}
131: .mail-chat .mc-manage-overlay{position:fixed;inset:0;z-index:990;display:flex;align-items:center;justify-content:center;padding:16px;background:#172c4738;backdrop-filter:blur(2px)}
132: .mail-chat .mc-manage-dialog{width:480px;max-width:100%;max-height:85dvh;overflow:auto;background:#fff;color:#475d79;border:1px solid #d9e3f1;border-radius:14px;box-shadow:0 20px 90px #17325730}
133: .mail-chat .mc-manage-dialog header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 20px;border-bottom:1px solid #edf1f7}
134: .mail-chat .mc-manage-dialog h3{font-size:16px;font-weight:600}
135: .mail-chat .mc-manage-dialog header p{margin-top:6px;color:#96a6ba;font-size:11px}
136: .mail-chat .mc-status-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px;padding:22px 20px}
137: .mail-chat .mc-settings-tags{padding:0 20px 20px}
138: .mail-chat .mc-settings-tags .expert-tag-editor{margin:0;padding:0;border:0;background:transparent;box-shadow:none}
139: .mail-chat .mc-settings-tags .tag-editor-loading{min-height:0}
140: .mail-chat .mc-settings-tags .inbound-tag-editor-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 12px}
141: .mail-chat .mc-settings-tags .inbound-tag-editor-head h3{font-size:12px}
142: .mail-chat .mc-settings-tags .inbound-tag-editor-chips{display:flex;flex-wrap:wrap;gap:6px}
143: .mail-chat .mc-settings-tags .expert-tag{font-size:11px;line-height:1.6;padding:2px 7px;border-radius:5px;margin:0}
144: .mail-chat .mc-manage-note{padding:0 20px 16px;color:#8b9bb1;font-size:11px;line-height:1.7}
145: .mail-chat .mc-dialog-actions{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
146: .mail-chat .mc-dialog-actions .button{width:auto;flex:none}
147: .mail-chat .button{height:32px;min-height:32px;padding:0 11px;font-size:12px;border-radius:7px}
148: .mail-chat .button:not(.primary){box-shadow:none}
149: .mail-chat .mc-manage-dialog .mc-inline-error,.mail-chat .mc-filter-popover .mc-inline-error{padding:0 20px 12px}
150: @media(max-width:1100px){.mail-chat{grid-template-columns:275px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:15px}.mail-chat .mc-timeline-head{padding:10px 15px}.mail-chat .mc-message{width:96%}.mail-chat .mc-header .button{font-size:11px;padding:0 8px}}
151: @media(max-width:760px){#view-mailbox.mc-refined #mailboxList{padding:12px}.mail-chat{height:auto;min-height:0}.mail-chat .mc-search-row{position:static}.mail-chat .mc-filter-popover{position:fixed;top:100px;left:16px;width:calc(100vw - 32px);max-width:none;max-height:calc(100dvh - 116px)}.mail-chat .mc-scroll{max-height:65dvh;overflow:auto}.mail-chat .mc-message{width:100%}.mail-chat .mc-timeline-head{padding:10px 12px}.mail-chat .mc-position-hint{display:none}.mail-chat .mc-status-grid{grid-template-columns:1fr}.mail-chat .mc-manage-dialog{max-height:calc(100dvh - 32px)}}
152: @media(prefers-reduced-motion:reduce){.mail-chat *{scroll-behavior:auto!important;transition:none!important}}
153: 
154: .mail-chat.mc-overlay-root{display:contents}
155: 
156: /* S-7: expert tags in the list, one line only. */
157: .mail-chat .mc-person-heading{display:flex;align-items:center;gap:6px;min-width:0}
158: .mail-chat .mc-person-heading strong{flex:1;min-width:0}
159: .mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
160: .mail-chat .mc-person-counts{flex:none;white-space:nowrap;font-size:11px;color:#79899f}
161: .mail-chat .mc-person-tags{display:block;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;line-height:20px;color:#6482b2;cursor:help}
162: .mail-chat .mc-person-tag{display:inline;padding:2px 5px;margin-right:4px;border:1px solid #d5e2fb;border-radius:5px;background:#edf3ff;color:#5476ba;font-size:10px;line-height:16px;white-space:nowrap}
163: .mail-chat .mc-person-tags:hover .mc-person-tag{background:#e6efff;border-color:#b8cef3}
164: .mail-chat .mc-person-tags-unavailable{color:#94a3b8;font-size:10px}
165: 
```

## E18 — src/main/resources/static/mailbox-chat.css:201
```text
201: .mc-note-dialog .button{height:30px;min-height:30px;padding:4px 12px;font-size:12px}
202: .mc-note-dialog button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
203: .mc-note-dialog button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
204: /* mailbox-expert-note:S2:end */
205: 
206: /* mailbox-expert-note:S3 */
207: @media(max-width:760px){.mail-chat .mc-note-row{flex-wrap:wrap}.mail-chat .mc-note-row .mc-note-slot{flex-basis:100%}.mail-chat .mc-note-row .contact-timing{margin-left:0}.mail-chat .mc-note-trigger{min-height:36px}.mc-note-dialog textarea{font-size:16px}.mc-note-dialog footer{flex-wrap:wrap}.mc-note-dialog .button{min-height:36px;height:36px}}
208: /* mailbox-expert-note:S3:end */
209: 
210: body[data-mail-read-only="true"] .nav-tab[data-view]:not([data-view="mailbox"]),
211: body[data-mail-read-only="true"] .view:not(#view-mailbox),
212: body[data-mail-read-only="true"] #mailboxLegacyToolbar,
213: body[data-mail-read-only="true"] #mailboxExecutionFilterBar,
214: body[data-mail-read-only="true"] #mailboxConversationPanel > .panel-head .panel-head-actions,
215: body[data-mail-read-only="true"] #mailboxPagination,
216: body[data-mail-read-only="true"] #unmatchedDetailPanel,
217: body[data-mail-read-only="true"] #worldClockTrigger,
218: body[data-mail-read-only="true"] #worldClockPanel {
219:     display: none !important;
220: }
221: body[data-mail-read-only="true"] .mail-chat .mc-person {
222:     grid-template-columns: minmax(0, 1fr);
223: }
```

## E19 — src/main/resources/static/meeting-confirmation.js:240
```text
240:         return list.some(function (zone) {
241:             return zone && zone.countryCode != null && String(zone.countryCode) !== "";
242:         });
243:     }
244: 
245:     // ------------------------------------------------------------------
246:     // 草稿/正文 html 白名单清洗（I-6：template.content 遍历；无正则清 html）
247:     // ------------------------------------------------------------------
248: 
249:     var ALLOWED_TAGS = {
250:         div: true, p: true, br: true, b: true, strong: true,
251:         i: true, em: true, u: true, ul: true, ol: true, li: true, a: true, span: true
252:     };
253:     var REMOVE_TAGS = { script: true, style: true, iframe: true, object: true };
254: 
255:     function allowedLinkHref(href) {
256:         var value = String(href == null ? "" : href).trim();
257:         if (/^https?:\/\//i.test(value) || /^mailto:/i.test(value)) return value;
258:         return null;
259:     }
260: 
261:     /** 递归清洗：把 node 清洗后的节点 append 进 out。script/style/iframe/object
262:      *  连内容删除；其它非白名单标签 unwrap；a 只保留校验过协议 href + target/rel；
263:      *  div 只保留 meeting-body-block + data-meeting-block=true，其余属性全删。 */
264:     function sanitizeNode(node, doc, out) {
265:         if (!node) return;
266:         if (node.nodeType === 3) {
267:             if (node.data) out.push(doc.createTextNode(String(node.data)));
268:             return;
269:         }
270:         if (node.nodeType !== 1) return;
271:         var tag = String(node.tagName || "").toLowerCase();
272:         if (REMOVE_TAGS[tag]) return;
273:         var children = node.childNodes ? Array.prototype.slice.call(node.childNodes) : [];
274:         if (!ALLOWED_TAGS[tag]) {
275:             for (var i = 0; i < children.length; i += 1) sanitizeNode(children[i], doc, out);
276:             return;
277:         }
278:         var el = doc.createElement(tag);
279:         if (tag === "a") {
280:             var href = allowedLinkHref(node.getAttribute ? node.getAttribute("href") : "");
281:             if (href) {
282:                 el.setAttribute("href", href);
283:                 el.setAttribute("target", "_blank");
284:                 el.setAttribute("rel", "noopener noreferrer");
285:             }
286:         } else if (tag === "div") {
287:             var cls = node.getAttribute ? node.getAttribute("class") || "" : "";
288:             var blockAttr = node.getAttribute ? node.getAttribute("data-meeting-block") || "" : "";
289:             if (cls.split(/\s+/).indexOf("meeting-body-block") !== -1 && String(blockAttr) === "true") {
290:                 el.setAttribute("class", "meeting-body-block");
291:                 el.setAttribute("data-meeting-block", "true");
292:             }
293:         }
294:         for (var j = 0; j < children.length; j += 1) {
295:             var bucket = [];
296:             sanitizeNode(children[j], doc, bucket);
297:             for (var k = 0; k < bucket.length; k += 1) el.appendChild(bucket[k]);
298:         }
299:         out.push(el);
300:     }
301: 
302:     /** 清洗整段 draft html；返回可安全 innerHTML 的字符串。 */
303:     function sanitizeDraftHtml(html, documentRef) {
304:         var doc = documentRef || docRoot();
305:         var input = String(html == null ? "" : html);
306:         if (!doc || !input) return "";
307:         var template = doc.createElement("template");
308:         template.innerHTML = input;
309:         var root = (template.content && template.content.childNodes) ? template.content : template;
310:         var out = doc.createElement("div");
311:         var children = root.childNodes ? Array.prototype.slice.call(root.childNodes) : [];
312:         for (var i = 0; i < children.length; i += 1) {
313:             var bucket = [];
314:             sanitizeNode(children[i], doc, bucket);
315:             for (var k = 0; k < bucket.length; k += 1) out.appendChild(bucket[k]);
316:         }
317:         return out.innerHTML;
318:     }
319: 
```

## E20 — pom.xml:160
```text
160:                     <dependency>
161:                         <groupId>org.jetbrains.kotlin</groupId>
162:                         <artifactId>kotlin-maven-allopen</artifactId>
163:                         <version>${kotlin.version}</version>
164:                     </dependency>
165:                 </dependencies>
166:             </plugin>
167:             <plugin>
168:                 <groupId>org.springframework.boot</groupId>
169:                 <artifactId>spring-boot-maven-plugin</artifactId>
170:                 <configuration>
171:                     <mainClass>com.weibo.talentintroduction.TalentIntroductionApplicationKt</mainClass>
172:                 </configuration>
173:             </plugin>
174:             <plugin>
175:                 <groupId>org.apache.maven.plugins</groupId>
176:                 <artifactId>maven-surefire-plugin</artifactId>
177:                 <configuration>
178:                     <systemPropertyVariables>
179:                         <migrationIt>${migrationIt}</migrationIt>
180:                         <mysqlIt>${mysqlIt}</mysqlIt>
181:                     </systemPropertyVariables>
182:                     <environmentVariables>
183:                         <DYLD_LIBRARY_PATH>/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib</DYLD_LIBRARY_PATH>
184:                     </environmentVariables>
185:                 </configuration>
186:             </plugin>
187:             <plugin>
188:                 <groupId>org.codehaus.mojo</groupId>
189:                 <artifactId>exec-maven-plugin</artifactId>
190:                 <version>3.1.0</version>
191:                 <executions>
192:                     <execution>
193:                         <id>node-test</id>
194:                         <phase>test</phase>
195:                         <goals>
196:                             <goal>exec</goal>
197:                         </goals>
198:                         <configuration>
199:                             <executable>bash</executable>
200:                             <arguments>
201:                                 <argument>-lc</argument>
202:                                 <argument>node --test src/test/js/*.test.js</argument>
203:                             </arguments>
204:                             <skip>${skipNodeTests}</skip>
205:                         </configuration>
206:                     </execution>
207:                     <execution>
208:                         <id>node-check-app</id>
209:                         <phase>test</phase>
210:                         <goals>
211:                             <goal>exec</goal>
212:                         </goals>
213:                         <configuration>
214:                             <executable>node</executable>
215:                             <arguments>
216:                                 <argument>--check</argument>
217:                                 <argument>src/main/resources/static/app.js</argument>
218:                             </arguments>
219:                             <skip>${skipNodeTests}</skip>
220:                         </configuration>
221:                     </execution>
222:                     <execution>
223:                         <id>node-check-task-modal-runtime</id>
224:                         <phase>test</phase>
225:                         <goals>
226:                             <goal>exec</goal>
227:                         </goals>
228:                         <configuration>
229:                             <executable>node</executable>
230:                             <arguments>
231:                                 <argument>--check</argument>
232:                                 <argument>src/main/resources/static/task-modal-runtime.js</argument>
233:                             </arguments>
234:                             <skip>${skipNodeTests}</skip>
235:                         </configuration>
236:                     </execution>
237:                 </executions>
238:             </plugin>
239:         </plugins>
240:     </build>
241: 
242:     <profiles>
243:         <profile>
244:             <id>migration-it</id>
245:             <properties>
246:                 <migrationIt>true</migrationIt>
247:             </properties>
248:         </profile>
249:         <profile>
250:             <id>mysql-it</id>
251:             <properties>
252:                 <mysqlIt>true</mysqlIt>
253:             </properties>
254:         </profile>
255:     </profiles>
```

## E21 — src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt:1
```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import org.springframework.stereotype.Service
4: import java.net.URI
5: 
6: @Service
7: class MailContentService {
8:     fun plainTextToHtml(plain: String, linkedUrls: Collection<String>): String {
9:         if (plain.isBlank()) return ""
10:         val targets = linkedUrls.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
11:         return plain.split(Regex("\\n\\s*\\n"))
12:             .map { paragraph ->
13:                 var inner = escapeHtml(paragraph.trim()).replace("\n", "<br>")
14:                 targets.forEach { url ->
15:                     val escapedUrl = escapeHtml(url)
16:                     inner = inner.replace(escapedUrl, "<a href=\"$escapedUrl\">$escapedUrl</a>")
17:                 }
18:                 "<p>$inner</p>"
19:             }
20:             .joinToString("")
21:     }
22: 
23:     fun plainTextToHtml(plain: String): String = plainTextToHtml(plain, emptyList())
24: 
25:     fun htmlToPlainText(html: String): String =
26:         html.replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), "")
27:             .replace(Regex("(?i)<br\\s*/?>"), "\n")
28:             .replace(Regex("(?i)</p>"), "\n\n")
29:             .replace(Regex("<[^>]+>"), "")
30:             .let { unescapeHtmlEntities(it) }
31:             .replace(Regex("[ \\t]+"), " ")
32:             .replace(Regex("\\n{3,}"), "\n\n")
33:             .trim()
34: 
35:     /**
36:      * 人工富文本专用：CRLF/CR 统一为 LF；普通换行保留，连续空白行最多保留一行。
37:      * 非空行内容逐字保留（不 trim、不合并行内空白）。只服务人工富文本发送（`executeManualRichSend`），
38:      * 不改变 `plainTextToHtml` 与非人工邮件既有的 `\n\n` 段落约定（I-5）。
39:      */
40:     fun normalizeManualTextLineBreaks(text: String): String {
41:         val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
42:         val result = StringBuilder()
43:         var pendingBlankLine = false
44:         normalized.split("\n").forEach { line ->
45:             if (line.all { it == ' ' || it == '\t' }) {
46:                 if (result.isNotEmpty()) pendingBlankLine = true
47:             } else {
48:                 if (result.isNotEmpty()) result.append(if (pendingBlankLine) "\n\n" else "\n")
49:                 result.append(line)
50:                 pendingBlankLine = false
51:             }
52:         }
53:         return result.toString()
54:     }
55: 
56:     /**
57:      * 人工富文本专用：连续 `<br>` 最多保留两个，连续空 `<p>`/`<div>` 块最多保留一个，
58:      * 从而在邮件中保留一行空白。其他标签、属性与文本顺序逐字保留。
59:      */
60:     fun normalizeManualRichHtmlLineBreaks(html: String): String {
61:         var current = html
62:         var passes = 0
63:         while (passes++ < MAX_MANUAL_HTML_PASSES) {
64:             val next = EMPTY_BLOCK_RUN.replace(CONSECUTIVE_BR.replace(current, "<br><br>"), "$1")
65:             if (next == current) return current
66:             current = next
67:         }
68:         return current
69:     }
70: 
71:     /**
72:      * Remove only this application's open-tracking img tags from quoted reply HTML.
73:      * Locate the original tag spans instead of reserializing HTML so unrelated content stays byte-for-byte intact.
74:      */
75:     fun stripOpenTrackingImages(html: String): String {
76:         var searchFrom = 0
77:         var keptThrough = 0
```

## E22 — src/main/kotlin/com/weibo/talentintroduction/mail/controller/TranslationController.kt:1
```text
1: package com.weibo.talentintroduction.mail.controller
2: 
3: import com.weibo.talentintroduction.mail.service.MailTranslationService
4: import org.springframework.web.bind.annotation.PostMapping
5: import org.springframework.web.bind.annotation.RequestBody
6: import org.springframework.web.bind.annotation.RequestMapping
7: import org.springframework.web.bind.annotation.RestController
8: 
9: data class TranslateRequest(val text: String?)
10: 
11: data class TranslateResponse(
12:     val ok: Boolean,
13:     val translatedText: String?,
14:     val reason: String?
15: )
16: 
17: @RestController
18: @RequestMapping("/api/translate")
19: class TranslationController(
20:     private val mailTranslationService: MailTranslationService
21: ) {
22: 
23:     @PostMapping
24:     fun translate(@RequestBody request: TranslateRequest): TranslateResponse {
25:         val result = mailTranslationService.translate(request.text.orEmpty())
26:         return TranslateResponse(
27:             ok = result.ok,
28:             translatedText = result.text,
29:             reason = result.reason
30:         )
31:     }
32: }
```

## E23 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:161
```text
161:     fun sendManualRichReply(
162:         inboundProcessingId: Long,
163:         senderAccountCode: String?,
164:         subject: String,
165:         htmlBody: String,
166:         textBody: String?,
167:         operatorName: String?,
168:         qaRuleIds: List<Long>? = null,
169:         suggestedRuleIds: List<Long>? = null,
170:         ackSnippetId: Long? = null,
171:         edited: Boolean? = null,
172:         freeTextPreview: String? = null,
173:         useVariants: Boolean = false,
174:         templateTextBody: String? = null,
175:         templateHtmlBody: String? = null,
176:         trustReplyAssembly: TrustReplyAssembleRequest? = null,
177:         safetyWarningConfirmed: Boolean = false,
178:         strongConfirmationText: String? = null,
179:         // 03b (I-39~I-43): RAG 证据 —— fact_code 字符串列表 + 生成草稿时下发的语料指纹。
180:         // 追加在参数末尾，既有调用点零改动；与 trustReplyAssembly 互斥（I-39）。
181:         ragFactCodes: List<String>? = null,
182:         ragCorpusFingerprint: String? = null,
183:         // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256。meeting 与
184:         // previewAttachmentSha256 必须同时出现/同时为空（否则 400）；非空时在最终变量
185:         // 渲染后用真实 processing/contact/account 走 01 validateAndBuild 重算核对。
186:         meeting: MeetingInput? = null,
187:         previewAttachmentSha256: String? = null,
188:         // 06 (T1/I-1): 通用附件 id（用户选择顺序）与本次请求的真实会话身份。默认空/空身份
189:         // 保持既有内部调用点零改动；非空 id 时身份必须真实（空身份在 claim 前 400），
190:         // 绝不回退请求体里的 operatorName。
191:         attachmentIds: List<String> = emptyList(),
192:         authenticatedUsername: String? = null,
193:         draftRef: MailReplyDraftRef? = null
194:     ): PendingMailSendResult {
195:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
196:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
197:         val contactId = record.expertContactId
198:             ?: error("Inbound mail not bound to a contact")
199:         val contact = expertContactRepository.findById(contactId)
200:             .orElseThrow { error("Expert contact not found: $contactId") }
201:         if (draftRef != null) {
202:             val bound = requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
203:                 authenticatedUsername.orEmpty(),draftRef,contactId,inboundProcessingId,
204:                 senderAccountCode?.takeIf { it.isNotBlank() } ?: record.senderAccountCode)
205:             if (bound != null) return completedDraftResult(contactId,requireNotNull(manualReplySendAttemptService.findCompletedAttempt(bound)))
206:         }
207: 
208:         require(subject.isNotBlank()) { "Subject is required" }
```

## E24 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:315
```text
315:             qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
316:         } else {
317:             emptyList()
318:         }
319:         // 03b (I-43): RAG 路径 matched_qa_rule_id 恒 null，绝不用 legacy_rule_id 兜底。
320:         val primaryRuleId = if (ragMode) null else canonicalFactIds.firstOrNull()
321: 
322:         val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)
323:         val validatedDraft = draftRef?.let {
324:             requireNotNull(mailReplyDraftService).validateInboundSend(authenticatedUsername.orEmpty(),it,
325:                 MailReplyDraftTarget(contactId,MailReplyDraftKind.INBOUND,inboundProcessingId,account.accountCode),
326:                 subject,htmlBody,textBody,ragFactCodes,ragCorpusFingerprint,edited,freeTextPreview,meeting,
327:                 previewAttachmentSha256,attachmentIds,
328:                 qaRuleIds != null || suggestedRuleIds != null || ackSnippetId != null || useVariants ||
329:                     trustReplyAssembly != null || templateTextBody?.let { raw -> raw != textBody } == true ||
330:                     templateHtmlBody?.let { raw -> raw != htmlBody } == true)
331:         }
332: 
333:         val manualSource = ManualRichSendSource(
334:             contact = contact,
335:             contactId = contactId,
336:             inboundProcessingId = inboundProcessingId,
337:             inboundRecord = record,
338:             account = account,
339:             accountCode = account.accountCode,
340:             persistInReplyTo = record.messageId,
341:             smtpInReplyTo = null,
342:             smtpReferences = null,
343:             anchorMailRecordId = null,
344:             requestId = null
345:         )
346:         return executeManualRichSend(
347:             source = manualSource,
348:             rawSubject = subject,
349:             htmlBody = htmlBody,
350:             textBody = textBody,
351:             operatorName = operatorName,
352:             safetyWarningConfirmed = safetyWarningConfirmed,
353:             strongConfirmationText = strongConfirmationText,
354:             evidence = ManualReplyEvidenceContext(
355:                 verifiedAssembly = verifiedAssembly,
356:                 carriesQa = carriesQa,
357:                 canonicalFactIds = canonicalFactIds,
358:                 serverSuggestedFactIds = serverSuggestedFactIds,
359:                 degradedFactCodes = factResolution.degradedCodes,
360:                 ragMode = ragMode,
361:                 ragFactCodes = ragFactCodes,
362:                 ragFingerprintAtSend = ragFingerprintAtSend,
363:                 templateTextBody = templateTextBody,
364:                 templateHtmlBody = templateHtmlBody,
365:                 operatorAuthorizedActions = verifiedAssembly
366:                     ?.let { trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(it.response.itemVersions) }
367:                     .orEmpty(),
368:                 edited = edited,
369:                 inboundText = inboundText,
370:                 researchProfileSufficient = researchProfileSufficient
371:             ),
372:             meeting = meeting,
373:             previewAttachmentSha256 = previewAttachmentSha256,
374:             attachmentIds = attachmentIds,
375:             authenticatedUsername = authenticatedUsername,
376:             draftSendRef = validatedDraft
377:         )
378:     }
```

## E25 — src/main/resources/static/mailbox-chat.js:8838
```text
8838:         function sendManualReply() {
8839:             const rec = currentDraftRecord();
8840:             if (!rec || rec.preparing || rec.sending || rec.conflict || rec.closed || !rec.loaded || rec.error === "load" || rec.readOnly) return;
8841:             if (outboundAttachmentsBlockSend()) {
8842:                 hostShowStatus("附件正在上传或上传失败，请等待上传完成或移除失败附件后再发送", "error");
8843:                 return;
8844:             }
8845:             rec.preparing = true;
8846:             const key = rec.key;
8847:             return (async () => {
8848:                 try {
8849:                     let seq;
8850:                     do {
8851:                         if (currentTargetKey() !== key || !draftOwnerLive(rec)) return;
8852:                         saveDraftFromInputs({ force: true });
8853:                         ensureOutboundRequestId();
8854:                         seq = rec.seq;
8855:                         await flushDrafts();
8856:                     } while (seq !== rec.seq);
8857:                     const detail = await hostApi()(`/api/mail/mailbox/drafts/${rec.id}`);
8858:                     if (currentTargetKey() !== key || !draftOwnerLive(rec)) return;
8859:                     if (seq !== rec.seq) { rec.preparing = false; return sendManualReply(); }
8860:                     if (detail.state !== "ACTIVE" || Number(detail.version) !== rec.version
8861:                         || !draftContentEqual(draftFromDetail(detail), rec.map.get(key))) {
8862:                         rec.conflict = true;
8863:                         refreshDraftStatus();
8864:                         return;
8865:                     }
8866:                     acceptDraftDetail(rec, detail, false);
8867:                     if (["DELIVERY_UNKNOWN", "DELIVERY_IN_PROGRESS"].includes(detail.sendAttemptStatus)) {
8868:                         refreshDraftStatus();
8869:                         return;
8870:                     }
8871:                     return sendSavedManualReply(rec, seq);
8872:                 } catch (err) {
8873:                     hostShowStatus(err.message || "保存失败，内容尚未同步到服务器", "error");
8874:                 } finally { rec.preparing = false; }
8875:             })();
8876:         }
8877: 
8878:         function sendSavedManualReply(rec, sentSeq) {
8879:             const key = currentTargetKey();
8880:             if (!key || instance.manual.busy) return;
8881:             const composeEl = manualComposeEl();
8882:             const inputs = manualInputs(composeEl);
8883:             if (!inputs) return;
8884:             const draftSnapshot = getDraft(key);
8885:             if (!draftSnapshot) return;
8886:             const subject = String(draftSnapshot.subject || "").trim();
8887:             if (!subject) {
8888:                 hostShowStatus("请输入邮件主题", "error");
8889:                 return;
8890:             }
8891:             const hasBodyHtml = String(draftSnapshot.html || "").trim();
8892:             if (!hasBodyHtml) {
8893:                 hostShowStatus("请输入邮件正文", "error");
8894:                 return;
8895:             }
8896:             // The final flush and GET above acknowledged this exact canonical snapshot.
8897:             const textBody = String(draftSnapshot.text || "");
8898:             const htmlBody = String(draftSnapshot.html || "");
8899:             const mode = instance.manual.mode;
8900:             // I-8/跟进（I-3）：草稿携带所选锚点时，无论当前 target 是来信还是无来信会话，
8901:             // 都必须走会话级接口并把真实 id 交给服务端重新校验。
8902:             const followUpAnchorId = draftSnapshot && draftSnapshot.followUpAnchorMailRecordId != null
8903:                 ? Number(draftSnapshot.followUpAnchorMailRecordId)
8904:                 : null;
8905:             const conversationSend = mode === "outbound" || followUpAnchorId != null;
8906:             // 会议快照发送（T4）：只属于来信人工回复（inbound）且未选跟进锚点 —— 快照存在且
8907:             // state ready 且当前会议块文本与基线一致才允许带附件发送；outbound/跟进路径无 meeting。
8908:             const meeting = mode === "inbound" && !conversationSend ? manualMeetingSnapshot() : null;
8909:             if (meeting) {
8910:                 if (meeting.state !== "ready") {
8911:                     hostShowStatus("会议正文或回复目标已变化，请编辑会议重新生成，或移除日历附件。", "error");
8912:                     return;
8913:                 }
8914:                 const block = meetingBlockIn(inputs.editor);
8915:                 const blockText = block ? meetingNormalizeText(meetingElementText(block)) : "";
8916:                 if (!block || blockText !== meetingNormalizeText(meeting.blockText)) {
8917:                     hostShowStatus("会议正文已被修改，请编辑会议重新生成后再发送", "error");
8918:                     return;
8919:                 }
8920:             }
8921:             let requestBody = null;
8922:             let processingId = null;
8923:             // fast-p 07（I-3）：只有 ready 条目按选择顺序提交；任一 uploading/failed 禁止发送
8924:             // —— 失败项必须重新选择或移除，绝不静默漏发。
8925:             const attachmentItems = draftSnapshot ? outboundAttachmentDraftOf(draftSnapshot).items : [];
8926:             if (attachmentItems.some((item) => !item || item.state !== OUTBOUND_STATE_READY)) {
8927:                 hostShowStatus("附件正在上传或上传失败，请等待上传完成或移除失败附件后再发送", "error");
8928:                 return;
8929:             }
8930:             const attachmentIds = outboundAttachmentIds(draftSnapshot);
8931:             if (!conversationSend) {
8932:                 // 来信路径：既有 processingId adapter，保留 QA/RAG payload（I-8）。
8933:                 requestBody = {
8934:                     senderAccountCode: null,
8935:                     subject,
8936:                     htmlBody,
8937:                     textBody,
8938:                     operatorName: operatorName()
8939:                 };
8940:                 const qa = instance.manual.qa;
8941:                 if (qa && qa.ragFactCodes && qa.ragFactCodes.length) {
8942:                     requestBody.ragFactCodes = qa.ragFactCodes.slice();
8943:                     requestBody.ragCorpusFingerprint = qa.ragCorpusFingerprint || "";
8944:                     requestBody.edited = textBody.trim() !== normalizeManualTextLineBreaks(qa.baselineText || "").trim();
8945:                 }
8946:                 // 会议字段只进来信人工富文本请求（T4/S-3）
8947:                 if (meeting) {
8948:                     const sha = meeting.preview && meeting.preview.attachment
8949:                         ? String(meeting.preview.attachment.sha256 || "")
8950:                         : "";
8951:                     if (!sha) {
8952:                         hostShowStatus("会议附件信息不完整，请重新预览", "error");
8953:                         return;
8954:                     }
8955:                     requestBody.meeting = deepCopyMeeting(meeting.input);
8956:                     requestBody.previewAttachmentSha256 = sha;
8957:                 }
8958:                 // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
8959:                 if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
8960:                 processingId = Number(instance.manual.targetProcessingId);
8961:             } else {
8962:                 // 会话回信路径：body 只含 requestId/当前 accountScope/显式锚点/自由正文/确认
8963:                 // 字段；无 processingId/senderAccountCode/QA/RAG/meeting（I-3/I-4/I-6）。
8964:                 const requestId = draftSnapshot.requestId;
8965:                 if (!requestId) {
8966:                     hostShowStatus("无法生成发送请求标识", "error");
8967:                     return;
8968:                 }
8969:                 requestBody = {
8970:                     requestId,
8971:                     accountScope: rec.target.accountScope || null,
8972:                     subject,
8973:                     htmlBody,
8974:                     textBody,
8975:                     operatorName: operatorName()
8976:                 };
8977:                 if (followUpAnchorId != null) requestBody.anchorMailRecordId = followUpAnchorId;
8978:                 // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
8979:                 if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
8980:             }
8981:             // I-2：异步前捕获 draftsMap/owner/key/revision/requestBody；不回调里再取。
8982:             const contactId = Number(instance.selectedContactId);
8983:             const ownerKey = conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId);
8984:             requestBody.draftRef = { id: rec.id, version: rec.version };
8985:             const inFlightKey = meeting ? `${ownerKey}|${key}` : null;
8986:             if (inFlightKey && meetingInFlight.has(inFlightKey)) {
8987:                 hostShowStatus("该回复目标已有发送中的会议回复，请稍候", "error");
8988:                 return;
8989:             }
8990:             if (inFlightKey) meetingInFlight.add(inFlightKey);
8991:             // 07（I-3）：带已就绪附件的发送同样锁住当前 owner 的编辑/附件增删；其他专家不受影响。
8992:             const lockedCompose = !!meeting || attachmentItems.length > 0;
8993:             instance.manual.busy = true;
8994:             rec.sending = true;
8995:             refreshDraftStatus();
8996:             setSendButtonDisabled(true);
8997:             if (lockedCompose) setManualComposeSending(true);
8998:             const adapter = conversationSend
8999:                 ? hostFn("mcHostSendConversationRichReply")
9000:                 : hostFn("mcHostSendRichReply");
9001:             const request = adapter
9002:                 ? (conversationSend
9003:                     ? adapter(contactId, requestBody)
9004:                     : adapter(processingId, requestBody))
9005:                 : Promise.reject(new Error("发送能力不可用"));
9006:             return Promise.resolve(request).catch(() => false).then(async () => {
9007:                 // HTTP/host Boolean is not the draft-close authority.
9008:                 try {
9009:                     const detail = await hostApi()(`/api/mail/mailbox/drafts/${rec.id}`);
9010:                     if (!draftOwnerLive(rec)) return;
9011:                     const stillCurrent = !instance.disposed && currentTargetKey() === key;
9012:                     if (stillCurrent) saveDraftFromInputs();
9013:                     if (Number(detail.version) < rec.version) return;
9014:                     if (detail.state === "ACTIVE" && Number(detail.version) !== rec.version && rec.seq > sentSeq) {
9015:                         const remote = draftFromDetail(detail);
9016:                         if (draftContentEqual(remote, rec.map.get(key))) rec.ack = rec.seq;
9017:                         else if (rec.submitted && draftContentEqual(remote, {
9018:                             subject: rec.submitted.snapshot.subject, html: rec.submitted.snapshot.html,
9019:                             text: rec.submitted.snapshot.text, ...rec.submitted.snapshot.context })) {
9020:                             rec.ack = Math.max(rec.ack, rec.submitted.seq);
9021:                         } else rec.conflict = true;
9022:                     }
9023:                     acceptDraftDetail(rec, detail, false);
9024:                     rec.sendUnconfirmed = false;
9025:                     if (detail.state === "SENT" || detail.state === "DISCARDED") {
9026:                         clearTimeout(rec.timer);
9027:                         if (rec.seq === sentSeq) {
9028:                             rec.map.delete(key);
```

## E26 — src/main/resources/static/styles.css:1
```text
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
77:     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
78:     --shadow: var(--shadow-md);
79: 
80:     --transition: all 0.15s ease;
81: 
82:     --font-mono: 'SF Mono', ui-monospace, Menlo, monospace;
83:     --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
```

## E27 — src/main/resources/static/styles.css:802
```text
802: .button {
803:     display: inline-flex;
804:     align-items: center;
805:     justify-content: center;
806:     gap: 6px;
807:     min-height: 32px;
808:     height: 32px;
809:     padding: 0 12px;
810:     border-radius: var(--radius-sm);
811:     font-weight: 500;
812:     font-size: 12px;
813:     cursor: pointer;
814:     border: 1px solid var(--border);
815:     background-color: transparent;
816:     color: var(--text-main);
817:     transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
818:     outline: none;
819:     user-select: none;
820:     font-family: var(--font-body);
821:     position: relative;
822:     overflow: hidden;
823: }
824: 
825: .button:hover {
826:     border-color: rgba(15, 23, 42, 0.2);
827:     background-color: var(--surface);
828:     transform: translateY(-1px);
829:     box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
830: }
831: 
832: .button:active {
833:     transform: translateY(0) scale(0.97);
834:     box-shadow: none;
835:     opacity: 0.85;
836: }
837: 
838: .button.primary {
839:     background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
840:     background-color: var(--primary);
841:     border-color: transparent;
842:     color: #ffffff;
843:     font-weight: 600;
844:     box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
845: }
846: 
847: .button.primary:hover {
848:     background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
849:     box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
850: }
851: 
852: .button.secondary {
853:     background-color: var(--primary-light);
854:     border-color: rgba(var(--primary-rgb), 0.12);
855:     color: var(--primary);
856: }
857: 
858: .button.secondary:hover {
859:     background-color: rgba(var(--primary-rgb), 0.1);
860: }
861: 
862: .button.danger {
863:     background-color: var(--error-bg);
864:     border-color: var(--error-border);
865:     color: var(--error);
866: }
867: 
868: .button.danger:hover {
869:     background-color: rgba(var(--error-rgb), 0.1);
870: }
871: 
872: .icon-button {
```

## E28 — src/main/resources/static/styles.css:979
```text
979: .panel-head-actions {
980:     display: inline-flex;
981:     align-items: center;
982:     gap: 8px;
983:     flex-shrink: 0;
984: }
```

## E29 — src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt:1
```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import org.springframework.stereotype.Service
4: 
5: @Service
6: class MailPlaceholderService {
7:     fun variableMetadata(): List<VariableMeta> =
8:         VARIABLE_LABELS.map { (key, label) ->
9:             VariableMeta(
10:                 key = key,
11:                 label = label,
12:                 nullable = key in EXPERT_KEYS,
13:                 example = VARIABLE_EXAMPLES[key].orEmpty(),
14:                 esField = ES_FIELD_BY_KEY[key]
15:             )
16:         }
17: 
18:     fun placeholderKeysIn(vararg texts: String): List<String> {
19:         val keys = linkedSetOf<String>()
20:         texts.forEach { text ->
21:             PLACEHOLDER_REGEX.findAll(text).forEach { match ->
22:                 keys.add(parsePlaceholderToken(match.groupValues[1]).key)
23:             }
24:         }
25:         return keys.toList()
26:     }
27: 
28:     fun filterableEsFields(text: String): List<String> {
29:         if (text.isEmpty()) {
30:             return emptyList()
31:         }
32:         val esFieldByKey = variableMetadata().associate { it.key to it.esField }
33:         val fields = linkedSetOf<String>()
34:         PLACEHOLDER_REGEX.findAll(text).forEach { match ->
35:             val parsed = parsePlaceholderToken(match.groupValues[1])
36:             esFieldByKey[parsed.key]?.let { fields.add(it) }
37:         }
38:         return fields.toList()
39:     }
40: 
41:     fun validatePlaceholders(text: String): List<String> {
42:         if (text.isEmpty()) {
43:             return emptyList()
44:         }
45:         val metaByKey = variableMetadata().associateBy { it.key }
46:         val violations = linkedSetOf<String>()
47:         PLACEHOLDER_REGEX.findAll(text).forEach { match ->
48:             val token = match.value
49:             val parsed = parsePlaceholderToken(match.groupValues[1])
50:             val meta = metaByKey[parsed.key]
51:             when {
52:                 meta == null -> violations.add(token)
53:                 meta.nullable && parsed.fallback?.trim().isNullOrEmpty() -> violations.add(token)
54:             }
55:         }
56:         return violations.toList()
57:     }
58: 
59:     fun requireValidPlaceholders(text: String) {
60:         val violations = validatePlaceholders(text)
61:         require(violations.isEmpty()) {
62:             "Invalid placeholders: ${violations.joinToString(", ")}"
63:         }
64:     }
65: 
66:     fun unknownPlaceholderTokens(text: String): List<String> {
67:         if (text.isEmpty()) {
68:             return emptyList()
69:         }
70:         val metaByKey = variableMetadata().associateBy { it.key }
71:         val tokens = linkedSetOf<String>()
72:         PLACEHOLDER_REGEX.findAll(text).forEach { match ->
73:             val token = match.value
74:             val parsed = parsePlaceholderToken(match.groupValues[1])
75:             if (metaByKey[parsed.key] == null) {
76:                 tokens.add(token)
77:             }
78:         }
79:         return tokens.toList()
80:     }
```

## E30 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:613
```text
613:         require(rawSubject.isNotBlank()) { "Subject is required" }
614:         require(htmlBody.isNotBlank()) { "HTML body is required" }
615:         val trimmedSubject = rawSubject.trim()
616:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
617: 
618:         mailVariableService.requireValidPlaceholders(trimmedSubject)
619:         val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
620:         require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
621:         require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }
622: 
623:         val rawText = evidence.templateTextBody?.takeIf { it.isNotBlank() }
624:             ?: textBody?.takeIf { it.isNotBlank() }
625:             ?: mailBodyCleaner.clean(htmlBody)
626:         val rawHtmlFromTemplate = evidence.templateHtmlBody?.takeIf { it.isNotBlank() }
627:         mailVariableService.requireValidPlaceholders(rawText)
628:         if (rawHtmlFromTemplate != null) {
629:             mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
630:         } else if (evidence.templateTextBody.isNullOrBlank()) {
631:             mailVariableService.requireValidPlaceholders(htmlBody)
632:         }
633: 
634:         val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
635:         // I-1/I-2/I-3：人工富文本最终正文必须在变量渲染之后、最终校验/安全确认/幂等指纹/
636:         // SMTP/落库/审计之前规范化一次；本方法后续所有读路径（finalValidationText、会议正文
637:         // 核对、SendPayload 指纹、ComposedMail 两个 MIME alternative、预览与归档）全部引用这
638:         // 一份 canonical final body，禁止检查原文却发送另一份正文（I-4）。
639:         val finalTextBody = mailContentService.normalizeManualTextLineBreaks(renderedText)
640:         val finalHtmlBody = when {
641:             rawHtmlFromTemplate != null ->
642:                 mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
643:             !evidence.templateTextBody.isNullOrBlank() ->
644:                 mailContentService.plainTextToHtml(renderedText)
645:             else ->
646:                 mailVariableService.renderHtmlForContact(htmlBody, source.account, contact)
647:         }.let { mailContentService.stripOpenTrackingImages(it) }
648:             .let { mailContentService.normalizeManualRichHtmlLineBreaks(it) }
649: 
650:         val finalValidationText = buildFinalValidationText(renderedSubject, finalTextBody, finalHtmlBody)
651:         require(finalValidationText.isNotBlank()) { "Final validation text is empty after rendering" }
652:         require(finalValidationText.length <= 20000) {
653:             "Final validation text exceeds 20,000 characters (${finalValidationText.length})"
654:         }
655:         mailVariableService.requireValidPlaceholders(finalTextBody)
656:         mailVariableService.requireValidPlaceholders(finalHtmlBody)
657: 
658:         // 03 (T1/I-1/I-2): 会议附件重建与正文核对 —— 位于最终变量渲染之后、Safety/claim/
659:         // SMTP 之前。会议仅由来信路径产生（meeting 来自 unmatched-inbound 01 预览，
```

## E31 — src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1417
```text
1417:     private fun buildFinalValidationText(subject: String, finalText: String, finalHtml: String): String {
1418:         val htmlPlain = mailContentService.htmlToPlainText(finalHtml)
1419:         val hrefs = HREF_EXTRACTOR.findAll(finalHtml).map { m ->
1420:             m.groupValues[1].takeIf { it.isNotEmpty() }
1421:                 ?: m.groupValues[2].takeIf { it.isNotEmpty() }
1422:                 ?: m.groupValues[3]
1423:         }.toList()
1424:         return listOfNotNull(
1425:             subject.takeIf { it.isNotBlank() },
1426:             finalText.takeIf { it.isNotBlank() },
1427:             htmlPlain.takeIf { it.isNotBlank() },
1428:             hrefs.takeIf { it.isNotEmpty() }?.joinToString(" ")
1429:         ).joinToString(" ")
1430:     }
1431: 
```
