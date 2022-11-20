# Vert.x common lib for Kotlin

Convenience classes for Kotlin [Vert.x](https://vertx.io/) applications.

Based on Vert.x 4.3, but should be compatible with future 4.x versions.

**Features**:
* `application.yaml` config for verticles and logging with profiles similar to [Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
* Setup SLF4J logging


## Including in your project
Add this project as a Git [submodule](https://git-scm.com/book/en/v2/Git-Tools-Submodules):
```shell
git submodule add https://github.com/sogern/vertx-common-kotlin .vertx-common-kotlin
```

### Maven
Add the `build-helper-maven-plugin` to your `pom.xml` and then add the main source folders from
.vertx-common-kotlin, thus:
```xml
<build>
    <!-- ... -->
    <plugins>
        <!-- ... -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <version>3.3.0</version>
            <executions>
                <execution>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>.vertx-common-kotlin/src/main/kotlin</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <!-- ... -->
    </plugins>
</build>
```

TODO: instructions for Gradle projects
## Usage

Add `application.yaml` and `logback.xml` files to your `resources` folder.
Write your verticles and set main class to `dev.sognnes.vertx.impl.core.Launcher`.
Optionally specify application profile(s) on the command line with `application.profiles` (comma separated list).

Defining multiple profiles behave in a similar way as Spring Boot, where you can override configurations based on which
profiles are enabled. I.e. the individual YAML documents are added together based on the list of enabled profiles.

Example:
```shell
java -jar YourApplication.jar -Dapplication.profiles=profile1,profile2
```

### Application.yaml
There are 3 main parameters / sections:
* `profiles`
* `logging`
* `verticles`

See example file below.

#### Profiles
A comma separated list of profile names where the YAML config should be included. I.e.: `profiles: profile1,profile2`.<br/>
If no `profiles` is specified, it defaults to `default` and will always be included.

#### Logging
Specify `logging.config` to configure the Logback xml file that is to be used in the profile.<br/>
When omitted, the default is `logback.xml`.

#### Verticles
A list of Verticle class names. Each must be `enabled` in order to be started.<br/>
The optional `config` parameter is up to you to define based on your application's needs.<br/>
It is retrieved in your Verticle's `start` method thus:

```kotlin
override fun start() {
    val config = vertx.orCreateContext.config()
    // ...
}
```

**Example file**:
```yaml
# A default profile with logging configured to logback.xml
verticles:
  - com.example.SomeVerticle:
      enabled: true
      config:
        name1: value1
        name2: ${VAL_2}        # Replaced with environment variable, empty if not present
        name3: ${VAL_3:value3} # Replaced with environment variable, or use fallback if not present
        name4:
          - list_item1
          - list_item2
        name5:
          sub_object_name1: value3
  - com.example.AnotherVerticle:
      enabled: false

---
profiles: profile1,profile2

logging:
  config: some_other_logback.xml

verticles:
  - com.example.AnotherVerticle:
      enabled: true
      config:
        name1: value1

---
profiles: profile3

verticles:
  - com.example.SomeVerticle:
      enabled: false
```


### Logback.xml

See [Logback documentation](https://logback.qos.ch/manual/index.html).
