package dev.pankowski.garcon

import io.kotest.extensions.testcontainers.ContainerExtension
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

val TestDatabase: PostgreSQLContainer<*> =
  PostgreSQLContainer(DockerImageName.parse("postgres:15.10"))
    .withDatabaseName("garcon")
    .withUsername("garcon")
    .withPassword("garcon")

val TestDatabaseExtension = ContainerExtension(TestDatabase)

@TestConfiguration
class TestDatabaseConfiguration {

  @Bean
  fun testDatabaseCoordinates() = DynamicPropertyRegistrar {
    it.add("jdbc.datasource.url") { TestDatabase.jdbcUrl }
    it.add("jdbc.datasource.username") { TestDatabase.username }
    it.add("jdbc.datasource.password") { TestDatabase.password }
  }
}
