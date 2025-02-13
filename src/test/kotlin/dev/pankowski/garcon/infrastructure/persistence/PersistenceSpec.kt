package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.TestDatabaseConfiguration
import dev.pankowski.garcon.TestDatabaseExtension
import dev.pankowski.garcon.infrastructure.persistence.generated.DefaultCatalog.Companion.DEFAULT_CATALOG
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.extensions.spring.SpringExtension
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.context.annotation.ComponentScan.Filter
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Repository

/**
 * Base template for persistence-related specs.
 *
 * These should not require a full application context, but rather only the components related to
 * persistence such as Jooq DSL, repositories, etc.
 */
// @Repository components seem to be excluded by @JooqTest by default.
// Let's include them so repository injection works.
@JooqTest(includeFilters = [Filter(Repository::class)])
@Import(TestDatabaseConfiguration::class)
abstract class PersistenceSpec(body: PersistenceSpec.() -> Unit = {}) : FreeSpec() {

  @Autowired
  internal lateinit var context: DSLContext

  init {
    install(TestDatabaseExtension)
    register(SpringExtension)
    body()
  }

  override suspend fun beforeEach(testCase: TestCase) =
    // Clear all tables of test database, making each test start fresh
    DEFAULT_CATALOG.PUBLIC.tables.forEach { context.truncate(it).cascade().execute() }
}
