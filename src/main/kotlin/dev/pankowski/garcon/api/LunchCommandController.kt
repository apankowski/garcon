package dev.pankowski.garcon.api

import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST

@RestController
class LunchCommandController(private val lunchSlackService: LunchSlackService) {

  @SlashCommandMapping("/commands/lunch")
  fun lunch(c: SlashCommand): MessagePayload {
    return lunchSlackService.handle(c)
  }

  @ExceptionHandler(WrongCommandException::class)
  fun wrongCommand(response: HttpServletResponse) {
    response.sendError(SC_BAD_REQUEST)
  }
}
