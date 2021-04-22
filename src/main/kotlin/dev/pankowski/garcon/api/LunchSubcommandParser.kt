package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.*
import dev.pankowski.garcon.domain.LunchSubcommand.*
import org.springframework.stereotype.Component
import java.util.*

class WrongCommandException : RuntimeException("Received wrong command")

@Component
class LunchSubcommandParser {

  private val locale = Locale.ENGLISH

  fun parse(command: SlashCommand): LunchSubcommand {
    if (!"/lunch".equals(command.command, ignoreCase = true))
      throw WrongCommandException()

    val matcher = KeywordMatcher.onWordsOf(command.text, locale)
    return when {
      matcher.words.isEmpty() -> CheckForLunchPost
      matcher.words.size != 1 -> Unrecognized
      matcher.matches(Keyword("help", 1)) -> Help
      matcher.matches(Keyword("log", 1)) -> Log
      matcher.matches(Keyword("check", 2)) -> CheckForLunchPost
      else -> Unrecognized
    }
  }
}
