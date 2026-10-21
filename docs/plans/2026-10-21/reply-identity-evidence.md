# 称呼与签名：代码审计证据

采集日期：2026-10-21。基线 HEAD：`b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135`。证据来自当前工作区；不是线上已实现证明。行号用于该快照，实施前检查文件 SHA。以下命令均只读；退出码 1 的 rg 表示无匹配。

## E01 片段 schema

路径：`src/main/resources/db/migration/V47__create_reply_snippet.sql`

命令：`nl -ba src/main/resources/db/migration/V47__create_reply_snippet.sql | sed -n '1,40p'`

```text
1: CREATE TABLE reply_snippet (
2:     id BIGINT PRIMARY KEY AUTO_INCREMENT,
3:     snippet_type VARCHAR(32) NOT NULL,
4:     content TEXT NOT NULL,
5:     display_order INT NOT NULL DEFAULT 100,
6:     is_default TINYINT(1) NOT NULL DEFAULT 0,
7:     enabled TINYINT(1) NOT NULL DEFAULT 1,
8:     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
9:     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
10: );
11: 
12: INSERT INTO reply_snippet (snippet_type, content, display_order, is_default, enabled)
13: VALUES
14:     ('SALUTATION', 'Dear Professor,', 10, 1, 1),
15:     ('GREETING', 'Thank you for your email. Please find our answers below.', 10, 1, 1),
16:     ('CLOSING', 'Please let us know if you have any further questions.
17: 
18: Best regards,
19: Talent Introduction Team', 10, 1, 1),
20:     ('ACK', 'Thank you for sharing your CV.', 10, 0, 1),
21:     ('ACK', 'Thank you for sharing your materials.', 20, 0, 1),
22:     ('ACK', 'Thank you for your prompt reply.', 30, 0, 1);
```

## E02 默认选择与管理写入

路径：`src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt | sed -n '20,40p'`

```text
20:     fun listAll(): List<ReplySnippetDetail> =
21:         repository.findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc().map { toDetail(it) }
22: 
23:     fun listByType(snippetType: String): List<ReplySnippetDetail> {
24:         validateSnippetType(snippetType)
25:         return repository.findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType.uppercase())
26:             .map { toDetail(it) }
27:     }
28: 
29:     fun resolveManualFrame(): ManualReplyFrame =
30:         ManualReplyFrame(
31:             salutation = resolveDefaultText(SnippetType.SALUTATION),
32:             greeting = resolveDefaultText(SnippetType.GREETING),
33:             closing = resolveDefaultText(SnippetType.CLOSING),
34:             ackOptions = repository
35:                 .findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name)
36:                 .mapNotNull { snippet ->
37:                     val id = snippet.id ?: return@mapNotNull null
38:                     AckOption(id = id, content = snippet.content)
39:                 }
40:         )
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt | sed -n '58,120p'`

```text
58:     fun listSelectableFrameOptions(): List<ReplyFrameOption> =
59:         FRAME_SLOT_TYPES.flatMap { type ->
60:             repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(type.name)
61:                 .asSequence()
62:                 .filter { it.enabled && it.content.isNotBlank() }
63:                 .sortedWith(compareBy({ it.displayOrder }, { it.id ?: Long.MAX_VALUE }))
64:                 .mapNotNull { snippet ->
65:                     snippet.id?.let { id ->
66:                         ReplyFrameOption(
67:                             id = id,
68:                             snippetType = snippet.snippetType,
69:                             content = snippet.content,
70:                             displayOrder = snippet.displayOrder,
71:                             isDefault = snippet.isDefault
72:                         )
73:                     }
74:                 }
75:                 .toList()
76:         }
77: 
78:     /**
79:      * I-2 default frame: current enabled defaults for SALUTATION/GREETING/CLOSING,
80:      * ACK is never part of the default frame. A slot without an enabled default
81:      * resolves to null (absent), matching the legacy [resolveManualFrame] shape.
82:      */
83:     fun resolveDefaultSelectableFrame(): ResolvedReplyFrame {
84:         val selection = ReplyFrameSelection(
85:             salutationSnippetId = defaultSnippetId(SnippetType.SALUTATION),
86:             greetingSnippetId = defaultSnippetId(SnippetType.GREETING),
87:             ackSnippetId = null,
88:             closingSnippetId = defaultSnippetId(SnippetType.CLOSING)
89:         )
90:         return resolveSelectableFrame(selection)
91:     }
92: 
93:     /**
94:      * I-1 strict selectable-frame resolver: every non-null id must resolve to an
95:      * enabled snippet whose type matches the slot exactly and whose content is
96:      * non-blank, otherwise this fails closed with [IllegalArgumentException].
97:      * Null ids explicitly omit that slot; an all-null selection is a deliberate
98:      * "no frame" choice and never falls back to defaults (I-2).
99:      */
100:     fun resolveSelectableFrame(selection: ReplyFrameSelection): ResolvedReplyFrame {
101:         val salutation = resolveFrameSlot(selection.salutationSnippetId, SnippetType.SALUTATION)
102:         val greeting = resolveFrameSlot(selection.greetingSnippetId, SnippetType.GREETING)
103:         val ack = resolveFrameSlot(selection.ackSnippetId, SnippetType.ACK)
104:         val closing = resolveFrameSlot(selection.closingSnippetId, SnippetType.CLOSING)
105:         return ResolvedReplyFrame(
106:             selection = selection,
107:             version = frameVersion(salutation, greeting, ack, closing),
108:             salutation = salutation?.content,
109:             greeting = greeting?.content,
110:             acknowledgement = ack?.content,
111:             closing = closing?.content
112:         )
113:     }
114: 
115:     private fun defaultSnippetId(type: SnippetType): Long? =
116:         repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
117:             .asSequence()
118:             .filter { it.content.isNotBlank() }
119:             .sortedWith(compareBy({ it.displayOrder }, { it.id ?: Long.MAX_VALUE }))
120:             .firstNotNullOfOrNull { it.id }
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt | sed -n '181,303p'`

```text
181:     @Transactional
182:     fun create(command: ReplySnippetCreateCommand): ReplySnippetDetail {
183:         val snippetType = command.snippetType.uppercase()
184:         validateSnippetType(snippetType)
185:         require(command.content.isNotBlank()) { "content is required" }
186:         require(command.displayOrder > 0) { "displayOrder must be positive" }
187:         mailVariableService.requireValidTemplatePlaceholders(command.content)
188:         if (command.isDefault) {
189:             require(snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
190:             require(snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
191:         }
192:         contentVariantService.validateVariantTexts(command.content.trim(), command.variants, ContentVariantOwnerType.REPLY_SNIPPET)
193: 
194:         val now = LocalDateTime.now()
195:         val saved = repository.save(
196:             ReplySnippet(
197:                 snippetType = snippetType,
198:                 content = command.content.trim(),
199:                 displayOrder = command.displayOrder,
200:                 variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() },
201:                 name = command.name?.trim()?.takeIf { it.isNotBlank() },
202:                 isDefault = command.isDefault,
203:                 enabled = command.enabled,
204:                 createdAt = now,
205:                 updatedAt = now
206:             )
207:         )
208:         if (saved.isDefault) {
209:             clearOtherDefaults(saved)
210:         }
211:         val snippetId = saved.id ?: error("Reply snippet id is required")
212:         contentVariantService.replaceForOwner(
213:             ContentVariantOwnerType.REPLY_SNIPPET,
214:             snippetId,
215:             saved.content,
216:             command.variants
217:         )
218:         return toDetail(saved)
219:     }
220: 
221:     @Transactional
222:     fun update(id: Long, command: ReplySnippetUpdateCommand): ReplySnippetDetail {
223:         val existing = findById(id)
224:         require(command.content.isNotBlank()) { "content is required" }
225:         require(command.displayOrder > 0) { "displayOrder must be positive" }
226:         mailVariableService.requireValidTemplatePlaceholders(command.content)
227:         if (command.isDefault) {
228:             require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
229:             require(existing.snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
230:         }
231:         contentVariantService.validateVariantTexts(command.content.trim(), command.variants, ContentVariantOwnerType.REPLY_SNIPPET)
232: 
233:         val updated = repository.save(
234:             existing.copy(
235:                 content = command.content.trim(),
236:                 displayOrder = command.displayOrder,
237:                 variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() },
238:                 name = command.name?.trim()?.takeIf { it.isNotBlank() },
239:                 isDefault = command.isDefault,
240:                 enabled = command.enabled
241:             )
242:         )
243:         if (updated.isDefault) {
244:             clearOtherDefaults(updated)
245:         }
246:         contentVariantService.replaceForOwner(
247:             ContentVariantOwnerType.REPLY_SNIPPET,
248:             id,
249:             updated.content,
250:             command.variants
251:         )
252:         return toDetail(updated)
253:     }
254: 
255:     fun setEnabled(id: Long, enabled: Boolean): ReplySnippetDetail {
256:         val existing = findById(id)
257:         return toDetail(repository.save(existing.copy(enabled = enabled)))
258:     }
259: 
260:     @Transactional
261:     fun setDefault(id: Long): ReplySnippetDetail {
262:         val existing = findById(id)
263:         require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
264:         require(existing.snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
265:         require(existing.enabled) { "Disabled snippet cannot be default" }
266: 
267:         repository.findBySnippetTypeAndIsDefaultTrue(existing.snippetType)
268:             .filter { it.id != existing.id }
269:             .forEach { repository.save(it.copy(isDefault = false)) }
270: 
271:         return toDetail(repository.save(existing.copy(isDefault = true)))
272:     }
273: 
274:     @Transactional
275:     fun delete(id: Long) {
276:         findById(id)
277:         contentVariantService.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, id)
278:         repository.deleteById(id)
279:     }
280: 
281:     private fun toDetail(snippet: ReplySnippet): ReplySnippetDetail {
282:         val snippetId = snippet.id ?: error("Reply snippet id is required")
283:         return ReplySnippetDetail(
284:             snippet = snippet,
285:             variants = contentVariantService.listByOwner(ContentVariantOwnerType.REPLY_SNIPPET, snippetId)
286:                 .map { it.content }
287:         )
288:     }
289: 
290:     private fun resolveDefaultText(type: SnippetType): String? =
291:         repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
292:             .minByOrNull { it.displayOrder }
293:             ?.content
294:             ?.takeIf { it.isNotBlank() }
295: 
296:     private fun clearOtherDefaults(saved: ReplySnippet) {
297:         repository.findBySnippetTypeAndIsDefaultTrue(saved.snippetType)
298:             .filter { it.id != saved.id }
299:             .forEach { repository.save(it.copy(isDefault = false)) }
300:     }
301: 
302:     private fun findById(id: Long): ReplySnippet =
303:         repository.findById(id).orElseThrow { error("Reply snippet not found: $id") }
```

## E03 片段存储/消费者检索

命令：`rg -n 'reply_snippet|ReplySnippetRepository|replySnippetRepository\.|replySnippetService\.' src/main/kotlin src/main/resources/db scripts`

退出码：0

```text
src/main/resources/db/migration/V61__create_mail_compose_template.sql:16:    ref_id BIGINT NULL COMMENT 'qa_rule.id 或 reply_snippet.id，CUSTOM_TEXT 时为 NULL',
src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql:1:ALTER TABLE reply_snippet
src/main/resources/db/migration/V47__create_reply_snippet.sql:1:CREATE TABLE reply_snippet (
src/main/resources/db/migration/V47__create_reply_snippet.sql:12:INSERT INTO reply_snippet (snippet_type, content, display_order, is_default, enabled)
src/main/resources/db/migration/V64__add_subject_variants_and_snippet_variant_group.sql:2:ALTER TABLE reply_snippet ADD COLUMN variant_group VARCHAR(64) NULL COMMENT '变体组标识，同组 snippet 按确定性规则选一个';
src/main/kotlin/com/weibo/talentintroduction/reply/domain/ReplySnippet.kt:7:@Table("reply_snippet")
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:5:import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:16:    private val repository: ReplySnippetRepository,
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:354: * (I-3) and the authoritative per-slot text read fresh from [reply_snippet].
src/main/kotlin/com/weibo/talentintroduction/reply/repository/ReplySnippetRepository.kt:6:interface ReplySnippetRepository : CrudRepository<ReplySnippet, Long> {
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:13:import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:33:    private val replySnippetRepository: ReplySnippetRepository,
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:333:                val snippet = refId?.let { replySnippetRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:575:                replySnippetRepository.findById(refId).orElse(null)?.let { snippet ->
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:616:        val snippet = replySnippetRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:757:                    val snippet = replySnippetRepository.findById(refId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt:277:            replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt:279:            replySnippetService.resolveSelectableFrame(frameSelection)
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:730:        val frameOptions = replySnippetService.listSelectableFrameOptions().map { it.toTrustReplyFrameOption() }
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1150:            val resolved = replySnippetService.resolveSelectableFrame(selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1173:            val resolved = replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1180:            val resolved = replySnippetService.resolveSelectableFrame(snapshot.selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1200:            return replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1203:            val resolved = replySnippetService.resolveSelectableFrame(snapshot.selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:16:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:210:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:227:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:238:            replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let {
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2402:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2405:        replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let { appendLine("ACK=$it") }
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2584:        val frame = replySnippetService.resolveManualFrame()
```

## E04 变体写入、重复校验

路径：`src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt | sed -n '55,115p'`

```text
55:     fun listByOwner(ownerType: String, ownerId: Long): List<ContentVariant> =
56:         contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(ownerType, ownerId)
57: 
58:     fun validateVariantTexts(
59:         mainBody: String, variants: List<String>, ownerType: String = ContentVariantOwnerType.QA_RULE
60:     ) {
61:         validateVariants(mainBody, variants, ownerType)
62:     }
63: 
64:     @Transactional
65:     fun replaceForOwner(ownerType: String, ownerId: Long, mainBody: String, variants: List<String>) {
66:         require(ContentVariantOwnerType.isKnown(ownerType)) { "Unsupported content variant owner type: $ownerType" }
67:         validateVariants(mainBody, variants, ownerType)
68:         contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
69:         if (variants.isEmpty()) {
70:             return
71:         }
72:         val now = LocalDateTime.now()
73:         variants.forEachIndexed { index, raw ->
74:             contentVariantRepository.save(
75:                 ContentVariant(
76:                     ownerType = ownerType,
77:                     ownerId = ownerId,
78:                     variantOrder = index * 10 + 10,
79:                     content = raw.trim(),
80:                     enabled = true,
81:                     createdAt = now,
82:                     updatedAt = now
83:                 )
84:             )
85:         }
86:     }
87: 
88:     fun deleteForOwner(ownerType: String, ownerId: Long) {
89:         if (!ContentVariantOwnerType.isKnown(ownerType)) {
90:             return
91:         }
92:         contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
93:     }
94: 
95:     private fun validateVariants(mainBody: String, variants: List<String>, ownerType: String) {
96:         val trimmedMain = mainBody.trim()
97:         val seen = mutableSetOf<String>()
98:         variants.forEachIndexed { index, raw ->
99:             val trimmed = raw.trim()
100:             if (trimmed.isEmpty()) {
101:                 throw IllegalArgumentException("变体第 ${index + 1} 项不能为空")
102:             }
103:             if (trimmed == trimmedMain) {
104:                 throw IllegalArgumentException("变体不能与主体重复")
105:             }
106:             if (!seen.add(trimmed)) {
107:                 throw IllegalArgumentException("变体不能重复")
108:             }
109:             if (ownerType == ContentVariantOwnerType.REPLY_SNIPPET) {
110:                 mailPlaceholderService.requireValidTemplatePlaceholders(trimmed)
111:             } else {
112:                 mailPlaceholderService.requireValidPlaceholders(trimmed)
113:             }
114:         }
115:     }
```

## E05 变体调用路径

命令：`rg -n 'ContentVariantRepository|contentVariantRepository\.|replaceForOwner\(|deleteForOwner\(' src/main/kotlin scripts`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:130:        contentVariantService.deleteForOwner(ContentVariantOwnerType.QA_RULE, ruleId)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:212:        contentVariantService.replaceForOwner(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:246:        contentVariantService.replaceForOwner(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:277:        contentVariantService.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, id)
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:6:import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:15:    private val contentVariantRepository: ContentVariantRepository,
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:56:        contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:65:    fun replaceForOwner(ownerType: String, ownerId: Long, mainBody: String, variants: List<String>) {
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:68:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:74:            contentVariantRepository.save(
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:88:    fun deleteForOwner(ownerType: String, ownerId: Long) {
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:92:        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
src/main/kotlin/com/weibo/talentintroduction/variant/repository/ContentVariantRepository.kt:6:interface ContentVariantRepository : CrudRepository<ContentVariant, Long> {
```

## E06 最终发送与确认

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '194,210p'`

```text
194:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
195:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
196:         val contactId = record.expertContactId
197:             ?: error("Inbound mail not bound to a contact")
198:         val contact = expertContactRepository.findById(contactId)
199:             .orElseThrow { error("Expert contact not found: $contactId") }
200:         if (draftRef != null) {
201:             val bound = requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
202:                 authenticatedUsername.orEmpty(),draftRef,contactId,inboundProcessingId,
203:                 senderAccountCode?.takeIf { it.isNotBlank() } ?: record.senderAccountCode)
204:             if (bound != null) return completedDraftResult(contactId,requireNotNull(manualReplySendAttemptService.findCompletedAttempt(bound)))
205:         }
206: 
207:         require(subject.isNotBlank()) { "Subject is required" }
208:         require(htmlBody.isNotBlank()) { "HTML body is required" }
209:         val trimmedSubject = subject.trim()
210:         require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '417,455p'`

```text
417:         // I-4：已完成 attempt 先收敛 —— SENT 时直接返回原结果，绝不重查锚点/再次投递
418:         // （刚发出的信已成为最新成功发件也不得再次 SMTP）。
419:         // 06 (I-2)：本次或原记录任何一侧带通用附件时，先用 04 批量元数据校验归属
420:         // （同专家 + 同上传者，不要求原件仍在线），再按 filename/type/size/hash 有序语义
421:         // 比较：相同返回原 SENT；不同固定 409，绝不谎报新附件已发送、也不换 requestId。
422:         val completed = manualReplySendAttemptService.findCompletedByRequestId(
423:             contact.orcidId, canonicalRequestId
424:         )
425:         val trustedDraftAttempt = draftRef?.let {
426:             requireNotNull(mailReplyDraftService) { "Draft sending is not wired" }.completedBinding(
427:                 authenticatedUsername.orEmpty(),it,contactId,null,accountScope,completed?.attemptId)
428:         }
429:         if (completed != null && (draftRef == null || trustedDraftAttempt == completed.attemptId)) {
430:             val record = completed.mailRecord
431:             val originalSnapshots = OutboundAttachmentSnapshotCodec.parseOrThrow(
432:                 record.outboundAttachmentsJson
433:             )
434:             val requestedSnapshots = resolveRequestedAttachmentSnapshots(
435:                 contactId = contactId,
436:                 attachmentIds = attachmentIds,
437:                 authenticatedUsername = authenticatedUsername
438:             )
439:             if ((originalSnapshots != null || requestedSnapshots.isNotEmpty()) &&
440:                 !sameOutboundAttachmentSet(originalSnapshots.orEmpty(), requestedSnapshots)
441:             ) {
442:                 throw OutboundAttachmentException.conflict(CONVERSATION_SENT_ATTACHMENTS_CHANGED)
443:             }
444:             return PendingMailSendResult(
445:                 contactId = contactId,
446:                 senderAccountCode = record.senderAccountCode ?: completed.attemptAccountCode,
447:                 mailType = "MANUAL_RICH_REPLY",
448:                 subject = record.subject ?: subject,
449:                 sendStatus = "SENT",
450:                 messageId = record.messageId ?: completed.attemptMessageId
451:             )
452:         }
453:         val validatedDraft = draftRef?.let {
454:             requireNotNull(mailReplyDraftService).validateConversationSend(authenticatedUsername.orEmpty(),it,
455:                 contactId,accountScope,canonicalRequestId,anchorMailRecordId,subject,htmlBody,textBody,attachmentIds)
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '458,515p'`

```text
458:         // I-1：真实 SENT 出站锚点（排除空账号/模拟器）；accountScope 非空时只在该账号内找，
459:         // scope 下无成功发件不回退其他账号（I-6）。锚点查询在任何 claim/SMTP 之前。
460:         // 跟进（I-2）：显式 id 必须重读同一行并全校验，前端只作提示、绝不作为权限边界。
461:         val anchor = if (anchorMailRecordId != null) {
462:             mailRecordRepository.findById(anchorMailRecordId)
463:                 .orElse(null)
464:                 ?.takeIf { isConversationSentAnchor(it, contactId, accountScope) }
465:         } else {
466:             mailRecordRepository.findLatestSentOutboundAnchor(
467:                 contactId = contactId,
468:                 accountScope = accountScope?.takeIf { it.isNotBlank() },
469:                 excludedAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE
470:             )
471:         } ?: throw ResponseStatusException(
472:             HttpStatus.UNPROCESSABLE_ENTITY,
473:             "CONVERSATION_SENT_ANCHOR_NOT_FOUND"
474:         )
475:         val accountCode = requireNotNull(anchor.senderAccountCode) {
476:             "Conversation anchor mail record has no sender account"
477:         }
478:         val account = mailSenderAccountService.getManualSendAccount(accountCode)
479: 
480:         // I-7：Message-ID 仅在 trim 后非空且 <=255 时进入落库 inReplyTo 与 SMTP 线程头；
481:         // 缺失/超长不取消资格 —— 线程头为空仍发送（I-3：引用链只来自真实锚点）。
482:         val anchorMessageId = anchor.messageId
483:             ?.trim()
484:             ?.takeIf { it.isNotBlank() && it.length <= 255 }
485:         val smtpReferences = if (anchorMessageId != null) {
486:             listOfNotNull(anchor.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
487:                 .joinToString(" ")
488:         } else null
489: 
490:         val source = ManualRichSendSource(
491:             contact = contact,
492:             contactId = contactId,
493:             inboundProcessingId = null,
494:             inboundRecord = null,
495:             account = account,
496:             accountCode = accountCode,
497:             persistInReplyTo = anchorMessageId,
498:             smtpInReplyTo = anchorMessageId,
499:             smtpReferences = smtpReferences,
500:             anchorMailRecordId = requireNotNull(anchor.id) { "Conversation anchor mail record has no id" },
501:             requestId = canonicalRequestId
502:         )
503:         return executeManualRichSend(
504:             source = source,
505:             rawSubject = subject,
506:             htmlBody = htmlBody,
507:             textBody = textBody,
508:             operatorName = operatorName,
509:             safetyWarningConfirmed = safetyWarningConfirmed,
510:             strongConfirmationText = strongConfirmationText,
511:             evidence = ManualReplyEvidenceContext(runInboundSemanticChecks = false),
512:             attachmentIds = attachmentIds,
513:             authenticatedUsername = authenticatedUsername,
514:             draftSendRef = validatedDraft
515:         )
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '528,540p'`

```text
528:     private fun isConversationSentAnchor(
529:         record: MailRecord,
530:         contactId: Long,
531:         accountScope: String?
532:     ): Boolean {
533:         val accountCode = record.senderAccountCode
534:         return record.expertContactId == contactId &&
535:             record.direction == "OUTBOUND" &&
536:             record.sendStatus == "SENT" &&
537:             !accountCode.isNullOrBlank() &&
538:             accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE &&
539:             (accountScope.isNullOrBlank() || accountCode == accountScope)
540:     }
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '575,610p'`

```text
575:         mailVariableService.requireValidPlaceholders(trimmedSubject)
576:         val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
577:         require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
578:         require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }
579: 
580:         val rawText = evidence.templateTextBody?.takeIf { it.isNotBlank() }
581:             ?: textBody?.takeIf { it.isNotBlank() }
582:             ?: mailBodyCleaner.clean(htmlBody)
583:         val rawHtmlFromTemplate = evidence.templateHtmlBody?.takeIf { it.isNotBlank() }
584:         mailVariableService.requireValidPlaceholders(rawText)
585:         if (rawHtmlFromTemplate != null) {
586:             mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
587:         } else if (evidence.templateTextBody.isNullOrBlank()) {
588:             mailVariableService.requireValidPlaceholders(htmlBody)
589:         }
590: 
591:         val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
592:         // I-1/I-2/I-3：人工富文本最终正文必须在变量渲染之后、最终校验/安全确认/幂等指纹/
593:         // SMTP/落库/审计之前规范化一次；本方法后续所有读路径（finalValidationText、会议正文
594:         // 核对、SendPayload 指纹、ComposedMail 两个 MIME alternative、预览与归档）全部引用这
595:         // 一份 canonical final body，禁止检查原文却发送另一份正文（I-4）。
596:         val finalTextBody = mailContentService.normalizeManualTextLineBreaks(renderedText)
597:         val finalHtmlBody = when {
598:             rawHtmlFromTemplate != null ->
599:                 mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
600:             !evidence.templateTextBody.isNullOrBlank() ->
601:                 mailContentService.plainTextToHtml(renderedText)
602:             else ->
603:                 mailVariableService.renderHtmlForContact(htmlBody, source.account, contact)
604:         }.let { mailContentService.stripOpenTrackingImages(it) }
605:             .let { mailContentService.normalizeManualRichHtmlLineBreaks(it) }
606: 
607:         val finalValidationText = buildFinalValidationText(renderedSubject, finalTextBody, finalHtmlBody)
608:         require(finalValidationText.isNotBlank()) { "Final validation text is empty after rendering" }
609:         require(finalValidationText.length <= 20000) {
610:             "Final validation text exceeds 20,000 characters (${finalValidationText.length})"
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '673,740p'`

```text
673:         val findings = collectSafetyFindings(
674:             verificationText = finalValidationText,
675:             // 03b (I-47 ①): RAG 路径 carriesQa 显式传 false（RAG 不携带 QA 证据）。
676:             carriesQa = if (evidence.ragMode) false else evidence.carriesQa,
677:             canonicalFactIds = evidence.canonicalFactIds,
678:             contact = contact,
679:             inboundText = evidence.inboundText,
680:             researchProfileSufficient = evidence.researchProfileSufficient,
681:             operatorAuthorizedActions = evidence.operatorAuthorizedActions,
682:             degradedFactCodes = evidence.degradedFactCodes,
683:             // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection，禁止再次语义重筛。
684:             verifiedSelection = evidence.verifiedAssembly?.selection,
685:             // 03b (I-47): RAG 发送整段绕开 QA selection/trust-gap/intent 链。
686:             ragSend = evidence.ragMode,
687:             // T2.7: 会话回信路径在通用纯文本检查后返回，不跑来信语义检查。
688:             runInboundSemanticChecks = evidence.runInboundSemanticChecks
689:         )
690:         val requiresStrong = findings.any { it.severity == SafetySeverity.STRONG }
691:         if (findings.isNotEmpty() && !safetyWarningConfirmed) {
692:             throw ManualSendSafetyBlockedException(findings)
693:         }
694:         if (requiresStrong && strongConfirmationText?.trim() != "确认发送") {
695:             throw ManualSendSafetyBlockedException(findings)
696:         }
697: 
698:         // 06 (I-1)：通用附件在幂等 claim 之前解析 —— 04 按 (专家 + 上传者) 读元数据与原件、
699:         // 校验容量/尺寸/hash，失败以 400/404/409/413 直接抛出（位于下方投递 try 块之外，
700:         // 绝不烧掉 attempt，也绝不被归成 SMTP UNKNOWN）。安全确认重提会重新解析同一批 id，
701:         // 同一原件 → 同一快照 → 同一发送身份指纹。
702:         val attachmentFileSet = resolveOutboundAttachmentFileSet(
703:             contactId = source.contactId,
704:             attachmentIds = attachmentIds,
705:             authenticatedUsername = authenticatedUsername
706:         )
707: 
708:         val payload = ManualReplySendAttemptService.SendPayload(
709:             orcidId = contact.orcidId,
710:             contactId = source.contactId,
711:             inboundProcessingId = source.inboundProcessingId,
712:             accountCode = source.accountCode,
713:             normalizedRecipient = contact.expertEmail.lowercase().trim(),
714:             subject = renderedSubject,
715:             finalText = finalTextBody,
716:             finalHtml = finalHtmlBody,
717:             inReplyTo = source.persistInReplyTo,
718:             canonicalQaRuleIds = evidence.canonicalFactIds,
719:             primaryRuleId = if (evidence.ragMode) null else evidence.canonicalFactIds.firstOrNull(),
720:             // I-3：会话回信锚点只以真实 mail_record.id 表达，绝不伪造 inbound id。
721:             sourceAnchor = if (source.inboundProcessingId == null) {
722:                 requireNotNull(source.anchorMailRecordId) {
723:                     "Conversation rich reply requires a real anchor mail record"
724:                 }.let { "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$it" }
725:             } else null,
726:             idempotencyRequestId = source.requestId,
727:             // 03 (I-3): 与 ComposedMail 共用同一 01 快照实例，绝不独立生成第二份。
728:             calendarAttachment = calendarSnapshot,
729:             // fast-p 02 (I-1)：同一次重算产出的结构化排期输入；无会议恒 null。
730:             meetingEvent = meetingCalendarInput,
731:             // 06 (I-1)：与 ComposedMail.outboundAttachments 同一文件集合导出的有序快照；
732:             // 05 的 finalize 四分支据此写 mail_record.outbound_attachments_json。
733:             outboundAttachments = attachmentFileSet.snapshots
734:         ).also { it.draftSendRef = draftSendRef }
735: 
736:         val persistInReplyTo = source.persistInReplyTo
737:         if (persistInReplyTo != null && persistInReplyTo.length > 255) {
738:             throw ResponseStatusException(
739:                 HttpStatus.UNPROCESSABLE_ENTITY,
740:                 "inReplyTo exceeds 255 characters"
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | sed -n '782,810p'`

```text
782:                     val delivered = mailDeliveryService.send(source.account, mail)
783:                     val classification = classifyDelivery(delivered)
784: 
785:                     if (classification.isSent) {
786:                         val mailRecordId = try {
787:                             val id = manualReplySendAttemptService.finalizeSuccess(
788:                                 payload = payload,
789:                                 attemptId = claim.attemptId,
790:                                 messageId = claim.messageId
791:                             )
792:                             if (source.inboundProcessingId != null) {
793:                                 manualReplySendAttemptService.recordSendAudit(
794:                                     inboundProcessingId = source.inboundProcessingId,
795:                                     contactId = source.contactId,
796:                                     mailRecordId = id,
797:                                     canonicalFactIds = evidence.canonicalFactIds,
798:                                     carriesQa = evidence.carriesQa,
799:                                     delivered = delivered,
800:                                     sendSubject = renderedSubject,
801:                                     bodyPreviewText = bodyPreviewText,
802:                                     operatorName = operatorName,
803:                                     inboundRecord = requireNotNull(source.inboundRecord) {
804:                                         "Inbound rich reply audit requires the inbound processing record"
805:                                     },
806:                                     serverSuggestedFactIds = evidence.serverSuggestedFactIds,
807:                                     edited = evidence.edited,
808:                                     // 04 (I-1/I-7): 仅在该次发送存在 verified assembly 时附加诊断。
809:                                     trustReplyDiagnostics = evidence.verifiedAssembly?.response?.diagnostics,
810:                                     note = auditNote(inboundProcessingId = source.inboundProcessingId, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
```

## E07 异常响应事实

路径：`src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt | sed -n '34,56p'`

```text
34:     @ExceptionHandler(ManualSendSafetyBlockedException::class)
35:     fun handleManualSendSafetyBlocked(
36:         ex: ManualSendSafetyBlockedException
37:     ): ResponseEntity<ManualSendSafetyBlockedResponse> {
38:         val findings = ex.findings
39:         val truncated = findings.size > 20
40:         return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
41:             ManualSendSafetyBlockedResponse(
42:                 message = "发送内容安全校验未通过，共 ${findings.size} 项，请在界面上逐条确认",
43:                 detail = HttpStatus.UNPROCESSABLE_ENTITY.reasonPhrase,
44:                 requiresStrongConfirmation = findings.any { it.severity == SafetySeverity.STRONG },
45:                 truncated = truncated,
46:                 findings = findings.take(20).map { finding ->
47:                     SafetyFindingResponse(
48:                         code = finding.code,
49:                         severity = finding.severity.name,
50:                         sentence = finding.sentence?.takeIf { it.isNotEmpty() }?.let { sentence ->
51:                             if (sentence.length > 200) sentence.take(199) + "…" else sentence
52:                         }
53:                     )
54:                 }
55:             )
56:         )
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt | sed -n '82,84p'`

```text
82:     @ExceptionHandler(Exception::class)
83:     fun handleException(ex: Exception): ResponseEntity<ApiErrorResponse> =
84:         error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.message ?: "Internal server error")
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt | sed -n '111,124p'`

```text
111: data class ManualSendSafetyBlockedResponse(
112:     val code: String = "MANUAL_SEND_SAFETY_BLOCKED",
113:     val message: String,
114:     val detail: String?,
115:     val requiresStrongConfirmation: Boolean,
116:     val truncated: Boolean,
117:     val findings: List<SafetyFindingResponse>
118: )
119: 
120: data class SafetyFindingResponse(
121:     val code: String,
122:     val severity: String,
123:     val sentence: String?
124: )
```

## E08 真实变量与资料缺失

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt | sed -n '53,96p'`

```text
53:     fun resolveExpertFullName(expertProfile: ExpertProfile?, contact: ExpertContact? = null): String {
54:         if (expertProfile == null) return ""
55:         val given = expertProfile.givenNames?.trim().orEmpty()
56:         val family = expertProfile.familyNames?.trim().orEmpty()
57:         val profileOrcid = expertProfile.orcidId?.trim().orEmpty()
58:         val profileEmail = expertProfile.email?.trim().orEmpty()
59:         val profileEsDocId = expertProfile.esDocId?.trim().orEmpty()
60:         val contactOrcid = contact?.orcidId?.trim().orEmpty()
61:         val contactEmail = contact?.expertEmail?.trim().orEmpty()
62: 
63:         fun isKnownTechId(name: String): Boolean =
64:             isTechnicalId(name) ||
65:                 name.equals(profileOrcid, ignoreCase = true) ||
66:                 name.equals(profileEmail, ignoreCase = true) ||
67:                 name.equals(profileEsDocId, ignoreCase = true) ||
68:                 name.equals(contactOrcid, ignoreCase = true) ||
69:                 name.equals(contactEmail, ignoreCase = true)
70: 
71:         val fullName = buildString {
72:             if (given.isNotBlank() && !isKnownTechId(given)) append(given)
73:             if (family.isNotBlank() && !isKnownTechId(family)) {
74:                 if (isNotEmpty()) append(" ")
75:                 append(family)
76:             }
77:         }.trim()
78:         return if (fullName.isNotBlank() && !isKnownTechId(fullName)) fullName else ""
79:     }
80: 
81:     fun resolveFamilyName(expertProfile: ExpertProfile?, contact: ExpertContact? = null): String {
82:         val family = expertProfile?.familyNames?.trim().orEmpty()
83:         if (family.isBlank()) return ""
84:         if (isTechnicalId(family)) return ""
85:         val profileOrcid = expertProfile?.orcidId?.trim().orEmpty()
86:         val profileEmail = expertProfile?.email?.trim().orEmpty()
87:         val profileEsDocId = expertProfile?.esDocId?.trim().orEmpty()
88:         val contactOrcid = contact?.orcidId?.trim().orEmpty()
89:         val contactEmail = contact?.expertEmail?.trim().orEmpty()
90:         if (family.equals(profileOrcid, ignoreCase = true) ||
91:             family.equals(profileEmail, ignoreCase = true) ||
92:             family.equals(profileEsDocId, ignoreCase = true) ||
93:             family.equals(contactOrcid, ignoreCase = true) ||
94:             family.equals(contactEmail, ignoreCase = true)
95:         ) return ""
96:         return family
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt | sed -n '119,164p'`

```text
119:     fun buildVariables(
120:         account: MailSenderAccount?,
121:         expert: ExpertProfile?,
122:         unsubscribeEmail: String? = expert?.email,
123:         previewFallbacks: Boolean = false,
124:         contact: ExpertContact? = null
125:     ): Map<String, String> {
126:         val senderVars = mapOf(
127:             "senderEmail" to (account?.senderEmail).orEmpty(),
128:             "senderName" to (account?.senderName).orEmpty(),
129:             "senderTitle" to account?.senderTitle.orEmpty(),
130:             "teamName" to account?.teamName.orEmpty(),
131:             "countryName" to account?.countryName.orEmpty()
132:         )
133:         val expertVars = if (expert != null) {
134:             mapOf(
135:                 "expertName" to resolveExpertName(expert, contact),
136:                 "expertFamilyName" to ExpertRecipientNamePolicy.resolveFamilyName(expert, contact),
137:                 "researchFields" to expert.researchFields.orEmpty(),
138:                 "institution" to expert.institution.orEmpty(),
139:                 "keyword" to expert.keyword.orEmpty(),
140:                 "expertCountry" to expert.country.orEmpty(),
141:                 "employment" to expert.employment.orEmpty(),
142:                 "hIndex" to (expert.hIndex?.toString()).orEmpty(),
143:                 "worksCount" to (expert.worksCount?.toString()).orEmpty(),
144:                 "lastPublicationYear" to (expert.lastPublicationYear?.toString()).orEmpty(),
145:                 "degree" to expert.degree.orEmpty(),
146:                 "recentWorkTitle" to (expert.recentWorkTitles?.firstOrNull()).orEmpty(),
147:                 "patentTitle" to (expert.patentTitles?.firstOrNull()).orEmpty(),
148:                 "primaryResearchField" to (expert.researchFields
149:                     ?.split(", ")
150:                     ?.firstOrNull()
151:                     ?.trim())
152:                     .orEmpty()
153:             )
154:         } else {
155:             PROFILE_KEYS.associateWith { "" }
156:         }
157:         val materialVars = mapOf(
158:             "pendingExpertMaterials" to renderPendingMaterials(contact)
159:         )
160:         val unsubscribeVars = mapOf(
161:             "unsubscribeUrl" to unsubscribeUrl(unsubscribeEmail, previewFallbacks)
162:         )
163:         return senderVars + expertVars + materialVars + unsubscribeVars
164:     }
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt | sed -n '187,215p'`

```text
187:     fun renderForContact(text: String, account: MailSenderAccount?, contact: ExpertContact): String =
188:         renderContact(text, account, contact, previewFallbacks = false).rendered
189: 
190:     fun renderHtmlForContact(html: String, account: MailSenderAccount?, contact: ExpertContact): String {
191:         val expert = resolveExpertProfileFor(contact)
192:         val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks = false, contact = contact)
193:             .mapValues { (_, value) -> HtmlUtils.htmlEscape(value) }
194:         return mailComposeTemplateService.renderWithVariables(html, variables)
195:     }
196: 
197:     fun renderPreview(text: String, account: MailSenderAccount?, contact: ExpertContact): RenderPreviewResult =
198:         renderContact(text, account, contact, previewFallbacks = true)
199: 
200:     private fun renderContact(
201:         text: String,
202:         account: MailSenderAccount?,
203:         contact: ExpertContact,
204:         previewFallbacks: Boolean
205:     ): RenderPreviewResult {
206:         val expert = resolveExpertProfileFor(contact)
207:         val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks, contact = contact)
208:         val rendered = mailComposeTemplateService.renderWithVariables(text, variables)
209:         val fallbackKeys = detectFallbackKeys(text, variables)
210:         return RenderPreviewResult(
211:             rendered = rendered,
212:             fallbackKeys = fallbackKeys,
213:             variables = buildPreviewVariables(text, variables, fallbackKeys),
214:             invalidTokens = unknownPlaceholderTokens(text)
215:         )
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt | sed -n '284,308p'`

```text
284:     fun resolveExpertProfileFor(contact: ExpertContact): ExpertProfile? {
285:         val orcidId = contact.orcidId.takeIf { it.isNotBlank() } ?: return null
286:         return try {
287:             val level = parseIndexLevel(contact.currentIndexLevel)
288:             val profile = expertSearchService.findByOrcidId(orcidId, level)
289:             if (profile != null) {
290:                 return profile
291:             }
292:             if (level == ExpertIndexLevel.APPLICATION) {
293:                 expertSearchService.findByOrcidId(orcidId, ExpertIndexLevel.CANDIDATE)
294:             } else {
295:                 null
296:             }
297:         } catch (e: Exception) {
298:             log.warn("Failed to resolve expert profile for orcidId={}: {}", orcidId, e.message)
299:             null
300:         }
301:     }
302: 
303:     private fun parseIndexLevel(level: String): ExpertIndexLevel =
304:         runCatching { ExpertIndexLevel.valueOf(level) }.getOrDefault(ExpertIndexLevel.CANDIDATE)
305: 
306:     private fun resolveExpertName(expert: ExpertProfile, contact: ExpertContact? = null): String {
307:         return ExpertRecipientNamePolicy.resolveExpertFullName(expert, contact)
308:     }
```

## E09 模板占位符语法

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt | sed -n '41,63p'`

```text
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
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt | sed -n '83,127p'`

```text
83:      * I-1: keys that gate a send from [text] — every `${key}` without a default and
84:      * every `${key|<blank>}`. `${key|non-blank default}` never gates. First-occurrence
85:      * order, deduplicated. This is the single source of the gate; the legacy
86:      * `required_keys` column is never consulted.
87:      */
88:     fun requiredKeysIn(text: String): List<String> {
89:         if (text.isEmpty()) {
90:             return emptyList()
91:         }
92:         val keys = linkedSetOf<String>()
93:         PLACEHOLDER_REGEX.findAll(text).forEach { match ->
94:             val parsed = parsePlaceholderToken(match.groupValues[1])
95:             if (parsed.gates && parsed.key.isNotBlank()) {
96:                 keys.add(parsed.key)
97:             }
98:         }
99:         return keys.toList()
100:     }
101: 
102:     /**
103:      * I-1 save-time validation for compose templates: unknown keys, blank defaults and
104:      * broken `${` fragments. Deliberately looser than [validatePlaceholders], which
105:      * keeps rejecting bare nullable expert variables in QA rules and reply snippets.
106:      */
107:     fun templatePlaceholderViolations(text: String): List<String> {
108:         if (text.isEmpty()) {
109:             return emptyList()
110:         }
111:         val metaByKey = variableMetadata().associateBy { it.key }
112:         val violations = linkedSetOf<String>()
113:         PLACEHOLDER_REGEX.findAll(text).forEach { match ->
114:             val parsed = parsePlaceholderToken(match.groupValues[1])
115:             if (metaByKey[parsed.key] == null || parsed.blankDefault) {
116:                 violations.add(match.value)
117:             }
118:         }
119:         violations.addAll(brokenTokens(text))
120:         return violations.toList()
121:     }
122: 
123:     fun requireValidTemplatePlaceholders(text: String) {
124:         val violations = templatePlaceholderViolations(text)
125:         require(violations.isEmpty()) {
126:             "Invalid template placeholders: ${violations.joinToString(", ")}"
127:         }
```

## E10 HTML解析现状及可用JDK先例

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt | sed -n '25,33p'`

```text
25:     fun htmlToPlainText(html: String): String =
26:         html.replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), "")
27:             .replace(Regex("(?i)<br\\s*/?>"), "\n")
28:             .replace(Regex("(?i)</p>"), "\n\n")
29:             .replace(Regex("<[^>]+>"), "")
30:             .let { unescapeHtmlEntities(it) }
31:             .replace(Regex("[ \\t]+"), " ")
32:             .replace(Regex("\\n{3,}"), "\n\n")
33:             .trim()
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt | sed -n '40,69p'`

```text
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
```

## E10b JDK解析先例

路径：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt | sed -n '188,240p'`

```text
188:             for (part in parts) if (part is Node) yieldAll(part.descendants())
189:         }
190:         fun visibleText(): String {
191:             if (tag in setOf("script", "style", "head", "sup")) return ""
192:             val body = parts.joinToString(" ") { if (it is Node) it.visibleText() else it.toString() }
193:             return if (tag in setOf("p", "div", "li", "tr", "td", "br") || isAuthor()) "\n$body\n" else body
194:         }
195:     }
196: 
197:     private fun parseHtml(html: String): Node {
198:         val root = Node("root")
199:         val stack = mutableListOf(root)
200:         ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
201:             private fun node(tag: HTML.Tag, attrs: MutableAttributeSet): Node {
202:                 val attributes = mutableMapOf<String, String>()
203:                 val keys = attrs.attributeNames
204:                 while (keys.hasMoreElements()) {
205:                     val key = keys.nextElement()
206:                     attributes[key.toString()] = attrs.getAttribute(key).toString()
207:                 }
208:                 return Node(tag.toString(), attributes)
209:             }
210:             override fun handleStartTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
211:                 val child = node(tag, attrs)
212:                 stack.last().parts += child
213:                 stack += child
214:             }
215:             override fun handleEndTag(tag: HTML.Tag, pos: Int) {
216:                 val index = stack.indexOfLast { it.tag == tag.toString() }
217:                 if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
218:             }
219:             override fun handleSimpleTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
220:                 stack.last().parts += node(tag, attrs)
221:             }
222:             override fun handleText(data: CharArray, pos: Int) { stack.last().parts += String(data) }
223:         }, true)
224:         return root
225:     }
226: }
```

## E11 发送服务手工构造点

命令：`rg -n 'PendingMailOperationService\(' src/test`

退出码：0

```text
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:137:    private val service = PendingMailOperationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt:130:        pending = PendingMailOperationService(inbound,contacts,mock(ExpertOperatorStatusService::class.java),
src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt:212:        pendingService = PendingMailOperationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt:183:        pendingMailOperationService = PendingMailOperationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:120:    private val service = PendingMailOperationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:107:    private val service = PendingMailOperationService(
```

## E12 跟进引文文本与日期

路径：`src/main/resources/static/mailbox-chat.js`

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '346,354p'`

```text
346:     function datePart(iso) {
347:         return String(iso || "").slice(0, 10);
348:     }
349: 
350:     function timePart(iso) {
351:         const value = String(iso || "");
352:         const idx = value.indexOf("T");
353:         return idx >= 0 ? value.slice(idx + 1, idx + 6) : "";
354:     }
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '509,571p'`

```text
509:     /** 与 MailSenderAccountService.SIMULATOR_ACCOUNT_CODE 同值：模拟器发件永不作为候选。 */
510:     const FOLLOWUP_SIMULATOR_ACCOUNT = "SIMULATOR_NOOP";
511:     const FOLLOWUP_HTMLISH = /<[a-z!/][^>]*>/i;
512:     const FOLLOWUP_ENTITY = /&(?:amp|lt|gt|quot|apos|nbsp|#\d+|#x[0-9a-fA-F]+);/g;
513: 
514:     const FOLLOWUP_BODY_LINES = {
515:         video: "Just following up on my email below about a brief Zoom call. Would you be available sometime this week or next? We’re happy to work around your time zone.",
516:         meetingReminder: "This is a courteous reminder of our scheduled meeting. We would be honored by your participation at the appointed time.",
517:         cv: "Just following up on my note below. When convenient, could you please send your CV? It will help us identify suitable industry partners.",
518:         generic: "I hope you’re doing well. I wanted to follow up on my previous email and would be happy to continue our conversation. Please feel free to share any thoughts or questions you may have. I look forward to hearing from you."
519:     };
520: 
521:     function quotePlainTextFromSource(raw) {
522:         const value = raw == null ? "" : String(raw);
523:         if (!value) return "";
524:         if (!FOLLOWUP_HTMLISH.test(value)) return finalizeQuotePlainText(value);
525:         const withBlockBreaks = value
526:             .replace(/<br\s*\/?>/gi, "\n")
527:             .replace(/<\/li\s*>/gi, "\n")
528:             .replace(/<\/(p|div|blockquote|h[1-6])\s*>/gi, "\n\n");
529:         return finalizeQuotePlainText(htmlFragmentToPlainText(withBlockBreaks));
530:     }
531: 
532:     /**
533:      * 惰性文档取纯文本（I-6）：优先 `DOMParser.parseFromString(...).body.textContent`；
534:      * 沙箱没有 DOMParser 时剥标签后解实体 —— 两者在同一输入上同结果（块级边界已在上一步
535:      * 转成换行，故 textContent 不会吞掉段落）。
536:      */
537:     function htmlFragmentToPlainText(html) {
538:         if (typeof global.DOMParser === "function") {
539:             try {
540:                 const parsed = new global.DOMParser().parseFromString(html, "text/html");
541:                 if (parsed && parsed.body) return String(parsed.body.textContent || "");
542:             } catch (e) { /* 退回文本剥离 */ }
543:         }
544:         return decodeQuoteEntities(html.replace(/<[^>]*>/g, ""));
545:     }
546: 
547:     function decodeQuoteEntities(value) {
548:         return String(value == null ? "" : value).replace(FOLLOWUP_ENTITY, (entity) => {
549:             const name = entity.slice(1, -1).toLowerCase();
550:             if (name === "amp") return "&";
551:             if (name === "lt") return "<";
552:             if (name === "gt") return ">";
553:             if (name === "quot") return "\"";
554:             if (name === "apos") return "'";
555:             if (name === "nbsp") return " ";
556:             if (name.charAt(0) === "#") {
557:                 const code = name.charAt(1) === "x" ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10);
558:                 if (Number.isFinite(code) && code > 0) return String.fromCharCode(code);
559:             }
560:             return entity;
561:         });
562:     }
563: 
564:     function finalizeQuotePlainText(value) {
565:         return String(value == null ? "" : value)
566:             .replace(/\r\n?/g, "\n")
567:             .split("\n")
568:             .map((line) => line.replace(/[ \t]+$/, ""))
569:             .join("\n")
570:             .replace(/\n{3,}/g, "\n\n")
571:             .trim();
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '6699,6727p'`

```text
6699:         /** I-6：引用源优先非空 cleanedBody，否则 body。 */
6700:         function followupSourceText(item) {
6701:             const cleaned = item ? item.cleanedBody : null;
6702:             if (cleaned != null && String(cleaned).trim() !== "") return String(cleaned);
6703:             return item && item.body != null ? String(item.body) : "";
6704:         }
6705: 
6706:         /** I-6：主题/引用头/引用正文同源；引用头固定 `On YYYY-MM-DD HH:mm, <accountCode> wrote:`。 */
6707:         function followupQuoteText(item) {
6708:             const when = `${datePart(item.eventAt)} ${timePart(item.eventAt)}`.trim();
6709:             return `On ${when}, ${String(item.accountCode || "")} wrote:\n\n`
6710:                 + quotePlainTextFromSource(followupSourceText(item));
6711:         }
6712: 
6713:         /** I-5：称呼优先复用所选邮件纯文本首个非空行（只接受 Dear/Hi 且 <=100 字符）。 */
6714:         function followupGreeting(item) {
6715:             const plain = quotePlainTextFromSource(followupSourceText(item));
6716:             const first = plain.split("\n").map((line) => line.trim()).find((line) => line !== "") || "";
6717:             if (/^(Dear|Hi)(\s|,)/.test(first) && first.length <= 100) return first;
6718:             return "Dear Professor,";
6719:         }
6720: 
6721:         function followupBodyText(item, copy) {
6722:             const line = FOLLOWUP_BODY_LINES[copy];
6723:             if (!line) return "";
6724:             const getSenderName = hostFn("mcHostGetSenderName");
6725:             const senderName = getSenderName ? String(getSenderName(item.accountCode) || "").trim() : "";
6726:             return `${followupGreeting(item)}\n\n${line}\n\nBest regards,${senderName ? `\n${senderName}` : ""}`;
6727:         }
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '6903,6945p'`

```text
6903:             if (applyButton) applyButton.disabled = !(item && selected);
6904:         }
6905: 
6906:         /** I-7：填入是全文替换（清 QA/会议快照与会议正文块），并写入所选锚点。 */
6907:         function applyFollowUpFromDialog() {
6908:             const key = currentTargetKey();
6909:             const dialog = followupDialogEl();
6910:             if (!key || !dialog || !instance.followup.open || instance.followup.targetKey !== key) return;
6911:             const item = instance.followup.selectedId == null
6912:                 ? null
6913:                 : followupItemById(instance.followup.selectedId);
6914:             if (!item || !Object.prototype.hasOwnProperty.call(FOLLOWUP_BODY_LINES, instance.followup.selectedCopy)) return;
6915:             const composeEl = manualComposeEl();
6916:             const inputs = manualInputs(composeEl);
6917:             if (!inputs) return;
6918:             const subjectInput = dialog.querySelector('[data-role="followup-subject"]');
6919:             const bodyInput = dialog.querySelector('[data-role="followup-body"]');
6920:             const typedSubject = subjectInput ? String(subjectInput.value || "").trim() : "";
6921:             const typedBody = bodyInput ? String(bodyInput.value || "") : "";
6922:             inputs.subjectInput.value = typedSubject || chatSubjectPrefill(item.subject);
6923:             // 全文替换：原编辑器内容（含任何会议正文块）整体消失。
6924:             inputs.editor.innerText = `${typedBody}\n\n${followupQuoteText(item)}`;
6925:             instance.manual.qa = null;
6926:             instance.meeting.editorRevision += 1;
6927:             saveDraftFromInputs({
6928:                 meeting: null,
6929:                 meetingAccountCode: "",
6930:                 followUpAnchorMailRecordId: Number(item.id)
6931:             });
6932:             refreshMeetingAttachmentCard();
6933:             refreshFollowupAnchorNote();
6934:             closeFollowUpDialog({ restoreFocus: true });
6935:             saveConversationState();
6936:             const manualSection = composeEl && composeEl.closest
6937:                 ? composeEl.closest('.mc-section[data-section="manual"]')
6938:                 : null;
6939:             if (manualSection && !manualSection.open && manualSection.setAttribute) {
6940:                 manualSection.setAttribute("open", "");
6941:             }
6942:             hostShowStatus("已填入跟进回复草稿，发送前请确认", "ok");
6943:         }
6944: 
6945:         /** 只关闭弹窗：不触碰主题/正文/QA/会议/锚点（I-7）。 */
```

## E12b 引文源字段

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt | sed -n '534,560p'`

```text
534:     private fun rangeUnionSql(includeBody: Boolean): String {
535:         val outboundBody = if (includeBody) {
536:             """,
537:                    CONVERT(mr.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
538:                    CONVERT(mr.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
539:         } else ""
540:         val inboundBody = if (includeBody) {
541:             """,
542:                    CONVERT(imp.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
543:                    CONVERT(imp.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
544:         } else ""
545:         return """
546:             SELECT mr.expert_contact_id AS expert_contact_id,
547:                    1 AS source_rank,
548:                    CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
549:                    mr.id AS id,
550:                    CONVERT('OUTBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
551:                    CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
552:                    COALESCE(mr.sent_at, mr.created_at) AS event_at,
553:                    CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
554:                    CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
555:                    CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
556:                    CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
557:                    CONVERT(mr.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
558:                    CONVERT(mr.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
559:                    CASE WHEN mr.send_status = 'SENT' THEN 1 ELSE 0 END AS sent_flag,
560:                    CASE WHEN mr.send_status = 'FAILED' THEN 1 ELSE 0 END AS failed_flag,
```

## E13 双发送确认及共享dialog

路径：`src/main/resources/static/app.js`

命令：`nl -ba src/main/resources/static/app.js | sed -n '18171,18248p'`

```text
18171:     async function submitManualRichReply(recordId, requestBody, safetyWarningConfirmed = false) {
18172:         try {
18173:             const result = await api(`/api/mail/unmatched-inbound/${recordId}/manual-rich-reply`, {
18174:                 method: "POST",
18175:                 body: JSON.stringify(requestBody)
18176:             });
18177:             manualReplyQaContext = null;
18178:             aiReplyState.adoptContext = null;
18179:             resetPreflightState();
18180:             const archiveStatus = result.unsupportedAnswerArchiveStatus || "NOT_APPLICABLE";
18181:             const archivedCount = Number(result.unsupportedAnswerArchivedCount) || 0;
18182:             if (archiveStatus === "SAVED") {
18183:                 const suffix = archivedCount > 0 ? `，已记录 ${archivedCount} 条无依据回答` : "";
18184:                 alert(`人工回复邮件发送成功${suffix}`);
18185:                 showStatus(`人工回复邮件发送成功${suffix}`, "ok");
18186:             } else if (archiveStatus === "PARTIAL" || archiveStatus === "FAILED") {
18187:                 alert("人工回复邮件发送成功\n无依据回答索引未完整写入，请勿重复发送");
18188:                 showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn");
18189:             } else {
18190:                 alert("人工回复邮件发送成功");
18191:             }
18192:         } catch (e) {
18193:             const canConfirmSafety = !safetyWarningConfirmed &&
18194:                 e.data?.code === "MANUAL_SEND_SAFETY_BLOCKED" &&
18195:                 Array.isArray(e.data.findings) &&
18196:                 e.data.findings.length > 0;
18197:             if (canConfirmSafety) {
18198:                 const findings = e.data.findings;
18199:                 const renderFindings = (list) => list.map((finding) => {
18200:                     const severityClass = finding.severity === "STRONG" ? "ai-reply-error" : "ai-reply-warning";
18201:                     const label = AI_REPLY_WARNING_LABELS[finding.code] || "正文包含需人工核对的风险声明";
18202:                     const coverage = finding.sentence
18203:                         ? `<div class="ai-reply-coverage">命中原句：${escapeHtml(finding.sentence)}</div>`
18204:                         : "";
18205:                     return `<div class="${severityClass}">${escapeHtml(label)}</div>${coverage}`;
18206:                 }).join("");
18207:                 const firstConfirmed = await openActionDialog("confirm", {
18208:                     message: `<p>本次发送命中 ${findings.length} 项内容安全门禁，请逐条核对后确认：</p><div class="ai-reply-feedback">${renderFindings(findings)}</div><p>确认已人工核对，仍要发送吗？</p>`
18209:                 });
18210:                 if (!firstConfirmed) {
18211:                     alert("人工回复发送失败: " + e.message);
18212:                     throw e;
18213:                 }
18214:                 let strongConfirmationText = null;
18215:                 if (e.data.requiresStrongConfirmation === true) {
18216:                     const strongFindings = findings.filter((finding) => finding.severity === "STRONG");
18217:                     const secondConfirmed = await openActionDialog("confirm-typed", {
18218:                         message: `<div class="ai-reply-error">高风险：本封邮件正文向专家索取护照 / 身份证 / 在职证明 / 银行流水一类敏感证件材料。此类索取存在合规与信任风险，一经发出不可撤回。</div><div class="ai-reply-feedback">${renderFindings(strongFindings)}</div><p>确认要发送，请在下方输入框中逐字输入「确认发送」四个字。</p>`
18219:                     });
18220:                     if (!secondConfirmed) {
18221:                         alert("人工回复发送失败: " + e.message);
18222:                         throw e;
18223:                     }
18224:                     strongConfirmationText = "确认发送";
18225:                 }
18226:                 const retryBody = { ...requestBody, safetyWarningConfirmed: true };
18227:                 if (strongConfirmationText !== null) {
18228:                     retryBody.strongConfirmationText = strongConfirmationText;
18229:                 }
18230:                 return submitManualRichReply(recordId, retryBody, true);
18231:             }
18232:             alert("人工回复发送失败: " + e.message);
18233:             throw e;
18234:         }
18235:         await showUnmatchedDetail(recordId);
18236:         await refreshMailboxAfterPendingAction();
18237:     }
18238: 
18239:     if (action === "send-manual-rich-reply") {
18240:         const editor = $("#manualRichReplyEditor");
18241:         const subject = $("#manualReplySubject")?.value?.trim();
18242:         if (!subject) {
18243:             showStatus("请输入邮件主题", "error");
18244:             return;
18245:         }
18246:         if (!editor || !editor.innerHTML.trim()) {
18247:             showStatus("请输入邮件正文", "error");
18248:             return;
```

命令：`nl -ba src/main/resources/static/app.js | sed -n '20344,20358p'`

```text
20344:     "confirm": {
20345:         title: "确认操作",
20346:         fields: [
20347:             { name: "message", label: "", type: "html", required: false }
20348:         ]
20349:     },
20350:     "confirm-typed": {
20351:         title: "高风险发送二次确认",
20352:         fields: [
20353:             { name: "message", label: "", type: "html", required: false },
20354:             { name: "confirmText", label: "请输入「确认发送」", type: "text", required: true, placeholder: "确认发送" }
20355:         ],
20356:         validate: (result) => (String(result.confirmText || "").trim() === "确认发送" ? null : "输入不匹配，请逐字输入「确认发送」四个字。")
20357:     }
20358: };
```

命令：`nl -ba src/main/resources/static/app.js | sed -n '20423,20467p'`

```text
20423:         const handleCancel = () => {
20424:             cleanup();
20425:             resolve(null);
20426:         };
20427: 
20428:         const handleSubmit = (e) => {
20429:             e.preventDefault();
20430:             const result = {};
20431:             fields.forEach(field => {
20432:                 const el = document.getElementById(`dialog_${field.name}`);
20433:                 if (!el) return;
20434:                 if (field.type === "checkbox") {
20435:                     result[field.name] = el.checked;
20436:                 } else {
20437:                     result[field.name] = el.value;
20438:                 }
20439:             });
20440:             if (schema.validate) {
20441:                 const validationError = schema.validate(result);
20442:                 if (validationError) {
20443:                     const errorEl = document.getElementById("dialog_validationError");
20444:                     if (errorEl) {
20445:                         errorEl.textContent = validationError;
20446:                         errorEl.hidden = false;
20447:                     }
20448:                     return;
20449:                 }
20450:             }
20451:             cleanup();
20452:             resolve(result);
20453:         };
20454: 
20455:         const cleanup = () => {
20456:             form.removeEventListener("submit", handleSubmit);
20457:             const cancelBtn = form.querySelector("[data-action='action-dialog-cancel']");
20458:             cancelBtn.removeEventListener("click", handleCancel);
20459:             dialog.close();
20460:         };
20461: 
20462:         const cancelBtn = form.querySelector("[data-action='action-dialog-cancel']");
20463:         cancelBtn.addEventListener("click", handleCancel);
20464:         form.addEventListener("submit", handleSubmit);
20465: 
20466:         dialog.showModal();
20467:     });
```

命令：`nl -ba src/main/resources/static/app.js | sed -n '21278,21345p'`

```text
21278: // 安全确认取消返回 false —— 调用方保留草稿与 requestId）。成功提示与归档状态提示
21279: // 对来信/会话两条路径保持同一口径（I-8）。
21280: async function submitManualRichReply(url, requestBody) {
21281:     const submitWithConfirmation = async (body) => {
21282:         try {
21283:             const result = await api(url, {
21284:                 method: "POST",
21285:                 body: JSON.stringify(body)
21286:             });
21287:             const archiveStatus = result?.unsupportedAnswerArchiveStatus || "NOT_APPLICABLE";
21288:             const archivedCount = Number(result?.unsupportedAnswerArchivedCount) || 0;
21289:             if (archiveStatus === "SAVED") {
21290:                 const suffix = archivedCount > 0 ? `，已记录 ${archivedCount} 条无依据回答` : "";
21291:                 alert(`人工回复邮件发送成功${suffix}`);
21292:                 showStatus(`人工回复邮件发送成功${suffix}`, "ok");
21293:             } else if (archiveStatus === "PARTIAL" || archiveStatus === "FAILED") {
21294:                 alert("人工回复邮件发送成功\n无依据回答索引未完整写入，请勿重复发送");
21295:                 showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn");
21296:             } else {
21297:                 alert("人工回复邮件发送成功");
21298:             }
21299:             return true;
21300:         } catch (e) {
21301:             const canConfirmSafety = !body.safetyWarningConfirmed
21302:                 && e.data?.code === "MANUAL_SEND_SAFETY_BLOCKED"
21303:                 && Array.isArray(e.data.findings)
21304:                 && e.data.findings.length > 0;
21305:             if (canConfirmSafety) {
21306:                 const findings = e.data.findings;
21307:                 const renderFindings = (list) => list.map((finding) => {
21308:                     const severityClass = finding.severity === "STRONG" ? "ai-reply-error" : "ai-reply-warning";
21309:                     const label = AI_REPLY_WARNING_LABELS[finding.code] || "正文包含需人工核对的风险声明";
21310:                     const coverage = finding.sentence
21311:                         ? `<div class="ai-reply-coverage">命中原句：${escapeHtml(finding.sentence)}</div>`
21312:                         : "";
21313:                     return `<div class="${severityClass}">${escapeHtml(label)}</div>${coverage}`;
21314:                 }).join("");
21315:                 const firstConfirmed = await openActionDialog("confirm", {
21316:                     message: `<p>本次发送命中 ${findings.length} 项内容安全门禁，请逐条核对后确认：</p><div class="ai-reply-feedback">${renderFindings(findings)}</div><p>确认已人工核对，仍要发送吗？</p>`
21317:                 });
21318:                 if (!firstConfirmed) {
21319:                     alert("人工回复发送失败: " + e.message);
21320:                     return false;
21321:                 }
21322:                 let strongConfirmationText = null;
21323:                 if (e.data.requiresStrongConfirmation === true) {
21324:                     const strongFindings = findings.filter((finding) => finding.severity === "STRONG");
21325:                     const secondConfirmed = await openActionDialog("confirm-typed", {
21326:                         message: `<div class="ai-reply-error">高风险：本封邮件正文向专家索取护照 / 身份证 / 在职证明 / 银行流水一类敏感证件材料。此类索取存在合规与信任风险，一经发出不可撤回。</div><div class="ai-reply-feedback">${renderFindings(strongFindings)}</div><p>确认要发送，请在下方输入框中逐字输入「确认发送」四个字。</p>`
21327:                     });
21328:                     if (!secondConfirmed) {
21329:                         alert("人工回复发送失败: " + e.message);
21330:                         return false;
21331:                     }
21332:                     strongConfirmationText = "确认发送";
21333:                 }
21334:                 const retryBody = { ...body, safetyWarningConfirmed: true };
21335:                 if (strongConfirmationText !== null) {
21336:                     retryBody.strongConfirmationText = strongConfirmationText;
21337:                 }
21338:                 return submitWithConfirmation(retryBody);
21339:             }
21340:             alert("人工回复发送失败: " + e.message);
21341:             return false;
21342:         }
21343:     };
21344:     return submitWithConfirmation(requestBody);
21345: }
```

## E14 前端DOM/操作所有权

路径：`src/main/resources/static/mailbox-chat.js`

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '5500,5543p'`

```text
5500:             const meetingTrigger = ui ? meetingTriggerHtml() : "";
5501:             // S-2：材料索取紧随会议按钮之后，仍在跟进按钮之前。
5502:             const materialTrigger = ui ? materialRequestTriggerHtml() : "";
5503:             // S-1（fast-p 01 I-1）：引用模板入口只属于有真实来信的人工回复（outbound=false），
5504:             // 不依赖会议组件；固定在材料索取之后、跟进按钮之前。
5505:             const templateReferenceTrigger = isOutbound
5506:                 ? ""
5507:                 : `<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>`;
5508:             // S-1：跟进按钮只在当前专家确有成功发件时渲染，固定紧随既有会议按钮之后，
5509:             // class 严格为 button（不新增按钮 class、不改会议按钮顺序）。
5510:             const followUpButton = Number(instance.selectedSummary && instance.selectedSummary.sentCount) > 0
5511:                 ? `<button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>`
5512:                 : "";
5513:             // S-3：草稿携带跟进锚点时，在 .mc-editor 之后、会议附件之前显示所选邮件提示。
5514:             const anchorNote = draft && draft.followUpAnchorMailRecordId != null
5515:                 ? followupAnchorNoteHtml(draft.followUpAnchorMailRecordId)
5516:                 : "";
5517:             const meetingAttachment = ui && meetingCardContainerHtml(draft && draft.meeting ? draft.meeting : null) || "";
5518:             // fast-p 07（S-2）：通用附件草稿卡放在会议附件卡之后、发送 footer 之前。
5519:             const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
5520:             return `
5521:                 <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
5522:                     <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
5523:                     <div class="mc-editor-tools">
5524:                         <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
5525:                         <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
5526:                         <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
5527:                         <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
5528:                         <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
5529:                         <input type="file" data-role="outbound-file-input" multiple hidden>
5530:                         ${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
5531:                     </div>
5532:                     <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
5533:                     ${anchorNote}
5534:                     ${meetingAttachment}
5535:                     ${outboundFiles}
5536:                     <div class="mc-error" data-role="draft-load-error" role="alert" hidden>草稿加载失败，请重试</div>
5537:                     <button class="button" type="button" data-action="mc-reload-draft" data-role="draft-load-retry" hidden>重试加载</button>
5538:                     <div class="mc-note" data-role="draft-target-warning" hidden></div>
5539:                     <button class="button" type="button" data-action="mc-retarget-draft" hidden>为新来信另建草稿</button>
5540:                     <button class="button" type="button" data-action="mc-open-existing-draft" hidden>打开已有草稿</button>
5541:                     <div class="mc-note" data-role="draft-send-unknown" hidden>发送结果待确认，草稿已保留，请勿重复发送</div>
5542:                     <div class="mailbox-draft-status-row">
5543:                         <span class="mailbox-draft-status" data-role="draft-save-status" data-state="dirty" role="status" aria-live="polite">尚未保存</span>
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '7340,7385p'`

```text
7340:         /** I-6：打开时捕获的草稿身份（owner/contact/target/epoch + 主题与正文快照）。 */
7341:         function templateReferenceIdentity() {
7342:             const contactId = Number(instance.selectedContactId);
7343:             const contact = instance.conversation.contact || null;
7344:             const summary = instance.selectedSummary || {};
7345:             return {
7346:                 ownerKey: conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId),
7347:                 contactId,
7348:                 expertEmail: contact ? String(contact.expertEmail || "") : "",
7349:                 expertName: String(summary.name || (contact && contact.expertName) || ""),
7350:                 targetKey: currentTargetKey(),
7351:                 convEpoch: Number(instance.convEpoch),
7352:                 editorRevision: Number(instance.meeting.editorRevision),
7353:                 anchorId: null,
7354:                 senderAccountCode: "",
7355:                 subject: "",
7356:                 html: "",
7357:                 text: ""
7358:             };
7359:         }
7360: 
7361:         /** I-2：普通来信取来信账号；有跟进锚点时必须找到相同 id 的成功发件，否则明确失败。 */
7362:         function templateReferenceSenderAccount(anchorId) {
7363:             if (anchorId == null) {
7364:                 return { ok: true, code: String(instance.manual.targetAccountCode || "") };
7365:             }
7366:             const match = followupCandidates().find((item) => Number(item.id) === Number(anchorId));
7367:             if (!match) return { ok: false, code: "" };
7368:             return { ok: true, code: String(match.accountCode || "") };
7369:         }
7370: 
7371:         /** I-6：在途回包只承认「弹框仍打开 + seq 未变 + owner/target/epoch 仍匹配」。 */
7372:         function templateReferenceSeqMatches(seq) {
7373:             const state = instance.templateReference;
7374:             if (instance.disposed || !state.open || state.seq !== seq) return false;
7375:             const captured = state.identity;
7376:             if (!captured) return false;
7377:             const now = templateReferenceIdentity();
7378:             return now.ownerKey === captured.ownerKey
7379:                 && now.contactId === captured.contactId
7380:                 && now.targetKey === captured.targetKey
7381:                 && now.convEpoch === captured.convEpoch;
7382:         }
7383: 
7384:         function templateReferenceSelectedItem() {
7385:             const state = instance.templateReference;
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '8336,8353p'`

```text
8336:         /** 编辑器输入统一入口：editorRevision++、meeting stale 检测、草稿保存。 */
8337:         function handleManualComposeInput(target) {
8338:             const composeEl = manualComposeEl();
8339:             const inputs = manualInputs(composeEl);
8340:             const isEditor = !!(inputs && target === inputs.editor);
8341:             let patch = null;
8342:             if (isEditor) {
8343:                 instance.meeting.editorRevision += 1;
8344:                 const meeting = manualMeetingSnapshot();
8345:                 if (meeting) {
8346:                     const change = detectMeetingChange(inputs.editor, meeting);
8347:                     if (change) patch = change;
8348:                 }
8349:             }
8350:             saveDraftFromInputs(patch || {});
8351:             if (patch) refreshMeetingAttachmentCard();
8352:             refreshSendAvailability();
8353:         }
```

命令：`nl -ba src/main/resources/static/mailbox-chat.js | sed -n '8545,8625p'`

```text
8545:                     requestId,
8546:                     accountScope: rec.target.accountScope || null,
8547:                     subject,
8548:                     htmlBody,
8549:                     textBody,
8550:                     operatorName: operatorName()
8551:                 };
8552:                 if (followUpAnchorId != null) requestBody.anchorMailRecordId = followUpAnchorId;
8553:                 // 07（I-3）：空数组省略字段，兼容未带附件的旧请求形态。
8554:                 if (attachmentIds.length > 0) requestBody.attachmentIds = attachmentIds.slice();
8555:             }
8556:             // I-2：异步前捕获 draftsMap/owner/key/revision/requestBody；不回调里再取。
8557:             const contactId = Number(instance.selectedContactId);
8558:             const ownerKey = conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId);
8559:             requestBody.draftRef = { id: rec.id, version: rec.version };
8560:             const inFlightKey = meeting ? `${ownerKey}|${key}` : null;
8561:             if (inFlightKey && meetingInFlight.has(inFlightKey)) {
8562:                 hostShowStatus("该回复目标已有发送中的会议回复，请稍候", "error");
8563:                 return;
8564:             }
8565:             if (inFlightKey) meetingInFlight.add(inFlightKey);
8566:             // 07（I-3）：带已就绪附件的发送同样锁住当前 owner 的编辑/附件增删；其他专家不受影响。
8567:             const lockedCompose = !!meeting || attachmentItems.length > 0;
8568:             instance.manual.busy = true;
8569:             rec.sending = true;
8570:             refreshDraftStatus();
8571:             setSendButtonDisabled(true);
8572:             if (lockedCompose) setManualComposeSending(true);
8573:             const adapter = conversationSend
8574:                 ? hostFn("mcHostSendConversationRichReply")
8575:                 : hostFn("mcHostSendRichReply");
8576:             const request = adapter
8577:                 ? (conversationSend
8578:                     ? adapter(contactId, requestBody)
8579:                     : adapter(processingId, requestBody))
8580:                 : Promise.reject(new Error("发送能力不可用"));
8581:             return Promise.resolve(request).catch(() => false).then(async () => {
8582:                 // HTTP/host Boolean is not the draft-close authority.
8583:                 try {
8584:                     const detail = await hostApi()(`/api/mail/mailbox/drafts/${rec.id}`);
8585:                     if (!draftOwnerLive(rec)) return;
8586:                     const stillCurrent = !instance.disposed && currentTargetKey() === key;
8587:                     if (stillCurrent) saveDraftFromInputs();
8588:                     if (Number(detail.version) < rec.version) return;
8589:                     if (detail.state === "ACTIVE" && Number(detail.version) !== rec.version && rec.seq > sentSeq) {
8590:                         const remote = draftFromDetail(detail);
8591:                         if (draftContentEqual(remote, rec.map.get(key))) rec.ack = rec.seq;
8592:                         else if (rec.submitted && draftContentEqual(remote, {
8593:                             subject: rec.submitted.snapshot.subject, html: rec.submitted.snapshot.html,
8594:                             text: rec.submitted.snapshot.text, ...rec.submitted.snapshot.context })) {
8595:                             rec.ack = Math.max(rec.ack, rec.submitted.seq);
8596:                         } else rec.conflict = true;
8597:                     }
8598:                     acceptDraftDetail(rec, detail, false);
8599:                     rec.sendUnconfirmed = false;
8600:                     if (detail.state === "SENT" || detail.state === "DISCARDED") {
8601:                         clearTimeout(rec.timer);
8602:                         if (rec.seq === sentSeq) {
8603:                             rec.map.delete(key);
8604:                             rec.ack = rec.seq;
8605:                             rec.closed = false;
8606:                             if (stillCurrent) renderDraftEditor(rec);
8607:                         } else {
8608:                             rec.closed = true;
8609:                             rec.error = "";
8610:                             rec.conflict = false;
8611:                         }
8612:                     } else if (rec.seq === sentSeq && rec.ack === rec.seq) {
8613:                         const saved = draftFromDetail(detail);
8614:                         if (saved && !draftContentEqual(saved, rec.map.get(key))) {
8615:                             rec.map.set(key, saved);
8616:                             if (stillCurrent) renderDraftEditor(rec);
8617:                         }
8618:                     }
8619:                     if (stillCurrent && detail.sendAttemptStatus === "SENT") afterSuccessfulSend(key);
8620:                     loadDraftSummaries();
8621:                 } catch (err) {
8622:                     if (draftOwnerLive(rec)) rec.sendUnconfirmed = true;
8623:                 } finally {
8624:                     rec.sending = false;
8625:                     if (inFlightKey) meetingInFlight.delete(inFlightKey);
```

## E15 CSS基准完整规则

路径：`src/main/resources/static/styles.css`

命令：`nl -ba src/main/resources/static/styles.css | sed -n '1,80p'`

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
```

命令：`nl -ba src/main/resources/static/styles.css | sed -n '802,860p'`

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
```

命令：`nl -ba src/main/resources/static/styles.css | sed -n '2873,2923p'`

```text
2873: .action-dialog {
2874:     border: 1px solid var(--panel-border);
2875:     border-radius: var(--radius-lg);
2876:     padding: 20px;
2877:     width: min(500px, 90vw);
2878:     margin: auto;
2879:     background: rgba(255, 255, 255, 0.97);
2880:     backdrop-filter: blur(8px);
2881:     -webkit-backdrop-filter: blur(8px);
2882:     color: var(--text-main);
2883:     box-shadow: var(--shadow-xl);
2884: }
2885: 
2886: .action-dialog::backdrop {
2887:     background: rgba(15, 23, 42, 0.5);
2888:     backdrop-filter: blur(6px);
2889:     -webkit-backdrop-filter: blur(6px);
2890: }
2891: 
2892: .action-dialog h3 {
2893:     margin-bottom: 12px;
2894:     font-size: 15px;
2895:     font-weight: 600;
2896:     font-family: var(--font-body);
2897:     color: var(--text-main);
2898: }
2899: 
2900: .action-dialog-body {
2901:     display: flex;
2902:     flex-direction: column;
2903:     gap: 12px;
2904:     margin-bottom: 20px;
2905: }
2906: 
2907: .action-dialog-body p {
2908:     color: var(--text-main);
2909:     font-size: 13px;
2910:     line-height: 1.6;
2911: }
2912: 
2913: .action-dialog-body .ai-reply-coverage {
2914:     color: var(--text-secondary);
2915: }
2916: 
2917: .action-dialog-footer {
2918:     display: flex;
2919:     justify-content: flex-end;
2920:     gap: 8px;
2921: }
2922: 
2923: /* Attention Banner */
```

命令：`nl -ba src/main/resources/static/styles.css | sed -n '6387,6414p'`

```text
6387: .ai-reply-feedback {
6388:     display: flex;
6389:     flex-direction: column;
6390:     gap: 6px;
6391:     margin-bottom: 10px;
6392: }
6393: 
6394: .ai-reply-coverage {
6395:     padding: 7px 9px;
6396:     border: 1px solid var(--line);
6397:     border-radius: var(--radius-sm);
6398:     background: var(--surface);
6399:     color: var(--text-muted);
6400:     font-size: 12px;
6401: }
6402: 
6403: .ai-reply-warning {
6404:     padding: 8px 10px;
6405:     border: 1px solid var(--warning-border);
6406:     border-radius: var(--radius-sm);
6407:     background: var(--warning-bg);
6408:     color: var(--warning);
6409:     font-size: 12px;
6410:     line-height: 1.5;
6411: }
6412: 
6413: .ai-reply-error {
6414:     padding: 8px 10px;
```

## E16 邮箱局部覆盖

路径：`src/main/resources/static/mailbox-chat.css`

命令：`nl -ba src/main/resources/static/mailbox-chat.css | sed -n '59,71p'`

```text
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
```

命令：`nl -ba src/main/resources/static/mailbox-chat.css | sed -n '138,152p'`

```text
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
```

## E17 版本化静态资源

命令：`rg -n '\?v=' src/main/resources/static/index.html`

退出码：0

```text
11:    <link rel="stylesheet" href="styles.css?v=20261020-apollo-matching-v2">
12:    <link rel="stylesheet" href="expert-materials.css?v=20261020-apollo-matching-v2">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20261020-apollo-matching-v2">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20261020-apollo-matching-v2">
15:    <link rel="stylesheet" href="world-clock.css?v=20261020-apollo-matching-v2">
16:<link rel="stylesheet" href="meeting-records.css?v=20261020-apollo-matching-v2">
17:<link rel="stylesheet" href="contact-details.css?v=20261020-apollo-matching-v2">
2424:<script src="trust-reply-workbench.js?v=20261020-apollo-matching-v2"></script>
2425:<script src="expert-materials.js?v=20261020-apollo-matching-v2"></script>
2426:<script src="meeting-confirmation.js?v=20261020-apollo-matching-v2"></script>
2427:<script src="mailbox-chat.js?v=20261020-apollo-matching-v2"></script>
2428:<script src="contact-history.js?v=20261020-apollo-matching-v2"></script>
2429:<script src="meeting-records.js?v=20261020-apollo-matching-v2"></script>
2430:<script src="contact-details.js?v=20261020-apollo-matching-v2"></script>
2431:<script src="app.js?v=20261020-apollo-matching-v2"></script>
2432:<script src="world-clock.js?v=20261020-apollo-matching-v2"></script>
```

## E18 当前缓存键测试引用

命令：`rg -n -F 20261020-apollo-matching-v2 src/test`

退出码：1

```text
(无匹配)
```

## E19 草稿SQL写读

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt | sed -n '16,110p'`

```text
16: class MailReplyDraftRepository(private val jdbc: NamedParameterJdbcTemplate) {
17:     private val mapper = RowMapper { r: ResultSet, _: Int ->
18:         MailReplyDraftRow(r.getLong("id"), r.getString("username"),
19:             MailReplyDraftTarget(r.getLong("expert_contact_id"), MailReplyDraftKind.valueOf(r.getString("target_kind")), r.getLong("inbound_processing_id"), r.getString("account_scope")),
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
105: }
```

## E20 草稿表及repo消费全集

命令：`rg -n 'mailbox_reply_draft|mailReplyDraftRepository\.|draftRepository\.' src/main/kotlin src/main/resources/db scripts`

退出码：0

```text
src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql:1:CREATE TABLE mailbox_reply_draft (
src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql:19:    UNIQUE KEY uk_mailbox_reply_draft_target (username, expert_contact_id, target_kind, inbound_processing_id, account_scope),
src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql:20:    KEY idx_mailbox_reply_draft_list (username, state, updated_at, id)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:26:        a.status AS send_attempt_status FROM mailbox_reply_draft d
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:36:        jdbc.update("""INSERT INTO mailbox_reply_draft (username,expert_contact_id,target_kind,inbound_processing_id,account_scope,version,state,subject,html_body,text_body,context_json,created_at,updated_at)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:42:        return jdbc.update("""UPDATE mailbox_reply_draft SET version=version+1,state='ACTIVE',subject=:subject,html_body=:html,text_body=:text,context_json=:context,updated_at=:now$resetBinding
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:46:    fun discard(owner: String, id: Long, version: Long, now: LocalDateTime): Boolean = jdbc.update("""UPDATE mailbox_reply_draft SET state='DISCARDED',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:56:        val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter",params,Long::class.java) ?: 0
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:60:            FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter ORDER BY d.updated_at DESC,d.id DESC LIMIT :limit OFFSET :offset""",params,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:69:        val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d$filter",params,Long::class.java) ?: 0
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:70:        val counts = if (contacts.isEmpty()) emptyMap() else jdbc.query("SELECT d.expert_contact_id,COUNT(*) AS n FROM mailbox_reply_draft d$filter AND d.expert_contact_id IN (:contacts) GROUP BY d.expert_contact_id",params,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:80:        jdbc.queryForList("SELECT id FROM mailbox_reply_draft WHERE username=:owner AND id=:id FOR UPDATE",mapOf("owner" to owner,"id" to id))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:86:        return jdbc.update("""UPDATE mailbox_reply_draft SET send_attempt_id=:attempt,send_version=:version
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:94:        return jdbc.update("""UPDATE mailbox_reply_draft SET state='SENT',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt:101:        return jdbc.update("UPDATE mailbox_reply_draft SET send_attempt_id=NULL,send_version=NULL WHERE username=:owner AND id=:id AND send_attempt_id=:attempt AND send_version=:version",
```

## E21 真实MySQL事务测试先例

路径：`src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt`

命令：`nl -ba src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt | sed -n '1,66p'`

```text
1: package com.weibo.talentintroduction.mail.repository
2: 
3: import com.fasterxml.jackson.databind.ObjectMapper
4: import com.fasterxml.jackson.module.kotlin.KotlinModule
5: import com.weibo.talentintroduction.auth.config.AuthSessionKeys
6: import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
7: import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
8: import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
9: import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
10: import com.weibo.talentintroduction.mail.service.*
11: import org.apache.catalina.startup.Tomcat
12: import org.flywaydb.core.Flyway
13: import org.junit.jupiter.api.*
14: import org.junit.jupiter.api.Assertions.*
15: import org.junit.jupiter.api.condition.EnabledIfSystemProperty
16: import org.mockito.Mockito.*
17: import org.springframework.aop.framework.ProxyFactory
18: import org.springframework.dao.DuplicateKeyException
19: import org.springframework.jdbc.core.JdbcTemplate
20: import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
21: import org.springframework.jdbc.datasource.DataSourceTransactionManager
22: import org.springframework.jdbc.datasource.DriverManagerDataSource
23: import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
24: import org.springframework.transaction.interceptor.TransactionInterceptor
25: import org.springframework.transaction.support.TransactionTemplate
26: import org.springframework.web.context.support.GenericWebApplicationContext
27: import org.springframework.web.servlet.DispatcherServlet
28: import org.springframework.web.servlet.config.annotation.EnableWebMvc
29: import org.springframework.context.annotation.Configuration
30: import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
31: import org.testcontainers.DockerClientFactory
32: import org.testcontainers.containers.MySQLContainer
33: import java.net.HttpURLConnection
34: import java.net.URL
35: import java.nio.file.Files
36: import java.time.LocalDateTime
37: import java.util.concurrent.CountDownLatch
38: import java.util.concurrent.Executors
39: import java.util.concurrent.TimeUnit
40: import javax.servlet.http.HttpServlet
41: import javax.servlet.http.HttpServletRequest
42: import javax.servlet.http.HttpServletResponse
43: 
44: @EnabledIfSystemProperty(named="mysqlIt",matches="true")
45: class MailReplyDraftRepositoryIT {
46:     companion object {
47:         private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
48:         private val mysql = Mysql().withDatabaseName("talent_introduction").withUsername("test").withPassword("test")
49:         private lateinit var dataSource: DriverManagerDataSource
50:         @JvmStatic @BeforeAll fun start() {
51:             check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for draft MySQL IT" }
52:             mysql.start()
53:             dataSource = DriverManagerDataSource(mysql.jdbcUrl,mysql.username,mysql.password)
54:             Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
55:         }
56:         @JvmStatic @AfterAll fun stop() { mysql.stop() }
57:     }
58:     private val jdbc by lazy { JdbcTemplate(dataSource) }
59:     private val repository by lazy { MailReplyDraftRepository(NamedParameterJdbcTemplate(dataSource)) }
60:     private val tx by lazy { TransactionTemplate(DataSourceTransactionManager(dataSource)) }
61:     private val now = LocalDateTime.of(2026,10,8,12,0)
62:     private val target = MailReplyDraftTarget(9101,MailReplyDraftKind.OUTBOUND)
63:     private fun insert(owner: String = "A", t: MailReplyDraftTarget = target, content: MailReplyDraftContent = MailReplyDraftContent(text="original")) = repository.insert(owner,t,content,MailReplyDraftContextCodec.serialize(content.context),now)
64:     private fun save(id: Long, version: Long, text: String = "new", state: MailReplyDraftState = MailReplyDraftState.ACTIVE) = repository.save("A",id,version,state,MailReplyDraftContent(text=text),MailReplyDraftContextCodec.serialize(MailReplyDraftContext()),now.plusSeconds(version))
65:     @BeforeEach fun seed() {
66:         jdbc.update("DELETE FROM mailbox_reply_draft")
```

## E22 认证

路径：`src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt | sed -n '1,80p'`

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

## E23 片段路由

路径：`src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt | sed -n '18,58p'`

```text
18: @RestController
19: @RequestMapping("/api/reply-snippets")
20: class ReplySnippetController(
21:     private val service: ReplySnippetService
22: ) {
23:     @GetMapping
24:     fun list(@RequestParam(required = false) snippetType: String?): List<ReplySnippetResponse> =
25:         if (snippetType.isNullOrBlank()) {
26:             service.listAll()
27:         } else {
28:             service.listByType(snippetType)
29:         }.map { it.toResponse() }
30: 
31:     @PostMapping
32:     fun create(@RequestBody request: ReplySnippetCreateRequest): ReplySnippetResponse =
33:         service.create(request.toCommand()).toResponse()
34: 
35:     @PutMapping("/{id}")
36:     fun update(
37:         @PathVariable id: Long,
38:         @RequestBody request: ReplySnippetUpdateRequest
39:     ): ReplySnippetResponse =
40:         service.update(id, request.toCommand()).toResponse()
41: 
42:     @PostMapping("/{id}/enable")
43:     fun enable(@PathVariable id: Long): ReplySnippetResponse =
44:         service.setEnabled(id, true).toResponse()
45: 
46:     @PostMapping("/{id}/disable")
47:     fun disable(@PathVariable id: Long): ReplySnippetResponse =
48:         service.setEnabled(id, false).toResponse()
49: 
50:     @PostMapping("/{id}/default")
51:     fun setDefault(@PathVariable id: Long): ReplySnippetResponse =
52:         service.setDefault(id).toResponse()
53: 
54:     @DeleteMapping("/{id}")
55:     fun delete(@PathVariable id: Long) {
56:         service.delete(id)
57:     }
58: }
```

## E24 片段前端缓存与管理入口

命令：`rg -n 'replySnippetsLoaded|async function loadReplySnippets|async function ensureReplySnippetsLoaded|/api/reply-snippets' src/main/resources/static/app.js`

退出码：0

```text
107:    replySnippetsLoaded: false,
6836:async function loadReplySnippets() {
6837:    state.replySnippets = await api("/api/reply-snippets");
6838:    state.replySnippetsLoaded = true;
6845:async function ensureReplySnippetsLoaded() {
6846:    if (state.replySnippetsLoaded) return;
8078:        await api(`/api/reply-snippets/${state.selectedReplySnippetId}`, {
8083:        await api("/api/reply-snippets", {
8103:        await api(`/api/reply-snippets/${id}/${enabled ? "disable" : "enable"}`, { method: "POST" });
8108:        await api(`/api/reply-snippets/${id}/default`, { method: "POST" });
8115:        await api(`/api/reply-snippets/${id}`, { method: "DELETE" });
```

## E25 既有持久化调用清单（非本轮新增写入）

命令：`rg -n '(expertContactRepository|mailSenderAccountRepository|mailRecordRepository|mailSendAttemptRepository|inboundMailProcessingRepository)\.(save|delete|insert|update|mark|find|increment|claim|finalize)|UPDATE (expert_contact|mail_sender_account|mail_record|mail_send_attempt)|INSERT INTO (expert_contact|mail_sender_account|mail_record|mail_send_attempt)' src/main/kotlin scripts`

退出码：0

```text
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:137:            ?.let { mailRecordRepository.findAllById(it).associateBy { record -> record.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:190:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:229:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:239:        val contact = expertContactRepository.findById(promotion.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:246:        val lastReceived = inboundMailProcessingRepository.findLastReceivedAtPerAccount()
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:248:        return mailSenderAccountRepository.findAllByOrderByAccountCodeAsc().map { account ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:393:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateOperatorStatusSyncService.kt:18:        val latestUpdates = expertContactRepository.findAllByOrderByUpdatedAtDesc()
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:490:        val contact = expertContactRepository.findById(promotion.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:78:        val mail = mailRecordRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:97:        val inbound = inboundMailProcessingRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:134:        expertContactRepository.findById(contactId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:134:                expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewService.kt:23:        val account = mailSenderAccountRepository.findByAccountCode(code)
src/main/kotlin/com/weibo/talentintroduction/postmaster/service/ReputationAutoPauseService.kt:30:        val accountsForDomain = mailSenderAccountRepository.findAllByEnabledTrue()
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:81:            return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:259:        inboundMailProcessingRepository.findAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:477:                val processing = inboundMailProcessingRepository.findById(candidate.id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:710:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:730:                val processing = inboundMailProcessingRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:69:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:93:        mailSenderAccountRepository.incrementTodaySentCount(accountCode, now)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:96:        val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:98:            mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:127:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:151:            val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:153:                mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:50:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:56:                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:33:        val all = expertContactRepository.findAll().toList()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:75:            expertContactRepository.updateCountryById(contactId, country)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:57:                val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:50:        val record = mailRecordRepository.findById(mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:47:        UPDATE mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:59:        UPDATE mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:118:                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:55:        val contacts = expertContactRepository.findAll().toList()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:56:        val mailRecords = mailRecordRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:227:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:261:            mailRecordRepository.findAllById(outboundSentRows.map { it.id })
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:334:        val contacts = expertContactRepository.findAllById(rows.map { it.expertContactId })
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:67:    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:72:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:81:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:92:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:105:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:113:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:137:        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:389:        val contact = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:392:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:94:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:145:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:166:        mailSenderAccountRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:191:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:217:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:439:                        mailSenderAccountRepository.incrementTodaySentCount(account.accountCode, LocalDateTime.now())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1385:                        expertContactRepository.save(ExpertContact(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1435:                    val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1436:                    val attempt = mailSendAttemptRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1811:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1832:            addAll(expertContactRepository.findByOrcidIdIn(listOf(normOrcid)).mapNotNull { it.id })
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1856:            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1922:        expertContactRepository.findByOrcidIdIn(listOf(normOrcid))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1959:        return expertContactRepository.findByOrcidIdIn(distinct)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1981:        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1986:                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2090:        val enabledAccounts = mailSenderAccountRepository.findAllByEnabledTrue()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2177:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:2275:        val contacts = if (normOrcidList.isNotEmpty()) expertContactRepository.findByOrcidIdIn(normOrcidList) else emptyList()
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:37:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:21:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:24:        return expertContactRepository.findById(alias.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:29:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(normalized)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:30:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:33:        return expertContactRepository.findById(alias.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:42:        expertContactRepository.findById(expertContactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:26:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:69:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:86:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:103:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:110:        return expertContactRepository.save(contact.copy(currentIndexLevel = "RAW", applicationIndexed = false))
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:121:        val records = mailRecordRepository.findInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:140:            expertContactRepository.findAllById(contactIds).associateBy { requireNotNull(it.id) }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:177:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:181:        val contacts = expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:184:            val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:197:            val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:203:            val c = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:209:            val c = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:211:            val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:218:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:369:        val contactIds = expertContactRepository.findByOrcidIdIn(orcidIds).mapNotNull { it.id }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:410:                ?: inboundMailProcessingRepository.findAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt:224:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:25:            val batch = inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(batchSize, offset)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:27:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:30:        val updated = expertContactRepository.save(contact.copy(operatorStatus = target.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:64:        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:88:        val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:50:        expertContactRepository.findFilteredContacts(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:57:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:64:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:71:        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:277:        val updated = expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:287:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:296:        return expertContactRepository.save(contact.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:316:                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:454:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:463:        val allContacts = expertContactRepository.findAll().toList()
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:102:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:134:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:169:        mailSenderAccountRepository.save(account.copy(lastSentAt = now))
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:364:            contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:60:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:83:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:87:            inboundMailProcessingRepository.findAllByExpertContactId(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:98:            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:149:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ConversationStateService.kt:26:        val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:228:        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:240:        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:368:                check(mailRecordRepository.findByMailSendAttemptId(claim.attemptId)?.sendStatus == "SENT") {
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:399:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:437:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:499:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:538:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2356:        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2373:        val inbound = inboundMailProcessingRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2398:        val contact = expertContactRepository.findById(contactId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2401:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:127:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:147:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:194:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:198:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:405:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:462:            mailRecordRepository.findById(anchorMailRecordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:466:            mailRecordRepository.findLatestSentOutboundAnchor(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:944:                val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1071:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1076:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1087:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1092:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1208:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1552:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1558:        inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1575:                expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1577:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1610:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1639:            expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1641:                    expertContactRepository.save(contact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1690:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1695:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:214:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:182:        inboundMailProcessingRepository.findByMailboxOwnerCodeAndUidValidityAndImapUid(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:203:        val legacySameUid = inboundMailProcessingRepository.findLegacyOwnerlessByGroupAndImapUid(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:376:                    expertContactRepository.save(disabledContact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:406:        val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:439:        val inboundMailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:780:        val outboundRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1067:    ): MailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1266:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1328:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1384:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1452:            .flatMap { mailRecordRepository.findOutboundCandidatesByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:192:        val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:207:        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:265:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:270:                val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:296:        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:328:        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:51:                inboundMailProcessingRepository.findUnmatchedManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:56:            inboundMailProcessingRepository.findManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:85:        val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:96:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:98:                val contact = expertContactRepository.findById(outboundMail.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:147:        expertContactRepository.findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:158:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:162:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:190:        expertContactRepository.save(currentContact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:193:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:234:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:238:        val saved = inboundMailProcessingRepository.save(record.copy(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:251:                expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:253:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:263:        val processing = inboundMailProcessingRepository.findById(processingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:276:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactNoteService.kt:76:            INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt:26:        UPDATE mail_sender_account
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt:37:        UPDATE mail_sender_account
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt:49:        UPDATE mail_sender_account
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt:65:        UPDATE mail_sender_account
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt:81:        UPDATE mail_sender_account
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:83:                val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:50:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:87:                val contactForSeed = contactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:111:                val previewContact = contactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:168:        val contact = expertContactRepository.findById(contactId).orElse(null) ?: return blocked
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:209:            return expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt:278:            INSERT INTO expert_contact_location (expert_contact_id, country_code, zone_id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:213:            for (record in mailRecordRepository.findOutboundCandidatesByMessageId(candidate)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:300:                expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:45:        expertContactRepository.updateBindingById(contactId, code, at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:51:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:70:        return expertContactRepository.findById(contactId).orElseThrow()
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:119:        return expertContactRepository.findById(contactId).orElseThrow()
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt:394:            return mailRecordRepository.findByIdOrNull(mailRecordId)?.expertContactId
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:469:            return expertContactRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt:222:                expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email) != null ||
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt:302:                    expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt:323:                    expertContactRepository.findFirstByOrcidIdOrderByUpdatedAtDesc(value)
```

## E26 共享弹窗HTML基线

路径：`src/main/resources/static/index.html`

命令：`nl -ba src/main/resources/static/index.html | sed -n '2302,2311p'`

```text
2302: <dialog id="actionDialog" class="action-dialog">
2303:     <form id="actionDialogForm" method="dialog">
2304:         <h3 id="actionDialogTitle"></h3>
2305:         <div id="actionDialogBody" class="action-dialog-body"></div>
2306:         <div class="action-dialog-footer">
2307:             <button type="button" class="button secondary" data-action="action-dialog-cancel">取消</button>
2308:             <button type="submit" class="button primary">确认执行</button>
2309:         </div>
2310:     </form>
2311: </dialog>
```

## E27 草稿服务调用

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt | sed -n '25,84p'`

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
```

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt | sed -n '136,161p'`

```text
136: 
137:     /** Caller already owns the sending transaction; take the draft lock before touching attempts. */
138:     internal fun lockForClaim(ref: ValidatedDraftSendRef): MailReplyDraftRow {
139:         val row = locked(ref.owner,ref.id)
140:         if (row.state != MailReplyDraftState.ACTIVE || row.version != ref.version) conflict(row)
141:         if (row.target != ref.target || content(row) != ref.content) mismatch(row)
142:         return row
143:     }
144:     internal fun lockForFinalize(ref: ValidatedDraftSendRef) { locked(ref.owner,ref.id) }
145:     internal fun releaseBinding(row: MailReplyDraftRow) {
146:         check(repository.releaseCompletedBinding(row.username,row.id,requireNotNull(row.sendVersion),requireNotNull(row.sendAttemptId))) {
147:             "Draft binding release failed"
148:         }
149:     }
150:     internal fun bind(ref: ValidatedDraftSendRef, attemptId: Long) {
151:         check(repository.bindAttempt(ref.owner,ref.id,ref.version,attemptId)) { "Draft send binding failed" }
152:     }
153:     internal fun closeSent(ref: ValidatedDraftSendRef, attemptId: Long, now: LocalDateTime) {
154:         if (repository.closeSent(ref.owner,ref.id,ref.version,attemptId,now)) return
155:         val row = owned(ref.owner,ref.id)
156:         if (row.sendAttemptId != attemptId || row.sendVersion != ref.version) error("Draft send binding changed")
157:         // An edit or an explicitly discarded UNKNOWN send is not resurrected by a late success.
158:         if (row.version > ref.version) { releaseBinding(row); return }
159:         error("Draft sent close failed")
160:     }
161:     private fun activeSend(owner: String, ref: MailReplyDraftRef): MailReplyDraftRow {
```

## E28 前端草稿Map写入检索

命令：`rg -n 'currentDraftsMap|setDraft\(|\.drafts\.(set|delete)|draftsMap\.(set|delete)|rec\.map\.(set|delete)' src/main/resources/static/mailbox-chat.js`

退出码：0

```text
912:        function currentDraftsMap() {
918:            instance.draftsRef = currentDraftsMap();
923:            return targetKey ? currentDraftsMap().get(targetKey) || null : null;
957:            rec.map.set(rec.key, draft);
969:        function setDraft(targetKey, draft) {
985:                    rec.map.set(rec.key, draft);
990:                } else rec.map.delete(rec.key);
1247:                rec.map.delete(rec.key);
5046:            const drafts = currentDraftsMap();
6352:            return captured.draftsMap === currentDraftsMap();
6597:            setDraft(key, {
6652:                setDraft(key, Object.assign({}, values, {
8239:            setDraft(key, next);
8325:                setDraft(key, Object.assign({}, existing || {}, {
8603:                            rec.map.delete(key);
8615:                            rec.map.set(key, saved);
```

## E29 草稿schema

路径：`src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql`

命令：`nl -ba src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql | sed -n '1,24p'`

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

## E30 变量渲染隐式写入

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt | sed -n '257,281p'`

```text
257:         return mailPlaceholderService.detectFallbackKeys(text, variables)
258:     }
259: 
260:     private fun unsubscribeUrl(email: String?, previewFallbacks: Boolean): String {
261:         if (email.isNullOrBlank()) {
262:             return ""
263:         }
264:         val service = unsubscribeTokenService ?: return previewUnsubscribeUrl(previewFallbacks)
265:         if (!service.enabled()) {
266:             return previewUnsubscribeUrl(previewFallbacks)
267:         }
268:         return service.unsubscribeUrl(email)
269:     }
270: 
271:     private fun previewUnsubscribeUrl(previewFallbacks: Boolean): String =
272:         if (previewFallbacks) PREVIEW_UNSUBSCRIBE_URL else ""
273: 
274:     /**
275:      * I1-7：key 永远存在。真实 contact 且材料服务可用时输出 I1-5 过滤编号正文；
276:      * 无 contact、contact.id 为 null 或未注入服务时为空字符串，绝不泄漏占位符。
277:      */
278:     private fun renderPendingMaterials(contact: ExpertContact?): String {
279:         val service = expertMaterialService ?: return ""
280:         val contactId = contact?.id ?: return ""
281:         return service.renderPendingMaterials(contactId)
```

## E30b 退订token写入

路径：`src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt | sed -n '17,47p'`

```text
17: class UnsubscribeTokenService(
18:     private val properties: UnsubscribeProperties,
19:     private val repository: UnsubscribeTokenRepository? = null
20: ) {
21:     private val secureRandom = SecureRandom()
22: 
23:     fun enabled(): Boolean =
24:         if (repository != null) properties.baseUrl.isNotBlank()
25:         else properties.baseUrl.isNotBlank() && properties.secret.isNotBlank()
26: 
27:     fun sign(email: String): String {
28:         val n = email.trim().lowercase(Locale.ROOT)
29:         val repo = repository ?: return legacySign(n)
30:         repo.findByEmail(n)?.let { return it.token }
31:         val token = newToken()
32:         return try {
33:             repo.save(UnsubscribeToken(email = n, token = token, createdAt = LocalDateTime.now())).token
34:         } catch (e: DuplicateKeyException) {
35:             repo.findByEmail(n)?.token ?: throw e
36:         }
37:     }
38: 
39:     /** 校验并返回归一化邮箱；失败返回 null。 */
40:     fun verify(token: String): String? {
41:         repository?.findByToken(token)?.let { return it.email }
42:         return verifyLegacy(token)
43:     }
44: 
45:     fun unsubscribeUrl(email: String): String =
46:         "${properties.baseUrl.trimEnd('/')}/u/unsubscribe?token=${sign(email)}"
47: 
```

## E31 纯变量替换

路径：`src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | sed -n '856,870p'`

```text
856:     fun renderWithVariables(text: String, variables: Map<String, String>): String = renderText(text, variables)
857: 
858:     private fun renderText(text: String, variables: Map<String, String>): String {
859:         val withFallback = FALLBACK_PLACEHOLDER_REGEX.replace(text) { match ->
860:             val key = match.groupValues[1]
861:             val fallback = match.groupValues[2]
862:             variables[key]?.takeIf { it.isNotEmpty() } ?: fallback
863:         }
864:         return variables.entries.fold(withFallback) { rendered, (key, value) ->
865:             rendered.replace("\${$key}", value)
866:         }
867:     }
868: 
869:     companion object {
870:         const val EXCERPT_MAX_CHARS = 40
```

## E32 pendingMaterials是读取

路径：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt`

命令：`nl -ba src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt | sed -n '173,185p'`

```text
173:     /**
174:      * I1-5：只取 PENDING 项，按 I1-1 目录顺序过滤后从 1 连续编号，
175:      * 以 `\n` 连接输出英文编号行；空集合返回 `""`。
176:      * 调用方已有真实 contact，不重复查询联系人。
177:      */
178:     fun renderPendingMaterials(contactId: Long): String {
179:         val byCode = expertMaterialStatusRepository.findAllByExpertContactId(contactId)
180:             .associateBy { it.materialCode }
181:         return ExpertMaterialCode.entries
182:             .filter { byCode[it.name] == null }
183:             .mapIndexed { index, code -> "${index + 1}. ${code.requestText}" }
184:             .joinToString("\n")
185:     }
```

## 文件指纹

|文件|SHA256|
|---|---|
|`src/main/resources/db/migration/V47__create_reply_snippet.sql`|`98be81618a6bc495ff404b92304d922cc2dff735b8c4b94d3457dc6820e48464`|
|`src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt`|`7a57a7668f0857b967d23af6bf854d7ebca9f3706f98b2f974da93fc09dcbfc4`|
|`src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt`|`a949f20993f151b1927eb39426fc3b5079ac57b1fdb0ed384650a151a0b7434b`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`|`db62b5be9abb8e27aa4c509c7227aed8f47444bb04990cb4fab820c476d58041`|
|`src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`|`222ee6b60338f757957493b195f3295f23fe2b765c11d67b375c6c961b0aeeb7`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`|`27ac41a17e146bdd8adfccc74fe6594f13e5398d928332a352ff1cb44a0398af`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`|`ff14e0d4a4315bdfeb81ba8e022240bbd157874701977bc81deaa4c2921b9f38`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt`|`eda832e21658985fd417d4f3b0cdb8aec29cf65c3da6380d4e4ccc3c11672ad0`|
|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt`|`25eb98fd3d0769b9bf6cc486dce16abb4f582f1704c6e867f2151c620080fac3`|
|`src/main/resources/static/mailbox-chat.js`|`e500ed4a0ff64ce1dd5bd4d4954583bf18f7ff1504b90e20e87e5e3bbba53cd4`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`|`a91d2661f6aec624f76ca6b5c32ec3a08d2e7359d251e36dd0d98d412fa39fda`|
|`src/main/resources/static/app.js`|`586ef6106c5efbd87f380729d8f96048ac8b6466b642c4af4b579cfd75fbc9b8`|
|`src/main/resources/static/styles.css`|`1a02383b30a8937a4614624e87eaefdd366f4c4e7294665b96a05c9928f86263`|
|`src/main/resources/static/mailbox-chat.css`|`a03e892dda28d2274da2011354bdc602df247e236f672039ee9edce5d910d4b4`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt`|`5c54e84e029c5b67ea7e242c9b067ec433c64d75fc1a4e03a422f8b85fd3f3a8`|
|`src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt`|`5cd9817f24a2f4a2384d1f06cde944686448886f67cdac2d617bf0fe2324b003`|
|`src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`|`f41a744f244f28346dfa4f8c1da37abe2cfd795c496699edf5f9ac8679a78ebb`|
|`src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt`|`10b7714c464cf40cd4276d7d6a6c4e4787c1c50b34e3535cf5995b7cb69c481e`|
|`src/main/resources/static/index.html`|`af64e5757f98499dfb9f46ca55118d522fd1b5b7ef5170fb1693da8259e741eb`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt`|`36aac4afe0d1f746dfbd1ebfff5f8a550583ac94c43f135042eedd4f50d42411`|
|`src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql`|`72b07416d0270edd2f7c64aa848f6a3fa3c05b18773a7765062ca3a3cbf1d6e9`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt`|`8a1ee426e1f8405285dda4daf522c9a2699b601b26a76379bc88b6edf8f2d95e`|
|`src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`|`c86e89511ec607120a4119b99b90939d8ef7d6498efbad875e25e4a69a540143`|
|`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt`|`da4d7e26327fb2c27ba140c9644aaae3c923a22123604a3a87a4a1b2fbd1155a`|