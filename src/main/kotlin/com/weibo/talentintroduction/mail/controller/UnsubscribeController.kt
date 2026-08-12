package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.SuppressionSource
import com.weibo.talentintroduction.mail.service.UnsubscribePageRenderer
import com.weibo.talentintroduction.mail.service.UnsubscribeTokenService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/u")
class UnsubscribeController(
    private val tokenService: UnsubscribeTokenService,
    private val suppressionService: EmailSuppressionService,
    private val pageRenderer: UnsubscribePageRenderer
) {
    /** RFC 8058 一键退订：邮件客户端 POST。 */
    @PostMapping("/unsubscribe")
    fun oneClick(@RequestParam token: String): ResponseEntity<String> {
        val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid")
        suppressionService.suppress(email, SuppressionSource.ONE_CLICK, "one-click unsubscribe")
        return ResponseEntity.ok("unsubscribed")
    }

    /** 浏览器打开链接：品牌化确认页（GET 不直接退订，避免预取误触发）。 */
    @GetMapping("/unsubscribe")
    fun page(@RequestParam token: String): ResponseEntity<String> {
        val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid link")
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pageRenderer.confirmPage(token, email))
    }

    @PostMapping("/unsubscribe/confirm")
    fun confirm(@RequestParam token: String): ResponseEntity<String> {
        val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid")
        suppressionService.suppress(email, SuppressionSource.ONE_CLICK, "web confirm unsubscribe")
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
            .body(pageRenderer.successPage())
    }
}
