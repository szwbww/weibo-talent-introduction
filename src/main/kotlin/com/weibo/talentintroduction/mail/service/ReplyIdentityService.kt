package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.reply.service.IdentityDefaultSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

/** Read-only comparison. Nothing returned here is a replacement MIME body or send authority. */
@Service
class ReplyIdentityService(
    private val snippets: ReplySnippetService,
    private val variables: MailVariableService,
    private val templates: MailComposeTemplateService
) {
    fun inspect(source: ReplyIdentitySource, textBody: String, htmlBody: String): ReplyIdentityResult = try {
        analyze(source, textBody, htmlBody, true)
    } catch (_: Exception) {
        val slot = ReplyIdentitySlot(ReplyIdentityStatus.UNCERTAIN,false,null,null,"IDENTITY_READ_FAILED")
        ReplyIdentityResult(source.context,"",slot,slot,ReplyIdentityQuoteBoundary("UNCERTAIN",null),listOf("IDENTITY_READ_FAILED"))
    }

    fun analyzeFinalBody(source: ReplyIdentitySource, textBody: String, htmlBody: String): List<SafetyFinding> = try {
        findings(analyze(source, textBody, htmlBody, false))
    } catch (_: Exception) {
        listOf(SafetyFinding("REPLY_IDENTITY_UNCERTAIN", SafetySeverity.NORMAL, "身份格式暂无法核对；可检查正文后确认发送"))
    }

    fun previewDefaults(source: ReplyIdentitySource, salutation: IdentityDefaultSelection,
                        signature: IdentityDefaultSelection): ReplyIdentityDefaultsPreview {
        val snapshot = snippets.getIdentityDefaults()
        require(snapshot.salutationOptions.any { it.id == salutation.id }) { "Invalid SALUTATION snippet" }
        require(snapshot.signatureOptions.any { it.id == signature.id }) { "Invalid CLOSING snippet" }
        listOf(salutation, signature).forEach {
            require(it.content.isNotBlank() && it.content.length <= 10000) { "content must contain 1 to 10000 characters" }
            variables.requireValidTemplatePlaceholders(it.content)
        }
        val values = values(source)
        val first = expected(salutation.content, values, true)
        val last = expected(signature.content, values, false)
        return ReplyIdentityDefaultsPreview(source.context, mapOf("salutation" to first.text,"signature" to last.text),
            (first.missingVariables + last.missingVariables).distinct(), listOfNotNull(first.reason, last.reason).distinct())
    }

    private fun values(source: ReplyIdentitySource): Map<String, String> {
        val profile = variables.resolveExpertProfileFor(source.contact)
        return variables.buildVariables(source.account, profile, unsubscribeEmail = null,
            previewFallbacks = false, contact = source.contact)
    }

    private fun analyze(source: ReplyIdentitySource, text: String, html: String, render: Boolean): ReplyIdentityResult {
        val snapshot = snippets.getIdentityDefaults()
        val values = values(source)
        val first = expected(snapshot.salutation?.content, values, true)
        val last = expected(snapshot.signature?.content, values, false)
        val bodyNeedsWrite = listOf(text, html).any { placeholders(it).contains("unsubscribeUrl") }
        val renderedText = if (render && !bodyNeedsWrite) templates.renderWithVariables(text, values) else text
        val renderedHtml = if (render && !bodyNeedsWrite)
            templates.renderWithVariables(html, values.mapValues { HtmlUtils.htmlEscape(it.value) }) else html
        val parsed = visibleHtml(renderedHtml)
        val rich = partition(parsed.text, source, parsed.quoteTail, parsed.quoteCandidate)
        val plain = partition(normalize(renderedText), source, parsed.quoteTail, parsed.quoteCandidate)
        val boundaryReason = when {
            bodyNeedsWrite -> "BODY_VARIABLE_REQUIRES_WRITE"
            parsed.uncertain -> "HTML_VISIBILITY_UNCERTAIN"
            plain.uncertain || rich.uncertain -> "QUOTE_BOUNDARY_UNCERTAIN"
            plain.tail != rich.tail -> "MIME_QUOTE_BOUNDARY_MISMATCH"
            placeholders(renderedText).isNotEmpty() || placeholders(parsed.text).isNotEmpty() -> "UNRESOLVED_BODY_VARIABLE"
            else -> null
        }
        fun slot(expectation: Expected, start: Boolean): ReplyIdentitySlot {
            val reason = expectation.reason ?: boundaryReason
            if (reason != null) return ReplyIdentitySlot(ReplyIdentityStatus.UNCERTAIN, false, expectation.text, null, reason)
            val a = classify(plain.body, expectation.text.orEmpty(), start)
            val b = classify(rich.body, expectation.text.orEmpty(), start)
            if (a.status != b.status || a.actualText != b.actualText) return ReplyIdentitySlot(
                ReplyIdentityStatus.UNCERTAIN, false, expectation.text, listOfNotNull(a.actualText,b.actualText).distinct().joinToString(" / "),
                "MIME_SLOT_MISMATCH")
            return a.copy(expectedText = expectation.text)
        }
        return ReplyIdentityResult(source.context, snapshot.baseFingerprint, slot(first,true),slot(last,false),
            ReplyIdentityQuoteBoundary(if (boundaryReason != null) "UNCERTAIN" else if (plain.tail != null) "VERIFIED_TAIL" else "NONE",
                if (boundaryReason == null) plain.tail else null),
            (snapshot.diagnostics + listOfNotNull(boundaryReason,first.reason,last.reason)).distinct())
    }

    private data class Expected(val text: String?, val reason: String?, val missingVariables: List<String> = emptyList())
    private fun placeholders(text: String): List<String> = Regex("\\$\\{([^}|]+)(?:\\|[^}]*)?}")
        .findAll(text).map { it.groupValues[1] }.toList()
    private fun expected(content: String?, values: Map<String,String>, first: Boolean): Expected {
        if (content.isNullOrBlank()) return Expected(null,"DEFAULT_UNAVAILABLE")
        val keys = placeholders(content)
        val missing = keys.filter { values[it].isNullOrBlank() }.distinct()
        if ("unsubscribeUrl" in keys) return Expected(null,"BODY_VARIABLE_REQUIRES_WRITE",missing)
        try { variables.requireValidTemplatePlaceholders(content) } catch (_: IllegalArgumentException) {
            return Expected(null,"INVALID_DEFAULT_TEMPLATE",missing)
        }
        val rendered = normalize(templates.renderWithVariables(content,values))
        if (missing.isNotEmpty()) return Expected(rendered,"MISSING_VARIABLE",missing)
        if (placeholders(rendered).isNotEmpty() || rendered.contains("\${")) return Expected(rendered,"UNRESOLVED_DEFAULT_VARIABLE")
        // A variable is evidence only for the identity it references; literal personal names need their own check.
        val trusted = if (first) {
            keys.any { it == "expertName" || it == "expertFamilyName" } ||
                rendered in setOf("Dear Professor,","Dear Colleague,","Hi,","Hello,","您好","尊敬的专家，") ||
                Regex("^(?:Dear|Hi)\\s+(?:(?:Dr\\.?|Professor)\\s+)?(.+?)[,，]?$",RegexOption.IGNORE_CASE)
                    .matchEntire(rendered)?.groupValues?.get(1)?.let { name ->
                        name in listOf(values["expertName"],values["expertFamilyName"]).filterNotNull().filter { it.isNotBlank() }
                    } == true
        } else {
            keys.any { it == "senderName" || it == "teamName" } ||
                rendered in setOf("Best regards,","Kind regards,","Regards,","Sincerely,","此致") ||
                rendered.lines().any { line -> line in listOf(values["senderName"],values["teamName"]).filterNotNull().filter { it.isNotBlank() } }
        }
        return Expected(rendered,if (trusted) null else "UNVERIFIED_LITERAL")
    }

    private val salutation = Regex("^(?:Dear\\b|Hi\\b|Hello\\b|尊敬|您好)",RegexOption.IGNORE_CASE)
    private val closing = Regex("^(?:Best regards\\b|Kind regards\\b|Regards\\b|Sincerely\\b|此致)",RegexOption.IGNORE_CASE)
    private fun classify(body: String, expected: String, start: Boolean): ReplyIdentitySlot {
        val lines = body.lines()
        val marker = if (start) salutation else closing
        val positions = lines.indices.filter { marker.containsMatchIn(lines[it]) }
        val occurrences = if (expected.isEmpty()) emptyList() else Regex(Regex.escape(expected)).findAll(body)
            .filter { (it.range.first == 0 || body[it.range.first-1] == '\n') &&
                (it.range.last == body.lastIndex || body[it.range.last+1] == '\n') }.toList()
        val positioned = if (start) body == expected || body.startsWith(expected+"\n") else body == expected || body.endsWith("\n"+expected)
        fun result(status: ReplyIdentityStatus, actual: String?, reason: String) =
            ReplyIdentitySlot(status,status == ReplyIdentityStatus.MISSING,expected,actual,reason)
        if (positions.size > 1 || occurrences.size > 1 || occurrences.isNotEmpty() && !positioned ||
            start && positions.any { it != 0 }) return result(ReplyIdentityStatus.MULTIPLE_OR_MISPLACED,body,"MULTIPLE_OR_MISPLACED")
        if (positioned) return result(ReplyIdentityStatus.MATCH,expected,"DEFAULT_MATCH")
        if (positions.isNotEmpty()) {
            val actual = if (start) lines.first() else lines.drop(positions.last()).joinToString("\n")
            return result(ReplyIdentityStatus.MISMATCH,actual,"DEFAULT_MISMATCH")
        }
        val edge = (if (start) body.split("\n\n").firstOrNull() else body.split("\n\n").lastOrNull()).orEmpty()
        val bare = edge.isNotBlank() && edge.lines().size <= 2 && edge.lines().all {
            it.split(Regex("[\\s-]+")).size <= 6 && Regex("[\\p{L}\\p{M} .'-]+").matches(it) &&
                !Regex("[.!?。！？,，:;]$").containsMatchIn(it)
        }
        if (bare) return result(ReplyIdentityStatus.UNCERTAIN,edge,"UNSTRUCTURED_IDENTITY_BLOCK")
        return result(ReplyIdentityStatus.MISSING,null,"NO_IDENTITY_STRUCTURE")
    }

    private data class Partition(val body: String, val tail: String? = null, val uncertain: Boolean = false)
    private fun partition(body: String, source: ReplyIdentitySource, quoteTail: String?, quoteCandidate: Boolean): Partition {
        val rawSource = source.quotedBody?.takeIf { it.isNotBlank() }
        val sourceText = rawSource?.let { if (it.contains(Regex("<[^>]+>"))) visibleHtml(it).text else normalize(it) }
        val canonical = source.canonicalQuoteHeader?.let { header -> sourceText?.let { "$header\n\n$it" } }
        val candidates = listOfNotNull(canonical, sourceText?.takeIf { it == quoteTail }).distinct()
        for (tail in candidates) {
            if ((body == tail || body.endsWith("\n"+tail)) && body.indexOf(tail) == body.lastIndexOf(tail)) {
                return Partition(body.removeSuffix(tail).trimEnd(),tail)
            }
        }
        val suspicious = quoteCandidate || Regex("(?m)^(?:On .+ wrote:|From:|> ?)").containsMatchIn(body)
        return Partition(body,uncertain = suspicious)
    }

    private data class Visible(val text: String, val uncertain: Boolean, val quoteCandidate: Boolean, val quoteTail: String?)
    private fun visibleHtml(html: String): Visible {
        val output = StringBuilder()
        data class Frame(val tag: HTML.Tag, val hidden: Boolean, val quoteStart: Int?)
        val stack = mutableListOf<Frame>()
        var uncertain = false
        var quote = false
        var tailStart: Int? = null
        var tailEnd: Int? = null
        fun boundary() { if (output.isNotEmpty() && output.last() != '\n') output.append('\n'); output.append('\n') }
        fun hidden() = stack.lastOrNull()?.hidden == true
        fun attrs(a: MutableAttributeSet): Map<String,String> = a.attributeNames.toList().associate { it.toString().lowercase() to a.getAttribute(it).toString() }
        val blocks = setOf("p","div","li","blockquote","h1","h2","h3","h4","h5","h6")
        ParserDelegator().parse(StringReader(html),object: HTMLEditorKit.ParserCallback() {
            override fun handleStartTag(tag: HTML.Tag,a: MutableAttributeSet,pos: Int) {
                val at = attrs(a)
                val style = at["style"].orEmpty().lowercase().replace(Regex("\\s+"),"")
                val hide = hidden() || tag.toString() in setOf("script","style","head") || "hidden" in at ||
                    at["aria-hidden"].equals("true",true) || "display:none" in style || "visibility:hidden" in style
                if (!hide && (at["class"]?.let { it != "gmail_quote" } == true || style.isNotBlank() && style.split(';').any {
                        it.isNotBlank() && it !in setOf("display:block","display:inline","visibility:visible") &&
                            !Regex("(?:color:#[0-9a-f]{3,8}|font-weight:(?:normal|bold)|font-style:(?:normal|italic)|text-align:(?:left|right|center))").matches(it)
                    })) uncertain = true
                if (tag.toString() == "style" || tag.toString() == "link") uncertain = true
                val isQuote = !hide && (tag.toString() == "blockquote" || at["type"] == "cite" || at["class"].orEmpty().split(' ').contains("gmail_quote"))
                if (isQuote) quote = true
                if (!hide && tag.toString() in blocks) boundary()
                stack.add(Frame(tag,hide,if (isQuote) output.length else null))
            }
            override fun handleEndTag(tag: HTML.Tag,pos: Int) {
                if (!hidden() && tag.toString() in blocks) boundary()
                val index = stack.indexOfLast { it.tag == tag }
                if (index >= 0) stack[index].quoteStart?.let { tailStart = it; tailEnd = output.length }
                if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            }
            override fun handleSimpleTag(tag: HTML.Tag,a: MutableAttributeSet,pos: Int) {
                if (tag.toString() == "link") uncertain = true
                if (!hidden() && tag == HTML.Tag.BR) output.append('\n')
            }
            override fun handleText(data: CharArray,pos: Int) { if (!hidden()) output.append(data) }
        },true)
        val tail = tailStart?.let { start -> tailEnd?.let { end ->
            if (output.substring(end).isBlank()) normalize(output.substring(start,end)) else null
        } }
        return Visible(normalize(output.toString()),uncertain,quote,tail)
    }

    private fun normalize(text: String): String = HtmlUtils.htmlUnescape(text).replace("\r\n","\n").replace('\r','\n')
        .replace('\u00a0',' ').lines().joinToString("\n") { it.replace(Regex("[\\t ]+")," ").trim() }
        .trim().replace(Regex("\n{3,}"),"\n\n")

    private fun findings(result: ReplyIdentityResult): List<SafetyFinding> = listOf(
        "SALUTATION" to result.salutation,"SIGNATURE" to result.signature).mapNotNull { (slot,value) ->
        if (value.status == ReplyIdentityStatus.MATCH) null else {
            val code = when(value.status) {
                ReplyIdentityStatus.MISSING -> "REPLY_${slot}_MISSING"
                ReplyIdentityStatus.MISMATCH -> "REPLY_${slot}_MISMATCH"
                ReplyIdentityStatus.MULTIPLE_OR_MISPLACED -> "REPLY_IDENTITY_MULTIPLE_OR_MISPLACED"
                else -> "REPLY_IDENTITY_UNCERTAIN"
            }
            fun short(text: String?) = text.orEmpty().let { if (it.length > 70) it.take(69)+"…" else it }
            SafetyFinding(code,SafetySeverity.NORMAL,"${if (slot == "SALUTATION") "称呼" else "签名"}：当前：${short(value.actualText)}；建议：${short(value.expectedText)}")
        }
    }
}

enum class ReplyIdentityStatus { MATCH, MISSING, MISMATCH, UNCERTAIN, MULTIPLE_OR_MISPLACED }
data class ReplyIdentityContext(val contactId: Long,val inboundProcessingId: Long?,val anchorMailRecordId: Long?,
    val accountCode: String,val recipientEmail: String,val senderName: String?)
data class ReplyIdentitySource(val contact: ExpertContact,val account: MailSenderAccount,val context: ReplyIdentityContext,
    val quotedBody: String?,val canonicalQuoteHeader: String?)
data class ReplyIdentitySlot(val status: ReplyIdentityStatus,val canInsert: Boolean,val expectedText: String?,val actualText: String?,val reasonCode: String)
data class ReplyIdentityQuoteBoundary(val kind: String,val canonicalQuotedTail: String?)
data class ReplyIdentityResult(val context: ReplyIdentityContext,val defaultsFingerprint: String,val salutation: ReplyIdentitySlot,
    val signature: ReplyIdentitySlot,val quoteBoundary: ReplyIdentityQuoteBoundary,val warnings: List<String>)
data class ReplyIdentityDefaultsPreview(val context: ReplyIdentityContext,val renderedTexts: Map<String,String?>,
    val missingVariables: List<String>,val warnings: List<String>)

enum class ReplyIdentitySourceKind { INBOUND, CONVERSATION }
data class ReplyIdentityContextRequest(val sourceKind: ReplyIdentitySourceKind,val contactId: Long,
    val inboundProcessingId: Long? = null,val senderAccountCode: String? = null,
    val accountScope: String? = null,val anchorMailRecordId: Long? = null) {
    fun validate() {
        require(contactId > 0) { "contactId must be positive" }
        require(inboundProcessingId == null || inboundProcessingId > 0) { "inboundProcessingId must be positive" }
        require(anchorMailRecordId == null || anchorMailRecordId > 0) { "anchorMailRecordId must be positive" }
        if (sourceKind == ReplyIdentitySourceKind.INBOUND) {
            require(inboundProcessingId != null && anchorMailRecordId == null && accountScope == null) { "Invalid inbound identity context" }
        } else {
            require(inboundProcessingId == null && senderAccountCode == null) { "Invalid conversation identity context" }
        }
    }
}
data class ReplyIdentityInspectRequest(val sourceKind: ReplyIdentitySourceKind,val contactId: Long,
    val inboundProcessingId: Long? = null,val senderAccountCode: String? = null,
    val accountScope: String? = null,val anchorMailRecordId: Long? = null,
    val htmlBody: String = "",val textBody: String = "") {
    fun contextRequest() = ReplyIdentityContextRequest(sourceKind,contactId,inboundProcessingId,senderAccountCode,accountScope,anchorMailRecordId)
}
data class ReplyIdentityDefaultsPreviewRequest(val sourceKind: ReplyIdentitySourceKind,val contactId: Long,
    val salutation: IdentityDefaultSelection,val signature: IdentityDefaultSelection,
    val inboundProcessingId: Long? = null,val senderAccountCode: String? = null,
    val accountScope: String? = null,val anchorMailRecordId: Long? = null) {
    fun contextRequest() = ReplyIdentityContextRequest(sourceKind,contactId,inboundProcessingId,senderAccountCode,accountScope,anchorMailRecordId)
}
