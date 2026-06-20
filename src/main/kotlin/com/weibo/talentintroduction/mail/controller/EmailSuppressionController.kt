package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.SuppressionPage
import com.weibo.talentintroduction.mail.service.SuppressionSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/suppressions")
class EmailSuppressionController(
    private val service: EmailSuppressionService
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): SuppressionPage = service.list(keyword, page, size)

    @PostMapping
    fun add(@RequestBody req: AddSuppressionRequest): ResponseEntity<Map<String, Boolean>> {
        require(req.email.isNotBlank()) { "email required" }
        val added = service.suppress(req.email, SuppressionSource.MANUAL, req.reason ?: "manual add")
        return ResponseEntity.ok(mapOf("added" to added))
    }

    @DeleteMapping
    fun remove(@RequestParam email: String): Map<String, Boolean> =
        mapOf("removed" to service.remove(email))
}

data class AddSuppressionRequest(
    val email: String,
    val reason: String?
)
