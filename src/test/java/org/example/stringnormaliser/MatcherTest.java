package org.example.stringnormaliser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.SequencedMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MatcherTest {

  private static final WordSimilarityAlgorithm LEVENSHTEIN = new LevenshteinSimilarity();

  /**
   * Holds each key's canonical as the key in upper case, so a test can tell a canonical from its
   * key.
   */
  private static SequencedMap<String, String> canonicals(String... keys) {
    SequencedMap<String, String> canonicals = new LinkedHashMap<>();
    for (String key : keys) {
      canonicals.put(key, key.toUpperCase(Locale.ENGLISH));
    }
    return canonicals;
  }

  /** The score {@code key} gets against a single canonical, comparing words by edit distance. */
  private static double score(String key, String canonicalKey) {
    return new Matcher(canonicals(canonicalKey), LEVENSHTEIN).findBestMatch(key).score();
  }

  @Nested
  class FindBestMatch {

    @Test
    void returnsTheHighestScoringCanonicalWithItsScore() {
      Matcher matcher =
          new Matcher(canonicals("architect", "accountant", "software engineer"), LEVENSHTEIN);

      Match match = matcher.findBestMatch("chief accountant");

      assertEquals("ACCOUNTANT", match.canonical());
      assertEquals(2.0 / 3.0, match.score(), 1e-9);
    }

    @Test
    void prefersTheEarlierCanonicalOnATie() {
      WordSimilarityAlgorithm allEqual = (first, second) -> 1.0;

      assertEquals(
          new Match("AA", 1.0), new Matcher(canonicals("aa", "bb"), allEqual).findBestMatch("xx"));
      assertEquals(
          new Match("BB", 1.0), new Matcher(canonicals("bb", "aa"), allEqual).findBestMatch("xx"));
    }

    @Test
    void returnsTheFirstCanonicalWithAZeroScoreWhenNothingMatches() {
      Matcher matcher = new Matcher(canonicals("architect", "accountant"), LEVENSHTEIN);

      assertEquals(new Match("ARCHITECT", 0.0), matcher.findBestMatch("plumber"));
    }
  }

  @Nested
  class Scoring {

    @ParameterizedTest(name = "\"{0}\" vs \"{1}\" = {2}")
    @CsvSource({
      "accountant,                accountant,         1.0",
      "software engineer,         software engineer,  1.0",
      "engineer software,         software engineer,  1.0",
      // Each copy of a repeated word counts, so this is 1.0 rather than the Dice coefficient's 2/3.
      "accountant accountant,     accountant,         1.0",
      "chief accountant,          accountant,         0.6666666667",
      "java engineer,             software engineer,  0.5",
      "senior software engineer,  software engineer,  0.8",
      "java engineer,             accountant,         0.0",
    })
    void scoresByProportionOfMatchingWords(String key, String canonicalKey, double expected) {
      assertEquals(expected, score(key, canonicalKey), 1e-9);
    }

    @Test
    void isSymmetric() {
      assertEquals(
          score("chief accountant", "accountant"), score("accountant", "chief accountant"));
    }

    @Test
    void closeWordsScoreTheirSimilarity() {
      // "acountant" is one edit from "accountant": word similarity 0.9 in both directions.
      assertEquals(0.9, score("acountant", "accountant"), 1e-9);
    }

    @Test
    void wordsAtTheWordThresholdCount() {
      // "chief" vs "chef" is 1 edit in 5 letters: 0.8, exactly the threshold.
      assertEquals(0.8, score("chief", "chef"), 1e-9);
    }

    @ParameterizedTest(name = "\"{0}\" vs \"{1}\"")
    @CsvSource({
      // 3 edits in 12 letters: 0.75, just below the threshold.
      "architecture, architect",
      // 3 edits in 11 letters: about 0.73.
      "engineering,  engineer",
    })
    void wordsBelowTheWordThresholdDoNotCount(String key, String canonicalKey) {
      assertEquals(0.0, score(key, canonicalKey));
    }

    @ParameterizedTest(name = "\"{0}\" vs \"{1}\"")
    @CsvSource({"accountant, accountant acountant", "accountant, acountant accountant"})
    void pairsEachWordWithItsMostSimilarWordWhereverItIs(String key, String canonicalKey) {
      // "accountant" pairs with "accountant" (1.0), not "acountant" (0.9): (1.0 + 1.0 + 0.9) / 3.
      assertEquals(2.9 / 3, score(key, canonicalKey), 1e-9);
    }

    @Test
    void usesTheSuppliedWordSimilarityAlgorithm() {
      WordSimilarityAlgorithm sameFirstLetter =
          (first, second) -> first.charAt(0) == second.charAt(0) ? 1.0 : 0.0;
      Matcher matcher = new Matcher(canonicals("architect"), sameFirstLetter);

      assertEquals(1.0, matcher.findBestMatch("accountant").score());
    }

    @ParameterizedTest(name = "\"{0}\" vs \"{1}\"")
    @CsvSource({"r&d, accountant", "accountant, c", "r&d, c"})
    void textWithNoWordsScoresZero(String key, String canonicalKey) {
      assertEquals(0.0, score(key, canonicalKey));
    }

    @Test
    void scoreIsWithinRangeEvenWithRepeatedWords() {
      double score = score("c# c# c# engineer engineer", "software engineer");

      assertTrue(score >= 0.0 && score <= 1.0, "score was " + score);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAWordScoreOutsideZeroToOne(double score) {
      Matcher matcher = new Matcher(canonicals("accountant"), (first, second) -> score);

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> matcher.findBestMatch("chief"));
      assertEquals(
          "wordSimilarityAlgorithm must return a score between 0.0 and 1.0 but returned "
              + score
              + " for \"chief\" and \"accountant\"",
          thrown.getMessage());
    }

    @Test
    void checksEveryWordScoreEvenAfterAPerfectMatch() {
      WordSimilarityAlgorithm brokenForB =
          (first, second) -> first.equals("bb") || second.equals("bb") ? 2.0 : 1.0;
      Matcher matcher = new Matcher(canonicals("aa", "bb"), brokenForB);

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> matcher.findBestMatch("xx"));
      assertEquals(
          "wordSimilarityAlgorithm must return a score between 0.0 and 1.0 but returned 2.0"
              + " for \"xx\" and \"bb\"",
          thrown.getMessage());
    }
  }

  @Nested
  class Construction {

    @Test
    void copiesCanonicalsSoLaterChangesHaveNoEffect() {
      SequencedMap<String, String> canonicals = canonicals("aa");
      Matcher matcher = new Matcher(canonicals, LEVENSHTEIN);

      canonicals.put("bb", "BB");

      assertEquals(new Match("AA", 0.0), matcher.findBestMatch("bb"));
    }
  }
}
