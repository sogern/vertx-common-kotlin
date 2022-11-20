package dev.sognnes.vertx.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.sognnes.vertx.impl.core.Launcher.Companion.DEFAULT_APPLICATION_PROFILE
import dev.sognnes.vertx.impl.core.Launcher.Companion.DEFAULT_LOGBACK_CONFIGURATION_FILE
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.*

open class BaseApplicationConfig(
    var logging: LoggingConfig = LoggingConfig(),
    var profiles: String = DEFAULT_APPLICATION_PROFILE,
    var verticles: Array<VerticleConfig> = emptyArray(),
) {
    companion object {
        private val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun fromYAML(
            `is`: InputStream,
            profiles: Set<String> = setOf(DEFAULT_APPLICATION_PROFILE)
        ): List<BaseApplicationConfig> = BufferedInputStream(`is`).use { fis ->
            val parser = YAMLFactory().createParser(fis)
            objectMapper.readValues(parser, object : TypeReference<BaseApplicationConfig>() {})
                .readAll()
                .filter { config ->
                    config.profiles.contains(DEFAULT_APPLICATION_PROFILE) || profiles.any {
                        config.profiles.contains(it)
                    }
                }.toList()
        }
    }

    fun toJsonObject(): JsonObject {
        return JsonObject(
            mapOf(
                "logging" to JsonObject(mapOf("config" to logging.config)),
                "profiles" to profiles,
                "verticles" to JsonArray(
                    verticles.map { it.toJsonObject() }.toList()
                )
            )
        )
    }
}

class LoggingConfig {
    var config: String = DEFAULT_LOGBACK_CONFIGURATION_FILE
}

open class VerticleConfig(declaration: JsonNode) {
    var className: String
    var enabled: Optional<Boolean>
    var config: Optional<JsonObject>

    init {
        val decl = declaration.fields().next()
        className = decl.key
        config = Optional.ofNullable(decl.value["config"]).map { if (it.isNull) null else JsonObject(it.toString()) }
        enabled = Optional.ofNullable(decl.value["enabled"]?.asBoolean())
    }

    fun toJsonObject(): JsonObject {
        val props = mutableMapOf<String, Any>(
            "className" to className
        )
        // Allow inheritance from profiles higher up
        if (enabled.isPresent) {
            props += "enabled" to enabled.get()
        }
        if (config.isPresent) {
            props += "config" to config.get()
        }
        return JsonObject(props)
    }
}
