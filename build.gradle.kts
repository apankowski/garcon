import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging

// Gradle

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("org.flywaydb:flyway-database-postgresql:11.3.1")
  }
}

plugins {
  kotlin("jvm") version "2.1.10"
  kotlin("plugin.spring") version "2.1.10"
  id("org.springframework.boot") version "3.4.2"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.gorylenko.gradle-git-properties") version "2.4.2"
  id("com.adarshr.test-logger") version "4.0.0"
  id("com.avast.gradle.docker-compose") version "0.17.12"
  id("org.flywaydb.flyway") version "11.3.1"
  id("nu.studer.jooq") version "9.0"
  jacoco
  id("org.sonarqube") version "6.0.1.5171"
  id("com.dorongold.task-tree") version "4.0.0"
}

tasks.wrapper {
  gradleVersion = "8.12.1"
}

group = "dev.pankowski"

// Dependencies

repositories {
  mavenCentral()
}

dependencyManagement {
  dependencies {
    dependency("com.google.guava:guava:33.4.0-jre")
    dependency("org.jsoup:jsoup:1.18.3")
    dependency("org.mozilla:rhino:1.8.0")
    dependency("net.thisptr:jackson-jq:1.2.0")
    dependency("com.slack.api:slack-api-client:1.45.2")
    dependency("io.kotest:kotest-runner-junit5:5.9.1")
    dependency("io.kotest:kotest-framework-datatest:5.9.1")
    dependency("io.kotest:kotest-assertions-core:5.9.1")
    dependency("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    dependency("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    dependency("io.kotest.extensions:kotest-extensions-wiremock:3.1.0")
    dependency("io.mockk:mockk:1.13.16")
    dependency("org.wiremock:wiremock-standalone:3.12.0")
    dependency("com.tngtech.archunit:archunit-junit5:1.4.0")
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
  implementation("org.flywaydb:flyway-database-postgresql")
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
  testImplementation("io.kotest.extensions:kotest-extensions-testcontainers")
  testImplementation("io.kotest.extensions:kotest-extensions-spring")
  testImplementation("io.kotest.extensions:kotest-extensions-wiremock")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.testcontainers:testcontainers")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("io.rest-assured:rest-assured")
  testImplementation("org.wiremock:wiremock-standalone")
  testImplementation("com.tngtech.archunit:archunit-junit5")

  // Monitoring
  implementation("io.micrometer:micrometer-registry-prometheus")

  // Other
  developmentOnly("org.springframework.boot:spring-boot-devtools")
}

// Java compiler

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

// Kotlin compiler

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_21
    freeCompilerArgs = listOf(
      "-Xjsr305=strict", // Enable strict null checking
      "-Xemit-jvm-type-annotations", // Enable type annotations
    )
  }
}

// Spring

springBoot {
  buildInfo()
}

tasks.bootJar {
  archiveFileName = "application.jar"
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
  version = "3.19.18"

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

// See https://github.com/etiennestuder/gradle-jooq-plugin/blob/main/example/configure_jooq_with_flyway/build.gradle
tasks.generateJooq {
  dependsOn(tasks.flywayMigrate)

  inputs.files(fileTree("src/main/resources/db/migration"))
    .withPropertyName("migrations")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  allInputsDeclared = true
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
  toolVersion = "0.8.12"
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

// Sonar v5 doesn't trigger compilation tasks. Therefore, we declare the dependencies ourselves.
tasks.sonar {
  dependsOn(tasks.compileKotlin)
  dependsOn(tasks.generateJooq)
}
