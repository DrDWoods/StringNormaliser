package org.example.stringnormaliser;

import java.util.Objects;

/**
 * The canonical chosen for a term, and how well it matched.
 *
 * @param canonical the canonical, as given to {@link Normaliser} but without whitespace at the
 *     start or end
 * @param score the quality score {@code q}, where {@code 0.0 <= q <= 1.0}; {@code 1.0} means the
 *     term and canonical are considered identical
 */
public record Match(String canonical, double score) {

  /**
   * Creates a match, checking both fields.
   *
   * @throws NullPointerException if {@code canonical} is {@code null}
   * @throws IllegalArgumentException if {@code canonical} is blank, or {@code score} is outside
   *     {@code [0.0, 1.0]} or {@code NaN}
   */
  public Match {
    Objects.requireNonNull(canonical, "canonical must not be null");
    if (canonical.isBlank()) {
      throw new IllegalArgumentException("canonical must not be blank");
    }
    if (!(score >= 0.0 && score <= 1.0)) {
      throw new IllegalArgumentException("score must be between 0.0 and 1.0 but was " + score);
    }
  }
}
