package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.discovery.service.DiscoveryEmailRoutingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

/** One document per request; UI processes a captured page serially, without an unbounded server job. */
@RestController
@RequestMapping("/api/discovery/email-routing")
class DiscoveryEmailRoutingController(private val service: DiscoveryEmailRoutingService) {
    data class RefreshRequest(val docId: String)

    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest, @RequestBody body: RefreshRequest): Any {
        val actor = request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String
        if (actor.isNullOrBlank()) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录")
        return service.refresh(body.docId)
    }
}
