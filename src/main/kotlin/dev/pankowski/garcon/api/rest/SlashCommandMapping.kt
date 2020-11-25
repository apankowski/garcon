package dev.pankowski.garcon.api.rest

import org.springframework.core.annotation.AliasFor
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.*

@Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
@Retention(RUNTIME)
@MustBeDocumented
@RequestMapping(
  method = [RequestMethod.POST],
  consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
  produces = [MediaType.APPLICATION_JSON_VALUE]
)
annotation class SlashCommandMapping(

  @get:AliasFor(annotation = RequestMapping::class)
  vararg val value: String
)
