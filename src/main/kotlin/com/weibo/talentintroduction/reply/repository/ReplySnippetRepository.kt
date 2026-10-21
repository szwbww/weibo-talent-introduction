package com.weibo.talentintroduction.reply.repository

import com.weibo.talentintroduction.reply.domain.ReplySnippet
import org.springframework.data.repository.CrudRepository
import org.springframework.data.jdbc.repository.query.Query

interface ReplySnippetRepository : CrudRepository<ReplySnippet, Long> {
    @Query("SELECT * FROM reply_snippet ORDER BY id FOR UPDATE")
    fun lockAllForUpdate(): List<ReplySnippet>

    fun findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc(): List<ReplySnippet>

    fun findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndIsDefaultTrue(snippetType: String): List<ReplySnippet>
}
