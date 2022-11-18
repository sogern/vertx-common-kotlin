package dev.sognnes.vertx.impl.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.sognnes.vertx.config.BaseApplicationConfig
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.impl.launcher.VertxCommandLauncher
import io.vertx.core.impl.launcher.VertxLifecycleHooks
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.SLF4JLogDelegateFactory
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.util.*

// Inspiration from: https://github.com/greyseal/vertx-event-bus/blob/master/src/main/java/com/api/scrubber/launcher/ScrubberLauncher.java
open class Launcher : VertxCommandLauncher(), VertxLifecycleHooks {

    companion object {
        private const val PROPERTY_LOGGER_DELEGATE_FACTORY = io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
        private const val PROPERTY_LOGBACK_CONFIGURATION_FILE = ch.qos.logback.classic.util.ContextInitializer.CONFIG_FILE_PROPERTY
        private const val PROPERTY_CONFIG_PATH = "application.config.path"
        private const val PROPERTY_APPLICATION_PROFILES = "application.profiles"
        const val DEFAULT_APPLICATION_PROFILE = "default"
        const val DEFAULT_LOGBACK_CONFIGURATION_FILE = "logback.xml"
        private const val DEFAULT_CONFIG_FILE = "application.yaml"

        private val defaultProperties = mapOf(
            PROPERTY_LOGGER_DELEGATE_FACTORY to SLF4JLogDelegateFactory::class.java.canonicalName,
            PROPERTY_LOGBACK_CONFIGURATION_FILE to DEFAULT_LOGBACK_CONFIGURATION_FILE,
            PROPERTY_CONFIG_PATH to DEFAULT_CONFIG_FILE,
            PROPERTY_APPLICATION_PROFILES to DEFAULT_APPLICATION_PROFILE
        )

        private fun getPropertiesOrDefault(props: Properties): Properties {
            defaultProperties.forEach { p ->
                if (!props.containsKey(p.key) || (props[p.key] as String).trim() == "") {
                    props[p.key] = p.value
                }
            }
            return props
        }

        private fun initSystemProperties(): Properties {
            val properties = getPropertiesOrDefault(System.getProperties())
            applicationConfigs = getApplicationConfigs(properties)
            setPropertiesBasedOnConfig(applicationConfigs, properties)
            System.setProperties(properties)
            return properties
        }

        private fun setPropertiesBasedOnConfig(applicationConfigs: List<BaseApplicationConfig>, props: Properties) {
            val logbackConfigFile = applicationConfigs
                .map { it.logging.config }
                .lastOrNull { it != DEFAULT_LOGBACK_CONFIGURATION_FILE }
                .let { it ?: DEFAULT_LOGBACK_CONFIGURATION_FILE }
            props[PROPERTY_LOGBACK_CONFIGURATION_FILE] = logbackConfigFile
        }

        private fun getApplicationConfigs(properties: Properties): List<BaseApplicationConfig> {
            val applicationConfigResources = this::class.java.getResourceAsStream("/" + properties[PROPERTY_CONFIG_PATH] as String)
                ?: throw IllegalArgumentException("Resource not found: ${properties[PROPERTY_CONFIG_PATH]}")

            val profiles = (properties[PROPERTY_APPLICATION_PROFILES]!! as String).split(",").toSet()

            // Ref: https://stackoverflow.com/questions/25222327/deserialize-pojos-from-multiple-yaml-documents-in-a-single-file-in-jackson
            val configs = BufferedInputStream(applicationConfigResources).use { fis ->
                val parser = YAMLFactory().createParser(fis)
                objectMapper.readValues(parser, object : TypeReference<BaseApplicationConfig>() {})
                    .readAll()
                    .filter { config ->
                        config.profiles.contains(DEFAULT_APPLICATION_PROFILE) || profiles.any {
                            config.profiles.contains(
                                it
                            )
                        }
                    }.toList()
            }
            return configs
        }

        private val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private lateinit var log: Logger

        private var applicationConfigs = emptyList<BaseApplicationConfig>()

        val instance: Launcher = Launcher()

        @Override
        @JvmStatic
        fun main(args: Array<String>) {
            val properties = initSystemProperties()

            log = LoggerFactory.getLogger(Launcher::class.java)
            log.debug("Application starting")
            log.debug("Args:\n${args.joinToString(" ")}")

            if (log.isDebugEnabled) {
                val systemProperties = properties.map { p -> "${p.key}: ${p.value}" }
                    .joinToString("\n")
                log.debug("System properties:\n$systemProperties")
                applicationConfigs.forEach {
                    log.debug("Config read: ${it.profiles}")
                }
            }

            val finalArgs: MutableList<String> = mutableListOf()
            if (args.indexOf("run") == -1) {
                finalArgs += "run"
                finalArgs += MainVerticle::class.java.canonicalName
            }
            finalArgs += args
            instance.dispatch(finalArgs.toTypedArray())
        }
    }

    fun configRetrieverOptions(): ConfigRetrieverOptions {
        val out = ConfigRetrieverOptions()
        applicationConfigs
            .map { config -> config.verticles.map { it.toJsonObject() } }
            .flatten()
            .map {
                JsonObject().put(it["className"], it)
            }
            .map {
                ConfigStoreOptions()
                    .setType("json")
                    .setConfig(it)
            }
            .forEach {
                out.addStore(it)
            }
        return out
    }

    override fun handleDeployFailed(vertx: Vertx?, mainVerticle: String?, deploymentOptions: DeploymentOptions?, cause: Throwable?) {
        vertx?.close()
    }

    override fun beforeStartingVertx(options: VertxOptions?) {
        log.info("Starting vertx with options: $options")
    }

    override fun afterStoppingVertx() {
    }

    override fun afterConfigParsed(config: JsonObject?) {
        log.debug("Config: ${config?.encodePrettily()}")
    }

    override fun afterStartingVertx(vertx: Vertx?) {

    }

    override fun beforeStoppingVertx(vertx: Vertx?) {
    }

    override fun beforeDeployingVerticle(deploymentOptions: DeploymentOptions?) {
    }

}

class MainVerticle : CoroutineVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger(MainVerticle::class.java)
    }

    override suspend fun start() {
        val options = Launcher.instance.configRetrieverOptions()
        val retriever = ConfigRetriever.create(vertx, options)

        retriever.getConfig {
            if (it.failed()) {
                log.error("Could not get config", it.cause())
                return@getConfig
            }
            log.info("Deploying verticles")
            val r = it.result()
            r.fieldNames().forEach { className ->
                val config: JsonObject = r[className] ?: JsonObject()
                val enabled: Boolean = config["enabled"] ?: false
                if (!enabled) {
                    log.info("Skipping disabled verticle $className")
                    return@forEach
                }
                launch {
                    try {
                        val result = vertx.deployVerticle(
                            Class.forName(className).canonicalName,
                            deploymentOptionsOf(config = config["config"])
                        ).await()
                        log.info("Deploy success $result")
                    } catch (t: Throwable) {
                        log.error("Deploy failure $className", t)
                    }
                }
            }
        }
    }
}
