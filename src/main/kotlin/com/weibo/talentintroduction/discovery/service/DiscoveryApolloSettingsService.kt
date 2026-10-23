package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSetting
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingConflict
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

enum class ApolloSettingSource { SAVED, DEPLOYMENT_DEFAULT, LEGACY }

/** Shared run-snapshot protocol. LEGACY is accepted only when interpreting an existing run. */
data class FrozenApolloAccess private constructor(
    val schemaVersion: Int,
    val settingRevision: Long,
    val searchEnabled: Boolean,
    val detailEnabled: Boolean,
    val source: ApolloSettingSource
) {
    init {
        require(schemaVersion == SCHEMA_VERSION && settingRevision >= 0) { "Invalid Apollo access snapshot" }
        require(source != ApolloSettingSource.SAVED || settingRevision > 0) { "Invalid saved Apollo revision" }
        require(source != ApolloSettingSource.DEPLOYMENT_DEFAULT || settingRevision == 0L) {
            "Invalid deployment Apollo revision"
        }
    }

    fun toJson(mapper: ObjectMapper): ObjectNode = mapper.createObjectNode()
        .put("schemaVersion", schemaVersion).put("settingRevision", settingRevision)
        .put("searchEnabled", searchEnabled).put("detailEnabled", detailEnabled).put("source", source.name)

    companion object {
        const val SCHEMA_VERSION = 1
        private val FIELDS = setOf("schemaVersion", "settingRevision", "searchEnabled", "detailEnabled", "source")

        fun fromSettings(settings: DiscoveryApolloSettings): FrozenApolloAccess {
            require(settings.source != ApolloSettingSource.LEGACY) { "Legacy is not a new setting source" }
            return FrozenApolloAccess(SCHEMA_VERSION, settings.revision, settings.searchEnabled,
                settings.detailEnabled, settings.source)
        }

        fun parse(node: JsonNode): FrozenApolloAccess {
            require(node.isObject && node.fieldNames().asSequence().toSet() == FIELDS) {
                "Invalid Apollo access snapshot fields"
            }
            val schema = node.get("schemaVersion")
            val revision = node.get("settingRevision")
            require(schema.isIntegralNumber && schema.canConvertToInt() && schema.intValue() == SCHEMA_VERSION &&
                revision.isIntegralNumber && revision.canConvertToLong() && revision.longValue() >= 0 &&
                node.get("searchEnabled").isBoolean && node.get("detailEnabled").isBoolean &&
                node.get("source").isTextual) { "Invalid Apollo access snapshot types" }
            val source = ApolloSettingSource.values().firstOrNull { it.name == node.get("source").textValue() }
                ?: throw IllegalArgumentException("Invalid Apollo access snapshot source")
            return FrozenApolloAccess(schema.intValue(), revision.longValue(), node.get("searchEnabled").booleanValue(),
                node.get("detailEnabled").booleanValue(), source)
        }
    }
}

data class DiscoveryApolloSettings(
    val revision: Long,
    val source: ApolloSettingSource,
    val searchEnabled: Boolean,
    val detailEnabled: Boolean,
    val effectiveSearch: Boolean,
    val effectiveDetails: Boolean,
    val keyConfigured: Boolean,
    val serviceEnabled: Boolean,
    val workerEnabled: Boolean,
    val updatedAt: Instant?
)

class DiscoveryApolloSettingsUnavailable : IllegalStateException("Apollo settings unavailable")

/** No worker/provider dependency: saving changes storage only, never an existing run. */
@Service
class DiscoveryApolloSettingsService(
    private val repository: DiscoveryApolloSettingRepository,
    private val properties: DiscoveryVerificationProperties,
    private val clock: Clock = Clock.systemUTC()
) {
    fun resolve(): DiscoveryApolloSettings = storage { view(repository.find()) }

    fun save(revision: Long, searchEnabled: Boolean, detailEnabled: Boolean): DiscoveryApolloSettings {
        require(revision >= 0) { "Invalid Apollo setting revision" }
        return storage { view(repository.save(revision, searchEnabled, detailEnabled, clock.instant())) }
    }

    fun freeze(): FrozenApolloAccess = FrozenApolloAccess.fromSettings(resolve())

    private fun view(row: DiscoveryApolloSetting?): DiscoveryApolloSettings {
        val keyConfigured = properties.apolloApiKey.isNotBlank()
        val search = row?.searchEnabled ?: (properties.enabled && keyConfigured && properties.apolloDefault)
        val detail = row?.detailEnabled ?: false
        val effectiveSearch = search && properties.enabled && keyConfigured
        return DiscoveryApolloSettings(row?.revision ?: 0, if (row == null) ApolloSettingSource.DEPLOYMENT_DEFAULT
            else ApolloSettingSource.SAVED, search, detail, effectiveSearch, effectiveSearch && detail,
            keyConfigured, properties.enabled, properties.workerEnabled, row?.updatedAt)
    }

    private fun <T> storage(action: () -> T): T = try {
        action()
    } catch (conflict: DiscoveryApolloSettingConflict) {
        throw conflict
    } catch (_: Exception) {
        // Do not attach JDBC/config exception messages or causes to an operator-visible failure.
        throw DiscoveryApolloSettingsUnavailable()
    }
}
