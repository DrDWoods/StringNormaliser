package org.example.stringnormaliser;

/**
 * Character-level similarity based on Levenshtein edit distance, scaled to {@code 0.0..1.0}.
 *
 * <p>The score is {@code 1 - distance / max(length)}, so identical strings score {@code 1.0} and
 * strings with nothing in common score {@code 0.0}. The comparison is case-sensitive.
 *
 * <p>The default word measure for {@link Normaliser}. Instances hold no state, so one can be shared
 * freely.
 */
final class LevenshteinSimilarity implements WordSimilarityAlgorithm {

  @Override
  public double score(String first, String second) {
    assert first != null;
    assert second != null;

    int maxLength = Math.max(first.length(), second.length());
    if (maxLength == 0) {
      return 1.0;
    }
    return 1.0 - (double) distance(first, second) / maxLength;
  }

  /**
   * Minimum number of single-character insertions, deletions or substitutions, found by the
   * Wagner-Fischer algorithm keeping only two rows of its table.
   */
  static int distance(String first, String second) {
    assert first != null;
    assert second != null;

    int[] previous = new int[second.length() + 1];
    int[] current = new int[second.length() + 1];
    for (int j = 0; j <= second.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= first.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= second.length(); j++) {
        int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(
                Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitutionCost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[second.length()];
  }
}
