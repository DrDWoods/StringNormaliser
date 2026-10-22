package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LevenshteinSimilarityTest {

  private static final LevenshteinSimilarity SIMILARITY = new LevenshteinSimilarity();

  @ParameterizedTest(name = "{3}: \"{0}\" to \"{1}\" takes {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "''         | ''        | 0 | both empty",
        "abc        | ''        | 3 | delete every character",
        "''         | abc       | 3 | insert every character",
        "a          | ''        | 1 | delete a single character",
        "a          | a         | 0 | single identical letter",
        "a          | b         | 1 | single substitution",
        "engineer   | engineer  | 0 | identical word",
        "accountant | acountant | 1 | one missing letter",
        "xengineer  | engineer  | 1 | extra letter at the start",
        "engxineer  | engineer  | 1 | extra letter in the middle",
        "engineerx  | engineer  | 1 | extra letter at the end",
        "ab         | ba        | 2 | swapping two letters costs two edits, not one",
        "abc        | cba       | 2 | reversal: the middle letter stays",
        "abc        | xyz       | 3 | every letter substituted",
        "a          | aaaa      | 3 | much shorter string inside a longer one",
        "aaaa       | aaa       | 1 | repeated letters",
        "flaw       | lawn      | 2 | delete at the start, insert at the end",
        "kitten     | sitting   | 3 | two substitutions and an insertion",
        "sunday     | saturday  | 3 | two insertions and a substitution",
      })
  void distanceIsTheFewestSingleCharacterEdits(String first, String second, int expected) {
    assertEquals(expected, LevenshteinSimilarity.distance(first, second));
    assertEquals(expected, LevenshteinSimilarity.distance(second, first));
  }

  @ParameterizedTest(name = "{3}: \"{0}\" vs \"{1}\" scores {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "''         | ''        | 1.0   | both empty",
        "engineer   | engineer  | 1.0   | identical",
        "abc        | xyz       | 0.0   | nothing in common",
        "abc        | ''        | 0.0   | one empty",
        "a          | b         | 0.0   | single letters that differ",
        "ab         | ba        | 0.0   | two letters swapped",
        "a          | aaaa      | 0.25  | shorter string inside a longer one",
        "abcd       | abcx      | 0.75  | 1 edit in 4 letters",
        "abcde      | abcdx     | 0.8   | 1 edit in 5 letters, exactly the word threshold",
        "accountant | acountant | 0.9   | 1 edit in 10 letters",
        "Engineer   | engineer  | 0.875 | case counts, 1 edit in 8 letters",
      })
  void scoreIsOneMinusDistanceOverTheLongerLength(String first, String second, double expected) {
    assertEquals(expected, SIMILARITY.score(first, second));
    assertEquals(expected, SIMILARITY.score(second, first));
  }

  @Test
  void handlesStringsAtTheMaximumTermLength() {
    String longest = "a".repeat(Normaliser.MAX_TERM_LENGTH);

    assertEquals(0, LevenshteinSimilarity.distance(longest, longest));
    assertEquals(1, LevenshteinSimilarity.distance(longest, longest.substring(1)));
    assertEquals(
        Normaliser.MAX_TERM_LENGTH,
        LevenshteinSimilarity.distance(longest, "b".repeat(Normaliser.MAX_TERM_LENGTH)));
  }

  /**
   * Properties that must hold for any strings, checked on random ones built from printable ASCII,
   * the range {@link Normaliser} accepts characters from, so they look like real input. The fixed
   * seed makes a failure repeatable.
   *
   * <p>With so many characters, two independent random strings are almost never equal or close. So
   * half the time the second string is made by applying a few random edits to the first, which
   * keeps equal and nearly equal pairs common.
   */
  @Nested
  class ForAnyStrings {

    private static final int TRIALS = 1_000;

    /** Every printable ASCII character, from the space to the tilde. */
    private static final String ALPHABET = printableAscii();

    private final Random random = new Random(42);

    private static String printableAscii() {
      StringBuilder printable = new StringBuilder();
      for (char c = ' '; c <= '~'; c++) {
        printable.append(c);
      }
      return printable.toString();
    }

    private char randomCharacter() {
      return ALPHABET.charAt(random.nextInt(ALPHABET.length()));
    }

    private String randomString() {
      StringBuilder text = new StringBuilder();
      int length = random.nextInt(Normaliser.MAX_TERM_LENGTH + 1);
      for (int i = 0; i < length; i++) {
        text.append(randomCharacter());
      }
      return text.toString();
    }

    /** Applies {@code edits} random insertions, deletions or substitutions to {@code text}. */
    private String applyRandomEdits(String text, int edits) {
      StringBuilder edited = new StringBuilder(text);
      for (int i = 0; i < edits; i++) {
        int kind = edited.isEmpty() ? 0 : random.nextInt(3);
        switch (kind) {
          case 0 -> edited.insert(random.nextInt(edited.length() + 1), randomCharacter());
          case 1 -> edited.deleteCharAt(random.nextInt(edited.length()));
          default -> edited.setCharAt(random.nextInt(edited.length()), randomCharacter());
        }
      }
      return edited.toString();
    }

    /** Either an unrelated random string or {@code first} with up to three random edits. */
    private String secondFor(String first) {
      return random.nextBoolean() ? randomString() : applyRandomEdits(first, random.nextInt(4));
    }

    @Test
    void distanceIsZeroExactlyWhenTheStringsAreEqual() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        String second = secondFor(first);

        assertEquals(
            first.equals(second),
            LevenshteinSimilarity.distance(first, second) == 0,
            () -> "\"" + first + "\" vs \"" + second + "\"");
      }
    }

    // Edits can undo each other, such as inserting a character and then deleting it, so the
    // distance can be less than the number of edits, but never more.
    @Test
    void distanceIsAtMostTheNumberOfEditsMade() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        int edits = random.nextInt(10);
        String second = applyRandomEdits(first, edits);

        assertTrue(
            LevenshteinSimilarity.distance(first, second) <= edits,
            () -> "\"" + first + "\" vs \"" + second + "\" after " + edits + " edits");
      }
    }

    @Test
    void distanceIsSymmetric() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        String second = secondFor(first);

        assertEquals(
            LevenshteinSimilarity.distance(first, second),
            LevenshteinSimilarity.distance(second, first),
            () -> "\"" + first + "\" vs \"" + second + "\"");
      }
    }

    // Coarse verification of limits
    @Test
    void distanceIsBetweenTheLengthDifferenceAndTheLongerLength() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        String second = secondFor(first);
        int distance = LevenshteinSimilarity.distance(first, second);

        assertTrue(
            distance >= Math.abs(first.length() - second.length())
                && distance <= Math.max(first.length(), second.length()),
            () -> "\"" + first + "\" vs \"" + second + "\" gave " + distance);
      }
    }

    @Test
    void distanceObeysTheTriangleInequality() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        String middle = secondFor(first);
        String last = secondFor(middle);

        assertTrue(
            LevenshteinSimilarity.distance(first, last)
                <= LevenshteinSimilarity.distance(first, middle)
                    + LevenshteinSimilarity.distance(middle, last),
            () -> "\"" + first + "\", \"" + middle + "\", \"" + last + "\"");
      }
    }

    @Test
    void scoreIsAlwaysWithinZeroAndOne() {
      for (int trial = 0; trial < TRIALS; trial++) {
        String first = randomString();
        String second = secondFor(first);
        double score = SIMILARITY.score(first, second);

        assertTrue(
            score >= 0.0 && score <= 1.0,
            () -> "\"" + first + "\" vs \"" + second + "\" scored " + score);
      }
    }
  }
}
