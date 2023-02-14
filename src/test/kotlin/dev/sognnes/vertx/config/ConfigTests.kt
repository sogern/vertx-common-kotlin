package dev.sognnes.vertx.config

import dev.sognnes.vertx.impl.core.*
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class ConfigTests {

    companion object {
        val testFile: File = File("src/test/resources/test.yaml").canonicalFile

        @BeforeAll
        @JvmStatic
        fun setup() {
            if (!testFile.isFile || !testFile.exists()) {
                throw FileNotFoundException("File not found: ${testFile.absolutePath}")
            }
        }

    }

    @Test
    @DisplayName("Default config")
    fun defaultProfile() {
        val parsed = BaseApplicationConfig.fromYAML(FileInputStream(testFile))
        assertEquals(1, parsed.size)
        assertEquals(Launcher.DEFAULT_APPLICATION_PROFILE, parsed[0].profiles)
        assertEquals(Launcher.DEFAULT_LOGBACK_CONFIGURATION_FILE, parsed[0].logging.config)

        val properties = Properties()
        Launcher.setPropertiesBasedOnConfig(parsed, properties)
        assertEquals(Launcher.DEFAULT_LOGBACK_CONFIGURATION_FILE, properties[Launcher.PROPERTY_LOGBACK_CONFIGURATION_FILE])
    }

    @Test
    @DisplayName("Sandwiched config")
    fun profile1(vertx: Vertx) {
        val parsed = BaseApplicationConfig.fromYAML(FileInputStream(testFile), setOf("profile1"))
        assertEquals(2, parsed.size)
        assertEquals("profile1,profile2", parsed[1].profiles)
        assertEquals("some_other_logback.xml", parsed[1].logging.config)

        val properties = Properties()
        Launcher.setPropertiesBasedOnConfig(parsed, properties)
        assertEquals("some_other_logback.xml", properties[Launcher.PROPERTY_LOGBACK_CONFIGURATION_FILE])

        val configStoreOptions = toConfigStoreOptions(parsed)
        assertEquals(5, configStoreOptions.size)

        ConfigRetriever.create(vertx, ConfigRetrieverOptions().setStores(configStoreOptions)).getConfig {
            if (it.failed()) throw it.cause()

            val result = it.result()
            assertEquals(2, result.size())

            val anotherVerticleConfig = result.getJsonObject("com.example.AnotherVerticle")
            assertTrue(anotherVerticleConfig.getBoolean("enabled"))
            assertEquals("value1", anotherVerticleConfig.getJsonObject("config").getString("name1"))

            val importantVerticleConfig = result.getJsonObject("com.example.ImportantVerticle")
            assertEquals(1, importantVerticleConfig.getJsonObject("config").getInteger("priority"))
        }
    }

    @Test
    @DisplayName("Env variables")
    fun envVariables() {
        val props = Properties()
        listOf(
            "VAL_1" to "value1",
            "VAL_3" to "value3",
            "VAL_5" to "value5",
        ).forEach { (k, v) ->
            props.setProperty(k, v)
        }
        listOf(
            "\${VAL_1}" to "value1",
            "\${VAL_2:value2}" to "value2",
            "\${VAL_3:}" to "value3",
            "value4" to "value4",
            "prefix_\${VAL_5}_suffix" to "prefix_value5_suffix",
            "prefix_\${VAL_5}:\${VAL_1}_suffix" to "prefix_value5:value1_suffix",
            "\${VAL_1}.\${VAL_2:}.\${VAL_4:text}" to "value1..text"
        ).forEach { (value, expected) ->
            assertEquals(expected, injectPropertyValues(value, props))
        }
    }

    @Test
    @DisplayName("Inject env variables")
    fun injectEnvVariables() {
        val `is` = FileInputStream(testFile)
        val props = Properties()
        listOf(
            "VAL_2" to "value2",
            "VAL_3" to "value3",
            "VAL_4" to "value4",
            "VAL_5" to "value5",
        ).forEach { (k, v) ->
            props.setProperty(k, v)
        }
        val updatedInputStream = injectPropertyValuesInInputStream(`is`, props)
        val parsed = BaseApplicationConfig.fromYAML(updatedInputStream)

        val config = parsed[0].verticles[0].config
        assertEquals("value2", config?.getString("name2"))
        assertEquals("value3", config?.getString("name3"))
        assertEquals("protocol://value4:value5/endpoint", config?.getString("name4"))
    }
}
