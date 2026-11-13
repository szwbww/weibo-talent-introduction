package com.weibo.talentintroduction.expert.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import com.weibo.talentintroduction.mail.service.ExpertTemplateOverrideService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/experts/template-overrides")
class ExpertTemplateOverrideController(
    private val overrides: ExpertTemplateOverrideService,
    private val identity: ExpertProfileIdentityResolver,
    private val search: ExpertSearchService,
    private val variables: MailVariableService
) {
    @ExceptionHandler(com.weibo.talentintroduction.expert.domain.ExpertManualProfileException::class)
    fun identityError(e: com.weibo.talentintroduction.expert.domain.ExpertManualProfileException): org.springframework.http.ResponseEntity<com.weibo.talentintroduction.common.controller.ApiErrorResponse> {
        val status = when (e) {
            is com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException -> org.springframework.http.HttpStatus.BAD_REQUEST
            is com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException -> org.springframework.http.HttpStatus.NOT_FOUND
            is com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException -> org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
            else -> org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
        }
        return org.springframework.http.ResponseEntity.status(status).body(
            com.weibo.talentintroduction.common.controller.ApiErrorResponse("EXPERT_IDENTITY_ERROR", e.message ?: "专家查询失败", null))
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException::class)
    fun statusError(e: org.springframework.web.server.ResponseStatusException) =
        org.springframework.http.ResponseEntity.status(e.status).body(
            com.weibo.talentintroduction.common.controller.ApiErrorResponse("EXPERT_MAIL_CONFLICT", e.reason ?: "操作失败", null))

    @GetMapping
    fun get(request: HttpServletRequest, @RequestParam expertId: String,
            @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel): Map<String, Any> {
        actor(request)
        val key = identity.requireKnown(expertId, level)
        val snapshot = overrides.read(key)
        val profile = search.findByOrcidId(expertId, level)
        return mapOf("version" to snapshot.version, "values" to snapshot.values,
            "original" to variables.buildOriginalVariables(null, profile, unsubscribeEmail = null))
    }

    @PutMapping
    fun save(request: HttpServletRequest, @RequestBody body: JsonNode): Any {
        val actor = actor(request)
        require(body.path("expertId").isTextual && body.path("level").isTextual) { "专家标识或层级无效" }
        val version = body.path("expectedVersion")
        require(version.isIntegralNumber && version.canConvertToLong()) { "版本必须为整数" }
        val values = body.path("values")
        require(values.isObject && values.fields().asSequence().all { it.value.isTextual }) { "变量值必须为文本" }
        val key = identity.requireKnown(body.path("expertId").asText(), ExpertIndexLevel.valueOf(body.path("level").asText()))
        return overrides.save(key, version.asLong(), values.fields().asSequence().associate { it.key to it.value.asText() }, actor)
    }

    private fun actor(request: HttpServletRequest): String {
        val actor = request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String
        MailReadOnlyPolicy.requireAdmin(actor)
        return requireNotNull(actor)
    }
}
