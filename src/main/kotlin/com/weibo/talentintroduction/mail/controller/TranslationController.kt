package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MailTranslationService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TranslateRequest(val text: String?)

data class TranslateResponse(
    val ok: Boolean,
    val translatedText: String?,
    val reason: String?
)

@RestController
@RequestMapping("/api/translate")
class TranslationController(
    private val mailTranslationService: MailTranslationService
) {

    @PostMapping
    fun translate(@RequestBody request: TranslateRequest): TranslateResponse {
        val result = mailTranslationService.translate(request.text.orEmpty())
        return TranslateResponse(
            ok = result.ok,
            translatedText = result.text,
            reason = result.reason
        )
    }
}
