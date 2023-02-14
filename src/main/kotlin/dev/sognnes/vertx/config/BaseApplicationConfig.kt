package dev.sognnes.vertx.config

import dev.sognnes.vertx.impl.core.Launcher.Companion.DEFAULT_APPLICATION_PROFILE
import dev.sognnes.vertx.impl.core.Launcher.Companion.DEFAULT_LOGBACK_CONFIGURATION_FILE
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import org.yaml.snakeyaml.Yaml
import java.io.BufferedInputStream
import java.io.InputStream

data class BaseApplicationConfig(
    val logging: LoggingConfig = LoggingConfig(),
    val profiles: String = DEFAULT_APPLICATION_PROFILE,
    val verticles: List<VerticleConfig> = emptyList(),
) {
    companion object {
        fun fromYAML(
            `is`: InputStream,
            profiles: Set<String> = setOf(DEFAULT_APPLICATION_PROFILE)
        ): List<BaseApplicationConfig> = BufferedInputStream(`is`).use { bis ->
            Yaml().loadAll(bis)
                .asSequence()
                .map(JsonObject::mapFrom)
                .map(BaseApplicationConfig::fromJson)
                .filter { config ->
                    config.profiles.contains(DEFAULT_APPLICATION_PROFILE) || profiles.any {
                        config.profiles.contains(it)
                    }
                }
                .toList()
        }

        private fun fromJson(jsonObject: JsonObject): BaseApplicationConfig = BaseApplicationConfig(
            LoggingConfig.fromJson(jsonObject["logging"] ?: JsonObject()),
            jsonObject.getString("profiles") ?: DEFAULT_APPLICATION_PROFILE,
            jsonObject.getJsonArray("verticles")
                ?.let { it.list as List<LinkedHashMap<String, Any>> }
                ?.map { VerticleConfig.fromJson(it.keys.first(), JsonObject.mapFrom(it[it.keys.first()] ?: object { })) }
                ?: emptyList()
        )
    }

    fun toJson() = JsonObject(
        mapOf(
            "logging" to JsonObject(mapOf("config" to logging.config)),
            "profiles" to profiles,
            "verticles" to JsonArray(
                verticles.map { it.toJson() }.toList()
            )
        )
    )
}

data class LoggingConfig(
    val config: String = DEFAULT_LOGBACK_CONFIGURATION_FILE
) {
    companion object {
        fun fromJson(jsonObject: JsonObject): LoggingConfig = LoggingConfig(
            jsonObject.getString("config") ?: DEFAULT_LOGBACK_CONFIGURATION_FILE
        )
    }

}

data class VerticleConfig(
    val className: String,
    val priority: Int?,
    val enabled: Boolean?,
    val config: JsonObject?
) {
    companion object {
        fun fromJson(className: String, jsonObject: JsonObject): VerticleConfig =
            VerticleConfig(
                className,
                jsonObject.getInteger("priority"),
                jsonObject.getBoolean("enabled"),
                jsonObject.getJsonObject("config")
            )
    }

    fun toJson(): JsonObject {
        val props = mutableMapOf<String, Any>(
            "className" to className
        )
        // Allow inheritance from profiles higher up
        priority?.let { props += "priority" to priority }
        enabled?.let { props += "enabled" to enabled }
        config?.let { props += "config" to config }
        return JsonObject(props)
    }
}
