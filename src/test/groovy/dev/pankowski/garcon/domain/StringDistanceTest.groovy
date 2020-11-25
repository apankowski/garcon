package dev.pankowski.garcon.domain

import spock.lang.Specification
import spock.lang.Unroll

import static dev.pankowski.garcon.domain.TextsKt.damerauLevenshtein
import static dev.pankowski.garcon.domain.TextsKt.levenshtein

class StringDistanceTest extends Specification {

  @Unroll
  def "Levenshtein distance between '#a' and '#b' is #distance"() {
    expect:
    levenshtein(a, b) == distance
    levenshtein(b, a) == distance

    where:
    a     | b     | distance
    ""    | ""    | 0
    "abc" | "abc" | 0
    "abc" | "ab"  | 1
    "abc" | "abd" | 1
    "bc"  | "abc" | 1
    "ac"  | "abc" | 1
    "abc" | "b"   | 2
    "abc" | "d"   | 3
    "abc" | "cd"  | 3
    "abc" | "ad"  | 2
  }

  @Unroll
  def "Damerau-Levenshtein distance between '#a' and '#b' is #distance"() {
    expect:
    damerauLevenshtein(a, b) == distance
    damerauLevenshtein(b, a) == distance

    where:
    a        | b        | distance
    ""       | ""       | 0
    ""       | "abc"    | 3
    "b"      | "abc"    | 2
    "d"      | "abc"    | 3
    "abc"    | "abc"    | 0
    "ab"     | "abc"    | 1
    "abc"    | "ab"     | 1
    "abc"    | "abd"    | 1
    "ac"     | "abc"    | 1
    "abc"    | "ac"     | 1
    "abc"    | "adc"    | 1
    "abc"    | "acb"    | 1
    "abc"    | "bac"    | 1
    "abc"    | "cab"    | 2
    "abcdef" | "bacdfe" | 2
    "abcdef" | "poiu"   | 6
  }
}
