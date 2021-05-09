package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.LunchSubcommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class LunchSubcommandParserTest : FreeSpec({

  val parser = LunchSubcommandParser()

  fun verifyParsing(subcommand: LunchSubcommand, vararg texts: String) =
    texts.forEach { text ->
      "text '$text' is parsed as $subcommand" {
        // given
        val command = someLunchCommand(text = text)

        // expect
        parser.parse(command) shouldBe subcommand
      }
    }

  "should fail for wrong command" {
    // given
    val command = someLunchCommand(command = "/wrong-command")

    // expect
    shouldThrow<WrongCommandException> {
      parser.parse(command)
    }
  }

  "should handle commands with no words" - {
    verifyParsing(LunchSubcommand.CheckForLunchPost, "", " ", "\t", "\n", ".", "  ,@% ")
  }

  "should handle 'help' commands" - {
    verifyParsing(LunchSubcommand.Help, "help", "  Help  ", "help!", "Hlep", "hlp.")
  }

  "should handle 'log' commands" - {
    verifyParsing(LunchSubcommand.Log, "log", "  Log  ", "log!", "lgo", ".og")
  }

  "should handle 'check' commands" - {
    verifyParsing(LunchSubcommand.CheckForLunchPost, "check", "  Check  ", "check!", "Chcek", "chk.")
  }

  "should handle unrecognized commands" - {
    verifyParsing(
      LunchSubcommand.Unrecognized,
      "l",
      "He",
      "hle",
      "no go!",
      "What?",
      "log something",
      "check something",
      "help something"
    )
  }
})
