package dev.pankowski.garcon.domain

import dev.pankowski.garcon.domain.LunchSubcommand.*
import org.springframework.stereotype.Component

class WrongCommandException : RuntimeException("Received wrong command")

@Component
class LunchSubcommandParser {

  private val locale = PolishLocale
  private val wordExtractor = WordExtractor(locale)

  fun parse(command: SlashCommand): LunchSubcommand {
    if (!"/lunch".equals(command.command, ignoreCase = true))
      throw WrongCommandException()

    val words = wordExtractor.extract(command.text.toLowerCase(locale))
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
