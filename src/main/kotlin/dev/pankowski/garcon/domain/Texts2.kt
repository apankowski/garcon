package dev.pankowski.garcon.domain

import java.text.BreakIterator
import java.util.*
import java.util.Collections.unmodifiableList

/**
 * Calculates [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
 * between given char sequences.
 */
fun levenshtein2(a: CharSequence, b: CharSequence): Int {
  val aLength = a.length
  val bLength = b.length

  var cost = IntArray(aLength + 1) { it }
  var newCost = IntArray(aLength + 1) { 0 }

  for (j in 1..bLength) {
    newCost[0] = j

    for (i in 1..aLength) {
      val insertCost = cost[i] + 1
      val deleteCost = newCost[i - 1] + 1
      val replaceCost = cost[i - 1] + if (a[i - 1] == b[j - 1]) 0 else 1

      newCost[i] = minOf(insertCost, deleteCost, replaceCost)
    }

    run { val temp = cost; cost = newCost; newCost = temp }
    // Or: cost = newCost.also { newCost = cost }
  }

  return cost[aLength]
}

/**
 * Calculates [Damerau-Levenshtein distance](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance)
 * between given char sequences.
 */
fun damerauLevenshtein2(a: CharSequence, b: CharSequence): Int {
  fun distance(a: CharSequence, b: CharSequence): Int {
    assert(a.length >= 2)
    assert(b.length >= 2)

    fun levenshteinCost(cost_1: IntArray, cost_0: IntArray, i: Int, j: Int): Int {
      val insertCost = cost_1[i] + 1
      val deleteCost = cost_0[i - 1] + 1
      val replaceCost = cost_1[i - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
      return minOf(insertCost, deleteCost, replaceCost)
    }

    fun damerauCost(cost_2: IntArray, cost_1: IntArray, cost_0: IntArray, i: Int, j: Int): Int {
      val levenshteinCost = levenshteinCost(cost_1, cost_0, i, j)
      if (a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
        val transpositionCost = cost_2[i - 2] + 1
        return minOf(levenshteinCost, transpositionCost)
      }
      return levenshteinCost
    }

    val aLength = a.length
    val bLength = b.length

    // First seed row, corresponding to j = 0
    val cost_2 = IntArray(aLength + 1) { it }

    // Second seed row, corresponding to j = 1
    val cost_1 = IntArray(aLength + 1)
    cost_1[0] = 1
    for (i in 1..aLength) cost_1[i] = levenshteinCost(cost_2, cost_1, i, 1)

    val cost_0 = IntArray(aLength + 1)

    tailrec fun cost(cost_2: IntArray, cost_1: IntArray, cost_0: IntArray, j: Int): Int {
      cost_0[0] = j
      cost_0[1] = levenshteinCost(cost_1, cost_0, 1, j)
      for (i in 2..aLength) cost_0[i] = damerauCost(cost_2, cost_1, cost_0, i, j)

      return if (j == bLength) cost_0[aLength]
      else cost(cost_1, cost_0, cost_2, j + 1)
    }

    return cost(cost_2, cost_1, cost_0, 2)
  }

  // Analyze input and eliminate simple cases:
  // - Order strings by length, so that we have longer (l) and shorter (s).
  // - Exit early when shorter string has zero or one character.
  // This leaves us with l.length >= s.length >= 2.
  val (l, s) = if (a.length >= b.length) Pair(a, b) else Pair(b, a)
  return when {
    s.isEmpty() -> l.length
    s.length == 1 -> if (l.contains(s[0])) l.length - 1 else l.length
    else -> distance(l, s)
  }
}

fun String.extractWords2(locale: Locale): List<String> {
  val it = BreakIterator.getWordInstance(locale)
  it.setText(this)

  tailrec fun collectWords(start: Int, end: Int, words: List<String>): List<String> {
    if (end == BreakIterator.DONE) return words
    val word = this.substring(start, end)
    val newWords =
      if (Character.isLetterOrDigit(word[0])) words + word
      else words
    return collectWords(end, it.next(), newWords)
  }

  return unmodifiableList(collectWords(it.first(), it.next(), emptyList()))
}
