package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@TestConfiguration
@EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.task.repository", "com.weibo.talentintroduction.discovery.repository"])
internal class Paper04JdbcConfig : AbstractJdbcConfiguration() {
    @Bean fun namedParameterJdbcOperations(source: DataSource): NamedParameterJdbcOperations = NamedParameterJdbcTemplate(source)
    @Bean fun transactionManager(source: DataSource) = DataSourceTransactionManager(source)
}

class DiscoveryPaperReprocessApplyServiceTest {
    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)
    @Test fun `missing worker fails before any consumer or business write`() {
        val consumer = mock<ExpertDiscoveryService>()
        val writer = mock<ExpertIndexWriterService>()
        val registration = mock<DiscoveryVerificationRegistrationService>()
        val service = DiscoveryPaperReprocessApplyService(mock(), mock(), registration, consumer, mock(), writer,
            mock(), mock(), mock(), jacksonObjectMapper(), mock(), DefaultListableBeanFactory().getBeanProvider(DiscoveryVerificationWorker::class.java))
        val access = FrozenApolloAccess.parse(jacksonObjectMapper().readTree("""{"schemaVersion":1,"settingRevision":7,"searchEnabled":true,"detailEnabled":false,"source":"SAVED"}"""))
        assertEquals("VERIFICATION_UNAVAILABLE", assertThrows(IllegalStateException::class.java) { service.freezeSettings(access) }.message)
        Mockito.verifyNoInteractions(consumer, writer, registration)
    }

    @Test fun `public preparation contract has no parent settings or author authority`() {
        val fields = RecheckPrepareRequest::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("setting", true) || it.contains("parent", true) || it.contains("author", true) })
        assertTrue(ParsedArchivedPaper::class.java.declaredConstructors.filterNot { it.isSynthetic }.all {
            java.lang.reflect.Modifier.isPrivate(it.modifiers)
        })
    }
}
