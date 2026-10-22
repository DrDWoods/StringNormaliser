package org.example.stringnormaliser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Finds which of a fixed set of canonicals best matches a term.
 *
 * <p>Each canonical's comparison form, produced by {@link TermCleaner}, is split into words by
 * {@link Tokeniser} once, when the matcher is built; each query only tokenises the term. Every
 * canonical is scored against the term and the highest score wins, with ties going to the canonical
 * held first. Whether that score is good enough is for {@link Normaliser} to decide. A match always
 * returns the canonical, never its comparison form.
 *
 * <p>The score is the proportion of words in either string that have a close match in the other.
 * Each word is paired with its most similar word on the other side, as judged by a {@link
 * WordSimilarityAlgorithm}; pairs scoring below {@value #WORD_SIMILARITY_THRESHOLD} count as {@code
 * 0}. The score is the sum of those best-pair scores in both directions divided by the total number
 * of words. When every word matches exactly or not at all, and none is repeated, this is the Dice
 * coefficient {@code 2|A ∩ B| / (|A| + |B|)}, e.g. "Chief Accountant" vs "Accountant" scores {@code
 * 2/3}. With the default {@link LevenshteinSimilarity}, small typos ("Acountant") still count as a
 * match.
 *
 * <p>The word similarity algorithm may come from a client, so every score it returns is checked. A
 * score outside {@code [0.0, 1.0]}, or {@code NaN}, would silently pick the wrong canonical, so it
 * throws {@link IllegalStateException} instead.
 *
 * <p>Package-private: {@link Normaliser} validates the canonicals and the term, so this class
 * asserts its assumptions rather than checking them. It keeps its own copy of the canonicals, so
 * instances are immutable and safe to share between threads, provided the word similarity algorithm
 * is.
 */
final class Matcher {

  /** A canonical with the words of its comparison form. */
  private record Candidate(String canonical, List<String> tokens) {}

  /** Minimum word similarity for two words to be treated as the same word. */
  private static final double WORD_SIMILARITY_THRESHOLD = 0.80;

  private final List<Candidate> candidates;
  private final WordSimilarityAlgorithm wordSimilarityAlgorithm;

  /**
   * @param canonicalsCleanToOriginal each canonical under its comparison form, in order of
   *     preference for ties; at least one
   * @param wordSimilarityAlgorithm scores one word against another
   */
  Matcher(
      SequencedMap<String, String> canonicalsCleanToOriginal,
      WordSimilarityAlgorithm wordSimilarityAlgorithm) {
    assert canonicalsCleanToOriginal != null;
    assert !canonicalsCleanToOriginal.isEmpty();
    assert canonicalsCleanToOriginal.keySet().stream().noneMatch(Objects::isNull);
    assert canonicalsCleanToOriginal.values().stream().noneMatch(Objects::isNull);
    assert wordSimilarityAlgorithm != null;

    List<Candidate> candidates = new ArrayList<>();
    for (Map.Entry<String, String> entry : canonicalsCleanToOriginal.entrySet()) {
      String clean = entry.getKey();
      String original = entry.getValue();
      candidates.add(new Candidate(original, Tokeniser.tokenise(clean)));
    }
    // List.copyOf keeps the order, and with it the tie-break.
    this.candidates = List.copyOf(candidates);
    this.wordSimilarityAlgorithm = wordSimilarityAlgorithm;
  }

  /**
   * Scores every canonical against {@code key} and returns the best one.
   *
   * @param key the term in comparison form
   * @return the best canonical with its score, even if that score is {@code 0.0}
   * @throws IllegalStateException if the word similarity algorithm returns a score outside {@code
   *     [0.0, 1.0]}
   */
  Match findBestMatch(String key) {
    assert key != null;
    assert !key.isBlank();

    List<String> keyTokens = Tokeniser.tokenise(key);
    String bestCanonical = null;
    double bestScore = -1.0;
    for (Candidate candidate : candidates) {
      double score = score(keyTokens, candidate.tokens());
      // Strictly greater, so on a tie the canonical held first is kept; tiebreaker is therefore
      // input order.
      if (score > bestScore) {
        bestCanonical = candidate.canonical();
        bestScore = score;
      }
    }
    // Every score is at least 0.0 and there is at least one canonical, so one was always chosen.
    return new Match(bestCanonical, bestScore);
  }

  private double score(List<String> keyTokens, List<String> canonicalTokens) {
    assert keyTokens != null;
    assert canonicalTokens != null;

    if (keyTokens.isEmpty() || canonicalTokens.isEmpty()) {
      return 0.0;
    }

    double matched =
        sumOfBestMatches(keyTokens, canonicalTokens) + sumOfBestMatches(canonicalTokens, keyTokens);
    double score = matched / (keyTokens.size() + canonicalTokens.size());
    // Every word score has been checked, so the average of the best ones is in range too.
    assert score >= 0.0 && score <= 1.0;
    return score;
  }

  private double sumOfBestMatches(List<String> tokens, List<String> others) {
    assert tokens != null;
    assert tokens.stream().noneMatch(Objects::isNull);
    assert others != null;
    assert others.stream().noneMatch(Objects::isNull);

    double sum = 0.0;
    for (String token : tokens) {
      sum += bestMatch(token, others);
    }
    return sum;
  }

  private double bestMatch(String token, List<String> others) {
    assert token != null;
    assert others != null;
    assert others.stream().noneMatch(Objects::isNull);

    double best = 0.0;
    for (String other : others) {
      double similarityScore = checkedWordScore(token, other);
      if (similarityScore >= WORD_SIMILARITY_THRESHOLD && similarityScore > best) {
        best = similarityScore;
      }
    }
    return best;
  }

  private double checkedWordScore(String token, String other) {
    assert token != null;
    assert other != null;

    double score = wordSimilarityAlgorithm.score(token, other);
    if (!(score >= 0.0 && score <= 1.0)) {
      // Tokens contain only ASCII letters, digits, # + and ', so they are safe to copy.
      throw new IllegalStateException(
          "wordSimilarityAlgorithm must return a score between 0.0 and 1.0 but returned "
              + score
              + " for \""
              + token
              + "\" and \""
              + other
              + "\"");
    }
    return score;
  }
}
