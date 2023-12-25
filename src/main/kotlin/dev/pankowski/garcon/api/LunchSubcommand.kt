package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.Keyword
import dev.pankowski.garcon.domain.KeywordMatcher
import org.springframework.stereotype.Component
import java.util.*

sealed class LunchSubcommand {

  data object Help : LunchSubcommand()
  data object Log : LunchSubcommand()
  data object CheckForLunchPost : LunchSubcommand()
  data object Unrecognized : LunchSubcommand()
}

class WrongCommandException : RuntimeException("Received wrong command")

@Component
class LunchSubcommandParser {

  private val locale = Locale.ENGLISH

  fun parse(command: SlashCommand): LunchSubcommand {
    if (!"/lunch".equals(command.command, ignoreCase = true))
      throw WrongCommandException()

    val matcher = KeywordMatcher.onWordsOf(command.text, locale)
    return when {
      matcher.words.isEmpty() -> LunchSubcommand.CheckForLunchPost
      matcher.words.size != 1 -> LunchSubcommand.Unrecognized
      matcher.matches(Keyword("help", 1)) -> LunchSubcommand.Help
      matcher.matches(Keyword("log", 1)) -> LunchSubcommand.Log
      matcher.matches(Keyword("check", 2)) -> LunchSubcommand.CheckForLunchPost
      else -> LunchSubcommand.Unrecognized
    }
  }
}
