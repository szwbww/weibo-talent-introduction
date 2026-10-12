package com.weibo.talentintroduction.document.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.document.service.ExpertMeetingRecordService
import com.weibo.talentintroduction.document.service.MeetingRecordException
import com.weibo.talentintroduction.document.service.MeetingRecordInput
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/experts/meeting-records")
class ExpertMeetingRecordController(private val service: ExpertMeetingRecordService) {
    @GetMapping
    fun list(request: HttpServletRequest, @RequestParam expertId: String,
        @RequestParam(defaultValue="0") page: Int, @RequestParam(defaultValue="20") size: Int): Any {
        actor(request)
        return service.list(expertId, page, size)
    }
    @PutMapping("/{id}")
    fun save(request: HttpServletRequest, @PathVariable id: String, @RequestBody input: MeetingRecordInput): Any =
        service.save(id, input, actor(request))

    private fun actor(request: HttpServletRequest): String =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.takeIf { it.isNotBlank() }
            ?: throw MeetingRecordException(HttpStatus.UNAUTHORIZED, "未登录")

    @ExceptionHandler(Exception::class)
    fun error(ex: Exception): ResponseEntity<ApiErrorResponse> {
        val status = when(ex) {
            is MeetingRecordException -> ex.status
            is ExpertProfileNotFoundException -> HttpStatus.NOT_FOUND
            is ExpertProfileLookupUnavailableException -> HttpStatus.SERVICE_UNAVAILABLE
            is DuplicateKeyException -> HttpStatus.CONFLICT
            is IllegalArgumentException, is org.springframework.http.converter.HttpMessageNotReadableException,
            is org.springframework.web.bind.MissingServletRequestParameterException,
            is org.springframework.web.method.annotation.MethodArgumentTypeMismatchException -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        val message = when {
            ex is MeetingRecordException -> ex.message ?: "会议处理失败"
            status == HttpStatus.BAD_REQUEST -> "会议参数无效，请检查填写内容"
            status == HttpStatus.CONFLICT -> "会议已存在，请刷新后重试"
            else -> "会议处理失败，请稍后重试"
        }
        return ResponseEntity.status(status).body(ApiErrorResponse("MEETING_RECORD_${status.value()}",message,null))
    }
}
