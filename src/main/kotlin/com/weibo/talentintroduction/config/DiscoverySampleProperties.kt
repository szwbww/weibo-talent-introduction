package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths

@ConfigurationProperties("talent-introduction.expert-discovery.samples")
data class DiscoverySampleProperties(
    var enabled: Boolean = false,
    var directory: String = "/opt/talent/data/discovery-samples",
    var retentionDays: Int = 7,
    var maxTotalBytes: Long = 536870912,
    var maxTotalSamples: Int = 1000,
    var maxBodyBytes: Long = 10485760,
    var maxBodiesPerSample: Int = 2,
    var maxMetadataBytes: Int = 1048576,
    var maxAttemptsPerSample: Int = 16,
    var maxSamplesPerBucketPerRun: Int = 20,
    var maxSuccessControlsPerRun: Int = 10,
    var maxInFlightCaptures: Int = 2
) {
    fun valid(): Boolean = try {
        Paths.get(directory).isAbsolute && retentionDays > 0 && maxTotalSamples > 0 &&
            maxBodyBytes in 1..10485760 && maxBodiesPerSample in 1..2 &&
            maxMetadataBytes in 1..1048576 && maxAttemptsPerSample in 1..16 &&
            maxSamplesPerBucketPerRun > 0 && maxSuccessControlsPerRun > 0 &&
            maxInFlightCaptures in 1..2 &&
            maxTotalBytes >= maxBodyBytes * maxBodiesPerSample + maxMetadataBytes
    } catch (_: Exception) { false }
}

@Configuration
@EnableConfigurationProperties(DiscoverySampleProperties::class)
class DiscoverySampleConfiguration
