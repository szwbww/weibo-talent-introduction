package com.weibo.talentintroduction.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
@EnableConfigurationProperties(
    ElasticsearchProperties::class,
    CandidateFilterProperties::class,
    MailQueueProperties::class,
    MailSchedulingProperties::class,
    MailAttachmentStorageProperties::class,
    EmailValidationProperties::class,
    AcademicFilterProperties::class,
    EuropePmcProperties::class,
    ExpertDiscoveryProperties::class,
    OpenAlexProperties::class,
    CrossrefProperties::class,
    ArxivProperties::class,
    UnpaywallProperties::class,
    PdfExtractionProperties::class,
    PmcOaProperties::class,
    OrcidProperties::class,
    CoreProperties::class,
    ManualOutreachProperties::class,
    UnsubscribeProperties::class,
    WarmupProperties::class
)
class RestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

    @Bean
    @Qualifier("europePmcRestTemplate")
    fun europePmcRestTemplate(
        europePmcProperties: EuropePmcProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(europePmcProperties.connectTimeoutMs.toLong()))
            .setReadTimeout(Duration.ofMillis(europePmcProperties.readTimeoutMs.toLong()))
            .build()

    @Bean
    @Qualifier("pdfDownloadRestTemplate")
    fun pdfDownloadRestTemplate(
        pdfExtractionProperties: PdfExtractionProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(10_000L))
            .setReadTimeout(Duration.ofMillis(pdfExtractionProperties.downloadTimeoutMs))
            .build()
}
