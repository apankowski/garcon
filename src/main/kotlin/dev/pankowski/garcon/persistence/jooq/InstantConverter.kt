package dev.pankowski.garcon.persistence.jooq

import org.jooq.Converter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class InstantConverter : Converter<LocalDateTime, Instant> {

  override fun from(timestamp: LocalDateTime?): Instant? =
    timestamp?.toInstant(ZoneOffset.UTC)

  override fun to(instant: Instant?): LocalDateTime? =
    instant?.let { i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC) }

  override fun fromType(): Class<LocalDateTime> =
    LocalDateTime::class.java

  override fun toType(): Class<Instant> =
    Instant::class.java
}
