package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingConflict
import com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsService
import com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsUnavailable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*

/** Authentication is supplied by the existing API interceptor, without an exemption. */
@RestController
@RequestMapping("/api/expert-discovery/verification/apollo-settings")
class DiscoveryApolloSettingsController(private val settings: DiscoveryApolloSettingsService) {
    @GetMapping
    fun get() = settings.resolve()

    @PutMapping
    fun put(@RequestBody body: JsonNode): com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettings {
        require(body.isObject && body.fieldNames().asSequence().toSet() == FIELDS) { "Invalid setting fields" }
        val revision = body.get("revision")
        require(revision.isIntegralNumber && revision.canConvertToLong() && revision.longValue() >= 0 &&
            body.get("searchEnabled").isBoolean && body.get("detailEnabled").isBoolean) { "Invalid setting types" }
        return settings.save(revision.longValue(), body.get("searchEnabled").booleanValue(),
            body.get("detailEnabled").booleanValue())
    }

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    fun invalid() = error(HttpStatus.BAD_REQUEST, "INVALID_APOLLO_SETTING")

    @ExceptionHandler(DiscoveryApolloSettingConflict::class)
    fun conflict() = error(HttpStatus.CONFLICT, "APOLLO_SETTING_CONFLICT")

    @ExceptionHandler(DiscoveryApolloSettingsUnavailable::class)
    fun unavailable() = error(HttpStatus.SERVICE_UNAVAILABLE, "APOLLO_SETTINGS_UNAVAILABLE")

    private fun error(status: HttpStatus, code: String) = ResponseEntity.status(status).body(mapOf("code" to code))

    private companion object {
        val FIELDS = setOf("revision", "searchEnabled", "detailEnabled")
    }
}
