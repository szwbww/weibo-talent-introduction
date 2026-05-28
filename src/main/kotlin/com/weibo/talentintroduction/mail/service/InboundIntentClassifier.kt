package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service
import java.util.Locale

@Service
class InboundIntentClassifier {
    fun classify(body: String, subject: String? = null): InboundIntentClassification {
        val text = listOfNotNull(subject, body)
            .joinToString("\n")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isBlank()) {
            return InboundIntentClassification(InboundIntentCode.UNKNOWN, 0, emptyList(), AutoIntentAction.MANUAL_REVIEW)
        }

        return when {
            text.matchesAny(notInterestedKeywords) ->
                result(InboundIntentCode.NOT_INTERESTED, text, notInterestedKeywords, AutoIntentAction.CLOSE)

            text.matchesAny(cvKeywords) ->
                result(InboundIntentCode.CV_ATTACHED, text, cvKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(videoRequirementKeywords) ->
                result(InboundIntentCode.ASK_VIDEO_REQUIREMENT, text, videoRequirementKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(documentKeywords) ->
                result(InboundIntentCode.DOCS_ATTACHED, text, documentKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(meetingTimeKeywords) ->
                result(InboundIntentCode.MEETING_TIME_PROVIDED, text, meetingTimeKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(meetingRequestKeywords) ->
                result(InboundIntentCode.MEETING_REQUESTED, text, meetingRequestKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(materialListKeywords) ->
                result(InboundIntentCode.ASK_MATERIAL_LIST, text, materialListKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(confidentialityKeywords) ->
                result(InboundIntentCode.ASK_CONFIDENTIALITY, text, confidentialityKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(fundingKeywords) ->
                result(InboundIntentCode.ASK_FUNDING, text, fundingKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(companyInfoKeywords) ->
                result(InboundIntentCode.ASK_COMPANY_INFO, text, companyInfoKeywords, AutoIntentAction.MANUAL_REVIEW)

            text.matchesAny(remotePartTimeKeywords) ->
                result(InboundIntentCode.ASK_REMOTE_PART_TIME, text, remotePartTimeKeywords, AutoIntentAction.QA)

            text.matchesAny(processKeywords) ->
                result(InboundIntentCode.ASK_PROCESS, text, processKeywords, AutoIntentAction.QA)

            text.matchesAny(moreInfoKeywords) ->
                result(InboundIntentCode.ASK_MORE_INFO, text, moreInfoKeywords, AutoIntentAction.QA)

            text.matchesAny(interestedKeywords) ->
                result(InboundIntentCode.INTERESTED, text, interestedKeywords, AutoIntentAction.SEND_MEETING_INVITATION)

            else ->
                InboundIntentClassification(InboundIntentCode.UNKNOWN, 0, emptyList(), AutoIntentAction.QA)
        }
    }

    private fun result(
        code: InboundIntentCode,
        text: String,
        keywords: List<String>,
        action: AutoIntentAction
    ): InboundIntentClassification {
        val matched = keywords.filter(text::contains)
        return InboundIntentClassification(
            intentCode = code,
            confidence = if (matched.size > 1) 90 else 75,
            matchedKeywords = matched,
            autoAction = action
        )
    }

    private fun String.matchesAny(keywords: List<String>): Boolean =
        keywords.any(::contains)

    companion object {
        private val interestedKeywords = listOf(
            "i am interested",
            "i'm interested",
            "i will be interested",
            "i would be interested",
            "i am open to",
            "i'm open to",
            "happy to discuss",
            "delighted to talk",
            "would like to know more",
            "sounds interesting"
        )

        private val meetingTimeKeywords = listOf(
            "i am available",
            "i will be available",
            "available at",
            "available on",
            "available from",
            "my available",
            "zoom",
            "teams",
            "webex",
            "google meet",
            "meeting link"
        )

        private val meetingRequestKeywords = listOf(
            "schedule a meeting",
            "arrange a meeting",
            "set up a meeting",
            "book a meeting",
            "can we meet",
            "could we meet",
            "can we have a meeting",
            "could we have a meeting"
        )

        private val cvKeywords = listOf(
            "attached is my cv",
            "attached my cv",
            "cv attached",
            "resume attached",
            "attached is my resume",
            "updated cv",
            "completed my cv"
        )

        private val documentKeywords = listOf(
            "attached",
            "attachment",
            "passport",
            "degree certificate",
            "phd certificate",
            "employment proof",
            "work proof",
            "patent",
            "award",
            "publication",
            "ppt",
            "powerpoint",
            "video",
            "vcr",
            "commitment"
        )

        private val materialListKeywords = listOf(
            "what documents",
            "which documents",
            "what materials",
            "which materials",
            "missing documents",
            "missing materials",
            "material list",
            "document list"
        )

        private val processKeywords = listOf(
            "process",
            "procedure",
            "timeline",
            "application steps",
            "what kind of application",
            "result",
            "deadline",
            "submit"
        )

        private val moreInfoKeywords = listOf(
            "more information",
            "more details",
            "additional information",
            "additional details",
            "tell me more",
            "learn more"
        )

        private val fundingKeywords = listOf(
            "funding",
            "funded",
            "salary",
            "subsidy",
            "money transfer",
            "transfer the money",
            "bank account",
            "commission",
            "fee",
            "fees",
            "who is funding"
        )

        private val remotePartTimeKeywords = listOf(
            "remote",
            "part-time",
            "part time",
            "relocate",
            "relocation",
            "move to china",
            "visit china"
        )

        private val confidentialityKeywords = listOf(
            "confidential",
            "confidentiality",
            "proprietary",
            "employer",
            "conflict",
            "intellectual property",
            "ip"
        )

        private val companyInfoKeywords = listOf(
            "company",
            "enterprise",
            "industry partner",
            "project topic",
            "research direction",
            "matched project"
        )

        private val videoRequirementKeywords = listOf(
            "video",
            "vcr",
            "recording",
            "self statement",
            "self-statement"
        )

        private val notInterestedKeywords = listOf(
            "not interested",
            "no longer interested",
            "do not want to participate",
            "don't want to participate",
            "please remove me",
            "unsubscribe"
        )
    }
}

data class InboundIntentClassification(
    val intentCode: InboundIntentCode,
    val confidence: Int,
    val matchedKeywords: List<String>,
    val autoAction: AutoIntentAction
)

enum class InboundIntentCode {
    INTERESTED,
    ASK_MORE_INFO,
    MEETING_TIME_PROVIDED,
    MEETING_REQUESTED,
    CV_ATTACHED,
    DOCS_ATTACHED,
    ASK_MATERIAL_LIST,
    ASK_PROCESS,
    ASK_FUNDING,
    ASK_CONFIDENTIALITY,
    ASK_REMOTE_PART_TIME,
    ASK_COMPANY_INFO,
    ASK_VIDEO_REQUIREMENT,
    PASSPORT_UPDATED,
    NOT_INTERESTED,
    UNKNOWN
}

enum class AutoIntentAction {
    QA,
    SEND_MEETING_INVITATION,
    MANUAL_REVIEW,
    CLOSE
}
