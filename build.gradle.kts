import com.rohanprabhu.gradle.plugins.kdjooq.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.pankowski"

// PLUGINS

plugins {
  kotlin("jvm") version "1.6.10"
  kotlin("kapt") version "1.6.10"
  kotlin("plugin.spring") version "1.6.10"
  id("org.springframework.boot") version "2.6.2"
  id("io.spring.dependency-management") version "1.0.11.RELEASE"
  id("com.gorylenko.gradle-git-properties") version "2.3.2"
  id("com.adarshr.test-logger") version "3.1.0"
  id("com.avast.gradle.docker-compose") version "0.14.11"
  id("org.flywaydb.flyway") version "8.3.0"
  id("com.rohanprabhu.kotlin-dsl-jooq") version "0.4.6"
}

tasks.wrapper {
  gradleVersion = "7.3.3"
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict") // Enable strict null-safety for Kotlin project
    jvmTarget = "17"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

springBoot {
  buildInfo()
}

tasks.bootJar {
  archiveFileName.set("application.jar")
}

// DEPENDENCIES

repositories {
  mavenCentral()
}

dependencyManagement {
  dependencies {
    dependency("org.jsoup:jsoup:1.14.3")
    dependency("com.github.tomakehurst:wiremock-jre8:2.32.0")
    dependency("io.kotest:kotest-runner-junit5:5.0.3")
    dependency("io.kotest:kotest-framework-datatest:5.0.3")
    dependency("io.kotest.extensions:kotest-extensions-spring:1.1.0")
    dependency("io.kotest.extensions:kotest-extensions-wiremock:1.0.3")
    dependency("io.mockk:mockk:1.12.1")
  }
}

dependencies {
  // KOTLIN
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  // WEB
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  // HTML
  implementation("org.jsoup:jsoup")

  // PERSISTENCE
  implementation("org.springframework.boot:spring-boot-starter-jooq")
  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql")

  // TESTS
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.rest-assured:rest-assured")
  testImplementation("com.github.tomakehurst:wiremock-jre8")

  testImplementation("io.mockk:mockk")
  testImplementation("io.kotest:kotest-runner-junit5")
  testImplementation("io.kotest:kotest-framework-datatest")
  testImplementation("io.kotest.extensions:kotest-extensions-wiremock")
  testImplementation("io.kotest.extensions:kotest-extensions-spring")

  // OTHER
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  jooqGeneratorRuntime("org.postgresql:postgresql")
}

// CI

val isCiEnv = System.getenv("CI") == "true"

// DOCKER COMPOSE

dockerCompose {
  useComposeFiles.set(listOf("docker-compose-integration-test.yml"))
}

// DATABASE

tasks.register("databaseUp") {
  dependsOn(tasks.composeUp)
  dependsOn(tasks.flywayMigrate)
}

tasks.register("databaseDown") {
  dependsOn(tasks.composeDown)
}

// FLYWAY

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

// JOOQ

jooqGenerator {
  jooqVersion = "3.15.5"
  configuration("database", sourceSets.main.get()) {
    databaseSources {
      + "${project.projectDir}/src/main/resources/db/migration"
    }
    configuration = jooqCodegenConfiguration {
      logging = org.jooq.meta.jaxb.Logging.WARN
      jdbc {
        driver = "org.postgresql.Driver"
        url = dbUrl
        user = dbUser
        password = dbPassword
      }
      generator {
        database {
          name = "org.jooq.meta.postgres.PostgresDatabase"
          inputSchema = "public"
          // `flyway_schema_history` is a Flyway table.
          excludes = "flyway_schema_history"
          forcedTypes {
            forcedType {
              includeTypes = "timestamp\\ with\\ time\\ zone"
              name = "INSTANT"
            }
            forcedType {
              includeTypes = "integer"
              includeExpression = "version"
              userType = "dev.pankowski.garcon.domain.Version"
              converter = "dev.pankowski.garcon.infrastructure.persistence.VersionConverter"
            }
            forcedType {
              includeExpression = "classification_status"
              userType = "dev.pankowski.garcon.domain.ClassificationStatus"
              isEnumConverter = true
            }
            forcedType {
              includeExpression = "repost_status"
              userType = "dev.pankowski.garcon.domain.RepostStatus"
              isEnumConverter = true
            }
            // Jooq applies the first matching conversion it encounters.
            // For columns holding enum values both below and enum converters match.
            // We want enum converters to win, so conversion below must be specified after them.
            // We need this because PostgreSQL `text` type doesn't have length restrictions,
            // so Jooq treats this as CLOB (mapped to String on Java side), but I don't want the JDBC
            // CLOB infrastructure to be involved for these fields.
            forcedType {
              includeTypes = "text"
              name = "varchar"
            }
          }
        }
        generate {
          isJavaTimeTypes = true
          isFluentSetters = true
          isGeneratedAnnotation = false
          isDeprecated = false
        }
        target {
          packageName = "dev.pankowski.garcon.infrastructure.persistence.generated"
          directory = "build/generated/source/jooq/main"
        }
      }
    }
  }
}

val jooqCodegen = tasks.named("jooq-codegen-database")

jooqCodegen {
  dependsOn(tasks.flywayMigrate)
}

tasks.compileKotlin {
  mustRunAfter(jooqCodegen)
}
