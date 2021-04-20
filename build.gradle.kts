import com.rohanprabhu.gradle.plugins.kdjooq.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.pankowski"

// PLUGINS

plugins {
  groovy
  kotlin("jvm") version "1.4.32"
  kotlin("kapt") version "1.4.32"
  kotlin("plugin.spring") version "1.4.32"
  id("org.springframework.boot") version "2.4.5"
  id("io.spring.dependency-management") version "1.0.11.RELEASE"
  id("com.gorylenko.gradle-git-properties") version "2.2.4"
  id("com.adarshr.test-logger") version "3.0.0"
  id("com.avast.gradle.docker-compose") version "0.14.3"
  id("org.flywaydb.flyway") version "7.8.1"
  id("com.rohanprabhu.kotlin-dsl-jooq") version "0.4.6"
}

tasks.wrapper {
  gradleVersion = "6.8.3"
}

java {
  sourceCompatibility = JavaVersion.VERSION_15
  targetCompatibility = JavaVersion.VERSION_15
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict") // Enable strict null-safety for Kotlin project
    jvmTarget = "15"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

// Needed because Spock tests depend on Kotlin test classes
tasks.compileTestGroovy {
  dependsOn(tasks.compileTestKotlin)
  classpath += files(tasks.compileTestKotlin.get().destinationDir)
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
    dependency("org.spockframework:spock-spring:1.3-groovy-2.5")
    dependency("org.spockframework:spock-core:1.3-groovy-2.5")
    dependency("org.jsoup:jsoup:1.13.1")
    dependency("com.github.tomakehurst:wiremock:2.27.2")
    dependency("org.flywaydb.flyway-test-extensions:flyway-spring-test:7.0.0")
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
  testImplementation("io.rest-assured:rest-assured") {
    // io.rest-assured:xml-path uses an older version of com.sun.xml.bind:jaxb-osgi which causes the following error:
    // 'dependencyManagement.dependencies.dependency.systemPath' for com.sun:tools:jar must specify an absolute
    //   path but is ${tools.jar} in com.sun.xml.bind:jaxb-osgi:2.2.10
    // We don't use XML path matching so let's just remove it altogether.
    exclude("io.rest-assured", "xml-path")
  }
  testImplementation("org.spockframework:spock-spring")
  testImplementation("org.spockframework:spock-core")
  testImplementation("com.github.tomakehurst:wiremock")
  testImplementation("org.flywaydb.flyway-test-extensions:flyway-spring-test")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine") // For Spock (relying on JUnit 4)

  // OTHER
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  jooqGeneratorRuntime("org.postgresql:postgresql")
}

// CI

val isCiEnv = System.getenv("CI") == "true"

// DOCKER COMPOSE

dockerCompose {
  useComposeFiles = listOf("docker-compose-integration-test.yml")
}

// Convenience task bringing up a clean, migrated database (e.g. for running integration tests in IDEA).
val databaseUp = tasks.register("databaseUp")

databaseUp {
  dependsOn(tasks.flywayMigrate)
}

tasks.composeUp {
  doLast {
    // Don't run docker-compose cleanup if databaseUp task was requested.
    if (gradle.taskGraph.allTasks.contains(databaseUp.get())) return@doLast

    logger.info("Registering docker-compose cleanup")
    gradle.taskGraph.afterTask {
      val hasFailed = this.state.failure != null
      val isLast = this == gradle.taskGraph.allTasks.last()
      if (hasFailed || isLast) tasks.composeDown.get().down()
    }
  }
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
  jooqVersion = "3.14.3"
  configuration("database", sourceSets.main.get()) {
    databaseSources {
      + "${project.projectDir}/src/main/resources/db/migration"
    }
    configuration = jooqCodegenConfiguration {
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
              types = "timestamp\\ without\\ time\\ zone"
              userType = "java.time.Instant"
              converter = "dev.pankowski.garcon.persistence.InstantConverter"
            }
            forcedType {
              types = "integer"
              expression = "version"
              userType = "dev.pankowski.garcon.domain.Version"
              converter = "dev.pankowski.garcon.persistence.VersionConverter"
            }
            forcedType {
              expression = "classification_status"
              userType = "dev.pankowski.garcon.domain.ClassificationStatus"
              isEnumConverter = true
            }
            forcedType {
              expression = "repost_status"
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
              types = "text"
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
          packageName = "dev.pankowski.garcon.persistence.generated"
          directory = "build/generated/source/jooq/main"
        }
      }
    }
  }
}

val `jooq-codegen-database` by project.tasks

`jooq-codegen-database`.dependsOn(tasks.flywayMigrate)

tasks.compileKotlin {
  mustRunAfter(`jooq-codegen-database`)
}
