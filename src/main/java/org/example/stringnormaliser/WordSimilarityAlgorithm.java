package org.example.stringnormaliser;

/**
 * Scores how similar two single words are. Pass an implementation to {@link
 * Normaliser#Normaliser(java.util.List, WordSimilarityAlgorithm, double)} to replace the default,
 * Levenshtein edit distance.
 *
 * <p>Implementations must return a quality score {@code q} where {@code 0.0 <= q <= 1.0}: {@code
 * 0.0} means no similarity and {@code 1.0} means the words are considered identical. {@link
 * Normaliser} checks every score and throws {@link IllegalStateException} for one outside that
 * range, including {@code NaN}.
 *
 * <p>Two words count as matching only if they score at least {@code 0.8}; a lower score counts as
 * {@code 0.0}. Implementations should be scaled with that in mind.
 *
 * <p>Every pair of words is scored in both orders: a word of the term against a word of a
 * canonical, and the reverse.
 *
 * <p>Each word is a lower-case word of a term or canonical: at least two characters, each an ASCII
 * letter, digit, {@code #}, {@code +} or apostrophe, with at least one letter or digit and no
 * apostrophe at either end.
 */
@FunctionalInterface
public interface WordSimilarityAlgorithm {

  /**
   * Scores how similar two words are.
   *
   * @param first the first word, never {@code null}
   * @param second the second word, never {@code null}
   * @return the similarity score, between {@code 0.0} and {@code 1.0} inclusive
   */
  double score(String first, String second);
}
