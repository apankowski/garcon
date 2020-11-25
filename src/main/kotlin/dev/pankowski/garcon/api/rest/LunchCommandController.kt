package dev.pankowski.garcon.api.rest

import dev.pankowski.garcon.domain.LunchService
import dev.pankowski.garcon.domain.MessagePayload
import dev.pankowski.garcon.domain.SlashCommand
import dev.pankowski.garcon.domain.WrongCommandException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

@RestController
class LunchCommandController(private val lunchService: LunchService) {

  @SlashCommandMapping("/commands/lunch")
  fun lunch(c: SlashCommand): MessagePayload {
    return lunchService.handle(c)
  }

  @ExceptionHandler(WrongCommandException::class)
  fun wrongCommand(response: HttpServletResponse) {
    response.sendError(SC_BAD_REQUEST)
  }
}
