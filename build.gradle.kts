import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging

// Gradle

plugins {
  kotlin("jvm") version "1.8.21"
  kotlin("kapt") version "1.8.21"
  kotlin("plugin.spring") version "1.8.21"
  id("org.springframework.boot") version "3.0.7"
  id("io.spring.dependency-management") version "1.1.0"
  id("com.gorylenko.gradle-git-properties") version "2.4.1"
  id("com.adarshr.test-logger") version "3.2.0"
  id("com.avast.gradle.docker-compose") version "0.16.12"
  id("org.flywaydb.flyway") version "9.18.0"
  id("nu.studer.jooq") version "8.2"
  jacoco
  id("org.sonarqube") version "4.0.0.2929"
}

tasks.wrapper {
  gradleVersion = "8.1.1"
}

group = "dev.pankowski"

// Dependencies

repositories {
  mavenCentral()
}

dependencyManagement {
  dependencies {
    dependency("com.google.guava:guava:31.1-jre")
    dependency("org.jsoup:jsoup:1.16.1")
    dependency("org.mozilla:rhino:1.7.14")
    dependency("net.thisptr:jackson-jq:1.0.0-preview.20230409")
    dependency("com.slack.api:slack-api-client:1.29.2")
    dependency("io.kotest:kotest-runner-junit5:5.6.2")
    dependency("io.kotest:kotest-framework-datatest:5.6.2")
    dependency("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    dependency("io.kotest.extensions:kotest-extensions-wiremock:2.0.0")
    dependency("io.mockk:mockk:1.13.5")
    dependency("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
    dependency("com.tngtech.archunit:archunit-junit5:1.0.1")
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
  testImplementation("io.kotest.extensions:kotest-extensions-wiremock")
  testImplementation("io.kotest.extensions:kotest-extensions-spring")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.rest-assured:rest-assured")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone")
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
  archiveFileName.set("application.jar")
}

// CI

val isCiEnv = System.getenv("CI") == "true"

// Tests & code coverage

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

jacoco {
  toolVersion = "0.8.10"
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

// SonarCloud

sonarqube {
  properties {
    property("sonar.organization", "apankowski")
    property("sonar.projectKey", "garcon")
    property("sonar.host.url", "https://sonarcloud.io")
  }
}

// Docker compose

dockerCompose {
  useComposeFiles.set(listOf("docker-compose-integration-test.yml"))
}

// Database

tasks.register("databaseUp") {
  dependsOn(tasks.composeUp)
  dependsOn(tasks.flywayMigrate)
}

tasks.register("databaseDown") {
  dependsOn(tasks.composeDown)
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

// Jooq

jooq {
  version.set("3.18.2")

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

val generateJooq = tasks.named<JooqGenerate>("generateJooq")

generateJooq {
  inputs.dir("src/main/resources/db/migration")
  allInputsDeclared.set(true)
  dependsOn(tasks.flywayMigrate)
}

tasks.compileKotlin {
  mustRunAfter(generateJooq)
}
