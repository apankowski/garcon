package dev.pankowski.garcon.persistence.jooq

import dev.pankowski.garcon.domain.Version
import org.jooq.Converter

class VersionConverter : Converter<Int, Version> {

  override fun from(value: Int?): Version? = value?.let { Version(it) }

  override fun to(version: Version?): Int? = version?.value

  override fun fromType(): Class<Int> = Int::class.java

  override fun toType(): Class<Version> = Version::class.java
}
