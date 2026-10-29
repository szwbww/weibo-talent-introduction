# 逐字源码证据

基线 main@b15bb069f43f89c45ee6859dba04a0baff5d774d，2026-10-29；以下是读取到的工作树源码，不代表线上部署版本。

## E01 · src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt:1

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

## E02 · src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt:20

```text
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

## E03 · src/main/resources/db/migration/V166__create_expert_mail_read_access.sql:1

```text
1: -- ============================================================================
2: -- V166 expert_mail_read_access：admin 授予内置 user1 的专家邮件查看权（01）
3: --
4: -- 关系即状态：行存在 = 允许查看该专家（expert_contact.id）的邮件；
5: -- 行不存在 = 禁止。不增加 enabled / role / read / send 等冗余列，也没有第二套
6: -- 状态词汇。主键固定为 (username, expert_contact_id)，username 只允许内置
7: -- `user1`（由 ExpertMailReadAccessService 固定写入，不来自请求体）。
8: --
9: -- granted_by 只记录当前授权的操作者（admin session），created_at = 首次授予时间；
10: -- 重复 PUT 不刷新，撤销后重新授予写新时间。撤销不保留历史审计（本步不建
11: -- audit_log 副作用）。
12: --
13: -- 唯一写者：ExpertMailReadAccessService（参数化 JDBC，INSERT ... ON DUPLICATE
14: -- KEY UPDATE / DELETE）。FK 级联是本次设计：专家联系人被删除时自动清理授权行。
15: -- 不设 admin_user 外键（允许账号开放前预先授权）；无初始 grant / 回填。
16: -- ============================================================================
17: CREATE TABLE expert_mail_read_access (
18:     username VARCHAR(64) NOT NULL,
19:     expert_contact_id BIGINT NOT NULL,
20:     granted_by VARCHAR(64) NOT NULL,
21:     created_at DATETIME NOT NULL,
22:     PRIMARY KEY (username, expert_contact_id),
23:     CONSTRAINT fk_expert_mail_read_access_contact
24:         FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
25: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## E04 · src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:155

```text
155:         }
156:         val offset = pageIndex.toLong() * pageSize
157:         val rows = repository.pageConversations(sessionUser, filter, pageSize, offset)
158:         if (rows.isEmpty()) {
159:             return ConversationListResponse(emptyList(), total, pageIndex, pageSize)
160:         }
161:         val contactIds = rows.map { it.expertContactId }
162:         val latestMessages = repository.latestMessageByContacts(contactIds, activeCodes, accountCode)
163:         val latestInbounds = repository.latestInboundByContacts(contactIds, activeCodes, accountCode)
164:         val accountCodesByContact = repository.accountCodesByContacts(contactIds, activeCodes, accountCode)
165:         val materialCounts = repository.materialCountByContacts(contactIds)
166:         val expertTagsByContact = currentPageExpertTags(rows)
167:         // 01 (T3/I-3)：本页挂起摘要一次批量读取；新表无行也拿到真实跨账号 pending。
168:         val suspensionStates = repository.suspensionStatesByContacts(sessionUser, contactIds)
169: 
170:         val items = rows.map { row ->
171:             ConversationItemResponse(
172:                 contactId = row.expertContactId,
173:                 name = row.expertName,
174:                 email = row.expertEmail,
175:                 orcid = row.orcidId,
176:                 // expert_contact 无机构列（MySQL 无 institution 存储），此处恒 null，
177:                 // 由前端从专家资料（ES 画像）展示；不伪造 DB 事实。
178:                 institution = null,
179:                 accountCodes = accountCodesByContact[row.expertContactId].orEmpty(),
180:                 followed = row.followed,
181:                 receivedCount = row.receivedCount,
182:                 sentCount = row.sentCount,
183:                 failedCount = row.failedCount,
184:                 pendingCount = row.pendingCount,
185:                 waitingReply = row.receivedCount == 0L && row.sentCount > 0L,
186:                 latestMessage = latestMessages[row.expertContactId]?.let { m ->
187:                     ConversationLatestMessageItem(
188:                         source = m.source,
189:                         id = m.id,
190:                         direction = m.direction,
191:                         // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）。
192:                         subject = MailSubjectDecoder.decode(m.subject),
193:                         preview = m.preview,
194:                         time = m.eventAt.format(ISO),
195:                         sendStatus = m.sendStatus
196:                     )
197:                 },
198:                 latestInbound = latestInbounds[row.expertContactId]?.let { i ->
199:                     ConversationLatestInboundItem(
200:                         processingId = i.processingId,
201:                         accountCode = i.accountCode,
202:                         messageId = i.messageId,
203:                         receivedAt = i.receivedAt.format(ISO)
204:                     )
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
293:         }
294: 
295:         // 行按 DESC（新→旧）返回；正序呈现最新窗口。
296:         val items = page.rows.asReversed().map { row ->
297:             // 02 (I-3/I-4)：reader 与附件能力同时关闭 —— 不解析附件、不读快照，四个附件
298:             // 字段固定为 0/[]/null/[]；正文/主题/方向/时间/来源/发送状态与标签照常。
299:             val attachments = if (reader) {
300:                 emptyList()
301:             } else {
302:                 expertMaterialService.resolveMessageAttachments(row.source, row.id)
303:             }
304:             ConversationMessageItemResponse(
305:                 source = row.source,
306:                 id = row.id,
307:                 contactId = row.expertContactId,
308:                 direction = row.direction,
309:                 accountCode = row.accountCode,
310:                 // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）；新收信已在
311:                 // 头读取时解码落库，普通文本二次解码是幂等 no-op。
312:                 subject = MailSubjectDecoder.decode(row.subject),
313:                 body = row.body,
314:                 cleanedBody = row.cleanedBody,
315:                 eventAt = row.eventAt.format(ISO),
316:                 sendStatus = row.sendStatus,
317:                 processStatus = row.processStatus,
318:                 attachmentCount = attachments.size,
319:                 firstAttachmentNames = attachments.take(FIRST_ATTACHMENT_NAME_LIMIT).mapNotNull { it.fileName },
320:                 messageId = row.messageId,
321:                 inReplyTo = row.inReplyTo,
322:                 tags = if (row.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING) {
323:                     tagsByProcessingId[row.id].orEmpty()
324:                 } else {
325:                     emptyList()
326:                 },
327:                 calendarAttachment = if (reader) null else calendarAttachmentOf(row, mailRecordRowsById[row.id]),
328:                 // 06 (I-3)：同一批 findAllById 结果投影通用附件快照（零额外查询、不读磁盘）。
329:                 outboundAttachments = if (reader) {
330:                     emptyList()
331:                 } else {
332:                     outboundAttachmentsOf(row, mailRecordRowsById[row.id])
333:                 }
334:             )
335:         }
336:         val nextBefore = if (page.hasMore) {
337:             // items 为正序；本窗口最旧的一条 = 边界（更早页 = 严格早于该键）。
338:             val boundary = items.firstOrNull()
339:             if (boundary != null) {
340:                 encodeCursor(
341:                     contactId = contactId,
342:                     accountCode = accountCode,
343:                     item = boundary
344:                 )
345:             } else null
346:         } else null
347:         return ConversationMessageListResponse(items, nextBefore, page.hasMore)
348:     }
349: 
350:     // ------------------------------------------------------------------
351:     // 本页专家标签投影（I-6/X8）
352:     // ------------------------------------------------------------------
353: 
354:     /**
355:      * 当前 SQL 页的专家标签：contactId → 标签列表或 null。
356:      *
357:      * - null = 未取得有效画像结果（本页无该 contact / 无 ORCID / 层级非法 / 画像缺失 /
358:      *   该层 ES 查询异常）——绝不伪称 []，绝不跨层猜测；[] = 画像已读取且无标签。
359:      * - 只对当前页 id 调一次 findAllById（不查全库、不逐人 findByOrcidId）；按层级分组后
360:      *   每层至多一次 searchByOrcidIds（全页 ≤3 次）；页为空/无合法 (level, orcid) 零调用。
361:      * - 结果按 (level, orcidId) 回填原 SQL 行序（绝不用 ES 返回顺序替代 SQL 顺序）；
362:      *   tags trim/去空/去重并保持 ES 顺序，服务端不截断。只读 ES 画像（复用既有
363:      *   _source 投影），不触发发现/补全/晋级；某层异常仅该层降级为 null，不影响其它层、
364:      *   SQL 排序、分页与整页响应。
365:      */
366:     private fun currentPageExpertTags(rows: List<ConversationSummarySqlRow>): Map<Long, List<String>?> {
367:         if (rows.isEmpty()) return emptyMap()
368:         val contacts = expertContactRepository.findAllById(rows.map { it.expertContactId })
369:             .associateBy { requireNotNull(it.id) }
370:         val profilesByLevelAndOrcid = mutableMapOf<Pair<String, String>, ExpertProfile>()
371:         for ((levelName, orcidIds) in levelOrcidIdsOf(contacts)) {
372:             try {
373:                 val level = ExpertIndexLevel.valueOf(levelName)
374:                 for (profile in expertSearchService.searchByOrcidIds(orcidIds, level)) {
375:                     if (profile.orcidId.isNotBlank()) {
376:                         profilesByLevelAndOrcid[levelName to profile.orcidId] = profile
377:                     }
378:                 }
379:             } catch (e: Exception) {
380:                 log.warn(
381:                     "Expert tag lookup failed for level {} ({} orcidIds); expertTags=null for this level",
382:                     levelName, orcidIds.size, e
383:                 )
384:             }
385:         }
386:         return rows.associate { row ->
387:             val contact = contacts[row.expertContactId]
388:             val tags = when {
389:                 contact == null -> null
390:                 contact.currentIndexLevel !in LEVEL_NAMES -> null
391:                 contact.orcidId.isBlank() -> null
392:                 else -> {
393:                     val profile = profilesByLevelAndOrcid[contact.currentIndexLevel to contact.orcidId]
394:                     if (profile == null) {
395:                         null
396:                     } else {
397:                         profile.tags
398:                             ?.map { it.trim() }
399:                             ?.filter { it.isNotEmpty() }
400:                             ?.distinct()
401:                             ?: emptyList()
402:                     }
403:                 }
404:             }
405:             row.expertContactId to tags
406:         }
407:     }
408: 
409:     /** 按合法层级分组去重本页 ORCID（空白或非法层级不进入任何 ES 查询）。 */
410:     private fun levelOrcidIdsOf(contacts: Map<Long, ExpertContact>): Map<String, List<String>> {
```

## E05 · src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:560

```text
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
619:     /** 消息级筛选在聚合口径上完全不可满足的组合（label 只存在于 INBOUND 侧）。 */
620:     private class MembershipEligibility(
621:         val impossible: Boolean,
622:         val outboundClause: String?,
623:         val inboundClause: String?
624:     )
625: 
626:     private fun membership(filter: ConversationFilter): MembershipEligibility {
627:         val hasMessageFilter = filter.direction != null ||
628:             filter.startTime != null ||
629:             filter.endTime != null ||
630:             filter.subject != null ||
631:             filter.label != null ||
632:             filter.recipientEmail != null ||
633:             filter.keyword != null
634:         if (!hasMessageFilter) {
635:             return MembershipEligibility(impossible = false, outboundClause = null, inboundClause = null)
636:         }
637:         // label 只存在于 inbound 侧；direction=OUTBOUND + label 无任何消息可满足 → 空集。
638:         if (filter.direction == DIRECTION_OUTBOUND && filter.label != null) {
639:             return MembershipEligibility(impossible = true, outboundClause = null, inboundClause = null)
640:         }
641:         val outboundEligible = filter.label == null && filter.direction != DIRECTION_INBOUND
642:         val inboundEligible = filter.direction != DIRECTION_OUTBOUND
643:         // 关键词只出现在消息 EXISTS 内（subject OR cleaned_body OR body；NULL 列照 SQL 空值
644:         // 处理）；summary SELECT 永不取回正文。邮箱语义沿旧 mailbox 口径：出站匹配
645:         // expert_contact.expert_email（本专家所有出站共享的联系人邮箱，在出站 EXISTS 内恒等），
646:         // 入站匹配 inbound_mail_processing.from_email（别名）。recipientEmail/keyword 与
647:         // 方向/日期/主题/label 全部 AND 在**同一封消息**的 EXISTS 内（I-2/X3）。
648:         val outboundClause = if (outboundEligible) {
649:             """
650:             EXISTS (
651:                 SELECT 1 FROM mail_record mro
652:                  WHERE mro.direction = 'OUTBOUND'
653:                    AND mro.expert_contact_id = u.expert_contact_id
654:                    AND mro.sender_account_code IN (:accountCodes)
655:                    AND (:accountCode IS NULL OR mro.sender_account_code = :accountCode)
656:                    AND (:startTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) >= :startTime)
657:                    AND (:endTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) < :endTime)
658:                    AND (:subject IS NULL OR mro.subject LIKE CONCAT('%', :subject, '%'))
659:                    AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
660:                    AND (:keyword IS NULL
661:                         OR mro.subject LIKE CONCAT('%', :keyword, '%')
662:                         OR mro.cleaned_body LIKE CONCAT('%', :keyword, '%')
663:                         OR mro.body LIKE CONCAT('%', :keyword, '%'))
664:             )
665:             """.trimIndent()
666:         } else null
667:         val inboundClause = if (inboundEligible) {
668:             """
669:             EXISTS (
670:                 SELECT 1 FROM inbound_mail_processing impi
671:                  WHERE impi.expert_contact_id = u.expert_contact_id
672:                    AND impi.sender_account_code IN (:accountCodes)
673:                    AND (:accountCode IS NULL OR impi.sender_account_code = :accountCode)
674:                    AND (:startTime IS NULL OR impi.received_at >= :startTime)
675:                    AND (:endTime IS NULL OR impi.received_at < :endTime)
676:                    AND (:subject IS NULL OR impi.subject LIKE CONCAT('%', :subject, '%'))
677:                    AND (:recipientEmail IS NULL OR impi.from_email LIKE CONCAT('%', :recipientEmail, '%'))
678:                    AND (:keyword IS NULL
679:                         OR impi.subject LIKE CONCAT('%', :keyword, '%')
680:                         OR impi.cleaned_body LIKE CONCAT('%', :keyword, '%')
681:                         OR impi.body LIKE CONCAT('%', :keyword, '%'))
682:                    AND (:label IS NULL OR EXISTS (
683:                          SELECT 1 FROM inbound_mail_tag imt
684:                           WHERE imt.inbound_processing_id = impi.id
685:                             AND imt.label = :label))
686:             )
687:             """.trimIndent()
688:         } else null
689:         return MembershipEligibility(
690:             impossible = false,
691:             outboundClause = outboundClause,
692:             inboundClause = inboundClause
693:         )
694:     }
695: 
696:     /**
697:      * WHERE 片段：q（真实 expert_contact 姓名/邮箱）+ followed/providedOnly 三态过滤 +
698:      * 挂起归类 + 消息级 membership。
699:      * membership 为单消息合取语义：方向/日期/主题/标签须被同一封消息满足；label 只
700:      * join 其对应来源（inbound_mail_tag → processing），绝不因多标签产生重复行。
701:      * outbound/inbound 两个方向 EXISTS 是 OR 互补组：组外加整层括号后再与 q/followed
702:      * AND（X1），否则 SQL 优先级会让单个方向 EXISTS 绕过 q/followed。
703:      */
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
824:         ConversationSummarySqlRow(
825:             expertContactId = getLong("expert_contact_id"),
```

## E06 · src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt:122

```text
122:     fun pendingBadge(username: String): MailboxPendingBadge {
123:         require(username.isNotBlank()) { "未登录" }
124:         val grouped = jdbcTemplate.query(
125:             """
126:             SELECT COALESCE(imp.reason_type, 'UNKNOWN') AS reason_type, COUNT(*) AS mail_count
127:               FROM inbound_mail_processing imp
128:               JOIN mail_sender_account msa ON msa.account_code = imp.sender_account_code
129:              WHERE imp.process_status = :status
130:                AND msa.account_code <> :simulatorCode
131:                AND NOT EXISTS (
132:                    SELECT 1 FROM expert_mailbox_suspension ems
133:                     WHERE ems.username = :username AND ems.expert_contact_id = imp.expert_contact_id
134:                )
135:              GROUP BY COALESCE(imp.reason_type, 'UNKNOWN')
136:             """.trimIndent(),
137:             MapSqlParameterSource("username", username)
138:                 .addValue("status", PROCESS_STATUS_MANUAL_REVIEW)
139:                 .addValue("simulatorCode", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
140:         ) { rs, _ -> rs.getString("reason_type") to rs.getLong("mail_count") }.toMap()
141:         return MailboxPendingBadge(grouped.values.sum(), grouped)
142:     }
143: 
144:     /** DELETE：显式取消（幂等；不删除/更新其它任何表 I-2/I-6）。 */
```

## E07 · src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:230

```text
230:      */
231:     private fun reclassifyFirstReplyAutoResponse(
232:         owner: MailSenderAccount,
233:         received: ReceivedMail,
234:         reason: String?,
235:         result: SinglePipelineResult
236:     ): SinglePipelineResult {
237:         if (reason == null) return result
238:         if (result.outcome == SinglePipelineOutcome.DUPLICATE_IMAP_UID ||
239:             result.outcome == SinglePipelineOutcome.DUPLICATE_INBOUND_MESSAGE
240:         ) {
241:             return result
242:         }
243:         // 物理键在本事务前已判重为空，因此同键行只可能是本封新行；仍以当前读确认状态。
244:         val created = findPhysicalRow(owner, received) ?: return result
245:         if (created.processStatus != "MANUAL_REVIEW") return result
246:         inboundMailProcessingRepository.save(
247:             created.copy(
248:                 processStatus = "PROCESSED",
249:                 processReason = reason,
250:                 reasonType = InboundAutoResponseDetector.REASON_TYPE_AUTO_RESPONSE,
251:                 updatedAt = LocalDateTime.now()
252:             )
253:         )
254:         return result.copy(
255:             outcome = SinglePipelineOutcome.AUTO_RESPONSE_PENDING_EXCLUDED,
256:             reason = reason
257:         )
258:     }
259: 
260:     /** I-2：物理组成员（owner + 直接别名账号）——收件人路由、历史指纹核验与同组探针识别的共同口径。 */
```

## E08 · src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1453

```text
1453:     private fun confirmManualReview(
1454:         account: MailSenderAccount,
1455:         received: ReceivedMail,
1456:         expertContactId: Long?,
1457:         reason: String,
1458:         reasonType: String? = "UNCLEAR_INTENT",
1459:         bridgeMetadata: Boolean = true
1460:     ): InboundMailProcessing =
1461:         confirmProcessed(account, received, expertContactId, "MANUAL_REVIEW", reason, reasonType, bridgeMetadata)
1462: 
1463:     private fun confirmManualReviewWithBody(
1464:         account: MailSenderAccount,
1465:         received: ReceivedMail,
1466:         expertContactId: Long?,
1467:         reason: String,
1468:         reasonType: String?,
1469:         cleanedBody: String,
1470:         bridgeMetadata: Boolean = true
1471:     ): InboundMailProcessing {
1472:         val now = LocalDateTime.now()
1473:         val saved = inboundMailProcessingRepository.save(
1474:             InboundMailProcessing(
1475:                 senderAccountCode = account.accountCode,
1476:                 // 真实抓取来源优先；兼容旧 INBOX 调用才回退到逻辑账号的转发组主邮箱。
1477:                 mailboxOwnerCode = received.sourceAccountCode ?: mailSenderAccountService.resolveInboundOwner(account).accountCode,
1478:                 folder = received.folder,
1479:                 uidValidity = received.uidValidity,
1480:                 imapUid = received.imapUid,
1481:                 messageId = received.messageId,
1482:                 inReplyTo = received.inReplyTo,
1483:                 fromEmail = received.from,
1484:                 subject = received.subject,
1485:                 body = received.body,
1486:                 cleanedBody = cleanedBody,
1487:                 receivedAt = received.receivedAt,
1488:                 processStatus = "MANUAL_REVIEW",
1489:                 processReason = reason,
1490:                 reasonType = reasonType,
1491:                 expertContactId = expertContactId,
1492:                 createdAt = now,
1493:                 updatedAt = now
1494:             )
1495:         )
1496:         applyAutoTags(saved, cleanedBody, received.body)
1497:         val savedId = saved.id ?: error("Inbound mail processing id is required")
1498:         if (bridgeMetadata) {
1499:             mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
1500:         }
1501:         return saved
1502:     }
1503: 
1504:     private fun applyAutoTags(
1505:         saved: InboundMailProcessing,
1506:         cleanedBody: String?,
1507:         fallbackBody: String?
1508:     ) {
1509:         runCatching {
1510:             val tagBody = cleanedBody ?: saved.body ?: fallbackBody
1511:             if (!tagBody.isNullOrBlank()) {
1512:                 saved.id?.let { inboundMailTagService.autoApplyQaTags(it, tagBody) }
1513:             }
1514:         }.onFailure { log.warn("auto tag failed for inbound ${saved.id}", it) }
1515:     }
1516: 
1517:     private fun confirmProcessed(
1518:         account: MailSenderAccount,
1519:         received: ReceivedMail,
1520:         expertContactId: Long?,
1521:         status: String,
1522:         reason: String,
1523:         reasonType: String? = null,
1524:         bridgeMetadata: Boolean = true,
1525:         body: String? = null,
1526:         cleanedBody: String? = null
1527:     ): InboundMailProcessing {
1528:         val now = LocalDateTime.now()
1529:         val saved = inboundMailProcessingRepository.save(
1530:             InboundMailProcessing(
1531:                 senderAccountCode = account.accountCode,
1532:                 // I-3：同 confirmManualReviewWithBody —— 新行必须带物理 owner 键。
1533:                 mailboxOwnerCode = received.sourceAccountCode ?: mailSenderAccountService.resolveInboundOwner(account).accountCode,
1534:                 folder = received.folder,
1535:                 uidValidity = received.uidValidity,
1536:                 imapUid = received.imapUid,
1537:                 messageId = received.messageId,
1538:                 inReplyTo = received.inReplyTo,
1539:                 fromEmail = received.from,
1540:                 subject = received.subject,
1541:                 body = body ?: received.body,
1542:                 cleanedBody = cleanedBody,
1543:                 receivedAt = received.receivedAt,
1544:                 processStatus = status,
1545:                 processReason = reason,
1546:                 reasonType = reasonType,
1547:                 expertContactId = expertContactId,
1548:                 createdAt = now,
1549:                 updatedAt = now
1550:             )
1551:         )
1552:         applyAutoTags(saved, cleanedBody, body ?: received.body)
1553:         val savedId = saved.id ?: error("Inbound mail processing id is required")
1554:         if (bridgeMetadata) {
1555:             mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
1556:         }
1557:         return saved
1558:     }
1559: 
1560:     // ------------------------------------------------------------------
1561:     // 04 新增路由辅助
1562:     // ------------------------------------------------------------------
1563: 
1564:     /** I-2 路由结果：唯一逻辑账号，或不可判定的显式人工兜底。 */
```

## E09 · src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:30

```text
30: ) {
31:     fun listManualReviewQueue(
32:         reasonType: String? = null,
33:         email: String? = null,
34:         subject: String? = null,
35:         pageSize: Int = 20,
36:         pageOffset: Int = 0,
37:         unmatchedOnly: Boolean = false,
38:         query: String? = null
39:     ): ManualReviewQueueResult {
40:         // I-2：账号范围与顶部 badge 同源（非模拟器），不附加 enabled 条件。
41:         val activeCodes = senderAccountRepository
42:             .findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
43:             .map { it.accountCode }
44:         val (records, totalCount) = if (unmatchedOnly) {
45:             // I-1：成员资格由 MANUAL_REVIEW + expert_contact_id IS NULL 共同决定。
46:             val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
47:             if (activeCodes.isEmpty()) {
48:                 // 账号集合为空：直接返回空结果，禁止生成空 IN (...) SQL。
49:                 emptyList<InboundMailProcessing>() to 0L
50:             } else {
51:                 inboundMailProcessingRepository.findUnmatchedManualReviewQueue(
52:                     activeCodes, normalizedQuery, pageSize, pageOffset
53:                 ) to inboundMailProcessingRepository.countUnmatchedManualReviewQueue(activeCodes, normalizedQuery)
54:             }
55:         } else {
56:             inboundMailProcessingRepository.findManualReviewQueue(
57:                 reasonType = reasonType,
58:                 email = email,
59:                 subject = subject,
60:                 limit = pageSize,
61:                 offset = pageOffset
62:             ) to inboundMailProcessingRepository.countManualReviewQueue(
63:                 reasonType = reasonType,
64:                 email = email,
65:                 subject = subject
66:             )
67:         }
68:         val (manualReviewTotal, counts) = if (activeCodes.isEmpty()) {
69:             0L to emptyMap()
70:         } else {
71:             val total = inboundMailProcessingRepository.countManualReviewByAccounts(activeCodes)
72:             val grouped = inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(activeCodes)
73:                 .associate { it.reasonType to it.count }
74:             total to grouped
75:         }
76:         return ManualReviewQueueResult(
77:             records = records,
78:             totalCount = totalCount,
79:             manualReviewTotal = manualReviewTotal,
80:             countsByReasonType = counts
81:         )
82:     }
83: 
84:     fun getDetail(id: Long): InboundMailProcessing {
85:         val record = inboundMailProcessingRepository.findById(id)
```

## E10 · src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:150

```text
150: 
151:     @Transactional
152:     fun bindToContact(
153:         recordId: Long,
154:         contactId: Long,
155:         resolvedBy: String,
156:         promoteToApplication: Boolean = false
157:     ): InboundMailProcessing {
158:         val record = inboundMailProcessingRepository.findById(recordId)
159:             .orElseThrow { error("Inbound mail processing not found: $recordId") }
160:         require(record.expertContactId == null) { "Already bound to a contact" }
161: 
162:         val contact = expertContactRepository.findById(contactId)
163:             .orElseThrow { error("Expert contact not found: $contactId") }
164: 
165:         expertEmailAliasService.bindAlias(
166:             expertContactId = contactId,
167:             email = record.fromEmail,
168:             source = "MANUAL_BIND"
169:         )
170: 
171:         var currentContact = contact.copy(needsManualAttention = true)
172:         if (promoteToApplication && !contact.applicationIndexed) {
173:             val now = record.receivedAt
174:             val firstReplyAt = contact.firstReplyAt ?: now
175:             val updatedContact = contact.copy(firstReplyAt = firstReplyAt)
176:             val ok = expertIndexWriterService.promoteToApplication(
177:                 orcid = contact.orcidId,
178:                 contact = updatedContact,
179:                 firstReplyAt = firstReplyAt.toInstant(ZoneId.systemDefault().rules.getOffset(firstReplyAt)),
180:                 triggeredBy = TriggeredBy.OPERATOR,
181:                 operatorName = resolvedBy
182:             )
183:             if (ok) {
184:                 currentContact = updatedContact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION")
185:             } else {
186:                 error("Failed to promote expert to Application index")
187:             }
188:         }
189: 
190:         expertContactRepository.save(currentContact)
191: 
192:         val now = LocalDateTime.now()
193:         val saved = inboundMailProcessingRepository.save(
194:             record.copy(
195:                 expertContactId = contactId,
196:                 processStatus = "MANUAL_REVIEW",
197:                 processReason = "MANUAL_BOUND",
198:                 resolvedBy = null,
199:                 resolvedAt = null,
200:                 updatedAt = now
201:             )
202:         )
203: 
204:         // 04（I-3/I-4）：开关启用时，按已有 processing-owner 附件幂等补 ExpertDocument——
205:         // 复用同一 attachmentId、零文件 I/O、不搬迁 owner/路径、不改 review/document
206:         // 状态；历史已绑定未建档数据不在此猜测回填（06 显式关联修复入口处理）。
207:         mailAttachmentService.ensureDocumentsForProcessingAttachments(recordId, contactId)
208: 
209:         operatorActionLogService.record(
210:             targetType = "INBOUND_MAIL_PROCESSING",
211:             targetId = recordId,
212:             actionType = OperatorActionType.BIND_INBOUND_MAIL,
213:             expertContactId = contactId,
214:             inboundProcessingId = recordId,
215:             before = mapOf(
216:                 "expertContactId" to null,
217:                 "processStatus" to record.processStatus,
218:                 "processReason" to record.processReason
219:             ),
220:             after = mapOf(
221:                 "expertContactId" to contactId,
222:                 "processStatus" to saved.processStatus,
223:                 "processReason" to saved.processReason
224:             ),
225:             operatorName = resolvedBy,
226:             note = "Bound unmatched inbound mail to expert contact $contactId"
227:         )
228: 
229:         return saved
230:     }
231: 
232:     @Transactional
233:     fun markResolved(recordId: Long, resolvedBy: String, note: String?): InboundMailProcessing {
234:         val record = inboundMailProcessingRepository.findById(recordId)
235:             .orElseThrow { error("Inbound mail processing not found: $recordId") }
236:         require(record.processStatus == "MANUAL_REVIEW") { "Record $recordId is not in MANUAL_REVIEW" }
237:         val now = LocalDateTime.now()
238:         val saved = inboundMailProcessingRepository.save(record.copy(
239:             processStatus = "PROCESSED",
240:             processReason = "MANUAL_RESOLVED",
241:             reasonType = "MANUAL_RESOLVED",
242:             resolvedBy = resolvedBy,
243:             resolvedAt = now,
244:             updatedAt = now
245:         ))
246: 
247:         val contactId = record.expertContactId
248:         if (contactId != null) {
249:             val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW")
250:             if (remaining == 0L) {
251:                 expertContactRepository.findById(contactId).ifPresent { contact ->
252:                     if (contact.needsManualAttention) {
253:                         expertContactRepository.save(contact.copy(needsManualAttention = false))
254:                     }
255:                 }
256:             }
257:         }
258:         return saved
259:     }
260: 
```

## E11 · src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1602

```text
1602:         }
1603:     }
1604: 
1605:     @Transactional
1606:     fun markResolved(
1607:         inboundProcessingId: Long,
1608:         resolvedBy: String?,
1609:         operatorName: String?,
1610:         note: String?
1611:     ) {
1612:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1613:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1614:         require(record.processStatus == "MANUAL_REVIEW") { "Record $inboundProcessingId is not in MANUAL_REVIEW" }
1615: 
1616:         val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: resolvedBy ?: "UNKNOWN"
1617:         val now = LocalDateTime.now()
1618:         inboundMailProcessingRepository.save(
1619:             record.copy(
1620:                 processStatus = "PROCESSED",
1621:                 processReason = "MANUAL_RESOLVED",
1622:                 reasonType = "MANUAL_RESOLVED",
1623:                 resolvedBy = actualOperator,
1624:                 resolvedAt = now,
1625:                 updatedAt = now
1626:             )
1627:         )
1628: 
1629:         val contactId = record.expertContactId
1630:         if (contactId != null) {
1631:             val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
1632:                 contactId, "MANUAL_REVIEW"
1633:             )
1634:             if (remaining == 0L) {
1635:                 expertContactRepository.findById(contactId).ifPresent { contact ->
1636:                     if (contact.needsManualAttention) {
1637:                         expertContactRepository.save(contact.copy(needsManualAttention = false))
1638:                     }
1639:                 }
1640:             }
1641:         }
1642: 
1643:         operatorActionLogService.record(
1644:             targetType = "INBOUND_MAIL_PROCESSING",
1645:             targetId = inboundProcessingId,
1646:             actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
1647:             expertContactId = contactId,
1648:             inboundProcessingId = inboundProcessingId,
1649:             before = mapOf(
1650:                 "processStatus" to "MANUAL_REVIEW",
1651:                 "processReason" to record.processReason,
1652:                 "reasonType" to record.reasonType
1653:             ),
1654:             after = mapOf(
1655:                 "processStatus" to "PROCESSED",
1656:                 "processReason" to "MANUAL_RESOLVED",
1657:                 "reasonType" to "MANUAL_RESOLVED"
1658:             ),
1659:             operatorName = actualOperator,
1660:             note = note
1661:         )
1662:     }
1663: 
1664:     @Transactional
1665:     fun cancelResolved(
1666:         inboundProcessingId: Long,
1667:         operatorName: String?,
1668:         note: String?
1669:     ) {
1670:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1671:             .orElseThrow {
1672:                 ResponseStatusException(
1673:                     HttpStatus.NOT_FOUND,
1674:                     "Inbound mail processing not found: $inboundProcessingId"
1675:                 )
1676:             }
1677:         if (record.processStatus != "PROCESSED" ||
1678:             record.processReason != "MANUAL_RESOLVED" ||
1679:             record.reasonType != "MANUAL_RESOLVED"
1680:         ) {
1681:             throw ResponseStatusException(
1682:                 HttpStatus.CONFLICT,
1683:                 "Record $inboundProcessingId is not manually resolved and cannot be cancelled"
1684:             )
1685:         }
1686: 
1687:         val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
1688:         val now = LocalDateTime.now()
1689:         val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
1690:         if (updated != 1) {
1691:             throw ResponseStatusException(
1692:                 HttpStatus.CONFLICT,
1693:                 "Record $inboundProcessingId changed concurrently and cannot be cancelled"
1694:             )
1695:         }
1696: 
1697:         val contactId = record.expertContactId
1698:         if (contactId != null) {
1699:             expertContactRepository.findById(contactId).ifPresent { contact ->
1700:                 if (!contact.needsManualAttention) {
1701:                     expertContactRepository.save(contact.copy(needsManualAttention = true))
1702:                 }
1703:             }
1704:         }
1705: 
1706:         operatorActionLogService.record(
1707:             targetType = "INBOUND_MAIL_PROCESSING",
1708:             targetId = inboundProcessingId,
1709:             actionType = OperatorActionType.CANCEL_INBOUND_RESOLVED,
1710:             expertContactId = contactId,
```

## E12 · src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:65

```text
65: 
66:     // ------------------------------------------------------------------
67:     // 共享所有权与文件就绪（I-1/I-3）——旧三服务委托的统一入口
68:     // ------------------------------------------------------------------
69: 
70:     /**
71:      * 附件唯一 owner 的联系人 id：
72:      * - mail_record owner → record.expert_contact_id；
73:      * - processing owner → processing.expert_contact_id（未绑定来信返回 null）；
74:      * - manual owner（V130）→ upload.expert_contact_id；
75:      * - 无 owner / owner 行缺失 → null。
76:      * 仅用于归属核验；不读取本地文件。
77:      */
78:     fun resolveOwnerContact(attachment: MailAttachment): Long? {
79:         val attachmentId = attachment.id ?: return null
80:         if (attachment.mailRecordId != null) {
81:             return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)
82:                 ?.expertContactId
83:         }
84:         if (attachment.inboundProcessingId != null) {
85:             return inboundMailProcessingRepository
86:                 .findById(attachment.inboundProcessingId)
87:                 .orElse(null)
88:                 ?.expertContactId
89:         }
90:         if (attachment.manualUploadId != null) {
91:             return manualUploadRepository
92:                 ?.findById(attachment.manualUploadId)
93:                 ?.orElse(null)
94:                 ?.expertContactId
95:         }
96:         error(
97:             "Attachment $attachmentId has no owner (mail_record_id, inbound_processing_id " +
98:                 "and manual_upload_id are all null)"
99:         )
100:     }
101: 
102:     /**
103:      * 集中式就绪解析（联系人作用域）：doc 归属 + attachment 唯一 owner 归属 +
104:      * realpath/基目录/就绪。未就绪抛 [MaterialNotReadyException]（HTTP 409）；
105:      * 不存在抛 NoSuchElement（404）；归属/路径越权抛 IllegalArgumentException。
106:      */
107:     fun resolveReadyFile(contactId: Long, attachmentId: Long): ReadyFile {
108:         val attachment = mailAttachmentRepository.findById(attachmentId)
109:             .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
110:         val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
111:             ?: throw NoSuchElementException("Document not found for attachment $attachmentId")
112:         require(document.expertContactId == contactId) {
113:             "Document for attachment $attachmentId does not belong to expert contact $contactId"
114:         }
115:         val ownerContactId = resolveOwnerContact(attachment)
116:         require(ownerContactId == null || ownerContactId == contactId) {
117:             "Attachment $attachmentId does not belong to expert contact $contactId"
118:         }
119:         return resolveFileReady(attachment)
120:     }
121: 
122:     /**
123:      * 集中式就绪解析（无联系人作用域，mailbox 附件下载用）：只做
124:      * attachment 就绪 + realpath/基目录校验。消息级归属在 07 的
125:      * resolveMessageAttachments 调用链内完成，本方法不读 doc。
126:      */
127:     fun resolveReadyFileUnscoped(attachmentId: Long): ReadyFile {
128:         val attachment = mailAttachmentRepository.findById(attachmentId)
129:             .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
130:         return resolveFileReady(attachment)
131:     }
132: 
133:     private fun resolveFileReady(attachment: MailAttachment): ReadyFile {
```

## E13 · src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:289

```text
289:     @PostMapping("/tags/add")
290:     fun addTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
291:         require(request.orcidId.isNotBlank()) { "orcidId is required" }
292:         require(request.tag.isNotBlank()) { "tag is required" }
293:         val profile = expertSearchService.findByOrcidId(request.orcidId, request.level)
294:             ?: return TagMutationResult(success = false, message = "Expert not found: ${request.orcidId}")
295:         val docId = profile.esDocId ?: request.orcidId
296:         val ok = expertIndexWriterService.addTag(docId, request.tag.trim(), request.level)
297:         return TagMutationResult(
298:             success = ok,
299:             message = if (ok) null else "Failed to add tag"
300:         )
301:     }
302: 
303:     @PostMapping("/tags/remove")
304:     fun removeTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
305:         require(request.orcidId.isNotBlank()) { "orcidId is required" }
306:         require(request.tag.isNotBlank()) { "tag is required" }
307:         val profile = expertSearchService.findByOrcidId(request.orcidId, request.level)
308:             ?: return TagMutationResult(success = false, message = "Expert not found: ${request.orcidId}")
309:         val docId = profile.esDocId ?: request.orcidId
310:         val ok = expertIndexWriterService.removeTag(docId, request.tag.trim(), request.level)
311:         return TagMutationResult(
312:             success = ok,
313:             message = if (ok) null else "Failed to remove tag"
314:         )
315:     }
316: 
317:     @GetMapping("/tags/aggregation")
```

## E14 · src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt:41

```text
41: @RequestMapping("/api/experts/manual-profile")
42: class ExpertManualProfileController(private val service: ExpertManualProfileService) {
43: 
44:     @GetMapping
45:     fun get(
46:         request: HttpServletRequest,
47:         @RequestParam(required = false) expertId: String?,
48:         @RequestParam(required = false) level: ExpertIndexLevel?
49:     ): ResponseEntity<Any> {
50:         sessionUsername(request) ?: return unauthorized()
51:         return ResponseEntity.ok(service.get(expertId, level ?: ExpertIndexLevel.RAW))
52:     }
53: 
54:     @PatchMapping
55:     fun patch(
56:         request: HttpServletRequest,
57:         @RequestBody(required = false) body: ExpertManualProfilePatchRequest?
58:     ): ResponseEntity<Any> {
59:         val username = sessionUsername(request) ?: return unauthorized()
60:         val payload = body ?: throw ExpertManualProfileInvalidRequestException("请求体无效")
61:         return ResponseEntity.ok(service.patch(payload.toCommand(), username))
62:     }
63: 
64:     @ExceptionHandler(ExpertManualProfileInvalidRequestException::class)
65:     fun handleInvalidRequest(ex: ExpertManualProfileInvalidRequestException): ResponseEntity<ApiErrorResponse> =
66:         error(HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, ex.message)
67: 
68:     /** 请求体/查询参数在进入业务前就无法解析：固定消息，不回显原始输入。 */
69:     @ExceptionHandler(
70:         HttpMessageNotReadableException::class,
71:         MethodArgumentTypeMismatchException::class,
72:         MissingServletRequestParameterException::class,
73:         HttpMediaTypeNotSupportedException::class
```

## E15 · src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:255

```text
255:     @PostMapping("/{contactId}/meeting-schedules")
256:     fun createMeetingSchedule(
257:         @PathVariable contactId: Long,
258:         @RequestBody request: CreateMeetingScheduleRequest
259:     ): MeetingScheduleResponse =
260:         meetingScheduleService.createManual(contactId, request.toCommand()).toResponse()
261: 
262:     @PutMapping("/{contactId}/meeting-schedules/{scheduleId}")
263:     fun updateMeetingSchedule(
264:         @PathVariable contactId: Long,
265:         @PathVariable scheduleId: Long,
266:         @RequestBody request: UpdateMeetingScheduleRequest
267:     ): MeetingScheduleResponse =
268:         meetingScheduleService.updateSchedule(contactId, scheduleId, request.toCommand()).toResponse()
269: 
270:     @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/confirm-and-email")
271:     fun confirmMeetingAndEmail(
272:         @PathVariable contactId: Long,
273:         @PathVariable scheduleId: Long,
274:         @RequestBody request: ConfirmMeetingRequest
275:     ): MeetingScheduleResponse =
276:         meetingScheduleService.confirmMeetingAndEmail(contactId, scheduleId, request.toCommand()).toResponse()
277: 
278:     @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/complete")
279:     fun completeMeeting(
280:         @PathVariable contactId: Long,
281:         @PathVariable scheduleId: Long
282:     ): MeetingScheduleResponse =
283:         meetingScheduleService.completeMeeting(contactId, scheduleId).toResponse()
284: 
285:     @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/cancel")
286:     fun cancelMeeting(
287:         @PathVariable contactId: Long,
288:         @PathVariable scheduleId: Long
289:     ): MeetingScheduleResponse =
290:         meetingScheduleService.cancelMeeting(contactId, scheduleId).toResponse()
291: 
292:     // A1: GET /{contactId}/materials 旧 feed 映射退役（与 06 ExpertMaterialController 同路径歧义，启动即 Ambiguous mapping）；方法体保留以兼容未授权 campaign 单元测试直接调用，不参与路由
293:     fun listMaterials(@PathVariable contactId: Long): List<ExpertMaterialItem> =
294:         expertMaterialService.listMaterials(contactId)
295: 
```

## E16 · src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt:25

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

## E17 · src/main/resources/static/mailbox-readonly.js:249

```text
249:         function renderList() {
250:             expertList.replaceChildren();
251:             if (listLoading) {
252:                 expertList.appendChild(statusNode(doc, "mc-empty", COPY.loading));
253:                 updatePager();
254:                 return;
255:             }
256:             if (listFailed) {
257:                 expertList.appendChild(statusNode(doc, "mc-error", COPY.network, "alert"));
258:                 updatePager();
259:                 return;
260:             }
261:             if (!listItems.length) {
262:                 expertList.appendChild(statusNode(doc, "mc-empty", listQuery ? COPY.searchEmpty : COPY.grantEmpty));
263:                 updatePager();
264:                 return;
265:             }
266:             listItems.forEach(function (item) {
267:                 var person = makeNode(doc, "div", "mc-person");
268:                 person.dataset.active = selected && String(selected.contactId) === String(item.contactId) ? "true" : "false";
269:                 var main = makeNode(doc, "button", "mc-person-main");
270:                 main.setAttribute("type", "button");
271:                 main.dataset.action = "select-expert";
272:                 main.dataset.contactId = String(item.contactId);
273:                 var name = makeNode(doc, "strong");
274:                 setText(name, (item.name || "").trim() || "未填写姓名");
275:                 var subject = makeNode(doc, "small");
276:                 setText(subject, latestSubject(item));
277:                 main.appendChild(name);
278:                 main.appendChild(subject);
279:                 person.appendChild(main);
280:                 expertList.appendChild(person);
281:             });
282:             updatePager();
283:         }
284: 
285:         function updatePager() {
286:             setText(pagerLabel, "第 " + (listPage + 1) + " 页，共 " + listTotal + " 位专家");
287:             previousButton.disabled = listLoading || listPage <= 0;
288:             nextButton.disabled = listLoading || (listPage + 1) * LIST_PAGE_SIZE >= listTotal;
289:         }
290: 
291:         // ---- 会话渲染 --------------------------------------------------------------
292:         function renderIdentity() {
293:             setText(identityName, selected ? ((selected.name || "").trim() || "未填写姓名") : "");
294:         }
295: 
296:         function renderConversation() {
297:             scroll.replaceChildren();
298:             renderIdentity();
299:             if (!selected) {
300:                 scroll.appendChild(statusNode(doc, "mc-empty", COPY.noSelection));
301:                 return;
302:             }
303:             if (detailStatus === "loading") {
304:                 scroll.appendChild(statusNode(doc, "mc-empty", COPY.loading));
305:                 return;
306:             }
307:             if (detailStatus === "forbidden") {
308:                 scroll.appendChild(statusNode(doc, "mc-error", COPY.forbidden, "alert"));
309:                 return;
310:             }
311:             if (detailStatus === "error") {
312:                 scroll.appendChild(statusNode(doc, "mc-error", COPY.network, "alert"));
313:                 return;
314:             }
315:             var timeline = makeNode(doc, "div", "mc-timeline");
316:             if (detailHasMore) {
317:                 var older = buttonNode(doc, "older", "加载更早邮件", "mc-text-button mc-load-older");
318:                 older.disabled = detailStatus === "loadingOlder";
319:                 timeline.appendChild(older);
320:             }
321:             if (!detailItems.length) {
322:                 timeline.appendChild(statusNode(doc, "mc-empty", COPY.noMail));
323:             }
324:             detailItems.forEach(function (message) {
325:                 var article = makeNode(doc, "article", "mc-message");
326:                 article.dataset.direction = message && message.direction ? String(message.direction) : "";
327:                 var head = makeNode(doc, "header");
328:                 var direction = makeNode(doc, "span");
329:                 setText(direction, directionLabel(message && message.direction));
330:                 var time = makeNode(doc, "time");
331:                 setText(time, formatTime(message && message.eventAt));
332:                 head.appendChild(direction);
333:                 head.appendChild(time);
334:                 var subject = makeNode(doc, "h3");
335:                 setText(subject, message && message.subject ? message.subject : "");
336:                 var body = makeNode(doc, "div", "mc-body");
337:                 setText(body, messageBody(message));
338:                 article.appendChild(head);
339:                 article.appendChild(subject);
340:                 article.appendChild(body);
341:                 timeline.appendChild(article);
342:             });
343:             scroll.appendChild(timeline);
344:         }
345: 
```

## E18 · src/main/resources/static/mailbox-chat.js:95

```text
95:     const FILTER_FIELDS = [
96:         { id: "mailboxFilterAccountCode", key: "accountCode", label: "邮箱账号", kind: "select", wide: false },
97:         { id: "mailboxFilterDirection", key: "direction", label: "收发方向", kind: "select", wide: false },
98:         { id: "mailboxFilterTag", key: "label", label: "邮件标签", kind: "select", wide: false },
99:         { id: "mailboxFilterRecipient", key: "recipientEmail", label: "专家邮箱", kind: "input", wide: false, placeholder: "输入邮箱关键词" },
100:         { id: "mailboxFilterKeyword", key: "keyword", label: "主题 / 内容关键词", kind: "input", wide: true, placeholder: "搜索邮件主题或正文" },
101:         { id: "mailboxFilterStartDate", key: "startDate", label: "开始日期", kind: "date", wide: false },
102:         { id: "mailboxFilterEndDate", key: "endDate", label: "结束日期", kind: "date", wide: false }
103:     ];
104: 
105:     const FILTER_KEYS = FILTER_FIELDS.map((field) => field.key);
106: 
```

## E19 · src/main/resources/static/mailbox-chat.js:1640

```text
1640:         function skeletonHtml() {
1641:             const chipButtons = FILTER_CHIPS.map((chip) => {
1642:                 const countSpan = chip.key === CHIP_DRAFTS
1643:                     ? '<span class="mailbox-draft-count" data-role="draft-total">0</span>'
1644:                     : CHIP_COUNT_KEYS.indexOf(chip.key) >= 0
1645:                         ? `<span class="mailbox-suspend-count" data-chip-count="${chip.key}" hidden></span>` : "";
1646:                 return `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}${countSpan}</button>`;
1647:             }).join("");
1648:             return `
1649:                 <div class="mail-chat mobile-core-mailbox" data-mobile-pane="list">
1650:                     <aside class="mc-experts" aria-label="专家会话列表">
1651:                         <div class="mc-list-tools">
1652:                             <div class="mc-search-row">
1653:                                 <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
1654:                                 <button class="mc-icon" type="button" data-action="mc-more-filters" title="更多筛选" aria-label="更多筛选" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-filter-count" hidden></span></button>
1655:                                 <div class="mc-filter-popover" id="mcFilterPopover" role="dialog" aria-label="更多筛选" hidden>
1656:                                     <header><strong>更多筛选</strong><button class="mc-close" type="button" data-action="mc-close-filters" aria-label="关闭筛选">×</button></header>
1657:                                     <div class="mc-filter-fields" id="mailboxFilterFields"></div>
1658:                                     <p class="mc-inline-error" role="alert" hidden></p>
1659:                                     <footer><button class="mc-text-button" type="button" data-action="mc-reset-filters">重置</button><button class="button primary" type="button" id="mailboxSearchBtn">应用筛选</button></footer>
1660:                                 </div>
1661:                             </div>
1662:                             <div class="mc-filters">
1663:                                 ${chipButtons}
1664:                             </div>
1665:                             <div class="mc-filter-summary" hidden></div>
1666:                         </div>
1667:                         <div class="mc-expert-list" aria-live="polite"></div>
1668:                         <div class="mc-pager"></div>
1669:                     </aside>
1670:                     <button type="button" class="button mobile-mailbox-back" data-action="mobile-mailbox-back">返回会话列表</button>
1671:                     <section class="mc-conversation" aria-label="专家往来信件"></section>
1672:                 </div>
1673:             `;
1674:         }
1675: 
1676:         function expertsRoot() {
```

## E20 · src/main/resources/static/mailbox-chat.js:3684

```text
3684:         function readAccessUrl(contactId) {
3685:             return `/api/mail/mailbox/conversations/${Number(contactId)}/read-access/${READ_ACCESS_TARGET}`;
3686:         }
3687: 
3688:         function readAccessAdmin() {
3689:             return String(instance.user) === READ_ACCESS_ACTOR;
3690:         }
3691: 
3692:         /** 当前选择是否可作为授权目标：仅 admin + 已选专家 + 非待匹配视图。 */
3693:         function readAccessTargetContactId() {
3694:             if (!readAccessAdmin() || isUnmatchedChip()) return 0;
3695:             const contactId = Number(instance.selectedContactId);
3696:             return Number.isFinite(contactId) && contactId > 0 ? contactId : 0;
3697:         }
3698: 
3699:         function nextReadAccessState(contactId, phase, canRead) {
3700:             const generation = (instance.readAccess ? instance.readAccess.generation : 0) + 1;
3701:             return { contactId: contactId || null, phase, canRead, generation };
3702:         }
3703: 
3704:         /** 换专家/清空选择/销毁/身份变化：作废一切在途读写的写回资格（I-3）。 */
3705:         function invalidateReadAccess() {
3706:             instance.readAccess = nextReadAccessState(0, "idle", null);
3707:             applyReadAccessView();
3708:         }
3709: 
3710:         function readAccessView(contactId) {
3711:             const state = instance.readAccess || {};
3712:             const same = state.contactId != null && Number(state.contactId) === Number(contactId);
3713:             const phase = same ? state.phase : "loading";
3714:             const canRead = same ? state.canRead : null;
3715:             const lastBadge = canRead === true ? READ_ACCESS_TEXT.granted.badge
3716:                 : canRead === false ? READ_ACCESS_TEXT.denied.badge
3717:                     : READ_ACCESS_TEXT.loading.badge;
3718:             if (phase === "saving") return { button: READ_ACCESS_TEXT.saving.button, badge: lastBadge, disabled: true };
3719:             if (phase === "error") return { button: READ_ACCESS_TEXT.error.button, badge: READ_ACCESS_TEXT.error.badge, disabled: false };
3720:             if (phase === "ready" && typeof canRead === "boolean") {
3721:                 const copy = canRead ? READ_ACCESS_TEXT.granted : READ_ACCESS_TEXT.denied;
3722:                 return { button: copy.button, badge: copy.badge, disabled: false };
3723:             }
3724:             return { button: READ_ACCESS_TEXT.loading.button, badge: READ_ACCESS_TEXT.loading.badge, disabled: true };
3725:         }
3726: 
3727:         /** S-1：追加在原 header actions 末尾的两个节点；非 admin 不产出任何标记。 */
3728:         function readAccessNodesHtml(contactId) {
3729:             const id = Number(contactId);
3730:             if (!readAccessAdmin() || !(id > 0) || isUnmatchedChip()) return "";
3731:             const view = readAccessView(id);
3732:             return `<button class="button" type="button" data-action="mc-toggle-user1-read-access" data-contact-id="${escapeText(id)}"${view.disabled ? " disabled" : ""}>${view.button}</button><span class="mc-badge" data-role="mc-user1-read-access-status" aria-live="polite">${view.badge}</span>`;
3733:         }
3734: 
3735:         /** 只改 textContent/disabled；不重建 header、不触发请求。 */
3736:         function applyReadAccessView() {
3737:             if (instance.disposed) return;
3738:             const body = conversationBody();
3739:             const actions = body && body.querySelector ? body.querySelector(".mc-actions") : null;
3740:             if (!actions) return;
```

## E21 · src/main/resources/static/mailbox-chat.css:60

```text
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
```

## E22 · src/main/resources/static/styles.css:3828

```text
3828: .inbound-tag-chip {
3829:     display: inline-flex;
3830:     align-items: center;
3831:     gap: 4px;
3832:     padding: 2px 10px;
3833:     border-radius: 999px;
3834:     font-size: 12px;
3835:     line-height: 1.5;
3836:     border: 1px solid var(--border);
3837:     background: var(--surface);
3838:     color: var(--text-main);
3839:     cursor: default;
3840:     max-width: 100%;
3841: }
3842: 
3843: .inbound-tag-chip.qa {
3844:     background: rgba(59, 130, 246, 0.12);
3845:     border-color: rgba(59, 130, 246, 0.35);
3846:     color: var(--primary-hover);
3847: }
3848: 
3849: .inbound-tag-chip.custom {
3850:     background: rgba(100, 116, 139, 0.12);
3851:     border-color: rgba(100, 116, 139, 0.35);
3852:     color: var(--text-secondary);
3853: }
3854: 
3855: .inbound-tag-chip.inactive {
3856:     opacity: 0.65;
3857:     color: var(--text-muted) !important;
3858:     background: rgba(148, 163, 184, 0.12) !important;
3859:     border-color: rgba(148, 163, 184, 0.35) !important;
3860:     text-decoration: line-through;
3861: }
3862: 
3863: .inbound-tag-chip.filter-chip {
```

## E23 · src/main/resources/static/styles.css:4766

```text
4766: .expert-tag {
4767:     display: inline-block;
4768:     font-size: 11px;
4769:     font-weight: 600;
4770:     padding: 1px 6px;
4771:     border-radius: var(--radius-sm);
4772:     line-height: 16px;
4773:     background: rgba(15, 23, 42, 0.06);
4774:     color: var(--text-muted);
4775:     border: 1px solid rgba(15, 23, 42, 0.1);
4776: }
4777: 
4778: .expert-tag.tag-auto_promoted {
4779:     background: rgba(var(--primary-rgb), 0.08);
4780:     color: var(--primary);
4781:     border-color: rgba(var(--primary-rgb), 0.18);
4782: }
4783: 
4784: .expert-tag.tag-verified {
4785:     background: rgba(var(--success-rgb), 0.08);
4786:     color: var(--success);
4787:     border-color: rgba(var(--success-rgb), 0.18);
4788: }
4789: 
4790: .expert-tag.tag-discovered {
4791:     background: rgba(var(--warning-rgb), 0.08);
4792:     color: var(--warning);
4793:     border-color: rgba(var(--warning-rgb), 0.18);
4794: }
4795: 
4796: .expert-tag.tag-sender-changed {
4797:     background: rgba(var(--warning-rgb), 0.08);
4798:     color: var(--warning);
4799:     border-color: rgba(var(--warning-rgb), 0.18);
4800: }
4801: 
4802: .expert-tag-editor .inbound-tag-editor-chips {
4803:     display: flex;
```

## E24 · src/main/resources/static/app.js:21094

```text
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
```

## E25 · src/main/resources/static/app.js:21158

```text
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
21186: function releaseMailReadOnly() {
21187:     const readerState = state.mailReadOnly;
21188:     if (!readerState) return;
21189:     try {
21190:         if (readerState.handle && typeof readerState.handle.unmount === "function") readerState.handle.unmount();
21191:         else if (readerState.active && typeof window !== "undefined" && window.MailboxReadOnly
21192:             && typeof window.MailboxReadOnly.unmount === "function") {
21193:             window.MailboxReadOnly.unmount($("#mailboxList"));
21194:         }
21195:     } catch (error) {
21196:         // 清理失败无后续影响：页面随后隐藏或重载。
21197:     }
21198:     readerState.active = false;
21199:     readerState.username = null;
21200:     readerState.handle = null;
21201:     const body = document.body;
21202:     if (body && body.dataset) delete body.dataset.mailReadOnly;
21203: }
21204: 
21205: function startAuthenticatedApp(username, mailReadOnly) {
21206:     const reader = username === MAIL_READ_ONLY_USERNAME;
21207:     // I-1：固定 user1 的元数据缺失或不为 true 时不得进入 admin 分支。
21208:     if (reader && mailReadOnly !== true) {
21209:         stopAuthenticatedApp();
21210:         showMailReadOnlyIdentityError();
21211:         return;
21212:     }
21213:     // I-3：身份变化整页重载，禁止沿用上一身份的内存状态（正文/草稿/游标）。
21214:     const identity = (reader ? "reader:" : "admin:") + String(username ?? "");
21215:     if (state.authIdentity && state.authIdentity !== identity) {
21216:         location.reload();
21217:         return;
21218:     }
21219:     state.authIdentity = identity;
21220:     $("#loginOverlay").hidden = true;
21221:     $("#changePasswordOverlay").hidden = true;
21222:     const userDisplay = $("#currentUserDisplay");
21223:     if (userDisplay) {
21224:         userDisplay.textContent = username;
21225:     }
21226:     const shell = $(".app-shell");
21227:     if (shell) {
21228:         shell.style.display = "grid";
21229:     }
21230:     if (reader) {
21231:         // 变量/徽标/任务/群推等 admin 启动链一律不执行。
21232:         appStarted = true;
21233:         startMailReadOnlyApp(username);
21234:         return;
21235:     }
21236:     if (!appStarted) {
21237:         appStarted = true;
21238:         if (typeof ensureVariableMeta === "function") {
21239:             ensureVariableMeta()
21240:                 .then(() => {
21241:                     if (typeof refreshVariableEditors === "function") {
21242:                         return refreshVariableEditors();
21243:                     }
21244:                     return undefined;
21245:                 })
21246:                 .catch((error) => showStatus(error.message, "error"));
21247:         }
21248:         updateUnmatchedBadge();
21249:         resumeProgressPollingIfNeeded().catch(() => {});
21250:         initBatchSendBanner();
21251:         refreshCurrentView();
21252:     }
21253: }
21254: 
21255: function stopAuthenticatedApp() {
21256:     // 最前停止观察器：之后到达的 active/详情响应一律失效（I-5）。
21257:     stopTaskActivityPolling();
21258:     if (typeof invalidateMailboxGroupPush === "function") invalidateMailboxGroupPush();
```

## E26 · src/main/resources/static/app.js:21535

```text
21535: // I-1/I-5：查看全部附件/材料按钮 —— 打开 child-08 ExpertMaterials 原生 dialog 抽屉
21536: // （同一 contactId 共享 store；本宿主不复制材料 DOM/逻辑）。
21537: function mcHostOpenMaterials(contactId) {
21538:     if (!expertMaterialsAvailable()) return false;
21539:     const id = Number(contactId);
21540:     if (!Number.isFinite(id) || id <= 0) return false;
21541:     if (typeof window.ExpertMaterials.configure !== "function" ||
21542:         typeof window.ExpertMaterials.mount !== "function") {
21543:         return false;
21544:     }
21545:     const host = $("#mailboxList");
21546:     if (!host) return false;
21547:     window.ExpertMaterials.configure({
21548:         api,
21549:         contextPath,
21550:         downloadZip: (contactId, attachmentIds) => downloadExpertMaterialsZip(contactId, attachmentIds),
21551:         labels: {
21552:             documentType: (value) => labelDocumentType(value),
21553:             documentStatus: (value) => labelDocumentStatus(value),
21554:             fileSize: (value) => formatFileSize(value)
21555:         }
21556:     });
21557:     window.ExpertMaterials.mount({ host, contactId: id, mode: "drawer" });
21558:     return true;
21559: }
21560: 
21561: // I-5：查看专家详情 → 既有联系人详情流程
21562: async function mcHostOpenExpertDetail(contactId) {
21563:     const id = Number(contactId);
21564:     if (!Number.isFinite(id) || id <= 0) return;
21565:     await openContactInList(id);
21566: }
21567: 
21568: // I-2：聊天内可信回复工作台宿主（LIVE_INBOUND 固定；autoBootstrap=false
21569: // —— 默认折叠不发生成、展开不自动生成，须显式点「重新生成」）。显式传入
21570: // host 元素，绝不使用既有 [data-trust-reply-live-host] 的全局 querySelector。
21571: function mcHostMountWorkbench(hostEl, processingId, callbacks) {
21572:     if (!hostEl) return null;
21573:     const runtime = requireTrustReplyWorkbenchRuntime(hostEl);
21574:     if (!runtime) return null;
21575:     const instance = runtime.mount(hostEl, {
21576:         mode: "LIVE",
21577:         source: { sourceType: "LIVE_INBOUND", sourceId: Number(processingId) },
21578:         contextPath,
21579:         autoBootstrap: false,
21580:         onUnauthorized: trustReplyUnauthorized,
21581:         onComplete: async (assembly) => {
21582:             if (callbacks && typeof callbacks.onComplete === "function") {
21583:                 callbacks.onComplete(assembly);
21584:             }
21585:         }
21586:     });
21587:     return {
21588:         unmount: () => {
21589:             try {
21590:                 instance.unmount();
21591:             } catch (e) {
21592:                 // noop
21593:             }
21594:         }
21595:     };
21596: }
21597: 
21598: // I-3/I-4：聊天人工回复发送 —— 沿用 submitManualRichReply 的服务端校验/QA 审计与
21599: // 安全确认文案；不触碰 #unmatchedDetailPanel / manualReplyQaContext 等原流程状态。
21600: async function mcHostSendRichReply(processingId, requestBody) {
21601:     return submitManualRichReply(`/api/mail/unmatched-inbound/${processingId}/manual-rich-reply`, requestBody);
21602: }
21603: 
21604: // T4 (I-3/I-4/I-6)：无来信会话自由回信 —— 同一 URL 提交 + 两级安全确认，只换 endpoint。
21605: // body 只含 requestId/accountScope/自由正文；服务端决定真实锚点与发件账号。
21606: async function mcHostSendConversationRichReply(contactId, requestBody) {
21607:     const id = Number(contactId);
21608:     if (!Number.isFinite(id) || id <= 0) return false;
21609:     return submitManualRichReply(`/api/mail/mailbox/conversations/${id}/manual-rich-reply`, requestBody);
21610: }
```

## E27 · src/main/resources/static/mailbox-chat.js:3499

```text
3499:             const mySeq = instance.seq;
3500:             const myEpoch = instance.convEpoch;
3501:             // 04（I-1/I-3）：换专家即新代次，并立即向服务端对账授权状态。
3502:             beginReadAccess();
3503:             instance.conversation.accountScope = accountFilterFromOptions();
3504:             instance.conversation.loading = true;
3505:             instance.conversation.error = "";
3506:             instance.conversation.items = [];
3507:             instance.conversation.nextBefore = null;
3508:             instance.conversation.hasMore = false;
3509:             instance.conversation.contact = null;
3510:             instance.manual = {
3511:                 mode: "none",
3512:                 targetProcessingId: null,
3513:                 targetAccountCode: "",
3514:                 targetKey: null,
3515:                 qa: null,
3516:                 busy: false
3517:             };
3518:             instance.logs = { loaded: false };
3519:             instance.dismissedNewInbound = null;
3520:             instance.pendingPrompt = null;
3521:             instance.translations = new Map();
3522:             instance.loadOlderBusy = false;
3523:             instance.draftsRef = null;
3524:             resetSuspensionState();
3525:             renderConversationScaffold();
3526: 
3527:             const contactId = Number(item.contactId);
3528:             loadExpertNote(contactId);
3529:             loadSuspensionState(contactId);
3530:             // c3（I-3）：换专家即作废旧 timing 代次，再按新 contact 取推荐。
3531:             loadContactTiming(contactId);
3532:             const accountFilter = accountFilterFromOptions();
3533:             const msgParams = new URLSearchParams();
3534:             msgParams.set("limit", String(MESSAGE_LIMIT));
3535:             if (accountFilter) msgParams.set("accountCode", accountFilter);
3536:             const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
3537:             const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
3538:                 .catch(() => null);
3539: 
3540:             Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
3541:                 if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
3542:                 instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
3543:                 instance.conversation.accountScope = accountFilter;
3544:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
3545:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
3546:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
3547:                 const cached = getConversationRecord(instance.user, accountFilter, contactId);
3548:                 if (cached && cached.items && cached.items.length > 0) {
3549:                     // 恢复缓存窗口（保留已加载历史），服务端同 key 状态胜
3550:                     instance.conversation.items = mergeServerIntoWindow(cached.items, serverItems);
3551:                     if (cached.nextBefore && (!instance.conversation.nextBefore || cached.items.length > serverItems.length)) {
3552:                         instance.conversation.nextBefore = cached.nextBefore;
3553:                     }
3554:                     if (cached.hasMore && cached.items.length > serverItems.length) {
3555:                         instance.conversation.hasMore = cached.hasMore;
3556:                     }
3557:                     instance.draftsRef = cached.drafts;
3558:                     instance.conversation.loading = false;
3559:                     renderConversationContent({ restoreRecord: cached });
3560:                 } else {
3561:                     instance.conversation.items = serverItems;
3562:                     instance.conversation.loading = false;
3563:                     renderConversationContent({ locateLatest: true });
3564:                 }
3565:                 // 02（I-3）：时间线就绪后重算完成提示锚点位置。
```

## E28 · src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt:1

```text
1: package com.weibo.talentintroduction.auth.config
2: 
3: import com.fasterxml.jackson.databind.ObjectMapper
4: import com.weibo.talentintroduction.auth.service.AuthService
5: import org.springframework.web.servlet.HandlerInterceptor
6: import javax.servlet.http.HttpServletRequest
7: import javax.servlet.http.HttpServletResponse
8: class AuthInterceptor(
9:     private val authService: AuthService,
10:     private val objectMapper: ObjectMapper
11: ) : HandlerInterceptor {
12: 
13:     override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
14:         if ("OPTIONS".equals(request.method, ignoreCase = true)) {
15:             return true
16:         }
17: 
18:         val session = request.getSession(false)
19:         val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String
20: 
21:         if (username == null) {
22:             writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
23:             return false
24:         }
25: 
26:         val user = authService.findUser(username)
27:         if (user == null) {
28:             session.invalidate()
29:             writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
30:             return false
31:         }
32: 
33:         if (user.mustChangePassword) {
34:             val uri = request.requestURI
35:             val contextPath = request.contextPath
36:             val relativeUri = if (uri.startsWith(contextPath)) uri.substring(contextPath.length) else uri
37: 
38:             if (relativeUri != "/api/auth/change-password" &&
39:                 relativeUri != "/api/auth/logout" &&
40:                 relativeUri != "/api/auth/me"
41:             ) {
42:                 writeErrorResponse(
43:                     response,
44:                     HttpServletResponse.SC_FORBIDDEN,
45:                     "PASSWORD_CHANGE_REQUIRED",
46:                     "首次登录请先修改密码"
47:                 )
48:                 return false
49:             }
50:         }
51: 
52:         // I-1/I-4：精确 user1 的已认证请求默认拒绝，仅放行白名单 handler
53:         // （HandlerMethod 类名+方法名+HTTP method 精确匹配，非 URL 前缀）。
54:         if (MailReadOnlyPolicy.isReader(username) &&
55:             !MailReadOnlyPolicy.isReaderAllowed(handler, request.method)
56:         ) {
57:             writeErrorResponse(
58:                 response,
59:                 HttpServletResponse.SC_FORBIDDEN,
60:                 MailReadOnlyPolicy.READ_ONLY_FORBIDDEN,
61:                 "只读账号无权访问该资源"
62:             )
63:             return false
64:         }
65: 
66:         return true
67:     }
68: 
69:     private fun writeErrorResponse(
70:         response: HttpServletResponse,
71:         statusCode: Int,
72:         code: String,
73:         message: String
74:     ) {
75:         response.status = statusCode
76:         response.contentType = "application/json;charset=UTF-8"
77:         val errorMap = mapOf(
78:             "code" to code,
79:             "message" to message,
80:             "detail" to null
81:         )
82:         objectMapper.writeValue(response.writer, errorMap)
83:     }
84: }
```

## E29 · src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt:1

```text
1: package com.weibo.talentintroduction.auth.config
2: 
3: import com.fasterxml.jackson.databind.ObjectMapper
4: import com.weibo.talentintroduction.auth.service.AuthService
5: import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
6: import org.springframework.context.annotation.Bean
7: import org.springframework.context.annotation.Configuration
8: import org.springframework.web.servlet.config.annotation.InterceptorRegistry
9: import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
10: 
11: @Configuration
12: @ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)
13: class AuthWebConfig(
14:     private val authService: AuthService,
15:     private val objectMapper: ObjectMapper
16: ) : WebMvcConfigurer {
17: 
18:     @Bean
19:     fun authInterceptor(): AuthInterceptor {
20:         return AuthInterceptor(authService, objectMapper)
21:     }
22: 
23:     override fun addInterceptors(registry: InterceptorRegistry) {
24:         registry.addInterceptor(authInterceptor())
25:             .addPathPatterns("/api/**")
26:             .excludePathPatterns("/api/auth/login", "/api/auth/me")
27:     }
28: }
```

## E30 · src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt:23
```text
23: class MeetingCalendarController(
24:     private val service: MeetingCalendarService
25: ) {
26:     @GetMapping("/events")
27:     fun events(
28:         @RequestParam(required = false) from: String?,
29:         @RequestParam(required = false) to: String?,
30:         @RequestParam(required = false) contactId: Long?,
31:         @RequestParam(defaultValue = "false") showCancelled: Boolean,
32:         @RequestParam(defaultValue = "100") limit: Int,
33:         @RequestParam(required = false) cursor: String?
34:     ): MeetingCalendarPageResponse = service.list(from, to, contactId, showCancelled, limit, cursor).let { page ->
35:         MeetingCalendarPageResponse(page.items.map(::response), page.nextCursor)
36:     }
37: 
38:     @GetMapping("/events/{id}")
39:     fun event(@PathVariable id: Long): MeetingCalendarEventResponse = response(service.get(id))
40: 
41:     @GetMapping("/summaries")
42:     fun summaries(@RequestParam(required = false) contactIds: String?): List<MeetingCalendarSummaryResponse> {
43:         val ids = contactIds?.trim()?.takeIf { it.isNotEmpty() }?.split(',')?.map {
44:             it.trim().toLongOrNull() ?: throw IllegalArgumentException("contactIds must contain numbers")
45:         } ?: emptyList()
46:         return service.summaries(ids).map { MeetingCalendarSummaryResponse(it.contactId, it.activeCount, it.next?.let(::response)) }
47:     }
48: 
49:     @PostMapping("/events")
50:     fun create(@RequestBody request: CreateMeetingCalendarRequest): ResponseEntity<MeetingCalendarEventResponse> =
51:         ResponseEntity.status(HttpStatus.CREATED).body(
52:             response(service.createManual(request.contactId, request.startBeijing, request.endBeijing, request.meetingLink, request.note))
53:         )
54: 
55:     @PutMapping("/events/{id}")
56:     fun update(@PathVariable id: Long, @RequestBody request: UpdateMeetingCalendarRequest): MeetingCalendarEventResponse =
57:         response(service.update(id, request.startBeijing, request.endBeijing, request.meetingLink, request.note, request.expectedUpdatedAt))
58: 
59:     @PostMapping("/events/{id}/cancel")
60:     fun cancel(@PathVariable id: Long, @RequestBody request: CancelMeetingCalendarRequest): MeetingCalendarEventResponse =
61:         response(service.cancel(id, request.expectedUpdatedAt, request.reason))
62: 
63:     private fun response(row: EventRow): MeetingCalendarEventResponse = MeetingCalendarEventResponse(
64:         id = row.event.id!!,
65:         contactId = row.event.expertContactId,
66:         expertName = row.expertName,
67:         expertEmail = row.expertEmail,
68:         sourceMailRecordId = row.event.sourceMailRecordId,
69:         startUtc = row.event.startsAtUtc.toString(),
70:         endUtc = row.event.endsAtUtc.toString(),
71:         meetingLink = row.event.meetingLink,
72:         note = row.event.note,
```

## E31 · src/main/resources/static/app.js:27275
```text
27275: 
27276: /** 收发件箱批量读取当前页专家摘要；失败必须向上抛出（I-4 不得冒充 0 场）。 */
27277: function mcHostGetMeetingSummaries(contactIds) {
27278:     return meetingCalendarFetchSummaries(contactIds);
27279: }
27280: 
27281: /** 排期已变更：按 contactId 失效并回读，同时广播给页面内其它订阅者（I-1/I-3）。 */
27282: function mcHostMeetingScheduleChanged(contactId) {
27283:     const id = Number(contactId);
27284:     if (typeof CustomEvent === "function" && typeof document !== "undefined" && document
27285:         && typeof document.dispatchEvent === "function") {
27286:         document.dispatchEvent(new CustomEvent("meeting-calendar-changed", {
27287:             detail: { contactId: Number.isFinite(id) ? id : null }
27288:         }));
27289:     }
27290:     if (state.view === "meeting-calendar") loadMeetingCalendar();
27291: }
27292: 
27293: /** 收发件箱入口：新增直接用共享表单；改期/取消先按专家取有效排期（多场先选择）。 */
27294: async function mcHostOpenMeetingSchedule(contactId, options) {
27295:     const opts = options || {};
27296:     const id = Number(contactId);
27297:     if (!Number.isFinite(id) || id <= 0) throw new Error("缺少专家标识");
27298:     const mode = (opts.mode === "edit" || opts.mode === "cancel") ? opts.mode : "create";
27299:     const expertLabel = String(opts.expertLabel || "");
27300:     if (mode === "create") {
27301:         openMeetingCalendarDialog({ mode: "create", contactId: id, expertLabel });
27302:         return;
27303:     }
27304:     const events = await meetingCalendarFetchExpertEvents(id);
27305:     const active = (events || []).filter((event) => !meetingCalendarIsCancelled(event));
27306:     if (active.length === 0) {
27307:         showStatus("该专家暂无排期", "error");
27308:         mcHostMeetingScheduleChanged(id);
27309:         return;
27310:     }
27311:     const label = expertLabel || meetingCalendarEventExpertLabel(active[0]);
27312:     if (active.length === 1) {
27313:         openMeetingCalendarDialog({
27314:             mode: mode === "cancel" ? "cancel" : "edit",
27315:             contactId: id,
27316:             event: active[0],
27317:             expertLabel: label
27318:         });
27319:         return;
27320:     }
27321:     openMeetingCalendarDialog({
27322:         mode: "pick",
27323:         contactId: id,
27324:         expertLabel: label,
27325:         pickEvents: active,
27326:         pickMode: mode
27327:     });
27328: }
27329: 
27330: async function meetingCalendarFetchExpertEvents(contactId) {
27331:     const params = new URLSearchParams();
27332:     params.set("contactId", String(contactId));
27333:     params.set("showCancelled", "false");
27334:     params.set("limit", String(MEETING_CALENDAR_PAGE_LIMIT));
27335:     const data = await api(`/api/meeting-calendar/events?${params.toString()}`);
27336:     return (data && Array.isArray(data.items)) ? data.items : [];
27337: }
27338: 
27339: // Auto-init on load: bind events after DOM is ready
27340: document.addEventListener("DOMContentLoaded", function() {
```

## E32 · src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:33
```text
33:     fun createManual(
34:         contactId: Long,
35:         startBeijing: String,
36:         endBeijing: String,
37:         meetingLink: String?,
38:         note: String?
39:     ): MeetingCalendarEventRepository.EventRow {
40:         requireContact(contactId)
41:         val input = MeetingCalendarInput(
42:             startUtc = parseBeijing(startBeijing),
43:             endUtc = parseBeijing(endBeijing),
44:             meetingLink = normalizeMeetingLink(meetingLink)
45:         )
46:         validateInput(input)
47:         val now = Instant.now().truncatedTo(MICROS)
48:         val id = repository.insert(
49:             MeetingCalendarEvent(
50:                 expertContactId = contactId,
51:                 startsAtUtc = input.startUtc,
52:                 endsAtUtc = input.endUtc,
53:                 meetingLink = input.meetingLink,
54:                 note = normalizeNote(note),
55:                 status = ACTIVE,
56:                 createdAt = now,
57:                 updatedAt = now
58:             )
59:         )
60:         return repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
61:     }
62: 
63:     @Transactional(propagation = Propagation.MANDATORY)
64:     fun createFromSentMail(record: MailRecord, input: MeetingCalendarInput): MeetingCalendarEventRepository.EventRow {
65:         val recordId = record.id ?: throw IllegalArgumentException("Sent mail record id is required")
66:         require(record.direction == "OUTBOUND") { "Calendar source mail must be OUTBOUND" }
67:         require(record.mailType == "MANUAL_RICH_REPLY") { "Calendar source mail type must be MANUAL_RICH_REPLY" }
68:         require(record.sendStatus == "SENT") { "Calendar source mail must be SENT" }
69:         require(CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) != null) {
70:             "Calendar source mail must contain a valid calendar attachment"
71:         }
72:         requireContact(record.expertContactId)
73:         validateInput(input.copy(meetingLink = normalizeMeetingLink(input.meetingLink)))
74: 
75:         repository.findBySourceMailRecordId(recordId)?.let { existing ->
76:             return existingSourceFor(record, existing)
77:         }
78: 
79:         val now = Instant.now().truncatedTo(MICROS)
80:         val event = MeetingCalendarEvent(
81:             expertContactId = record.expertContactId,
82:             sourceMailRecordId = recordId,
83:             startsAtUtc = input.startUtc,
84:             endsAtUtc = input.endUtc,
85:             meetingLink = normalizeMeetingLink(input.meetingLink),
86:             status = ACTIVE,
87:             createdAt = now,
88:             updatedAt = now
89:         )
90:         return try {
91:             val id = repository.insert(event)
92:             repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
93:         } catch (ex: DataIntegrityViolationException) {
94:             repository.findBySourceMailRecordIdForUpdate(recordId)?.let { existingSourceFor(record, it) } ?: throw ex
95:         }
96:     }
97: 
98:     private fun existingSourceFor(
```

## E33 · src/main/resources/static/app.js:11626
```text
11626: function expertManualProfileReadonlyCardTemplate(field) {
11627:     return `<div class="metadata-card" data-ec-readonly="${field.name}"><div class="metadata-card-header">${field.label}</div><div class="metadata-card-value ec-text" data-ec-value="${field.name}"></div></div>`;
11628: }
11629: 
11630: function expertManualProfileCardTemplate(field) {
11631:     const spanAll = field.spanAll ? " span-all" : "";
11632:     return `<div class="metadata-card${spanAll}"><div class="metadata-card-header">${field.label}</div><div class="metadata-card-value ec-text" data-ec-value="${field.name}"></div></div>`;
11633: }
11634: 
11635: function expertManualProfileSectionTemplate(group) {
11636:     const cards = [];
11637:     if (group.readonly) {
11638:         EXPERT_MANUAL_PROFILE_READONLY_FIELDS.forEach((field) => cards.push(expertManualProfileReadonlyCardTemplate(field)));
11639:     }
11640:     group.fields.forEach((field) => cards.push(expertManualProfileCardTemplate(field)));
11641:     return `<section class="ec-section" data-ec-group="${group.key}">` +
11642:         `<div class="ec-heading"><h3>${group.label}</h3><button type="button" class="button" data-ec-edit="${group.key}">编辑</button></div>` +
11643:         `<div class="metadata-grid">${cards.join("")}</div>` +
11644:         `<span class="ec-message" role="status" aria-live="polite"></span>` +
11645:         `<span class="ec-error" role="alert" hidden></span>` +
11646:         `</section>`;
11647: }
11648: 
11649: function expertManualProfileEditorTemplate(group) {
11650:     const controls = group.fields.map((field) => {
11651:         const labelClass = field.multiline ? ' class="ec-span"' : "";
11652:         // I-3（01 字段契约）：长度上限按 Unicode code point 校验（expertManualProfileCollectValues），
11653:         // 因此控件刻意不输出原生 maxlength——浏览器按 UTF-16 code unit 计数，会先于契约校验拒绝合法的星平面字符值。
11654:         const placeholder = field.placeholder ? ` placeholder="${field.placeholder}"` : "";
11655:         let control;
11656:         if (field.control === "select") {
11657:             const options = field.emptyOption
11658:                 ? [{ value: "", label: field.emptyDisplay }].concat(field.options || [])
11659:                 : (field.options || []);
11660:             control = `<select name="${field.name}">` +
11661:                 options.map((option) => `<option value="${option.value}">${option.label}</option>`).join("") +
11662:                 `</select>`;
11663:         } else if (field.multiline) {
11664:             control = `<textarea name="${field.name}"${placeholder}></textarea>`;
11665:         } else {
11666:             control = `<input name="${field.name}"${placeholder}>`;
11667:         }
11668:         return `<label${labelClass}>${field.label}${control}</label>`;
11669:     }).join("");
11670:     return `<dialog class="em-upload-dialog ec-editor" data-ec-editor="${group.key}" aria-label="编辑${group.label}">` +
11671:         `<form>` +
11672:         `<header class="em-upload-head"><div class="em-upload-title"><h3>${group.label}</h3><span class="em-upload-subtitle">保存后同步到当前专家</span></div><button class="modal-close-btn" type="button" aria-label="关闭">×</button></header>` +
11673:         `<div class="em-upload-body"><div class="ec-edit-grid">${controls}</div><span class="ec-error" role="alert" hidden></span></div>` +
11674:         `<footer class="em-upload-actions"><span class="em-upload-summary" role="status"></span><button class="button" type="button" data-ec-cancel>取消</button><button class="button primary" type="submit">保存</button></footer>` +
11675:         `</form>` +
11676:         `</dialog>`;
11677: }
11678: 
11679: function expertManualProfileShellTemplate() {
11680:     // 03（contact-profile-03-notes）：备注 section 前置于 nav，且沿用本文件既有的 typeof 守卫约定，
11681:     // 使按区域/函数切片执行的既有测试在未加载 03 区域时保持原骨架（4 个 section / 4 个 editor / nav 四按钮）。
11682:     // 05（contact-profile-05-video）：用同一守卫接入 nav「会议视频」按钮与视频 section/dialog，
11683:     // 未加载 05 区域时返回的骨架与新增前逐字一致。
11684:     const sharedNoteSection = typeof expertContactSharedNoteSectionTemplate === "function"
11685:         ? expertContactSharedNoteSectionTemplate()
11686:         : "";
11687:     const nav = EXPERT_MANUAL_PROFILE_GROUPS
11688:         .map((group) => `<button type="button" class="button" data-ec-jump="${group.key}">${group.label}</button>`)
11689:         .join("");
11690:     const videoNav = typeof expertMeetingVideoNavTemplate === "function" ? expertMeetingVideoNavTemplate() : "";
11691:     const sections = EXPERT_MANUAL_PROFILE_GROUPS.map(expertManualProfileSectionTemplate).join("");
11692:     const editors = EXPERT_MANUAL_PROFILE_GROUPS.map(expertManualProfileEditorTemplate).join("");
11693:     const videoPanel = typeof expertMeetingVideoPanelTemplate === "function" ? expertMeetingVideoPanelTemplate() : "";
11694:     return sharedNoteSection + `<nav class="ec-nav" aria-label="联系详情分组">${nav}${videoNav}</nav>` +
11695:         sections + editors + videoPanel;
11696: }
11697: 
11698: /** I-2：一次挂载 = 一个会话；捕获 expertId/contactId/generation/宿主引用，await 之后统一校验。 */
```

## E34 · src/main/resources/static/app.js:12141
```text
12141: function mountExpertManualProfile(rootEl, context) {
12142:     if (!rootEl || typeof rootEl.querySelector !== "function") return null;
12143:     const host = typeof rootEl.matches === "function" && rootEl.matches(EXPERT_MANUAL_PROFILE_HOST_SELECTOR)
12144:         ? rootEl
12145:         : rootEl.querySelector(EXPERT_MANUAL_PROFILE_HOST_SELECTOR);
12146:     if (!host) return null;
12147:     unmountExpertManualProfilePanels(rootEl);
12148:     const session = createExpertManualProfileSession(host, context || {});
12149:     expertManualProfileSessions.add(session);
12150:     host.innerHTML = expertManualProfileShellTemplate();
12151:     expertManualProfileBind(session);
12152:     // 03（I-1/I-2）：备注独立加载，不并入资料 Promise.all —— 备注失败不影响四组资料与旧材料。
12153:     if (typeof mountExpertContactSharedNote === "function") {
12154:         mountExpertContactSharedNote(session);
12155:     }
12156:     // 05（I-1/I-2）：会议视频列表独立加载，不并入资料 Promise.all —— 视频失败不影响四组资料/备注/旧材料。
12157:     if (typeof mountExpertMeetingVideos === "function") {
12158:         mountExpertMeetingVideos(session);
12159:     }
12160:     session.ready = loadExpertManualProfile(session);
12161:     return session;
12162: }
12163: /* contact-profile-02-ui:end */
12164: 
12165: /* contact-profile-03-notes:begin */
12166: // 03（contact-profile-03-notes）：联系详情人工档案顶部只读显示当前 contactId 的收发件箱备注。
12167: // I-1 单一来源：本页只有 GET /api/mail/contact-notes/{contactId}（无 PUT、无 localStorage 副本、无专家级 note）；
12168: //   contactId 不是正整数（含空串）时不发请求、不新建联系人，直接给“无联系人”空态。
```

## E35 · src/main/resources/static/mailbox-chat.css:210
```text
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

## E36 · src/main/resources/static/styles.css:12938
```text
12938: .ec-profile { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
12939: .ec-section { border: 1px solid var(--panel-border); border-radius: 10px; padding: 14px; background: #fff; scroll-margin-top: 12px; }
12940: .ec-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
12941: .ec-heading h3 { margin: 0; color: #1e293b; font-size: 14px; font-weight: 600; line-height: 1.5; }
12942: .ec-actions, .ec-nav { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
12943: .ec-text { white-space: pre-wrap; overflow-wrap: anywhere; }
12944: .ec-message { display: block; color: #64748b; font-size: 12px; line-height: 1.5; }
12945: .ec-error { display: block; color: #be123c; font-size: 12px; line-height: 1.5; }
12946: .ec-edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
12947: .ec-span { grid-column: 1 / -1; }
12948: .ec-editor > form { display: flex; flex-direction: column; min-height: 0; max-height: calc(100dvh - 32px); }
12949: .ec-editor label { text-transform: none; letter-spacing: 0; color: #475569; }
12950: .ec-editor textarea { min-height: 88px; }
12951: .ec-editor :is(input, select, textarea) { background: #fff; }
12952: .ec-profile :is(button, a):focus-visible, .ec-editor :is(button, input, select, textarea):focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
12953: .ec-profile button:disabled, .ec-editor :is(button, input, select, textarea):disabled { opacity: .45; cursor: not-allowed; transform: none; box-shadow: none; }
```

## E37 · src/main/resources/static/app.js:17155
```text
17155:     UNMATCHED_CONTACT: "未匹配到专家",
17156:     QA_NO_MATCH: "QA 未命中",
17157:     NOT_INTERESTED: "专家不感兴趣",
17158:     UNCLEAR_INTENT: "意图模糊",
17159:     MANUAL_RESOLVED: "已人工处理"
17160: };
17161: const REASON_TYPE_BADGE_CLASS = {
17162:     UNMATCHED_CONTACT: "info",       // 蓝
17163:     QA_NO_MATCH: "warn",             // 橙
17164:     NOT_INTERESTED: "error",         // 红
17165:     UNCLEAR_INTENT: "warn-yellow"    // 黄
17166: };
17167: const HIGH_PRIORITY_REASON_TYPES = new Set(["NOT_INTERESTED", "QA_NO_MATCH"]);
17168: 
17169: let unmatchedBadgeRefreshSeq = 0;
17170: async function refreshUnmatchedBadge() {
17171:     const seq = ++unmatchedBadgeRefreshSeq;
17172:     try {
17173:         const data = await api("/api/mail/mailbox/conversations/pending-badge");
17174:         if (seq !== unmatchedBadgeRefreshSeq) return;
17175:         updateUnmatchedBadge(data.countsByReasonType, data.manualReviewTotal);
17176:     } catch (_) {
17177:     }
17178: }
17179: 
17180: function updateUnmatchedBadge(counts, total) {
17181:     // I-2：reader 不读取待处理工单计数（该入口在 S-2 中不可见）。
17182:     if (typeof isMailReadOnlyMode === "function" && isMailReadOnlyMode()) return;
17183:     if (!counts) {
17184:         return refreshUnmatchedBadge();
17185:     }
17186:     const high = Array.from(HIGH_PRIORITY_REASON_TYPES).reduce((s, k) => s + (counts[k] || 0), 0);
17187:     const t = typeof total === "number" ? total : Object.values(counts).reduce((s, v) => s + (v || 0), 0);
17188:     const normal = Math.max(0, t - high);
17189:     setBadge("#unmatchedBadgeHigh", high);
17190:     setBadge("#unmatchedBadgeNormal", normal);
17191: }
17192: 
17193: function setBadge(sel, n) {
17194:     const el = $(sel);
17195:     if (!el) return;
17196:     if (n > 0) { el.textContent = n > 99 ? "99+" : n; el.hidden = false; }
17197:     else el.hidden = true;
17198: }
17199: 
17200: const MAILBOX_TAG_BADGE_CLASS = {
```
