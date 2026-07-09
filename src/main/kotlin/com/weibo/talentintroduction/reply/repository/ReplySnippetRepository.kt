package com.weibo.talentintroduction.reply.repository

import com.weibo.talentintroduction.reply.domain.ReplySnippet
import org.springframework.data.repository.CrudRepository

interface ReplySnippetRepository : CrudRepository<ReplySnippet, Long> {
    fun findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc(): List<ReplySnippet>

    fun findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(snippetType: String): List<ReplySnippet>

    fun findBySnippetTypeAndIsDefaultTrue(snippetType: String): List<ReplySnippet>
}
