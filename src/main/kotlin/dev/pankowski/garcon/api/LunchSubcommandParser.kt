package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.LunchSubcommand
import dev.pankowski.garcon.domain.LunchSubcommand.*
import dev.pankowski.garcon.domain.PolishLocale
import dev.pankowski.garcon.domain.damerauLevenshtein
import dev.pankowski.garcon.domain.extractWords
import org.springframework.stereotype.Component

class WrongCommandException : RuntimeException("Received wrong command")

@Component
class LunchSubcommandParser {

  // TODO: Make locale configurable
  private val locale = PolishLocale

  fun parse(command: SlashCommand): LunchSubcommand {
    if (!"/lunch".equals(command.command, ignoreCase = true))
      throw WrongCommandException()

    val words = command.text.toLowerCase(locale).extractWords(locale)
    if (words.isEmpty())
      return CheckForLunchPost

    fun matchesKeyword(k: String, editDistance: Int) =
      damerauLevenshtein(words.first(), k) <= editDistance

    return when {
      // TODO: Introduce Keyword and possibly KeywordMatcher?
      matchesKeyword("help", 1) -> Help
      matchesKeyword("log", 1) -> Log
      matchesKeyword("check", 2) -> CheckForLunchPost
      else -> Unrecognized(words)
    }
  }
}
