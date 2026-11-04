# 带行号的代码原文

2026-11-04 工作树快照。代码事实以此与 source-manifest.json 为准。


## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt

```text
1: package com.weibo.talentintroduction.mail.repository
2: 
3: import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
4: import com.weibo.talentintroduction.mail.service.*
5: import org.springframework.jdbc.core.RowMapper
6: import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
7: import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
8: import org.springframework.jdbc.support.GeneratedKeyHolder
9: import org.springframework.stereotype.Repository
10: import org.springframework.transaction.annotation.Propagation
11: import org.springframework.transaction.annotation.Transactional
12: import org.springframework.transaction.support.TransactionSynchronizationManager
13: import java.sql.ResultSet
14: import java.time.LocalDateTime
15: 
16: @Repository
17: class MailReplyDraftRepository(private val jdbc: NamedParameterJdbcTemplate) {
18:     private val mapper = RowMapper { r: ResultSet, _: Int ->
19:         MailReplyDraftRow(r.getLong("id"), r.getString("username"),
20:             MailReplyDraftTarget(r.getLong("expert_contact_id"), MailReplyDraftKind.valueOf(r.getString("target_kind")), r.getLong("inbound_processing_id"), r.getString("account_scope")),
21:             r.getLong("version"), MailReplyDraftState.valueOf(r.getString("state")), r.getString("subject"), r.getString("html_body"), r.getString("text_body"), r.getString("context_json"),
22:             r.getObject("send_attempt_id")?.let { (it as Number).toLong() }, r.getObject("send_version")?.let { (it as Number).toLong() },
23:             r.getTimestamp("created_at").toLocalDateTime(), r.getTimestamp("updated_at").toLocalDateTime(),
24:             r.getBoolean("contact_exists"), r.getString("expert_name"), r.getString("expert_email"), r.getString("send_attempt_status"))
25:     }
26:     private val projection = """SELECT d.*, c.id IS NOT NULL AS contact_exists, c.expert_name, c.expert_email,
27:         a.status AS send_attempt_status FROM mailbox_reply_draft d
28:         LEFT JOIN expert_contact c ON c.id=d.expert_contact_id
29:         LEFT JOIN mail_send_attempt a ON a.id=d.send_attempt_id"""
30:     private fun targetParams(owner: String, target: MailReplyDraftTarget) = mapOf("owner" to owner, "contact" to target.contactId,
31:         "kind" to target.kind.name, "processing" to target.processingId, "scope" to target.accountScope)
32:     fun findTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.expert_contact_id=:contact AND d.target_kind=:kind AND d.inbound_processing_id=:processing AND d.account_scope=:scope", targetParams(owner, target), mapper).singleOrNull()
33:     fun findOwned(owner: String, id: Long): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.id=:id", mapOf("owner" to owner, "id" to id), mapper).singleOrNull()
34:     fun insert(owner: String, target: MailReplyDraftTarget, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Long {
35:         val params = MapSqlParameterSource(targetParams(owner, target)).addValue("subject", content.subject).addValue("html", content.html).addValue("text", content.text).addValue("context", contextJson).addValue("now", now)
36:         val key = GeneratedKeyHolder()
37:         jdbc.update("""INSERT INTO mailbox_reply_draft (username,expert_contact_id,target_kind,inbound_processing_id,account_scope,version,state,subject,html_body,text_body,context_json,created_at,updated_at)
38:             VALUES (:owner,:contact,:kind,:processing,:scope,1,'ACTIVE',:subject,:html,:text,:context,:now,:now)""", params, key)
39:         return requireNotNull(key.key).toLong()
40:     }
41:     fun save(owner: String, id: Long, version: Long, state: MailReplyDraftState, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Boolean {
42:         val resetBinding = if (state == MailReplyDraftState.ACTIVE) "" else ",send_attempt_id=NULL,send_version=NULL"
43:         return jdbc.update("""UPDATE mailbox_reply_draft SET version=version+1,state='ACTIVE',subject=:subject,html_body=:html,text_body=:text,context_json=:context,updated_at=:now$resetBinding
44:             WHERE id=:id AND username=:owner AND version=:version AND state=:state""",
45:             mapOf("owner" to owner,"id" to id,"version" to version,"state" to state.name,"subject" to content.subject,"html" to content.html,"text" to content.text,"context" to contextJson,"now" to now)) == 1
46:     }
47:     fun discard(owner: String, id: Long, version: Long, now: LocalDateTime): Boolean = jdbc.update("""UPDATE mailbox_reply_draft SET state='DISCARDED',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
48:         WHERE id=:id AND username=:owner AND version=:version AND state='ACTIVE'""", mapOf("owner" to owner,"id" to id,"version" to version,"now" to now)) == 1
49: 
50:     private fun scopeSql(scope: String?) = if (scope == null) "" else " AND d.account_scope=:scope"
51:     private fun accessSql(owner: String) = if (!MailReadOnlyPolicy.isReader(owner)) "" else """
52:         AND EXISTS (SELECT 1 FROM expert_mail_read_access access
53:             JOIN expert_contact contact ON contact.id=access.expert_contact_id
54:             WHERE access.username=:owner AND access.expert_contact_id=d.expert_contact_id AND access.access_level='FULL')
55:         AND (d.target_kind='OUTBOUND' OR EXISTS (SELECT 1 FROM inbound_mail_processing source
56:             WHERE source.id=d.inbound_processing_id AND source.expert_contact_id=d.expert_contact_id
57:                 AND source.sender_account_code=d.account_scope))
58:     """
59:     private fun listParams(owner: String, scope: String?, search: String?): Map<String, Any?> = mapOf("owner" to owner,"scope" to scope,
60:         "search" to ("%" + search.orEmpty().replace("!","!!").replace("%","!%").replace("_","!_") + "%"))
61:     fun list(owner: String, scope: String?, search: String?, page: Int, size: Int): MailReplyDraftList {
62:         val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope) + accessSql(owner) +
63:             if (search.isNullOrEmpty()) "" else " AND (c.expert_name LIKE :search ESCAPE '!' OR c.expert_email LIKE :search ESCAPE '!' OR d.subject LIKE :search ESCAPE '!')"
64:         val params = listParams(owner,scope,search) + mapOf("limit" to size,"offset" to page.toLong()*size)
65:         val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter",params,Long::class.java) ?: 0
66:         // Do not fetch large HTML/context merely to render a list preview.
67:         val items = jdbc.query("""SELECT d.id,d.version,d.state,d.expert_contact_id,d.target_kind,d.inbound_processing_id,d.account_scope,d.subject,
68:             LEFT(d.text_body,120) AS preview,d.updated_at,c.id IS NOT NULL AS contact_exists,c.expert_name,c.expert_email
69:             FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter ORDER BY d.updated_at DESC,d.id DESC LIMIT :limit OFFSET :offset""",params,
70:             RowMapper { r, _ -> MailReplyDraftListItem(r.getLong("id"),r.getLong("version"),MailReplyDraftState.valueOf(r.getString("state")),
71:                 MailReplyDraftTarget(r.getLong("expert_contact_id"),MailReplyDraftKind.valueOf(r.getString("target_kind")),r.getLong("inbound_processing_id"),r.getString("account_scope")),
72:                 r.getString("subject"),r.getString("preview").orEmpty(),r.getTimestamp("updated_at").toLocalDateTime(),r.getBoolean("contact_exists"),r.getString("expert_name"),r.getString("expert_email")) })
73:         return MailReplyDraftList(items,total,page,size)
74:     }
75:     fun summaries(owner: String, scope: String?, contacts: List<Long>): MailReplyDraftSummaries {
76:         val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope) + accessSql(owner)
77:         val params = mapOf("owner" to owner,"scope" to scope,"contacts" to contacts)
78:         val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d$filter",params,Long::class.java) ?: 0
79:         val counts = if (contacts.isEmpty()) emptyMap() else jdbc.query("SELECT d.expert_contact_id,COUNT(*) AS n FROM mailbox_reply_draft d$filter AND d.expert_contact_id IN (:contacts) GROUP BY d.expert_contact_id",params,
80:             RowMapper { r, _ -> r.getLong("expert_contact_id") to r.getLong("n") }).toMap()
81:         return MailReplyDraftSummaries(contacts.map { MailReplyDraftSummary(it,counts[it] ?: 0) },total)
82:     }
83: 
84:     /** Child 02 calls these only inside its existing sending transaction. */
85:     @Transactional(propagation = Propagation.MANDATORY)
86:     fun lockOwned(owner: String, id: Long): MailReplyDraftRow? {
87:         mandatory()
88:         // Lock only the draft row, not the optional LEFT JOIN business rows.
89:         jdbc.queryForList("SELECT id FROM mailbox_reply_draft WHERE username=:owner AND id=:id FOR UPDATE",mapOf("owner" to owner,"id" to id))
90:         return findOwned(owner,id)
91:     }
92:     @Transactional(propagation = Propagation.MANDATORY)
93:     fun bindAttempt(owner: String, id: Long, version: Long, attemptId: Long): Boolean {
94:         mandatory(); require(attemptId > 0 && version > 0)
95:         return jdbc.update("""UPDATE mailbox_reply_draft SET send_attempt_id=:attempt,send_version=:version
96:             WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE'
97:             AND ((send_attempt_id IS NULL AND send_version IS NULL) OR (send_attempt_id=:attempt AND send_version=:version))""",
98:             mapOf("owner" to owner,"id" to id,"version" to version,"attempt" to attemptId)) == 1
99:     }
100:     @Transactional(propagation = Propagation.MANDATORY)
101:     fun closeSent(owner: String, id: Long, sendVersion: Long, attemptId: Long, now: LocalDateTime): Boolean {
102:         mandatory()
103:         return jdbc.update("""UPDATE mailbox_reply_draft SET state='SENT',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
104:             WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE' AND send_attempt_id=:attempt AND send_version=:version""",
105:             mapOf("owner" to owner,"id" to id,"version" to sendVersion,"attempt" to attemptId,"now" to now)) == 1
106:     }
107:     @Transactional(propagation = Propagation.MANDATORY)
108:     fun releaseCompletedBinding(owner: String, id: Long, sendVersion: Long, attemptId: Long): Boolean {
109:         mandatory()
110:         return jdbc.update("UPDATE mailbox_reply_draft SET send_attempt_id=NULL,send_version=NULL WHERE username=:owner AND id=:id AND send_attempt_id=:attempt AND send_version=:version",
111:             mapOf("owner" to owner,"id" to id,"attempt" to attemptId,"version" to sendVersion)) == 1
112:     }
113:     private fun mandatory() { check(TransactionSynchronizationManager.isActualTransactionActive()) { "Draft send operations require an existing transaction" } }
114: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt

```text
1: package com.weibo.talentintroduction.mail.repository
2: 
3: import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
4: import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
5: import org.springframework.stereotype.Repository
6: import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
7: import com.weibo.talentintroduction.mail.service.MailSenderAccountService
8: import com.weibo.talentintroduction.mail.service.MailboxProgressStatus
9: import java.sql.ResultSet
10: import java.time.LocalDateTime
11: 
12: /**
13:  * 专家会话查询（fast-p 07）专用 JDBC 仓库。
14:  *
15:  * 数据权威（I-1）：来信只取 linked inbound_mail_processing（排除未匹配与独立机器信；
16:  * 绝不再 UNION INBOUND mail_record 重复计数）；发件只取 OUTBOUND mail_record。
17:  * 事件时间 = processing.received_at / OUTBOUND 的 COALESCE(sent_at, created_at)；
18:  * 稳定排序键 (event_at, source_rank, id)，source_rank 1=MAIL_RECORD 2=INBOUND_PROCESSING。
19:  * 文本列沿既有 mailbox SQL 显式统一 utf8mb4_unicode_ci，避免 UNION 排序字符集冲突。
20:  *
21:  * summary / count / timeline 共用同一份归一化 UNION SQL 基础（[rangeUnionSql]）。
22:  * 聚合在账号范围（accountCodes ∩ accountCode）全历史计算；方向/日期/主题/标签等
23:  * 消息级筛选只经 EXISTS 限定专家 membership，绝不影响聚合口径（I-2/I-4）。
24:  */
25: @Repository
26: class MailboxConversationRepository(
27:     private val jdbcTemplate: NamedParameterJdbcTemplate
28: ) {
29:     /**
30:      * Full real conversation fingerprint. Length-prefixed UTF-8 fields distinguish null,
31:      * empty strings and field boundaries; processing labels/status deliberately do not participate.
32:      */
33:     fun reviewContext(contactId: Long, accountCodes: List<String>): String {
34:         val digest = java.security.MessageDigest.getInstance("SHA-256")
35:         val lengthPrefix = java.nio.ByteBuffer.allocate(4)
36:         fun feed(value: String?) {
37:             if (value == null) {
38:                 digest.update(lengthPrefix.clear().putInt(-1).array())
39:             } else {
40:                 val bytes = value.toByteArray(Charsets.UTF_8)
41:                 digest.update(lengthPrefix.clear().putInt(bytes.size).array())
42:                 digest.update(bytes)
43:             }
44:         }
45:         feed("mailbox-review-context-v1")
46:         feed(contactId.toString())
47:         if (accountCodes.isNotEmpty()) {
48:             val sql = """
49:                 SELECT u.* FROM (${rangeUnionSql(includeBody = true)}) u
50:                 JOIN mail_sender_account a ON a.account_code=u.account_code
51:                 WHERE u.expert_contact_id=:contactId AND a.account_code<>:simulator
52:                 ORDER BY u.source_rank,u.id
53:             """.trimIndent()
54:             val p = MapSqlParameterSource().addValue("contactId", contactId)
55:                 .addValue("accountCodes", accountCodes).addValue("accountCode", null)
56:                 .addValue("simulator", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
57:                 .addValue("username", "")
58:             jdbcTemplate.query(sql, p, org.springframework.jdbc.core.RowCallbackHandler { rs ->
59:                 feed(rs.getString("source"))
60:                 feed(rs.getLong("id").toString())
61:                 feed(rs.getString("account_code"))
62:                 feed(rs.getString("direction"))
63:                 feed(rs.getTimestamp("event_at")?.toLocalDateTime()?.toString())
64:                 feed(rs.getString("message_id"))
65:                 feed(rs.getString("in_reply_to"))
66:                 feed(rs.getString("subject"))
67:                 feed(rs.getString("body"))
68:                 feed(rs.getString("cleaned_body"))
69:                 feed(rs.getString("send_status"))
70:             })
71:         }
72:         return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
73:     }
74: 
75:     // ------------------------------------------------------------------
76:     // 参数与投影
77:     // ------------------------------------------------------------------
78: 
79:     /** 会话 summary 的消息级筛选（均为可空白名单值；方向由 service/controller 白名单约束）。 */
80:     data class ConversationFilter(
81:         val accountCodes: List<String>,
82:         val accountCode: String? = null,
83:         val q: String? = null,
84:         val followed: Boolean = false,
85:         val repliedOnly: Boolean = false,
86:         val waitingReply: Boolean = false,
87:         val pendingOnly: Boolean = false,
88:         val direction: String? = null,
89:         val startTime: LocalDateTime? = null,
90:         val endTime: LocalDateTime? = null,
91:         val subject: String? = null,
92:         val label: String? = null,
93:         val recipientEmail: String? = null,
94:         val keyword: String? = null,
95:         /** 01 (I-4)：只看当前用户的挂起专家；默认 false 保持既有调用兼容。 */
96:         val suspendedOnly: Boolean = false,
97:         /** 01 (I-4)：只看当前用户的“已提供”专家；与 followed 同层 AND（同传得空集）。 */
98:         val providedOnly: Boolean = false,
99:         val reviewPendingOnly: Boolean = false,
100:         val expertScope: String = "ALL",
101:         val accessLevel: String = "ALL",
102:         val draftsOnly: Boolean = false
103:     )
104: 
105:     data class ConversationSummarySqlRow(
106:         val expertContactId: Long,
107:         val expertName: String?,
108:         val expertEmail: String,
109:         val orcidId: String,
110:         val receivedCount: Long,
111:         val sentCount: Long,
112:         val failedCount: Long,
113:         val pendingCount: Long,
114:         val latestEventAt: LocalDateTime,
115:         /**
116:          * 01 (I-1/I-4)：DB 真值三态（无行 = NONE）。`followed` 由同一次读取派生，
117:          * 绝不二次查询状态或布尔。
118:          */
119:         val progressStatus: MailboxProgressStatus
120:     ) {
121:         val followed: Boolean
122:             get() = progressStatus == MailboxProgressStatus.FOLLOWING
123:     }
124: 
125:     data class ConversationLatestMessageRow(
126:         val source: String,
127:         val id: Long,
128:         val direction: String,
129:         val accountCode: String?,
130:         val subject: String?,
131:         val preview: String?,
132:         val eventAt: LocalDateTime,
133:         val sendStatus: String?,
134:         val processStatus: String?
135:     )
136: 
137:     data class ConversationLatestInboundRow(
138:         val processingId: Long,
139:         val accountCode: String,
140:         val messageId: String?,
141:         val receivedAt: LocalDateTime
142:     )
143: 
144:     data class ConversationAccountCodesRow(
145:         val expertContactId: Long,
146:         val accountCodes: List<String>
147:     )
148: 
149:     data class ConversationMaterialCountRow(
150:         val expertContactId: Long,
151:         val materialCount: Long
152:     )
153: 
154:     /**
155:      * 当前页专家的真实挂起摘要（01/T3/I-1/I-3）。
156:      *
157:      * suspended/suspendReason 来自新表 `expert_mailbox_suspension` 的 (username, contactId)
158:      * 行存在性；suspensionPendingCount 是跨该专家所有真实账号的 MANUAL_REVIEW 聚合
159:      * （排除 SIMULATOR_NOOP 与不存在账号），与列表原 pendingCount（受账号筛选）语义分离。
160:      */
161:     data class ConversationSuspensionStateRow(
162:         val expertContactId: Long,
163:         val suspended: Boolean,
164:         val suspendReason: String?,
165:         val suspensionPendingCount: Long
166:     )
167: 
168:     data class ConversationMessageSqlRow(
169:         val source: String,
170:         val id: Long,
171:         val expertContactId: Long,
172:         val direction: String,
173:         val accountCode: String?,
174:         val subject: String?,
175:         val body: String?,
176:         val cleanedBody: String?,
177:         val eventAt: LocalDateTime,
178:         val sendStatus: String?,
179:         val processStatus: String?,
180:         val messageId: String?,
181:         val inReplyTo: String?
182:     )
183: 
184:     /** timeline 游标键（keyset，(event_at, source_rank, id) 严格更旧）。 */
185:     data class ConversationKeyset(
186:         val beforeTime: LocalDateTime,
187:         val beforeSourceRank: Int,
188:         val beforeId: Long
189:     )
190: 
191:     class TimelinePage(
192:         val rows: List<ConversationMessageSqlRow>,
193:         val hasMore: Boolean
194:     )
195: 
196:     // ------------------------------------------------------------------
197:     // summary / count
198:     // ------------------------------------------------------------------
199: 
200:     /** 会话专家数（受全部筛选/关注/等待/待处理约束；专家级去重后再计数）。 */
201:     fun countConversations(username: String, filter: ConversationFilter): Long {
202:         val eligibility = membership(filter)
203:         if (eligibility.impossible) return 0L
204:         val sql = """
205:             SELECT COUNT(*) FROM (
206:                 SELECT u.expert_contact_id AS expert_contact_id
207:                   FROM (${rangeUnionSql(includeBody = false)}) u
208:                   JOIN expert_contact ec ON ec.id = u.expert_contact_id
209:                  WHERE ${expertPredicates(username, filter, eligibility)}
210:                  GROUP BY u.expert_contact_id
211:                  ${havingClause(filter)}
212:             ) counted
213:         """.trimIndent()
214:         return jdbcTemplate.queryForObject(sql, params(username, filter), Long::class.java) ?: 0L
215:     }
216: 
217:     /** Page projection: one query for all contacts, without loading any draft content. */
218:     fun reviewPendingCountsByContacts(
219:         contactIds: List<Long>,
220:         accountCodes: List<String>,
221:         accountCode: String?
222:     ): Map<Long, Long> {
223:         if (contactIds.isEmpty() || accountCodes.isEmpty()) return emptyMap()
224:         val p = params("", ConversationFilter(accountCodes, accountCode))
225:             .addValue("contactIds", contactIds)
226:         return jdbcTemplate.query("""
227:             SELECT d.expert_contact_id, COUNT(*) AS pending_count
228:               FROM mailbox_review_draft d
229:               JOIN expert_contact ec ON ec.id = d.expert_contact_id
230:              WHERE d.expert_contact_id IN (:contactIds) AND ${reviewPendingPredicate()}
231:              GROUP BY d.expert_contact_id
232:         """.trimIndent(), p) { rs, _ -> rs.getLong("expert_contact_id") to rs.getLong("pending_count") }.toMap()
233:     }
234: 
235:     /** Draft count over the complete non-chip candidate scope, not merely the current page. */
236:     fun reviewPendingTotal(username: String, filter: ConversationFilter): Long {
237:         if (MailReadOnlyPolicy.isReader(username) || filter.accountCodes.isEmpty()) return 0L
238:         val scope = filter.copy(
239:             followed = false, providedOnly = false, repliedOnly = false,
240:             pendingOnly = false, waitingReply = false, suspendedOnly = false
241:         )
242:         val eligibility = membership(scope)
243:         if (eligibility.impossible) return 0L
244:         val sql = """
245:             SELECT COUNT(*) FROM mailbox_review_draft d
246:               JOIN (
247:                   SELECT DISTINCT u.expert_contact_id
248:                     FROM (${rangeUnionSql(includeBody = false)}) u
249:                     JOIN expert_contact ec ON ec.id = u.expert_contact_id
250:                    WHERE ${expertPredicates(username, scope, eligibility)}
251:               ) candidates ON candidates.expert_contact_id = d.expert_contact_id
252:              WHERE ${reviewPendingPredicate()}
253:         """.trimIndent()
254:         return jdbcTemplate.queryForObject(sql, params(username, scope), Long::class.java) ?: 0L
255:     }
256: 
257:     /** Admin toolbar units over all real conversations, independent of list filters/page. */
258:     fun filterCounts(accountCodes: List<String>): Pair<Long, Long> {
259:         if (accountCodes.isEmpty()) return 0L to 0L
260:         val scope = ConversationFilter(accountCodes)
261:         val writable = jdbcTemplate.queryForObject("""
262:             SELECT COUNT(DISTINCT u.expert_contact_id)
263:               FROM (${rangeUnionSql(includeBody = false)}) u
264:               JOIN expert_contact ec ON ec.id = u.expert_contact_id
265:              WHERE EXISTS (
266:                  SELECT 1 FROM expert_mail_read_access access_grant
267:                   WHERE access_grant.expert_contact_id = ec.id
268:                     AND access_grant.username = 'user1' AND access_grant.access_level = 'FULL')
269:         """.trimIndent(), params("", scope), Long::class.java) ?: 0L
270:         return writable to reviewPendingTotal("admin", scope)
271:     }
272: 
273:     /** Account existence, not automatic-send eligibility, determines review visibility. */
274:     private fun reviewPendingPredicate(): String = """
275:         d.state = 'SUBMITTED' AND d.deleted_at IS NULL
276:         AND d.account_code IN (:accountCodes)
277:         AND (:accountCode IS NULL OR d.account_code = :accountCode)
278:         AND EXISTS (
279:             SELECT 1 FROM mail_sender_account review_account
280:              WHERE review_account.account_code = d.account_code
281:                AND review_account.account_code <> '${MailSenderAccountService.SIMULATOR_ACCOUNT_CODE}')
282:     """.trimIndent()
283: 
284:     /**
285:      * 会话专家分页 summary（先 DB 聚合专家再分页）。每行只含聚合口径字段与最新事件
286:      * 时间，绝不含邮件正文；latestMessage/latestInbound/accountCodes/materialCount
287:      * 由调用方按本页 contact id 集合追加查询（[latestMessageByContacts] 等）。
288:      */
289:     fun pageConversations(
290:         username: String,
291:         filter: ConversationFilter,
292:         size: Int,
293:         offset: Long
294:     ): List<ConversationSummarySqlRow> {
295:         val eligibility = membership(filter)
296:         if (eligibility.impossible) return emptyList()
297:         val sql = """
298:             SELECT u.expert_contact_id AS expert_contact_id,
299:                    CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
300:                    CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
301:                    CONVERT(ec.orcid_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS orcid_id,
302:                    SUM(u.received_flag) AS received_count,
303:                    SUM(u.sent_flag) AS sent_count,
304:                    SUM(u.failed_flag) AS failed_count,
305:                    SUM(u.pending_flag) AS pending_count,
306:                    MAX(u.event_at) AS latest_event_at,
307:                    COALESCE((
308:                        SELECT ef.progress_status FROM expert_follow ef
309:                         WHERE ef.username = :username
310:                           AND ef.expert_contact_id = u.expert_contact_id
311:                    ), 'NONE') AS progress_status
312:               FROM (${rangeUnionSql(includeBody = false)}) u
313:               JOIN expert_contact ec ON ec.id = u.expert_contact_id
314:              WHERE ${expertPredicates(username, filter, eligibility)}
315:              GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
316:              ${havingClause(filter)}
317:              ORDER BY ${orderByClause(filter)}
318:              LIMIT :size OFFSET :offset
319:         """.trimIndent()
320:         val p = params(username, filter)
321:         p.addValue("size", size)
322:         p.addValue("offset", offset)
323:         return jdbcTemplate.query(sql, p) { rs, _ -> rs.toSummaryRow() }
324:     }
325: 
326:     /**
327:      * EXPLAIN 诊断（只读、不执行主查询）：供 mysqlIt 集成测试对同一份 SQL 做计划
328:      * 健全性检查（G-1）。MySQL 支持 EXPLAIN 预编译语句，命名参数原样绑定。
329:      * 与 [pageConversations] 共用 [orderByClause]，保证诊断 SQL 与执行 SQL 的排序不漂移。
330:      */
331:     fun explainConversationsPage(
332:         username: String,
333:         filter: ConversationFilter,
334:         size: Int,
335:         offset: Long
336:     ): List<Map<String, Any>> {
337:         val eligibility = membership(filter)
338:         val sql = """
339:             SELECT u.expert_contact_id AS expert_contact_id,
340:                    CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
341:                    CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
342:                    CONVERT(ec.orcid_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS orcid_id,
343:                    SUM(u.received_flag) AS received_count,
344:                    SUM(u.sent_flag) AS sent_count,
345:                    SUM(u.failed_flag) AS failed_count,
346:                    SUM(u.pending_flag) AS pending_count,
347:                    MAX(u.event_at) AS latest_event_at,
348:                    COALESCE((
349:                        SELECT ef.progress_status FROM expert_follow ef
350:                         WHERE ef.username = :username
351:                           AND ef.expert_contact_id = u.expert_contact_id
352:                    ), 'NONE') AS progress_status
353:               FROM (${rangeUnionSql(includeBody = false)}) u
354:               JOIN expert_contact ec ON ec.id = u.expert_contact_id
355:              WHERE ${expertPredicates(username, filter, eligibility)}
356:              GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
357:              ${havingClause(filter)}
358:              ORDER BY ${orderByClause(filter)}
359:              LIMIT :size OFFSET :offset
360:         """.trimIndent()
361:         val p = params(username, filter)
362:         p.addValue("size", size)
363:         p.addValue("offset", offset)
364:         return jdbcTemplate.queryForList("EXPLAIN $sql", p)
365:     }
366: 
367:     /**
368:      * 分页前排序的唯一私有 order 片段（I-1），page 与 explain 共用。
369:      *
370:      * 排序键：`latest_reply_at = MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END)`
371:      * （纯本地 SQL 投影，无表字段、无新响应字段）。"全部" 视图（非关注/待处理/旧 waitingReply）：
372:      * 待处理>0 在前 → 最近来信为 NULL（无来信，仅有发件）置底 → 最近来信倒序 → contactId DESC 稳定。
373:      * 关注/待处理/旧 waitingReply 省略 pending 首项（只按最近来信倒序 + 稳定 id）。
374:      * 聚合先 GROUP BY 再排序 LIMIT；count 不受排序影响。
375:      */
376:     private fun orderByClause(filter: ConversationFilter): String {
377:         val latestReply = "MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END)"
378:         val plainOrder = listOf(
379:             "CASE WHEN $latestReply IS NULL THEN 1 ELSE 0 END ASC",
380:             "$latestReply DESC",
381:             "u.expert_contact_id DESC"
382:         )
383:         val pendingFirst = if (filter.followed || filter.repliedOnly || filter.pendingOnly ||
384:             filter.waitingReply || filter.suspendedOnly || filter.providedOnly
385:         ) {
386:             emptyList()
387:         } else {
388:             listOf("CASE WHEN SUM(u.pending_flag) > 0 THEN 0 ELSE 1 END ASC")
389:         }
390:         return (pendingFirst + plainOrder).joinToString(", ")
391:     }
392: 
393:     // ------------------------------------------------------------------
394:     // summary 追加投影（本页 contact id 集合）
395:     // ------------------------------------------------------------------
396: 
397:     /** 每位专家的最近一条消息（双来源中 event 最大者；并列按 (source_rank, id) 稳定）。
398:      *  MySQL 5.7 兼容：同一账号范围/来源内 NOT EXISTS 反连接实现 groupwise max，
399:      *  禁 ROW_NUMBER/OVER（旧线上服务端解析窗口函数报 1064）。 */
400:     fun latestMessageByContacts(
401:         contactIds: List<Long>,
402:         accountCodes: List<String>,
403:         accountCode: String?
404:     ): Map<Long, ConversationLatestMessageRow> {
405:         if (contactIds.isEmpty()) return emptyMap()
406:         val sql = """
407:             SELECT u.expert_contact_id AS expert_contact_id,
408:                    u.source AS source,
409:                    u.id AS id,
410:                    u.direction AS direction,
411:                    u.account_code AS account_code,
412:                    u.subject AS subject,
413:                    u.preview AS preview,
414:                    u.event_at AS event_at,
415:                    u.send_status AS send_status,
416:                    u.process_status AS process_status
417:               FROM (${rangeUnionSql(includeBody = false)}) u
418:              WHERE u.expert_contact_id IN (:contactIds)
419:                AND NOT EXISTS (
420:                    SELECT 1
421:                      FROM (${rangeUnionSql(includeBody = false)}) newer
422:                     WHERE newer.expert_contact_id = u.expert_contact_id
423:                       AND (newer.event_at > u.event_at
424:                            OR (newer.event_at = u.event_at
425:                                AND newer.source_rank > u.source_rank)
426:                            OR (newer.event_at = u.event_at
427:                                AND newer.source_rank = u.source_rank
428:                                AND newer.id > u.id))
429:                )
430:         """.trimIndent()
431:         val p = MapSqlParameterSource()
432:             .addValue("contactIds", contactIds)
433:             .addValue("accountCodes", accountCodes)
434:             .addValue("accountCode", accountCode)
435:             .addValue("username", "")
436:         return jdbcTemplate.query(sql, p) { rs, _ ->
437:             rs.getLong("expert_contact_id") to rs.toLatestMessageRow()
438:         }.toMap()
439:     }
440: 
441:     /** 每位专家最近一封来信（真实 processing.id，绝不从 mail_record 推算）。
442:      *  MySQL 5.7 兼容：候选与反连接对手都限 INBOUND_PROCESSING，见 [latestMessageByContacts]。 */
443:     fun latestInboundByContacts(
444:         contactIds: List<Long>,
445:         accountCodes: List<String>,
446:         accountCode: String?
447:     ): Map<Long, ConversationLatestInboundRow> {
448:         if (contactIds.isEmpty()) return emptyMap()
449:         val sql = """
450:             SELECT u.expert_contact_id AS expert_contact_id,
451:                    u.id AS processing_id,
452:                    u.account_code AS account_code,
453:                    u.message_id AS message_id,
454:                    u.event_at AS received_at
455:               FROM (${rangeUnionSql(includeBody = false)}) u
456:              WHERE u.expert_contact_id IN (:contactIds)
457:                AND u.source = 'INBOUND_PROCESSING'
458:                AND NOT EXISTS (
459:                    SELECT 1
460:                      FROM (${rangeUnionSql(includeBody = false)}) newer
461:                     WHERE newer.expert_contact_id = u.expert_contact_id
462:                       AND newer.source = 'INBOUND_PROCESSING'
463:                       AND (newer.event_at > u.event_at
464:                            OR (newer.event_at = u.event_at
465:                                AND newer.source_rank > u.source_rank)
466:                            OR (newer.event_at = u.event_at
467:                                AND newer.source_rank = u.source_rank
468:                                AND newer.id > u.id))
469:                )
470:         """.trimIndent()
471:         val p = MapSqlParameterSource()
472:             .addValue("contactIds", contactIds)
473:             .addValue("accountCodes", accountCodes)
474:             .addValue("accountCode", accountCode)
475:             .addValue("username", "")
476:         return jdbcTemplate.query(sql, p) { rs, _ ->
477:             rs.getLong("expert_contact_id") to ConversationLatestInboundRow(
478:                 processingId = rs.getLong("processing_id"),
479:                 accountCode = rs.getString("account_code"),
480:                 messageId = rs.getString("message_id"),
481:                 receivedAt = rs.localDateTime("received_at")
482:             )
483:         }.toMap()
484:     }
485: 
486:     /** 每位专家在账号范围内出现过的发件账号（去重，排序稳定）。 */
487:     fun accountCodesByContacts(
488:         contactIds: List<Long>,
489:         accountCodes: List<String>,
490:         accountCode: String?
491:     ): Map<Long, List<String>> {
492:         if (contactIds.isEmpty()) return emptyMap()
493:         val sql = """
494:             SELECT u.expert_contact_id AS expert_contact_id,
495:                    GROUP_CONCAT(DISTINCT u.account_code ORDER BY u.account_code SEPARATOR ',') AS account_codes
496:               FROM (${rangeUnionSql(includeBody = false)}) u
497:              WHERE u.expert_contact_id IN (:contactIds)
498:              GROUP BY u.expert_contact_id
499:         """.trimIndent()
500:         val p = MapSqlParameterSource()
501:             .addValue("contactIds", contactIds)
502:             .addValue("accountCodes", accountCodes)
503:             .addValue("accountCode", accountCode)
504:             .addValue("username", "")
505:         return jdbcTemplate.query(sql, p) { rs, _ ->
506:             val codes = rs.getString("account_codes")
507:                 ?.split(",")
508:                 ?.filter { it.isNotBlank() }
509:                 ?: emptyList()
510:             rs.getLong("expert_contact_id") to codes
511:         }.toMap()
512:     }
513: 
514:     /** 每位专家全体资料数（expert_document；不随账号范围/筛选变化）。 */
515:     fun materialCountByContacts(contactIds: List<Long>): Map<Long, Long> {
516:         if (contactIds.isEmpty()) return emptyMap()
517:         val sql = """
518:             SELECT expert_contact_id AS expert_contact_id, COUNT(*) AS material_count
519:               FROM expert_document
520:              WHERE expert_contact_id IN (:contactIds)
521:              GROUP BY expert_contact_id
522:         """.trimIndent()
523:         return jdbcTemplate.query(sql, MapSqlParameterSource("contactIds", contactIds)) { rs, _ ->
524:             rs.getLong("expert_contact_id") to rs.getLong("material_count")
525:         }.toMap()
526:     }
527: 
528:     /**
529:      * 当前页专家挂起摘要（01/T3/I-1/I-3）：一次批量读取，禁止逐专家 N+1 与窗口函数。
530:      *
531:      * 两条参数化查询各覆盖当前页真实 id 集合（空集合直接返回，绝不执行 `IN ()`）：
532:      * 1. `expert_mailbox_suspension` 当前用户行 → suspended/reason（行存在即挂起 I-1）；
533:      * 2. `inbound_mail_processing JOIN mail_sender_account` 的 MANUAL_REVIEW 分组聚合 →
534:      *    跨账号 pending（排除 SIMULATOR_NOOP，不带 enabled/账号/date I-3）。
535:      *
536:      * 未出现在任一结果中的 id 填默认 (false, null, 0)，因此新表无行时也返回真实跨账号
537:      * pending，绝不把未挂起当 0 展示为已挂起。
538:      */
539:     fun suspensionStatesByContacts(
540:         username: String,
541:         contactIds: List<Long>
542:     ): Map<Long, ConversationSuspensionStateRow> {
543:         if (contactIds.isEmpty()) return emptyMap()
544:         val states = contactIds.associateWith { id ->
545:             ConversationSuspensionStateRow(
546:                 expertContactId = id,
547:                 suspended = false,
548:                 suspendReason = null,
549:                 suspensionPendingCount = 0L
550:             )
551:         }.toMutableMap()
552: 
553:         val suspensionSql = """
554:             SELECT expert_contact_id AS expert_contact_id, reason AS reason
555:               FROM expert_mailbox_suspension
556:              WHERE username = :username
557:                AND expert_contact_id IN (:contactIds)
558:         """.trimIndent()
559:         jdbcTemplate.query(
560:             suspensionSql,
561:             MapSqlParameterSource()
562:                 .addValue("username", username)
563:                 .addValue("contactIds", contactIds)
564:         ) { rs, _ -> rs.getLong("expert_contact_id") to rs.getString("reason") }
565:             .forEach { (id, reason) ->
566:                 states[id]?.let { states[id] = it.copy(suspended = true, suspendReason = reason) }
567:             }
568: 
569:         // 跨账号真实待处理：JOIN 排除不存在账号，显式排除模拟账号；不含 enabled / date / 账号范围。
570:         val pendingSql = """
571:             SELECT imp.expert_contact_id AS expert_contact_id, COUNT(*) AS pending_count
572:               FROM inbound_mail_processing imp
573:               JOIN mail_sender_account msa ON msa.account_code = imp.sender_account_code
574:              WHERE imp.expert_contact_id IN (:contactIds)
575:                AND imp.process_status = :manualReviewStatus
576:                AND imp.pending_owner_username = :username
577:                AND (:username <> 'user1' OR EXISTS (
578:                    SELECT 1 FROM expert_mail_read_access owner_access
579:                     WHERE owner_access.username = :username
580:                       AND owner_access.expert_contact_id = imp.expert_contact_id
581:                       AND owner_access.access_level = 'FULL'))
582:                AND msa.account_code <> :simulatorCode
583:              GROUP BY imp.expert_contact_id
584:         """.trimIndent()
585:         jdbcTemplate.query(
586:             pendingSql,
587:             MapSqlParameterSource()
588:                 .addValue("contactIds", contactIds)
589:                 .addValue("username", username)
590:                 .addValue("manualReviewStatus", PROCESS_STATUS_MANUAL_REVIEW)
591:                 .addValue("simulatorCode", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
592:         ) { rs, _ -> rs.getLong("expert_contact_id") to rs.getLong("pending_count") }
593:             .forEach { (id, count) ->
594:                 states[id]?.let { states[id] = it.copy(suspensionPendingCount = count) }
595:             }
596: 
597:         return states
598:     }
599: 
600:     // ------------------------------------------------------------------
601:     // 读取准入（02 I-2）
602:     // ------------------------------------------------------------------
603: 
604:     /**
605:      * 只读账号对单个专家的授权读取（每次请求一次 `expert_mail_read_access` 查询，无缓存）。
606:      *
607:      * 关系即状态：行存在即允许。grant 缺失与专家不存在**都**返回 false —— 调用方统一映射
608:      * 403 `MAIL_READ_FORBIDDEN`，绝不泄露专家是否存在。参数化 JDBC，username 只来自
609:      * session 身份；本查询不写任何表。
610:      */
611:     fun hasReadAccess(username: String, contactId: Long): Boolean {
612:         if (username.isBlank() || contactId <= 0L) return false
613:         val count = jdbcTemplate.queryForObject(
614:             """
615:             SELECT COUNT(*) FROM expert_mail_read_access
616:              WHERE username = :username AND expert_contact_id = :contactId
617:             """.trimIndent(),
618:             MapSqlParameterSource()
619:                 .addValue("username", username)
620:                 .addValue("contactId", contactId),
621:             Long::class.java
622:         ) ?: 0L
623:         return count > 0L
624:     }
625: 
626:     // ------------------------------------------------------------------
627:     // timeline（单专家消息）
628:     // ------------------------------------------------------------------
629:     /**
630:      * 单个专家的往来消息（默认取最新窗口；before 为空取最新 limit 条）。
631:      * keyset (event_at DESC, source_rank DESC, id DESC) 严格更旧；同时间戳不丢不重。
632:      * 返回行按 DESC 排列（新→旧），由 service 逆转为正序展示；hasMore = 还有更早消息。
633:      * 行包含完整 body/cleanedBody（仅当前专家 timeline 加载正文；summary 永不携带）。
634:      */
635:     fun timelineMessages(
636:         contactId: Long,
637:         filter: ConversationFilter,
638:         before: ConversationKeyset?,
639:         limit: Int
640:     ): TimelinePage {
641:         val sql = """
642:             SELECT u.expert_contact_id AS expert_contact_id,
643:                    u.source AS source,
644:                    u.id AS id,
645:                    u.direction AS direction,
646:                    u.account_code AS account_code,
647:                    u.subject AS subject,
648:                    u.body AS body,
649:                    u.cleaned_body AS cleaned_body,
650:                    u.event_at AS event_at,
651:                    u.send_status AS send_status,
652:                    u.process_status AS process_status,
653:                    u.message_id AS message_id,
654:                    u.in_reply_to AS in_reply_to
655:               FROM (${rangeUnionSql(includeBody = true)}) u
656:              WHERE u.expert_contact_id = :contactId
657:                AND (:beforeTime IS NULL
658:                     OR u.event_at < :beforeTime
659:                     OR (u.event_at = :beforeTime
660:                         AND (u.source_rank < :beforeSourceRank
661:                              OR (u.source_rank = :beforeSourceRank AND u.id < :beforeId))))
662:              ORDER BY u.event_at DESC, u.source_rank DESC, u.id DESC
663:              LIMIT :fetchLimit
664:         """.trimIndent()
665:         val p = params("", filter)
666:         p.addValue("contactId", contactId)
667:         p.addValue("beforeTime", before?.beforeTime)
668:         p.addValue("beforeSourceRank", before?.beforeSourceRank)
669:         p.addValue("beforeId", before?.beforeId)
670:         p.addValue("fetchLimit", limit + 1)
671:         val rows = jdbcTemplate.query(sql, p) { rs, _ -> rs.toMessageRow() }
672:         val hasMore = rows.size > limit
673:         return TimelinePage(rows = if (hasMore) rows.take(limit) else rows, hasMore = hasMore)
674:     }
675: 
676:     // ------------------------------------------------------------------
677:     // SQL 装配
678:     // ------------------------------------------------------------------
679: 
680:     /**
681:      * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
682:      * 两个分支的文本列显式 CONVERT + COLLATE utf8mb4_unicode_ci，避免 UNION 排序/物化
683:      * 时字符集冲突。includeBody 仅 timeline 需要（summary 永不读正文列）。
684:      * 注意：本 SQL 片段同时作为 count/summary/latest 系列/accountCodes/timeline 的数据基础，
685:      * 各列位置与别名必须保持一致。
686:      */
687:     private fun rangeUnionSql(includeBody: Boolean): String {
688:         val outboundBody = if (includeBody) {
689:             """,
690:                    CONVERT(mr.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
691:                    CONVERT(mr.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
692:         } else ""
693:         val inboundBody = if (includeBody) {
694:             """,
695:                    CONVERT(imp.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
696:                    CONVERT(imp.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
697:         } else ""
698:         return """
699:             SELECT mr.expert_contact_id AS expert_contact_id,
700:                    1 AS source_rank,
701:                    CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
702:                    mr.id AS id,
703:                    CONVERT('OUTBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
704:                    CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
705:                    COALESCE(mr.sent_at, mr.created_at) AS event_at,
706:                    CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
707:                    CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
708:                    CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
709:                    CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
710:                    CONVERT(mr.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
711:                    CONVERT(mr.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
712:                    CASE WHEN mr.send_status = 'SENT' THEN 1 ELSE 0 END AS sent_flag,
713:                    CASE WHEN mr.send_status = 'FAILED' THEN 1 ELSE 0 END AS failed_flag,
714:                    0 AS received_flag,
715:                    0 AS pending_flag$outboundBody
716:               FROM mail_record mr
717:              WHERE mr.direction = 'OUTBOUND'
718:                AND mr.expert_contact_id IS NOT NULL
719:                AND mr.sender_account_code IN (:accountCodes)
720:                AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
721:             UNION ALL
722:             SELECT imp.expert_contact_id AS expert_contact_id,
723:                    2 AS source_rank,
724:                    CONVERT('INBOUND_PROCESSING' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
725:                    imp.id AS id,
726:                    CONVERT('INBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
727:                    CONVERT(imp.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
728:                    imp.received_at AS event_at,
729:                    CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
730:                    CONVERT(imp.process_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
731:                    CONVERT(imp.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
732:                    CONVERT(SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
733:                    CONVERT(imp.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
734:                    CONVERT(imp.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
735:                    0 AS sent_flag,
736:                    0 AS failed_flag,
737:                    1 AS received_flag,
738:                    CASE WHEN imp.process_status = 'MANUAL_REVIEW'
739:                              AND imp.pending_owner_username = :username
740:                              AND (:username <> 'user1' OR EXISTS (
741:                                  SELECT 1 FROM expert_mail_read_access owner_access
742:                                   WHERE owner_access.username = :username
743:                                     AND owner_access.expert_contact_id = imp.expert_contact_id
744:                                     AND owner_access.access_level = 'FULL'))
745:                         THEN 1 ELSE 0 END AS pending_flag$inboundBody
746:               FROM inbound_mail_processing imp
747:              WHERE imp.expert_contact_id IS NOT NULL
748:                AND imp.sender_account_code IN (:accountCodes)
749:                AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
750:         """.trimIndent()
751:     }
752: 
753:     /** 消息级筛选在聚合口径上完全不可满足的组合（label 只存在于 INBOUND 侧）。 */
754:     private class MembershipEligibility(
755:         val impossible: Boolean,
756:         val outboundClause: String?,
757:         val inboundClause: String?
758:     )
759: 
760:     private fun membership(filter: ConversationFilter): MembershipEligibility {
761:         val hasMessageFilter = filter.direction != null ||
762:             filter.startTime != null ||
763:             filter.endTime != null ||
764:             filter.subject != null ||
765:             filter.label != null ||
766:             filter.recipientEmail != null ||
767:             filter.keyword != null
768:         if (!hasMessageFilter) {
769:             return MembershipEligibility(impossible = false, outboundClause = null, inboundClause = null)
770:         }
771:         // label 只存在于 inbound 侧；direction=OUTBOUND + label 无任何消息可满足 → 空集。
772:         if (filter.direction == DIRECTION_OUTBOUND && filter.label != null) {
773:             return MembershipEligibility(impossible = true, outboundClause = null, inboundClause = null)
774:         }
775:         val outboundEligible = filter.label == null && filter.direction != DIRECTION_INBOUND
776:         val inboundEligible = filter.direction != DIRECTION_OUTBOUND
777:         // 关键词只出现在消息 EXISTS 内（subject OR cleaned_body OR body；NULL 列照 SQL 空值
778:         // 处理）；summary SELECT 永不取回正文。邮箱语义沿旧 mailbox 口径：出站匹配
779:         // expert_contact.expert_email（本专家所有出站共享的联系人邮箱，在出站 EXISTS 内恒等），
780:         // 入站匹配 inbound_mail_processing.from_email（别名）。recipientEmail/keyword 与
781:         // 方向/日期/主题/label 全部 AND 在**同一封消息**的 EXISTS 内（I-2/X3）。
782:         val outboundClause = if (outboundEligible) {
783:             """
784:             EXISTS (
785:                 SELECT 1 FROM mail_record mro
786:                  WHERE mro.direction = 'OUTBOUND'
787:                    AND mro.expert_contact_id = u.expert_contact_id
788:                    AND mro.sender_account_code IN (:accountCodes)
789:                    AND (:accountCode IS NULL OR mro.sender_account_code = :accountCode)
790:                    AND (:startTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) >= :startTime)
791:                    AND (:endTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) < :endTime)
792:                    AND (:subject IS NULL OR mro.subject LIKE CONCAT('%', :subject, '%'))
793:                    AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
794:                    AND (:keyword IS NULL
795:                         OR mro.subject LIKE CONCAT('%', :keyword, '%')
796:                         OR mro.cleaned_body LIKE CONCAT('%', :keyword, '%')
797:                         OR mro.body LIKE CONCAT('%', :keyword, '%'))
798:             )
799:             """.trimIndent()
800:         } else null
801:         val inboundClause = if (inboundEligible) {
802:             """
803:             EXISTS (
804:                 SELECT 1 FROM inbound_mail_processing impi
805:                  WHERE impi.expert_contact_id = u.expert_contact_id
806:                    AND impi.sender_account_code IN (:accountCodes)
807:                    AND (:accountCode IS NULL OR impi.sender_account_code = :accountCode)
808:                    AND (:startTime IS NULL OR impi.received_at >= :startTime)
809:                    AND (:endTime IS NULL OR impi.received_at < :endTime)
810:                    AND (:subject IS NULL OR impi.subject LIKE CONCAT('%', :subject, '%'))
811:                    AND (:recipientEmail IS NULL OR impi.from_email LIKE CONCAT('%', :recipientEmail, '%'))
812:                    AND (:keyword IS NULL
813:                         OR impi.subject LIKE CONCAT('%', :keyword, '%')
814:                         OR impi.cleaned_body LIKE CONCAT('%', :keyword, '%')
815:                         OR impi.body LIKE CONCAT('%', :keyword, '%'))
816:                    AND (:label IS NULL OR EXISTS (
817:                          SELECT 1 FROM inbound_mail_tag imt
818:                           WHERE imt.inbound_processing_id = impi.id
819:                             AND imt.label = :label))
820:             )
821:             """.trimIndent()
822:         } else null
823:         return MembershipEligibility(
824:             impossible = false,
825:             outboundClause = outboundClause,
826:             inboundClause = inboundClause
827:         )
828:     }
829: 
830:     /**
831:      * WHERE 片段：q（真实 expert_contact 姓名/邮箱）+ followed/providedOnly 三态过滤 +
832:      * 挂起归类 + 消息级 membership。
833:      * membership 为单消息合取语义：方向/日期/主题/标签须被同一封消息满足；label 只
834:      * join 其对应来源（inbound_mail_tag → processing），绝不因多标签产生重复行。
835:      * outbound/inbound 两个方向 EXISTS 是 OR 互补组：组外加整层括号后再与 q/followed
836:      * AND（X1），否则 SQL 优先级会让单个方向 EXISTS 绕过 q/followed。
837:      */
838:     // Shared by membership/count and per-page navigation. Ordinary drafts require FULL;
839:     // review drafts remain available only in the READ workflow. Never mix owners.
840:     private fun ownDraftRows(): String = """
841:         SELECT d.expert_contact_id, d.id,
842:                CONVERT('MANUAL' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS kind,
843:                CONVERT(d.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
844:                CONVERT(d.state USING utf8mb4) COLLATE utf8mb4_unicode_ci AS state, d.updated_at
845:           FROM mailbox_reply_draft d
846:           JOIN expert_mail_read_access a ON a.expert_contact_id=d.expert_contact_id AND a.username=:username
847:          WHERE d.username=:username AND d.state='ACTIVE' AND a.access_level='FULL'
848:            AND (d.target_kind='OUTBOUND' OR EXISTS (SELECT 1 FROM inbound_mail_processing source
849:                 WHERE source.id=d.inbound_processing_id AND source.expert_contact_id=d.expert_contact_id
850:                   AND source.sender_account_code=d.account_scope))
851:         UNION ALL
852:         SELECT d.expert_contact_id, d.id,
853:                CONVERT('REVIEW' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS kind,
854:                CONVERT(d.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
855:                CONVERT(d.state USING utf8mb4) COLLATE utf8mb4_unicode_ci AS state, d.updated_at
856:           FROM mailbox_review_draft d
857:           JOIN expert_mail_read_access a ON a.expert_contact_id=d.expert_contact_id AND a.username=:username
858:          WHERE d.owner_username=:username AND d.deleted_at IS NULL AND a.access_level='READ'
859:     """.trimIndent()
860: 
861:     fun ownDraftsByContacts(username: String, contactIds: List<Long>): Map<Long, List<com.weibo.talentintroduction.mail.controller.ConversationOwnDraft>> {
862:         if (contactIds.isEmpty()) return emptyMap()
863:         return jdbcTemplate.query("SELECT * FROM (${ownDraftRows()}) drafts WHERE expert_contact_id IN (:contacts) ORDER BY updated_at DESC, id DESC",
864:             mapOf("username" to username, "contacts" to contactIds)) { rs, _ ->
865:             rs.getLong("expert_contact_id") to com.weibo.talentintroduction.mail.controller.ConversationOwnDraft(
866:                 rs.getLong("id"), rs.getString("kind"), rs.getString("subject"), rs.getString("state"))
867:         }.groupBy({ it.first }, { it.second })
868:     }
869: 
870:     private fun expertPredicates(username: String, filter: ConversationFilter, eligibility: MembershipEligibility): String {
871:         val clauses = mutableListOf<String>()
872:         if (filter.accessLevel != "ALL") {
873:             clauses += "EXISTS (SELECT 1 FROM expert_mail_read_access a WHERE a.expert_contact_id=ec.id AND a.username='user1' AND a.access_level=:accessLevel)"
874:         }
875:         if (filter.draftsOnly) {
876:             clauses += "EXISTS (SELECT 1 FROM (${ownDraftRows()}) own_draft WHERE own_draft.expert_contact_id=ec.id)"
877:         }
878:         clauses += """
879:             (:q IS NULL OR :q = ''
880:              OR ec.expert_name LIKE CONCAT('%', :q, '%')
881:              OR ec.expert_email LIKE CONCAT('%', :q, '%'))
882:         """.trimIndent()
883:         if (filter.reviewPendingOnly || filter.expertScope == "PENDING_REVIEW") {
884:             clauses += """
885:                 EXISTS (
886:                     SELECT 1 FROM mailbox_review_draft d
887:                      WHERE d.expert_contact_id = ec.id AND ${reviewPendingPredicate()})
888:             """.trimIndent()
889:         }
890:         if (filter.expertScope == "USER1_FULL") {
891:             clauses += """
892:                 EXISTS (
893:                     SELECT 1 FROM expert_mail_read_access full_access
894:                      WHERE full_access.expert_contact_id = ec.id
895:                        AND full_access.username = 'user1' AND full_access.access_level = 'FULL')
896:             """.trimIndent()
897:         }
898:         clauses += """
899:             (:followed = 0 OR EXISTS (
900:                 SELECT 1 FROM expert_follow eff
901:                  WHERE eff.username = :username
902:                    AND eff.expert_contact_id = u.expert_contact_id
903:                    AND eff.progress_status = 'FOLLOWING'))
904:         """.trimIndent()
905:         // 01 (I-4)：已提供与跟进中同层、互斥（同一行 progress_status 只能取一个值），
906:         // followed=true AND providedOnly=true 按 AND 得空集（不悄悄取其一）；参数化布尔
907:         // 开关，绝不被消息 OR 括号绕过。
908:         clauses += """
909:             (:providedOnly = 0 OR EXISTS (
910:                 SELECT 1 FROM expert_follow ef_provided
911:                  WHERE ef_provided.username = :username
912:                    AND ef_provided.expert_contact_id = u.expert_contact_id
913:                    AND ef_provided.progress_status = 'PROVIDED'))
914:         """.trimIndent()
915:         // 01 (I-4)：挂起归类与关注/待处理同层，OR 组外再 AND（q/followed 不可被绕过）。
916:         // suspendedOnly=true 用 EXISTS（有行）；待处理/已回复用 NOT EXISTS（无当前用户挂起行）。
917:         // 同传相互冲突条件按 AND 得空集，不隐藏改写参数。
918:         if (filter.suspendedOnly) {
919:             clauses += """
920:                 EXISTS (
921:                     SELECT 1 FROM expert_mailbox_suspension ems_suspended
922:                      WHERE ems_suspended.username = :username
923:                        AND ems_suspended.expert_contact_id = u.expert_contact_id)
924:             """.trimIndent()
925:         }
926:         if (filter.pendingOnly || filter.repliedOnly) {
927:             clauses += """
928:                 NOT EXISTS (
929:                     SELECT 1 FROM expert_mailbox_suspension ems_not_suspended
930:                      WHERE ems_not_suspended.username = :username
931:                        AND ems_not_suspended.expert_contact_id = u.expert_contact_id)
932:             """.trimIndent()
933:         }
934:         if (filter.repliedOnly) {
935:             clauses += """
936:                 NOT EXISTS (
937:                     SELECT 1 FROM expert_follow ef_replied
938:                      WHERE ef_replied.username = :username
939:                        AND ef_replied.expert_contact_id = u.expert_contact_id)
940:             """.trimIndent()
941:             clauses += """
942:                 EXISTS (
943:                     SELECT 1 FROM mail_record mr_replied
944:                      WHERE mr_replied.expert_contact_id = u.expert_contact_id
945:                        AND mr_replied.direction = 'OUTBOUND'
946:                        AND mr_replied.send_status = 'SENT'
947:                        AND mr_replied.sender_account_code IN (:accountCodes)
948:                        AND (:accountCode IS NULL OR mr_replied.sender_account_code = :accountCode))
949:             """.trimIndent()
950:             clauses += """
951:                 EXISTS (
952:                     SELECT 1 FROM inbound_mail_processing imp_replied
953:                      WHERE imp_replied.expert_contact_id = u.expert_contact_id
954:                        AND imp_replied.sender_account_code IN (:accountCodes)
955:                        AND (:accountCode IS NULL OR imp_replied.sender_account_code = :accountCode)
956:                        AND imp_replied.id > COALESCE((
957:                            SELECT erd.last_inbound_id FROM expert_replied_dismissal erd
958:                             WHERE erd.username = :username
959:                               AND erd.expert_contact_id = u.expert_contact_id
960:                        ), 0))
961:             """.trimIndent()
962:         }
963:         val membershipClauses = listOfNotNull(eligibility.outboundClause, eligibility.inboundClause)
964:         if (membershipClauses.isNotEmpty()) {
965:             clauses += "(\n${membershipClauses.joinToString("\n      OR ")}\n      )"
966:         }
967:         // 02 (I-1)：只读账号的成员过滤必须在所有 OR 条件之外（每条既有子句都已带括号），
968:         // 与 q/followed/provided/挂起/消息级 EXISTS 同层 AND。关系即状态：无 grant 行即
969:         // 不可见，空授权集自然得到 total=0/items=[]（绝不在应用层取全量后再过滤）。
970:         // `:username` 是固定绑定参数，绝不做字符串拼接。
971:         if (MailReadOnlyPolicy.isReader(username)) {
972:             clauses += """
973:                 EXISTS (
974:                     SELECT 1 FROM expert_mail_read_access emra
975:                      WHERE emra.username = :username
976:                        AND emra.expert_contact_id = u.expert_contact_id)
977:             """.trimIndent()
978:         }
979:         return clauses.joinToString("\n   AND ")
980:     }
981: 
982:     private fun havingClause(filter: ConversationFilter): String {
983:         val conditions = mutableListOf<String>()
984:         if (filter.waitingReply) {
985:             // I-2：全历史账号范围 receivedCount=0 且 sentCount>0（FAILED 不计为 sent）。
986:             conditions += "SUM(u.received_flag) = 0 AND SUM(u.sent_flag) > 0"
987:         }
988:         if (filter.pendingOnly) {
989:             // 待处理谓词复用 processing 现有 MANUAL_REVIEW 谓词。
990:             conditions += "SUM(u.pending_flag) > 0"
991:         }
992:         return if (conditions.isEmpty()) "" else "HAVING ${conditions.joinToString(" AND ")}"
993:     }
994: 
995:     private fun params(username: String, filter: ConversationFilter): MapSqlParameterSource =
996:         MapSqlParameterSource()
997:             .addValue("username", username)
998:             .addValue("accessLevel", filter.accessLevel)
999:             .addValue("accountCodes", filter.accountCodes)
1000:             .addValue("accountCode", filter.accountCode)
1001:             .addValue("q", filter.q?.takeIf { it.isNotBlank() })
1002:             .addValue("followed", if (filter.followed) 1 else 0)
1003:             .addValue("providedOnly", if (filter.providedOnly) 1 else 0)
1004:             .addValue("startTime", filter.startTime)
1005:             .addValue("endTime", filter.endTime)
1006:             .addValue("subject", filter.subject?.takeIf { it.isNotBlank() })
1007:             .addValue("label", filter.label?.takeIf { it.isNotBlank() })
1008:             .addValue("recipientEmail", filter.recipientEmail?.takeIf { it.isNotBlank() })
1009:             .addValue("keyword", filter.keyword?.takeIf { it.isNotBlank() })
1010: 
1011:     private fun ResultSet.toSummaryRow(): ConversationSummarySqlRow =
1012:         ConversationSummarySqlRow(
1013:             expertContactId = getLong("expert_contact_id"),
1014:             expertName = getString("expert_name"),
1015:             expertEmail = getString("expert_email"),
1016:             orcidId = getString("orcid_id"),
1017:             receivedCount = getLong("received_count"),
1018:             sentCount = getLong("sent_count"),
1019:             failedCount = getLong("failed_count"),
1020:             pendingCount = getLong("pending_count"),
1021:             latestEventAt = localDateTime("latest_event_at"),
1022:             progressStatus = MailboxProgressStatus.valueOf(getString("progress_status"))
1023:         )
1024: 
1025:     private fun ResultSet.toLatestMessageRow(): ConversationLatestMessageRow =
1026:         ConversationLatestMessageRow(
1027:             source = getString("source"),
1028:             id = getLong("id"),
1029:             direction = getString("direction"),
1030:             accountCode = getString("account_code"),
1031:             subject = getString("subject"),
1032:             preview = getString("preview"),
1033:             eventAt = localDateTime("event_at"),
1034:             sendStatus = getString("send_status"),
1035:             processStatus = getString("process_status")
1036:         )
1037: 
1038:     private fun ResultSet.toMessageRow(): ConversationMessageSqlRow =
1039:         ConversationMessageSqlRow(
1040:             source = getString("source"),
1041:             id = getLong("id"),
1042:             expertContactId = getLong("expert_contact_id"),
1043:             direction = getString("direction"),
1044:             accountCode = getString("account_code"),
1045:             subject = getString("subject"),
1046:             body = getString("body"),
1047:             cleanedBody = getString("cleaned_body"),
1048:             eventAt = localDateTime("event_at"),
1049:             sendStatus = getString("send_status"),
1050:             processStatus = getString("process_status"),
1051:             messageId = getString("message_id"),
1052:             inReplyTo = getString("in_reply_to")
1053:         )
1054: 
1055: 
1056:     private fun ResultSet.localDateTime(column: String): LocalDateTime {
1057:         val ts = getTimestamp(column) ?: error("column $column is null")
1058:         return ts.toLocalDateTime()
1059:     }
1060: 
1061:     companion object {
1062:         const val DIRECTION_INBOUND = "INBOUND"
1063:         const val DIRECTION_OUTBOUND = "OUTBOUND"
1064:         const val SOURCE_MAIL_RECORD = "MAIL_RECORD"
1065:         const val SOURCE_INBOUND_PROCESSING = "INBOUND_PROCESSING"
1066: 
1067:         /** 01 (I-3)：跨账号挂起摘要复用的待处理权威谓词。 */
1068:         const val PROCESS_STATUS_MANUAL_REVIEW = "MANUAL_REVIEW"
1069:     }
1070: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt

```text
1: package com.weibo.talentintroduction.mail.repository
2: 
3: import com.fasterxml.jackson.databind.ObjectMapper
4: import com.weibo.talentintroduction.mail.service.*
5: import org.springframework.jdbc.core.RowMapper
6: import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
7: import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
8: import org.springframework.jdbc.support.GeneratedKeyHolder
9: import org.springframework.stereotype.Repository
10: import org.springframework.transaction.support.TransactionSynchronizationManager
11: import java.sql.ResultSet
12: import java.time.LocalDateTime
13: 
14: @Repository
15: class MailReviewDraftRepository(private val jdbc: NamedParameterJdbcTemplate, private val objectMapper: ObjectMapper) {
16:     private val mapper = RowMapper { r: ResultSet, _: Int ->
17:         MailReviewDraftRow(r.getLong("id"), r.getString("owner_username"), r.getLong("expert_contact_id"),
18:             r.getString("client_request_id"), r.getString("target_kind"), r.getLong("target_id"), r.getString("account_code"),
19:             r.getLong("version"), MailReviewDraftState.valueOf(r.getString("state")), r.getString("subject"),
20:             r.getString("html_body"), r.getString("text_body"), r.time("submitted_at"), r.getString("return_reason"),
21:             r.getString("reviewed_by"), r.time("reviewed_at"), r.getString("approved_context_token"), r.getString("send_request_id"),
22:             (r.getObject("send_attempt_id") as? Number)?.toLong(), r.time("deleted_at"),
23:             requireNotNull(r.time("created_at")), requireNotNull(r.time("updated_at")))
24:     }
25:     private fun ResultSet.time(column: String): LocalDateTime? = getTimestamp(column)?.toLocalDateTime()
26:     private fun transaction() = check(TransactionSynchronizationManager.isActualTransactionActive()) { "Review writes and locks require a transaction" }
27: 
28:     fun find(id: Long, lock: Boolean = false): MailReviewDraftRow? {
29:         if (lock) transaction()
30:         return jdbc.query("SELECT * FROM mailbox_review_draft WHERE id=:id" + if (lock) " FOR UPDATE" else "", mapOf("id" to id), mapper).singleOrNull()
31:     }
32:     fun findByRequest(owner: String, clientRequestId: String): MailReviewDraftRow? = jdbc.query(
33:         "SELECT * FROM mailbox_review_draft WHERE owner_username=:owner AND client_request_id=:request",
34:         mapOf("owner" to owner, "request" to clientRequestId), mapper).singleOrNull()
35: 
36:     private fun params(r: MailReviewDraftRow) = MapSqlParameterSource()
37:         .addValue("id", r.id).addValue("owner", r.ownerUsername).addValue("contact", r.contactId)
38:         .addValue("request", r.clientRequestId).addValue("kind", r.targetKind).addValue("target", r.targetId)
39:         .addValue("account", r.accountCode).addValue("version", r.version).addValue("state", r.state.name)
40:         .addValue("subject", r.subject).addValue("html", r.html).addValue("text", r.text)
41:         .addValue("submitted", r.submittedAt).addValue("reason", r.returnReason).addValue("reviewer", r.reviewedBy)
42:         .addValue("reviewed", r.reviewedAt).addValue("token", r.approvedContextToken).addValue("sendRequest", r.sendRequestId)
43:         .addValue("attempt", r.sendAttemptId).addValue("deleted", r.deletedAt).addValue("created", r.createdAt).addValue("updated", r.updatedAt)
44: 
45:     fun insert(row: MailReviewDraftRow): MailReviewDraftRow {
46:         transaction()
47:         require(row.version == 1L && row.state == MailReviewDraftState.DRAFT && row.ownerUsername == "user1")
48:         val keys = GeneratedKeyHolder()
49:         jdbc.update("""INSERT INTO mailbox_review_draft
50:             (owner_username,expert_contact_id,client_request_id,target_kind,target_id,account_code,version,state,subject,html_body,text_body,created_at,updated_at)
51:             VALUES (:owner,:contact,:request,:kind,:target,:account,1,'DRAFT',:subject,:html,:text,:created,:updated)""", params(row), keys)
52:         return row.copy(id = requireNotNull(keys.key).toLong())
53:     }
54: 
55:     /** Identity, first submission, and send binding are not writable through a content/state CAS. */
56:     fun update(row: MailReviewDraftRow, expectedVersion: Long, expectedState: MailReviewDraftState): Boolean {
57:         transaction()
58:         require(expectedVersion >= 1 && row.version == expectedVersion + 1)
59:         return jdbc.update("""UPDATE mailbox_review_draft SET version=version+1,state=:state,subject=:subject,
60:             html_body=:html,text_body=:text,submitted_at=COALESCE(submitted_at,:submitted),return_reason=:reason,
61:             reviewed_by=:reviewer,reviewed_at=:reviewed,approved_context_token=:token,send_request_id=:sendRequest,
62:             deleted_at=:deleted,updated_at=:updated
63:             WHERE id=:id AND owner_username=:owner AND version=:expectedVersion AND state=:expectedState
64:               AND deleted_at IS NULL AND state<>'APPROVED'""",
65:             params(row).addValue("expectedVersion", expectedVersion).addValue("expectedState", expectedState.name)) == 1
66:     }
67: 
68:     fun lockAccess(owner: String, contactId: Long): Boolean {
69:         transaction()
70:         return jdbc.queryForList("SELECT username FROM expert_mail_read_access WHERE username=:owner AND expert_contact_id=:contact FOR UPDATE",
71:             mapOf("owner" to owner, "contact" to contactId)).isNotEmpty()
72:     }
73:     fun contactName(contactId: Long): String? = jdbc.query("SELECT expert_name FROM expert_contact WHERE id=:id", mapOf("id" to contactId),
74:         RowMapper { r, _ -> r.getString("expert_name") }).singleOrNull()
75: 
76:     fun resolveTarget(contactId: Long): MailReviewDraftTarget? {
77:         val p = mapOf("contact" to contactId, "simulator" to MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
78:         fun target(sql: String) = jdbc.query(sql, p, RowMapper { r, _ -> MailReviewDraftTarget(r.getString("kind"), r.getLong("id"), r.getString("sender_account_code")) }).firstOrNull()
79:         return target("""SELECT 'INBOUND' AS kind,p.id,p.sender_account_code FROM inbound_mail_processing p
80:             JOIN mail_sender_account a ON a.account_code=p.sender_account_code
81:             WHERE p.expert_contact_id=:contact AND a.account_code<>:simulator ORDER BY p.received_at DESC,p.id DESC LIMIT 1""")
82:             ?: target("""SELECT 'OUTBOUND' AS kind,m.id,m.sender_account_code FROM mail_record m
83:                 JOIN mail_sender_account a ON a.account_code=m.sender_account_code
84:                 WHERE m.expert_contact_id=:contact AND m.direction='OUTBOUND' AND m.send_status='SENT'
85:                   AND a.account_code<>:simulator ORDER BY COALESCE(m.sent_at,m.created_at) DESC,m.id DESC LIMIT 1""")
86:     }
87: 
88:     /** Resolve only the immutable reply target, never a caller-supplied subject or latest unrelated mail. */
89:     fun targetSubject(contactId: Long, kind: String, targetId: Long, account: String): String {
90:         val table = when (kind) { "INBOUND" -> "inbound_mail_processing"; "OUTBOUND" -> "mail_record"; else -> error("Invalid target") }
91:         val subjects = jdbc.query("SELECT subject FROM $table WHERE id=:id AND expert_contact_id=:contact AND sender_account_code=:account",
92:             mapOf("id" to targetId, "contact" to contactId, "account" to account), RowMapper { r, _ -> r.getString("subject").orEmpty() })
93:         if (subjects.size != 1) throw MailReviewDraftException(422, "REVIEW_TARGET_NOT_FOUND", "原邮件不存在，无法创建回复")
94:         val subject = MailSubjectDecoder.decode(subjects.single()).orEmpty().trim()
95:         return if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject".trim()
96:     }
97: 
98:     fun validTarget(row: MailReviewDraftRow): Boolean {
99:         val target = when (row.targetKind) {
100:             "INBOUND" -> """SELECT p.id FROM inbound_mail_processing p JOIN mail_sender_account a
101:                 ON a.account_code=p.sender_account_code JOIN expert_contact c ON c.id=p.expert_contact_id
102:                 WHERE p.id=:target AND p.expert_contact_id=:contact AND p.sender_account_code=:account"""
103:             "OUTBOUND" -> """SELECT m.id FROM mail_record m JOIN mail_sender_account a
104:                 ON a.account_code=m.sender_account_code JOIN expert_contact c ON c.id=m.expert_contact_id
105:                 WHERE m.id=:target AND m.expert_contact_id=:contact AND m.sender_account_code=:account
106:                 AND m.direction='OUTBOUND' AND m.send_status='SENT'"""
107:             else -> return false
108:         }
109:         return row.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE && jdbc.queryForList(target,
110:             mapOf("target" to row.targetId, "contact" to row.contactId, "account" to row.accountCode)).size == 1
111:     }
112: 
113:     fun bindAttempt(row: MailReviewDraftRow, attemptId: Long) {
114:         transaction()
115:         check(jdbc.update("""UPDATE mailbox_review_draft SET send_attempt_id=:attempt
116:             WHERE id=:id AND version=:version AND state='APPROVED' AND deleted_at IS NULL
117:             AND (send_attempt_id IS NULL OR send_attempt_id=:attempt)""",
118:             mapOf("id" to row.id, "version" to row.version, "attempt" to attemptId)) == 1) { "Review attempt binding conflict" }
119:     }
120: 
121:     fun sendProjection(row: MailReviewDraftRow): MailReviewSendProjection {
122:         val attempt = row.sendAttemptId ?: return MailReviewSendProjection("NOT_SENT", null)
123:         return jdbc.query("""SELECT a.status,m.id AS record_id FROM mail_send_attempt a
124:             LEFT JOIN mail_record m ON m.mail_send_attempt_id=a.id AND m.send_status='SENT'
125:                 AND m.direction='OUTBOUND' AND m.expert_contact_id=:contact AND m.sender_account_code=:account
126:                 AND m.subject=:subject AND m.body=:text AND m.message_id=a.message_id
127:             WHERE a.id=:attempt""", mapOf("attempt" to attempt, "contact" to row.contactId,
128:             "account" to row.accountCode, "subject" to row.subject, "text" to row.text),
129:             RowMapper { r, _ -> MailReviewSendProjection(r.getString("status"),
130:                 if (r.getString("status") == "SENT") (r.getObject("record_id") as? Number)?.toLong() else null) })
131:             .singleOrNull() ?: MailReviewSendProjection("NOT_SENT", null)
132:     }
133: 
134:     fun event(id: Long): MailReviewDraftEvent? = jdbc.query(
135:         "SELECT * FROM mailbox_review_draft_event WHERE id=:id", mapOf("id" to id), eventMapper).singleOrNull()
136: 
137:     fun appendSendResult(row: MailReviewDraftRow, attemptId: Long, result: String, mailRecordId: Long?, now: LocalDateTime) {
138:         transaction()
139:         val type = if (result == "SENT") "SEND_SUCCEEDED" else "SEND_RESULT"
140:         val exists = jdbc.queryForObject("""SELECT COUNT(*) FROM mailbox_review_draft_event
141:             WHERE draft_id=:id AND draft_version=:version AND event_type=:type
142:             AND JSON_EXTRACT(payload_json,'$.attemptId')=:attempt
143:             AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.result'))=:result""",
144:             mapOf("id" to row.id, "version" to row.version, "type" to type, "attempt" to attemptId, "result" to result), Long::class.java) ?: 0
145:         if (exists == 0L) {
146:             val payload = linkedMapOf<String, Any?>("attemptId" to attemptId, "result" to result)
147:             if (mailRecordId != null) payload["mailRecordId"] = mailRecordId
148:             appendEvent(row.id, row.version, type, "admin", payload, now)
149:         }
150:     }
151: 
152:     private fun visibility(actor: String, contactId: Long?, includeDeleted: Boolean): String {
153:         val role = when (actor) {
154:             "admin" -> "d.owner_username='user1' AND d.submitted_at IS NOT NULL"
155:             "user1" -> "d.owner_username=:actor AND EXISTS (SELECT 1 FROM expert_mail_read_access g WHERE g.username=:actor AND g.expert_contact_id=d.expert_contact_id)"
156:             else -> "1=0"
157:         }
158:         return " WHERE $role" + (if (contactId == null) "" else " AND d.expert_contact_id=:contact") +
159:             (if (includeDeleted) "" else " AND d.deleted_at IS NULL")
160:     }
161:     fun list(actor: String, contactId: Long?, page: Int, size: Int, includeDeleted: Boolean): List<MailReviewDraftRow> = jdbc.query(
162:         "SELECT d.* FROM mailbox_review_draft d" + visibility(actor, contactId, includeDeleted) + " ORDER BY d.updated_at DESC,d.id DESC LIMIT :size OFFSET :offset",
163:         mapOf("actor" to actor, "contact" to contactId, "size" to size, "offset" to page.toLong() * size), mapper)
164:     fun count(actor: String, contactId: Long?, includeDeleted: Boolean): Long = jdbc.queryForObject(
165:         "SELECT COUNT(*) FROM mailbox_review_draft d" + visibility(actor, contactId, includeDeleted),
166:         mapOf("actor" to actor, "contact" to contactId), Long::class.java) ?: 0L
167:     fun commentCount(id: Long): Long = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=:id AND event_type='COMMENT'", mapOf("id" to id), Long::class.java) ?: 0L
168: 
169:     fun appendEvent(draftId: Long, draftVersion: Long, eventType: String, actor: String, payload: Map<String, Any?>, createdAt: LocalDateTime): MailReviewDraftEvent {
170:         transaction()
171:         validatePayload(eventType, payload)
172:         val key = GeneratedKeyHolder()
173:         jdbc.update("""INSERT INTO mailbox_review_draft_event (draft_id,draft_version,event_type,actor_username,payload_json,created_at)
174:             VALUES (:draft,:version,:type,:actor,:payload,:created)""", MapSqlParameterSource()
175:             .addValue("draft", draftId).addValue("version", draftVersion).addValue("type", eventType).addValue("actor", actor)
176:             .addValue("payload", objectMapper.writeValueAsString(payload)).addValue("created", createdAt), key)
177:         return MailReviewDraftEvent(requireNotNull(key.key).toLong(), draftId, draftVersion, eventType, actor, payload, createdAt)
178:     }
179:     private val eventMapper = RowMapper { r: ResultSet, _: Int ->
180:         val type = r.getString("event_type")
181:         val node = objectMapper.factory.createParser(r.getString("payload_json")).use { parser ->
182:             parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
183:             val parsed = objectMapper.readTree<com.fasterxml.jackson.databind.JsonNode>(parser)
184:             require(parser.nextToken() == null) { "Trailing review event payload" }
185:             parsed
186:         }
187:         require(node.isObject) { "Invalid review event payload" }
188:         val payload = node.fields().asSequence().associate { (key, value) ->
189:             key to when { value.isTextual -> value.textValue(); value.isIntegralNumber && value.canConvertToLong() -> value.longValue(); value.isNull -> null; else -> error("Invalid review event value") }
190:         }
191:         validatePayload(type, payload)
192:         MailReviewDraftEvent(r.getLong("id"), r.getLong("draft_id"), r.getLong("draft_version"), type,
193:             r.getString("actor_username"), payload, requireNotNull(r.time("created_at")))
194:     }
195:     fun events(id: Long, beforeId: Long?, limit: Int): List<MailReviewDraftEvent> = jdbc.query(
196:         "SELECT * FROM mailbox_review_draft_event WHERE draft_id=:id" + (if (beforeId == null) "" else " AND id<:before") + " ORDER BY id DESC LIMIT :limit",
197:         mapOf("id" to id, "before" to beforeId, "limit" to limit), eventMapper)
198:     fun creationEvent(draftId: Long): MailReviewDraftEvent? = jdbc.query(
199:         "SELECT * FROM mailbox_review_draft_event WHERE draft_id=:id AND event_type='CREATE' ORDER BY id LIMIT 1", mapOf("id" to draftId), eventMapper).singleOrNull()
200: 
201:     private fun validatePayload(type: String, payload: Map<String, Any?>) {
202:         val snapshots = setOf("CREATE", "SAVE", "SUBMIT", "WITHDRAW", "RETURN", "APPROVE", "DELETE")
203:         val required: Set<String>
204:         val optional: Set<String>
205:         when {
206:             type in snapshots -> { required = setOf("subject", "html", "text", "state"); optional = setOf("reason") }
207:             type == "COMMENT" -> { required = setOf("text"); optional = emptySet() }
208:             type == "CONTEXT_CONFIRMED" -> { required = setOf("contextToken"); optional = emptySet() }
209:             type in setOf("SEND_CLAIMED", "SEND_SUCCEEDED", "SEND_RESULT", "SEND_FAILED", "SEND_UNKNOWN") -> { required = setOf("attemptId", "result"); optional = setOf("mailRecordId") }
210:             else -> throw IllegalStateException("Unknown review event type")
211:         }
212:         require(payload.keys.containsAll(required) && payload.keys.all { it in required || it in optional }) { "Invalid review event schema" }
213:         payload.forEach { (key, value) ->
214:             if (key == "attemptId" || key == "mailRecordId") require(value is Long && value > 0)
215:             else require(value is String) { "Invalid review event string" }
216:         }
217:         if (type in snapshots) MailReviewDraftState.valueOf(payload["state"] as String)
218:         if (type == "COMMENT" || type == "RETURN") require((payload[if (type == "COMMENT") "text" else "reason"] as? String)?.let { it.isNotBlank() && it.length <= 2000 } == true)
219:         if (type == "CONTEXT_CONFIRMED") require((payload["contextToken"] as String).matches(Regex("[0-9a-f]{64}")))
220:     }
221: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.audit.domain.OperatorActionType
4: import com.weibo.talentintroduction.audit.service.OperatorActionLogService
5: import com.weibo.talentintroduction.campaign.domain.ExpertContact
6: import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
7: import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
8: import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
9: import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
10: import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
11: import com.weibo.talentintroduction.mail.domain.MailRecord
12: import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
13: import com.weibo.talentintroduction.mail.domain.MailSenderAccount
14: import com.weibo.talentintroduction.mail.domain.MailRecordRagFact
15: import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
16: import com.weibo.talentintroduction.mail.domain.TriggeredBy
17: import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
18: import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
19: import com.weibo.talentintroduction.mail.repository.MailRecordRagFactRepository
20: import com.weibo.talentintroduction.mail.repository.MailRecordRepository
21: import com.weibo.talentintroduction.llm.controller.IntentCoverageResponse
22: import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
23: import com.weibo.talentintroduction.llm.service.AiReplyAction
24: import com.weibo.talentintroduction.llm.service.AiReplyActionPolicy
25: import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
26: import com.weibo.talentintroduction.llm.service.AiReplyDraftService
27: import com.weibo.talentintroduction.llm.service.AiReplyContextService
28: import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
29: import com.weibo.talentintroduction.llm.service.QaFactSelectionService
30: import com.weibo.talentintroduction.llm.service.RequestFactItem
31: import com.weibo.talentintroduction.llm.service.ResolvedQaRules
32: import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
33: import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
34: import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
35: import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
36: import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
37: import com.weibo.talentintroduction.llm.service.TrustReplySourceType
38: import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
39: import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
40: import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
41: import com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus
42: import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
43: import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
44: import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
45: import com.weibo.talentintroduction.qa.domain.QaRule
46: import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
47: import com.weibo.talentintroduction.qa.repository.QaRuleRepository
48: import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
49: import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
50: import com.weibo.talentintroduction.qa.service.GapItem
51: import com.weibo.talentintroduction.qa.service.SuggestQaRule
52: import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
53: import org.springframework.http.HttpStatus
54: import org.springframework.stereotype.Service
55: import org.springframework.transaction.annotation.Transactional
56: import org.springframework.web.server.ResponseStatusException
57: import org.slf4j.LoggerFactory
58: import java.time.Instant
59: import java.time.LocalDateTime
60: 
61: @Service
62: class PendingMailOperationService(
63:     private val inboundMailProcessingRepository: InboundMailProcessingRepository,
64:     private val expertContactRepository: ExpertContactRepository,
65:     private val expertOperatorStatusService: ExpertOperatorStatusService,
66:     private val expertIndexLevelOperationService: ExpertIndexLevelOperationService,
67:     private val mailSenderAccountService: MailSenderAccountService,
68:     private val mailDeliveryService: MailDeliveryService,
69:     private val mailRecordRepository: MailRecordRepository,
70:     private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
71:     private val operatorActionLogService: OperatorActionLogService,
72:     private val qaRuleRepository: QaRuleRepository,
73:     private val qaCategoryRepository: QaCategoryRepository,
74:     private val qaFactSelectionService: QaFactSelectionService,
75:     private val aiReplyDraftService: AiReplyDraftService,
76:     private val aiReplyContextService: AiReplyContextService,
77:     private val aiReplyHighRiskClaimValidator: AiReplyHighRiskClaimValidator,
78:     private val mailBodyCleaner: MailBodyCleaner,
79:     private val mailContentService: MailContentService,
80:     private val mailVariableService: MailVariableService,
81:     private val manualReplySendAttemptService: ManualReplySendAttemptService,
82:     private val trustReplyWorkbenchService: TrustReplyWorkbenchService,
83:     private val unsupportedAnswerIndexService: UnsupportedAnswerIndexService,
84:     private val emailSuppressionService: EmailSuppressionService,
85:     // 03 (T1/I-1): 会议日历校验/重建 —— 发送时用服务端解析后的真实 account/contact/
86:     // processing 与 01 validateAndBuild 重算（禁止默认 null 让生产依赖悄悄消失）。
87:     private val meetingConfirmationService: MeetingConfirmationService,
88:     private val replyIdentityService: ReplyIdentityService,
89:     private val expertMailPermissionGuard: ExpertMailPermissionGuard,
90:     // 03b (I-40/I-41/I-43): RAG 发送路径的协作件。可空默认 null 仅为让既有的直接
91:     // 构造单元测试（不触碰 RAG 分支）保持零改动；Spring 运行时按主构造器完整注入，
92:     // RAG 分支入口 requireNotNull 防御性校验。
93:     private val mailRecordRagFactRepository: MailRecordRagFactRepository? = null,
94:     private val ragKnowledgeBase: RagKnowledgeBase? = null,
95:     // 06 (I-1/I-2): 通用附件原件解析/元数据读取（04）。与上方两个 RAG 协作件同款：
96:     // Spring 运行时按主构造器完整注入，可空默认值只让既有的直接构造单元测试零改动；
97:     // 携带附件的入口在 claim 之前用 requireNotNull 防御性校验，绝不静默放行。
98:     private val outboundAttachmentService: OutboundAttachmentService? = null,
99:     private val mailReplyDraftService: MailReplyDraftService? = null
100: ) {
101:     companion object {
102:         private val log = LoggerFactory.getLogger(PendingMailOperationService::class.java)
103:         const val AI_REPLY_PREFLIGHT_SOURCE_CHANGED = "AI_REPLY_PREFLIGHT_SOURCE_CHANGED"
104:         const val AI_REPLY_PREFLIGHT_NO_EVIDENCE = "AI_REPLY_PREFLIGHT_NO_EVIDENCE"
105:         /**
106:          * 06 (I-2)：会话 requestId 已 SENT 且本次附件的有序语义（filename/type/size/hash）
107:          * 与原记录不同 —— 由 04 的 [OutboundAttachmentException] 固定 409，绝不谎报新附件
108:          * 已发送、绝不自动换 requestId 重投。
109:          */
110:         const val CONVERSATION_SENT_ATTACHMENTS_CHANGED =
111:             "该请求已发送，附件与原请求不同，请发起新回复"
112:         // 计划 04 (T2.7): 显式 QA 选择在发送路径被降级为可确认风险时的诊断码。
113:         const val QA_FACT_NOT_MATCHING_REQUEST = "QA_FACT_NOT_MATCHING_REQUEST"
114:         const val QA_FACT_UNAVAILABLE = "QA_FACT_UNAVAILABLE"
115:         const val QA_FACT_NO_EXTRACTABLE_REQUEST = "QA_FACT_NO_EXTRACTABLE_REQUEST"
116:         private val HREF_EXTRACTOR = Regex(
117:             """href\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>"']+))""",
118:             RegexOption.IGNORE_CASE
119:         )
120:     }
121: 
122:     @Transactional
123:     fun changeOperatorStatus(
124:         inboundProcessingId: Long,
125:         operatorStatus: String,
126:         operatorName: String?,
127:         note: String?
128:     ): ExpertContact {
129:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
130:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
131:         val contactId = record.expertContactId
132:             ?: error("Inbound mail not bound to a contact")
133:         return expertOperatorStatusService.changeStatus(
134:             contactId = contactId,
135:             targetStatus = operatorStatus,
136:             operatorName = operatorName,
137:             note = note,
138:             inboundProcessingId = inboundProcessingId
139:         )
140:     }
141: 
142:     @Transactional
143:     fun changeIndexLevel(
144:         inboundProcessingId: Long,
145:         targetLevel: String,
146:         operatorName: String?,
147:         note: String?
148:     ): ExpertContact {
149:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
150:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
151:         val contactId = record.expertContactId
152:             ?: error("Inbound mail not bound to a contact")
153:         return expertIndexLevelOperationService.changeLevel(
154:             contactId = contactId,
155:             targetLevel = targetLevel,
156:             operatorName = operatorName,
157:             note = note,
158:             inboundProcessingId = inboundProcessingId
159:         )
160:     }
161: 
162:     fun sendManualRichReply(
163:         inboundProcessingId: Long,
164:         senderAccountCode: String?,
165:         subject: String,
166:         htmlBody: String,
167:         textBody: String?,
168:         operatorName: String?,
169:         qaRuleIds: List<Long>? = null,
170:         suggestedRuleIds: List<Long>? = null,
171:         ackSnippetId: Long? = null,
172:         edited: Boolean? = null,
173:         freeTextPreview: String? = null,
174:         useVariants: Boolean = false,
175:         templateTextBody: String? = null,
176:         templateHtmlBody: String? = null,
177:         trustReplyAssembly: TrustReplyAssembleRequest? = null,
178:         safetyWarningConfirmed: Boolean = false,
179:         strongConfirmationText: String? = null,
180:         // 03b (I-39~I-43): RAG 证据 —— fact_code 字符串列表 + 生成草稿时下发的语料指纹。
181:         // 追加在参数末尾，既有调用点零改动；与 trustReplyAssembly 互斥（I-39）。
182:         ragFactCodes: List<String>? = null,
183:         ragCorpusFingerprint: String? = null,
184:         // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256。meeting 与
185:         // previewAttachmentSha256 必须同时出现/同时为空（否则 400）；非空时在最终变量
186:         // 渲染后用真实 processing/contact/account 走 01 validateAndBuild 重算核对。
187:         meeting: MeetingInput? = null,
188:         previewAttachmentSha256: String? = null,
189:         // 06 (T1/I-1): 通用附件 id（用户选择顺序）与本次请求的真实会话身份。默认空/空身份
190:         // 保持既有内部调用点零改动；非空 id 时身份必须真实（空身份在 claim 前 400），
191:         // 绝不回退请求体里的 operatorName。
192:         attachmentIds: List<String> = emptyList(),
193:         authenticatedUsername: String? = null,
194:         draftRef: MailReplyDraftRef? = null,
195:         reviewSendRef: MailReviewSendRef? = null
196:     ): PendingMailSendResult {
197:         if (draftRef != null && reviewSendRef != null)
198:             throw MailReviewDraftException(422, "REVIEW_SEND_REFERENCE_CONFLICT", "Conflicting draft references")
199:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
200:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
201:         val contactId = record.expertContactId
202:             ?: error("Inbound mail not bound to a contact")
203:         val contact = expertContactRepository.findById(contactId)
204:             .orElseThrow { error("Expert contact not found: $contactId") }
205:         expertMailPermissionGuard.requireFull(authenticatedUsername, contactId)
206:         if (draftRef != null) {
207:             val bound = requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
208:                 authenticatedUsername.orEmpty(),draftRef,contactId,inboundProcessingId,
209:                 senderAccountCode?.takeIf { it.isNotBlank() } ?: record.senderAccountCode)
210:             if (bound != null) return completedDraftResult(contactId,requireNotNull(manualReplySendAttemptService.findCompletedAttempt(bound)))
211:         }
212: 
213:         require(subject.isNotBlank()) { "Subject is required" }
214:         require(htmlBody.isNotBlank()) { "HTML body is required" }
215:         val trimmedSubject = subject.trim()
216:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
217: 
218:         // 03 (T1/I-1): meeting 与 previewAttachmentSha256 必须同时出现或同时为空。
219:         // 半配置请求直接 400，绝不静默降级为无附件发送。
220:         if (meeting == null && previewAttachmentSha256 != null ||
221:             meeting != null && previewAttachmentSha256 == null
222:         ) {
223:             throw ResponseStatusException(HttpStatus.BAD_REQUEST, "会议附件配置不完整，请重新预览")
224:         }
225: 
226:         val inboundText = inboundMessageBody(record)
227:         val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
228: 
229:         // 03b (I-39): 三条发送路径互斥 —— 旧工作台 assembly 与 RAG fact_code 证据同时出现
230:         // → 400 SEND_EVIDENCE_SOURCE_CONFLICT，绝不任选其一（否则 mail_record_qa_rule 与
231:         // mail_record_rag_fact 各写一份、互相矛盾）。判定先于下方 assembly 服务端重算。
232:         val ragMode = ragFactCodes != null
233:         if (trustReplyAssembly != null && ragMode) {
234:             throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SEND_EVIDENCE_SOURCE_CONFLICT")
235:         }
236:         // 03b (I-41/I-40): RAG 发送门禁 —— 指纹缺失 → 400 RAG_FINGERPRINT_REQUIRED；
237:         // 与当前语料指纹不符 → 409 RAG_CORPUS_STALE（不自动重新生成、不静默放行）；
238:         // 每个 fact_code 必须存在且 enabled → 否则 422 RAG_FACT_CODE_UNKNOWN。
239:         // 校验只对 RagKnowledgeBase 快照进行（I-40/G-4），绝不读 qa_rule / legacy_rule_id。
240:         val ragFingerprintAtSend: String? = if (ragMode) {
241:             val ragKb = requireNotNull(ragKnowledgeBase) {
242:                 "RagKnowledgeBase is not wired for RAG send"
243:             }
244:             val supplied = ragCorpusFingerprint?.takeIf { it.isNotBlank() }
245:                 ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "RAG_FINGERPRINT_REQUIRED")
246:             if (supplied != ragKb.fingerprint()) {
247:                 throw ResponseStatusException(HttpStatus.CONFLICT, "RAG_CORPUS_STALE")
248:             }
249:             val enabledFactCodes = ragKb.snapshot().facts
250:                 .asSequence()
251:                 .filter { it.enabled }
252:                 .map { it.factCode }
253:                 .toHashSet()
254:             for (factCode in ragFactCodes.orEmpty()) {
255:                 if (factCode !in enabledFactCodes) {
256:                     throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "RAG_FACT_CODE_UNKNOWN")
257:                 }
258:             }
259:             supplied
260:         } else {
261:             null
262:         }
263: 
264:         // 03 (I-1): 可信 workbench assembly 必须在任何发送副作用（suppression /
265:         // prepareAndClaim / SMTP / DB 成功落库）之前完成服务端重算验证；source 必须
266:         // 指向本次来信，验证失败在 claim 前稳定失败，且不烧掉 attempt（I-7）。
267:         val verifiedAssembly = trustReplyAssembly?.let { assembly ->
268:             if (assembly.source.sourceType != TrustReplySourceType.LIVE_INBOUND ||
269:                 assembly.source.sourceId != inboundProcessingId
270:             ) {
271:                 throw ResponseStatusException(
272:                     HttpStatus.UNPROCESSABLE_ENTITY,
273:                     "Trust reply assembly must target the current inbound"
274:                 )
275:             }
276:             try {
277:                 trustReplyWorkbenchService.verifyAssembly(assembly)
278:                     ?: error("Trust reply assembly verification returned no result")
279:             } catch (ex: TrustReplyWorkbenchException) {
280:                 throw ResponseStatusException(ex.status, ex.code)
281:             }
282:         }
283:         // 03 (I-2): 有 assembly 时 canonical facts 只来自服务端重算；客户端
284:         // qaRuleIds 必须与 verified canonical facts 逐元素相等（任一缺失/增加/乱序都
285:         // 在 claim 前失败），绝不静默采纳客户端 ids、不回退自动推荐、不部分删减。
286:         if (verifiedAssembly != null && qaRuleIds.orEmpty() != verifiedAssembly.response.canonicalFactIds) {
287:             throw ResponseStatusException(
288:                 HttpStatus.UNPROCESSABLE_ENTITY,
289:                 "qaRuleIds must equal the server-verified canonical fact ids"
290:             )
291:         }
292:         // 03 (阶段 2.3): carriesQa —— assembly 路径按 verified canonical 是否非空；
293:         // 无 assembly 的 legacy 路径保留「客户端提交过 qaRuleIds」的既有判据；
294:         // 03b (I-47 ①): RAG 路径按是否携带 fact_code 判定（审计据此区分动作类型），
295:         // 但 safety 调用点显式传 false —— RAG 不携带 QA 证据。
296:         val carriesQa = if (verifiedAssembly != null) {
297:             verifiedAssembly.response.canonicalFactIds.isNotEmpty()
298:         } else if (ragMode) {
299:             ragFactCodes.orEmpty().isNotEmpty()
300:         } else {
301:             !qaRuleIds.isNullOrEmpty()
302:         }
303:         val factResolution = if (verifiedAssembly != null) {
304:             // 03 (I-2/I-6): 无 degraded codes，canonical ids 原样进入 safety 与 SendPayload。
305:             CanonicalFactResolution(verifiedAssembly.response.canonicalFactIds, emptyList())
306:         } else if (ragMode) {
307:             // 03b (I-40): RAG 证据是 fact_code 字符串，不产生 Long ids；绝不调用
308:             // canonicalizeFactRuleIds / 读取 qa_rule（G-4）。
309:             CanonicalFactResolution(emptyList(), emptyList())
310:         } else if (carriesQa) {
311:             canonicalizeFactRuleIds(inboundText, qaRuleIds!!, researchProfileSufficient)
312:         } else {
313:             CanonicalFactResolution(emptyList(), emptyList())
314:         }
315:         val canonicalFactIds = factResolution.canonicalFactIds
316:         val serverSuggestedFactIds = if (ragMode) {
317:             // 03b (I-40): 不调用 qaFactSelectionService.select()。
318:             emptyList()
319:         } else if (carriesQa) {
320:             qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
321:         } else {
322:             emptyList()
323:         }
324:         // 03b (I-43): RAG 路径 matched_qa_rule_id 恒 null，绝不用 legacy_rule_id 兜底。
325:         val primaryRuleId = if (ragMode) null else canonicalFactIds.firstOrNull()
326: 
327:         val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)
328:         val validatedDraft = draftRef?.let {
329:             requireNotNull(mailReplyDraftService).validateInboundSend(authenticatedUsername.orEmpty(),it,
330:                 MailReplyDraftTarget(contactId,MailReplyDraftKind.INBOUND,inboundProcessingId,account.accountCode),
331:                 subject,htmlBody,textBody,ragFactCodes,ragCorpusFingerprint,edited,freeTextPreview,meeting,
332:                 previewAttachmentSha256,attachmentIds,
333:                 qaRuleIds != null || suggestedRuleIds != null || ackSnippetId != null || useVariants ||
334:                     trustReplyAssembly != null || templateTextBody?.let { raw -> raw != textBody } == true ||
335:                     templateHtmlBody?.let { raw -> raw != htmlBody } == true)
336:         }
337: 
338:         val manualSource = ManualRichSendSource(
339:             contact = contact,
340:             contactId = contactId,
341:             inboundProcessingId = inboundProcessingId,
342:             inboundRecord = record,
343:             account = account,
344:             accountCode = account.accountCode,
345:             persistInReplyTo = record.messageId,
346:             smtpInReplyTo = null,
347:             smtpReferences = null,
348:             anchorMailRecordId = null,
349:             requestId = null
350:         )
351:         return executeManualRichSend(
352:             source = manualSource,
353:             rawSubject = subject,
354:             htmlBody = htmlBody,
355:             textBody = textBody,
356:             operatorName = if (com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy.isReader(authenticatedUsername))
357:                 authenticatedUsername else operatorName,
358:             safetyWarningConfirmed = safetyWarningConfirmed,
359:             strongConfirmationText = strongConfirmationText,
360:             evidence = ManualReplyEvidenceContext(
361:                 verifiedAssembly = verifiedAssembly,
362:                 carriesQa = carriesQa,
363:                 canonicalFactIds = canonicalFactIds,
364:                 serverSuggestedFactIds = serverSuggestedFactIds,
365:                 degradedFactCodes = factResolution.degradedCodes,
366:                 ragMode = ragMode,
367:                 ragFactCodes = ragFactCodes,
368:                 ragFingerprintAtSend = ragFingerprintAtSend,
369:                 templateTextBody = templateTextBody,
370:                 templateHtmlBody = templateHtmlBody,
371:                 operatorAuthorizedActions = verifiedAssembly
372:                     ?.let { trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(it.response.itemVersions) }
373:                     .orEmpty(),
374:                 edited = edited,
375:                 inboundText = inboundText,
376:                 researchProfileSufficient = researchProfileSufficient
377:             ),
378:             meeting = meeting,
379:             previewAttachmentSha256 = previewAttachmentSha256,
380:             attachmentIds = attachmentIds,
381:             authenticatedUsername = authenticatedUsername,
382:             draftSendRef = validatedDraft,
383:             reviewSendRef = reviewSendRef
384:         )
385:     }
386: 
387:     /**
388:      * 会话级自由回信入口（T2）：联系人有真实 SENT 出站锚点才开放；锚点账号即发件账号。
389:      * 幂等（I-4）：先按 requestId 收敛已完成 attempt，再查锚点；无成功发件在 claim/SMTP
390:      * 前返回 422 CONVERSATION_SENT_ANCHOR_NOT_FOUND（I-1/I-10）。DTO 不携带
391:      * senderAccountCode/QA/RAG —— 本方法只接收可空 accountScope 约束锚点查询（I-6）。
392:      *
393:      * 跟进邮件（I-2/I-3）：`anchorMailRecordId` 非空时按 id 重读权威 `mail_record` 并逐条
394:      * 校验（同 contactId、OUTBOUND、SENT、非空非模拟器账号、accountScope 相等），任一不满足
395:      * 统一 422 且不 claim/不 SMTP；为空保持既有「最近成功发件」查询，不改变旧行为。
396:      */
397:     fun sendConversationManualRichReply(
398:         contactId: Long,
399:         requestId: String,
400:         accountScope: String?,
401:         anchorMailRecordId: Long? = null,
402:         subject: String,
403:         htmlBody: String,
404:         textBody: String?,
405:         operatorName: String?,
406:         safetyWarningConfirmed: Boolean = false,
407:         strongConfirmationText: String? = null,
408:         // 06 (T1/I-2)：通用附件 id 与真实会话身份。空/空身份保持既有调用点零改动。
409:         attachmentIds: List<String> = emptyList(),
410:         authenticatedUsername: String? = null,
411:         draftRef: MailReplyDraftRef? = null,
412:         reviewSendRef: MailReviewSendRef? = null
413:     ): PendingMailSendResult {
414:         if (draftRef != null && reviewSendRef != null)
415:             throw MailReviewDraftException(422, "REVIEW_SEND_REFERENCE_CONFLICT", "Conflicting draft references")
416:         val contact = expertContactRepository.findById(contactId)
417:             .orElseThrow { error("Expert contact not found: $contactId") }
418:         expertMailPermissionGuard.requireFull(authenticatedUsername, contactId)
419:         require(subject.isNotBlank()) { "Subject is required" }
420:         require(htmlBody.isNotBlank()) { "HTML body is required" }
421:         require(subject.trim().length <= 255) { "Subject exceeds 255 characters" }
422:         require(requestId.isNotBlank()) { "requestId is required" }
423:         val canonicalRequestId = try {
424:             manualReplySendAttemptService.canonicalConversationRequestId(requestId)
425:         } catch (ex: IllegalArgumentException) {
426:             throw IllegalArgumentException("requestId must be a valid UUID", ex)
427:         }
428: 
429:         // I-4：已完成 attempt 先收敛 —— SENT 时直接返回原结果，绝不重查锚点/再次投递
430:         // （刚发出的信已成为最新成功发件也不得再次 SMTP）。
431:         // 06 (I-2)：本次或原记录任何一侧带通用附件时，先用 04 批量元数据校验归属
432:         // （同专家 + 同上传者，不要求原件仍在线），再按 filename/type/size/hash 有序语义
433:         // 比较：相同返回原 SENT；不同固定 409，绝不谎报新附件已发送、也不换 requestId。
434:         val completed = manualReplySendAttemptService.findCompletedByRequestId(
435:             contact.orcidId, canonicalRequestId
436:         )
437:         val trustedDraftAttempt = draftRef?.let {
438:             requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
439:                 authenticatedUsername.orEmpty(),it,contactId,null,accountScope,completed?.attemptId)
440:         }
441:         if (reviewSendRef == null && completed != null && (draftRef == null || trustedDraftAttempt == completed.attemptId)) {
442:             val record = completed.mailRecord
443:             val originalSnapshots = OutboundAttachmentSnapshotCodec.parseOrThrow(
444:                 record.outboundAttachmentsJson
445:             )
446:             val requestedSnapshots = resolveRequestedAttachmentSnapshots(
447:                 contactId = contactId,
448:                 attachmentIds = attachmentIds,
449:                 authenticatedUsername = authenticatedUsername
450:             )
451:             if ((originalSnapshots != null || requestedSnapshots.isNotEmpty()) &&
452:                 !sameOutboundAttachmentSet(originalSnapshots.orEmpty(), requestedSnapshots)
453:             ) {
454:                 throw OutboundAttachmentException.conflict(CONVERSATION_SENT_ATTACHMENTS_CHANGED)
455:             }
456:             return PendingMailSendResult(
457:                 contactId = contactId,
458:                 senderAccountCode = record.senderAccountCode ?: completed.attemptAccountCode,
459:                 mailType = "MANUAL_RICH_REPLY",
460:                 subject = record.subject ?: subject,
461:                 sendStatus = "SENT",
462:                 messageId = record.messageId ?: completed.attemptMessageId
463:             )
464:         }
465:         val validatedDraft = draftRef?.let {
466:             requireNotNull(mailReplyDraftService).validateConversationSend(authenticatedUsername.orEmpty(),it,
467:                 contactId,accountScope,canonicalRequestId,anchorMailRecordId,subject,htmlBody,textBody,attachmentIds)
468:         }
469: 
470:         // I-1：真实 SENT 出站锚点（排除空账号/模拟器）；accountScope 非空时只在该账号内找，
471:         // scope 下无成功发件不回退其他账号（I-6）。锚点查询在任何 claim/SMTP 之前。
472:         // 跟进（I-2）：显式 id 必须重读同一行并全校验，前端只作提示、绝不作为权限边界。
473:         val anchor = resolveIdentityConversationAnchor(contactId, accountScope, anchorMailRecordId)
474:         val accountCode = requireNotNull(anchor.senderAccountCode) {
475:             "Conversation anchor mail record has no sender account"
476:         }
477:         val account = mailSenderAccountService.getManualSendAccount(accountCode)
478: 
479:         // I-7：Message-ID 仅在 trim 后非空且 <=255 时进入落库 inReplyTo 与 SMTP 线程头；
480:         // 缺失/超长不取消资格 —— 线程头为空仍发送（I-3：引用链只来自真实锚点）。
481:         val anchorMessageId = anchor.messageId
482:             ?.trim()
483:             ?.takeIf { it.isNotBlank() && it.length <= 255 }
484:         val smtpReferences = if (anchorMessageId != null) {
485:             listOfNotNull(anchor.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
486:                 .joinToString(" ")
487:         } else null
488: 
489:         val source = ManualRichSendSource(
490:             contact = contact,
491:             contactId = contactId,
492:             inboundProcessingId = null,
493:             inboundRecord = null,
494:             account = account,
495:             accountCode = accountCode,
496:             persistInReplyTo = anchorMessageId,
497:             smtpInReplyTo = anchorMessageId,
498:             smtpReferences = smtpReferences,
499:             anchorMailRecordId = requireNotNull(anchor.id) { "Conversation anchor mail record has no id" },
500:             requestId = canonicalRequestId,
501:             identityAnchor = anchor
502:         )
503:         return executeManualRichSend(
504:             source = source,
505:             rawSubject = subject,
506:             htmlBody = htmlBody,
507:             textBody = textBody,
508:             operatorName = if (com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy.isReader(authenticatedUsername))
509:                 authenticatedUsername else operatorName,
510:             safetyWarningConfirmed = safetyWarningConfirmed,
511:             strongConfirmationText = strongConfirmationText,
512:             evidence = ManualReplyEvidenceContext(runInboundSemanticChecks = false),
513:             attachmentIds = attachmentIds,
514:             authenticatedUsername = authenticatedUsername,
515:             draftSendRef = validatedDraft,
516:             reviewSendRef = reviewSendRef
517:         )
518:     }
519: 
520:     fun inspectReplyIdentity(request: ReplyIdentityInspectRequest): ReplyIdentityResult =
521:         replyIdentityService.inspect(resolveIdentitySource(request.contextRequest()), request.textBody, request.htmlBody)
522: 
523:     fun previewReplyIdentityDefaults(request: ReplyIdentityDefaultsPreviewRequest): ReplyIdentityDefaultsPreview =
524:         replyIdentityService.previewDefaults(resolveIdentitySource(request.contextRequest()), request.salutation, request.signature)
525: 
526:     private fun resolveIdentityConversationAnchor(contactId: Long, accountScope: String?, anchorMailRecordId: Long?): MailRecord {
527:         return (if (anchorMailRecordId != null) {
528:             mailRecordRepository.findById(anchorMailRecordId).orElse(null)
529:                 ?.takeIf { isConversationSentAnchor(it, contactId, accountScope) }
530:         } else {
531:             mailRecordRepository.findLatestSentOutboundAnchor(contactId,
532:                 accountScope?.takeIf { it.isNotBlank() }, MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
533:         }) ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CONVERSATION_SENT_ANCHOR_NOT_FOUND")
534:     }
535: 
536:     private fun resolveIdentitySource(request: ReplyIdentityContextRequest): ReplyIdentitySource {
537:         request.validate()
538:         val contact = expertContactRepository.findById(request.contactId)
539:             .orElseThrow { error("Expert contact not found: ${request.contactId}") }
540:         if (request.sourceKind == ReplyIdentitySourceKind.INBOUND) {
541:             val processing = inboundMailProcessingRepository.findById(requireNotNull(request.inboundProcessingId))
542:                 .orElseThrow { error("Inbound mail processing not found") }
543:             require(processing.expertContactId == request.contactId) { "Inbound mail contact mismatch" }
544:             val account = resolvePendingReplyAccount(request.senderAccountCode, processing.senderAccountCode)
545:             return ReplyIdentitySource(contact, account,
546:                 ReplyIdentityContext(request.contactId, processing.id, null, account.accountCode, contact.expertEmail, account.senderName),
547:                 processing.cleanedBody?.takeIf { it.isNotBlank() } ?: processing.body, null)
548:         }
549:         val anchor = resolveIdentityConversationAnchor(request.contactId, request.accountScope, request.anchorMailRecordId)
550:         val account = mailSenderAccountService.getManualSendAccount(requireNotNull(anchor.senderAccountCode))
551:         return identitySource(contact, account, null, anchor)
552:     }
553: 
554:     private fun identitySource(source: ManualRichSendSource): ReplyIdentitySource {
555:         if (source.inboundRecord != null) {
556:             val record = source.inboundRecord
557:             return ReplyIdentitySource(source.contact, source.account,
558:                 ReplyIdentityContext(source.contactId,source.inboundProcessingId,null,source.accountCode,
559:                     source.contact.expertEmail,source.account.senderName),
560:                 record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body,null)
561:         }
562:         return identitySource(source.contact, source.account, null, source.identityAnchor)
563:     }
564: 
565:     private fun identitySource(contact: ExpertContact, account: MailSenderAccount,
566:                                inboundId: Long?, anchor: MailRecord?): ReplyIdentitySource {
567:         val date = (anchor?.sentAt ?: anchor?.createdAt)?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
568:         return ReplyIdentitySource(contact,account,
569:             ReplyIdentityContext(requireNotNull(contact.id),inboundId,anchor?.id,account.accountCode,contact.expertEmail,account.senderName),
570:             anchor?.cleanedBody?.takeIf { it.isNotBlank() } ?: anchor?.body,
571:             date?.let { "On $it, ${account.accountCode} wrote:" })
572:     }
573: 
574:     private fun completedDraftResult(contactId: Long, completed: ManualReplySendAttemptService.CompletedOutboundReply) =
575:         PendingMailSendResult(contactId,completed.mailRecord.senderAccountCode ?: completed.attemptAccountCode,
576:             "MANUAL_RICH_REPLY",completed.mailRecord.subject.orEmpty(),"SENT",
577:             completed.mailRecord.messageId ?: completed.attemptMessageId)
578: 
579:     /**
580:      * I-2：显式锚点资格判定 —— 与 `findLatestSentOutboundAnchor` 的 SQL 谓词同口径
581:      * （同联系人 / OUTBOUND / SENT / 非空非模拟器账号 / accountScope 相等）。前端只传 id，
582:      * 这里重读的行是唯一权威。
583:      */
584:     private fun isConversationSentAnchor(
585:         record: MailRecord,
586:         contactId: Long,
587:         accountScope: String?
588:     ): Boolean {
589:         val accountCode = record.senderAccountCode
590:         return record.expertContactId == contactId &&
591:             record.direction == "OUTBOUND" &&
592:             record.sendStatus == "SENT" &&
593:             !accountCode.isNullOrBlank() &&
594:             accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE &&
595:             (accountScope.isNullOrBlank() || accountCode == accountScope)
596:     }
597: 
598:     /**
599:      * 人工富文本发送共同实现（T2.4）：两条路径共享「占位符校验 → 最终渲染 → 最终文本
600:      * 安全检查 → suppression → claim → SMTP → 分类 → 持久化 → after-commit audit」
601:      * 生命周期（I-9）。来信路径（source.inboundProcessingId != null）保留 QA/RAG/
602:      * assembly/archive/旧审计语义（I-8）；会话回信路径（null）只跑通用纯文本风险检查，
603:      * 不运行依赖来信语义的 QA/意图检查，审计 target 为联系人（I-11）。
604:      */
605:     private fun executeManualRichSend(
606:         source: ManualRichSendSource,
607:         rawSubject: String,
608:         htmlBody: String,
609:         textBody: String?,
610:         operatorName: String?,
611:         safetyWarningConfirmed: Boolean,
612:         strongConfirmationText: String?,
613:         evidence: ManualReplyEvidenceContext,
614:         // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256（仅来信路径携带；会话回信
615:         // 路径不传，保持默认 null）。非空时在最终变量渲染后用真实 processing/contact/
616:         // account 走 01 validateAndBuild 重算核对，两者必须同时出现/同时为空。
617:         meeting: MeetingInput? = null,
618:         previewAttachmentSha256: String? = null,
619:         // 06 (I-1)：通用附件 id（选择顺序）与真实会话身份；两条入口都传到这里，附件在
620:         // claim 之前解析（同一文件集合同时喂 SendPayload 快照与 ComposedMail 载荷）。
621:         attachmentIds: List<String> = emptyList(),
622:         authenticatedUsername: String? = null,
623:         draftSendRef: ValidatedDraftSendRef? = null,
624:         reviewSendRef: MailReviewSendRef? = null
625:     ): PendingMailSendResult {
626:         val contact = source.contact
627:         require(rawSubject.isNotBlank()) { "Subject is required" }
628:         require(htmlBody.isNotBlank()) { "HTML body is required" }
629:         val trimmedSubject = rawSubject.trim()
630:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
631: 
632:         mailVariableService.requireValidPlaceholders(trimmedSubject)
633:         val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
634:         require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
635:         require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }
636: 
637:         val rawText = evidence.templateTextBody?.takeIf { it.isNotBlank() }
638:             ?: textBody?.takeIf { it.isNotBlank() }
639:             ?: mailBodyCleaner.clean(htmlBody)
640:         val rawHtmlFromTemplate = evidence.templateHtmlBody?.takeIf { it.isNotBlank() }
641:         mailVariableService.requireValidPlaceholders(rawText)
642:         if (rawHtmlFromTemplate != null) {
643:             mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
644:         } else if (evidence.templateTextBody.isNullOrBlank()) {
645:             mailVariableService.requireValidPlaceholders(htmlBody)
646:         }
647: 
648:         val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
649:         // I-1/I-2/I-3：人工富文本最终正文必须在变量渲染之后、最终校验/安全确认/幂等指纹/
650:         // SMTP/落库/审计之前规范化一次；本方法后续所有读路径（finalValidationText、会议正文
651:         // 核对、SendPayload 指纹、ComposedMail 两个 MIME alternative、预览与归档）全部引用这
652:         // 一份 canonical final body，禁止检查原文却发送另一份正文（I-4）。
653:         val finalTextBody = mailContentService.normalizeManualTextLineBreaks(renderedText)
654:         val finalHtmlBody = when {
655:             rawHtmlFromTemplate != null ->
656:                 mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
657:             !evidence.templateTextBody.isNullOrBlank() ->
658:                 mailContentService.plainTextToHtml(renderedText)
659:             else ->
660:                 mailVariableService.renderHtmlForContact(htmlBody, source.account, contact)
661:         }.let { mailContentService.stripOpenTrackingImages(it) }
662:             .let { mailContentService.normalizeManualRichHtmlLineBreaks(it) }
663:         reviewSendRef?.let { ref ->
664:             val frozen = ref.draft
665:             if (renderedSubject != frozen.subject || finalHtmlBody != frozen.html || finalTextBody != frozen.text)
666:                 throw MailReviewDraftException(409, "REVIEW_CONTENT_CHANGED", "Final content differs from the approved snapshot")
667:             if (source.contactId != frozen.contactId || source.accountCode != frozen.accountCode ||
668:                 (if (source.inboundProcessingId != null) "INBOUND" else "OUTBOUND") != frozen.targetKind ||
669:                 (source.inboundProcessingId ?: source.anchorMailRecordId) != frozen.targetId ||
670:                 source.requestId != (if (frozen.targetKind == "OUTBOUND") frozen.sendRequestId else null) ||
671:                 authenticatedUsername != ref.actor || draftSendRef != null || attachmentIds.isNotEmpty() ||
672:                 meeting != null || evidence.canonicalFactIds.isNotEmpty() || evidence.ragMode || evidence.verifiedAssembly != null)
673:                 throw MailReviewDraftException(422, "REVIEW_SEND_REFERENCE_CONFLICT", "Review target or payload mismatch")
674:         }
675: 
676:         val finalValidationText = buildFinalValidationText(renderedSubject, finalTextBody, finalHtmlBody)
677:         require(finalValidationText.isNotBlank()) { "Final validation text is empty after rendering" }
678:         require(finalValidationText.length <= 20000) {
679:             "Final validation text exceeds 20,000 characters (${finalValidationText.length})"
680:         }
681:         mailVariableService.requireValidPlaceholders(finalTextBody)
682:         mailVariableService.requireValidPlaceholders(finalHtmlBody)
683: 
684:         // 03 (T1/I-1/I-2): 会议附件重建与正文核对 —— 位于最终变量渲染之后、Safety/claim/
685:         // SMTP 之前。会议仅由来信路径产生（meeting 来自 unmatched-inbound 01 预览，
686:         // 会话回信路径恒不携带）；用服务端解析后的真实 account/recipient/contact 与
687:         // processing 调用 01 validateAndBuild：digest 不一致 400（配置已变化）；模板禁用
688:         // 沿 01 拒绝；01 生成的完整会议正文必须连续存在于最终 text 与
689:         // htmlToPlainText(finalHtml) 的规范化文本（不是只检索日期/链接关键词），否则 400。
690:         // 最终正文仍取人工编辑器。
691:         val rebuiltMeeting: MeetingPreviewResponse? = if (meeting != null) {
692:             require(source.inboundProcessingId != null) {
693:                 "Meeting attachment requires an inbound processing context"
694:             }
695:             val rebuilt = try {
696:                 meetingConfirmationService.validateAndBuild(
697:                     processingId = source.inboundProcessingId,
698:                     contact = source.contact,
699:                     account = source.account,
700:                     input = meeting
701:                 )
702:             } catch (ex: IllegalArgumentException) {
703:                 throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message ?: "会议配置无效")
704:             }
705:             if (rebuilt.attachment.sha256 != previewAttachmentSha256) {
706:                 throw ResponseStatusException(HttpStatus.BAD_REQUEST, "会议配置已变化，请重新预览")
707:             }
708:             val expected = normalizeBodyText(rebuilt.textBody)
709:             val finalTextNormalized = normalizeBodyText(finalTextBody)
710:             val finalHtmlPlainNormalized = normalizeBodyText(mailContentService.htmlToPlainText(finalHtmlBody))
711:             if (!finalTextNormalized.contains(expected) || !finalHtmlPlainNormalized.contains(expected)) {
712:                 throw ResponseStatusException(
713:                     HttpStatus.BAD_REQUEST,
714:                     "会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件"
715:                 )
716:             }
717:             rebuilt
718:         } else {
719:             null
720:         }
721:         // 01 返回的同一份附件数据只构造一次快照实例，SendPayload 与 ComposedMail 共用。
722:         val calendarSnapshot: CalendarAttachmentSnapshot? = rebuiltMeeting?.let { rebuilt ->
723:             CalendarAttachmentSnapshot(
724:                 schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
725:                 filename = rebuilt.attachment.filename,
726:                 contentType = rebuilt.attachment.contentType,
727:                 icsText = rebuilt.attachment.icsText,
728:                 sha256 = rebuilt.attachment.sha256,
729:                 semanticSha256 = rebuilt.attachment.semanticSha256
730:             )
731:         }
732:         // fast-p 02 (I-1)：结构化排期输入同样来自这一次 validateAndBuild —— 只取已校验的
733:         // startUtc/endUtc/zoomUrl，绝不重新生成 ICS、不重新取当前时间、不接受浏览器排期
734:         // 对象；与 calendarSnapshot 同有同无（均仅在有 meeting 时为非 null）。
735:         val meetingCalendarInput: MeetingCalendarInput? = rebuiltMeeting?.let { rebuilt ->
736:             MeetingCalendarInput(
737:                 startUtc = Instant.parse(rebuilt.startUtc),
738:                 endUtc = Instant.parse(rebuilt.endUtc),
739:                 meetingLink = rebuilt.meeting.zoomUrl
740:             )
741:         }
742:         val identityFindings = try {
743:             replyIdentityService.analyzeFinalBody(identitySource(source), finalTextBody, finalHtmlBody)
744:         } catch (_: Exception) {
745:             listOf(SafetyFinding("REPLY_IDENTITY_UNCERTAIN",SafetySeverity.NORMAL,"身份格式暂无法核对；可检查正文后确认发送"))
746:         }
747:         val originalFindings = collectSafetyFindings(
748:             verificationText = finalValidationText,
749:             // 03b (I-47 ①): RAG 路径 carriesQa 显式传 false（RAG 不携带 QA 证据）。
750:             carriesQa = if (evidence.ragMode) false else evidence.carriesQa,
751:             canonicalFactIds = evidence.canonicalFactIds,
752:             contact = contact,
753:             inboundText = evidence.inboundText,
754:             researchProfileSufficient = evidence.researchProfileSufficient,
755:             operatorAuthorizedActions = evidence.operatorAuthorizedActions,
756:             degradedFactCodes = evidence.degradedFactCodes,
757:             // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection，禁止再次语义重筛。
758:             verifiedSelection = evidence.verifiedAssembly?.selection,
759:             // 03b (I-47): RAG 发送整段绕开 QA selection/trust-gap/intent 链。
760:             ragSend = evidence.ragMode,
761:             // T2.7: 会话回信路径在通用纯文本检查后返回，不跑来信语义检查。
762:             runInboundSemanticChecks = evidence.runInboundSemanticChecks
763:         )
764:         val findings = identityFindings + originalFindings
765:         val requiresStrong = findings.any { it.severity == SafetySeverity.STRONG }
766:         if (originalFindings.isNotEmpty() && !safetyWarningConfirmed) {
767:             throw ManualSendSafetyBlockedException(findings)
768:         }
769:         if (requiresStrong && strongConfirmationText?.trim() != "确认发送") {
770:             throw ManualSendSafetyBlockedException(findings)
771:         }
772: 
773:         // 06 (I-1)：通用附件在幂等 claim 之前解析 —— 04 按 (专家 + 上传者) 读元数据与原件、
774:         // 校验容量/尺寸/hash，失败以 400/404/409/413 直接抛出（位于下方投递 try 块之外，
775:         // 绝不烧掉 attempt，也绝不被归成 SMTP UNKNOWN）。安全确认重提会重新解析同一批 id，
776:         // 同一原件 → 同一快照 → 同一发送身份指纹。
777:         val attachmentFileSet = resolveOutboundAttachmentFileSet(
778:             contactId = source.contactId,
779:             attachmentIds = attachmentIds,
780:             authenticatedUsername = authenticatedUsername
781:         )
782: 
783:         val payload = ManualReplySendAttemptService.SendPayload(
784:             orcidId = contact.orcidId,
785:             contactId = source.contactId,
786:             inboundProcessingId = source.inboundProcessingId,
787:             accountCode = source.accountCode,
788:             normalizedRecipient = contact.expertEmail.lowercase().trim(),
789:             subject = renderedSubject,
790:             finalText = finalTextBody,
791:             finalHtml = finalHtmlBody,
792:             inReplyTo = source.persistInReplyTo,
793:             canonicalQaRuleIds = evidence.canonicalFactIds,
794:             primaryRuleId = if (evidence.ragMode) null else evidence.canonicalFactIds.firstOrNull(),
795:             // I-3：会话回信锚点只以真实 mail_record.id 表达，绝不伪造 inbound id。
796:             sourceAnchor = if (source.inboundProcessingId == null) {
797:                 requireNotNull(source.anchorMailRecordId) {
798:                     "Conversation rich reply requires a real anchor mail record"
799:                 }.let { "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$it" }
800:             } else null,
801:             idempotencyRequestId = source.requestId,
802:             // 03 (I-3): 与 ComposedMail 共用同一 01 快照实例，绝不独立生成第二份。
803:             calendarAttachment = calendarSnapshot,
804:             // fast-p 02 (I-1)：同一次重算产出的结构化排期输入；无会议恒 null。
805:             meetingEvent = meetingCalendarInput,
806:             // 06 (I-1)：与 ComposedMail.outboundAttachments 同一文件集合导出的有序快照；
807:             // 05 的 finalize 四分支据此写 mail_record.outbound_attachments_json。
808:             outboundAttachments = attachmentFileSet.snapshots
809:         ).also { it.draftSendRef = draftSendRef; it.reviewSendRef = reviewSendRef }
810: 
811:         val persistInReplyTo = source.persistInReplyTo
812:         if (persistInReplyTo != null && persistInReplyTo.length > 255) {
813:             throw ResponseStatusException(
814:                 HttpStatus.UNPROCESSABLE_ENTITY,
815:                 "inReplyTo exceeds 255 characters"
816:             )
817:         }
818: 
819:         // I-5: 幂等占位（prepareAndClaim）之前必须判抑制，禁止把发送尝试烧成 DELIVERY_UNKNOWN。
820:         if (emailSuppressionService.isSuppressed(contact.expertEmail)) {
821:             throw ResponseStatusException(
822:                 HttpStatus.BAD_REQUEST,
823:                 "收件人已退订，禁止外发：${contact.expertEmail}"
824:             )
825:         }
826: 
827:         // Preserve original risk/attachment/suppression priority. Only an exact completed
828:         // non-draft inbound payload may bypass a newly introduced identity reminder.
829:         if (draftSendRef == null && (source.inboundProcessingId != null || reviewSendRef != null)) {
830:             manualReplySendAttemptService.findCompletedByPayload(payload)?.let {
831:                 return completedDraftResult(source.contactId, it)
832:             }
833:         }
834:         if (identityFindings.isNotEmpty() && !safetyWarningConfirmed) {
835:             throw ManualSendSafetyBlockedException(findings)
836:         }
837: 
838:         val claim = manualReplySendAttemptService.prepareAndClaim(payload)
839: 
840:         return when (claim.result) {
841:             ManualReplySendAttemptService.ClaimResult.CLAIMED,
842:             ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED -> {
843:                 val mail = ComposedMail(
844:                     to = contact.expertEmail,
845:                     subject = renderedSubject,
846:                     body = finalHtmlBody,
847:                     html = true,
848:                     text = finalTextBody,
849:                     messageId = claim.messageId,
850:                     // 03 (I-2/I-3): 带会议日历的新分支用真实来信 processing.messageId 作
851:                     // SMTP 线程头（inReplyTo/references 同一来源）；会议只由来信路径产生，
852:                     // 无会议时回落该路径原形态（来信 SMTP 头恒 null；会话回信 = 锚点头），
853:                     // 不因本合并顺带改变旧邮件线程形态。
854:                     inReplyTo = if (calendarSnapshot != null) source.inboundRecord?.messageId
855:                         else source.smtpInReplyTo,
856:                     references = if (calendarSnapshot != null) source.inboundRecord?.messageId
857:                         else source.smtpReferences,
858:                     calendarAttachment = calendarSnapshot,
859:                     // 06 (I-1)：同一文件集合的原件（已核尺寸/hash），SMTP 在 ICS 之后按
860:                     // 选择顺序以 multipart/mixed 携带。
861:                     outboundAttachments = attachmentFileSet.files,
862:                     isReply = true
863:                 )
864:                 val bodyPreviewText = (finalTextBody.ifBlank { mailBodyCleaner.clean(finalHtmlBody) }
865:                     .takeIf { it.isNotBlank() } ?: mailBodyCleaner.clean(finalHtmlBody)).take(500)
866: 
867:                 try {
868:                     val delivered = mailDeliveryService.send(source.account, mail)
869:                     val classification = classifyDelivery(delivered)
870: 
871:                     if (classification.isSent) {
872:                         val mailRecordId = try {
873:                             val id = manualReplySendAttemptService.finalizeSuccess(
874:                                 payload = payload,
875:                                 attemptId = claim.attemptId,
876:                                 messageId = claim.messageId
877:                             )
878:                             if (source.inboundProcessingId != null) {
879:                                 manualReplySendAttemptService.recordSendAudit(
880:                                     inboundProcessingId = source.inboundProcessingId,
881:                                     contactId = source.contactId,
882:                                     mailRecordId = id,
883:                                     canonicalFactIds = evidence.canonicalFactIds,
884:                                     carriesQa = evidence.carriesQa,
885:                                     delivered = delivered,
886:                                     sendSubject = renderedSubject,
887:                                     bodyPreviewText = bodyPreviewText,
888:                                     operatorName = operatorName,
889:                                     inboundRecord = requireNotNull(source.inboundRecord) {
890:                                         "Inbound rich reply audit requires the inbound processing record"
891:                                     },
892:                                     serverSuggestedFactIds = evidence.serverSuggestedFactIds,
893:                                     edited = evidence.edited,
894:                                     // 04 (I-1/I-7): 仅在该次发送存在 verified assembly 时附加诊断。
895:                                     trustReplyDiagnostics = evidence.verifiedAssembly?.response?.diagnostics,
896:                                     note = auditNote(inboundProcessingId = source.inboundProcessingId, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
897:                                 )
898:                             } else {
899:                                 manualReplySendAttemptService.recordConversationSendAudit(
900:                                     contactId = source.contactId,
901:                                     anchorMailRecordId = requireNotNull(source.anchorMailRecordId) {
902:                                         "Conversation rich reply audit requires the anchor mail record"
903:                                     },
904:                                     mailRecordId = id,
905:                                     delivered = delivered,
906:                                     sendSubject = renderedSubject,
907:                                     bodyPreviewText = bodyPreviewText,
908:                                     operatorName = operatorName,
909:                                     note = auditNote(inboundProcessingId = null, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
910:                                 )
911:                             }
912:                             // 03b (I-42): 发送成功后按请求中 ragFactCodes 的原始顺序写入
913:                             // mail_record_rag_fact 存证；RAG 路径 canonicalFactIds 恒为空，
914:                             // 绝不写 mail_record_qa_rule。
915:                             if (evidence.ragFactCodes != null) {
916:                                 val ragEvidenceRepo = requireNotNull(mailRecordRagFactRepository) {
917:                                     "MailRecordRagFactRepository is not wired for RAG send"
918:                                 }
919:                                 val fingerprintAtSend = requireNotNull(evidence.ragFingerprintAtSend) {
920:                                     "RAG fingerprint must be present after gate validation"
921:                                 }
922:                                 ragEvidenceRepo.saveAll(
923:                                     evidence.ragFactCodes.mapIndexed { ordinal, factCode ->
924:                                         MailRecordRagFact(
925:                                             mailRecordId = id,
926:                                             factCode = factCode,
927:                                             ordinal = ordinal,
928:                                             corpusFingerprint = fingerprintAtSend
929:                                         )
930:                                     }
931:                                 )
932:                             }
933:                             id
934:                         } catch (finalizeEx: Exception) {
935:                             log.warn("finalizeSuccess failed for attempt {}: {}", claim.attemptId, finalizeEx.message)
936:                             try {
937:                                 manualReplySendAttemptService.finalizeFailure(
938:                                     payload = payload,
939:                                     attemptId = claim.attemptId,
940:                                     messageId = claim.messageId,
941:                                     resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
942:                                     errorSummary = "finalize_failure:${finalizeEx.message?.take(400).orEmpty()}"
943:                                 )
944:                             } catch (finalizeFailEx: Exception) {
945:                                 log.error("finalizeFailure to UNKNOWN also failed for attempt {}: {}",
946:                                     claim.attemptId, finalizeFailEx.message)
947:                             }
948:                             throw ResponseStatusException(
949:                                 HttpStatus.CONFLICT,
950:                                 "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
951:                             )
952:                         }
953:                         // 归档只属来信语义链（verifiedAssembly 非空才可能产生样本）；会话回信
954:                         // 路径 verifiedAssembly 恒 null，直接返回默认 NOT_APPLICABLE（I-8/I-11）。
955:                         val archive = if (source.inboundProcessingId != null) {
956:                             archiveLiveUnsupportedAnswers(
957:                                 inboundProcessingId = source.inboundProcessingId,
958:                                 templateTextBody = evidence.templateTextBody,
959:                                 finalTextBody = finalTextBody,
960:                                 operatorName = operatorName,
961:                                 outboundMailRecordId = mailRecordId,
962:                                 verifiedAssembly = evidence.verifiedAssembly
963:                             )
964:                         } else {
965:                             UnsupportedAnswerIndexArchiveResult()
966:                         }
967:                         PendingMailSendResult(
968:                             contactId = source.contactId,
969:                             senderAccountCode = source.accountCode,
970:                             mailType = "MANUAL_RICH_REPLY",
971:                             subject = renderedSubject,
972:                             sendStatus = "SENT",
973:                             messageId = claim.messageId,
974:                             unsupportedAnswerArchiveStatus = archive.status,
975:                             unsupportedAnswerArchivedCount = archive.archivedCount,
976:                             unsupportedAnswerArchiveFailedCount = archive.failedCount
977:                         )
978:                     } else {
979:                         manualReplySendAttemptService.finalizeFailure(
980:                             payload = payload,
981:                             attemptId = claim.attemptId,
982:                             messageId = claim.messageId,
983:                             resultStatus = classification.attemptStatus,
984:                             errorSummary = classification.errorSummary
985:                         )
986:                         if (classification.isUnknown) {
987:                             throw ResponseStatusException(
988:                                 HttpStatus.CONFLICT,
989:                                 "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
990:                             )
991:                         }
992:                         if (classification.isSafeRetry) {
993:                             throw ResponseStatusException(
994:                                 HttpStatus.SERVICE_UNAVAILABLE,
995:                                 "发送暂时失败，可安全重试"
996:                             )
997:                         }
998:                         throw ResponseStatusException(
999:                             HttpStatus.UNPROCESSABLE_ENTITY,
1000:                             "发送失败，请修改内容后重试"
1001:                         )
1002:                     }
1003:                 } catch (deliveryEx: Exception) {
1004:                     when (deliveryEx) {
1005:                         is ResponseStatusException -> throw deliveryEx
1006:                         else -> {
1007:                             log.warn("Unchecked delivery error for attempt {}: {}", claim.attemptId, deliveryEx.message)
1008:                             try {
1009:                                 manualReplySendAttemptService.finalizeFailure(
1010:                                     payload = payload,
1011:                                     attemptId = claim.attemptId,
1012:                                     messageId = claim.messageId,
1013:                                     resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
1014:                                     errorSummary = "delivery_error:${deliveryEx.message?.take(400).orEmpty()}"
1015:                                 )
1016:                             } catch (finalizeEx: Exception) {
1017:                                 log.error("finalizeFailure to UNKNOWN failed for attempt {}: {}",
1018:                                     claim.attemptId, finalizeEx.message)
1019:                             }
1020:                             throw ResponseStatusException(
1021:                                 HttpStatus.CONFLICT,
1022:                                 "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
1023:                             )
1024:                         }
1025:                     }
1026:                 }
1027:             }
1028: 
1029:             ManualReplySendAttemptService.ClaimResult.DEDUP_SENT -> {
1030:                 val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
1031:                 val archive = if (source.inboundProcessingId != null) {
1032:                     archiveLiveUnsupportedAnswers(
1033:                         inboundProcessingId = source.inboundProcessingId,
1034:                         templateTextBody = evidence.templateTextBody,
1035:                         finalTextBody = finalTextBody,
1036:                         operatorName = operatorName,
1037:                         outboundMailRecordId = existingRecord?.id,
1038:                         verifiedAssembly = evidence.verifiedAssembly
1039:                     )
1040:                 } else {
1041:                     UnsupportedAnswerIndexArchiveResult()
1042:                 }
1043:                 PendingMailSendResult(
1044:                     contactId = source.contactId,
1045:                     senderAccountCode = payload.accountCode,
1046:                     mailType = "MANUAL_RICH_REPLY",
1047:                     subject = renderedSubject,
1048:                     sendStatus = "SENT",
1049:                     messageId = existingRecord?.messageId ?: claim.messageId,
1050:                     unsupportedAnswerArchiveStatus = archive.status,
1051:                     unsupportedAnswerArchivedCount = archive.archivedCount,
1052:                     unsupportedAnswerArchiveFailedCount = archive.failedCount
1053:                 )
1054:             }
1055: 
1056:             ManualReplySendAttemptService.ClaimResult.IN_PROGRESS ->
1057:                 throw ResponseStatusException(
1058:                     HttpStatus.CONFLICT,
1059:                     "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
1060:                 )
1061: 
1062:             ManualReplySendAttemptService.ClaimResult.UNKNOWN ->
1063:                 throw ResponseStatusException(
1064:                     HttpStatus.CONFLICT,
1065:                     "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
1066:                 )
1067: 
1068:             ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED ->
1069:                 throw ResponseStatusException(
1070:                     HttpStatus.UNPROCESSABLE_ENTITY,
1071:                     "该内容已发送失败，请修改内容后重试"
1072:                 )
1073:         }
1074:     }
1075: 
1076:     /**
1077:      * 06 (I-1)：04 通用附件原件解析 —— 元数据/原件读取与归属（同专家 + 同上传者）、容量校验
1078:      * 全部发生在调用方（最终发送门）的幂等 claim 之前。空 id 列表是「无通用附件」的既有
1079:      * 形态，不触碰 04 服务、不要求会话身份。[authenticatedUsername] 为 null 时传空身份，
1080:      * 由 04 的 normalizeOperator 固定 400「上传身份缺失」——绝不用请求体 operatorName 兜底。
1081:      */
1082:     private fun resolveOutboundAttachmentFileSet(
1083:         contactId: Long,
1084:         attachmentIds: List<String>,
1085:         authenticatedUsername: String?
1086:     ): OutboundAttachmentFileSet {
1087:         if (attachmentIds.isEmpty()) {
1088:             return OutboundAttachmentFileSet(emptyList(), emptyList())
1089:         }
1090:         val service = requireNotNull(outboundAttachmentService) {
1091:             "OutboundAttachmentService is not wired for attachment send"
1092:         }
1093:         return service.resolveForSend(contactId, attachmentIds, authenticatedUsername.orEmpty())
1094:     }
1095: 
1096:     /**
1097:      * 06 (I-2)：已完成 requestId 的附件元数据读取 —— 只读 04 元数据（不加载字节、不要求
1098:      * 原件仍在线），空 id 列表返回空列表（不与 04 服务交互）。
1099:      */
1100:     private fun resolveRequestedAttachmentSnapshots(
1101:         contactId: Long,
1102:         attachmentIds: List<String>,
1103:         authenticatedUsername: String?
1104:     ): List<OutboundAttachmentSnapshot> {
1105:         if (attachmentIds.isEmpty()) {
1106:             return emptyList()
1107:         }
1108:         val service = requireNotNull(outboundAttachmentService) {
1109:             "OutboundAttachmentService is not wired for attachment comparison"
1110:         }
1111:         return service.loadSnapshots(contactId, attachmentIds, authenticatedUsername.orEmpty())
1112:     }
1113: 
1114:     /**
1115:      * 06 (I-2)：有序附件语义比较 —— filename/contentType/byteLength/sha256 逐项、按顺序
1116:      * 全等即视为同一附件集合（同内容重新上传得到的新 id 仍算相同：语义相同即不改变
1117:      * 已发送事实）。
1118:      */
1119:     private fun sameOutboundAttachmentSet(
1120:         original: List<OutboundAttachmentSnapshot>,
1121:         requested: List<OutboundAttachmentSnapshot>
1122:     ): Boolean =
1123:         original.size == requested.size && original.indices.all { index ->
1124:             val left = original[index]
1125:             val right = requested[index]
1126:             left.filename == right.filename &&
1127:                 left.contentType == right.contentType &&
1128:                 left.byteLength == right.byteLength &&
1129:                 left.sha256 == right.sha256
1130:         }
1131: 
1132:     private fun auditNote(
1133:         inboundProcessingId: Long?,
1134:         contactId: Long,
1135:         findings: List<SafetyFinding>,
1136:         requiresStrong: Boolean
1137:     ): String = buildString {
1138:         if (inboundProcessingId != null) {
1139:             append("Manual rich reply sent for inbound processing $inboundProcessingId")
1140:         } else {
1141:             append("Manual rich reply sent for conversation of contact $contactId")
1142:         }
1143:         if (findings.isNotEmpty()) {
1144:             append("; safety findings confirmed: ")
1145:             val codes = findings.map { it.code }
1146:             append(codes.take(10).joinToString(","))
1147:             if (codes.size > 10) {
1148:                 append("+").append(codes.size)
1149:             }
1150:         }
1151:         if (requiresStrong) {
1152:             append("; strong confirmation typed")
1153:         }
1154:     }
1155: 
1156:     fun suggestComposedReply(inboundProcessingId: Long): TrustWorkbenchSuggestResult {
1157:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1158:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1159:         val inboundText = inboundMessageBody(record)
1160:         val contactId = record.expertContactId
1161:             ?: error("Inbound mail not bound to a contact")
1162:         val contact = expertContactRepository.findById(contactId)
1163:             .orElseThrow { error("Expert contact not found: $contactId") }
1164:         val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
1165:         val autoSelection = qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
1166:         return buildTrustWorkbenchSuggest(inboundText, autoSelection, autoSelection.sendQaRuleIds)
1167:     }
1168: 
1169:     fun evaluateComposedReply(
1170:         inboundProcessingId: Long,
1171:         factRuleIds: List<Long>
1172:     ): TrustWorkbenchEvaluateResult {
1173:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1174:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1175:         val inboundText = inboundMessageBody(record)
1176:         val contactId = record.expertContactId
1177:             ?: error("Inbound mail not bound to a contact")
1178:         val contact = expertContactRepository.findById(contactId)
1179:             .orElseThrow { error("Expert contact not found: $contactId") }
1180:         val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
1181:         val autoSuggested = qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
1182:         val selection = qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient)
1183:         val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
1184:             selection.requestFacts,
1185:             selection.sendQaRuleIds
1186:         )
1187:         return TrustWorkbenchEvaluateResult(
1188:             canonicalFactIds = selection.sendQaRuleIds,
1189:             suggestedFactIds = autoSuggested,
1190:             draftReadiness = readiness.name,
1191:             requestCoverage = toRequestCoverage(selection.requestFacts),
1192:             gapDetected = selection.requestFacts.any {
1193:                 it.status.name == "UNSUPPORTED" || it.status.name == "PARTIAL"
1194:             }
1195:         )
1196:     }
1197: 
1198:     private fun buildTrustWorkbenchSuggest(
1199:         inboundText: String,
1200:         selection: ResolvedQaRules,
1201:         suggestedFactIds: List<Long>
1202:     ): TrustWorkbenchSuggestResult {
1203:         val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
1204:             selection.requestFacts,
1205:             selection.sendQaRuleIds
1206:         )
1207:         val matchableRules = qaRuleRepository.findAllEnabledOrdered()
1208:             .filter { it.answerBody.trim().isNotBlank() && it.isMatchable() }
1209:         val categories = qaCategoryRepository.findAll().filter { it.enabled }
1210:         val rulesByCategory = categories.map { category ->
1211:             val categoryId = requireNotNull(category.id)
1212:             CategoryRulesGroup(
1213:                 categoryId = categoryId,
1214:                 categoryCode = category.categoryCode,
1215:                 categoryName = category.categoryName,
1216:                 composeOrder = category.composeOrder,
1217:                 rules = matchableRules
1218:                     .filter { it.categoryId == categoryId }
1219:                     .map { it.toTrustSuggestRule() }
1220:             )
1221:         }.sortedBy { it.composeOrder }
1222:         val gapItems = selection.requestFacts.map { fact ->
1223:             GapItem(
1224:                 text = fact.requestText,
1225:                 candidateRuleIds = fact.factRuleIds
1226:             )
1227:         }
1228:         return TrustWorkbenchSuggestResult(
1229:             suggestedRuleIds = suggestedFactIds,
1230:             suggestedRules = matchableRules
1231:                 .filter { it.id in suggestedFactIds }
1232:                 .map { it.toTrustSuggestRule() },
1233:             rulesByCategory = rulesByCategory,
1234:             gapItems = gapItems,
1235:             gapDetected = gapItems.any { it.candidateRuleIds.isEmpty() },
1236:             matchedCategoryIds = matchableRules.map { it.categoryId }.distinct(),
1237:             draftReadiness = readiness.name,
1238:             requestCoverage = toRequestCoverage(selection.requestFacts),
1239:             inboundText = inboundText
1240:         )
1241:     }
1242: 
1243:     private data class CanonicalFactResolution(
1244:         val canonicalFactIds: List<Long>,
1245:         val degradedCodes: List<String>   // 有序、去重
1246:     )
1247: 
1248:     // 计划 04 (T2.1): 显式 QA 选择的唯一放宽接缝（I-1）。select() 本身逐字不变；
1249:     // 仅当它抛 IllegalArgumentException（validateExplicitSelection /
1250:     // validateExplicitRulesMatchRequests 的异常）时降级为可确认的风险：
1251:     // - I-2: 只捕获 IllegalArgumentException，真故障（DB/IO/ResponseStatusException）向上抛；
1252:     // - I-3: 降级产出的 canonicalFactIds 只能是运营选择的子集（partition.selectable
1253:     //   再经一次真 select()），永不回退自动全集；
1254:     // - I-5: 子集仍走真 select()，用其 sendQaRuleIds 作为取证源；
1255:     // - I-6: 子集为空时直接返回 emptyList()，禁止调 select(emptyList())。
1256:     private fun canonicalizeFactRuleIds(
1257:         inboundText: String,
1258:         requestedRuleIds: List<Long>,
1259:         researchProfileSufficient: Boolean
1260:     ): CanonicalFactResolution {
1261:         try {
1262:             return CanonicalFactResolution(
1263:                 qaFactSelectionService.select(inboundText, requestedRuleIds, researchProfileSufficient).sendQaRuleIds,
1264:                 emptyList()
1265:             )
1266:         } catch (ex: IllegalArgumentException) {
1267:             val partition = qaFactSelectionService.partitionExplicitSelection(inboundText, requestedRuleIds)
1268:             val codes = mutableListOf<String>()
1269:             if (partition.noRequests) {
1270:                 codes += QA_FACT_NO_EXTRACTABLE_REQUEST
1271:             }
1272:             if (partition.unmatched.isNotEmpty()) {
1273:                 codes += QA_FACT_NOT_MATCHING_REQUEST
1274:             }
1275:             if (partition.unavailable.isNotEmpty()) {
1276:                 codes += QA_FACT_UNAVAILABLE
1277:             }
1278:             val ids = if (partition.selectable.isEmpty()) {
1279:                 emptyList()
1280:             } else {
1281:                 try {
1282:                     qaFactSelectionService.select(inboundText, partition.selectable, researchProfileSufficient)
1283:                         .sendQaRuleIds
1284:                 } catch (innerEx: IllegalArgumentException) {
1285:                     codes += QA_FACT_UNAVAILABLE
1286:                     emptyList()
1287:                 }
1288:             }
1289:             return CanonicalFactResolution(ids, codes.distinct())
1290:         }
1291:     }
1292: 
1293:     private fun resolveResearchProfileSufficient(contact: ExpertContact, inboundText: String): Boolean {
1294:         val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
1295:         val context = aiReplyContextService.build(contact, records, inboundText, "")
1296:         return context.researchProfileSufficient
1297:     }
1298: 
1299:     private fun inboundMessageBody(record: com.weibo.talentintroduction.mail.domain.InboundMailProcessing): String =
1300:         record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()
1301: 
1302:     private fun archiveLiveUnsupportedAnswers(
1303:         inboundProcessingId: Long,
1304:         templateTextBody: String?,
1305:         finalTextBody: String,
1306:         operatorName: String?,
1307:         outboundMailRecordId: Long?,
1308:         // 03 (阶段 4): 直接复用发送前 verifyAssembly 的已验证结果；不再发送成功后
1309:         // 二次 assemble（避免前后两次解析漂移）。
1310:         verifiedAssembly: VerifiedTrustReplyAssembly?
1311:     ): UnsupportedAnswerIndexArchiveResult {
1312:         if (verifiedAssembly == null || outboundMailRecordId == null) {
1313:             return UnsupportedAnswerIndexArchiveResult()
1314:         }
1315:         val assembled = verifiedAssembly.response
1316:         val operatorDirectedCount = assembled.itemVersions.count {
1317:             it.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
1318:         }
1319:         return try {
1320:             if (templateTextBody.isNullOrBlank()) {
1321:                 return UnsupportedAnswerIndexArchiveResult()
1322:             }
1323:             // c6 (T-2 / I-4): 去掉「正文一字未改才归档」门槛——运营编辑过正文的样本
1324:             // 同样照常归档（由 service 在文档上置 editedByOperator = true，A-2）。
1325:             // Repair R-2 (V-2)：资格判定统一走 service 的权威 isArchiveEligible
1326:             // （四种 handling × 两种 generationKind，operatorInstruction 可选）。
1327:             val eligibleVersions = assembled.itemVersions.filter { version ->
1328:                 unsupportedAnswerIndexService.isArchiveEligible(version)
1329:             }
1330:             if (eligibleVersions.isEmpty()) {
1331:                 return UnsupportedAnswerIndexArchiveResult()
1332:             }
1333:             // 归档失败不得阻断主流程：source 解析异常（含 null 返回）按归档失败处理，
1334:             // 绝不把异常抛出到发送主流程（A-7 回归语义）。
1335:             val resolved = requireNotNull(trustReplyWorkbenchService.resolveSource(assembled.source)) {
1336:                 "Unsupported answer archive source could not be resolved for inbound $inboundProcessingId"
1337:             }
1338:             unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
1339:                 source = resolved,
1340:                 versions = eligibleVersions,
1341:                 qualificationId = outboundMailRecordId.toString(),
1342:                 approvedBy = normalizeArchiveOperatorName(operatorName),
1343:                 createdAt = Instant.now(),
1344:                 // Repair R-1 (V-3)：逐条确定性映射 → finalParagraphText（步骤 03 权威段落）。
1345:                 finalParagraphs = assembled.finalParagraphByRequestKey
1346:             )
1347:         } catch (error: TrustReplyWorkbenchException) {
1348:             log.warn(
1349:                 "Unsupported answer archive rejected for inbound {}: {}",
1350:                 inboundProcessingId,
1351:                 error.code
1352:             )
1353:             failedArchive(operatorDirectedCount)
1354:         } catch (error: Exception) {
1355:             log.warn(
1356:                 "Unsupported answer archive failed for inbound {}: {}",
1357:                 inboundProcessingId,
1358:                 error.javaClass.simpleName
1359:             )
1360:             failedArchive(operatorDirectedCount.coerceAtLeast(eligibleFailureCount(verifiedAssembly)))
1361:         }
1362:     }
1363: 
1364:     private fun failedArchive(failedCount: Int): UnsupportedAnswerIndexArchiveResult =
1365:         UnsupportedAnswerIndexArchiveResult(
1366:             status = UnsupportedAnswerArchiveStatus.FAILED,
1367:             failedCount = failedCount.coerceAtLeast(1)
1368:         )
1369: 
1370:     // Repair R-2 (V-2)：失败归档计数对齐权威资格判定（旧口径只数
1371:     // ANSWER_FROM_OPERATOR_INPUT，与放宽后的允许集合不一致）。
1372:     private fun eligibleFailureCount(verifiedAssembly: VerifiedTrustReplyAssembly): Int =
1373:         verifiedAssembly.response.itemVersions.count { version ->
1374:             unsupportedAnswerIndexService.isArchiveEligible(version)
1375:         }.coerceAtLeast(1)
1376: 
1377:     // Repair R-2 (V-2)：线上侧资格判定统一委托 service 的权威 isArchiveEligible
1378:     // （四种 handling × 两种 generationKind，operatorInstruction 可选）；旧窄化过滤
1379:     // （仅 ANSWER_FROM_OPERATOR_INPUT × AI_GENERATED + 必填说明）已废弃。
1380:     private fun isArchiveEligibleOperatorDirectedVersion(version: TrustReplyItemVersion): Boolean =
1381:         unsupportedAnswerIndexService.isArchiveEligible(version)
1382: 
1383:     private fun normalizeArchiveOperatorName(value: String?): String {
1384:         val normalized = value?.trim().orEmpty()
1385:         return normalized.ifEmpty { "UNKNOWN" }.take(128)
1386:     }
1387: 
1388:     private fun toRequestCoverage(requestFacts: List<RequestFactItem>): List<RequestCoverageItem> =
1389:         requestFacts.map { fact ->
1390:             RequestCoverageItem(
1391:                 index = fact.index,
1392:                 requestText = fact.requestText,
1393:                 status = fact.status.name,
1394:                 factRuleIds = fact.factRuleIds,
1395:                 intents = fact.intents.map { intent ->
1396:                     IntentCoverageResponse(
1397:                         intentKey = intent.intentKey,
1398:                         title = intent.title,
1399:                         status = intent.status,
1400:                         evidenceRuleIds = intent.evidenceRuleIds,
1401:                         missingEvidenceKeys = intent.missingEvidenceKeys,
1402:                         requiresResearchContext = intent.requiresResearchContext
1403:                     )
1404:                 }
1405:             )
1406:         }
1407: 
1408:     private fun QaRule.toTrustSuggestRule() = SuggestQaRule(
1409:         id = requireNotNull(id),
1410:         categoryId = categoryId,
1411:         displayName = displayName,
1412:         sectionTitle = sectionTitle,
1413:         replySubject = replySubject,
1414:         replyBody = "",
1415:         keywords = keywords,
1416:         replyPolicy = replyPolicyEnum().name
1417:     )
1418: 
1419:     private fun resolvePendingReplyAccount(
1420:         requestedAccountCode: String?,
1421:         inboundSenderAccountCode: String
1422:     ) = mailSenderAccountService.getManualSendAccount(
1423:         requestedAccountCode?.takeIf { it.isNotBlank() } ?: inboundSenderAccountCode
1424:     )
1425: 
1426:     internal data class ManualReplyDeliveryClassification(
1427:         val attemptStatus: String,
1428:         val isSent: Boolean,
1429:         val isSafeRetry: Boolean,
1430:         val isUnknown: Boolean,
1431:         val errorSummary: String?
1432:     )
1433: 
1434:     /** 03 (I-1)：会议正文一致性核对用的规范化 —— CRLF→LF、NBSP→space、
1435:      *  所有连续空白→单空格、trim；expected/finalText/htmlToPlainText(finalHtml) 同款。 */
1436:     private fun normalizeBodyText(value: String): String =
1437:         value.replace("\r\n", "\n")
1438:             .replace('\r', '\n')
1439:             .replace('\u00A0', ' ')
1440:             .replace(Regex("\\s+"), " ")
1441:             .trim()
1442: 
1443:     private fun buildFinalValidationText(subject: String, finalText: String, finalHtml: String): String {
1444:         val htmlPlain = mailContentService.htmlToPlainText(finalHtml)
1445:         val hrefs = HREF_EXTRACTOR.findAll(finalHtml).map { m ->
1446:             m.groupValues[1].takeIf { it.isNotEmpty() }
1447:                 ?: m.groupValues[2].takeIf { it.isNotEmpty() }
1448:                 ?: m.groupValues[3]
1449:         }.toList()
1450:         return listOfNotNull(
1451:             subject.takeIf { it.isNotBlank() },
1452:             finalText.takeIf { it.isNotBlank() },
1453:             htmlPlain.takeIf { it.isNotBlank() },
1454:             hrefs.takeIf { it.isNotEmpty() }?.joinToString(" ")
1455:         ).joinToString(" ")
1456:     }
1457: 
1458:     private fun collectSafetyFindings(
1459:         verificationText: String,
1460:         carriesQa: Boolean,
1461:         canonicalFactIds: List<Long>,
1462:         contact: ExpertContact,
1463:         inboundText: String,
1464:         researchProfileSufficient: Boolean,
1465:         operatorAuthorizedActions: Set<AiReplyAction>,
1466:         // 计划 04 (T2.4): 发送路径显式 QA 选择被降级时产生的诊断码（默认空，预检不传——
1467:         // 预检已有 AI_REPLY_PREFLIGHT_SOURCE_CHANGED，不重复报，N-6）。
1468:         degradedFactCodes: List<String> = emptyList(),
1469:         // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection（非 null 时禁止再次
1470:         // 调用 qaFactSelectionService.select()）；legacy 路径保持 null 与既有 strict
1471:         // select 行为逐字一致。
1472:         verifiedSelection: ResolvedQaRules? = null,
1473:         // 03b (I-47): RAG 发送时整段绕开 QA selection/trust-gap/intent 链（见方法体
1474:         // 守卫处注释）。既有两条路径不传（默认 false），逻辑逐字不变。
1475:         ragSend: Boolean = false,
1476:         // T2.7: 会话（无来信锚点）回信路径传 false —— 数字/URL、高风险声明、信任话术
1477:         // 通用检查跑完后立即返回，不跑依赖来信语义的 QA selection/trust-gap/intent 门禁。
1478:         // 来信路径默认 true，行为逐字不变。
1479:         runInboundSemanticChecks: Boolean = true
1480:     ): List<SafetyFinding> {
1481:         val findings = mutableListOf<SafetyFinding>()
1482:         fun add(code: String, sentence: String? = null) {
1483:             if (findings.none { it.code == code }) {
1484:                 findings += SafetyFinding(
1485:                     code = code,
1486:                     severity = if (code == AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL) {
1487:                         SafetySeverity.STRONG
1488:                     } else {
1489:                         SafetySeverity.NORMAL
1490:                     },
1491:                     sentence = sentence
1492:                 )
1493:             }
1494:         }
1495: 
1496:         // I-9: 新增码走默认 NORMAL（一次确认弹窗即可，不要求输入「确认发送」）。
1497:         degradedFactCodes.forEach { add(it) }
1498: 
1499:         if (carriesQa && canonicalFactIds.isNotEmpty()) {
1500:             val claimValidation = aiReplyHighRiskClaimValidator.validatePlainText(
1501:                 verificationText, canonicalFactIds
1502:             )
1503:             if (!claimValidation.valid) {
1504:                 claimValidation.warningCodes.forEach { add(it) }
1505:                 if (claimValidation.warningCodes.isEmpty()) {
1506:                     add("CLAIM_VALIDATION_FAILED")
1507:                 }
1508:             }
1509:         } else if (carriesQa && canonicalFactIds.isEmpty()) {
1510:             add("QA_FACTS_ALL_INVALID")
1511:         } else {
1512:             if (aiReplyHighRiskClaimValidator.containsHallucinatedNumberOrUrl(verificationText, "")) {
1513:                 add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT)
1514:             }
1515:             if (aiReplyHighRiskClaimValidator.containsUnbackedHighRiskDeclarations(verificationText, "")) {
1516:                 add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED)
1517:             }
1518:         }
1519: 
1520:         if (aiReplyHighRiskClaimValidator.containsTrustRhetoric(verificationText)) {
1521:             add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC)
1522:         }
1523: 
1524:         // 03b (I-47): RAG 发送整段短路 —— 下面的 selection 求值、
1525:         // hasBlockingTrustGapForSelection、intent 遍历与 AiReplyActionPolicy 门禁
1526:         // 全部依赖 qa_rule（I-40/G-4），RAG 路径不适用（D-15）。上面的纯文本检查
1527:         // （hallucinated number/url、unbacked high-risk、trust rhetoric）已执行完毕
1528:         // 并保留 —— 它们不读 qa_rule，且是二次确认弹窗（safetyWarningConfirmed /
1529:         // strongConfirmationText）的触发源（D-1：人是唯一的门）。
1530:         if (ragSend || !runInboundSemanticChecks) {
1531:             return findings
1532:         }
1533: 
1534:         val selection = verifiedSelection ?: if (canonicalFactIds.isNotEmpty()) {
1535:             qaFactSelectionService.select(inboundText, canonicalFactIds, researchProfileSufficient)
1536:         } else {
1537:             qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
1538:         }
1539:         val hasBlockingTrust = aiReplyDraftService.hasBlockingTrustGapForSelection(selection.requestFacts)
1540:         if (hasBlockingTrust &&
1541:             aiReplyHighRiskClaimValidator.containsConfidentialitySubstitute(verificationText)
1542:         ) {
1543:             add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE)
1544:         }
1545: 
1546:         for (fact in selection.requestFacts) {
1547:             for (intent in fact.intents) {
1548:                 val isAgencyOrCompany = intent.intentKey.startsWith("agency.") ||
1549:                     intent.intentKey.startsWith("company.")
1550:                 val isEnterprise = intent.intentKey.startsWith("enterprise.")
1551:                 if (isAgencyOrCompany || isEnterprise) {
1552:                     val evidenceIds = intent.evidenceRuleIds
1553:                     if (evidenceIds.isNotEmpty()) {
1554:                         val sourceText = aiReplyHighRiskClaimValidator.resolveSourceText(evidenceIds)
1555:                         if (sourceText != null) {
1556:                             if (isAgencyOrCompany) {
1557:                                 if (aiReplyHighRiskClaimValidator.isRoleDisclosureRequired(sourceText) &&
1558:                                     !aiReplyHighRiskClaimValidator.containsRoleDisclosure(verificationText)
1559:                                 ) {
1560:                                     add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED)
1561:                                 }
1562:                             }
1563:                             if (isEnterprise) {
1564:                                 if (aiReplyHighRiskClaimValidator.isEnterpriseUncertaintyRequired(sourceText) &&
1565:                                     (aiReplyHighRiskClaimValidator.containsEnterpriseCertainty(verificationText) ||
1566:                                         !aiReplyHighRiskClaimValidator.containsEnterpriseUncertainty(verificationText))
1567:                                 ) {
1568:                                     add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_ENTERPRISE_UNGROUNDED)
1569:                                 }
1570:                             }
1571:                         }
1572:                     }
1573:                 }
1574:             }
1575:         }
1576: 
1577:         val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
1578:             AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()) + operatorAuthorizedActions,
1579:             hasBlockingTrust
1580:         )
1581:         val violations = AiReplyActionPolicy.findViolations(verificationText, restrictedActions)
1582:         violations.forEach { violation ->
1583:             if (violation.code != null) {
1584:                 add(violation.code, violation.sentence)
1585:             }
1586:         }
1587: 
1588:         return findings
1589:     }
1590: 
1591:     private fun classifyDelivery(delivered: DeliveredMail): ManualReplyDeliveryClassification {
1592:         return when {
1593:             delivered.status == "SENT" && delivered.errorCategory == SmtpErrorCategory.SUCCESS ->
1594:                 ManualReplyDeliveryClassification(
1595:                     attemptStatus = MailSendAttemptStatus.SENT,
1596:                     isSent = true, isSafeRetry = false, isUnknown = false,
1597:                     errorSummary = null
1598:                 )
1599:             delivered.errorCategory == SmtpErrorCategory.TRANSIENT &&
1600:                 delivered.smtpResponseCode != null &&
1601:                 delivered.smtpResponseCode in 400..499 ->
1602:                 ManualReplyDeliveryClassification(
1603:                     attemptStatus = MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
1604:                     isSent = false, isSafeRetry = true, isUnknown = false,
1605:                     errorSummary = delivered.errorDetail
1606:                 )
1607:             delivered.errorCategory == SmtpErrorCategory.PERMANENT &&
1608:                 delivered.smtpResponseCode != null &&
1609:                 delivered.smtpResponseCode in 500..599 ->
1610:                 ManualReplyDeliveryClassification(
1611:                     attemptStatus = MailSendAttemptStatus.FAILED,
1612:                     isSent = false, isSafeRetry = false, isUnknown = false,
1613:                     errorSummary = delivered.errorDetail
1614:                 )
1615:             delivered.errorCategory == SmtpErrorCategory.INFRASTRUCTURE &&
1616:                 delivered.errorDetail?.startsWith("AUTH_FAILED:") == true ->
1617:                 ManualReplyDeliveryClassification(
1618:                     attemptStatus = MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
1619:                     isSent = false, isSafeRetry = true, isUnknown = false,
1620:                     errorSummary = delivered.errorDetail
1621:                 )
1622:             else ->
1623:                 ManualReplyDeliveryClassification(
1624:                     attemptStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
1625:                     isSent = false, isSafeRetry = false, isUnknown = true,
1626:                     errorSummary = delivered.errorDetail
1627:                 )
1628:         }
1629:     }
1630: 
1631:     @Transactional
1632:     fun markResolved(
1633:         inboundProcessingId: Long,
1634:         resolvedBy: String?,
1635:         operatorName: String?,
1636:         note: String?
1637:     ) {
1638:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1639:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1640:         require(record.processStatus == "MANUAL_REVIEW") { "Record $inboundProcessingId is not in MANUAL_REVIEW" }
1641:         record.expertContactId?.let { contactId ->
1642:             check(inboundMailProcessingRepository.lockPendingContact(contactId) == contactId) {
1643:                 "Expert contact not found: $contactId"
1644:             }
1645:         }
1646: 
1647:         val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: resolvedBy ?: "UNKNOWN"
1648:         val now = LocalDateTime.now()
1649:         check(inboundMailProcessingRepository.resolvePending(inboundProcessingId, actualOperator, now) == 1) {
1650:             "Inbound mail changed concurrently: $inboundProcessingId"
1651:         }
1652: 
1653:         val contactId = record.expertContactId
1654:         if (contactId != null) {
1655:             val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
1656:                 contactId, "MANUAL_REVIEW"
1657:             )
1658:             if (remaining == 0L) {
1659:                 expertContactRepository.findById(contactId).ifPresent { contact ->
1660:                     if (contact.needsManualAttention) {
1661:                         expertContactRepository.save(contact.copy(needsManualAttention = false))
1662:                     }
1663:                 }
1664:             }
1665:         }
1666: 
1667:         operatorActionLogService.record(
1668:             targetType = "INBOUND_MAIL_PROCESSING",
1669:             targetId = inboundProcessingId,
1670:             actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
1671:             expertContactId = contactId,
1672:             inboundProcessingId = inboundProcessingId,
1673:             before = mapOf(
1674:                 "processStatus" to "MANUAL_REVIEW",
1675:                 "processReason" to record.processReason,
1676:                 "reasonType" to record.reasonType
1677:             ),
1678:             after = mapOf(
1679:                 "processStatus" to "PROCESSED",
1680:                 "processReason" to "MANUAL_RESOLVED",
1681:                 "reasonType" to "MANUAL_RESOLVED"
1682:             ),
1683:             operatorName = actualOperator,
1684:             note = note
1685:         )
1686:     }
1687: 
1688:     @Transactional
1689:     fun cancelResolved(
1690:         inboundProcessingId: Long,
1691:         operatorName: String?,
1692:         note: String?
1693:     ) {
1694:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1695:             .orElseThrow {
1696:                 ResponseStatusException(
1697:                     HttpStatus.NOT_FOUND,
1698:                     "Inbound mail processing not found: $inboundProcessingId"
1699:                 )
1700:             }
1701:         if (record.processStatus != "PROCESSED" ||
1702:             record.processReason != "MANUAL_RESOLVED" ||
1703:             record.reasonType != "MANUAL_RESOLVED"
1704:         ) {
1705:             throw ResponseStatusException(
1706:                 HttpStatus.CONFLICT,
1707:                 "Record $inboundProcessingId is not manually resolved and cannot be cancelled"
1708:             )
1709:         }
1710:         record.expertContactId?.let { contactId ->
1711:             check(inboundMailProcessingRepository.lockPendingContact(contactId) == contactId) {
1712:                 "Expert contact not found: $contactId"
1713:             }
1714:         }
1715: 
1716:         val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
1717:         val now = LocalDateTime.now()
1718:         val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
1719:         if (updated != 1) {
1720:             throw ResponseStatusException(
1721:                 HttpStatus.CONFLICT,
1722:                 "Record $inboundProcessingId changed concurrently and cannot be cancelled"
1723:             )
1724:         }
1725: 
1726:         val contactId = record.expertContactId
1727:         if (contactId != null) {
1728:             expertContactRepository.findById(contactId).ifPresent { contact ->
1729:                 if (!contact.needsManualAttention) {
1730:                     expertContactRepository.save(contact.copy(needsManualAttention = true))
1731:                 }
1732:             }
1733:         }
1734: 
1735:         operatorActionLogService.record(
1736:             targetType = "INBOUND_MAIL_PROCESSING",
1737:             targetId = inboundProcessingId,
1738:             actionType = OperatorActionType.CANCEL_INBOUND_RESOLVED,
1739:             expertContactId = contactId,
1740:             inboundProcessingId = inboundProcessingId,
1741:             before = mapOf(
1742:                 "processStatus" to record.processStatus,
1743:                 "processReason" to record.processReason,
1744:                 "reasonType" to record.reasonType,
1745:                 "resolvedBy" to record.resolvedBy,
1746:                 "resolvedAt" to record.resolvedAt
1747:             ),
1748:             after = mapOf(
1749:                 "processStatus" to "MANUAL_REVIEW",
1750:                 "processReason" to "MANUAL_REOPENED",
1751:                 "reasonType" to null,
1752:                 "resolvedBy" to null,
1753:                 "resolvedAt" to null
1754:             ),
1755:             operatorName = actualOperator,
1756:             note = note
1757:         )
1758:     }
1759: 
1760:     fun preflightEditedAiReply(
1761:         inboundProcessingId: Long,
1762:         factRuleIds: List<Long>,
1763:         expectedEvidenceSetVersion: String,
1764:         textBody: String
1765:     ): AiReplyPreflightResult {
1766:         require(textBody.isNotBlank() && textBody.length <= 20000) {
1767:             "textBody must be non-empty and <= 20000 characters"
1768:         }
1769:         require(factRuleIds.size <= 50 && factRuleIds.all { it > 0 } && factRuleIds.size == factRuleIds.distinct().size) {
1770:             "factRuleIds must be positive, deduplicated, and <= 50 items"
1771:         }
1772:         require(expectedEvidenceSetVersion.isEmpty() || expectedEvidenceSetVersion.length <= 128) {
1773:             "expectedEvidenceSetVersion must be <= 128 characters"
1774:         }
1775:         require(expectedEvidenceSetVersion.isEmpty() || AiReplyDraftService.PREFLIGHT_VERSION_CHARSET.matches(expectedEvidenceSetVersion)) {
1776:             "expectedEvidenceSetVersion contains invalid characters"
1777:         }
1778: 
1779:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1780:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1781:         val inboundText = inboundMessageBody(record)
1782:         val contactId = record.expertContactId
1783:             ?: error("Inbound mail not bound to a contact")
1784:         val contact = expertContactRepository.findById(contactId)
1785:             .orElseThrow { error("Expert contact not found: $contactId") }
1786:         val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
1787: 
1788:         val warningCodes = mutableListOf<String>()
1789: 
1790:         val selection = if (factRuleIds.isNotEmpty()) {
1791:             try {
1792:                 qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient)
1793:             } catch (ex: Exception) {
1794:                 warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1795:                 qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
1796:             }
1797:         } else {
1798:             qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
1799:         }
1800: 
1801:         val canonicalFactIds = if (factRuleIds.isNotEmpty()) selection.sendQaRuleIds else emptyList()
1802:         val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
1803:             selection.requestFacts,
1804:             canonicalFactIds
1805:         )
1806:         val (currentEvidenceSetVersion, _, _) = aiReplyDraftService.buildEvidenceSnapshotForSelection(canonicalFactIds)
1807: 
1808:         if (expectedEvidenceSetVersion.isNotBlank() && expectedEvidenceSetVersion != currentEvidenceSetVersion) {
1809:             if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
1810:                 warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1811:             }
1812:         }
1813: 
1814:         if (factRuleIds.isNotEmpty()) {
1815:             val validCount = canonicalFactIds.size
1816:             val requestedCount = factRuleIds.size
1817:             if (validCount < requestedCount) {
1818:                 if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
1819:                     warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1820:                 }
1821:             }
1822: 
1823:             for (ruleId in factRuleIds) {
1824:                 val rule = try {
1825:                     qaRuleRepository.findById(ruleId).orElse(null)
1826:                 } catch (_: Exception) {
1827:                     null
1828:                 }
1829:                 if (rule == null || !rule.enabled) {
1830:                     if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
1831:                         warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1832:                     }
1833:                     continue
1834:                 }
1835:                 if (rule.answerBody.isBlank()) {
1836:                     if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
1837:                         warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1838:                     }
1839:                 }
1840:                 if (rule.replyPolicyEnum() == QaReplyPolicy.NEVER) {
1841:                     if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
1842:                         warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
1843:                     }
1844:                 }
1845:             }
1846:         } else {
1847:             warningCodes += AI_REPLY_PREFLIGHT_NO_EVIDENCE
1848:         }
1849: 
1850:         val safetyFindings = collectSafetyFindings(
1851:             verificationText = textBody,
1852:             carriesQa = factRuleIds.isNotEmpty(),
1853:             canonicalFactIds = canonicalFactIds,
1854:             contact = contact,
1855:             inboundText = inboundText,
1856:             researchProfileSufficient = researchProfileSufficient,
1857:             // I-8: 预检没有 assembly 入参，改从持久化快照推导；读不到即空集（fail-closed）。
1858:             operatorAuthorizedActions = trustReplyWorkbenchService.operatorAuthorizedActions(
1859:                 TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, inboundProcessingId)
1860:             )
1861:         )
1862:         safetyFindings.forEach { finding ->
1863:             if (finding.code !in warningCodes) {
1864:                 warningCodes += finding.code
1865:             }
1866:         }
1867: 
1868:         val checkedTextHash = AiReplyDraftService.sha256Hex(textBody)
1869:         val distinctWarnings = warningCodes.distinct()
1870: 
1871:         val status = if (distinctWarnings.isEmpty()) "PASS" else "WARNING"
1872: 
1873:         return AiReplyPreflightResult(
1874:             status = status,
1875:             warningCodes = distinctWarnings,
1876:             canonicalFactIds = canonicalFactIds,
1877:             evidenceReadiness = readiness.name,
1878:             currentEvidenceSetVersion = currentEvidenceSetVersion,
1879:             checkedTextHash = checkedTextHash
1880:         )
1881:     }
1882: }
1883: 
1884: // ---------------------------------------------------------------------------
1885: // 人工富文本发送共同实现的最小 source/evidence 上下文（T2.4）。私有文件级类型，
1886: // 只被 PendingMailOperationService 使用。
1887: // ---------------------------------------------------------------------------
1888: 
1889: /**
1890:  * 发送生命周期最小 source context：联系人、可空 inbound id、可空锚点 mail record id、
1891:  * 账号 code、落库 inReplyTo、SMTP inReplyTo/references（T2.4）。会话回信路径
1892:  * inboundProcessingId = null 且携带真实 requestId/anchor；来信路径反向（I-3/I-4）。
1893:  */
1894: private data class ManualRichSendSource(
1895:     val contact: ExpertContact,
1896:     val contactId: Long,
1897:     val inboundProcessingId: Long?,
1898:     val inboundRecord: com.weibo.talentintroduction.mail.domain.InboundMailProcessing?,
1899:     val account: MailSenderAccount,
1900:     val accountCode: String,
1901:     /** 落库 mail_record.inReplyTo 与 SendPayload.inReplyTo 的值（线程锚点）。 */
1902:     val persistInReplyTo: String?,
1903:     /** SMTP In-Reply-To 头；来信路径恒 null（I-8 保持现状），会话路径 = 锚点 Message-ID。 */
1904:     val smtpInReplyTo: String?,
1905:     /** SMTP References 头；会话路径 = 锚点自身 inReplyTo + 锚点 Message-ID（I-7）。 */
1906:     val smtpReferences: String?,
1907:     /** 会话回信审计 before 的锚点 mail record id（I-11）；来信路径恒 null。 */
1908:     val anchorMailRecordId: Long?,
1909:     /** 会话回信幂等 requestId（已规范化 UUID）；来信路径恒 null（I-4）。 */
1910:     val requestId: String?,
1911:     val identityAnchor: MailRecord? = null
1912: )
1913: 
1914: /**
1915:  * 来信语义侧的可选证据/归档上下文：QA/RAG/assembly/archive 与模板正文只属于来信路径；
1916:  * 会话回信路径用全默认值（canonical 空、不归档、不跑来信语义检查）。
1917:  */
1918: private data class ManualReplyEvidenceContext(
1919:     val verifiedAssembly: VerifiedTrustReplyAssembly? = null,
1920:     val carriesQa: Boolean = false,
1921:     val canonicalFactIds: List<Long> = emptyList(),
1922:     val serverSuggestedFactIds: List<Long> = emptyList(),
1923:     val degradedFactCodes: List<String> = emptyList(),
1924:     val ragMode: Boolean = false,
1925:     val ragFactCodes: List<String>? = null,
1926:     val ragFingerprintAtSend: String? = null,
1927:     val templateTextBody: String? = null,
1928:     val templateHtmlBody: String? = null,
1929:     val operatorAuthorizedActions: Set<AiReplyAction> = emptySet(),
1930:     val edited: Boolean? = null,
1931:     val inboundText: String = "",
1932:     val researchProfileSufficient: Boolean = false,
1933:     /** T2.7：false 时只跑数字/URL、高风险声明、信任话术通用检查后返回。来信默认 true。 */
1934:     val runInboundSemanticChecks: Boolean = true
1935: )
1936: 
1937: data class PendingMailSendResult(
1938:     val contactId: Long,
1939:     val senderAccountCode: String,
1940:     val mailType: String,
1941:     val subject: String,
1942:     val sendStatus: String,
1943:     val messageId: String?,
1944:     val unsupportedAnswerArchiveStatus: UnsupportedAnswerArchiveStatus = UnsupportedAnswerArchiveStatus.NOT_APPLICABLE,
1945:     val unsupportedAnswerArchivedCount: Int = 0,
1946:     val unsupportedAnswerArchiveFailedCount: Int = 0
1947: )
1948: 
1949: data class PendingQaReplyRequest(
1950:     val qaRuleId: Long,
1951:     val senderAccountCode: String?,
1952:     val operatorName: String?,
1953:     val useVariants: Boolean = false
1954: )
1955: 
1956: data class PendingManualRichReplyRequest(
1957:     val senderAccountCode: String?,
1958:     val subject: String,
1959:     val htmlBody: String,
1960:     val textBody: String?,
1961:     val operatorName: String?,
1962:     val qaRuleIds: List<Long>? = null,
1963:     val suggestedRuleIds: List<Long>? = null,
1964:     val ackSnippetId: Long? = null,
1965:     val edited: Boolean? = null,
1966:     val freeTextPreview: String? = null,
1967:     val useVariants: Boolean = false,
1968:     val templateTextBody: String? = null,
1969:     val templateHtmlBody: String? = null,
1970:     val trustReplyAssembly: TrustReplyAssembleRequest? = null,
1971:     // 03b (I-39~I-43): RAG 证据字段 —— 与 trustReplyAssembly 互斥（服务端 400
1972:     // SEND_EVIDENCE_SOURCE_CONFLICT）；既有 qaRuleIds 字段与类型不动。
1973:     val ragFactCodes: List<String>? = null,
1974:     val ragCorpusFingerprint: String? = null,
1975:     val safetyWarningConfirmed: Boolean = false,
1976:     val strongConfirmationText: String? = null,
1977:     // 03 (T1/I-1): 已预览会议配置 + 预览快照 sha256；服务端要求两者同时出现/同时为空。
1978:     val meeting: MeetingInput? = null,
1979:     val previewAttachmentSha256: String? = null,
1980:     // 06 (T1/I-1): 通用附件 id（用户选择顺序，04 上传产物）。默认空 = 既有无附件形态逐字
1981:     // 不变；非空时服务端按 (专家 + 会话身份) 重读 04 元数据与原件，越权/缺失在 claim 前失败。
1982:     val attachmentIds: List<String> = emptyList(),
1983:     val draftRef: MailReplyDraftRef? = null
1984: )
1985: 
1986: data class ComposedReplyRequest(
1987:     val qaRuleIds: List<Long>,
1988:     val overrideTextBody: String?,
1989:     val freeTextBody: String? = null,
1990:     val ackSnippetId: Long? = null,
1991:     val senderAccountCode: String?,
1992:     val operatorName: String?,
1993:     val useVariants: Boolean = false
1994: )
1995: 
1996: data class ComposedReplyEvaluateRequest(
1997:     val factRuleIds: List<Long>
1998: )
1999: 
2000: data class TrustWorkbenchSuggestResult(
2001:     val suggestedRuleIds: List<Long>,
2002:     val suggestedRules: List<SuggestQaRule>,
2003:     val rulesByCategory: List<CategoryRulesGroup>,
2004:     val gapItems: List<GapItem>,
2005:     val gapDetected: Boolean,
2006:     val matchedCategoryIds: List<Long>,
2007:     val draftReadiness: String,
2008:     val requestCoverage: List<RequestCoverageItem>,
2009:     val inboundText: String
2010: )
2011: 
2012: data class TrustWorkbenchEvaluateResult(
2013:     val canonicalFactIds: List<Long>,
2014:     val suggestedFactIds: List<Long>,
2015:     val draftReadiness: String,
2016:     val requestCoverage: List<RequestCoverageItem>,
2017:     val gapDetected: Boolean
2018: )
2019: 
2020: data class AiReplyPreflightRequest(
2021:     val factRuleIds: List<Long> = emptyList(),
2022:     val expectedEvidenceSetVersion: String = "",
2023:     val textBody: String
2024: )
2025: 
2026: data class AiReplyPreflightResult(
2027:     val status: String,
2028:     val warningCodes: List<String>,
2029:     val canonicalFactIds: List<Long>,
2030:     val evidenceReadiness: String,
2031:     val currentEvidenceSetVersion: String,
2032:     val checkedTextHash: String
2033: )
2034: 
2035: enum class SafetySeverity {
2036:     NORMAL,
2037:     STRONG
2038: }
2039: 
2040: data class SafetyFinding(
2041:     val code: String,
2042:     val severity: SafetySeverity,
2043:     val sentence: String?
2044: )
2045: 
2046: class ManualSendSafetyBlockedException(
2047:     val findings: List<SafetyFinding>
2048: ) : RuntimeException("发送内容安全校验未通过")
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
4: import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
5: import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
6: import com.weibo.talentintroduction.mail.repository.OutboundMailAttachmentRepository
7: import org.springframework.stereotype.Service
8: import java.io.InputStream
9: import java.nio.file.Files
10: import java.nio.file.Path
11: import java.nio.file.StandardCopyOption
12: import java.nio.file.StandardOpenOption
13: import java.security.MessageDigest
14: import java.time.LocalDateTime
15: import java.util.UUID
16: 
17: /**
18:  * 04：人工回复通用附件的有界上传、归属校验与原文件读取。
19:  *
20:  * 存储布局（I-4）：`MailAttachmentStorageProperties.basePath/outbound/<UUID>`；用户原始
21:  * 文件名永不参与拼路径，也不落库第二份 storage_key。写入顺序固定为「写临时文件 →
22:  * 流式累计尺寸/SHA → 原子移动到最终 UUID 路径 → 最后写元数据」，因此崩溃遗留文件不会
23:  * 被当成可用附件（元数据才是权威），写库失败会删掉本次刚落的文件。
24:  *
25:  * 归属（I-2）：`contactId` 必须是已存在专家，上传者身份只取会话登录名；草稿下载与
26:  * 首次发送解析都要求「同专家 + 同上传者」，不匹配一律 404（不区分不存在与越权）。
27:  *
28:  * 读取校验（I-4）：候选路径必须 realpath 落在 outbound 根内且是常规文件，尺寸与
29:  * SHA-256 必须与元数据一致；文件缺失 404，越界/非普通文件/尺寸或 hash 不符 409，
30:  * 绝不静默忽略后继续发信。
31:  *
32:  * 本服务不写 `mail_attachment` / `expert_document`，不调用 SMTP、不建排期、不变更
33:  * 专家状态，也不占用任何发送 attempt（I-1）。
34:  */
35: @Service
36: class OutboundAttachmentService(
37:     private val properties: MailAttachmentStorageProperties,
38:     private val repository: OutboundMailAttachmentRepository,
39:     private val expertContactRepository: ExpertContactRepository
40: ) {
41: 
42:     /**
43:      * 上传一个原件（每次一个文件，multipart 单字段）。
44:      *
45:      * [authenticatedUsername] 只能是会话身份；[stream] 由调用方负责关闭（controller
46:      * 用 use 包裹 multipart 的输入流）。超过 [MAX_FILE_BYTES] 立即 413 并删除本次
47:      * 临时文件，不落元数据、不留半成品。
48:      */
49:     fun upload(
50:         contactId: Long,
51:         authenticatedUsername: String,
52:         originalFilename: String?,
53:         declaredContentType: String?,
54:         stream: InputStream
55:     ): OutboundAttachmentUploadResponse {
56:         val operator = normalizeOperator(authenticatedUsername)
57:         if (!expertContactRepository.existsById(contactId)) {
58:             throw OutboundAttachmentException.notFound("专家不存在")
59:         }
60:         val fileName = normalizeOutboundFileName(originalFilename)
61:         val contentType = normalizeOutboundContentType(declaredContentType)
62: 
63:         val root = outboundRoot()
64:         Files.createDirectories(root)
65:         val id = UUID.randomUUID().toString()
66:         val finalPath = root.resolve(id)
67:         val tempPath = root.resolve("$TEMP_FILE_PREFIX$id")
68: 
69:         val digest = MessageDigest.getInstance("SHA-256")
70:         var byteLength = 0L
71:         try {
72:             Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
73:                 val buffer = ByteArray(STREAM_BUFFER_BYTES)
74:                 while (true) {
75:                     val read = stream.read(buffer)
76:                     if (read < 0) {
77:                         break
78:                     }
79:                     if (read == 0) {
80:                         continue
81:                     }
82:                     byteLength += read
83:                     if (byteLength > MAX_FILE_BYTES) {
84:                         throw OutboundAttachmentException.payloadTooLarge(
85:                             "单个附件不能超过 $MAX_FILE_BYTES 字节"
86:                         )
87:                     }
88:                     digest.update(buffer, 0, read)
89:                     output.write(buffer, 0, read)
90:                 }
91:             }
92:             // 完整校验通过后才进入最终 UUID 路径；同一目录内保证原子性。
93:             Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
94:         } finally {
95:             Files.deleteIfExists(tempPath)
96:         }
97: 
98:         val attachment = OutboundMailAttachment(
99:             id = id,
100:             expertContactId = contactId,
101:             createdBy = operator,
102:             fileName = fileName,
103:             contentType = contentType,
104:             byteLength = byteLength,
105:             sha256 = digest.digest().joinToString("") { "%02x".format(it) },
106:             createdAt = LocalDateTime.now()
107:         )
108:         try {
109:             repository.insert(attachment)
110:         } catch (ex: Exception) {
111:             // 写库失败不得留下「可用附件」：删掉本次刚落地的文件再上抛。
112:             Files.deleteIfExists(finalPath)
113:             throw ex
114:         }
115:         return OutboundAttachmentUploadResponse(
116:             id = attachment.id,
117:             filename = attachment.fileName,
118:             contentType = attachment.contentType,
119:             byteLength = attachment.byteLength,
120:             sha256 = attachment.sha256,
121:             downloadUrl = draftDownloadUrl(contactId, attachment.id)
122:         )
123:     }
124: 
125:     /**
126:      * 草稿下载（I-2）：同专家 + 同上传用户；不匹配一律 404，不泄露附件是否存在。
127:      */
128:     fun resolveDraftDownload(
129:         contactId: Long,
130:         attachmentId: String,
131:         authenticatedUsername: String
132:     ): OutboundMailFile {
133:         val operator = normalizeOperator(authenticatedUsername)
134:         val attachment = loadOwned(contactId, attachmentId)
135:         if (attachment.createdBy != operator) {
136:             throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
137:         }
138:         return readVerifiedFile(attachment)
139:     }
140: 
141:     /**
142:      * 首次发送解析（I-2/I-3）：按用户选择顺序返回不可变文件集合与有序快照。
143:      *
144:      * 校验顺序：个数上限（400）→ 重复 id（400）→ 归属（同专家 + 同上传者，404）→
145:      * 总字节上限（413）→ 逐个原件尺寸/hash（404/409）。[attachmentIds] 为空表示
146:      * 没有通用附件，返回空集合（05/06 无需特判）。
147:      */
148:     fun resolveForSend(
149:         contactId: Long,
150:         attachmentIds: List<String>,
151:         authenticatedUsername: String
152:     ): OutboundAttachmentFileSet {
153:         if (attachmentIds.isEmpty()) {
154:             return OutboundAttachmentFileSet(emptyList(), emptyList())
155:         }
156:         val operator = normalizeOperator(authenticatedUsername)
157:         val attachments = loadOrdered(contactId, attachmentIds)
158:         attachments.firstOrNull { it.createdBy != operator }?.let {
159:             throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
160:         }
161:         val files = attachments.map { readVerifiedFile(it) }
162:         return OutboundAttachmentFileSet(files, files.map { it.snapshot })
163:     }
164: 
165:     /**
166:      * 元数据读取（06 的已发送比较/展示用）：只读元数据，**不读原件、不要求原件仍在线**；
167:      * 归属仍要求同专家 + 同上传者，边界与 [resolveForSend] 完全一致。
168:      */
169:     fun loadSnapshots(
170:         contactId: Long,
171:         attachmentIds: List<String>,
172:         authenticatedUsername: String
173:     ): List<OutboundAttachmentSnapshot> {
174:         if (attachmentIds.isEmpty()) {
175:             return emptyList()
176:         }
177:         val operator = normalizeOperator(authenticatedUsername)
178:         val attachments = loadOrdered(contactId, attachmentIds)
179:         attachments.firstOrNull { it.createdBy != operator }?.let {
180:             throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
181:         }
182:         return attachments.map { snapshotOf(it) }
183:     }
184: 
185:     /**
186:      * 已发送原件读取（06 的已发消息下载）：只按专家范围取行——「该 id 是否在这封已发
187:      * 消息的快照成员里」由调用方先判，因此这里不做上传者校验（跨操作员可下载已发件）。
188:      */
189:     fun resolveForMessageDownload(contactId: Long, attachmentId: String): OutboundMailFile =
190:         readVerifiedFile(loadOwned(contactId, attachmentId))
191: 
192:     // ------------------------------------------------------------------
193:     // 内部
194:     // ------------------------------------------------------------------
195: 
196:     private fun loadOwned(contactId: Long, attachmentId: String): OutboundMailAttachment =
197:         repository.findAllByExpertContactIdAndIdIn(contactId, listOf(attachmentId)).singleOrNull()
198:             ?: throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
199: 
200:     /** 批量取元数据并恢复请求顺序，同时执行数量/总量边界（I-3）。 */
201:     private fun loadOrdered(contactId: Long, attachmentIds: List<String>): List<OutboundMailAttachment> {
202:         if (attachmentIds.size > MAX_FILES) {
203:             throw OutboundAttachmentException.badRequest("最多 $MAX_FILES 个通用附件")
204:         }
205:         if (attachmentIds.distinct().size != attachmentIds.size) {
206:             throw OutboundAttachmentException.badRequest("通用附件 id 不能重复")
207:         }
208:         val byId = repository.findAllByExpertContactIdAndIdIn(contactId, attachmentIds).associateBy { it.id }
209:         val ordered = attachmentIds.map { byId[it] ?: throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE) }
210:         if (ordered.sumOf { it.byteLength } > MAX_TOTAL_BYTES) {
211:             throw OutboundAttachmentException.payloadTooLarge("通用附件总计不能超过 $MAX_TOTAL_BYTES 字节")
212:         }
213:         return ordered
214:     }
215: 
216:     /** 按元数据重算快照并校验（含文件名/类型/摘要形态），损坏即 409。 */
217:     private fun snapshotOf(attachment: OutboundMailAttachment): OutboundAttachmentSnapshot {
218:         val snapshot = OutboundAttachmentSnapshot(
219:             schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
220:             id = attachment.id,
221:             filename = attachment.fileName,
222:             contentType = attachment.contentType,
223:             byteLength = attachment.byteLength,
224:             sha256 = attachment.sha256
225:         )
226:         OutboundAttachmentSnapshotCodec.validateSnapshot(snapshot)
227:         return snapshot
228:     }
229: 
230:     /** 读原件：realpath 越界/非普通文件/尺寸或 hash 不符都不放行（I-4）。 */
231:     private fun readVerifiedFile(attachment: OutboundMailAttachment): OutboundMailFile {
232:         val snapshot = snapshotOf(attachment)
233:         val root = outboundRoot()
234:         val candidate = root.resolve(snapshot.id)
235:         if (!Files.exists(candidate)) {
236:             throw OutboundAttachmentException.notFound("附件原件缺失（id=${snapshot.id}）")
237:         }
238:         if (!Files.isRegularFile(candidate)) {
239:             throw OutboundAttachmentException.conflict("附件原件不是常规文件（id=${snapshot.id}）")
240:         }
241:         val realFile = candidate.toRealPath()
242:         if (!realFile.startsWith(root.toRealPath())) {
243:             throw OutboundAttachmentException.conflict("附件原件路径越界（id=${snapshot.id}）")
244:         }
245:         val bytes = Files.readAllBytes(realFile)
246:         if (bytes.size.toLong() != snapshot.byteLength) {
247:             throw OutboundAttachmentException.conflict("附件原件尺寸与元数据不一致（id=${snapshot.id}）")
248:         }
249:         if (outboundSha256Hex(bytes) != snapshot.sha256) {
250:             throw OutboundAttachmentException.conflict("附件原件摘要与元数据不一致（id=${snapshot.id}）")
251:         }
252:         return OutboundMailFile(snapshot, bytes)
253:     }
254: 
255:     /** 上传者身份：空身份拒绝写入（不伪造），超列宽报配置错误（绝不截断成另一个用户）。 */
256:     private fun normalizeOperator(authenticatedUsername: String?): String {
257:         val operator = authenticatedUsername?.trim().orEmpty()
258:         if (operator.isEmpty()) {
259:             throw OutboundAttachmentException.badRequest("上传身份缺失")
260:         }
261:         if (operator.length > MAX_CREATED_BY_CHARS) {
262:             throw IllegalStateException(
263:                 "登录用户名超过 created_by 列宽（$MAX_CREATED_BY_CHARS 字符），拒绝写入"
264:             )
265:         }
266:         return operator
267:     }
268: 
269:     private fun outboundRoot(): Path =
270:         Path.of(properties.basePath).toAbsolutePath().normalize().resolve(OUTBOUND_DIR)
271: 
272:     /** 草稿下载地址：context 相对，只含专家 id 与附件 id（不含任何服务器路径）。 */
273:     private fun draftDownloadUrl(contactId: Long, attachmentId: String): String =
274:         "/api/mail/conversations/$contactId/outbound-attachments/$attachmentId/download"
275: 
276:     private companion object {
277:         const val OUTBOUND_DIR = "outbound"
278:         const val TEMP_FILE_PREFIX = ".tmp-"
279:         const val STREAM_BUFFER_BYTES = 64 * 1024
280:         const val NOT_AVAILABLE_MESSAGE = "附件不存在或不属于当前会话"
281:     }
282: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
4: import com.weibo.talentintroduction.mail.repository.MailReviewDraftRepository
5: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
6: import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
7: import org.jsoup.Jsoup
8: import org.jsoup.nodes.Document
9: import org.jsoup.safety.Safelist
10: import org.springframework.dao.DuplicateKeyException
11: import org.springframework.stereotype.Service
12: import org.springframework.transaction.annotation.Isolation
13: import org.springframework.transaction.annotation.Transactional
14: import java.time.LocalDateTime
15: import java.time.temporal.ChronoUnit
16: import java.util.UUID
17: 
18: /** Approval freezes a canonical snapshot; this service has no delivery dependency. */
19: @Service
20: class MailReviewDraftService(
21:     private val repository: MailReviewDraftRepository,
22:     private val conversations: MailboxConversationRepository,
23:     private val senderAccounts: MailSenderAccountRepository,
24:     private val content: MailContentService,
25:     private val placeholders: MailPlaceholderService,
26:     private val translation: MailTranslationService
27: ) {
28:     @Transactional
29:     fun create(actor: String?, contactId: Long, clientRequestId: String, subject: String, html: String): Any {
30:         val username = requireReader(actor)
31:         grant(username, contactId, true)
32:         val requestId = try { UUID.fromString(clientRequestId).toString() } catch (e: IllegalArgumentException) { invalid() }
33:         if (requestId != clientRequestId) invalid()
34:         val canonical = canonical("", html)
35:         repository.findByRequest(username, requestId)?.let { previous ->
36:             if (previous.contactId != contactId) conflictVersion()
37:             val initial = repository.creationEvent(previous.id) ?: error("Missing review creation snapshot")
38:             if (initial.payload["html"] != canonical.html || initial.payload["text"] != canonical.text) conflictVersion()
39:             return response(previous, username)
40:         }
41:         val target = repository.resolveTarget(contactId)
42:             ?: throw MailReviewDraftException(422, "REVIEW_TARGET_NOT_FOUND", "No real reply target exists")
43:         val now = now()
44:         val row = try {
45:             repository.insert(MailReviewDraftRow(ownerUsername = username, contactId = contactId,
46:                 clientRequestId = requestId, targetKind = target.targetKind, targetId = target.targetId,
47:                 accountCode = target.accountCode, state = MailReviewDraftState.DRAFT,
48:                 subject = repository.targetSubject(contactId, target.targetKind, target.targetId, target.accountCode), html = canonical.html, text = canonical.text, createdAt = now, updatedAt = now))
49:         } catch (e: DuplicateKeyException) { conflictVersion() }
50:         snapshot(row, "CREATE", username)
51:         return response(row, username)
52:     }
53: 
54:     @Transactional(readOnly = true)
55:     fun detail(actor: String?, id: Long): Any {
56:         val username = requireActor(actor)
57:         return response(access(username, id, false), username)
58:     }
59: 
60:     @Transactional(readOnly = true)
61:     fun list(actor: String?, contactId: Long?, page: Int = 0, size: Int = 20, includeDeleted: Boolean = false): MailReviewDraftPage {
62:         val username = requireActor(actor)
63:         if (page < 0 || size !in 1..50) invalid()
64:         if (contactId != null && username == MailReadOnlyPolicy.READER_USERNAME) grant(username, contactId, false)
65:         return MailReviewDraftPage(repository.list(username, contactId, page, size, includeDeleted).map { response(it, username) },
66:             repository.count(username, contactId, includeDeleted), page, size)
67:     }
68: 
69:     @Transactional
70:     fun save(actor: String?, id: Long, expectedVersion: Long, subject: String, html: String): Any {
71:         val username = requireReader(actor)
72:         val row = writable(username, id, expectedVersion)
73:         state(row, MailReviewDraftState.DRAFT, MailReviewDraftState.RETURNED)
74:         val value = canonical(replySubject(row), html)
75:         if (value.subject == row.subject && value.html == row.html && value.text == row.text) return response(row, username)
76:         return response(change(row, row.copy(subject = value.subject, html = value.html, text = value.text), "SAVE", username), username)
77:     }
78: 
79:     @Transactional
80:     fun submit(actor: String?, id: Long, expectedVersion: Long): Any {
81:         val username = requireReader(actor)
82:         val row = writable(username, id, expectedVersion)
83:         state(row, MailReviewDraftState.DRAFT, MailReviewDraftState.RETURNED)
84:         val submitted = row.copy(subject = replySubject(row))
85:         validateSubmission(submitted)
86:         return response(change(row, submitted.copy(state = MailReviewDraftState.SUBMITTED, submittedAt = row.submittedAt ?: now(), returnReason = null), "SUBMIT", username), username)
87:     }
88: 
89:     @Transactional
90:     fun withdraw(actor: String?, id: Long, expectedVersion: Long): Any {
91:         val username = requireReader(actor)
92:         val row = writable(username, id, expectedVersion)
93:         state(row, MailReviewDraftState.SUBMITTED)
94:         return response(change(row, row.copy(state = MailReviewDraftState.DRAFT), "WITHDRAW", username), username)
95:     }
96: 
97:     @Transactional
98:     fun softDelete(actor: String?, id: Long, expectedVersion: Long): Any {
99:         val username = requireReader(actor)
100:         val row = access(username, id, true)
101:         if (row.deletedAt != null) {
102:             // Only the original delete request is idempotent, not arbitrary stale writes.
103:             if (expectedVersion < 0 || expectedVersion != row.version - 1) conflictVersion()
104:             return MailReviewDraftDeleted(row.id)
105:         }
106:         version(row, expectedVersion)
107:         if (row.state == MailReviewDraftState.APPROVED) conflictState()
108:         change(row, row.copy(deletedAt = now()), "DELETE", username)
109:         return MailReviewDraftDeleted(row.id)
110:     }
111: 
112:     // The access read precedes the draft lock; context must see commits made while waiting for that lock.
113:     @Transactional(isolation = Isolation.READ_COMMITTED)
114:     fun approve(actor: String?, id: Long, expectedVersion: Long, observedContextToken: String): Any {
115:         val username = requireAdmin(actor)
116:         val row = writable(username, id, expectedVersion)
117:         state(row, MailReviewDraftState.SUBMITTED)
118:         val token = matchingContext(row, observedContextToken)
119:         return response(change(row, row.copy(state = MailReviewDraftState.APPROVED,
120:             reviewedBy = username, reviewedAt = now(), approvedContextToken = token,
121:             sendRequestId = UUID.randomUUID().toString()), "APPROVE", username), username)
122:     }
123: 
124:     @Transactional
125:     fun returnDraft(actor: String?, id: Long, expectedVersion: Long, reason: String): Any {
126:         val username = requireAdmin(actor)
127:         val row = writable(username, id, expectedVersion)
128:         state(row, MailReviewDraftState.SUBMITTED)
129:         val value = shortText(reason)
130:         return response(change(row, row.copy(state = MailReviewDraftState.RETURNED, returnReason = value), "RETURN", username, value), username)
131:     }
132: 
133:     @Transactional
134:     fun comment(actor: String?, id: Long, expectedVersion: Long, text: String): MailReviewDraftEvent {
135:         val username = requireActor(actor)
136:         val row = writable(username, id, expectedVersion)
137:         if (username == MailReadOnlyPolicy.READER_USERNAME && row.state == MailReviewDraftState.APPROVED) conflictState()
138:         return repository.appendEvent(row.id, row.version, "COMMENT", username, mapOf("text" to shortText(text)), now())
139:     }
140: 
141:     @Transactional(readOnly = true)
142:     fun events(actor: String?, id: Long, beforeId: Long? = null, limit: Int = 20): List<MailReviewDraftEvent> {
143:         val username = requireActor(actor)
144:         access(username, id, false)
145:         if (limit !in 1..100 || (beforeId != null && beforeId <= 0)) invalid()
146:         return repository.events(id, beforeId, limit)
147:     }
148: 
149:     @Transactional(readOnly = true)
150:     fun context(actor: String?, id: Long): MailReviewDraftContext {
151:         val username = requireAdmin(actor)
152:         val row = access(username, id, false)
153:         val token = contextToken(row.contactId)
154:         return MailReviewDraftContext(token, row.approvedContextToken, token != row.approvedContextToken, now())
155:     }
156: 
157:     @Transactional(isolation = Isolation.READ_COMMITTED)
158:     fun confirmContext(actor: String?, id: Long, expectedVersion: Long, observedContextToken: String): MailReviewDraftConfirmation {
159:         val username = requireAdmin(actor)
160:         val row = writable(username, id, expectedVersion)
161:         state(row, MailReviewDraftState.APPROVED)
162:         val token = matchingContext(row, observedContextToken)
163:         val event = repository.appendEvent(row.id, row.version, "CONTEXT_CONFIRMED", username, mapOf("contextToken" to token), now())
164:         return MailReviewDraftConfirmation(event.id, token)
165:     }
166: 
167:     @Transactional(readOnly = true)
168:     fun translate(actor: String?, id: Long, expectedVersion: Long): Map<String, Any?> {
169:         val username = requireAdmin(actor)
170:         val row = access(username, id, false)
171:         if (row.deletedAt != null) conflictState()
172:         version(row, expectedVersion)
173:         val result = translation.translate(row.text)
174:         return mapOf("ok" to result.ok, "translatedText" to result.text, "reason" to result.reason)
175:     }
176: 
177:     @Transactional(readOnly = true)
178:     fun prepareSend(actor: String?, id: Long, request: MailReviewSendRequest): MailReviewSendRef {
179:         val username = requireAdmin(actor)
180:         val row = repository.find(id)?.takeIf { it.ownerUsername == MailReadOnlyPolicy.READER_USERNAME } ?: notFound()
181:         validateSendRow(row, request.expectedVersion)
182:         return MailReviewSendRef(row, username, request.confirmationEventId)
183:     }
184: 
185:     /** Called within the attempt transaction; review row is always locked before attempt. */
186:     fun lockForSend(ref: MailReviewSendRef): MailReviewDraftRow {
187:         requireAdmin(ref.actor)
188:         val row = access(ref.actor, ref.draft.id, true)
189:         validateSendRow(row, ref.draft.version)
190:         if (row.copy(sendAttemptId = null) != ref.draft.copy(sendAttemptId = null))
191:             throw MailReviewDraftException(409, "REVIEW_CONTENT_CHANGED", "Approved content changed")
192:         return row
193:     }
194: 
195:     /** Delivery has already happened: concurrent grant/target changes cannot discard its result. */
196:     fun lockForSendResult(ref: MailReviewSendRef): MailReviewDraftRow {
197:         val row = repository.find(ref.draft.id, true) ?: notFound()
198:         version(row, ref.draft.version)
199:         state(row, MailReviewDraftState.APPROVED)
200:         check(row.subject == ref.draft.subject && row.html == ref.draft.html && row.text == ref.draft.text &&
201:             row.contactId == ref.draft.contactId && row.targetId == ref.draft.targetId &&
202:             row.targetKind == ref.draft.targetKind && row.accountCode == ref.draft.accountCode &&
203:             row.sendRequestId == ref.draft.sendRequestId) { "Approved result identity changed" }
204:         return row
205:     }
206: 
207:     private fun validateSendRow(row: MailReviewDraftRow, expectedVersion: Long) {
208:         if (row.deletedAt != null) conflictState()
209:         version(row, expectedVersion)
210:         state(row, MailReviewDraftState.APPROVED)
211:         grant(row.ownerUsername, row.contactId, false)
212:         if (row.sendRequestId == null || !repository.validTarget(row))
213:             throw MailReviewDraftException(422, "REVIEW_TARGET_NOT_FOUND", "The original reply target is no longer valid")
214:     }
215: 
216:     fun validateSendContext(row: MailReviewDraftRow, ref: MailReviewSendRef) {
217:         val token = contextToken(row.contactId)
218:         if (token == row.approvedContextToken) return
219:         val event = ref.confirmationEventId?.let { repository.event(it) }
220:         if (event == null || event.draftId != row.id || event.draftVersion != row.version ||
221:             event.eventType != "CONTEXT_CONFIRMED" || event.actorUsername != ref.actor ||
222:             event.payload["contextToken"] != token)
223:             throw MailReviewDraftException(409, "REVIEW_CONTEXT_CHANGED", "Review and confirm the latest conversation before sending")
224:     }
225: 
226:     fun bindSend(row: MailReviewDraftRow, attemptId: Long, result: String) {
227:         repository.bindAttempt(row, attemptId)
228:         repository.appendEvent(row.id, row.version, "SEND_CLAIMED", "admin",
229:             mapOf("attemptId" to attemptId, "result" to result), now())
230:     }
231: 
232:     fun recordSendResult(row: MailReviewDraftRow, attemptId: Long, result: String, mailRecordId: Long?) {
233:         check(row.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
234:         repository.appendSendResult(row, attemptId, result, mailRecordId, now())
235:     }
236: 
237:     private fun access(username: String, id: Long, lock: Boolean): MailReviewDraftRow {
238:         val observed = repository.find(id) ?: notFound()
239:         if (username == MailReadOnlyPolicy.READER_USERNAME) {
240:             if (observed.ownerUsername != username) notFound()
241:             // Grant always precedes draft lock: revoke and successful writes have an ordered outcome.
242:             grant(username, observed.contactId, lock)
243:         } else if (observed.ownerUsername != MailReadOnlyPolicy.READER_USERNAME || observed.submittedAt == null) notFound()
244:         val row = if (lock) repository.find(id, true) ?: notFound() else observed
245:         return row
246:     }
247: 
248:     private fun writable(username: String, id: Long, expectedVersion: Long): MailReviewDraftRow {
249:         val row = access(username, id, true)
250:         if (row.deletedAt != null) conflictState()
251:         version(row, expectedVersion)
252:         return row
253:     }
254: 
255:     private fun grant(username: String, contactId: Long, lock: Boolean) {
256:         if (!(if (lock) repository.lockAccess(username, contactId) else conversations.hasReadAccess(username, contactId)))
257:             throw MailReviewDraftException(403, "MAIL_READ_FORBIDDEN", "Mail read access is required")
258:     }
259: 
260:     private fun change(old: MailReviewDraftRow, value: MailReviewDraftRow, event: String, username: String, reason: String? = null): MailReviewDraftRow {
261:         if (old.version == Long.MAX_VALUE) conflictVersion()
262:         val next = value.copy(version = old.version + 1, updatedAt = now())
263:         if (!repository.update(next, old.version, old.state)) conflictVersion()
264:         snapshot(next, event, username, reason)
265:         return next
266:     }
267: 
268:     private fun snapshot(row: MailReviewDraftRow, event: String, username: String, reason: String? = null) {
269:         val payload = linkedMapOf<String, Any?>("subject" to row.subject, "html" to row.html, "text" to row.text, "state" to row.state.name)
270:         if (reason != null) payload["reason"] = reason
271:         repository.appendEvent(row.id, row.version, event, username, payload, now())
272:     }
273: 
274:     private fun response(row: MailReviewDraftRow, username: String): Any {
275:         if (row.deletedAt != null) return MailReviewDraftDeleted(row.id)
276:         val admin = username == MailReadOnlyPolicy.ADMIN_USERNAME
277:         val send = repository.sendProjection(row)
278:         return MailReviewDraftDetail(id = row.id, contactId = row.contactId, expertName = repository.contactName(row.contactId),
279:             ownerUsername = row.ownerUsername, version = row.version, state = row.state, subject = if (row.subject.isNotBlank() || row.state == MailReviewDraftState.APPROVED) row.subject else replySubject(row),
280:             html = row.html, text = row.text, accountCode = row.accountCode, targetKind = row.targetKind, targetId = row.targetId,
281:             submittedAt = row.submittedAt, returnReason = row.returnReason, reviewedAt = row.reviewedAt, deleted = false,
282:             createdAt = row.createdAt, updatedAt = row.updatedAt, commentCount = repository.commentCount(row.id),
283:             currentContextToken = if (admin) contextToken(row.contactId) else null,
284:             approvedContextToken = if (admin) row.approvedContextToken else null,
285:             sendStatus = send.sendStatus, sentMailRecordId = send.sentMailRecordId)
286:     }
287: 
288:     private fun replySubject(row: MailReviewDraftRow) = repository.targetSubject(row.contactId, row.targetKind, row.targetId, row.accountCode)
289: 
290:     private data class Canonical(val subject: String, val html: String, val text: String)
291: 
292:     private fun canonical(subject: String, html: String): Canonical {
293:         if (subject.length > 255) invalid()
294:         size(html)
295:         val safelist = Safelist.none().addTags("p", "div", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "blockquote", "a")
296:             .addAttributes("a", "href").addProtocols("a", "href", "http", "https", "mailto")
297:         val cleaned = Jsoup.clean(html, "", safelist, Document.OutputSettings().prettyPrint(false))
298:         val normalized = content.normalizeManualRichHtmlLineBreaks(cleaned)
299:         size(normalized)
300:         return Canonical(subject.replace("\r\n", "\n").replace('\r', '\n').trim(), normalized,
301:             content.normalizeManualTextLineBreaks(content.htmlToPlainText(normalized)))
302:     }
303: 
304:     private fun validateSubmission(row: MailReviewDraftRow) {
305:         if (row.text.isBlank()) throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "请填写回复正文后提交审核")
306:         val texts = arrayOf(row.subject, row.html, row.text)
307:         try {
308:             texts.forEach { placeholders.requireValidTemplatePlaceholders(it) }
309:             if (placeholders.placeholderKeysIn(*texts).isNotEmpty())
310:                 throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "请填写实际内容后提交")
311:         } catch (e: IllegalArgumentException) {
312:             throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "请填写实际内容后提交")
313:         }
314:         val hrefs = HREF_EXTRACTOR.findAll(row.html).map { match ->
315:             match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { match.groupValues[3] } }
316:         }.toList()
317:         val finalText = listOfNotNull(row.subject.takeIf { it.isNotBlank() }, row.text.takeIf { it.isNotBlank() },
318:             content.htmlToPlainText(row.html).takeIf { it.isNotBlank() }, hrefs.takeIf { it.isNotEmpty() }?.joinToString(" ")).joinToString(" ")
319:         if (finalText.length > 20000) invalid()
320:     }
321: 
322:     private fun size(html: String) {
323:         if (html.toByteArray(Charsets.UTF_8).size > 1024 * 1024)
324:             throw MailReviewDraftException(413, "REVIEW_BODY_TOO_LARGE", "HTML exceeds 1 MiB")
325:     }
326:     private fun shortText(text: String): String {
327:         if (text.isBlank() || text.length > 2000) invalid()
328:         return text
329:     }
330:     private fun contextToken(contactId: Long): String = conversations.reviewContext(contactId,
331:         senderAccounts.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE).map { it.accountCode })
332:     private fun matchingContext(row: MailReviewDraftRow, observed: String): String {
333:         val current = contextToken(row.contactId)
334:         if (observed != current) throw MailReviewDraftException(409, "REVIEW_CONTEXT_CHANGED", "Review the latest conversation first")
335:         return current
336:     }
337:     private fun version(row: MailReviewDraftRow, expected: Long) { if (expected < 0 || expected != row.version) conflictVersion() }
338:     private fun state(row: MailReviewDraftRow, vararg allowed: MailReviewDraftState) { if (row.state !in allowed) conflictState() }
339:     private fun requireActor(actor: String?): String {
340:         if (actor == null) throw MailReviewDraftException(401, "UNAUTHORIZED", "Authentication required")
341:         if (actor != MailReadOnlyPolicy.ADMIN_USERNAME && actor != MailReadOnlyPolicy.READER_USERNAME)
342:             throw MailReviewDraftException(403, "READ_ONLY_FORBIDDEN", "Unknown review actor")
343:         return actor
344:     }
345:     private fun requireReader(actor: String?): String = requireActor(actor).also {
346:         if (it != MailReadOnlyPolicy.READER_USERNAME) throw MailReviewDraftException(403, "READ_ONLY_FORBIDDEN", "Only the owner may edit review drafts")
347:     }
348:     private fun requireAdmin(actor: String?): String = requireActor(actor).also {
349:         if (it != MailReadOnlyPolicy.ADMIN_USERNAME) throw MailReviewDraftException(403, "ADMIN_REQUIRED", "Administrator required")
350:     }
351:     private fun invalid(): Nothing = throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "Invalid review content")
352:     private fun notFound(): Nothing = throw MailReviewDraftException(404, "REVIEW_DRAFT_NOT_FOUND", "Review draft not found")
353:     private fun conflictVersion(): Nothing = throw MailReviewDraftException(409, "REVIEW_VERSION_CONFLICT", "Review version changed")
354:     private fun conflictState(): Nothing = throw MailReviewDraftException(409, "REVIEW_STATE_CONFLICT", "Review state does not allow this operation")
355:     private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
356: 
357:     companion object {
358:         private val HREF_EXTRACTOR = Regex("""href\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>"']+))""", RegexOption.IGNORE_CASE)
359:     }
360: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
4: import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
5: import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
6: import org.springframework.transaction.annotation.Propagation
7: import org.springframework.transaction.annotation.Transactional
8: import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
9: import com.weibo.talentintroduction.mail.repository.MailReplyDraftRepository
10: import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
11: import org.springframework.dao.DuplicateKeyException
12: import org.springframework.http.HttpStatus
13: import org.springframework.stereotype.Service
14: import java.time.LocalDateTime
15: import java.time.temporal.ChronoUnit
16: 
17: @Service
18: class MailReplyDraftService(
19:     private val repository: MailReplyDraftRepository,
20:     private val contacts: ExpertContactRepository,
21:     private val inbound: InboundMailProcessingRepository,
22:     private val accounts: MailSenderAccountRepository,
23:     private val attachments: OutboundAttachmentService,
24:     private val permissionGuard: ExpertMailPermissionGuard
25: ) {
26:     private val mailContent = MailContentService()
27:     fun getTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftDetail {
28:         identity(owner); targetShape(target)
29:         authorizeTarget(owner, target)
30:         val row = repository.findTarget(owner,target)
31:         if (row != null) return detail(row)
32:         validateTarget(target)
33:         return MailReplyDraftDetail(target=target)
34:     }
35:     fun get(owner: String, id: Long): MailReplyDraftDetail { identity(owner); return detail(authorizedOwned(owner,id)) }
36:     // Each write is one atomic CAS statement; conflict rereads must not use an old REPEATABLE READ snapshot.
37:     fun save(owner: String, target: MailReplyDraftTarget, request: MailReplyDraftSaveRequest): MailReplyDraftDetail {
38:         identity(owner); targetShape(target)
39:         authorizeTarget(owner, target)
40:         if (request.expectedVersion < 0) invalid("非法 expectedVersion")
41:         val existing = repository.findTarget(owner,target)
42:         if (existing == null) {
43:             if (request.expectedVersion != 0L || request.reopen) conflict(null)
44:         } else {
45:             if (existing.version != request.expectedVersion) conflict(existing)
46:             if (existing.state != MailReplyDraftState.ACTIVE && !request.reopen) conflict(existing, true)
47:         }
48:         validateTarget(target)
49:         val content = normalizeAttachments(owner,target,request.content,existing)
50:         val json = MailReplyDraftContextCodec.serialize(content.context)
51:         if (content.subject.codePointCount(0,content.subject.length) > 255) invalid("主题最多255字符")
52:         val bytes = sequenceOf(content.subject,content.html,content.text,json).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
53:         if (bytes > MAIL_REPLY_DRAFT_MAX_BYTES) throw MailReplyDraftException(HttpStatus.PAYLOAD_TOO_LARGE,"DRAFT_TOO_LARGE","草稿文本总量超过1MiB")
54:         val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
55:         if (existing == null) {
56:             val id = try { repository.insert(owner,target,content,json,now) } catch (e: DuplicateKeyException) { conflict(repository.findTarget(owner,target)) }
57:             return detail(owned(owner,id))
58:         }
59:         if (!repository.save(owner,existing.id,existing.version,existing.state,content,json,now)) conflict(owned(owner,existing.id))
60:         return detail(existing.copy(version=existing.version+1,state=MailReplyDraftState.ACTIVE,subject=content.subject,html=content.html,text=content.text,contextJson=json,updatedAt=now,
61:             sendAttemptId=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptId else null,
62:             sendVersion=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendVersion else null,
63:             sendAttemptStatus=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptStatus else null))
64:     }
65:     @Transactional(propagation = Propagation.REQUIRES_NEW)
66:     fun discard(owner: String, id: Long, expectedVersion: Long): MailReplyDraftDetail {
67:         identity(owner)
68:         repository.lockOwned(owner,id)
69:         val row = authorizedOwned(owner,id)
70:         if (expectedVersion <= 0 || row.version != expectedVersion) conflict(row)
71:         if (row.state != MailReplyDraftState.ACTIVE) return detail(row)
72:         if (row.sendAttemptStatus == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
73:             throw MailReplyDraftException(HttpStatus.CONFLICT,"DRAFT_SEND_IN_PROGRESS","草稿正在发送，不能放弃",row.version,row.state)
74:         }
75:         val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
76:         if (!repository.discard(owner,id,expectedVersion,now)) conflict(owned(owner,id))
77:         return detail(row.copy(version=row.version+1,state=MailReplyDraftState.DISCARDED,subject=null,html=null,text=null,contextJson=null,updatedAt=now))
78:     }
79:     fun list(owner: String, search: String?, accountScope: String?, page: Int, size: Int): MailReplyDraftList {
80:         identity(owner); filterScope(accountScope)
81:         if (page < 0 || size !in 1..100) invalid("非法草稿分页")
82:         return repository.list(owner,accountScope?.takeIf { it.isNotEmpty() },search,page,size)
83:     }
84:     fun summaries(owner: String, contactIds: List<Long>, accountScope: String?): MailReplyDraftSummaries {
85:         identity(owner); filterScope(accountScope)
86:         val ids = contactIds.distinct()
87:         if (ids.size > 100 || ids.any { it <= 0 }) invalid("contactIds最多100个真实id")
88:         return repository.summaries(owner,accountScope?.takeIf { it.isNotEmpty() },ids)
89:     }
90: 
91:     internal fun validateInboundSend(owner: String, ref: MailReplyDraftRef, target: MailReplyDraftTarget,
92:         subject: String, html: String, text: String?, ragCodes: List<String>?, ragFingerprint: String?,
93:         edited: Boolean?, freeTextPreview: String?, meeting: MeetingInput?, previewSha: String?,
94:         attachmentIds: List<String>, unsupportedQa: Boolean): ValidatedDraftSendRef {
95:         val row = activeSend(owner,ref)
96:         val content = content(row)
97:         val qa = content.context.qa
98:         val codes = qa?.ragFactCodes.orEmpty()
99:         val expectedEdited = if (codes.isEmpty()) null else
100:             text.orEmpty().trim() != mailContent.normalizeManualTextLineBreaks(qa?.baselineText.orEmpty()).trim()
101:         val savedMeeting = content.context.meeting
102:         val submittedInput = meeting?.let { MailReplyDraftMeetingInput(it.zoneId,it.startLocal,it.endLocal,it.zoomUrl,it.generatedAt) }
103:         if (row.target != target || content.context.followUpAnchorMailRecordId != null || unsupportedQa ||
104:             !sameBody(content,subject,html,text) || codes != ragCodes.orEmpty() ||
105:             qa?.ragCorpusFingerprint.orEmpty() != ragFingerprint.orEmpty() || edited != expectedEdited ||
106:             !freeTextPreview.isNullOrBlank() || savedMeeting?.input != submittedInput ||
107:             savedMeeting?.preview?.attachment?.sha256 != previewSha ||
108:             savedMeeting != null && savedMeeting.state != "ready" ||
109:             content.context.meetingAccountCode?.takeIf { it.isNotEmpty() }?.let { it != target.accountScope } == true ||
110:             !sameAttachments(content,attachmentIds)) mismatch(row)
111:         return validated(row,content)
112:     }
113: 
114:     internal fun validateConversationSend(owner: String, ref: MailReplyDraftRef, contactId: Long, scope: String?,
115:         requestId: String, anchorId: Long?, subject: String, html: String, text: String?,
116:         attachmentIds: List<String>): ValidatedDraftSendRef {
117:         val row = activeSend(owner,ref)
118:         val content = content(row)
119:         if (!conversationTarget(row,contactId,scope) || !sameBody(content,subject,html,text) ||
120:             content.context.requestId != requestId || content.context.followUpAnchorMailRecordId != anchorId ||
121:             row.target.kind == MailReplyDraftKind.INBOUND && anchorId == null ||
122:             content.context.meeting != null || !sameAttachments(content,attachmentIds)) mismatch(row)
123:         return validated(row,content)
124:     }
125: 
126:     /** Body-free terminal retries are admitted only by the durable binding, never by requestId alone. */
127:     internal fun completedBinding(owner: String, ref: MailReplyDraftRef, contactId: Long,
128:         processingId: Long?, scope: String?, attemptId: Long? = null): Long? {
129:         identity(owner)
130:         val row = authorizedOwned(owner,ref.id)
131:         val targetMatches = if (processingId != null) row.target == MailReplyDraftTarget(contactId,MailReplyDraftKind.INBOUND,processingId,scope.orEmpty())
132:             else conversationTarget(row,contactId,scope)
133:         if (!targetMatches) mismatch(row)
134:         return row.sendAttemptId?.takeIf {
135:             row.state == MailReplyDraftState.SENT && row.sendVersion == ref.version &&
136:                 row.version == ref.version + 1 && row.sendAttemptStatus == MailSendAttemptStatus.SENT &&
137:                 (attemptId == null || it == attemptId)
138:         }
139:     }
140: 
141:     /** Caller already owns the sending transaction; take the draft lock before touching attempts. */
142:     internal fun lockForClaim(ref: ValidatedDraftSendRef): MailReplyDraftRow {
143:         val row = locked(ref.owner,ref.id)
144:         authorizeTarget(ref.owner, row.target)
145:         if (row.state != MailReplyDraftState.ACTIVE || row.version != ref.version) conflict(row)
146:         if (row.target != ref.target || content(row) != ref.content) mismatch(row)
147:         return row
148:     }
149:     internal fun lockForFinalize(ref: ValidatedDraftSendRef) { locked(ref.owner,ref.id) }
150:     internal fun releaseBinding(row: MailReplyDraftRow) {
151:         check(repository.releaseCompletedBinding(row.username,row.id,requireNotNull(row.sendVersion),requireNotNull(row.sendAttemptId))) {
152:             "Draft binding release failed"
153:         }
154:     }
155:     internal fun bind(ref: ValidatedDraftSendRef, attemptId: Long) {
156:         check(repository.bindAttempt(ref.owner,ref.id,ref.version,attemptId)) { "Draft send binding failed" }
157:     }
158:     internal fun closeSent(ref: ValidatedDraftSendRef, attemptId: Long, now: LocalDateTime) {
159:         if (repository.closeSent(ref.owner,ref.id,ref.version,attemptId,now)) return
160:         val row = owned(ref.owner,ref.id)
161:         if (row.sendAttemptId != attemptId || row.sendVersion != ref.version) error("Draft send binding changed")
162:         // An edit or an explicitly discarded UNKNOWN send is not resurrected by a late success.
163:         if (row.version > ref.version) { releaseBinding(row); return }
164:         error("Draft sent close failed")
165:     }
166:     private fun activeSend(owner: String, ref: MailReplyDraftRef): MailReplyDraftRow {
167:         identity(owner)
168:         val row = authorizedOwned(owner,ref.id)
169:         if (ref.version <= 0 || row.version != ref.version || row.state != MailReplyDraftState.ACTIVE) conflict(row)
170:         return row
171:     }
172:     private fun locked(owner: String, id: Long): MailReplyDraftRow =
173:         repository.lockOwned(owner,id) ?: throw MailReplyDraftException(HttpStatus.NOT_FOUND,"NOT_FOUND","草稿不存在")
174:     private fun content(row: MailReplyDraftRow) = MailReplyDraftContent(row.subject.orEmpty(),row.html.orEmpty(),row.text.orEmpty(),
175:         MailReplyDraftContextCodec.parse(requireNotNull(row.contextJson)))
176:     private fun validated(row: MailReplyDraftRow, content: MailReplyDraftContent) =
177:         ValidatedDraftSendRef(row.username,row.id,row.version,row.target,content,row.sendAttemptId,row.sendVersion)
178:     private fun conversationTarget(row: MailReplyDraftRow, contactId: Long, scope: String?) =
179:         row.target.contactId == contactId && row.target.accountScope == scope.orEmpty()
180:     private fun sameBody(content: MailReplyDraftContent, subject: String, html: String, text: String?) =
181:         content.subject.trim() == subject.trim() && content.html == html && content.text == text.orEmpty()
182:     private fun sameAttachments(content: MailReplyDraftContent, ids: List<String>): Boolean {
183:         val items = content.context.outboundAttachmentDraft?.items.orEmpty()
184:         return items.all { it.state == "ready" } && items.mapNotNull { it.id } == ids
185:     }
186:     private fun mismatch(row: MailReplyDraftRow): Nothing = throw MailReplyDraftException(HttpStatus.CONFLICT,
187:         "DRAFT_CONTENT_MISMATCH","发送内容与已保存草稿不一致",row.version,row.state)
188:     private fun identity(owner: String) { if (owner.isBlank() || owner.length > 64) throw MailReplyDraftException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录") }
189:     private fun targetShape(target: MailReplyDraftTarget) {
190:         if (target.contactId <= 0 || target.accountScope.length > 100 || target.accountScope != target.accountScope.trim()) invalid("非法草稿目标")
191:         if (target.kind == MailReplyDraftKind.OUTBOUND && target.processingId != 0L || target.kind == MailReplyDraftKind.INBOUND && (target.processingId <= 0 || target.accountScope.isEmpty())) invalid("非法来信id/账号")
192:     }
193:     private fun validateTarget(target: MailReplyDraftTarget) {
194:         if (!contacts.existsById(target.contactId)) invalid("原专家已不存在")
195:         if (target.kind == MailReplyDraftKind.INBOUND) {
196:             val source = inbound.findById(target.processingId).orElse(null) ?: invalid("来信不存在")
197:             if (source.expertContactId != target.contactId || source.senderAccountCode != target.accountScope) invalid("来信不属于该专家/账号")
198:         } else if (target.accountScope.isNotEmpty()) {
199:             val account = accounts.findByAccountCode(target.accountScope) ?: invalid("账号不存在")
200:             if (account.accountCode == "SIMULATOR_NOOP") invalid("非法发件账号")
201:         }
202:     }
203:     private fun filterScope(scope: String?) { if (scope != null && (scope.length > 100 || scope != scope.trim())) invalid("非法账号范围") }
204:     private fun owned(owner: String, id: Long): MailReplyDraftRow = repository.findOwned(owner,id) ?: throw MailReplyDraftException(HttpStatus.NOT_FOUND,"NOT_FOUND","草稿不存在")
205:     private fun authorizeTarget(owner: String, target: MailReplyDraftTarget) {
206:         permissionGuard.requireFull(owner, target.contactId)
207:         if (MailReadOnlyPolicy.isReader(owner)) validateTarget(target)
208:     }
209:     private fun authorizedOwned(owner: String, id: Long): MailReplyDraftRow =
210:         owned(owner, id).also { authorizeTarget(owner, it.target) }
211:     private fun normalizeAttachments(owner: String, target: MailReplyDraftTarget, content: MailReplyDraftContent, existing: MailReplyDraftRow?): MailReplyDraftContent {
212:         MailReplyDraftContextCodec.validate(content.context)
213:         val field = content.context.outboundAttachmentDraft ?: return content
214:         val previous = existing?.contextJson?.let(MailReplyDraftContextCodec::parse)?.outboundAttachmentDraft?.items.orEmpty().associateBy { it.id }
215:         val items = field.items.map { item ->
216:             if (item.id == null) return@map if (item.state == "uploading") item.copy(state="failed",error="上传未完成，请重新选择文件") else item
217:             val snapshot = try { attachments.loadSnapshots(target.contactId,listOf(item.id),owner).single() } catch (e: OutboundAttachmentException) {
218:                 // A newly submitted missing/foreign id is never accepted. Previously validated missing references remain recoverable.
219:                 val old = previous[item.id]
220:                 if (e.status == HttpStatus.NOT_FOUND && old != null) return@map old.copy(key=item.key,state="failed",error="附件已不可用，请重新选择文件")
221:                 invalid("附件不存在或不属于当前用户/专家")
222:             }
223:             item.copy(filename=snapshot.filename,contentType=snapshot.contentType,byteLength=snapshot.byteLength,sha256=snapshot.sha256,
224:                 state=if (item.state == "uploading") "failed" else item.state,error=if (item.state == "uploading") "上传未完成，请重新选择文件" else item.error)
225:         }
226:         return content.copy(context=content.context.copy(outboundAttachmentDraft=field.copy(items=items)))
227:     }
228:     private fun detail(row: MailReplyDraftRow): MailReplyDraftDetail {
229:         val content = if (row.state == MailReplyDraftState.ACTIVE) MailReplyDraftContent(row.subject.orEmpty(),row.html.orEmpty(),row.text.orEmpty(),MailReplyDraftContextCodec.parse(requireNotNull(row.contextJson))) else null
230:         val downloads = linkedMapOf<String,String>()
231:         var sendable = row.contactExists
232:         val restored = content?.context?.outboundAttachmentDraft?.let { field -> field.copy(items=field.items.map { item ->
233:             if (item.state == "uploading") { sendable=false; return@map item.copy(state="failed",error="上传未完成，请重新选择文件") }
234:             if (item.id != null) {
235:                 try {
236:                     attachments.loadSnapshots(row.target.contactId,listOf(item.id),row.username).single()
237:                     if (item.state == "ready") downloads[item.id] = "/api/mail/conversations/${row.target.contactId}/outbound-attachments/${item.id}/download"
238:                 } catch (e: OutboundAttachmentException) { sendable=false; return@map item.copy(state="failed",error="附件已不可用，请重新选择文件") }
239:             }
240:             if (item.state != "ready") sendable=false
241:             item
242:         }) }
243:         return MailReplyDraftDetail(row.id,row.version,row.state,row.target,
244:             content?.let { it.copy(context=it.context.copy(outboundAttachmentDraft=restored)) },row.createdAt,row.updatedAt,row.contactExists,row.expertName,row.expertEmail,
245:             row.sendAttemptId,row.sendVersion,row.sendAttemptStatus,downloads,sendable)
246:     }
247:     private fun conflict(row: MailReplyDraftRow?, closed: Boolean = false): Nothing = throw MailReplyDraftException(HttpStatus.CONFLICT,
248:         if (closed) "DRAFT_CLOSED" else "DRAFT_VERSION_CONFLICT",if (closed) "草稿已关闭，请显式重新编辑" else "草稿版本冲突，请重新读取",row?.version,row?.state)
249:     private fun invalid(message: String): Nothing = throw MailReplyDraftException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_DRAFT",message)
250: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import org.springframework.stereotype.Service
4: 
5: /** Only frozen server content enters the existing manual send pipeline. No SMTP transaction here. */
6: @Service
7: class MailReviewSendService(
8:     private val drafts: MailReviewDraftService,
9:     private val pending: PendingMailOperationService
10: ) {
11:     fun send(actor: String?, id: Long, request: MailReviewSendRequest): PendingMailSendResult {
12:         val ref = drafts.prepareSend(actor, id, request)
13:         val row = ref.draft
14:         return if (row.targetKind == "INBOUND") {
15:             pending.sendManualRichReply(row.targetId, row.accountCode, row.subject, row.html, row.text, ref.actor,
16:                 safetyWarningConfirmed = request.safetyWarningConfirmed, strongConfirmationText = request.strongConfirmationText,
17:                 authenticatedUsername = ref.actor, reviewSendRef = ref)
18:         } else {
19:             pending.sendConversationManualRichReply(row.contactId, requireNotNull(row.sendRequestId), row.accountCode,
20:                 row.targetId, row.subject, row.html, row.text, ref.actor,
21:                 safetyWarningConfirmed = request.safetyWarningConfirmed, strongConfirmationText = request.strongConfirmationText,
22:                 authenticatedUsername = ref.actor, reviewSendRef = ref)
23:         }
24:     }
25: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.audit.domain.OperatorActionType
4: import com.weibo.talentintroduction.audit.service.OperatorActionLogService
5: import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
6: import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
7: import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
8: import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
9: import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
10: import com.weibo.talentintroduction.mail.domain.MailRecord
11: import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
12: import com.weibo.talentintroduction.mail.domain.TriggeredBy
13: import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
14: import com.weibo.talentintroduction.mail.repository.MailRecordRepository
15: import org.slf4j.LoggerFactory
16: import org.springframework.stereotype.Service
17: import org.springframework.transaction.annotation.Propagation
18: import org.springframework.transaction.annotation.Isolation
19: import org.springframework.transaction.annotation.Transactional
20: import org.springframework.transaction.support.TransactionSynchronization
21: import org.springframework.transaction.support.TransactionSynchronizationManager
22: import java.io.ByteArrayOutputStream
23: import java.security.MessageDigest
24: import java.time.LocalDateTime
25: import java.util.UUID
26: 
27: @Service
28: class ManualReplySendAttemptService(
29:     private val attemptRepository: MailSendAttemptRepository,
30:     private val mailRecordRepository: MailRecordRepository,
31:     private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
32:     private val operatorActionLogService: OperatorActionLogService,
33:     // fast-p 02 (I-2): 成功事务内创建排期的唯一协作件（01 createFromSentMail 要求
34:     // 调用方已在事务中，本方法即调用方）。
35:     private val meetingCalendarService: MeetingCalendarService,
36:     private val mailReplyDraftService: MailReplyDraftService? = null,
37:     private val mailReviewDraftService: MailReviewDraftService? = null
38: ) {
39:     companion object {
40:         private val log = LoggerFactory.getLogger(ManualReplySendAttemptService::class.java)
41:         private const val SCHEMA_VERSION = 1
42:         private const val FINGERPRINT_CONTENT_TYPE = "application/x-manual-rich-fingerprint-v1"
43:         private const val MANUAL_RICH_MAIL_TYPE_PREFIX = "MANUAL_RICH:"
44:         private const val MESSAGE_ID_TEMPLATE = "<manual-rich-%s@weibo.com>"
45:         private const val MAX_ERROR_SUMMARY_LENGTH = 500
46:         /** 通用附件指纹段域标记（fast-p 05，I-2）：独立版本段，不复用 calendar 段。 */
47:         private const val OUTBOUND_ATTACHMENTS_SEGMENT = "outbound-attachments-v1"
48:         // 无来信会话自由回信的 attempt 短键：requestId 派生、内容无关（I-4）。
49:         // mail_type 列宽 VARCHAR(50)：前缀 20 字符 + sha256 前 30 位 = 恰好 50。
50:         const val CONVERSATION_REQUEST_PREFIX = "MANUAL_RICH_REQUEST:"
51:         const val CONVERSATION_REQUEST_HEX_LENGTH = 30
52:         /** 会话回信线程/审计锚点类型前缀（真实 mail_record，绝不伪造 inbound id）。 */
53:         const val SOURCE_ANCHOR_PREFIX = "MAIL_RECORD:"
54:     }
55: 
56:     data class SendPayload(
57:         val orcidId: String,
58:         val contactId: Long,
59:         /** 来信路径 = 真实 inbound_mail_processing.id；会话回信路径 = null（I-3）。 */
60:         val inboundProcessingId: Long?,
61:         val accountCode: String,
62:         val normalizedRecipient: String,
63:         val subject: String,
64:         val finalText: String,
65:         val finalHtml: String,
66:         val inReplyTo: String?,
67:         val canonicalQaRuleIds: List<Long>,
68:         val primaryRuleId: Long?,
69:         /**
70:          * 会话回信路径的真实线程锚点（"MAIL_RECORD:<mailRecord.id>"，I-3）。
71:          * 来信路径必须为 null；与 inboundProcessingId 恰有一个非空。
72:          */
73:         val sourceAnchor: String? = null,
74:         /** 会话回信幂等 requestId（可被 UUID.fromString 解析，I-4）；来信路径恒 null。 */
75:         val idempotencyRequestId: String? = null,
76:         /** fast-p 02 (I-2/I-3)：会议日历附件快照；null=无日历（原发送身份字节流
77:          *  完全不变）。非 null 时把语义指纹并入发送身份：同配置不重复发，
78:          *  改时间/链接等语义获得不同发送身份。 */
79:         val calendarAttachment: CalendarAttachmentSnapshot? = null,
80:         /**
81:          * fast-p 02 (I-1/I-2)：与 calendarAttachment 同一次校验产物派生的结构化排期
82:          * 输入（只含已校验 startUtc/endUtc/zoomUrl，不接收浏览器排期对象）。
83:          * 默认 null：普通发送与无会议回信的身份、行为逐字不变；非 null 时
84:          * finalizeSuccess 在成功事务内创建排期，二者同有同无。
85:          * 不进入指纹（时间/链接语义已由 calendarAttachment.semanticSha256 覆盖）。
86:          */
87:         val meetingEvent: MeetingCalendarInput? = null,
88:         /**
89:          * fast-p 05 (I-1/I-2)：本次发送的有序通用附件快照（04
90:          * `OutboundAttachmentService.resolveForSend` 产物，选取顺序；不含上传 UUID/
91:          * 磁盘路径/上传时间）。默认空：不进入指纹、不写新列，普通发送身份与行为逐字
92:          * 不变；非空时追加 outbound-attachments-v1 段，并在两个 finalize 的四分支
93:          * 显式写入本次快照（空即显式 null）。
94:          */
95:         val outboundAttachments: List<OutboundAttachmentSnapshot> = emptyList()
96:     ) {
97:         // Server-only raw snapshot, deliberately absent from the fingerprint byte stream.
98:         internal var draftSendRef: ValidatedDraftSendRef? = null
99:         internal var reviewSendRef: MailReviewSendRef? = null
100:     }
101: 
102:     /** findCompletedByRequestId 命中的已完成会话回信（attempt SENT + 唯一 mail_record）。 */
103:     data class CompletedOutboundReply(
104:         val attemptId: Long,
105:         val attemptMessageId: String,
106:         val attemptAccountCode: String,
107:         val mailRecord: MailRecord
108:     )
109: 
110:     data class Fingerprint(
111:         val fullHex: String,
112:         val shortKey: String,
113:         val messageId: String
114:     )
115: 
116:     enum class ClaimResult {
117:         CLAIMED,
118:         DEDUP_SENT,
119:         SAFE_RETRY_CLAIMED,
120:         IN_PROGRESS,
121:         UNKNOWN,
122:         PERMANENT_FAILED,
123:     }
124: 
125:     data class ClaimedAttempt(
126:         val attemptId: Long,
127:         val messageId: String,
128:         val result: ClaimResult
129:     )
130: 
131:     fun computeFingerprint(payload: SendPayload): Fingerprint {
132:         val messageId = String.format(MESSAGE_ID_TEMPLATE, UUID.randomUUID().toString().replace("-", ""))
133:         // I-5: 来信路径未带新字段时，原首段仍是原始数字 inboundProcessingId.toString()，
134:         // 后续字段与顺序逐字不变，部署前后同一来信/正文产生同一 fullHex/shortKey。
135:         // I-3/I-4: 会话回信路径首段写真实 sourceAnchor（"MAIL_RECORD:<id>"），
136:         // 短键改由 requestId 派生；两路径指纹身份互不混淆。
137:         val inboundId = payload.inboundProcessingId
138:         val firstSegment = when {
139:             inboundId != null -> {
140:                 require(payload.sourceAnchor == null) {
141:                     "Mail send attempt fingerprint conflict: sourceAnchor must be null when inboundProcessingId is set"
142:                 }
143:                 require(payload.idempotencyRequestId == null) {
144:                     "Mail send attempt fingerprint conflict: idempotencyRequestId must be null when inboundProcessingId is set"
145:                 }
146:                 inboundId.toString()
147:             }
148:             else -> {
149:                 val sourceAnchor = requireNotNull(payload.sourceAnchor) {
150:                     "Mail send attempt requires sourceAnchor when no inbound processing id"
151:                 }
152:                 val requestId = requireNotNull(payload.idempotencyRequestId) {
153:                     "Mail send attempt requires idempotencyRequestId when no inbound processing id"
154:                 }
155:                 // 防御性双检：入口已用 UUID.fromString(...).toString() 规范化，此处仍须可解析
156:                 UUID.fromString(requestId.trim())
157:                 sourceAnchor
158:             }
159:         }
160:         val data = ByteArrayOutputStream()
161:         appendLengthPrefix(data, SCHEMA_VERSION.toString())
162:         appendLengthPrefix(data, firstSegment)
163:         appendLengthPrefix(data, payload.contactId.toString())
164:         appendLengthPrefix(data, payload.orcidId)
165:         appendLengthPrefix(data, payload.accountCode)
166:         appendLengthPrefix(data, payload.normalizedRecipient)
167:         appendLengthPrefix(data, payload.subject)
168:         appendLengthPrefix(data, payload.finalText)
169:         appendLengthPrefix(data, payload.finalHtml)
170:         appendLengthPrefix(data, payload.inReplyTo ?: "")
171:         appendLengthPrefix(data, payload.canonicalQaRuleIds.joinToString(","))
172:         // fast-p 02 (I-2)：calendar 只在非 null 时于全部原字段之后追加两段
173:         // （域标记 + 01 生成器语义 sha256）；null 时以上 11 段字节流与旧实现
174:         // 逐字一致，shortKey 仍为 MANUAL_RICH:+32。
175:         payload.calendarAttachment?.let { calendar ->
176:             appendLengthPrefix(data, "meeting-calendar-v1")
177:             appendLengthPrefix(data, calendar.semanticSha256)
178:         }
179:         // fast-p 05 (I-2)：通用附件只在非空时于既有全部段之后追加独立版本段
180:         // （域标记 + 数量 + 逐项 filename/contentType/byteLength/sha256，全部长度前缀
181:         // 编码，不用带分隔符的字符串拼接）。上传 UUID/磁盘路径/上传时间不进入
182:         // 身份：同字节同文件名重传（新上传 id）得到同一 fullHex/shortKey。
183:         // 空列表不追加任何字节，无附件与仅 ICS 的既有 hash 逐字不变。
184:         if (payload.outboundAttachments.isNotEmpty()) {
185:             appendLengthPrefix(data, OUTBOUND_ATTACHMENTS_SEGMENT)
186:             appendLengthPrefix(data, payload.outboundAttachments.size.toString())
187:             payload.outboundAttachments.forEach { attachment ->
188:                 appendLengthPrefix(data, attachment.filename)
189:                 appendLengthPrefix(data, attachment.contentType)
190:                 appendLengthPrefix(data, attachment.byteLength.toString())
191:                 appendLengthPrefix(data, attachment.sha256)
192:             }
193:         }
194:         payload.reviewSendRef?.let {
195:             appendLengthPrefix(data, "review-draft-v1")
196:             appendLengthPrefix(data, it.draft.id.toString())
197:             appendLengthPrefix(data, it.draft.version.toString())
198:         }
199:         val fullHex = sha256Hex(data.toByteArray())
200:         val shortKey = if (inboundId != null) {
201:             MANUAL_RICH_MAIL_TYPE_PREFIX + fullHex.take(32)
202:         } else {
203:             conversationRequestShortKey(requireNotNull(payload.idempotencyRequestId))
204:         }
205:         return Fingerprint(
206:             fullHex = fullHex,
207:             shortKey = shortKey,
208:             messageId = messageId
209:         )
210:     }
211: 
212:     /** requestId 规范化：trim 后必须可被 UUID.fromString 解析，输出 canonical 小写形式。 */
213:     fun canonicalConversationRequestId(value: String): String =
214:         UUID.fromString(value.trim()).toString()
215: 
216:     /**
217:      * 会话回信 attempt 短键：MANUAL_RICH_REQUEST: + sha256(canonicalRequestId UTF-8) 前
218:      * CONVERSATION_REQUEST_HEX_LENGTH 位。内容无关，同一 requestId 恒定（I-4）。
219:      */
220:     fun conversationRequestShortKey(requestId: String): String {
221:         val canonical = canonicalConversationRequestId(requestId)
222:         return CONVERSATION_REQUEST_PREFIX +
223:             sha256Hex(canonical.toByteArray(Charsets.UTF_8)).take(CONVERSATION_REQUEST_HEX_LENGTH)
224:     }
225: 
226:     /**
227:      * 会话回信幂等收敛（I-4）：按 requestId 短键读取已完成 attempt；仅当 attempt 为 SENT
228:      * 且其唯一 mail_record 存在时返回。其余状态（IN_PROGRESS/UNKNOWN/FAILED…）返回空，
229:      * 由调用方继续既有 claim/碰撞/fail-closed 逻辑（I-10）。
230:      */
231:     fun findCompletedByRequestId(orcidId: String, requestId: String): CompletedOutboundReply? {
232:         val shortKey = conversationRequestShortKey(requestId)
233:         val attempt = attemptRepository.findByOrcidIdAndMailType(orcidId, shortKey) ?: return null
234:         if (attempt.status != MailSendAttemptStatus.SENT) return null
235:         val attemptId = attempt.id ?: return null
236:         val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
237:         return CompletedOutboundReply(
238:             attemptId = attemptId,
239:             attemptMessageId = attempt.messageId,
240:             attemptAccountCode = attempt.accountCode,
241:             mailRecord = record
242:         )
243:     }
244: 
245:     internal fun findCompletedAttempt(attemptId: Long): CompletedOutboundReply? {
246:         val attempt = attemptRepository.findById(attemptId).orElse(null) ?: return null
247:         if (attempt.status != MailSendAttemptStatus.SENT) return null
248:         val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
249:         return CompletedOutboundReply(attemptId,attempt.messageId,attempt.accountCode,record)
250:     }
251: 
252:     /** Exact completed-payload lookup only: never reserves an attempt or touches draft state. */
253:     @Transactional
254:     fun findCompletedByPayload(payload: SendPayload): CompletedOutboundReply? {
255:         val review = payload.reviewSendRef?.let { reviewService().lockForSend(it) }
256:         validateReviewPayload(payload)
257:         val fingerprint = computeFingerprint(payload)
258:         val attempt = attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fingerprint.shortKey)
259:             ?: return null
260:         require(attempt.contentType == FINGERPRINT_CONTENT_TYPE) {
261:             "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
262:         }
263:         require(attempt.body == fingerprint.fullHex) {
264:             "Mail send attempt fingerprint collision: full hash mismatch"
265:         }
266:         require(attempt.recipient == payload.normalizedRecipient) {
267:             "Mail send attempt fingerprint collision: recipient mismatch"
268:         }
269:         if (attempt.status != MailSendAttemptStatus.SENT) return null
270:         val attemptId = attempt.id ?: return null
271:         if (review != null) {
272:             check(review.sendAttemptId == attemptId) { "Review is not bound to completed attempt" }
273:             validateReviewRecord(payload, record = mailRecordRepository.findByMailSendAttemptId(attemptId)
274:                 ?: error("Completed review record missing"))
275:         }
276:         val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
277:         return CompletedOutboundReply(attemptId, attempt.messageId, attempt.accountCode, record)
278:     }
279: 
280:     @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
281:     fun prepareAndClaim(payload: SendPayload): ClaimedAttempt {
282:         require(payload.draftSendRef == null || payload.reviewSendRef == null) { "Conflicting draft references" }
283:         val reviewRef = payload.reviewSendRef
284:         val review = reviewRef?.let { reviewService().lockForSend(it) }
285:         validateReviewPayload(payload)
286:         val fingerprint = computeFingerprint(payload)
287:         if (review?.sendAttemptId != null) {
288:             val bound = attemptRepository.findById(review.sendAttemptId).orElseThrow { IllegalStateException("Review attempt missing") }
289:             require(bound.body == fingerprint.fullHex && bound.recipient == payload.normalizedRecipient &&
290:                 bound.contentType == FINGERPRINT_CONTENT_TYPE && bound.accountCode == payload.accountCode &&
291:                 bound.orcidId == payload.orcidId && bound.mailType == fingerprint.shortKey) { "Review attempt identity mismatch" }
292:             val result = when (bound.status) {
293:                 MailSendAttemptStatus.SENT -> {
294:                     validateReviewRecord(payload, mailRecordRepository.findByMailSendAttemptId(requireNotNull(bound.id))
295:                         ?: error("Completed review record missing"))
296:                     ClaimResult.DEDUP_SENT
297:                 }
298:                 MailSendAttemptStatus.DELIVERY_UNKNOWN -> ClaimResult.UNKNOWN
299:                 MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> ClaimResult.IN_PROGRESS
300:                 else -> null
301:             }
302:             if (result != null) return ClaimedAttempt(requireNotNull(bound.id), bound.messageId, result)
303:         }
304:         if (review != null) reviewService().validateSendContext(review, requireNotNull(reviewRef))
305:         val draftRef = payload.draftSendRef
306:         val drafts = draftRef?.let { requireNotNull(mailReplyDraftService) { "Draft sending is not wired" } }
307:         val draftRow = draftRef?.let { requireNotNull(drafts).lockForClaim(it) }
308:         // A different requestId or changed content must not evade an unresolved delivery.
309:         if (draftRow?.sendAttemptId != null) {
310:             val bound = attemptRepository.findById(draftRow.sendAttemptId).orElseThrow {
311:                 IllegalStateException("Draft bound attempt is missing")
312:             }
313:             val blocked = when (bound.status) {
314:                 MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> ClaimResult.IN_PROGRESS
315:                 MailSendAttemptStatus.DELIVERY_UNKNOWN -> ClaimResult.UNKNOWN
316:                 else -> null
317:             }
318:             if (blocked != null) return ClaimedAttempt(requireNotNull(bound.id),bound.messageId,blocked)
319:             require(bound.status == MailSendAttemptStatus.FAILED_SAFE_TO_RETRY ||
320:                 bound.status == MailSendAttemptStatus.FAILED || bound.status == MailSendAttemptStatus.SENT) { "Draft attempt cannot be replaced" }
321:             requireNotNull(drafts).releaseBinding(draftRow)
322:         }
323:         // Review identity is computed before checking its bound attempt.
324:         val now = LocalDateTime.now()
325: 
326:         attemptRepository.insertIgnore(
327:             orcidId = payload.orcidId,
328:             mailType = fingerprint.shortKey,
329:             accountCode = payload.accountCode,
330:             messageId = fingerprint.messageId,
331:             status = MailSendAttemptStatus.PREPARED,
332:             recipient = payload.normalizedRecipient,
333:             subject = payload.subject,
334:             body = fingerprint.fullHex,
335:             contentType = FINGERPRINT_CONTENT_TYPE,
336:             createdAt = now,
337:             updatedAt = now
338:         )
339: 
340:         val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
341:             payload.orcidId, fingerprint.shortKey
342:         ) ?: throw IllegalStateException(
343:             "Mail send attempt not found after reservation: orcidId=${payload.orcidId} mailType=${fingerprint.shortKey}"
344:         )
345: 
346:         if (attempt.contentType != FINGERPRINT_CONTENT_TYPE) {
347:             throw IllegalArgumentException(
348:                 "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
349:             )
350:         }
351:         if (attempt.body != fingerprint.fullHex) {
352:             throw IllegalArgumentException(
353:                 "Mail send attempt fingerprint collision: full hash mismatch"
354:             )
355:         }
356:         if (attempt.recipient != payload.normalizedRecipient) {
357:             throw IllegalArgumentException(
358:                 "Mail send attempt fingerprint collision: recipient mismatch"
359:             )
360:         }
361: 
362:         val claim = when (attempt.status) {
363:             MailSendAttemptStatus.PREPARED -> {
364:                 val affected = attemptRepository.claimStatus(
365:                     requireNotNull(attempt.id),
366:                     MailSendAttemptStatus.PREPARED,
367:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
368:                     now
369:                 )
370:                 if (affected <= 0) {
371:                     throw IllegalStateException("CAS claim failed for attempt ${attempt.id}")
372:                 }
373:                 ClaimedAttempt(
374:                     attemptId = requireNotNull(attempt.id),
375:                     messageId = attempt.messageId,
376:                     result = ClaimResult.CLAIMED
377:                 )
378:             }
379: 
380:             MailSendAttemptStatus.SENT ->
381:                 ClaimedAttempt(
382:                     attemptId = requireNotNull(attempt.id),
383:                     messageId = attempt.messageId,
384:                     result = ClaimResult.DEDUP_SENT
385:                 )
386: 
387:             MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> {
388:                 val affected = attemptRepository.claimStatus(
389:                     requireNotNull(attempt.id),
390:                     MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
391:                     MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
392:                     now
393:                 )
394:                 if (affected <= 0) {
395:                     throw IllegalStateException("CAS claim for safe retry failed for attempt ${attempt.id}")
396:                 }
397:                 ClaimedAttempt(
398:                     attemptId = requireNotNull(attempt.id),
399:                     messageId = attempt.messageId,
400:                     result = ClaimResult.SAFE_RETRY_CLAIMED
401:                 )
402:             }
403: 
404:             MailSendAttemptStatus.DELIVERY_IN_PROGRESS ->
405:                 ClaimedAttempt(
406:                     attemptId = requireNotNull(attempt.id),
407:                     messageId = attempt.messageId,
408:                     result = ClaimResult.IN_PROGRESS
409:                 )
410: 
411:             MailSendAttemptStatus.DELIVERY_UNKNOWN ->
412:                 ClaimedAttempt(
413:                     attemptId = requireNotNull(attempt.id),
414:                     messageId = attempt.messageId,
415:                     result = ClaimResult.UNKNOWN
416:                 )
417: 
418:             else ->
419:                 ClaimedAttempt(
420:                     attemptId = requireNotNull(attempt.id),
421:                     messageId = attempt.messageId,
422:                     result = ClaimResult.PERMANENT_FAILED
423:                 )
424:         }
425:         if (review != null) {
426:             check(review.sendAttemptId == null || review.sendAttemptId == claim.attemptId) { "Review attempt cannot be replaced" }
427:             if (claim.result == ClaimResult.CLAIMED || claim.result == ClaimResult.SAFE_RETRY_CLAIMED)
428:                 reviewService().bindSend(review, claim.attemptId, claim.result.name)
429:             else if (claim.result == ClaimResult.DEDUP_SENT) {
430:                 check(review.sendAttemptId == claim.attemptId) { "Unbound review completed attempt" }
431:                 validateReviewRecord(payload, mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
432:                     ?: error("Completed review record missing"))
433:             }
434:         }
435:         if (draftRef != null && (claim.result == ClaimResult.CLAIMED || claim.result == ClaimResult.SAFE_RETRY_CLAIMED || claim.result == ClaimResult.DEDUP_SENT)) {
436:             if (claim.result == ClaimResult.DEDUP_SENT) {
437:                 check(mailRecordRepository.findByMailSendAttemptId(claim.attemptId)?.sendStatus == "SENT") {
438:                     "Completed attempt has no durable SENT mail record"
439:                 }
440:             }
441:             requireNotNull(drafts).bind(draftRef,claim.attemptId)
442:             if (claim.result == ClaimResult.DEDUP_SENT) drafts.closeSent(draftRef,claim.attemptId,now)
443:         }
444:         return claim
445:     }
446: 
447:     @Transactional(propagation = Propagation.REQUIRES_NEW)
448:     fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
449:         val review = payload.reviewSendRef?.let { reviewService().lockForSendResult(it) }
450:         validateReviewPayload(payload)
451:         check(review == null || review.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
452:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
453:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
454:             IllegalStateException("Mail send attempt not found: $attemptId")
455:         }
456:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
457:             "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
458:         }
459: 
460:         val now = LocalDateTime.now()
461:         val bodyText = payload.finalText.ifBlank { null }
462:         val mailBody = bodyText ?: payload.finalHtml
463:         // fast-p 02 (I-1/I-4)：与本次发送同一实例的规范快照 JSON；null 显式清空
464:         // （不沿用安全失败记录的旧附件）。
465:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
466:         // fast-p 05 (I-1/I-4)：本次发送的有序通用附件快照 JSON；空列表显式 null
467:         // （唯一 absence 形态，绝不写 []/空串，也不沿用上一次尝试的旧快照）。
468:         val outboundSnapshotJson = payload.outboundAttachments
469:             .takeIf { it.isNotEmpty() }
470:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
471:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
472: 
473:         val mailRecord = if (existingRecord != null) {
474:             existingRecord.copy(
475:                 senderAccountCode = payload.accountCode,
476:                 messageId = messageId,
477:                 inReplyTo = payload.inReplyTo,
478:                 subject = payload.subject,
479:                 body = mailBody,
480:                 matchedQaRuleId = payload.primaryRuleId,
481:                 sendStatus = "SENT",
482:                 sentAt = now,
483:                 errorSummary = null,
484:                 calendarAttachmentJson = snapshotJson,
485:                 outboundAttachmentsJson = outboundSnapshotJson
486:             )
487:         } else {
488:             MailRecord(
489:                 expertContactId = payload.contactId,
490:                 direction = "OUTBOUND",
491:                 mailType = "MANUAL_RICH_REPLY",
492:                 senderAccountCode = payload.accountCode,
493:                 triggeredBy = TriggeredBy.OPERATOR,
494:                 sourceInboundId = null,
495:                 messageId = messageId,
496:                 inReplyTo = payload.inReplyTo,
497:                 subject = payload.subject,
498:                 body = mailBody,
499:                 matchedQaRuleId = payload.primaryRuleId,
500:                 sendStatus = "SENT",
501:                 receivedAt = null,
502:                 sentAt = now,
503:                 mailSendAttemptId = attemptId,
504:                 createdAt = existingRecord?.createdAt ?: now,
505:                 calendarAttachmentJson = snapshotJson,
506:                 outboundAttachmentsJson = outboundSnapshotJson
507:             )
508:         }
509:         val savedRecord = mailRecordRepository.save(mailRecord)
510:         val mailRecordId = requireNotNull(savedRecord.id)
511: 
512:         if (payload.canonicalQaRuleIds.isNotEmpty()) {
513:             payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
514:                 mailRecordQaRuleRepository.save(
515:                     MailRecordQaRule(
516:                         mailRecordId = mailRecordId,
517:                         qaRuleId = qaRuleId,
518:                         ordinal = ordinal
519:                     )
520:                 )
521:             }
522:         }
523: 
524:         // fast-p 02 (I-2)：真实 SENT mail_record 取得 id 之后、attempt 标 SENT 之前创建
525:         // 排期 —— 与成功落库同一 REQUIRES_NEW 事务提交，二者同成功或同回滚（排期写失败
526:         // 绝不返回发送成功）。普通发送/无会议回信 meetingEvent 为 null，完全跳过。
527:         // 01 createFromSentMail 校验来源邮件（OUTBOUND/MANUAL_RICH_REPLY/SENT/带日历附件）
528:         // 与结构化输入；校验失败同样回滚整个成功事务（不静默吞掉排期写入错误）。
529:         payload.meetingEvent?.let { event ->
530:             meetingCalendarService.createFromSentMail(savedRecord, event)
531:         }
532: 
533:         attemptRepository.updateStatusAndError(
534:             id = attemptId,
535:             status = MailSendAttemptStatus.SENT,
536:             errorSummary = null,
537:             now = now
538:         )
539:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).closeSent(it,attemptId,now) }
540:         if (review != null) reviewService().recordSendResult(review, attemptId, MailSendAttemptStatus.SENT, mailRecordId)
541: 
542:         return mailRecordId
543:     }
544: 
545:     @Transactional(propagation = Propagation.REQUIRES_NEW)
546:     fun finalizeFailure(
547:         payload: SendPayload,
548:         attemptId: Long,
549:         messageId: String,
550:         resultStatus: String,
551:         errorSummary: String?
552:     ): Long {
553:         val review = payload.reviewSendRef?.let { reviewService().lockForSendResult(it) }
554:         validateReviewPayload(payload)
555:         check(review == null || review.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
556:         payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
557:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
558:             IllegalStateException("Mail send attempt not found: $attemptId")
559:         }
560:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
561:             "Cannot finalize failure: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
562:         }
563: 
564:         val now = LocalDateTime.now()
565:         val bodyText = payload.finalText.ifBlank { null }
566:         val mailBody = bodyText ?: payload.finalHtml
567:         val boundedError = errorSummary?.take(MAX_ERROR_SUMMARY_LENGTH)
568:         // fast-p 02 (I-1/I-4)：安全失败同样持久化本次快照 JSON；null 显式清空。
569:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
570:         // fast-p 05 (I-1/I-4)：安全失败同样持久化本次有序通用附件快照；空显式 null，
571:         // 绝不沿用上一次尝试（成功或失败）的旧快照。
572:         val outboundSnapshotJson = payload.outboundAttachments
573:             .takeIf { it.isNotEmpty() }
574:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
575:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
576: 
577:         val mailRecord = if (existingRecord != null) {
578:             existingRecord.copy(
579:                 senderAccountCode = payload.accountCode,
580:                 messageId = messageId,
581:                 inReplyTo = payload.inReplyTo,
582:                 subject = payload.subject,
583:                 body = mailBody,
584:                 matchedQaRuleId = payload.primaryRuleId,
585:                 sendStatus = "FAILED",
586:                 sentAt = null,
587:                 errorSummary = boundedError,
588:                 calendarAttachmentJson = snapshotJson,
589:                 outboundAttachmentsJson = outboundSnapshotJson
590:             )
591:         } else {
592:             MailRecord(
593:                 expertContactId = payload.contactId,
594:                 direction = "OUTBOUND",
595:                 mailType = "MANUAL_RICH_REPLY",
596:                 senderAccountCode = payload.accountCode,
597:                 triggeredBy = TriggeredBy.OPERATOR,
598:                 sourceInboundId = null,
599:                 messageId = messageId,
600:                 inReplyTo = payload.inReplyTo,
601:                 subject = payload.subject,
602:                 body = mailBody,
603:                 matchedQaRuleId = payload.primaryRuleId,
604:                 sendStatus = "FAILED",
605:                 receivedAt = null,
606:                 sentAt = null,
607:                 errorSummary = boundedError,
608:                 mailSendAttemptId = attemptId,
609:                 createdAt = now,
610:                 calendarAttachmentJson = snapshotJson,
611:                 outboundAttachmentsJson = outboundSnapshotJson
612:             )
613:         }
614:         val savedRecord = mailRecordRepository.save(mailRecord)
615:         val mailRecordId = requireNotNull(savedRecord.id)
616: 
617:         attemptRepository.updateStatusAndError(
618:             id = attemptId,
619:             status = resultStatus,
620:             errorSummary = boundedError,
621:             now = now
622:         )
623:         if (review != null) reviewService().recordSendResult(review, attemptId, resultStatus, mailRecordId)
624: 
625:         return mailRecordId
626:     }
627: 
628:     fun recordSendAudit(
629:         inboundProcessingId: Long,
630:         contactId: Long,
631:         mailRecordId: Long,
632:         canonicalFactIds: List<Long>,
633:         carriesQa: Boolean,
634:         delivered: DeliveredMail,
635:         sendSubject: String,
636:         bodyPreviewText: String,
637:         operatorName: String?,
638:         inboundRecord: com.weibo.talentintroduction.mail.domain.InboundMailProcessing,
639:         serverSuggestedFactIds: List<Long>,
640:         edited: Boolean?,
641:         note: String,
642:         // 04 (I-1/I-7): 服务端权威有界诊断，仅由发送方在 verified assembly 存在时传入；
643:         // null 时 after payload 保持既有字段逐字不变，不写伪造诊断。不进入 SendPayload
644:         // 或 attempt 幂等键，不新增 action row/action type。
645:         trustReplyDiagnostics: TrustReplyDiagnostics? = null
646:     ) {
647:         fun auditTask() {
648:             try {
649:                 val actionType = if (carriesQa) {
650:                     OperatorActionType.SEND_MANUAL_COMPOSED_REPLY
651:                 } else {
652:                     OperatorActionType.SEND_MANUAL_RICH_REPLY
653:                 }
654:                 val baseAfter = if (carriesQa) {
655:                     mapOf(
656:                         "mailRecordId" to mailRecordId,
657:                         "canonicalFactIds" to canonicalFactIds,
658:                         "serverSuggestedFactIds" to serverSuggestedFactIds,
659:                         "qaRuleIds" to canonicalFactIds,
660:                         "suggestedRuleIds" to serverSuggestedFactIds,
661:                         "draftGenerationState" to null,
662:                         "edited" to (edited ?: false),
663:                         "sendStatus" to delivered.status,
664:                         "subject" to sendSubject,
665:                         "bodyPreviewText" to bodyPreviewText
666:                     )
667:                 } else {
668:                     mapOf(
669:                         "mailRecordId" to mailRecordId,
670:                         "sendStatus" to delivered.status,
671:                         "subject" to sendSubject,
672:                         "bodyPreviewText" to bodyPreviewText
673:                     )
674:                 }
675:                 // 04 (I-1): 两条既有分支都可携带诊断 —— 「工作台无事实但完成发送」仍能
676:                 // 记录 unrecognized/unsupported 诊断到 SEND_MANUAL_RICH_REPLY。
677:                 val after = if (trustReplyDiagnostics != null) {
678:                     baseAfter + ("trustReplyDiagnostics" to trustReplyDiagnostics)
679:                 } else {
680:                     baseAfter
681:                 }
682:                 operatorActionLogService.record(
683:                     targetType = "INBOUND_MAIL_PROCESSING",
684:                     targetId = inboundProcessingId,
685:                     actionType = actionType,
686:                     expertContactId = contactId,
687:                     inboundProcessingId = inboundProcessingId,
688:                     before = mapOf("inboundProcessingId" to inboundProcessingId),
689:                     after = after,
690:                     operatorName = operatorName,
691:                     note = note
692:                 )
693:             } catch (ex: Exception) {
694:                 log.warn(
695:                     "Failed to record send audit for inbound {} mailRecord {}: {}",
696:                     inboundProcessingId, mailRecordId, ex.message, ex
697:                 )
698:             }
699:         }
700:         runAfterCommit(::auditTask)
701:     }
702: 
703:     /**
704:      * 会话（无来信锚点）回信审计（I-11）：动作类型固定 SEND_MANUAL_RICH_REPLY，
705:      * target 为联系人；before 记录锚点 mail record；after 字段沿用纯人工分支的
706:      * mailRecordId/sendStatus/subject/bodyPreviewText；inbound_processing_id 恒 NULL。
707:      * 与 recordSendAudit 共享 after-commit 调度与 best-effort 吞吐（I-8）。
708:      */
709:     fun recordConversationSendAudit(
710:         contactId: Long,
711:         anchorMailRecordId: Long,
712:         mailRecordId: Long,
713:         delivered: DeliveredMail,
714:         sendSubject: String,
715:         bodyPreviewText: String,
716:         operatorName: String?,
717:         note: String
718:     ) {
719:         fun auditTask() {
720:             try {
721:                 val before = mapOf("anchorMailRecordId" to anchorMailRecordId)
722:                 val after = mapOf(
723:                     "mailRecordId" to mailRecordId,
724:                     "sendStatus" to delivered.status,
725:                     "subject" to sendSubject,
726:                     "bodyPreviewText" to bodyPreviewText
727:                 )
728:                 operatorActionLogService.record(
729:                     targetType = "EXPERT_CONTACT",
730:                     targetId = contactId,
731:                     actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
732:                     expertContactId = contactId,
733:                     inboundProcessingId = null,
734:                     before = before,
735:                     after = after,
736:                     operatorName = operatorName,
737:                     note = note
738:                 )
739:             } catch (ex: Exception) {
740:                 log.warn(
741:                     "Failed to record conversation send audit for contact {} mailRecord {}: {}",
742:                     contactId, mailRecordId, ex.message, ex
743:                 )
744:             }
745:         }
746:         runAfterCommit(::auditTask)
747:     }
748: 
749:     private fun runAfterCommit(task: () -> Unit) {
750:         if (TransactionSynchronizationManager.isSynchronizationActive()) {
751:             TransactionSynchronizationManager.registerSynchronization(
752:                 object : TransactionSynchronization {
753:                     override fun afterCommit() {
754:                         task()
755:                     }
756:                 }
757:             )
758:         } else {
759:             task()
760:         }
761:     }
762: 
763:     private fun reviewService() = requireNotNull(mailReviewDraftService) { "Review sending is not wired" }
764: 
765:     private fun validateReviewPayload(payload: SendPayload) {
766:         val ref = payload.reviewSendRef ?: return
767:         val row = ref.draft
768:         if (payload.draftSendRef != null || payload.contactId != row.contactId || payload.accountCode != row.accountCode ||
769:             payload.subject != row.subject || payload.finalHtml != row.html || payload.finalText != row.text ||
770:             payload.canonicalQaRuleIds.isNotEmpty() || payload.primaryRuleId != null || payload.calendarAttachment != null ||
771:             payload.meetingEvent != null || payload.outboundAttachments.isNotEmpty() ||
772:             (row.targetKind == "INBOUND" && (payload.inboundProcessingId != row.targetId || payload.sourceAnchor != null)) ||
773:             (row.targetKind == "OUTBOUND" && (payload.inboundProcessingId != null ||
774:                 payload.sourceAnchor != "$SOURCE_ANCHOR_PREFIX${row.targetId}" || payload.idempotencyRequestId != row.sendRequestId)))
775:             throw MailReviewDraftException(409, "REVIEW_CONTENT_CHANGED", "Send payload differs from approved content")
776:     }
777: 
778:     private fun validateReviewRecord(payload: SendPayload, record: MailRecord) {
779:         check(record.sendStatus == "SENT" && record.direction == "OUTBOUND" &&
780:             record.expertContactId == payload.contactId && record.senderAccountCode == payload.accountCode &&
781:             record.subject == payload.subject && record.body == payload.finalText &&
782:             record.calendarAttachmentJson == null && record.outboundAttachmentsJson == null) { "Review SENT record mismatch" }
783:     }
784: 
785:     private fun appendLengthPrefix(data: ByteArrayOutputStream, value: String) {
786:         val bytes = value.toByteArray(Charsets.UTF_8)
787:         val len = bytes.size
788:         data.write(len shr 24)
789:         data.write((len shr 16) and 0xff)
790:         data.write((len shr 8) and 0xff)
791:         data.write(len and 0xff)
792:         data.write(bytes)
793:     }
794: 
795:     private fun sha256Hex(bytes: ByteArray): String {
796:         val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
797:         return digest.joinToString("") { "%02x".format(it) }
798:     }
799: }
```


## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
4: import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
5: import com.weibo.talentintroduction.campaign.domain.ExpertContact
6: import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
7: import com.weibo.talentintroduction.document.service.ExpertMaterialService
8: import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
9: import com.weibo.talentintroduction.expert.domain.ExpertProfile
10: import com.weibo.talentintroduction.expert.service.ExpertSearchService
11: import com.weibo.talentintroduction.mail.controller.ConversationCalendarAttachment
12: import com.weibo.talentintroduction.mail.controller.ConversationItemResponse
13: import com.weibo.talentintroduction.mail.controller.ConversationLatestInboundItem
14: import com.weibo.talentintroduction.mail.controller.ConversationLatestMessageItem
15: import com.weibo.talentintroduction.mail.controller.ConversationListResponse
16: import com.weibo.talentintroduction.mail.controller.ConversationMessageItemResponse
17: import com.weibo.talentintroduction.mail.controller.ConversationMessageListResponse
18: import com.weibo.talentintroduction.mail.controller.ConversationOutboundAttachment
19: import com.weibo.talentintroduction.mail.domain.MailRecord
20: import com.weibo.talentintroduction.mail.repository.MailRecordRepository
21: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
22: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationFilter
23: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationKeyset
24: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationMessageSqlRow
25: import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationSummarySqlRow
26: import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
27: import org.slf4j.LoggerFactory
28: import org.springframework.stereotype.Service
29: import java.time.LocalDate
30: import java.time.LocalDateTime
31: import java.time.format.DateTimeFormatter
32: import java.time.format.DateTimeParseException
33: import java.util.Base64
34: 
35: /**
36:  * 专家会话查询与关注的服务装配（fast-p 07）。GET 绝不调用 IMAP：
37:  * summary/timeline 全部来自 MySQL 归一化 UNION（I-1/I-4），仅当前专家 timeline
38:  * 才加载正文与附件元数据；附件元数据复用 06 的精确来源解析
39:  * （[ExpertMaterialService.resolveMessageAttachments]，与详情附件列表同一口径）。
40:  *
41:  * fast-p 01 追加：timeline 当前窗口邮件标签一次批量读取（I-3，复用
42:  * [InboundMailTagService.listTagsBatch]，仅真实 INBOUND_PROCESSING id）；summary 本页
43:  * 专家标签按 (level, orcidId) 批量投影（I-6，复用 [ExpertSearchService.searchByOrcidIds]，
44:  * 只读 ES 画像，不触发发现/补全/晋级）；主题 MIME 解码统一走 [MailSubjectDecoder]（I-4）。
45:  */
46: @Service
47: class MailboxConversationService(
48:     private val repository: MailboxConversationRepository,
49:     private val senderAccountRepository: MailSenderAccountRepository,
50:     private val expertContactRepository: ExpertContactRepository,
51:     private val expertMaterialService: ExpertMaterialService,
52:     private val inboundMailTagService: InboundMailTagService,
53:     private val expertSearchService: ExpertSearchService,
54:     // 03 (T2/I-3/I-4): 会话时间线单独日历附件元数据 —— 按 MAIL_RECORD OUTBOUND SENT
55:     // id 集合批量读一次存档快照。
56:     private val mailRecordRepository: MailRecordRepository,
57:     private val readAccessService: ExpertMailReadAccessService
58: ) {
59:     fun currentAccess(contactId: Long, username: String?): ExpertMailAccessLevel {
60:         require(contactId > 0)
61:         val level = readAccessService.accessLevel(contactId)
62:         if (MailReadOnlyPolicy.isReader(username) && !level.canRead)
63:             throw MailReadAccessDeniedException(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, "暂无权限查看该专家邮件")
64:         return level
65:     }
66: 
67:     private val log = LoggerFactory.getLogger(MailboxConversationService::class.java)
68:     companion object {
69:         const val DEFAULT_PAGE_SIZE = 20
70:         const val MAX_PAGE_SIZE = 100
71:         const val DEFAULT_MESSAGE_LIMIT = 50
72:         const val MAX_MESSAGE_LIMIT = 100
73:         const val MAX_TEXT_FILTER_LENGTH = 255
74:         const val FIRST_ATTACHMENT_NAME_LIMIT = 3
75: 
76:         /** 03 (I-3/I-4)：日历附件只在 SENT 出站行暴露（mail_record.send_status 值）。 */
77:         const val SEND_STATUS_SENT = "SENT"
78: 
79:         /** 会话消息级下载端点前缀（03 日历附件与 06 通用附件共用同一 `/api/mail/conversations` 前缀）。 */
80:         const val CONVERSATION_API_BASE = "/api/mail/conversations"
81: 
82:         /** 06 (I-3)：通用附件快照只投影人工富文本回复（05 的四个 finalize 分支之一）。 */
83:         const val MANUAL_RICH_REPLY_MAIL_TYPE = "MANUAL_RICH_REPLY"
84: 
85:         /** 专家层级白名单：只在合法层级查询 ES 画像（I-6，禁止跨层猜测回退）。 */
86:         private val LEVEL_NAMES = ExpertIndexLevel.values().map { it.name }.toSet()
87: 
88:         private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME
89:         private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
90:         private val CURSOR_SEPARATOR = "\u001F"
91:         private val SOURCE_RANK_OF = mapOf(
92:             MailboxConversationRepository.SOURCE_MAIL_RECORD to 1,
93:             MailboxConversationRepository.SOURCE_INBOUND_PROCESSING to 2
94:         )
95:         private val RANK_SOURCE_OF = SOURCE_RANK_OF.entries.associate { (k, v) -> v to k }
96:     }
97: 
98:     // ------------------------------------------------------------------
99:     // summary 列表
100:     // ------------------------------------------------------------------
101: 
102:     fun listConversations(
103:         username: String?,
104:         q: String?,
105:         followed: Boolean,
106:         repliedOnly: Boolean,
107:         pendingOnly: Boolean,
108:         waitingReply: Boolean,
109:         accountCode: String?,
110:         direction: String?,
111:         startDate: LocalDate?,
112:         endDate: LocalDate?,
113:         subject: String?,
114:         label: String?,
115:         recipientEmail: String?,
116:         keyword: String?,
117:         page: Int,
118:         size: Int,
119:         /** 01 (T3/I-4)：只看当前用户挂起专家；默认 false 保持既有调用兼容。 */
120:         suspendedOnly: Boolean = false,
121:         /** 01 (T3/I-4)：只看当前用户已提供专家；默认 false 保持既有调用兼容。 */
122:         providedOnly: Boolean = false,
123:         reviewPendingOnly: Boolean = false,
124:         expertScope: String = "ALL",
125:         accessLevel: String = "ALL",
126:         draftsOnly: Boolean = false
127:     ): ConversationListResponse {
128:         val reader = MailReadOnlyPolicy.isReader(username)
129:         require(accessLevel in setOf("ALL", "READ", "FULL")) { "非法 accessLevel" }
130:         require(expertScope in setOf("ALL", "USER1_FULL", "PENDING_REVIEW")) { "非法 expertScope" }
131:         if (expertScope != "ALL") MailReadOnlyPolicy.requireAdmin(username)
132:         if (reader && reviewPendingOnly) {
133:             throw MailReadAccessDeniedException(
134:                 MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, "无权查看待审核草稿"
135:             )
136:         }
137:         validateDirection(direction)
138:         validateTextFilter("q", q)
139:         validateTextFilter("subject", subject)
140:         validateTextFilter("label", label)
141:         if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
142:             throw IllegalArgumentException("startDate 不能晚于 endDate")
143:         }
144:         // 新筛选：trim 空白后为空 → null；长度校验在 trim 之后（超长 400）。
145:         val recipientEmailFilter = recipientEmail?.trim()?.takeIf { it.isNotEmpty() }
146:         val keywordFilter = keyword?.trim()?.takeIf { it.isNotEmpty() }
147:         validateTextFilter("recipientEmail", recipientEmailFilter)
148:         validateTextFilter("keyword", keywordFilter)
149:         val activeCodes = activeAccountCodes()
150:         val filterCounts = if (MailReadOnlyPolicy.isAdmin(username)) {
151:             val counts = repository.filterCounts(activeCodes)
152:             com.weibo.talentintroduction.mail.controller.ConversationFilterCounts(counts.first, counts.second)
153:         } else null
154:         if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
155:             return ConversationListResponse(emptyList(), 0, page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE),
156:                 filterCounts = filterCounts)
157:         }
158:         val pageIndex = page.coerceAtLeast(0)
159:         val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
160:         val filter = ConversationFilter(
161:             accountCodes = activeCodes,
162:             accountCode = accountCode,
163:             q = q,
164:             followed = followed,
165:             repliedOnly = repliedOnly,
166:             waitingReply = waitingReply,
167:             pendingOnly = pendingOnly,
168:             direction = direction,
169:             startTime = startDate?.atStartOfDay(),
170:             endTime = endDate?.plusDays(1)?.atStartOfDay(),
171:             subject = subject,
172:             label = label,
173:             recipientEmail = recipientEmailFilter,
174:             keyword = keywordFilter,
175:             suspendedOnly = suspendedOnly,
176:             providedOnly = providedOnly,
177:             reviewPendingOnly = reviewPendingOnly,
178:             expertScope = expertScope,
179:             accessLevel = accessLevel,
180:             draftsOnly = draftsOnly
181:         )
182:         val sessionUser = username.orEmpty()
183:         // Toolbar counts ignore mutually exclusive chips, including an empty chip result/page.
184:         val reviewPendingTotal = if (reader) 0L else repository.reviewPendingTotal(sessionUser, filter)
185:         val total = repository.countConversations(sessionUser, filter)
186:         if (total == 0L) {
187:             return ConversationListResponse(emptyList(), 0, pageIndex, pageSize, reviewPendingTotal, filterCounts)
188:         }
189:         val offset = pageIndex.toLong() * pageSize
190:         val rows = repository.pageConversations(sessionUser, filter, pageSize, offset)
191:         if (rows.isEmpty()) {
192:             return ConversationListResponse(emptyList(), total, pageIndex, pageSize, reviewPendingTotal, filterCounts)
193:         }
194:         val contactIds = rows.map { it.expertContactId }
195:         val accessLevels = readAccessService.accessLevels(contactIds)
196:         val ownDrafts = if (reader) repository.ownDraftsByContacts(sessionUser, contactIds).orEmpty() else emptyMap()
197:         val latestMessages = repository.latestMessageByContacts(contactIds, activeCodes, accountCode)
198:         val latestInbounds = repository.latestInboundByContacts(contactIds, activeCodes, accountCode)
199:         val accountCodesByContact = repository.accountCodesByContacts(contactIds, activeCodes, accountCode)
200:         val materialCounts = repository.materialCountByContacts(contactIds)
201:         val expertTagsByContact = currentPageExpertTags(rows)
202:         // 01 (T3/I-3)：本页挂起摘要一次批量读取；新表无行也拿到真实跨账号 pending。
203:         val suspensionStates = repository.suspensionStatesByContacts(sessionUser, contactIds)
204:         val reviewPendingCounts = if (reader) emptyMap() else
205:             repository.reviewPendingCountsByContacts(contactIds, activeCodes, accountCode)
206: 
207:         val sharedContacts = if (reader) expertContactRepository.findAllById(contactIds).associateBy { it.id } else emptyMap()
208:         val adminStates = if (reader) repository.suspensionStatesByContacts("admin", contactIds) else emptyMap()
209:         val items = rows.map { row ->
210:             val displayOwner = if (reader && accessLevels[row.expertContactId] != ExpertMailAccessLevel.FULL) "admin" else sessionUser
211:             val displayState = if (displayOwner == "admin" && reader) adminStates[row.expertContactId] else suspensionStates[row.expertContactId]
212:             ConversationItemResponse(
213:                 contactId = row.expertContactId,
214:                 name = row.expertName,
215:                 email = row.expertEmail,
216:                 orcid = row.orcidId,
217:                 // expert_contact 无机构列（MySQL 无 institution 存储），此处恒 null，
218:                 // 由前端从专家资料（ES 画像）展示；不伪造 DB 事实。
219:                 institution = null,
220:                 accountCodes = accountCodesByContact[row.expertContactId].orEmpty(),
221:                 followed = row.followed,
222:                 receivedCount = row.receivedCount,
223:                 sentCount = row.sentCount,
224:                 failedCount = row.failedCount,
225:                 pendingCount = row.pendingCount,
226:                 waitingReply = row.receivedCount == 0L && row.sentCount > 0L,
227:                 latestMessage = latestMessages[row.expertContactId]?.let { m ->
228:                     ConversationLatestMessageItem(
229:                         source = m.source,
230:                         id = m.id,
231:                         direction = m.direction,
232:                         // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）。
233:                         subject = MailSubjectDecoder.decode(m.subject),
234:                         preview = m.preview,
235:                         time = m.eventAt.format(ISO),
236:                         sendStatus = m.sendStatus
237:                     )
238:                 },
239:                 latestInbound = latestInbounds[row.expertContactId]?.let { i ->
240:                     ConversationLatestInboundItem(
241:                         processingId = i.processingId,
242:                         accountCode = i.accountCode,
243:                         messageId = i.messageId,
244:                         receivedAt = i.receivedAt.format(ISO)
245:                     )
246:                 },
247:                 materialCount = materialCounts[row.expertContactId] ?: 0L,
248:                 expertTags = expertTagsByContact[row.expertContactId],
249:                 // 01 (I-1/I-3)：逐项填真实值；未挂起行也带真实跨账号 pending 计数。
250:                 suspended = suspensionStates[row.expertContactId]?.suspended ?: false,
251:                 suspendReason = suspensionStates[row.expertContactId]?.suspendReason,
252:                 suspensionPendingCount = suspensionStates[row.expertContactId]?.suspensionPendingCount ?: 0L,
253:                 // 01 (I-1/I-4)：一次读取的 DB 真值；followed 由同一状态派生。
254:                 progressStatus = row.progressStatus,
255:                 operatorStatus = sharedContacts[row.expertContactId]?.operatorStatus,
256:                 currentIndexLevel = sharedContacts[row.expertContactId]?.currentIndexLevel,
257:                 displayPendingCount = displayState?.suspensionPendingCount ?: 0L,
258:                 displayPendingOwner = displayOwner,
259:                 displaySuspended = displayState?.suspended ?: false,
260:                 displaySuspendReason = displayState?.suspendReason,
261:                 reviewPendingCount = reviewPendingCounts[row.expertContactId] ?: 0L,
262:                 accessLevel = accessLevels.getValue(row.expertContactId),
263:                 ownDrafts = ownDrafts[row.expertContactId].orEmpty()
264:             )
265:         }
266:         return ConversationListResponse(items, total, pageIndex, pageSize, reviewPendingTotal, filterCounts)
267:     }
268: 
269:     // ------------------------------------------------------------------
270:     // 单专家 timeline
271:     // ------------------------------------------------------------------
272: 
273:     fun listMessages(
274:         contactId: Long,
275:         accountCode: String?,
276:         beforeCursor: String?,
277:         limit: Int,
278:         /**
279:          * 02 (I-2/I-4)：会话身份，只由 HTTP Controller 从 session 显式传入；默认 null 保持
280:          * 既有内部调用/测试兼容（null 非 reader → 管理员语义）。admission 在 Service 内，
281:          * 不新增绕行 HTTP 入口。
282:          */
283:         username: String? = null
284:     ): ConversationMessageListResponse {
285:         // Read the current grant once before contact/body/tag/attachment reads. No cached
286:         // capability from the list or an earlier page can authorize this request.
287:         val reader = MailReadOnlyPolicy.isReader(username)
288:         val accessLevel = readAccessService.accessLevel(contactId)
289:         if (reader && !accessLevel.canRead) {
290:             throw MailReadAccessDeniedException(
291:                 MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, "无权查看该专家的邮件"
292:             )
293:         }
294:         val hideAttachments = reader && accessLevel != ExpertMailAccessLevel.FULL
295:         expertContactRepository.findById(contactId)
296:             .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
297:         val activeCodes = activeAccountCodes()
298:         val pageLimit = limit.coerceIn(1, MAX_MESSAGE_LIMIT)
299:         if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
300:             return ConversationMessageListResponse(emptyList(), null, false, accessLevel)
301:         }
302:         val before = decodeKeyset(beforeCursor, contactId, accountCode)
303:         val filter = ConversationFilter(accountCodes = activeCodes, accountCode = accountCode)
304:         val page = repository.timelineMessages(contactId, filter, before, pageLimit)
305: 
306:         // I-3：只把当前窗口的真实 INBOUND_PROCESSING id 交出去批量读一次标签；窗口内
307:         // 无来信（纯 OUTBOUND / 更早页）不发请求。绝不用 source_inbound_id / 数值巧合
308:         // 给 OUTBOUND 行映射标签。
309:         val processingIds = page.rows
310:             .filter { it.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING }
311:             .map { it.id }
312:         val tagsByProcessingId = if (processingIds.isEmpty()) {
313:             emptyMap()
314:         } else {
315:             inboundMailTagService.listTagsBatch(processingIds)
316:         }
317: 
318:         // 03 (I-3/I-4)：只对当前窗口的 MAIL_RECORD+OUTBOUND+SENT 行按 id 集合批量读一次
319:         // 存档快照（findAllById 恰好一次；窗口无此类行不发请求）。INBOUND 行即使与
320:         // outbound 数值 id 相同也绝不读 outbound 快照（避免 source 碰撞串附件）。
321:         // READ skips every attachment-only lookup; FULL uses the original source checks.
322:         val outboundSentRows = if (hideAttachments) {
323:             emptyList()
324:         } else {
325:             page.rows.filter {
326:                 it.source == MailboxConversationRepository.SOURCE_MAIL_RECORD &&
327:                     it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
328:                     it.sendStatus == SEND_STATUS_SENT
329:             }
330:         }
331:         val mailRecordRowsById = if (outboundSentRows.isEmpty()) {
332:             emptyMap()
333:         } else {
334:             mailRecordRepository.findAllById(outboundSentRows.map { it.id })
335:                 .asSequence()
336:                 .filter {
337:                     it.id != null &&
338:                         it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
339:                         it.sendStatus == SEND_STATUS_SENT
340:                 }
341:                 .associateBy { requireNotNull(it.id) }
342:         }
343: 
344:         // 行按 DESC（新→旧）返回；正序呈现最新窗口。
345:         val items = page.rows.asReversed().map { row ->
346:             // Only READ suppresses attachment projection; source/snapshot validation stays shared.
347:             val attachments = if (hideAttachments) {
348:                 emptyList()
349:             } else {
350:                 expertMaterialService.resolveMessageAttachments(row.source, row.id)
351:             }
352:             ConversationMessageItemResponse(
353:                 source = row.source,
354:                 id = row.id,
355:                 contactId = row.expertContactId,
356:                 direction = row.direction,
357:                 accountCode = row.accountCode,
358:                 // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）；新收信已在
359:                 // 头读取时解码落库，普通文本二次解码是幂等 no-op。
360:                 subject = MailSubjectDecoder.decode(row.subject),
361:                 body = row.body,
362:                 cleanedBody = row.cleanedBody,
363:                 eventAt = row.eventAt.format(ISO),
364:                 sendStatus = row.sendStatus,
365:                 processStatus = row.processStatus,
366:                 attachmentCount = attachments.size,
367:                 firstAttachmentNames = attachments.take(FIRST_ATTACHMENT_NAME_LIMIT).mapNotNull { it.fileName },
368:                 messageId = row.messageId,
369:                 inReplyTo = row.inReplyTo,
370:                 tags = if (row.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING) {
371:                     tagsByProcessingId[row.id].orEmpty()
372:                 } else {
373:                     emptyList()
374:                 },
375:                 calendarAttachment = if (hideAttachments) null else calendarAttachmentOf(row, mailRecordRowsById[row.id]),
376:                 // 06 (I-3)：同一批 findAllById 结果投影通用附件快照（零额外查询、不读磁盘）。
377:                 outboundAttachments = if (hideAttachments) {
378:                     emptyList()
379:                 } else {
380:                     outboundAttachmentsOf(row, mailRecordRowsById[row.id])
381:                 }
382:             )
383:         }
384:         val nextBefore = if (page.hasMore) {
385:             // items 为正序；本窗口最旧的一条 = 边界（更早页 = 严格早于该键）。
386:             val boundary = items.firstOrNull()
387:             if (boundary != null) {
388:                 encodeCursor(
389:                     contactId = contactId,
390:                     accountCode = accountCode,
391:                     item = boundary
392:                 )
393:             } else null
394:         } else null
395:         return ConversationMessageListResponse(items, nextBefore, page.hasMore, accessLevel)
396:     }
397: 
398:     // ------------------------------------------------------------------
399:     // 本页专家标签投影（I-6/X8）
400:     // ------------------------------------------------------------------
401: 
402:     /**
403:      * 当前 SQL 页的专家标签：contactId → 标签列表或 null。
404:      *
405:      * - null = 未取得有效画像结果（本页无该 contact / 无 ORCID / 层级非法 / 画像缺失 /
406:      *   该层 ES 查询异常）——绝不伪称 []，绝不跨层猜测；[] = 画像已读取且无标签。
407:      * - 只对当前页 id 调一次 findAllById（不查全库、不逐人 findByOrcidId）；按层级分组后
408:      *   每层至多一次 searchByOrcidIds（全页 ≤3 次）；页为空/无合法 (level, orcid) 零调用。
409:      * - 结果按 (level, orcidId) 回填原 SQL 行序（绝不用 ES 返回顺序替代 SQL 顺序）；
410:      *   tags trim/去空/去重并保持 ES 顺序，服务端不截断。只读 ES 画像（复用既有
411:      *   _source 投影），不触发发现/补全/晋级；某层异常仅该层降级为 null，不影响其它层、
412:      *   SQL 排序、分页与整页响应。
413:      */
414:     private fun currentPageExpertTags(rows: List<ConversationSummarySqlRow>): Map<Long, List<String>?> {
415:         if (rows.isEmpty()) return emptyMap()
416:         val contacts = expertContactRepository.findAllById(rows.map { it.expertContactId })
417:             .associateBy { requireNotNull(it.id) }
418:         val profilesByLevelAndOrcid = mutableMapOf<Pair<String, String>, ExpertProfile>()
419:         for ((levelName, orcidIds) in levelOrcidIdsOf(contacts)) {
420:             try {
421:                 val level = ExpertIndexLevel.valueOf(levelName)
422:                 for (profile in expertSearchService.searchByOrcidIds(orcidIds, level)) {
423:                     if (profile.orcidId.isNotBlank()) {
424:                         profilesByLevelAndOrcid[levelName to profile.orcidId] = profile
425:                     }
426:                 }
427:             } catch (e: Exception) {
428:                 log.warn(
429:                     "Expert tag lookup failed for level {} ({} orcidIds); expertTags=null for this level",
430:                     levelName, orcidIds.size, e
431:                 )
432:             }
433:         }
434:         return rows.associate { row ->
435:             val contact = contacts[row.expertContactId]
436:             val tags = when {
437:                 contact == null -> null
438:                 contact.currentIndexLevel !in LEVEL_NAMES -> null
439:                 contact.orcidId.isBlank() -> null
440:                 else -> {
441:                     val profile = profilesByLevelAndOrcid[contact.currentIndexLevel to contact.orcidId]
442:                     if (profile == null) {
443:                         null
444:                     } else {
445:                         profile.tags
446:                             ?.map { it.trim() }
447:                             ?.filter { it.isNotEmpty() }
448:                             ?.distinct()
449:                             ?: emptyList()
450:                     }
451:                 }
452:             }
453:             row.expertContactId to tags
454:         }
455:     }
456: 
457:     /** 按合法层级分组去重本页 ORCID（空白或非法层级不进入任何 ES 查询）。 */
458:     private fun levelOrcidIdsOf(contacts: Map<Long, ExpertContact>): Map<String, List<String>> {
459:         val grouped = mutableMapOf<String, MutableList<String>>()
460:         for (contact in contacts.values) {
461:             if (contact.currentIndexLevel in LEVEL_NAMES && contact.orcidId.isNotBlank()) {
462:                 grouped.getOrPut(contact.currentIndexLevel) { mutableListOf() }.add(contact.orcidId)
463:             }
464:         }
465:         return grouped.mapValues { (_, orcids) -> orcids.distinct() }
466:     }
467: 
468:     // ------------------------------------------------------------------
469:     // 游标（(time, source, id) 编码/校验；绑定 contact/account scope）
470:     // ------------------------------------------------------------------
471: 
472:     /**
473:      * 03 (I-3/I-4)：单条时间线行的日历附件元数据。只在 MAIL_RECORD+OUTBOUND+SENT 行上
474:      * 按存档快照解析（01 CalendarAttachmentCodec 校验）；归属（contact/账号）一致且
475:      * 快照合法才附加，其余（旧行 null / 损坏 JSON / 跨专家账号）一律 null —— 与下载
476:      * 端点同一 parseOrNull，绝不从配置/当前模板重新生成。
477:      */
478:     private fun calendarAttachmentOf(
479:         row: ConversationMessageSqlRow,
480:         record: MailRecord?
481:     ): ConversationCalendarAttachment? {
482:         // I-3/I-4: 只对真实 MAIL_RECORD 行附加 —— INBOUND 行即使与 outbound 数值 id 相同
483:         // 也绝不读 outbound 快照（source 碰撞防护与下载端同款显式归属检查）。
484:         if (row.source != MailboxConversationRepository.SOURCE_MAIL_RECORD ||
485:             record == null ||
486:             record.expertContactId != row.expertContactId ||
487:             record.senderAccountCode != row.accountCode
488:         ) {
489:             return null
490:         }
491:         val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
492:         val id = requireNotNull(record.id)
493:         return ConversationCalendarAttachment(
494:             filename = snapshot.filename,
495:             byteLength = snapshot.icsText.toByteArray(Charsets.UTF_8).size,
496:             downloadUrl = "$CONVERSATION_API_BASE/${row.expertContactId}/messages/$id/calendar-attachment"
497:         )
498:     }
499: 
500:     /**
501:      * 06 (I-3)：该已发出站行的人工通用附件投影。
502:      *
503:      * - 只认 `MAIL_RECORD + OUTBOUND + MANUAL_RICH_REPLY + SENT`（行来源/方向/状态已由
504:      *   窗口批量查询过滤，这里再显式要求 MANUAL_RICH_REPLY、同专家、同账号）；
505:      * - 快照用 04 codec 的**展示语义**解析：NULL/空白与损坏都归为 null → 该行空列表，
506:      *   绝不让整页 500；非空白却解析失败时留一条不含正文/磁盘路径的诊断；
507:      * - downloadUrl 由服务端按消息归属固定路径拼接（client 不得自行构造）；
508:      * - 只读内存中的批量结果，不额外查询、不读磁盘/IMAP。
509:      */
510:     private fun outboundAttachmentsOf(
511:         row: ConversationMessageSqlRow,
512:         record: MailRecord?
513:     ): List<ConversationOutboundAttachment> {
514:         if (row.source != MailboxConversationRepository.SOURCE_MAIL_RECORD ||
515:             record == null ||
516:             record.expertContactId != row.expertContactId ||
517:             record.senderAccountCode != row.accountCode ||
518:             record.mailType != MANUAL_RICH_REPLY_MAIL_TYPE ||
519:             record.sendStatus != SEND_STATUS_SENT
520:         ) {
521:             return emptyList()
522:         }
523:         val json = record.outboundAttachmentsJson
524:         if (json.isNullOrBlank()) {
525:             return emptyList()
526:         }
527:         val snapshots = OutboundAttachmentSnapshotCodec.parseOrNull(json)
528:         if (snapshots == null) {
529:             // 诊断不含正文/路径/用户名，也不改变页面上其它行。
530:             log.warn(
531:                 "Corrupt outbound attachment snapshot on mail record {} (contact {})",
532:                 record.id,
533:                 row.expertContactId
534:             )
535:             return emptyList()
536:         }
537:         val recordId = requireNotNull(record.id)
538:         return snapshots.map { snapshot ->
539:             ConversationOutboundAttachment(
540:                 id = snapshot.id,
541:                 filename = snapshot.filename,
542:                 contentType = snapshot.contentType,
543:                 byteLength = snapshot.byteLength,
544:                 downloadUrl = "$CONVERSATION_API_BASE/${row.expertContactId}/messages/" +
545:                     "$recordId/outbound-attachments/${snapshot.id}/download"
546:             )
547:         }
548:     }
549: 
550:     private fun encodeCursor(contactId: Long, accountCode: String?, item: ConversationMessageItemResponse): String {
551:         val sourceRank = SOURCE_RANK_OF[item.source]
552:             ?: throw IllegalStateException("Unknown message source for cursor: ${item.source}")
553:         // scope = 本次请求的 contact + accountCode（不是消息自己的账号）：
554:         // 同一 scope 翻页才能复用；跨专家/换账号过滤一律拒绝。
555:         val payload = listOf(
556:             contactId.toString(),
557:             accountCode.orEmpty(),
558:             item.eventAt,
559:             item.source,
560:             item.id.toString(),
561:             sourceRank.toString()
562:         ).joinToString(CURSOR_SEPARATOR)
563:         return Base64.getUrlEncoder().withoutPadding()
564:             .encodeToString(payload.toByteArray(Charsets.UTF_8))
565:     }
566: 
567:     private fun decodeKeyset(cursor: String?, contactId: Long, accountCode: String?): ConversationKeyset? {
568:         if (cursor.isNullOrBlank()) return null
569:         val decoded = runCatching {
570:             String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
571:         }.getOrElse { throw IllegalArgumentException("无效的游标") }
572:         val parts = decoded.split(CURSOR_SEPARATOR)
573:         if (parts.size != 6) throw IllegalArgumentException("无效的游标")
574:         val cursorContactId = parts[0].toLongOrNull()
575:             ?: throw IllegalArgumentException("无效的游标")
576:         val cursorAccountCode = parts[1].ifEmpty { null }
577:         val cursorTime = runCatching { LocalDateTime.parse(parts[2], ISO) }
578:             .getOrElse { throw IllegalArgumentException("无效的游标") }
579:         val cursorSource = parts[3]
580:         val cursorId = parts[4].toLongOrNull()
581:             ?: throw IllegalArgumentException("无效的游标")
582:         val cursorRank = parts[5].toIntOrNull()
583:             ?: throw IllegalArgumentException("无效的游标")
584:         if (cursorContactId != contactId) throw IllegalArgumentException("无效的游标")
585:         if (cursorAccountCode != accountCode) throw IllegalArgumentException("无效的游标")
586:         if (SOURCE_RANK_OF[cursorSource] != cursorRank || cursorRank !in RANK_SOURCE_OF) {
587:             throw IllegalArgumentException("无效的游标")
588:         }
589:         return ConversationKeyset(
590:             beforeTime = cursorTime,
591:             beforeSourceRank = cursorRank,
592:             beforeId = cursorId
593:         )
594:     }
595: 
596:     // ------------------------------------------------------------------
597:     // 公共参数工具
598:     // ------------------------------------------------------------------
599: 
600:     /** 解析 yyyy-MM-dd 日期（含 end 当天的开区间语义由调用方处理）。 */
601:     fun parseDate(value: String?, paramName: String): LocalDate? {
602:         if (value.isNullOrBlank()) return null
603:         return try {
604:             LocalDate.parse(value.trim(), DATE)
605:         } catch (e: DateTimeParseException) {
606:             throw IllegalArgumentException("$paramName 必须是 yyyy-MM-dd 日期")
607:         }
608:     }
609: 
610:     private fun validateDirection(direction: String?) {
611:         if (direction != null && direction != MailboxConversationRepository.DIRECTION_INBOUND &&
612:             direction != MailboxConversationRepository.DIRECTION_OUTBOUND
613:         ) {
614:             throw IllegalArgumentException("direction 只能是 INBOUND 或 OUTBOUND")
615:         }
616:     }
617: 
618:     private fun validateTextFilter(name: String, value: String?) {
619:         if (value != null && value.length > MAX_TEXT_FILTER_LENGTH) {
620:             throw IllegalArgumentException("$name 过长（最多 $MAX_TEXT_FILTER_LENGTH 字符）")
621:         }
622:     }
623: 
624:     /** 活跃发件账号（排除模拟器），与 MailboxService 同一口径。 */
625:     fun activeAccountCodes(): List<String> =
626:         senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
627:             .map { it.accountCode }
628: }
```
