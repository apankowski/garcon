package dev.pankowski.garcon.configuration

import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ActuatorConfiguration {

  @Bean
  fun httpTraceRepository() = InMemoryHttpTraceRepository()
}
