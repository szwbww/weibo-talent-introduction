# 后端关键代码逐字证据

以下仅为本次本地工作区源码摘录；行号对应source-sha256基线，不是拟议实现。

## src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt:24

```kotlin
24:     ): LoginResponse {
25:         val username = request.username?.trim()
26:         val password = request.password
27: 
28:         if (username.isNullOrBlank() || password.isNullOrBlank()) {
29:             throw IllegalArgumentException("用户名或密码错误")
30:         }
31:         if (username != "admin") {
32:             throw IllegalArgumentException("用户名或密码错误")
33:         }
34: 
35:         val adminUser = authService.login(username, password)
36: 
37:         val session = servletRequest.getSession(true)
38:         servletRequest.changeSessionId()
39:         session.setAttribute(AuthSessionKeys.USERNAME, adminUser.username)
40: 
41:         return LoginResponse(
42:             username = adminUser.username,
```

## src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt:99

```kotlin
99: 
100: data class LoginResponse(
101:     val username: String,
102:     val mustChangePassword: Boolean
103: )
104: 
105: data class MeResponse(
106:     val authenticated: Boolean,
107:     val username: String?,
108:     val mustChangePassword: Boolean
109: )
110: 
111: data class ChangePasswordRequest(
112:     val oldPassword: String?,
113:     val newPassword: String?
114: )
```

## src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt:14

```kotlin
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
52:         return true
53:     }
54: 
55:     private fun writeErrorResponse(
56:         response: HttpServletResponse,
57:         statusCode: Int,
58:         code: String,
59:         message: String
```

## src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt:23

```kotlin
23:     override fun addInterceptors(registry: InterceptorRegistry) {
24:         registry.addInterceptor(authInterceptor())
25:             .addPathPatterns("/api/**")
26:             .excludePathPatterns("/api/auth/login", "/api/auth/me")
27:     }
28: }
```

## src/main/kotlin/com/weibo/talentintroduction/auth/service/AuthService.kt:40

```kotlin
40:     fun changePassword(username: String?, oldPassword: String?, newPassword: String?) {
41:         val trimmedUsername = username?.trim()
42:         if (trimmedUsername.isNullOrBlank() || oldPassword.isNullOrBlank() || newPassword.isNullOrBlank()) {
43:             throw IllegalArgumentException("输入参数不能为空")
44:         }
45:         if (trimmedUsername != "admin") {
46:             throw IllegalArgumentException("用户名错误")
47:         }
48:         if (oldPassword.length > 128 || newPassword.length > 128) {
49:             throw IllegalArgumentException("密码长度过长")
50:         }
51: 
52:         val user = adminUserRepository.findByUsername(trimmedUsername)
53:             ?: throw IllegalArgumentException("用户不存在")
54: 
```

## src/main/kotlin/com/weibo/talentintroduction/auth/service/AdminUserBootstrap.kt:23

```kotlin
23:         val existing = adminUserRepository.findByUsername("admin")
24:         if (existing == null) {
25:             try {
26:                 val admin = AdminUser(
27:                     username = "admin",
28:                     passwordHash = passwordEncoder.encode("admin"),
29:                     mustChangePassword = true,
30:                     createdAt = LocalDateTime.now(),
31:                     updatedAt = LocalDateTime.now()
32:                 )
33:                 adminUserRepository.save(admin)
34:                 logger.info("Default admin user created successfully.")
35:             } catch (e: Exception) {
36:                 val doubleCheck = adminUserRepository.findByUsername("admin")
37:                 if (doubleCheck != null) {
38:                     logger.info("Admin user already exists (inserted concurrently).")
39:                 } else {
40:                     throw e
41:                 }
42:             }
43:         } else {
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:265

```kotlin
265:     @GetMapping("/{contactId}/messages")
266:     fun messages(
267:         @PathVariable contactId: Long,
268:         @RequestParam(defaultValue = "50") limit: Int,
269:         @RequestParam(required = false) before: String?,
270:         @RequestParam(required = false) accountCode: String?
271:     ): ConversationMessageListResponse = conversationService.listMessages(
272:         contactId = contactId,
273:         accountCode = accountCode,
274:         beforeCursor = before,
275:         limit = limit
276:     )
277: 
```

## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:150

```kotlin
150:     fun countConversations(username: String, filter: ConversationFilter): Long {
151:         val eligibility = membership(filter)
152:         if (eligibility.impossible) return 0L
153:         val sql = """
154:             SELECT COUNT(*) FROM (
155:                 SELECT u.expert_contact_id AS expert_contact_id
156:                   FROM (${rangeUnionSql(includeBody = false)}) u
157:                   JOIN expert_contact ec ON ec.id = u.expert_contact_id
158:                  WHERE ${expertPredicates(username, filter, eligibility)}
159:                  GROUP BY u.expert_contact_id
160:                  ${havingClause(filter)}
161:             ) counted
162:         """.trimIndent()
163:         return jdbcTemplate.queryForObject(sql, params(username, filter), Long::class.java) ?: 0L
164:     }
165: 
166:     /**
167:      * 会话专家分页 summary（先 DB 聚合专家再分页）。每行只含聚合口径字段与最新事件
168:      * 时间，绝不含邮件正文；latestMessage/latestInbound/accountCodes/materialCount
169:      * 由调用方按本页 contact id 集合追加查询（[latestMessageByContacts] 等）。
170:      */
171:     fun pageConversations(
172:         username: String,
173:         filter: ConversationFilter,
174:         size: Int,
175:         offset: Long
176:     ): List<ConversationSummarySqlRow> {
177:         val eligibility = membership(filter)
178:         if (eligibility.impossible) return emptyList()
179:         val sql = """
180:             SELECT u.expert_contact_id AS expert_contact_id,
181:                    CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
182:                    CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
183:                    CONVERT(ec.orcid_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS orcid_id,
184:                    SUM(u.received_flag) AS received_count,
185:                    SUM(u.sent_flag) AS sent_count,
186:                    SUM(u.failed_flag) AS failed_count,
187:                    SUM(u.pending_flag) AS pending_count,
188:                    MAX(u.event_at) AS latest_event_at,
189:                    COALESCE((
190:                        SELECT ef.progress_status FROM expert_follow ef
191:                         WHERE ef.username = :username
192:                           AND ef.expert_contact_id = u.expert_contact_id
193:                    ), 'NONE') AS progress_status
194:               FROM (${rangeUnionSql(includeBody = false)}) u
195:               JOIN expert_contact ec ON ec.id = u.expert_contact_id
196:              WHERE ${expertPredicates(username, filter, eligibility)}
197:              GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
198:              ${havingClause(filter)}
199:              ORDER BY ${orderByClause(filter)}
200:              LIMIT :size OFFSET :offset
201:         """.trimIndent()
202:         val p = params(username, filter)
203:         p.addValue("size", size)
204:         p.addValue("offset", offset)
205:         return jdbcTemplate.query(sql, p) { rs, _ -> rs.toSummaryRow() }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:678

```kotlin
678:     private fun expertPredicates(username: String, filter: ConversationFilter, eligibility: MembershipEligibility): String {
679:         val clauses = mutableListOf<String>()
680:         clauses += """
681:             (:q IS NULL OR :q = ''
682:              OR ec.expert_name LIKE CONCAT('%', :q, '%')
683:              OR ec.expert_email LIKE CONCAT('%', :q, '%'))
684:         """.trimIndent()
685:         clauses += """
686:             (:followed = 0 OR EXISTS (
687:                 SELECT 1 FROM expert_follow eff
688:                  WHERE eff.username = :username
689:                    AND eff.expert_contact_id = u.expert_contact_id
690:                    AND eff.progress_status = 'FOLLOWING'))
691:         """.trimIndent()
692:         // 01 (I-4)：已提供与跟进中同层、互斥（同一行 progress_status 只能取一个值），
693:         // followed=true AND providedOnly=true 按 AND 得空集（不悄悄取其一）；参数化布尔
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:221

```kotlin
221:     fun listMessages(
222:         contactId: Long,
223:         accountCode: String?,
224:         beforeCursor: String?,
225:         limit: Int
226:     ): ConversationMessageListResponse {
227:         expertContactRepository.findById(contactId)
228:             .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
229:         val activeCodes = activeAccountCodes()
230:         val pageLimit = limit.coerceIn(1, MAX_MESSAGE_LIMIT)
231:         if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
232:             return ConversationMessageListResponse(emptyList(), null, false)
233:         }
234:         val before = decodeKeyset(beforeCursor, contactId, accountCode)
235:         val filter = ConversationFilter(accountCodes = activeCodes, accountCode = accountCode)
236:         val page = repository.timelineMessages(contactId, filter, before, pageLimit)
237: 
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:270

```kotlin
270: 
271:         // 行按 DESC（新→旧）返回；正序呈现最新窗口。
272:         val items = page.rows.asReversed().map { row ->
273:             val attachments = expertMaterialService.resolveMessageAttachments(row.source, row.id)
274:             ConversationMessageItemResponse(
275:                 source = row.source,
276:                 id = row.id,
277:                 contactId = row.expertContactId,
278:                 direction = row.direction,
279:                 accountCode = row.accountCode,
280:                 // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）；新收信已在
281:                 // 头读取时解码落库，普通文本二次解码是幂等 no-op。
282:                 subject = MailSubjectDecoder.decode(row.subject),
283:                 body = row.body,
284:                 cleanedBody = row.cleanedBody,
285:                 eventAt = row.eventAt.format(ISO),
286:                 sendStatus = row.sendStatus,
287:                 processStatus = row.processStatus,
288:                 attachmentCount = attachments.size,
289:                 firstAttachmentNames = attachments.take(FIRST_ATTACHMENT_NAME_LIMIT).mapNotNull { it.fileName },
290:                 messageId = row.messageId,
291:                 inReplyTo = row.inReplyTo,
292:                 tags = if (row.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING) {
293:                     tagsByProcessingId[row.id].orEmpty()
294:                 } else {
295:                     emptyList()
296:                 },
297:                 calendarAttachment = calendarAttachmentOf(row, mailRecordRowsById[row.id]),
298:                 // 06 (I-3)：同一批 findAllById 结果投影通用附件快照（零额外查询、不读磁盘）。
299:                 outboundAttachments = outboundAttachmentsOf(row, mailRecordRowsById[row.id])
300:             )
301:         }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:543

```kotlin
543:     fun activeAccountCodes(): List<String> =
544:         senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
545:             .map { it.accountCode }
546: }
```
