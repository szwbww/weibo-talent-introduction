# 回复片段变体计划：代码证据附件

2026-09-23 工作树只读快照。不是线上数据库快照；不是实施完成证明。行号随并行工作变化，以方法名和摘录复核。

## E-1 仓库与读写入口

```text
$ git rev-parse HEAD
5f34f4b116a1277fd0e5df52257c79e7e7e0c1a2

[exit=0]
```

```text
$ rg -n MailComposeTemplateRepository|MailComposeTemplateBlockRepository|ReplySnippetRepository|ContentVariantRepository src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/variant/repository/ContentVariantRepository.kt:6:interface ContentVariantRepository : CrudRepository<ContentVariant, Long> {
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:13:import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:17:import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:18:import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:29:    private val templateRepository: MailComposeTemplateRepository,
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:30:    private val blockRepository: MailComposeTemplateBlockRepository,
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:32:    private val replySnippetRepository: ReplySnippetRepository,
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:6:import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:13:    private val contentVariantRepository: ContentVariantRepository,
src/main/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateRepository.kt:9:interface MailComposeTemplateRepository : CrudRepository<MailComposeTemplate, Long> {
src/main/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateRepository.kt:17:interface MailComposeTemplateBlockRepository : CrudRepository<MailComposeTemplateBlock, Long> {
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:5:import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:16:    private val repository: ReplySnippetRepository,
src/main/kotlin/com/weibo/talentintroduction/reply/repository/ReplySnippetRepository.kt:6:interface ReplySnippetRepository : CrudRepository<ReplySnippet, Long> {
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:22:import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:42:    private val templateRepository: MailComposeTemplateRepository,

[exit=0]
```

```text
$ rg -n templateRepository\.|blockRepository\.|replySnippetRepository\. src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt
48:        templateRepository.findAllByOrderByIdAsc().map { toDetail(it) }
51:        templateRepository.findAllByEnabledTrueOrderByIdAsc()
62:        val saved = templateRepository.save(
88:        templateRepository.save(
103:        blockRepository.deleteAllByTemplateId(id)
112:        templateRepository.save(
132:            templateRepository.deleteById(id)
139:        blockRepository.deleteAllByTemplateId(id)
155:        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
164:        val template = templateRepository.findByTemplateCodeAndEnabledTrue(templateCode)
167:        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
227:        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
240:                val snippet = refId?.let { replySnippetRepository.findById(it).orElse(null) }
277:        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
432:        templateRepository.findById(id).orElseThrow { error("Compose template not found: $id") }
436:        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
478:                replySnippetRepository.findById(refId).orElse(null)?.let { snippet ->
502:            blockRepository.save(
602:                    val snippet = replySnippetRepository.findById(refId).orElse(null)

[exit=0]
```

```text
$ rg -n repository\.(save|delete|find)|contentVariantService\. src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt
21:        repository.findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc().map { toDetail(it) }
25:        return repository.findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType.uppercase())
46:        val snippet = repository.findById(ackSnippetId).orElse(null) ?: return null
60:            repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(type.name)
116:        repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
126:        val snippet = repository.findById(id).orElse(null)
192:        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)
195:        val saved = repository.save(
212:        contentVariantService.replaceForOwner(
231:        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)
233:        val updated = repository.save(
246:        contentVariantService.replaceForOwner(
257:        return toDetail(repository.save(existing.copy(enabled = enabled)))
267:        repository.findBySnippetTypeAndIsDefaultTrue(existing.snippetType)
269:            .forEach { repository.save(it.copy(isDefault = false)) }
271:        return toDetail(repository.save(existing.copy(isDefault = true)))
277:        contentVariantService.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, id)
278:        repository.deleteById(id)
285:            variants = contentVariantService.listByOwner(ContentVariantOwnerType.REPLY_SNIPPET, snippetId)
291:        repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
297:        repository.findBySnippetTypeAndIsDefaultTrue(saved.snippetType)
299:            .forEach { repository.save(it.copy(isDefault = false)) }
303:        repository.findById(id).orElseThrow { error("Reply snippet not found: $id") }

[exit=0]
```

```text
$ rg -n contentVariantService\. src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:192:        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:212:        contentVariantService.replaceForOwner(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:231:        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:246:        contentVariantService.replaceForOwner(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:277:        contentVariantService.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, id)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:285:            variants = contentVariantService.listByOwner(ContentVariantOwnerType.REPLY_SNIPPET, snippetId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt:105:        val resolvedBody = contentVariantService.resolveBody(
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:130:        contentVariantService.deleteForOwner(ContentVariantOwnerType.QA_RULE, ruleId)
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt:166:        contentVariantService.listByOwner(ContentVariantOwnerType.QA_RULE, ruleId).map { it.content }
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:614:                    val resolvedContent = contentVariantService.resolveBody(
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:622:                        contentVariantService.poolSize(ContentVariantOwnerType.REPLY_SNIPPET, refId, snippet.content)

[exit=0]
```

```text
$ rg -n -i mail_compose_template|reply_snippet|content_variant src/main/resources/db/migration scripts
src/main/resources/db/migration/V47__create_reply_snippet.sql:1:CREATE TABLE reply_snippet (
src/main/resources/db/migration/V47__create_reply_snippet.sql:12:INSERT INTO reply_snippet (snippet_type, content, display_order, is_default, enabled)
src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql:8:UPDATE mail_compose_template_block b
src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql:9:JOIN mail_compose_template t ON t.id = b.template_id
src/main/resources/db/migration/V78__decouple_compose_templates_from_qa_rule.sql:4:UPDATE mail_compose_template_block b
src/main/resources/db/migration/V67__create_content_variant.sql:1:CREATE TABLE content_variant (
src/main/resources/db/migration/V67__create_content_variant.sql:3:    owner_type VARCHAR(32) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET',
src/main/resources/db/migration/V61__create_mail_compose_template.sql:1:CREATE TABLE mail_compose_template (
src/main/resources/db/migration/V61__create_mail_compose_template.sql:11:CREATE TABLE mail_compose_template_block (
src/main/resources/db/migration/V61__create_mail_compose_template.sql:15:    block_type VARCHAR(30) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET | CUSTOM_TEXT',
src/main/resources/db/migration/V61__create_mail_compose_template.sql:16:    ref_id BIGINT NULL COMMENT 'qa_rule.id 或 reply_snippet.id，CUSTOM_TEXT 时为 NULL',
src/main/resources/db/migration/V61__create_mail_compose_template.sql:18:    FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql:7:UPDATE mail_compose_template_block b
src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql:8:JOIN mail_compose_template t ON t.id = b.template_id
src/main/resources/db/migration/V71__update_material_reminder_template.sql:24:UPDATE mail_compose_template
src/main/resources/db/migration/V71__update_material_reminder_template.sql:33:FROM mail_compose_template_block b
src/main/resources/db/migration/V71__update_material_reminder_template.sql:34:JOIN mail_compose_template t ON t.id = b.template_id
src/main/resources/db/migration/V71__update_material_reminder_template.sql:37:INSERT INTO mail_compose_template_block (
src/main/resources/db/migration/V71__update_material_reminder_template.sql:50:FROM mail_compose_template t
src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql:1:-- V84: add required_keys to mail_compose_template for the send-side personalization gate.
src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql:5:ALTER TABLE mail_compose_template
src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql:1:ALTER TABLE reply_snippet
src/main/resources/db/migration/V62__unify_mail_templates.sql:1:ALTER TABLE mail_compose_template
src/main/resources/db/migration/V62__unify_mail_templates.sql:4:    ADD UNIQUE KEY uk_mail_compose_template_code (template_code);
src/main/resources/db/migration/V62__unify_mail_templates.sql:6:INSERT INTO mail_compose_template (
src/main/resources/db/migration/V62__unify_mail_templates.sql:40:FROM mail_compose_template_block b
src/main/resources/db/migration/V62__unify_mail_templates.sql:41:JOIN mail_compose_template t ON t.id = b.template_id
src/main/resources/db/migration/V62__unify_mail_templates.sql:49:INSERT INTO mail_compose_template_block (
src/main/resources/db/migration/V62__unify_mail_templates.sql:62:FROM mail_compose_template t
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:30:        FOREIGN KEY (template_id) REFERENCES mail_compose_template(id)
src/main/resources/db/migration/V72__create_batch_send_task_config.sql:131:        (SELECT id FROM mail_compose_template WHERE template_code = 'MATERIAL_REMINDER' LIMIT 1)
src/main/resources/db/migration/V64__add_subject_variants_and_snippet_variant_group.sql:1:ALTER TABLE mail_compose_template ADD COLUMN subject_variants TEXT NULL COMMENT 'JSON 数组: subject 变体列表，为空时使用 subject 字段';
src/main/resources/db/migration/V64__add_subject_variants_and_snippet_variant_group.sql:2:ALTER TABLE reply_snippet ADD COLUMN variant_group VARCHAR(64) NULL COMMENT '变体组标识，同组 snippet 按确定性规则选一个';
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:13:-- 正文 SSOT 是 mail_compose_template_block.custom_text；本文件不写 mail_template。
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:16:INSERT INTO mail_compose_template (
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:38:    FROM mail_compose_template
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:42:INSERT INTO mail_compose_template_block (
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:68:FROM mail_compose_template t
src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql:73:      FROM mail_compose_template_block b

[exit=0]
```

## E-2 渲染、框架与测试入口

```text
$ rg -n mailComposeTemplateService\.(render|effectiveRequiredKeys)|resolveSelectableFrame\(|resolveDefaultSelectableFrame\(|resolveManualFrame\(|resolveAck\( src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:227:        val rendered = mailComposeTemplateService.render(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:236:        val requiredKeys = (mailComposeTemplateService.effectiveRequiredKeys(templateId) ?: emptyList())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:119:        val rendered = mailComposeTemplateService.renderByCode(
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:29:    fun resolveManualFrame(): ManualReplyFrame =
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:42:    fun resolveAck(ackSnippetId: Long?): String? {
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:83:    fun resolveDefaultSelectableFrame(): ResolvedReplyFrame {
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:90:        return resolveSelectableFrame(selection)
src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:100:    fun resolveSelectableFrame(selection: ReplyFrameSelection): ResolvedReplyFrame {
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt:277:            replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt:279:            replySnippetService.resolveSelectableFrame(frameSelection)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1156:        val rendered = mailComposeTemplateService.renderByCode(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:194:        return mailComposeTemplateService.renderWithVariables(html, variables)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt:208:        val rendered = mailComposeTemplateService.renderWithVariables(text, variables)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:16:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:210:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:227:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt:238:            replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let {
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2402:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2405:        replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let { appendLine("ACK=$it") }
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:2584:        val frame = replySnippetService.resolveManualFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1150:            val resolved = replySnippetService.resolveSelectableFrame(selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1173:            val resolved = replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1180:            val resolved = replySnippetService.resolveSelectableFrame(snapshot.selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1200:            return replySnippetService.resolveDefaultSelectableFrame()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:1203:            val resolved = replySnippetService.resolveSelectableFrame(snapshot.selection.toReplyFrameSelection())
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:247:            mailComposeTemplateService.renderByCode(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:93:                val rendered = mailComposeTemplateService.renderByCode(
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:22:            mailComposeTemplateService.render(templateId, variables, variantSeed)
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:24:            mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:28:        val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt:14:        val rendered = mailComposeTemplateService.renderByCode(

[exit=0]
```

```text
$ rg -n variantIndex|variantPoolSize|renderContentVariantRows|content-variant-input src/test/js
src/test/js/qaFactCardEditor.test.js:131:        assert.ok(appJsSource.includes("function renderContentVariantRows"));
src/test/js/composeTemplatePreview.test.js:52:                variantIndex: 0,
src/test/js/composeTemplatePreview.test.js:53:                variantPoolSize: 1
src/test/js/composeTemplatePreview.test.js:81:            variantPoolSize: 1,
src/test/js/composeTemplatePreview.test.js:233:            variantPoolSize: 1,
src/test/js/composeTemplatePreview.test.js:256:            variantPoolSize: 1,
src/test/js/composeTemplatePreview.test.js:276:                    variantPoolSize: 1,
src/test/js/composeTemplatePreview.test.js:316:                    variantPoolSize: 1,
src/test/js/expertMailPreviewTab.test.js:50:                variantIndex: 0,
src/test/js/expertMailPreviewTab.test.js:51:                variantPoolSize: 1
src/test/js/expertMailPreviewTab.test.js:249:    it("renderExpertMailPreview derives variantIndex from the trimmed ORCID via Java hashCode (V-2)", async () => {
src/test/js/expertMailPreviewTab.test.js:265:        assert.equal(captured.variantIndex, -2035179089);

[exit=0]
```

```text
$ rg -n -i CREATE TABLE.*mail_compose_template|subject_variants src/test --glob *.kt --glob *.sql
src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt:73:            CREATE TABLE mail_compose_template (
src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt:88:            CREATE TABLE mail_compose_template_block (

[exit=0]
```

## E-3 静态资源与选择器回执

```text
$ rg -n \?v= src/main/resources/static/index.html
11:    <link rel="stylesheet" href="styles.css?v=20260923-discovery-traffic">
12:    <link rel="stylesheet" href="expert-materials.css?v=20260923-discovery-traffic">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20260923-discovery-traffic">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20260923-discovery-traffic">
15:    <link rel="stylesheet" href="world-clock.css?v=20260923-discovery-traffic">
2195:<script src="trust-reply-workbench.js?v=20260923-discovery-traffic"></script>
2196:<script src="expert-materials.js?v=20260923-discovery-traffic"></script>
2197:<script src="meeting-confirmation.js?v=20260923-discovery-traffic"></script>
2198:<script src="mailbox-chat.js?v=20260923-discovery-traffic"></script>
2199:<script src="app.js?v=20260923-discovery-traffic"></script>
2200:<script src="world-clock.js?v=20260923-discovery-traffic"></script>

[exit=0]
```

```text
$ rg -n -F 20260923-discovery-traffic src/test

[exit=1]
```

```text
$ rg -n content-variants-block|content-variant-row|var-insert-btn|compose-template-fields|previewVariant src/main/resources/static/index.html src/main/resources/static/app.js src/main/resources/static/styles.css
src/main/resources/static/index.html:1924:                            <button type="button" class="var-insert-btn" data-var-insert-target="qaRuleAnswerBody">+ 插入变量 ▾</button>
src/main/resources/static/index.html:1968:                            <button type="button" class="var-insert-btn" data-var-insert-target="replySnippetContent">+ 插入变量 ▾</button>
src/main/resources/static/index.html:1976:            <div class="full-width content-variants-block">
src/main/resources/static/index.html:2014:                        <div class="compose-template-fields">
src/main/resources/static/index.html:2020:                                            <button type="button" class="var-insert-btn" data-var-insert-target="composeTemplateSubject">+ 插入变量 ▾</button>
src/main/resources/static/index.html:2111:        <div class="variant-switcher" id="previewVariantSwitcher" hidden>
src/main/resources/static/index.html:2112:            <button type="button" class="button small" id="previewVariantPrev" aria-label="上一个变体组合">‹</button>
src/main/resources/static/index.html:2113:            <span class="badge primary variant-switcher-label" id="previewVariantLabel">组合 1/1</span>
src/main/resources/static/index.html:2114:            <button type="button" class="button small" id="previewVariantNext" aria-label="下一个变体组合">›</button>
src/main/resources/static/styles.css:5889:.var-insert-btn {
src/main/resources/static/styles.css:5901:.var-insert-btn:hover {
src/main/resources/static/styles.css:6564:.compose-template-fields {
src/main/resources/static/styles.css:6570:.compose-template-fields .full-width {
src/main/resources/static/styles.css:6574:.compose-template-fields textarea {
src/main/resources/static/styles.css:6585:.content-variant-row {
src/main/resources/static/styles.css:6605:.content-variant-row .content-variant-input {
src/main/resources/static/styles.css:6650:.content-variant-rows .content-variant-row[hidden] {
src/main/resources/static/app.js:3913:    const switcher = $("#previewVariantSwitcher");
src/main/resources/static/app.js:3914:    const label = $("#previewVariantLabel");
src/main/resources/static/app.js:4188:        const targetId = wrap.querySelector(".var-insert-btn")?.dataset.varInsertTarget;
src/main/resources/static/app.js:10879:        <div class="content-variant-row${isActive ? " active" : ""}" data-variant-index="${index}"${isActive ? "" : " hidden"}>
src/main/resources/static/app.js:10897:            <div class="content-variant-rows">
src/main/resources/static/app.js:10909:    const rows = Array.from(container.querySelectorAll(".content-variant-row"));
src/main/resources/static/app.js:10942:    const badge = container.closest(".content-variants-block")?.querySelector(".content-variants-count");
src/main/resources/static/app.js:10980:        const row = inputs[index].closest(".content-variant-row");
src/main/resources/static/app.js:11011:        const row = firstDuplicate?.closest(".content-variant-row");
src/main/resources/static/app.js:11026:    container.querySelector(".content-variant-row.active .content-variant-input")?.focus();
src/main/resources/static/app.js:11041:        const container = addBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
src/main/resources/static/app.js:11047:        const container = removeBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
src/main/resources/static/app.js:11053:        const container = prevBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
src/main/resources/static/app.js:11059:        const container = nextBtn.closest(".content-variants-block")?.querySelector(".content-variants-container");
src/main/resources/static/app.js:11065:        const container = dot.closest(".content-variants-block")?.querySelector(".content-variants-container");
src/main/resources/static/app.js:11317:            ${blockType === "CUSTOM_TEXT" ? `<div class="var-editor-wrap"><div class="var-editor-toolbar"><div class="var-insert-wrap"><button type="button" class="var-insert-btn" data-var-insert-target="composeBlockCustomText-${index}">+ 插入变量 ▾</button><div class="var-insert-menu" hidden></div></div></div><textarea id="composeBlockCustomText-${index}" data-field="customText" rows="4" placeholder="输入自定义文本">${escapeHtml(block.customText || "")}</textarea></div>` : ""}
src/main/resources/static/app.js:14235:        const insertBtn = event.target.closest(".var-insert-btn");
src/main/resources/static/app.js:14266:    $("#previewVariantPrev")?.addEventListener("click", () => stepPreviewVariantIndex(-1));
src/main/resources/static/app.js:14267:    $("#previewVariantNext")?.addEventListener("click", () => stepPreviewVariantIndex(1));

[exit=0]
```

```text
$ rg -n renderContentVariantRows|collectContentVariants|validateContentVariantInputs|addContentVariantRow|removeContentVariantRow src/main/resources/static/app.js
5857:    renderContentVariantRows($("#replySnippetVariantsContainer"), []);
5888:    renderContentVariantRows($("#replySnippetVariantsContainer"), snippet?.variants || []);
5907:    if (!validateContentVariantInputs(variantsContainer, mainText)) {
5917:        variants: collectContentVariants(variantsContainer)
10855:function renderContentVariantRows(container, variants) {
10944:    const variantCount = collectContentVariants(container).length;
10954:function collectContentVariants(container) {
10969:function validateContentVariantInputs(container, mainText) {
11020:function addContentVariantRow(container) {
11025:    renderContentVariantRows(container, variants);
11029:function removeContentVariantRow(container, index) {
11035:    renderContentVariantRows(container, variants);
11042:        if (container) addContentVariantRow(container);
11048:        if (container) removeContentVariantRow(container, Number(removeBtn.dataset.index));

[exit=0]
```

## E-4 关键代码原文

### src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:16

```text
16:     fun resolveBody(
17:         ownerType: String,
18:         ownerId: Long?,
19:         mainBody: String,
20:         seed: Int,
21:         useVariants: Boolean = true
22:     ): String {
23:         val pool = buildPool(ownerType, ownerId, mainBody, useVariants)
24:         if (pool.size <= 1) {
25:             return mainBody
26:         }
27:         val index = Math.floorMod(seed + ownerId!!, pool.size)
28:         return pool[index]
29:     }
30: 
31:     fun poolSize(ownerType: String, ownerId: Long?, mainBody: String, useVariants: Boolean = true): Int =
32:         buildPool(ownerType, ownerId, mainBody, useVariants).size
33: 
34:     fun listByOwner(ownerType: String, ownerId: Long): List<ContentVariant> =
35:         contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(ownerType, ownerId)
```

### src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:89

```text
89: 
90:     private fun buildPool(
91:         ownerType: String,
92:         ownerId: Long?,
93:         mainBody: String,
94:         useVariants: Boolean
95:     ): List<String> {
96:         if (!useVariants || ownerId == null || !ContentVariantOwnerType.isKnown(ownerType)) {
97:             return listOf(mainBody)
98:         }
99:         val variants = contentVariantRepository
100:             .findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(ownerType, ownerId)
101:         if (variants.isEmpty()) {
102:             return listOf(mainBody)
103:         }
104:         return listOf(mainBody) + variants.map { it.content }
105:     }
106: }
```

### src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:178

```text
178:     fun effectiveRequiredKeys(templateId: Long): List<String> {
179:         val pool = renderTextPool(findTemplate(templateId))
180:         val keys = linkedSetOf<String>()
181:         keys.addAll(mailPlaceholderService.requiredKeysIn(pool.subject))
182:         pool.blockTexts.flatten().forEach { text ->
183:             keys.addAll(mailPlaceholderService.requiredKeysIn(text))
184:         }
185:         return keys.toList()
186:     }
187: 
188:     /**
189:      * I-2: the subset of [effectiveRequiredKeys] that is required in EVERY possible
190:      * render (`ALLOWED_HAS_FIELDS` prefilter input) mapped through the variable→ES-field
191:      * table, deduplicated in stable order. A key that only some snippet variant makes
192:      * mandatory — or that a whole block can be skipped without — is not a field the ES
193:      * prefilter may exclude experts on; the send gate still rejects those recipients.
194:      */
195:     fun requiredEsFields(templateId: Long): List<String> =
196:         alwaysRequiredKeys(findTemplate(templateId))
197:             .mapNotNull { MailPlaceholderService.ES_FIELD_BY_KEY[it] }
198:             .distinct()
199: 
200:     private fun alwaysRequiredKeys(template: MailComposeTemplate): List<String> {
201:         val pool = renderTextPool(template)
202:         val keys = linkedSetOf<String>()
203:         keys.addAll(mailPlaceholderService.requiredKeysIn(pool.subject))
204:         pool.blockTexts.forEach { texts ->
205:             val requiredByEveryVariant = texts
206:                 .map { mailPlaceholderService.requiredKeysIn(it).toSet() }
207:                 .reduce { acc, next -> acc intersect next }
208:             mailPlaceholderService.requiredKeysIn(texts.first())
209:                 .filter { it in requiredByEveryVariant }
210:                 .forEach { keys.add(it) }
211:         }
212:         return keys.toList()
213:     }
214: 
215:     private data class RenderTextPool(
216:         val subject: String,
217:         val blockTexts: List<List<String>>
218:     )
219: 
220:     /**
221:      * Every text the template can render: the subject plus, per enabled block, each text
222:      * that block may contribute. Blocks whose reference is missing or disabled are
223:      * dropped — they render nothing and therefore can never require a variable.
224:      */
225:     private fun renderTextPool(template: MailComposeTemplate): RenderTextPool {
226:         val templateId = template.id ?: error("Compose template id is required")
227:         val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
228:             .sortedBy { it.blockOrder }
229:         return RenderTextPool(
230:             subject = template.subject,
231:             blockTexts = blocks.mapNotNull { possibleRenderTexts(it) }
232:         )
233:     }
234: 
235:     private fun possibleRenderTexts(block: MailComposeTemplateBlock): List<String>? =
236:         when (block.blockType) {
237:             ComposeBlockType.CUSTOM_TEXT -> listOf(block.customText.orEmpty())
238:             ComposeBlockType.REPLY_SNIPPET -> {
239:                 val refId = block.refId
240:                 val snippet = refId?.let { replySnippetRepository.findById(it).orElse(null) }
241:                 if (snippet == null || !snippet.enabled) {
242:                     null
243:                 } else {
244:                     val variants = contentVariantService
245:                         .listByOwner(ContentVariantOwnerType.REPLY_SNIPPET, refId)
246:                         .filter { it.enabled }
247:                         .map { it.content }
248:                     listOf(snippet.content) + variants
249:                 }
250:             }
251:             ComposeBlockType.QA_RULE -> {
252:                 val rule = block.refId?.let { qaRuleRepository.findById(it).orElse(null) }
253:                 if (rule == null || !rule.enabled) null else listOf(rule.replyBody)
```

### src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:258

```text
258:     private fun renderTemplate(
259:         template: MailComposeTemplate,
260:         blocks: List<MailComposeTemplateBlock>,
261:         variables: Map<String, String>,
262:         variantSeed: Int = 0
263:     ): ComposeTemplateRenderResult {
264:         val resolved = resolveBlocks(blocks.map { it.toDraftBlock() }, variables, variantSeed)
265:         return ComposeTemplateRenderResult(
266:             subject = renderText(template.subject, variables),
267:             body = resolved.includedTexts.joinToString("\n\n"),
268:             qaRuleIds = resolved.qaRuleIds,
269:             mailType = template.mailType,
270:             rawTexts = listOf(template.subject) + resolved.rawTexts.values,
271:             templateId = template.id
272:         )
273:     }
274: 
275:     fun preview(id: Long): ComposeTemplatePreviewResult {
276:         val template = findTemplate(id)
277:         val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
278:             .map { it.toDraftBlock() }
279:         val resolved = resolveBlocks(blocks)
280:         return ComposeTemplatePreviewResult(
281:             subject = template.subject,
282:             body = resolved.includedTexts.joinToString("\n\n"),
283:             blocks = resolved.previewBlocks
284:         )
285:     }
286: 
287:     fun previewDraft(request: ComposeTemplatePreviewDraftRequest): ComposeTemplatePreviewDraftResult {
288:         request.blocks.forEach { block ->
289:             validateBlockCommand(
290:                 MailComposeTemplateBlockCommand(
291:                     blockOrder = block.blockOrder,
292:                     blockType = block.blockType,
293:                     refId = block.refId,
294:                     customText = block.customText
295:                 )
296:             )
297:         }
298:         val variantSeed = request.variantIndex ?: 0
299:         val draftBlocks = request.blocks.map { block ->
300:             ComposeDraftBlock(
301:                 blockOrder = block.blockOrder,
302:                 blockType = block.blockType.uppercase(),
303:                 refId = block.refId,
304:                 customText = block.customText
305:             )
306:         }
307:         val baseResolved = resolveBlocks(draftBlocks, variantSeed = variantSeed, renderVariables = false)
308:         val subjectTemplate = request.subject
309:         val contact = resolvePreviewContact(request.contactId, request.orcidId, request.expertEmail)
310:         val account = resolvePreviewAccount(request.senderAccountCode, contact)
311: 
312:         if (contact == null) {
313:             val texts = listOf(subjectTemplate) + baseResolved.rawTextsByOrder.values
314:             return ComposeTemplatePreviewDraftResult(
315:                 subject = subjectTemplate,
316:                 body = baseResolved.includedTexts.joinToString("\n\n"),
317:                 blocks = baseResolved.previewBlocks,
318:                 fallbackKeys = mailVariableService.placeholderKeysIn(*texts.toTypedArray()),
319:                 toEmail = null,
320:                 variables = emptyList(),
321:                 variantPoolSize = baseResolved.variantPoolSize
322:             )
```

### src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:541

```text
541:     private fun resolveBlocks(
542:         blocks: List<ComposeDraftBlock>,
543:         variables: Map<String, String> = emptyMap(),
544:         variantSeed: Int = 0,
545:         renderVariables: Boolean = true
546:     ): ResolvedBlocks {
547:         val includedTexts = mutableListOf<String>()
548:         val qaRuleIds = mutableListOf<Long>()
549:         val previewBlocks = mutableListOf<ComposeTemplatePreviewBlock>()
550:         val rawTextsByOrder = mutableMapOf<Int, String>()
551:         val rawTexts = mutableMapOf<Int, String>()
552:         var variantPoolSize = 1
```

### src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:602

```text
602:                     val snippet = replySnippetRepository.findById(refId).orElse(null)
603:                     if (snippet == null) {
604:                         previewBlocks += skippedPreviewBlock(block, "回复片段不存在", refId, null)
605:                         return@forEach
606:                     }
607:                     val displayName = snippet.name?.takeIf { it.isNotBlank() }
608:                         ?: snippetContentExcerpt(snippet.content)
609:                         ?: "${snippet.snippetType} #${snippet.id}"
610:                     if (!snippet.enabled) {
611:                         previewBlocks += skippedPreviewBlock(block, "已禁用", refId, displayName)
612:                         return@forEach
613:                     }
614:                     val resolvedContent = contentVariantService.resolveBody(
615:                         ContentVariantOwnerType.REPLY_SNIPPET,
616:                         refId,
617:                         snippet.content,
618:                         variantSeed
619:                     )
620:                     variantPoolSize = maxOf(
621:                         variantPoolSize,
622:                         contentVariantService.poolSize(ContentVariantOwnerType.REPLY_SNIPPET, refId, snippet.content)
623:                     )
624:                     rawTexts[block.blockOrder] = resolvedContent
625:                     val text = if (renderVariables) {
626:                         renderText(resolvedContent, variables).trim()
627:                     } else {
628:                         resolvedContent.trim()
629:                     }
630:                     rawTextsByOrder[block.blockOrder] = text
631:                     if (text.isNotBlank()) {
632:                         includedTexts += text
633:                     }
634:                     previewBlocks += ComposeTemplatePreviewBlock(
635:                         blockOrder = block.blockOrder,
```

### src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt:16

```text
16: ) {
17:     fun compose(accountCode: String, expert: ExpertProfile, templateId: Long? = null): ComposedMail {
18:         val account = mailSenderAccountService.getEnabledAccount(accountCode)
19:         val variables = buildVariables(account, expert)
20:         val variantSeed = expert.orcidId.hashCode()
21:         val rendered = if (templateId != null) {
22:             mailComposeTemplateService.render(templateId, variables, variantSeed)
23:         } else {
24:             mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
25:         }
26: 
27:         val gateTemplateId = templateId ?: rendered.templateId
28:         val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
29:         val gate = personalizationGateService.evaluate(rendered.rawTexts, variables, requiredKeys)
30:         if (gate.blocked) {
31:             throw PersonalizationGateException(gate.missingKeys)
32:         }
33: 
34:         val domain = account.senderEmail.substringAfter("@")
35:         val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"
36: 
37:         val plain = rendered.body
38:         val mail = ComposedMail(
39:             to = expert.email ?: error("Expert email is required for introduction mail"),
40:             subject = rendered.subject,
41:             body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
42:             html = true,
43:             text = plain,
44:             messageId = messageId
45:         )
46:         personalizationGateService.requireNoPlaceholderResidue(mail.subject, plain)
47:         return mail
48:     }
49: 
50:     fun buildTemplateVariables(expert: ExpertProfile, accountCode: String?): List<TemplateVariableItem> {
51:         val account = accountCode?.let { mailSenderAccountService.getEnabledAccount(it) }
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:699

```text
699:                     val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
700:                     val mail = try {
701:                         introductionMailComposer.compose(account.accountCode, expert, config.templateId)
702:                             .copy(messageId = messageId)
703:                     } catch (e: PersonalizationGateException) {
704:                         log.info("Personalization gate blocked ORCID {}: missing keys {}", normOrcid, e.missingKeys)
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:727

```text
727:                             roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
728:                         continue
729:                     }
730: 
731:                     // 4. Persist attempt as PREPARED (audit trail) — upsert to respect UNIQUE(orcid_id, mail_type) (I-7)
732:                     val now = LocalDateTime.now()
733:                     val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
734:                     val attempt = mailSendAttemptRepository.save(
735:                         if (existingAttempt != null) {
736:                             existingAttempt.copy(
737:                                 accountCode = account.accountCode, messageId = messageId,
738:                                 status = MailSendAttemptStatus.PREPARED, errorSummary = null,
739:                                 updatedAt = now
740:                             )
741:                         } else {
742:                             MailSendAttempt(
743:                                 orcidId = normOrcid, mailType = "INTRODUCTION",
744:                                 accountCode = account.accountCode, messageId = messageId,
745:                                 status = MailSendAttemptStatus.PREPARED,
746:                                 createdAt = now, updatedAt = now
747:                             )
748:                         }
749:                     )
750: 
751:                     // 5. Send via SMTP
752:                     val delivered = mailDeliveryService.send(account, mail)
753:                     if (delivered.status == "SENT") {
754:                         accountRateLimiter.recordSuccess(account.accountCode, provider, config.perMailIntervalMs)
755:                         // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
```

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1077

```text
1077:             val retryableContacts = newContacts.filter {
1078:                 !hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"
1079:             }
1080:             val orcidIds = retryableContacts.map { it.orcidId }
1081:             // I-3/I-4: 预估与执行共用本构造函数 —— 同一 ORCID 的任一 campaign 行已绑定即排除。
1082:             val boundOrcids = boundOrcidsOf(orcidIds)
1083:             val profilesByLevel = if (orcidIds.isEmpty()) {
1084:                 emptyMap()
1085:             } else {
1086:                 scope.funnelLevels.associateWith { level ->
1087:                     expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level))
1088:                         .associateBy { normalizeOrcid(it.orcidId) }
1089:                 }
1090:             }
1091:             for (contact in retryableContacts) {
1092:                 val normOrcid = normalizeOrcid(contact.orcidId)
1093:                 if (normOrcid in boundOrcids) continue
1094:                 val profile = scope.funnelLevels.asSequence()
1095:                     .mapNotNull { level -> profilesByLevel[level]?.get(normOrcid) }
```

### src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:15

```text
15:     private val emailSuppressionService: EmailSuppressionService
16: ) : MailDeliveryService {
17:     override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
18:         // I-1: 兜底 fail-closed 拦截。必须位于接触任何 SMTP 资源（getSender）之前；
19:         // 命中且未显式 override 时抛异常，绝不返回 DeliveredMail（I-2）。
20:         if (!mail.allowSuppressedRecipient && emailSuppressionService.isSuppressed(mail.to)) {
21:             throw RecipientSuppressedException(mail.to)
22:         }
23: 
24:         val sender = smtpSenderFactory.getSender(account)
25: 
```

### src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:42

```text
42:         } else {
43:             message.setFrom(account.senderEmail)
44:         }
45:         message.setRecipients(javax.mail.Message.RecipientType.TO, mail.to)
46:         message.subject = mail.subject
47:         mail.inReplyTo?.takeIf { it.isNotBlank() }?.let { message.setHeader("In-Reply-To", it) }
48:         mail.references?.takeIf { it.isNotBlank() }?.let { message.setHeader("References", it) }
49:         val calendar = mail.calendarAttachment
50:         val outboundAttachments = mail.outboundAttachments
51:         if (calendar == null && outboundAttachments.isEmpty()) {
52:             // fast-p 02 (I-3): 无附件分支逐字保留旧实现（正文/MIME 与旧状态机完全一致）。
53:             if (mail.html) {
54:                 val plain = mail.text?.takeIf { it.isNotBlank() }
55:                     ?: mailContentService.htmlToPlainText(mail.body)
56:                 val multipart = javax.mail.internet.MimeMultipart("alternative")
57:                 multipart.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
58:                     setText(plain, Charsets.UTF_8.name())
59:                 })
60:                 multipart.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
61:                     setContent(mail.body, "text/html; charset=UTF-8")
62:                 })
63:                 message.setContent(multipart)
64:             } else {
65:                 message.setText(mail.body, Charsets.UTF_8.name())
66:             }
```

### src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:105

```text
105:             message.addHeader("List-Unsubscribe", "<$httpsUrl>, <$mailto>")
106:             message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
107:         }
108: 
109:         return try {
110:             sender.send(message)
111:             DeliveredMail(
112:                 messageId = message.messageID ?: mail.messageId,
113:                 status = "SENT"
114:             )
115:         } catch (e: SendFailedException) {
```

### src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:29

```text
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
41: 
42:     fun resolveAck(ackSnippetId: Long?): String? {
43:         if (ackSnippetId == null) {
44:             return null
45:         }
46:         val snippet = repository.findById(ackSnippetId).orElse(null) ?: return null
47:         if (!snippet.enabled || snippet.snippetType != SnippetType.ACK.name) {
48:             return null
49:         }
50:         return snippet.content.takeIf { it.isNotBlank() }
51:     }
52: 
53:     /**
54:      * I-1/I-3 options reader: enabled, non-blank, main snippets of the four
55:      * frame slots only, in fixed slot order then displayOrder then id.
56:      * CUSTOM and content variants are never selectable frame options.
57:      */
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
```

### src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:100

```text
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
```

### src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt:147

```text
147:     private fun frameVersion(
148:         salutation: ReplySnippet?,
149:         greeting: ReplySnippet?,
150:         ack: ReplySnippet?,
151:         closing: ReplySnippet?
152:     ): String {
153:         val canonical = listOf(
154:             frameSlotIdentity(SnippetType.SALUTATION, salutation),
155:             frameSlotIdentity(SnippetType.GREETING, greeting),
156:             frameSlotIdentity(SnippetType.ACK, ack),
157:             frameSlotIdentity(SnippetType.CLOSING, closing)
158:         ).joinToString("\u0001")
159:         return sha256Hex(canonical)
160:     }
161: 
162:     private fun frameSlotIdentity(slot: SnippetType, snippet: ReplySnippet?): String {
163:         if (snippet == null) {
164:             return "${slot.name}\u0000NULL"
165:         }
166:         return listOf(
167:             slot.name,
168:             snippet.id?.toString() ?: "NULL",
169:             snippet.snippetType,
170:             snippet.enabled.toString(),
171:             snippet.updatedAt?.toString().orEmpty(),
172:             sha256Hex(snippet.content)
173:         ).joinToString("\u0000")
174:     }
175: 
176:     private fun sha256Hex(input: String): String {
177:         val digest = MessageDigest.getInstance("SHA-256")
```

### src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt:64

```text
64:     @Autowired
65:     private lateinit var jdbcTemplate: JdbcTemplate
66: 
67:     @BeforeEach
68:     fun createSchema() {
69:         jdbcTemplate.execute("DROP TABLE IF EXISTS mail_compose_template_block")
70:         jdbcTemplate.execute("DROP TABLE IF EXISTS mail_compose_template")
71:         jdbcTemplate.execute(
72:             """
73:             CREATE TABLE mail_compose_template (
74:                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
75:                 template_code VARCHAR(64) NULL,
76:                 template_name VARCHAR(100) NOT NULL,
77:                 subject VARCHAR(255) NOT NULL,
78:                 description VARCHAR(500) NULL,
79:                 mail_type VARCHAR(64) NULL,
80:                 enabled TINYINT(1) NOT NULL DEFAULT 1,
81:                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
82:                 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
83:             )
84:             """.trimIndent()
85:         )
86:         jdbcTemplate.execute(
87:             """
88:             CREATE TABLE mail_compose_template_block (
89:                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
90:                 template_id BIGINT NOT NULL,
91:                 block_order INT NOT NULL,
92:                 block_type VARCHAR(30) NOT NULL,
93:                 ref_id BIGINT NULL,
94:                 custom_text TEXT NULL,
95:                 FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
96:             )
97:             """.trimIndent()
98:         )
99:     }
100: 
101:     @Test
```

### src/main/resources/static/app.js:11158

```text
11158:     }
11159:     return hash;
11160: }
11161: 
11162: async function renderExpertMailPreview(panel, orcidId) {
11163:     const templateId = panel.querySelector('[data-role="mail-preview-template"]')?.value || "";
11164:     const template = (state.composeTemplates || []).find((t) => String(t.id) === String(templateId));
11165:     if (!template) return;
11166:     const previewContact = (state.contacts || []).find((item) => item.orcidId === orcidId);
11167:     const previewContactId = previewContact?.contactId ?? null;
11168:     const payload = {
11169:         subject: template.subject || "",
11170:         blocks: (template.blocks || []).map((block) => ({
11171:             blockOrder: block.blockOrder,
11172:             blockType: block.blockType,
11173:             refId: block.refId ?? null,
11174:             customText: block.customText ?? null
11175:         })),
11176:         strictPlaceholders: false,
11177:         orcidId,
11178:         contactId: previewContactId,
11179:         expertEmail: null,
11180:         senderAccountCode: null,
11181:         variantIndex: javaStringHashCode(orcidId)
11182:     };
11183:     const requestId = ++expertMailPreviewRequestId;
```

### src/main/resources/static/app.js:11424

```text
11424:     const form = $("#composeTemplateForm");
11425:     if (!form) return;
11426:     const requestId = ++composeTemplatePreviewRequestId;
11427:     const blocks = collectComposeTemplateBlocksFromForm();
11428:     const context = collectComposeTemplatePreviewContext();
11429:     const strictPlaceholders = $("#previewComposeStrictPlaceholders")?.checked === true;
11430:     const payload = {
11431:         subject: form.subject.value || "",
11432:         blocks,
11433:         strictPlaceholders,
11434:         contactId: context.contactId,
11435:         orcidId: context.orcidId,
11436:         expertEmail: context.expertEmail,
11437:         senderAccountCode: context.senderAccountCode,
11438:         variantIndex: state.previewDrawer.variantIndex
11439:     };
11440:     try {
11441:         const result = await api("/api/compose-templates/preview-draft", {
```

### src/main/resources/static/app.js:11509

```text
11509:     const form = $("#composeTemplateForm");
11510:     const blocks = collectComposeTemplateBlocksFromForm();
11511:     if (!blocks.length) {
11512:         showStatus("请至少添加一个内容块", "error");
11513:         return;
11514:     }
11515:     const payload = {
11516:         templateName: form.templateName.value.trim(),
11517:         subject: form.subject.value.trim(),
11518:         description: form.description.value.trim() || null,
11519:         enabled: form.enabled.checked,
11520:         blocks
11521:     };
11522:     if (state.selectedComposeTemplateId) {
11523:         // I-3: an edit never rewrites the stored mail type; the server keeps it.
11524:         await api(`/api/compose-templates/${state.selectedComposeTemplateId}`, {
11525:             method: "PUT",
11526:             body: JSON.stringify(payload)
11527:         });
11528:     } else {
11529:         // I-3: new templates must be selectable by a batch task, which only accepts
11530:         // INTRODUCTION / MATERIAL_REMINDER.
11531:         await api("/api/compose-templates", {
11532:             method: "POST",
11533:             body: JSON.stringify({ ...payload, mailType: "INTRODUCTION" })
11534:         });
11535:     }
11536:     hideComposeTemplateEditor();
11537:     state.mailSendOptions = [];
11538:     await loadComposeTemplates();
11539:     showStatus("邮件模板已保存", "ok");
11540: }
11541: 
```

## E-5 相关文件 SHA-256

执行前检查本轮计划之外的并行改动；不要回滚已有 WIP。

```text

30526ea50640c30f628110e4940b600f1b92181c22e2871c97bae9f067dc1f9e  src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt

923f502cd4e1e03b6998bb984aee9c148363a0ae2535268bf1a257279a2e8d17  src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt

d0862ab6614852d87f76aec6606485512cba8e52951d51e0e6f9a95d92809b99  src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt

83591b8861ad8fa84d8246a206a2a631fa1aaf8207609a0bb3f0bdef01338729  src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt

683a31e92d62c3f4bd97c67cfeb28a651a0244861f191a303b4d8defed168d79  src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt

6fd414533c700bc6357646ed51d555ff5d2c2317855ffe4488303e42e8935b49  src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt

dfb2b83061cbde5b0586d9a435807905f70f7b9b54c26248c32e6b562e4eddc8  src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt

caff8e43ae2b5d982e54aca31fd18819fc6b144f0b6c1db807dd3a3bf1e1be37  src/main/resources/static/app.js

966f8d0ba2492004f6f1cf5044408bd979dbaf85f4044f79e71b1bf6b3b8defe  src/main/resources/static/index.html

94fd4933a8bba44960469ef89d2cfca4c46116b3b8b09f2077ad1d81e078bdd4  src/main/resources/static/styles.css

```

## E-6 收尾并行变动复核

初始证据保持不改。研究期间另一个任务提交了发现任务流量功能，以下为收尾只读回执，本轮没有修改业务源码。

```text
$ git log -1 --oneline
13fb91e feat(discovery): show measured response traffic in execution logs
[exit=0]
```

```text
$ rg -n \?v= src/main/resources/static/index.html
11:    <link rel="stylesheet" href="styles.css?v=20260923-discovery-traffic-v2">
12:    <link rel="stylesheet" href="expert-materials.css?v=20260923-discovery-traffic-v2">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20260923-discovery-traffic-v2">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20260923-discovery-traffic-v2">
15:    <link rel="stylesheet" href="world-clock.css?v=20260923-discovery-traffic-v2">
2195:<script src="trust-reply-workbench.js?v=20260923-discovery-traffic-v2"></script>
2196:<script src="expert-materials.js?v=20260923-discovery-traffic-v2"></script>
2197:<script src="meeting-confirmation.js?v=20260923-discovery-traffic-v2"></script>
2198:<script src="mailbox-chat.js?v=20260923-discovery-traffic-v2"></script>
2199:<script src="app.js?v=20260923-discovery-traffic-v2"></script>
2200:<script src="world-clock.js?v=20260923-discovery-traffic-v2"></script>
[exit=0]
```

```text
$ rg -n -F 20260923-discovery-traffic-v2 src/test
[exit=1]
```

收尾 index.html SHA-256：`8b581a53d719051bf10365de8a9be3fa40df47c9e5e6132fddb86529958b3bdf`。其他 E-5 代码文件哈希未变。
