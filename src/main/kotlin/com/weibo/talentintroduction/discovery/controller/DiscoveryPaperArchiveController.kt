package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.discovery.archive.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletResponse

/** All archive routes remain covered by the existing AuthWebConfig interceptor. */
@RestController
@RequestMapping("/api/expert-discovery/papers")
class DiscoveryPaperArchiveController(private val service: DiscoveryPaperArchiveService) {
    @GetMapping("/settings") fun settings() = service.settings()
    @PutMapping("/settings") fun settings(@RequestBody body: JsonNode) = service.updateSettings(
        ArchiveSettingsRequest(number(body,"revision"),boolean(body,"enabled"),number(body,"maxBytes"),number(body,"minFreeBytes")))
    @GetMapping("/stats") fun stats(@RequestParam(required=false) executionId: Long?) = service.stats(executionId)
    @GetMapping fun list(@RequestParam(required=false) executionId: Long?, @RequestParam(required=false) q: String?,
        @RequestParam(required=false) status: ArchiveRecordState?, @RequestParam(name="protected",required=false) protected: Boolean?,
        @RequestParam(required=false) hasGain: Boolean?, @RequestParam(defaultValue="0") page: Int,
        @RequestParam(defaultValue="20") size: Int) = service.list(ArchiveQuery(executionId,q,status,protected,hasGain,page,size))
    @GetMapping("/{id}") fun detail(@PathVariable id: Long) = service.detail(id)
    @PostMapping("/protection") fun protection(@RequestBody body: JsonNode): ArchiveProtectionResult {
        val items = body.path("items"); if (!items.isArray) throw ArchiveException("INVALID_IDS",400)
        return service.protection(ArchiveProtectionRequest(items.map { ArchiveRevisionItem(number(it,"id"),number(it,"revision")) },boolean(body,"protected")))
    }
    @PostMapping("/delete-preview") fun preview(@RequestBody body: JsonNode) = service.deletePreview(ids(body))
    @PostMapping("/delete") fun delete(@RequestBody body: JsonNode): ArchiveDeleteResult {
        val hash = body.path("snapshotHash")
        if (!hash.isTextual || !hash.asText().matches(Regex("[a-f0-9]{64}"))) throw ArchiveException("INVALID_SNAPSHOT",400)
        return service.delete(ArchiveDeleteRequest(ids(body),hash.asText()))
    }
    @GetMapping("/{id}/attempts/{attemptId}/content")
    fun content(@PathVariable id: Long, @PathVariable attemptId: Long, @RequestParam(defaultValue="false") download: Boolean,
        @RequestHeader(name="Range",required=false) range: String?, response: HttpServletResponse) {
        service.withContent(id,attemptId) { descriptor, input ->
            response.setHeader("Cache-Control","private,no-store")
            response.setHeader("X-Content-Type-Options","nosniff")
            response.setHeader("Content-Disposition","${if (download) "attachment" else "inline"}; filename=\"${descriptor.filename}\"")
            response.contentType = descriptor.mediaType
            val length = descriptor.length
            val pdf = descriptor.mediaType == "application/pdf"
            if (pdf) response.setHeader("Accept-Ranges","bytes")
            val selected = if (pdf && range != null) parseRange(range,length) else null
            if (pdf && range != null && selected == null) {
                response.status = 416; response.setHeader("Content-Range","bytes */$length"); response.setContentLengthLong(0)
            } else {
                val start = selected?.first ?: 0L; val end = selected?.second ?: (length - 1)
                if (selected != null) { response.status=206; response.setHeader("Content-Range","bytes $start-$end/$length") }
                var remaining = maxOf(0,end - start + 1)
                response.setContentLengthLong(remaining)
                var skip = start
                while (skip > 0) { val n = input.skip(skip); if (n <= 0) { if (input.read() < 0) throw ArchiveException("SOURCE_MISSING",410); skip-- } else skip -= n }
                val buffer = ByteArray(8192)
                while (remaining > 0) { val n = input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt()); if (n < 0) throw ArchiveException("SOURCE_MISSING",410); response.outputStream.write(buffer,0,n); remaining -= n }
            }
        }
    }
    private fun parseRange(value: String, length: Long): Pair<Long,Long>? {
        val match = Regex("bytes=([0-9]*)-([0-9]*)").matchEntire(value) ?: return null
        if (length <= 0) return null
        val (left,right) = match.destructured
        if (left.isEmpty()) {
            val suffix = right.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return maxOf(0,length-suffix) to length-1
        }
        val start = left.toLongOrNull()?.takeIf { it < length } ?: return null
        val end = if (right.isEmpty()) length-1 else right.toLongOrNull()?.takeIf { it >= start }?.coerceAtMost(length-1) ?: return null
        return start to end
    }
    private fun number(body: JsonNode,key: String): Long = number(body.path(key))
    private fun number(value: JsonNode): Long {
        if (!value.isIntegralNumber || !value.canConvertToLong()) throw ArchiveException("INVALID_INTEGER",400)
        return value.longValue()
    }
    private fun boolean(body: JsonNode,key: String): Boolean {
        val value=body.path(key); if (!value.isBoolean) throw ArchiveException("INVALID_BOOLEAN",400)
        return value.booleanValue()
    }
    private fun ids(body: JsonNode): List<Long> {
        val values=body.path("ids"); if (!values.isArray) throw ArchiveException("INVALID_IDS",400)
        return values.map { number(it) }
    }
    @ExceptionHandler(ArchiveException::class)
    fun failure(e: ArchiveException): ResponseEntity<Map<String,Any?>> = ResponseEntity.status(e.httpStatus)
        .body(mapOf("code" to e.code,"message" to e.message,"preview" to e.preview))
}
