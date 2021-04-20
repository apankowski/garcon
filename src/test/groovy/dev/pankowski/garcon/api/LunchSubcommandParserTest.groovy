package dev.pankowski.garcon.api

import dev.pankowski.garcon.domain.LunchSubcommand
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class LunchSubcommandParserTest extends Specification {

  @Subject
  LunchSubcommandParser parser = new LunchSubcommandParser()

  private static def someLunchCommand(Map arguments = [:]) {
    arguments = [command: "/lunch", text: "text"] << arguments

    new SlashCommand(
      arguments.command,
      arguments.text,
      null,
      null,
      new UserId("U1234"),
      new ChannelId("C1234"),
      null,
      null
    )
  }

  def 'should fail for wrong command'() {
    given:
    def command = someLunchCommand(command: "/wrong-command")

    when:
    parser.parse(command)

    then:
    thrown WrongCommandException
  }

  def 'should return "check for lunch post" when command has no text'() {
    given:
    def command = someLunchCommand(text: text)

    expect:
    parser.parse(command) == LunchSubcommand.CheckForLunchPost.INSTANCE

    where:
    text << ["", " ", "\t", "\n"]
  }

  @Unroll
  def 'should return "help" for #text'() {
    given:
    def command = someLunchCommand(text: text)

    expect:
    parser.parse(command) == LunchSubcommand.Help.INSTANCE

    where:
    text << ["help", "  Help  ", "help!", "Hlep", "hlp."]
  }

  @Unroll
  def 'should return "log" for #text'() {
    given:
    def command = someLunchCommand(text: text)

    expect:
    parser.parse(command) == LunchSubcommand.Log.INSTANCE

    where:
    text << ["log", "  Log  ", "log!", "lgo", ".og"]
  }

  @Unroll
  def 'should return "check for lunch post" for #text'() {
    given:
    def command = someLunchCommand(text: text)

    expect:
    parser.parse(command) == LunchSubcommand.CheckForLunchPost.INSTANCE

    where:
    text << ["check", "  Check  ", "check!", "Chcek", "chk."]
  }

  @Unroll
  def 'should return "unrecognized" for #text'() {
    given:
    def command = someLunchCommand(text: text)

    expect:
    parser.parse(command) == new LunchSubcommand.Unrecognized(words)

    where:
    text     | words
    "l"      | ["l"]
    "He"     | ["he"]
    "hle"    | ["hle"]
    "no go!" | ["no", "go"]
    "What?"  | ["what"]
  }
}
