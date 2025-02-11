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
    // We have to start the container to be able to resolve its JDBC URL
    TestDatabase.start()
    it.add("spring.datasource.url") { TestDatabase.jdbcUrl }
    it.add("spring.datasource.username") { TestDatabase.username }
    it.add("spring.datasource.password") { TestDatabase.password }
  }
}
