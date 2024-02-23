import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging

// Gradle

plugins {
  kotlin("jvm") version "1.9.22"
  kotlin("kapt") version "1.9.22"
  kotlin("plugin.spring") version "1.9.22"
  id("org.springframework.boot") version "3.2.3"
  id("io.spring.dependency-management") version "1.1.4"
  id("com.gorylenko.gradle-git-properties") version "2.4.1"
  id("com.adarshr.test-logger") version "4.0.0"
  id("com.avast.gradle.docker-compose") version "0.17.6"
  id("org.flywaydb.flyway") version "9.22.3"
  id("nu.studer.jooq") version "9.0"
  jacoco
  id("org.sonarqube") version "4.4.1.3373"
  id("com.dorongold.task-tree") version "2.1.1"
}

tasks.wrapper {
  gradleVersion = "8.6"
}

group = "dev.pankowski"

// Dependencies

repositories {
  mavenCentral()
}

dependencyManagement {
  dependencies {
    dependency("com.google.guava:guava:33.0.0-jre")
    dependency("org.jsoup:jsoup:1.17.2")
    dependency("org.mozilla:rhino:1.7.14")
    dependency("net.thisptr:jackson-jq:1.0.0-preview.20240207")
    dependency("com.slack.api:slack-api-client:1.38.1")
    dependency("io.kotest:kotest-runner-junit5:5.8.0")
    dependency("io.kotest:kotest-framework-datatest:5.8.0")
    dependency("io.kotest:kotest-assertions-core:5.8.0")
    dependency("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    dependency("io.kotest.extensions:kotest-extensions-wiremock:3.0.1")
    dependency("io.mockk:mockk:1.13.9")
    dependency("org.wiremock:wiremock-standalone:3.4.2")
    dependency("com.tngtech.archunit:archunit-junit5:1.2.1")
  }
}

dependencies {
  // Kotlin & standard library
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib")
  implementation("com.google.guava:guava")

  // Web
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  // HTML, JavaScript, JSON processing
  implementation("org.jsoup:jsoup")
  implementation("org.mozilla:rhino")
  implementation("net.thisptr:jackson-jq")

  // Persistence
  implementation("org.springframework.boot:spring-boot-starter-jooq")
  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql")

  // Jooq generator
  jooqGenerator("org.postgresql:postgresql")

  // Slack
  implementation("com.slack.api:slack-api-client")

  // Tests
  testImplementation("io.mockk:mockk")
  testImplementation("io.kotest:kotest-runner-junit5")
  testImplementation("io.kotest:kotest-framework-datatest")
  testImplementation("io.kotest:kotest-assertions-core")
  testImplementation("io.kotest.extensions:kotest-extensions-spring")
  testImplementation("io.kotest.extensions:kotest-extensions-wiremock")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.rest-assured:rest-assured")
  testImplementation("org.wiremock:wiremock-standalone")
  testImplementation("com.tngtech.archunit:archunit-junit5")

  // Other
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  developmentOnly("org.springframework.boot:spring-boot-devtools")

  // Monitoring
  implementation("io.micrometer:micrometer-registry-prometheus")
}

// Java compiler

java {
  sourceCompatibility = JavaVersion.VERSION_19
  targetCompatibility = JavaVersion.VERSION_19
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict") // Enable strict null-safety for Kotlin project
    jvmTarget = "19"
  }
}

// Spring

springBoot {
  buildInfo()
}

tasks.bootJar {
  archiveFileName = "application.jar"
}

// CI

val isCiEnv = System.getenv("CI") == "true"

// Docker compose

dockerCompose {
  useComposeFiles = listOf("docker-compose-integration-test.yml")
  // Starting from plugin version 0.17.0, useDockerComposeV2 property defaults to true, so the new docker compose
  // (instead of deprecated docker-compose) is used. However, docker compose v2 isn't yet packaged in mainstream
  // linux distros. Let's not force anyone to install v2 manually and wait until distros come with v2 as the default.
  useDockerComposeV2 = false
}

// Flyway

val dbUrl = "jdbc:postgresql://localhost:5432/garcon"
val dbUser = "garcon"
val dbPassword = "garcon"

flyway {
  url = dbUrl
  user = dbUser
  password = dbPassword
  schemas = arrayOf("public")
}

tasks.flywayMigrate {
  dependsOn(tasks.composeUp)
}

// Database

tasks.register("databaseUp") {
  dependsOn(tasks.composeUp)
  dependsOn(tasks.flywayMigrate)
}

tasks.register("databaseDown") {
  dependsOn(tasks.composeDown)
}

// Jooq

jooq {
  version = "3.19.1"

  configurations {
    create("main") {

      jooqConfiguration.apply {
        logging = Logging.WARN
        jdbc.apply {
          driver = "org.postgresql.Driver"
          url = dbUrl
          user = dbUser
          password = dbPassword
        }
        generator.apply {
          name = "org.jooq.codegen.DefaultGenerator"
          database.apply {
            name = "org.jooq.meta.postgres.PostgresDatabase"
            inputSchema = "public"
            // `flyway_schema_history` is a Flyway table.
            excludes = "flyway_schema_history"
            forcedTypes.addAll(listOf(
              ForcedType().apply {
                includeTypes = "timestamp\\ with\\ time\\ zone"
                name = "INSTANT"
              },
              ForcedType().apply {
                includeTypes = "integer"
                includeExpression = "version"
                userType = "dev.pankowski.garcon.domain.Version"
                converter = "dev.pankowski.garcon.infrastructure.persistence.VersionConverter"
              },
              ForcedType().apply {
                includeExpression = "classification"
                userType = "dev.pankowski.garcon.domain.Classification"
                isEnumConverter = true
              },
              ForcedType().apply {
                includeExpression = "repost_status"
                userType = "dev.pankowski.garcon.domain.RepostStatus"
                isEnumConverter = true
              },
              // Jooq applies the first matching conversion it encounters.
              // For columns holding enum values both below and enum converters match.
              // We want enum converters to win, so conversion below must be specified after them.
              // We need this because PostgreSQL `text` type doesn't have length restrictions,
              // so Jooq treats this as CLOB (mapped to String on Java side), but I don't want the JDBC
              // CLOB infrastructure to be involved for these fields.
              ForcedType().apply {
                includeTypes = "text"
                name = "varchar"
              }
            ))
          }
          generate.apply {
            isDeprecated = false
          }
          target.apply {
            packageName = "dev.pankowski.garcon.infrastructure.persistence.generated"
            directory = "build/generated/source/jooq/main"
          }
        }
      }
    }
  }
}

// See https://github.com/etiennestuder/gradle-jooq-plugin#synchronizing-the-jooq-version-between-the-spring-boot-gradle-plugin-and-the-jooq-gradle-plugin
ext["jooq.version"] = jooq.version.get()

// Accessor for convenience
val TaskContainer.generateJooq
  get() = named<JooqGenerate>("generateJooq")

// See https://github.com/etiennestuder/gradle-jooq-plugin#configuring-the-jooq-generation-task-to-participate-in-incremental-builds-and-build-caching
tasks.generateJooq {
  inputs.dir("src/main/resources/db/migration")
  allInputsDeclared = true
  dependsOn(tasks.flywayMigrate)
}

tasks.compileKotlin {
  mustRunAfter(tasks.generateJooq)
}

// Tests & code coverage

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

jacoco {
  toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
  reports {
    html.required = true
    xml.required = true
  }
}

// SonarCloud

sonar {
  properties {
    property("sonar.organization", "apankowski")
    property("sonar.projectKey", "garcon")
    property("sonar.host.url", "https://sonarcloud.io")
  }
}

// Sonar v5 will no longer trigger compilation tasks. Therefore, we declare the dependencies ourselves.
// See: https://community.sonarsource.com/t/sonarscanner-for-gradle-you-can-now-decide-when-to-compile/102069
tasks.sonar {
  dependsOn(tasks.compileKotlin)
  dependsOn(tasks.generateJooq)
}
