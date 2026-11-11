package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

data class BuiltinAttachmentOption(
    val id: String,
    val filename: String,
    val contentType: String,
    val byteLength: Long
)

/** Server-owned catalog. Selecting a resource creates an ordinary, immutable outbound copy. */
@Service
class BuiltinAttachmentService(
    private val outbound: OutboundAttachmentService,
    private val contacts: ExpertContactRepository
) {
    // Version the ID when replacing a file; no client-supplied paths or filenames are opened.
    private val catalog = mapOf("qiming-program-information-v1" to "Program_Information.pdf")

    fun list(contactId: Long): List<BuiltinAttachmentOption> {
        if (!contacts.existsById(contactId)) throw OutboundAttachmentException.notFound("专家不存在")
        return catalog.map { (id, filename) ->
            BuiltinAttachmentOption(id, filename, "application/pdf", resource(filename).contentLength())
        }
    }

    fun add(contactId: Long, builtinId: String, username: String): OutboundAttachmentUploadResponse {
        val filename = catalog[builtinId] ?: throw OutboundAttachmentException.notFound("内置附件不存在")
        return resource(filename).inputStream.use {
            outbound.upload(contactId, username, filename, "application/pdf", it)
        }
    }

    private fun resource(filename: String): ClassPathResource =
        ClassPathResource("mail-attachments/$filename").also {
            if (!it.exists()) throw OutboundAttachmentException.notFound("内置附件原件缺失")
        }
}
