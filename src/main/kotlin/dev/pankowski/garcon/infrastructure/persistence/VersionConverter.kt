package dev.pankowski.garcon.infrastructure.persistence

import dev.pankowski.garcon.domain.Version
import org.jooq.Converter

class VersionConverter : Converter<Int, Version> {

  override fun from(number: Int?): Version? = number?.let { Version(it) }

  override fun to(version: Version?): Int? = version?.number

  override fun fromType(): Class<Int> = Int::class.java

  override fun toType(): Class<Version> = Version::class.java
}
