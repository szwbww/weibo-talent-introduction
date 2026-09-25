package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.mail.service.MailOpenTrackingService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.Base64

@RestController
class MailOpenTrackingController(private val service: MailOpenTrackingService) {
    @GetMapping("/api/mail-open-tracking/settings")
    fun settings() = service.settings()

    @PutMapping("/api/mail-open-tracking/settings")
    fun setEnabled(@RequestBody request: JsonNode): Any {
        val enabled = request.get("enabled")
        require(enabled != null && enabled.isBoolean) { "enabled must be a boolean" }
        return service.setEnabled(enabled.booleanValue())
    }

    @GetMapping("/api/mail-open-tracking/records")
    fun records(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) senderAccountCode: String?,
        @RequestParam(defaultValue = "ALL") status: String,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "0") pageOffset: Int
    ) = service.readPage(from, to, senderAccountCode, status, keyword, pageSize, pageOffset)

    @GetMapping("/api/mail-open-tracking/records/{mailRecordId}")
    fun detail(@PathVariable mailRecordId: Long) = service.detail(mailRecordId)

    @GetMapping("/t/mail-open/{token}.gif")
    fun pixel(@PathVariable token: String): ResponseEntity<ByteArray> {
        service.recordSignal(token)
        return gif()
    }

    @org.springframework.web.bind.annotation.RequestMapping("/t/mail-open/{token}.gif", method = [org.springframework.web.bind.annotation.RequestMethod.HEAD])
    fun pixelHead(@PathVariable token: String): ResponseEntity<ByteArray> = gif()

    private fun gif(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store,no-cache,must-revalidate,max-age=0")
        .contentType(MediaType.IMAGE_GIF)
        .contentLength(PIXEL.size.toLong())
        .body(PIXEL)

    companion object {
        private val PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIEAAAAAAAAAAAAAAAAAACH5BAEAAAAALAAAAAABAAEAAAgEAAEEBAA7")
    }
}
